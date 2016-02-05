package cc.mallet.topics;

import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

import java.io.*;

import cc.mallet.types.*;
import cc.mallet.util.*;
import cc.mallet.pipe.iterator.DBInstanceIterator;


/**
 * LabeledLDA
 * @author David Mimno
 */

public class LabeledLDA implements Serializable {

	protected static Logger logger = MalletLogger.getLogger(LabeledLDA.class.getName());
	
	static cc.mallet.util.CommandOption.String inputFile =
		new cc.mallet.util.CommandOption.String(LabeledLDA.class, "input", "FILENAME", true, null,
		  "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence, not FeatureVector", null);
	
	static cc.mallet.util.CommandOption.String outputPrefix = 
		new cc.mallet.util.CommandOption.String(LabeledLDA.class, "output-prefix", "STRING", true, null,
		  "The prefix for output files (sampling states, parameters, etc)  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String inputModelFilename = new CommandOption.String(LabeledLDA.class, "input-model", "FILENAME", true, null,
		 "The filename from which to read the binary topic model. The --input option is ignored. " +
		 "By default this is null, indicating that no file will be read.", null);

	static CommandOption.String inputStateFilename = new CommandOption.String(LabeledLDA.class, "input-state", "FILENAME", true, null,
		 "The filename from which to read the gzipped Gibbs sampling state created by --output-state. " +
		 "The original input file must be included, using --input. " + 
		 "By default this is null, indicating that no file will be read.", null);

	// Model output options

