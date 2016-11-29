package cc.mallet.topics;

import java.util.*;
import java.util.logging.*;
import java.util.zip.*;
import java.util.regex.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.util.*;

import com.carrotsearch.hppc.IntDoubleHashMap;
import com.carrotsearch.hppc.cursors.IntDoubleCursor;

public class WeightedTopicModel implements Serializable {

	private static Logger logger = MalletLogger.getLogger(WeightedTopicModel.class.getName());
	
	static CommandOption.String inputFile = new CommandOption.String
		(WeightedTopicModel.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String weightsFile = new CommandOption.String
		(WeightedTopicModel.class, "weights-filename", "FILENAME", true, null,
		 "The filename for the word-word weights file.", null);
	
	static CommandOption.String evaluatorFilename = new CommandOption.String
		(WeightedTopicModel.class, "evaluator-filename", "FILENAME", true, null,
		 "A held-out likelihood evaluator for new documents.  " +
		 "By default this is null, indicating that no file will be written.", null);
	
	static CommandOption.String stateFile = new CommandOption.String
		(WeightedTopicModel.class, "state-filename", "FILENAME", true, null,
		 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);
	
	static CommandOption.Integer numTopicsOption = new CommandOption.Integer
		(WeightedTopicModel.class, "num-topics", "INTEGER", true, 10,
		 "The number of topics to fit.", null);
	
	static CommandOption.Integer numEpochsOption = new CommandOption.Integer
		(WeightedTopicModel.class, "num-epochs", "INTEGER", true, 1,
		 "The number of cycles of training. Evaluators and state files will be saved after each epoch.", null);
	
	static CommandOption.Integer numIterationsOption = new CommandOption.Integer
		(WeightedTopicModel.class, "num-iterations", "INTEGER", true, 1000,
		 "The number of iterations of Gibbs sampling PER EPOCH.", null);
	
	static CommandOption.Integer randomSeedOption = new CommandOption.Integer
		(WeightedTopicModel.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	static CommandOption.Double alphaOption = new CommandOption.Double
		(WeightedTopicModel.class, "alpha", "DECIMAL", true, 50.0,
		 "Alpha parameter: smoothing over topic distribution.",null);
	
	static CommandOption.Double betaOption = new CommandOption.Double
		(WeightedTopicModel.class, "beta", "DECIMAL", true, 0.01,
		 "Beta parameter: smoothing over topic distribution.",null);
	

	public static Pattern sourceWordPattern = Pattern.compile("(.*) \\((\\d+)\\)");
	public static Pattern targetWordPattern = Pattern.compile("  (\\d+)\t(\\d+)\t([\\d\\.]+)\t(.*)");

	// the training instances and their topic assignments
	protected ArrayList<TopicAssignment> data;  

	// the alphabet for the input data
	protected Alphabet alphabet; 

	// the alphabet for the topics
	protected LabelAlphabet topicAlphabet; 
	
	// The number of topics requested
	protected int numTopics;

	// The size of the vocabulary
	protected int numTypes;

	// Prior parameters
	protected double alpha;	 // Dirichlet(alpha,alpha,...) is the distribution over topics
	protected double alphaSum;
	
	protected double beta;
	protected double betaSum;

	// An array to put the topic counts for the current document. 
	// Initialized locally below.  Defined here to avoid
	// garbage collection overhead.
	protected int[] oneDocTopicCounts; // indexed by <document index, topic index>

	// Statistics needed for sampling.
	protected int[][] typeTopicCounts; // indexed by <feature index, topic index>
	protected int[] tokensPerTopic; // indexed by <topic index>

	// Weights on type-type interactions
	protected IntDoubleHashMap[] typeTypeWeights;

	protected double[][] logTypeTopicWeights;
	protected double[][] typeTopicWeights;
	protected double[] totalTopicWeights;

	public int showTopicsInterval = 50;
	public int wordsPerTopic = 10;
	
	protected Randoms random;
	protected NumberFormat formatter;
	protected boolean printLogLikelihood = false;

	protected double[] logCountRatioCache;
	
	public WeightedTopicModel (int numberOfTopics, double alphaSum, double beta, Randoms random) {

		this.data = new ArrayList<TopicAssignment>();
		this.topicAlphabet = AlphabetFactory.labelAlphabetOfSize(numberOfTopics);
		this.numTopics = topicAlphabet.size();

		this.alphaSum = alphaSum;
		this.alpha = alphaSum / numTopics;
		this.beta = beta;
		this.random = random;
		
		oneDocTopicCounts = new int[numTopics];
		tokensPerTopic = new int[numTopics];
		
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		logger.info("Weighted LDA: " + numTopics + " topics");
	}
	
	public Alphabet getAlphabet() { return alphabet; }
	public LabelAlphabet getTopicAlphabet() { return topicAlphabet; }
	public int getNumTopics() { return numTopics; }
	public ArrayList<TopicAssignment> getData() { return data; }
	
	public void setTopicDisplay(int interval, int n) {
		this.showTopicsInterval = interval;
		this.wordsPerTopic = n;
	}

	public void setRandomSeed(int seed) {
		random = new Randoms(seed);
	}
	
	public int[][] getTypeTopicCounts() { return typeTopicCounts; }
	public int[] getTopicTotals() { return tokensPerTopic; }

	public void addInstances (InstanceList training) {

		alphabet = training.getDataAlphabet();
		numTypes = alphabet.size();
		betaSum = beta * numTypes;
		
		typeTopicCounts = new int[numTypes][numTopics];

		typeTopicWeights = new double[numTypes][numTopics];
		totalTopicWeights = new double[numTopics];

		for (int type = 0; type < numTypes; type++) {
			Arrays.fill(typeTopicWeights[type], beta);
		}
		Arrays.fill(totalTopicWeights, betaSum);

		int doc = 0;

		for (Instance instance : training) {
			doc++;

			FeatureSequence tokenSequence = (FeatureSequence) instance.getData();
			LabelSequence topicSequence =
				new LabelSequence(topicAlphabet, new int[ tokenSequence.size() ]);

			TopicAssignment t = new TopicAssignment (instance, topicSequence);
			data.add (t);
		}

	}

	public void readTypeTypeWeights (File weightsFile) throws Exception {
		
		typeTypeWeights = new IntDoubleHashMap[numTypes];

		logger.info("num types: " + numTypes);

		for (int type = 0; type < numTypes; type++) {
			typeTypeWeights[type] = new IntDoubleHashMap();
			typeTypeWeights[type].put(type, 1.0);
		}

		int sourceType = 0; // java complains if we don't initialize
		boolean sourceWordValid = true;
		
		BufferedReader reader = new BufferedReader(new FileReader(weightsFile));
		String line;
		while ((line = reader.readLine()) != null) {

			String[] fields = line.split("\t");

			double sum = 0.0;
			for (int i=1; i < fields.length; i += 2) {
				sum += Double.parseDouble(fields[i]);
			}

			sourceType = alphabet.lookupIndex( fields[0] );
			typeTypeWeights[sourceType].put(sourceType, Double.parseDouble(fields[1]) / sum);

			int i = 2;
			while (i < fields.length) {
				int targetType = alphabet.lookupIndex(fields[i]);
				typeTypeWeights[sourceType].put(targetType, Double.parseDouble(fields[i+1]) / sum);
				i += 2;
			}
		}
	}

	public void sample (int iterations, boolean shouldInitialize, int docCycleCount) throws IOException {

		for (int iteration = 1; iteration <= iterations; iteration++) {

			long iterationStart = System.currentTimeMillis();

			// Loop over every document in the corpus
			for (int doc = 0; doc < data.size(); doc++) {
				//			for (int doc = 0; doc < 5000; doc++) {
				FeatureSequence tokenSequence =
					(FeatureSequence) data.get(doc).instance.getData();
				LabelSequence topicSequence =
					(LabelSequence) data.get(doc).topicSequence;
				
				// Run the sampler in initialization mode for 
				//  the first iteration, and show debugging info 
				//  for the first document.
				sampleTopicsForOneDoc (tokenSequence, topicSequence, shouldInitialize && iteration == 1, false);
				
				for (int i = 1; i < docCycleCount; i++) {
					sampleTopicsForOneDoc (tokenSequence, topicSequence, false, false);
				}

				/*
				if ((doc+1) % 1000 == 0) {
					System.out.println(doc + 1);
				}
				*/
			}
		
			long elapsedMillis = System.currentTimeMillis() - iterationStart;
			logger.info(iteration + "\t" + elapsedMillis + "ms\t");

			// Occasionally print more information
			if (showTopicsInterval != 0 && iteration % showTopicsInterval == 0) {
				logger.info("<" + iteration + ">\n" +
							topWords (wordsPerTopic));
			}

		}
	}
	
	protected void sampleTopicsForOneDoc (FeatureSequence tokenSequence,
										  FeatureSequence topicSequence, 
										  boolean initializing, boolean debugging) {

		int[] oneDocTopics = topicSequence.getFeatures();

		int[] currentTypeTopicCounts;
		double[] currentTypeTopicWeights;

		int type, oldTopic, newTopic;
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		int[] localTopicCounts = new int[numTopics];

		if (! initializing) {
			//		populate topic counts
			for (int position = 0; position < docLength; position++) {
				localTopicCounts[oneDocTopics[position]]++;
			}
		}

		double score, sum;
		double[] topicTermScores = new double[numTopics];

		//	Iterate over the positions (words) in the document 
		for (int position = 0; position < docLength; position++) {
			type = tokenSequence.getIndexAtPosition(position);
			oldTopic = oneDocTopics[position];

			IntDoubleHashMap typeFactors = typeTypeWeights[type];

			// Grab the relevant row from our two-dimensional array
			currentTypeTopicCounts = typeTopicCounts[type];
			currentTypeTopicWeights = typeTopicWeights[type];

			if (! initializing) {

				//	Remove this token from all counts. 
				localTopicCounts[oldTopic]--;
				tokensPerTopic[oldTopic]--;
				assert(tokensPerTopic[oldTopic] >= 0);
				currentTypeTopicCounts[oldTopic]--;

				int typeCount, otherTypeCount;
				typeCount = currentTypeTopicCounts[oldTopic]; // already incremented
				
				for (IntDoubleCursor keyVal: typeFactors) {
					int otherType = keyVal.key;
					double factor = keyVal.value;

					typeTopicWeights[otherType][oldTopic] -= factor;
					totalTopicWeights[oldTopic] -= factor;
				}
			}

			// Now calculate and add up the scores for each topic for this word
			sum = 0.0;
				
			// Here's where the math happens! Note that overall performance is 
			//  dominated by what you do in this loop.
			for (int topic = 0; topic < numTopics; topic++) {
				score =
					(alpha + localTopicCounts[topic]) *
					(currentTypeTopicWeights[topic] / totalTopicWeights[topic]);
				sum += score;
				topicTermScores[topic] = score;
				

				if (debugging && type == 68) {System.out.println(type + "\t" + topic + "\t" + localTopicCounts[topic] + "\t" + currentTypeTopicCounts[topic] + "\t" + currentTypeTopicWeights[topic] + "\t" + tokensPerTopic[topic] + "\t" + sum);}
			}
			
			// Choose a random point between 0 and the sum of all topic scores
			double sample = random.nextUniform() * sum;

			if (debugging) {
				System.out.println("sample " + sample + " / " + sum);
			}

			// Figure out which topic contains that point
			newTopic = -1;
			while (sample > 0.0) {
				newTopic++;
				sample -= topicTermScores[newTopic];
			}

			// Make sure we actually sampled a topic
			if (debugging || newTopic == -1) {

				/*
				System.out.println(alphabet.lookupObject(type));
				for (int topic = 0; topic < numTopics; topic++) {
					System.out.println("(" + alpha + " + " + localTopicCounts[topic] + ") * " +
									   "(" + currentTypeTopicWeights[topic] + " / " + totalTopicWeights[topic] + ") = " +
									   topicTermScores[topic]);
				}
				*/
				
				//throw new IllegalStateException ("WeightedTopicModel: New topic not sampled.");
			}

			// Put that new topic into the counts
			oneDocTopics[position] = newTopic;
			localTopicCounts[newTopic]++;
			tokensPerTopic[newTopic]++;
			currentTypeTopicCounts[newTopic]++;

			//System.out.println(newTopic + "\t" + alphabet.lookupObject(type));

			int typeCount, otherTypeCount;
			typeCount = currentTypeTopicCounts[newTopic]; // already incremented

			for (IntDoubleCursor keyVal: typeFactors) {
				int otherType = keyVal.key;
				double factor = keyVal.value;
				
				typeTopicWeights[otherType][newTopic] += factor;
				totalTopicWeights[newTopic] += factor;
			}


		}
	}
	
	/*
	public double modelLogLikelihood() {
		double logLikelihood = 0.0;
		int nonZeroTopics;

		// The likelihood of the model is a combination of a 
		// Dirichlet-multinomial for the words in each topic
		// and a Dirichlet-multinomial for the topics in each
		// document.

		// The likelihood function of a dirichlet multinomial is
		//	 Gamma( sum_i alpha_i )	 prod_i Gamma( alpha_i + N_i )
		//	prod_i Gamma( alpha_i )	  Gamma( sum_i (alpha_i + N_i) )

		// So the log likelihood is 
		//	logGamma ( sum_i alpha_i ) - logGamma ( sum_i (alpha_i + N_i) ) + 
		//	 sum_i [ logGamma( alpha_i + N_i) - logGamma( alpha_i ) ]

		// Do the documents first

		int[] topicCounts = new int[numTopics];
		double[] topicLogGammas = new double[numTopics];
		int[] docTopics;

		for (int topic=0; topic < numTopics; topic++) {
			topicLogGammas[ topic ] = Dirichlet.logGamma( alpha );
		}
	
		for (int doc=0; doc < data.size(); doc++) {
			LabelSequence topicSequence = (LabelSequence) data.get(doc).topicSequence;

			docTopics = topicSequence.getFeatures();

			for (int token=0; token < docTopics.length; token++) {
				topicCounts[ docTopics[token] ]++;
			}

			for (int topic=0; topic < numTopics; topic++) {
				if (topicCounts[topic] > 0) {
					logLikelihood += (Dirichlet.logGamma(alpha + topicCounts[topic]) -
									  topicLogGammas[ topic ]);
				}
			}

			// subtract the (count + parameter) sum term
			logLikelihood -= Dirichlet.logGamma(alphaSum + docTopics.length);

			Arrays.fill(topicCounts, 0);
		}
	
		// add the parameter sum term
		logLikelihood += data.size() * Dirichlet.logGamma(alphaSum);

		// And the topics

		// Count the number of type-topic pairs
		int nonZeroTypeTopics = 0;

		for (int type=0; type < numTypes; type++) {
			// reuse this array as a pointer

			topicCounts = typeTopicCounts[type];

			for (int topic = 0; topic < numTopics; topic++) {
				if (topicCounts[topic] == 0) { continue; }
				
				nonZeroTypeTopics++;
				logLikelihood += Dirichlet.logGamma(beta + topicCounts[topic]);

				if (Double.isNaN(logLikelihood)) {
					System.out.println(topicCounts[topic]);
					System.exit(1);
				}
			}
		}
	
		for (int topic=0; topic < numTopics; topic++) {
			logLikelihood -= 
				Dirichlet.logGamma( (beta * numTopics) +
											tokensPerTopic[ topic ] );
			if (Double.isNaN(logLikelihood)) {
				System.out.println("after topic " + topic + " " + tokensPerTopic[ topic ]);
				System.exit(1);
			}

		}
	
		logLikelihood += 
			(Dirichlet.logGamma(beta * numTopics)) -
			(Dirichlet.logGamma(beta) * nonZeroTypeTopics);

		if (Double.isNaN(logLikelihood)) {
			System.out.println("at the end");
			System.exit(1);
		}


		return logLikelihood;
	}
	*/

	// 
	// Methods for displaying and saving results
	//

	public String topWords (int numWords) {

		StringBuilder output = new StringBuilder();

		IDSorter[] sortedWords = new IDSorter[numTypes];

		for (int topic = 0; topic < numTopics; topic++) {
			for (int type = 0; type < numTypes; type++) {
				sortedWords[type] = new IDSorter(type, typeTopicCounts[type][topic]);
			}

			Arrays.sort(sortedWords);
			
			output.append(topic + "\t" + tokensPerTopic[topic] + "\t" + formatter.format(totalTopicWeights[topic]));
			for (int i=0; i < numWords; i++) {
				output.append(alphabet.lookupObject(sortedWords[i].getID()) + " ");
			}
			output.append("\n");
		}

		return output.toString();
	}

	public MarginalProbEstimator getEstimator() {
		// The type-topic counts are "dense", meaning that the index of 
		//  the array element determines its topic. The marginal estimator
		//  uses the sparse "bit encoded" arrays used in ParallelTopicModel,
		//  so we need to convert to that format.

		int topicMask, topicBits;

		if (Integer.bitCount(numTopics) == 1) {
			// exact power of 2
			topicMask = numTopics - 1;
			topicBits = Integer.bitCount(topicMask);
		}
		else {
			// otherwise add an extra bit
			topicMask = Integer.highestOneBit(numTopics) * 2 - 1;
			topicBits = Integer.bitCount(topicMask);
		}

		int[][] sparseTypeTopicCounts = new int[numTypes][];

		for (int type = 0; type < numTypes; type++) {
			
			int[] currentTypeTopicCounts = typeTopicCounts[type];

			// First figure out how many entries we have
			int numNonZeros = 0;
			for (int topic = 0; topic < numTopics; topic++) {
				if (currentTypeTopicCounts[topic] > 0) {
					numNonZeros ++;
				}
			}

			// Allocate the sparse array
			int[] sparseCounts = new int[numNonZeros];

			// And fill it, keeping the array in descending order

			for (int topic = 0; topic < numTopics; topic++) {
				if (currentTypeTopicCounts[topic] > 0) {
					int value = (currentTypeTopicCounts[topic] << topicBits) + topic;
					int i = 0;
					
					// Move values along. Note that java arrays are 
					//  all zeros at initialization.
					while (sparseCounts[i] > value) {
						i++;
					}
					// We've now found where to insert, push along any other values
					while (i < sparseCounts.length && value > sparseCounts[i]) {
						int temp = sparseCounts[i];
						sparseCounts[i] = value;
						value = temp;
						i++;
					}
				}
			}

			// Now add it to the array of arrays
			sparseTypeTopicCounts[type] = sparseCounts;
			
		}

		double[] alphas = new double[ numTopics ];
		Arrays.fill(alphas, alpha);
		return new MarginalProbEstimator(numTopics, alphas, alphaSum, beta,
										 sparseTypeTopicCounts, tokensPerTopic);
	}

	public void printState (File f) throws IOException {
		PrintStream out =
			new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(f))));
		printState(out);
		out.close();
	}
	
	public void printState (PrintStream stream) {

		stream.println ("#doc source pos typeindex type topic");

		for (int doc = 0; doc < data.size(); doc++) {
		//for (int doc = 0; doc < 5000; doc++) {
			FeatureSequence tokenSequence =	(FeatureSequence) data.get(doc).instance.getData();
			LabelSequence topicSequence =	(LabelSequence) data.get(doc).topicSequence;

			String source = "NA";

			StringBuilder out = new StringBuilder();
				
			for (int position = 0; position < topicSequence.getLength(); position++) {
				int type = tokenSequence.getIndexAtPosition(position);
				int topic = topicSequence.getIndexAtPosition(position);
				
				out.append(doc); out.append(' ');
				out.append(source); out.append(' '); 
				out.append(position); out.append(' ');
				out.append(type); out.append(' ');
				out.append(alphabet.lookupObject(type)); out.append(' ');
				out.append(topic);
				out.append("\n");
			}

			stream.print(out.toString());
		}
	}
	
	public static void main (String[] args) throws Exception {

		CommandOption.setSummary (WeightedTopicModel.class,
								  "Train topics with weights between word types encoded in the prior");
		CommandOption.process (WeightedTopicModel.class, args);

		InstanceList training = InstanceList.load (new File(inputFile.value));

		Randoms random = null;
		if (randomSeedOption.value != 0) {
			random = new Randoms(randomSeedOption.value);
		}
		else {
			random = new Randoms();
		}

		WeightedTopicModel lda =
			new WeightedTopicModel (numTopicsOption.value, alphaOption.value, betaOption.value, random);
		lda.addInstances(training);
		lda.readTypeTypeWeights(new File(weightsFile.value));

		int docCycleCount = 1;

		for (int epoch = 1; epoch <= numEpochsOption.value; epoch++) {
			lda.sample(numIterationsOption.value, epoch == 1, docCycleCount);
			
			if (stateFile.wasInvoked()) {
				lda.printState(new File(stateFile.value + "." + epoch));
			}
			if (evaluatorFilename.wasInvoked()) {
				try {
					ObjectOutputStream oos = 
						new ObjectOutputStream(new FileOutputStream(evaluatorFilename.value + "." + epoch));
					oos.writeObject(lda.getEstimator());
					oos.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

	}
	
}