	static CommandOption.String outputModelFilename =
		new CommandOption.String(LabeledLDA.class, "output-model", "FILENAME", true, null,
		 "The filename in which to write the binary topic model at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String stateFile =
		new CommandOption.String(LabeledLDA.class, "output-state", "FILENAME", true, null,
		 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Integer outputModelInterval = 
		new CommandOption.Integer(LabeledLDA.class, "output-model-interval", "INTEGER", true, 0,
		 "The number of iterations between writing the model (and its Gibbs sampling state) to a binary file.  " +
		 "You must also set the --output-model to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Integer outputStateInterval =
		new CommandOption.Integer(LabeledLDA.class, "output-state-interval", "INTEGER", true, 0,
         "The number of iterations between writing the sampling state to a text file.  " +
         "You must also set the --output-state to use this option, whose argument will be the prefix of the filenames.", null);

	static CommandOption.String inferencerFilename =
		new CommandOption.String(LabeledLDA.class, "inferencer-filename", "FILENAME", true, null,
         "A topic inferencer applies a previously trained topic model to new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String evaluatorFilename = 
		new CommandOption.String(LabeledLDA.class, "evaluator-filename", "FILENAME", true, null,
         "A held-out likelihood evaluator for new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicKeysFile = 
		new CommandOption.String(LabeledLDA.class, "output-topic-keys", "FILENAME", true, null,
         "The filename in which to write the top words for each topic and any Dirichlet parameters.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Integer numTopWords = new CommandOption.Integer(LabeledLDA.class, "num-top-words", "INTEGER", true, 20,
		 "The number of most probable words to print for each topic after model estimation.", null);

	static CommandOption.Integer showTopicsIntervalOption = new CommandOption.Integer(LabeledLDA.class, "show-topics-interval", "INTEGER", true, 50,
		 "The number of iterations between printing a brief summary of the topics so far.", null);

	static CommandOption.String topicWordWeightsFile = new CommandOption.String(LabeledLDA.class, "topic-word-weights-file", "FILENAME", true, null,
         "The filename in which to write unnormalized weights for every topic and word type.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String wordTopicCountsFile = new CommandOption.String(LabeledLDA.class, "word-topic-counts-file", "FILENAME", true, null,
         "The filename in which to write a sparse representation of topic-word assignments.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String diagnosticsFile = new CommandOption.String(LabeledLDA.class, "diagnostics-file", "FILENAME", true, null,
         "The filename in which to write measures of topic quality, in XML format.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicReportXMLFile = new CommandOption.String(LabeledLDA.class, "xml-topic-report", "FILENAME", true, null,
         "The filename in which to write the top words for each topic and any Dirichlet parameters in XML format.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicPhraseReportXMLFile = new CommandOption.String(LabeledLDA.class, "xml-topic-phrase-report", "FILENAME", true, null,
		 "The filename in which to write the top words and phrases for each topic and any Dirichlet parameters in XML format.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicDocsFile = new CommandOption.String(LabeledLDA.class, "output-topic-docs", "FILENAME", true, null,
		 "The filename in which to write the most prominent documents for each topic, at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Integer numTopDocs = new CommandOption.Integer(LabeledLDA.class, "num-top-docs", "INTEGER", true, 100,
		 "When writing topic documents with --output-topic-docs, " +
		 "report this number of top documents.", null);

	static CommandOption.String docTopicsFile = new CommandOption.String(LabeledLDA.class, "output-doc-topics", "FILENAME", true, null,
		 "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Double docTopicsThreshold = new CommandOption.Double(LabeledLDA.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
		 "When writing topic proportions per document with --output-doc-topics, " +
		 "do not print topics with proportions less than this threshold value.", null);

	static CommandOption.Integer docTopicsMax = new CommandOption.Integer(LabeledLDA.class, "doc-topics-max", "INTEGER", true, -1,
		 "When writing topic proportions per document with --output-doc-topics, " +
		 "do not print more than INTEGER number of topics.  "+
		 "A negative value indicates that all topics should be printed.", null);

	// Model parameters

	static CommandOption.Integer numIterationsOption =
		new CommandOption.Integer(LabeledLDA.class, "num-iterations", "INTEGER", true, 1000,
		 "The number of iterations of Gibbs sampling.", null);

	static CommandOption.Boolean noInference =
		new CommandOption.Boolean(LabeledLDA.class, "no-inference", "true|false", false, false,
		 "Do not perform inference, just load a saved model and create a report. Equivalent to --num-iterations 0.", null);

	static CommandOption.Integer randomSeed =
		new CommandOption.Integer(LabeledLDA.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	// Hyperparameters

	static CommandOption.Double alphaOption =
		new CommandOption.Double(LabeledLDA.class, "alpha", "DECIMAL", true, 0.1,
		 "Alpha parameter: smoothing over doc topic distribution (NOT the sum over topics).",null);

	static CommandOption.Double betaOption =
		new CommandOption.Double(LabeledLDA.class, "beta", "DECIMAL", true, 0.01,
		 "Beta parameter: smoothing over word distributions.",null);


	// the training instances and their topic assignments
	protected ArrayList<TopicAssignment> data;  

	// the alphabet for the input data
	protected Alphabet alphabet; 

	// this alphabet stores the string meanings of the labels/topics
	protected Alphabet labelAlphabet;

	// the alphabet for the topics
	protected LabelAlphabet topicAlphabet; 
	
	// The number of topics requested
	protected int numTopics;

	// The size of the vocabulary
	protected int numTypes;

	// Prior parameters
	protected double alpha;	 // Dirichlet(alpha,alpha,...) is the distribution over topics
	protected double beta;   // Prior on per-topic multinomial distribution over words
	protected double betaSum;
	public static final double DEFAULT_BETA = 0.01;
	
	// An array to put the topic counts for the current document. 
	// Initialized locally below.  Defined here to avoid
	// garbage collection overhead.
	protected int[] oneDocTopicCounts; // indexed by <document index, topic index>

	// Statistics needed for sampling.
	protected int[][] typeTopicCounts; // indexed by <feature index, topic index>
	protected int[] tokensPerTopic; // indexed by <topic index>

	public int numIterations = 1000;

	public int showTopicsInterval = 50;
	public int wordsPerTopic = 10;
	
	protected Randoms random;
	protected boolean printLogLikelihood = false;
	
	public LabeledLDA (double alpha, double beta) {
		this.data = new ArrayList<TopicAssignment>();

		this.alpha = alpha;
		this.beta = beta;
		this.random = new Randoms();
		
		logger.info("Labeled LDA");
	}
	
	public Alphabet getAlphabet() { return alphabet; }
	public LabelAlphabet getTopicAlphabet() { return topicAlphabet; }
	public ArrayList<TopicAssignment> getData() { return data; }
	
	public void setTopicDisplay(int interval, int n) {
		this.showTopicsInterval = interval;
		this.wordsPerTopic = n;
	}

	public void setRandomSeed(int seed) {
		random = new Randoms(seed);
	}
	
	public void setNumIterations (int numIterations) {
		this.numIterations = numIterations;
	}

	public int[][] getTypeTopicCounts() { return typeTopicCounts; }
	public int[] getTopicTotals() { return tokensPerTopic; }

	public void addInstances (InstanceList training) {

		alphabet = training.getDataAlphabet();
		numTypes = alphabet.size();
		betaSum = beta * numTypes;
		
		// We have one topic for every possible label.
		labelAlphabet = training.getTargetAlphabet();
		numTopics = labelAlphabet.size();
		oneDocTopicCounts = new int[numTopics];
		tokensPerTopic = new int[numTopics];
		typeTopicCounts = new int[numTypes][numTopics];

		topicAlphabet = AlphabetFactory.labelAlphabetOfSize(numTopics);

		int doc = 0;

		for (Instance instance : training) {
			doc++;

			FeatureSequence tokens = (FeatureSequence) instance.getData();
			FeatureVector labels = (FeatureVector) instance.getTarget();

			LabelSequence topicSequence =
				new LabelSequence(topicAlphabet, new int[ tokens.size() ]);
			
			int[] topics = topicSequence.getFeatures();
			for (int position = 0; position < tokens.size(); position++) {

				int topic = labels.indexAtLocation(random.nextInt(labels.numLocations()));

				topics[position] = topic;
				tokensPerTopic[topic]++;
				
				int type = tokens.getIndexAtPosition(position);
				typeTopicCounts[type][topic]++;
			}

			TopicAssignment t = new TopicAssignment (instance, topicSequence);
			data.add (t);
		}

	}
	
	public void initializeFromState(File stateFile) throws IOException {
		String line;
		String[] fields;

		BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(stateFile))));
		line = reader.readLine();

		// Skip some lines starting with "#" that describe the format and specify hyperparameters
		while (line.startsWith("#")) {
			line = reader.readLine();
		}
		
		fields = line.split(" ");

		for (TopicAssignment document: data) {
			FeatureSequence tokens = (FeatureSequence) document.instance.getData();
			FeatureSequence topicSequence =  (FeatureSequence) document.topicSequence;

			int[] topics = topicSequence.getFeatures();
			for (int position = 0; position < tokens.size(); position++) {
				int type = tokens.getIndexAtPosition(position);
				
				if (type == Integer.parseInt(fields[3])) {
					int topic = Integer.parseInt(fields[5]);
					topics[position] = topic;
					
					// This is the difference between the dense type-topic representation used here
					//  and the sparse version used in ParallelTopicModel.
					typeTopicCounts[type][topic]++;
				}
				else {
					System.err.println("instance list and state do not match: " + line);
					throw new IllegalStateException();
				}

				line = reader.readLine();
				if (line != null) {
					fields = line.split(" ");
				}
			}
		}
	}

	public void estimate() throws IOException {

		for (int iteration = 1; iteration <= numIterations; iteration++) {

			long iterationStart = System.currentTimeMillis();

			// Loop over every document in the corpus
			for (int doc = 0; doc < data.size(); doc++) {
				FeatureSequence tokenSequence =
					(FeatureSequence) data.get(doc).instance.getData();
				FeatureVector labels = (FeatureVector) data.get(doc).instance.getTarget();
				LabelSequence topicSequence =
					(LabelSequence) data.get(doc).topicSequence;

				sampleTopicsForOneDoc (tokenSequence, labels, topicSequence);
			}
		
            long elapsedMillis = System.currentTimeMillis() - iterationStart;
			logger.info(iteration + "\t" + elapsedMillis + "ms\t");

			// Occasionally print more information
			if (showTopicsInterval != 0 && iteration % showTopicsInterval == 0) {
				logger.info("<" + iteration + "> Log Likelihood: " + modelLogLikelihood() + "\n" +
							topWords (wordsPerTopic));
			}

		}
	}
	
	protected void sampleTopicsForOneDoc (FeatureSequence tokenSequence,
										  FeatureVector labels,
										  FeatureSequence topicSequence) {

		int[] possibleTopics = labels.getIndices();
		int numLabels = labels.numLocations();

		int[] oneDocTopics = topicSequence.getFeatures();

		int[] currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		int[] localTopicCounts = new int[numTopics];

		//		populate topic counts
		for (int position = 0; position < docLength; position++) {
			localTopicCounts[oneDocTopics[position]]++;
		}

		double score, sum;
		double[] topicTermScores = new double[numLabels];

		//	Iterate over the positions (words) in the document 
		for (int position = 0; position < docLength; position++) {
			type = tokenSequence.getIndexAtPosition(position);
			oldTopic = oneDocTopics[position];

			// Grab the relevant row from our two-dimensional array
			currentTypeTopicCounts = typeTopicCounts[type];

			//	Remove this token from all counts. 
			localTopicCounts[oldTopic]--;
			tokensPerTopic[oldTopic]--;
			assert(tokensPerTopic[oldTopic] >= 0) : "old Topic " + oldTopic + " below 0";
			currentTypeTopicCounts[oldTopic]--;

			// Now calculate and add up the scores for each topic for this word
			sum = 0.0;
			
			// Here's where the math happens! Note that overall performance is 
			//  dominated by what you do in this loop.
			for (int labelPosition = 0; labelPosition < numLabels; labelPosition++) {
				int topic = possibleTopics[labelPosition];
				score =
					(alpha + localTopicCounts[topic]) *
					((beta + currentTypeTopicCounts[topic]) /
					 (betaSum + tokensPerTopic[topic]));
				sum += score;
				topicTermScores[labelPosition] = score;
			}
			
			// Choose a random point between 0 and the sum of all topic scores
			double sample = random.nextUniform() * sum;

			// Figure out which topic contains that point
			int labelPosition = -1;
			while (sample > 0.0) {
				labelPosition++;
				sample -= topicTermScores[labelPosition];
			}

			// Make sure we actually sampled a topic
			if (labelPosition == -1) {
				throw new IllegalStateException ("LabeledLDA: New topic not sampled.");
			}

			newTopic = possibleTopics[labelPosition];

			// Put that new topic into the counts
			oneDocTopics[position] = newTopic;
			localTopicCounts[newTopic]++;
			tokensPerTopic[newTopic]++;
			currentTypeTopicCounts[newTopic]++;
		}
	}
	
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
			FeatureVector labels = (FeatureVector) data.get(doc).instance.getTarget();
			
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

			// add the parameter sum term
			logLikelihood += Dirichlet.logGamma(alpha * labels.numLocations());

			// subtract the (count + parameter) sum term
			logLikelihood -= Dirichlet.logGamma(alpha * labels.numLocations() + docTopics.length);

			Arrays.fill(topicCounts, 0);
		}
	
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

	// 
	// Methods for displaying and saving results
	//

	public String topWords (int numWords) {

		StringBuilder output = new StringBuilder();

		IDSorter[] sortedWords = new IDSorter[numTypes];

		for (int topic = 0; topic < numTopics; topic++) {
			if (tokensPerTopic[topic] == 0) { continue; }
			
			for (int type = 0; type < numTypes; type++) {
				sortedWords[type] = new IDSorter(type, typeTopicCounts[type][topic]);
			}

			Arrays.sort(sortedWords);
			
			output.append(topic + "\t" + labelAlphabet.lookupObject(topic) + "\t" + tokensPerTopic[topic] + "\t");
			for (int i=0; i < numWords; i++) {
				if (sortedWords[i].getWeight() == 0) { break; }
				output.append(alphabet.lookupObject(sortedWords[i].getID()) + " ");
			}
			output.append("\n");
		}

		return output.toString();
	}

		
	// Serialization
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;
	
	public void write (File f) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(f));
			oos.writeObject(this);
			oos.close();
		}
		catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}
	
	public static LabeledLDA read (File f) throws Exception {

		LabeledLDA topicModel = null;

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
		topicModel = (LabeledLDA) ois.readObject();
		ois.close();

		return topicModel;
	}

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);

		// Instance lists
		out.writeObject (data);
		out.writeObject (alphabet);
		out.writeObject (topicAlphabet);

		out.writeInt (numTopics);
		out.writeObject (alpha);
		out.writeDouble (beta);
		out.writeDouble (betaSum);

		out.writeInt(showTopicsInterval);
		out.writeInt(wordsPerTopic);

		out.writeObject(random);
		out.writeBoolean(printLogLikelihood);

		out.writeObject (typeTopicCounts);

		for (int ti = 0; ti < numTopics; ti++) {
			out.writeInt (tokensPerTopic[ti]);
		}
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int featuresLength;
		int version = in.readInt ();

		data = (ArrayList<TopicAssignment>) in.readObject ();
		alphabet = (Alphabet) in.readObject();
		topicAlphabet = (LabelAlphabet) in.readObject();

		numTopics = in.readInt();
		alpha = in.readDouble();
		beta = in.readDouble();
		betaSum = in.readDouble();

		showTopicsInterval = in.readInt();
		wordsPerTopic = in.readInt();

		random = (Randoms) in.readObject();
		printLogLikelihood = in.readBoolean();
		
		int numDocs = data.size();
		this.numTypes = alphabet.size();

		typeTopicCounts = (int[][]) in.readObject();
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++) {
			tokensPerTopic[ti] = in.readInt();
		}
	}

	public static void main (String[] args) throws Exception {

		CommandOption.setSummary (LabeledLDA.class,
                                  "Sample associations between words and labels");
        CommandOption.process (LabeledLDA.class, args);

		LabeledLDA labeledLDA;

		if (inputModelFilename.value != null) {
			labeledLDA = LabeledLDA.read(new File(inputModelFilename.value));
		} 
		else {
			labeledLDA = new LabeledLDA (alphaOption.value, betaOption.value);
		}

		if (randomSeed.value != 0) {
			labeledLDA.setRandomSeed(randomSeed.value);
		}

		if (inputFile.value != null) {
			InstanceList training = null;
			try {
				if (inputFile.value.startsWith("db:")) {
					training = DBInstanceIterator.getInstances(inputFile.value.substring(3));
				}
				else {
					training = InstanceList.load (new File(inputFile.value));
				}
			} catch (Exception e) {
				logger.warning("Unable to restore instance list " +
								   inputFile.value + ": " + e);
				System.exit(1);
			}

			logger.info("Data loaded.");
			
			if (training.size() > 0 &&
				training.get(0) != null) {
				Object data = training.get(0).getData();
				if (! (data instanceof FeatureSequence)) {
					logger.warning("Topic modeling currently only supports feature sequences: use --keep-sequence option when importing data.");
					System.exit(1);
				}
			}

			labeledLDA.addInstances(training);

		}

		if (inputStateFilename.value != null) {
			logger.info("Initializing from saved state.");
			labeledLDA.initializeFromState(new File(inputStateFilename.value));
		}
				
		labeledLDA.setTopicDisplay(showTopicsIntervalOption.value, numTopWords.value);

		labeledLDA.setNumIterations(numIterationsOption.value);
		
		if (! noInference.value()) {
			labeledLDA.estimate();
		}
		
		if (topicKeysFile.value != null) {
			PrintStream out = new PrintStream (new File(topicKeysFile.value));
			out.print(labeledLDA.topWords(numTopWords.value));
			out.close();
		}

		if (outputModelFilename.value != null) {
			assert (labeledLDA != null);
			try {

				ObjectOutputStream oos =
					new ObjectOutputStream (new FileOutputStream (outputModelFilename.value));
				oos.writeObject (labeledLDA);
				oos.close();

			} catch (Exception e) {
				logger.warning("Couldn't write topic model to filename " + outputModelFilename.value);
			}
		}
		
		// I don't want to directly inherit from ParallelTopicModel 
		//  because the two implementations treat the type-topic counts differently.
		// Instead, simulate a standard Parallel Topic Model by copying over 
		//  the appropriate data structures.
		ParallelTopicModel topicModel = new ParallelTopicModel(labeledLDA.topicAlphabet, labeledLDA.alpha * labeledLDA.numTopics, labeledLDA.beta);
		topicModel.data = labeledLDA.data;
		topicModel.alphabet = labeledLDA.alphabet;
		topicModel.numTypes = labeledLDA.numTypes;
		topicModel.betaSum = labeledLDA.betaSum;
		topicModel.buildInitialTypeTopicCounts();
		
		if (diagnosticsFile.value != null) {
			PrintWriter out = new PrintWriter(diagnosticsFile.value);
			TopicModelDiagnostics diagnostics = new TopicModelDiagnostics(topicModel, numTopWords.value);
			out.println(diagnostics.toXML());
			out.close();
		}

		if (topicReportXMLFile.value != null) {
			PrintWriter out = new PrintWriter(topicReportXMLFile.value);
			topicModel.topicXMLReport(out, numTopWords.value);
			out.close();
		}

		if (topicPhraseReportXMLFile.value != null) {
			PrintWriter out = new PrintWriter(topicPhraseReportXMLFile.value);
			topicModel.topicPhraseXMLReport(out, numTopWords.value);
			out.close();
		}

		if (stateFile.value != null && outputStateInterval.value == 0) {
			topicModel.printState (new File(stateFile.value));
		}

		if (topicDocsFile.value != null) {
			PrintWriter out = new PrintWriter (new FileWriter ((new File(topicDocsFile.value))));
			topicModel.printTopicDocuments(out, numTopDocs.value);
			out.close();
		}

		if (docTopicsFile.value != null) {
			PrintWriter out = new PrintWriter (new FileWriter ((new File(docTopicsFile.value))));
			if (docTopicsThreshold.value == 0.0) {
				topicModel.printDenseDocumentTopics(out);
			}
			else {
				topicModel.printDocumentTopics(out, docTopicsThreshold.value, docTopicsMax.value);
			}
			out.close();
		}

		if (topicWordWeightsFile.value != null) {
			topicModel.printTopicWordWeights(new File (topicWordWeightsFile.value));
		}

		if (wordTopicCountsFile.value != null) {
			topicModel.printTypeTopicCounts(new File (wordTopicCountsFile.value));
		}

		if (inferencerFilename.value != null) {
			try {
				ObjectOutputStream oos = 
					new ObjectOutputStream(new FileOutputStream(inferencerFilename.value));
				oos.writeObject(topicModel.getInferencer());
				oos.close();
			} catch (Exception e) {
				logger.warning("Couldn't create inferencer: " + e.getMessage());
			}
					
		}

		if (evaluatorFilename.value != null) {
			try {
				ObjectOutputStream oos = 
					new ObjectOutputStream(new FileOutputStream(evaluatorFilename.value));
				oos.writeObject(topicModel.getProbEstimator());
				oos.close();
			} catch (Exception e) {
				logger.warning("Couldn't create evaluator: " + e.getMessage());
			}				
		}
	}	
}
