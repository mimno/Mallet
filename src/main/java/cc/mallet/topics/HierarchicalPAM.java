package cc.mallet.topics;

import cc.mallet.types.*;
import cc.mallet.util.*;

import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;

import java.util.logging.*;

/**
 * Hierarchical PAM, where each node in the DAG has a distribution over all topics on the 
 *  next level and one additional "node-specific" topic.
 * @author David Mimno
 */

public class HierarchicalPAM {
	
	protected static Logger logger = MalletLogger.getLogger(HierarchicalPAM.class.getName());

	static CommandOption.String inputFile = new CommandOption.String
		(HierarchicalPAM.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String stateFile = new CommandOption.String
		(HierarchicalPAM.class, "output-state", "FILENAME", true, null,
		 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Double superTopicBalanceOption = new CommandOption.Double
		(HierarchicalPAM.class, "super-topic-balance", "DECIMAL", true, 1.0,
		 "Weight (in \"words\") of the shared distribution over super-topics, relative to the document-specific distribution", null);
	
	static CommandOption.Double subTopicBalanceOption = new CommandOption.Double
		(HierarchicalPAM.class, "sub-topic-balance", "DECIMAL", true, 1.0,
		 "Weight (in \"words\") of the shared distribution over sub-topics for each super-topic, relative to the document-specific distribution", null);
	
	static CommandOption.Integer numSuperTopicsOption = new CommandOption.Integer
		(HierarchicalPAM.class, "num-super-topics", "INTEGER", true, 10,
		 "The number of super-topics", null);
	
	static CommandOption.Integer numSubTopicsOption = new CommandOption.Integer
		(HierarchicalPAM.class, "num-sub-topics", "INTEGER", true, 20,
		 "The number of sub-topics", null);
	
    public static final int NUM_LEVELS = 3;

    // Constants to determine the level of the output multinomial
    public static final int ROOT_TOPIC = 0;
    public static final int SUPER_TOPIC = 1;
    public static final int SUB_TOPIC = 2;    

    // Parameters
    int numSuperTopics; // Number of topics to be fit
    int numSubTopics;

	// This parameter controls the balance between
	//  the local document counts and the global distribution
	//   over super-topics.
	double superTopicBalance = 1.0;

	// This parameter is the smoothing on that global distribution
	double superTopicSmoothing = 1.0;

	// ... and the same for sub-topics.
	double subTopicBalance = 1.0;
	double subTopicSmoothing = 1.0;

	// Prior on per-topic multinomial distribution over words
    double beta;
    double betaSum;

    // Data
    InstanceList instances;  // the data field of the instances is expected to hold a FeatureSequence
    int numTypes;
    int numTokens;

    // Gibbs sampling state
    //  (these could be shorts, or we could encode both in one int)
    int[][] superTopics; // indexed by <document index, sequence index>
    int[][] subTopics; // indexed by <document index, sequence index>
    
    // Per-document state variables
    int[][] superSubCounts; // # of words per <super, sub>
    int[] superCounts; // # of words per <super>
    double[] superWeights; // the component of the Gibbs update that depends on super-topics
    double[] subWeights;   // the component of the Gibbs update that depends on sub-topics
    double[][] superSubWeights; // unnormalized sampling distribution
    double[] cumulativeSuperWeights; // a cache of the cumulative weight for each super-topic

	// Document frequencies used for "minimal path" hierarchical Dirichlets
	int[] superTopicDocumentFrequencies;
	int[][] superSubTopicDocumentFrequencies;

	// ... and their sums
	int sumDocumentFrequencies;
	int[] sumSuperTopicDocumentFrequencies;
	// [Note that this last is not the same as superTopicDocumentFrequencies]

	// Cached priors
	double[] superTopicPriorWeights;
	double[][] superSubTopicPriorWeights;
    
    // Per-word type state variables
    int[][] typeTopicCounts; // indexed by <feature index, topic index>
    int[] tokensPerTopic; // indexed by <topic index>

    int[] tokensPerSuperTopic; // indexed by <topic index>
    int[][] tokensPerSuperSubTopic;

    Runtime runtime;
    NumberFormat formatter;
    
    public HierarchicalPAM (int superTopics, int subTopics, double superTopicBalance, double subTopicBalance) {
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);
	
		this.superTopicBalance = superTopicBalance;
		this.subTopicBalance = subTopicBalance;

		this.numSuperTopics = superTopics;
		this.numSubTopics = subTopics;

		superTopicDocumentFrequencies = new int[numSuperTopics + 1];
		superSubTopicDocumentFrequencies = new int[numSuperTopics + 1][numSubTopics + 1];
		sumSuperTopicDocumentFrequencies = new int[numSuperTopics];

		this.beta = 0.01; // We can't calculate betaSum until we know how many word types...
	
		runtime = Runtime.getRuntime();
    }
    
    public void estimate (InstanceList documents, InstanceList testing,
						  int numIterations, int showTopicsInterval,
						  int outputModelInterval, int optimizeInterval, String outputModelFilename,
						  Randoms r) {

		instances = documents;
		numTypes = instances.getDataAlphabet().size ();
		int numDocs = instances.size();
		superTopics = new int[numDocs][];
		subTopics = new int[numDocs][];

		// Allocate several arrays for use within each document
		//  to cut down memory allocation and garbage collection time

		superSubCounts = new int[numSuperTopics + 1][numSubTopics + 1];
		superCounts = new int[numSuperTopics + 1];
		superWeights = new double[numSuperTopics + 1];
		subWeights = new double[numSubTopics];
		superSubWeights = new double[numSuperTopics + 1][numSubTopics + 1];
		cumulativeSuperWeights = new double[numSuperTopics];

		typeTopicCounts = new int[numTypes][1 + numSuperTopics + numSubTopics];
		tokensPerTopic = new int[1 + numSuperTopics + numSubTopics];

		tokensPerSuperTopic = new int[numSuperTopics + 1];
		tokensPerSuperSubTopic = new int[numSuperTopics + 1][numSubTopics + 1];

		betaSum = beta * numTypes;

		long startTime = System.currentTimeMillis();
	
		int maxTokens = 0;

		// Initialize with random assignments of tokens to topics
		// and finish allocating this.topics and this.tokens

		int superTopic, subTopic, seqLen;

		for (int doc = 0; doc < numDocs; doc++) {

			int[] localTokensPerSuperTopic = new int[numSuperTopics + 1];
			int[][] localTokensPerSuperSubTopic = new int[numSuperTopics + 1][numSubTopics + 1];

			FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();

			seqLen = fs.getLength();
			if (seqLen > maxTokens) { 
				maxTokens = seqLen;
			}

			numTokens += seqLen;
			superTopics[doc] = new int[seqLen];
			subTopics[doc] = new int[seqLen];

			// Randomly assign tokens to topics
			for (int position = 0; position < seqLen; position++) {

				// Random super-topic
				superTopic = r.nextInt(numSuperTopics);

				// Random sub-topic
				subTopic = r.nextInt(numSubTopics);

				int level = r.nextInt(NUM_LEVELS);
		
				if (level == ROOT_TOPIC) {
					superTopics[doc][position] = numSuperTopics;
					subTopics[doc][position] = numSubTopics;
					typeTopicCounts[ fs.getIndexAtPosition(position) ][0]++;
					tokensPerTopic[0]++;
					tokensPerSuperTopic[numSuperTopics]++;
					tokensPerSuperSubTopic[numSuperTopics][numSubTopics]++;

					if (localTokensPerSuperTopic[numSuperTopics] == 0) {
						superTopicDocumentFrequencies[numSuperTopics]++;
						sumDocumentFrequencies++;
					}
					localTokensPerSuperTopic[numSuperTopics]++;

				}
				else if (level == SUPER_TOPIC) {
					superTopics[doc][position] = superTopic;
					subTopics[doc][position] = numSubTopics;
					typeTopicCounts[ fs.getIndexAtPosition(position) ][1 + superTopic]++;
					tokensPerTopic[1 + superTopic]++;
					tokensPerSuperTopic[superTopic]++;
					tokensPerSuperSubTopic[superTopic][numSubTopics]++;

					if (localTokensPerSuperTopic[superTopic] == 0) {
						superTopicDocumentFrequencies[superTopic]++;
						sumDocumentFrequencies++;
					}
					localTokensPerSuperTopic[superTopic]++;

					if (localTokensPerSuperSubTopic[superTopic][numSubTopics] == 0) {
                        superSubTopicDocumentFrequencies[superTopic][numSubTopics]++;
						sumSuperTopicDocumentFrequencies[superTopic]++;
                    }
                    localTokensPerSuperSubTopic[superTopic][numSubTopics]++;
				}
				else {
					superTopics[doc][position] = superTopic;
					subTopics[doc][position] = subTopic;
					typeTopicCounts[ fs.getIndexAtPosition(position) ][ 1 + numSuperTopics + subTopic]++;
					tokensPerTopic[1 + numSuperTopics + subTopic]++;
					tokensPerSuperTopic[superTopic]++;
					tokensPerSuperSubTopic[superTopic][subTopic]++;

					if (localTokensPerSuperTopic[superTopic] == 0) {
						superTopicDocumentFrequencies[superTopic]++;
						sumDocumentFrequencies++;
					}
					localTokensPerSuperTopic[superTopic]++;

					if (localTokensPerSuperSubTopic[superTopic][subTopic] == 0) {
						superSubTopicDocumentFrequencies[superTopic][subTopic]++;
						sumSuperTopicDocumentFrequencies[superTopic]++;
					}
					localTokensPerSuperSubTopic[superTopic][subTopic]++;
				}
			}
		}

		// Initialize cached priors

		superTopicPriorWeights = new double[ numSuperTopics + 1 ];
		superSubTopicPriorWeights = new double[ numSuperTopics ][ numSubTopics + 1 ];
		
		cacheSuperTopicPrior();
		for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
			cacheSuperSubTopicPrior(superTopic);
		}

		// Finally, start the sampler!

		for (int iterations = 1; iterations < numIterations; iterations++) {
			long iterationStart = System.currentTimeMillis();

			// Loop over every word in the corpus
			for (int doc = 0; doc < superTopics.length; doc++) {
				sampleTopicsForOneDoc ((FeatureSequence)instances.get(doc).getData(),
									   superTopics[doc], subTopics[doc], r);
			}
			
			if (showTopicsInterval != 0 && iterations % showTopicsInterval == 0) {
				logger.info( printTopWords(8, false) );
			}

			logger.fine((System.currentTimeMillis() - iterationStart) + " ");
			if (iterations % 10 == 0) {
				logger.info ("<" + iterations + "> LL: " + formatter.format(modelLogLikelihood() / numTokens));
			}
		}
	
    }
    
	private void cacheSuperTopicPrior() {
		for (int superTopic = 0; superTopic < numSuperTopics; superTopic++) {
			superTopicPriorWeights[superTopic] = 
				(superTopicDocumentFrequencies[superTopic] + superTopicSmoothing) /
				(sumDocumentFrequencies + (numSuperTopics + 1) * superTopicSmoothing);
		}
		
		superTopicPriorWeights[numSuperTopics] = 
			(superTopicDocumentFrequencies[numSuperTopics] + superTopicSmoothing) /
			(sumDocumentFrequencies + (numSuperTopics + 1) * superTopicSmoothing);
	}

	private void cacheSuperSubTopicPrior(int superTopic) {
		int[] documentFrequencies = superSubTopicDocumentFrequencies[superTopic];

		for (int subTopic = 0; subTopic < numSubTopics; subTopic++) {
			superSubTopicPriorWeights[superTopic][subTopic] = 
				(documentFrequencies[subTopic] + subTopicSmoothing ) /
				(sumSuperTopicDocumentFrequencies[superTopic] + (numSubTopics + 1) * subTopicSmoothing);
		}
		
		superSubTopicPriorWeights[superTopic][numSubTopics] = 
			(documentFrequencies[numSubTopics] + subTopicSmoothing ) /
			(sumSuperTopicDocumentFrequencies[superTopic] + (numSubTopics + 1) * subTopicSmoothing);
	}

    private void sampleTopicsForOneDoc (FeatureSequence oneDocTokens,
										int[] superTopics, // indexed by seq position
										int[] subTopics,
										Randoms r) {

		//long startTime = System.currentTimeMillis();
	
		int[] currentTypeTopicCounts;
		int[] currentSuperSubCounts;
		double[] currentSuperSubWeights;

		double[] wordWeights = new double[ 1 + numSuperTopics + numSubTopics ];

		int type, subTopic, superTopic;
		double rootWeight, currentSuperWeight, cumulativeWeight, sample;
	    
		int docLen = oneDocTokens.getLength();

		Arrays.fill(superCounts, 0);
		for (int t = 0; t < numSuperTopics; t++) {
			Arrays.fill(superSubCounts[t], 0);
		}
	
		// populate topic counts
		for (int position = 0; position < docLen; position++) {
			superSubCounts[ superTopics[position] ][ subTopics[position] ]++;
			superCounts[ superTopics[position] ]++;
		}

		for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
			superWeights[superTopic] =
				((double) superCounts[superTopic] + 
				 (superTopicBalance * superTopicPriorWeights[superTopic])) /
				((double) superCounts[superTopic] + subTopicBalance);
			assert(superWeights[superTopic] != 0.0);
		}

		// Iterate over the positions (words) in the document

		for (int position = 0; position < docLen; position++) {

			type = oneDocTokens.getIndexAtPosition(position);
			currentTypeTopicCounts = typeTopicCounts[type];

			superTopic = superTopics[position];
			subTopic = subTopics[position];

			if (superTopic == numSuperTopics) {
				currentTypeTopicCounts[ 0 ]--;
				tokensPerTopic[ 0 ]--;
			}
			else if (subTopic == numSubTopics) {
				currentTypeTopicCounts[ 1 + superTopic ]--;
				tokensPerTopic[ 1 + superTopic ]--;
			}
			else {
				currentTypeTopicCounts[ 1 + numSuperTopics + subTopic ]--;
				tokensPerTopic[ 1 + numSuperTopics + subTopic ]--;
			}

			// Remove this token from all counts
			superCounts[superTopic]--;
			superSubCounts[superTopic][subTopic]--;

			if (superCounts[superTopic] == 0) {
				// The document frequencies have changed.
				//  Decrement and recalculate the prior weights
				superTopicDocumentFrequencies[superTopic]--;
				sumDocumentFrequencies--;
				cacheSuperTopicPrior();
			}
			if (superTopic != numSuperTopics && 
				superSubCounts[superTopic][subTopic] == 0) {
				superSubTopicDocumentFrequencies[superTopic][subTopic]--;
				sumSuperTopicDocumentFrequencies[superTopic]--;
				cacheSuperSubTopicPrior(superTopic);
			}

			tokensPerSuperTopic[superTopic]--;
			tokensPerSuperSubTopic[superTopic][subTopic]--;

			// Update the super-topic weight for the old topic.
			superWeights[superTopic] =
                ((double) superCounts[superTopic] +
                 (superTopicBalance * superTopicPriorWeights[superTopic])) /
                ((double) superCounts[superTopic] + subTopicBalance);

			// Build a distribution over super-sub topic pairs 
			//   for this token

			for (int i=0; i<wordWeights.length; i++) {
				wordWeights[i] = 
					(beta + currentTypeTopicCounts[i]) /
					(betaSum + tokensPerTopic[i]);

				assert(wordWeights[i] != 0);
			}

			Arrays.fill(cumulativeSuperWeights, 0.0);

			// The conditional probability of each super-sub pair is proportional
			//  to an expression with three parts, one that depends only on the 
			//  super-topic, one that depends only on the sub-topic and the word type,
			//  and one that depends on the super-sub pair.

			cumulativeWeight = 0.0;

			for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
				currentSuperSubWeights = superSubWeights[superTopic];
				currentSuperSubCounts = superSubCounts[superTopic];
				currentSuperWeight = superWeights[superTopic];
		
				int[] documentFrequencies = superSubTopicDocumentFrequencies[superTopic];
				double[] priorCache = superSubTopicPriorWeights[superTopic];
				
				for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
					currentSuperSubWeights[subTopic] =
						currentSuperWeight *
						wordWeights[ 1 + numSuperTopics + subTopic ] *
						((double) currentSuperSubCounts[subTopic] + 
						 ( subTopicBalance * priorCache[subTopic] ));
					cumulativeWeight += currentSuperSubWeights[subTopic];
				}
		
				currentSuperSubWeights[numSubTopics] = 
					currentSuperWeight *
					wordWeights[1 + superTopic] *
					((double) currentSuperSubCounts[numSubTopics] +
					 ( subTopicBalance * priorCache[numSubTopics] ));
				cumulativeWeight += currentSuperSubWeights[numSubTopics];

				cumulativeSuperWeights[superTopic] = cumulativeWeight;
				assert(cumulativeSuperWeights[superTopic] != 0.0);
			}

			rootWeight = wordWeights[0] *
				(superCounts[numSuperTopics] +
				 (superTopicBalance * superTopicPriorWeights[numSuperTopics]));

			// Sample a topic assignment from this distribution
			sample = r.nextUniform() * (cumulativeWeight + rootWeight);

			if (sample > cumulativeWeight) {
				// We picked the root topic
		
				currentTypeTopicCounts[ 0 ]++;
				tokensPerTopic[ 0 ] ++;
		
				superTopic = numSuperTopics;
				subTopic = numSubTopics;
			}
			else {

				// Go over the row sums to find the super-topic...
				superTopic = 0;
				while (sample > cumulativeSuperWeights[superTopic]) {
					superTopic++;
				}

				// Now read across to find the sub-topic
				currentSuperSubWeights = superSubWeights[superTopic];
				cumulativeWeight = cumulativeSuperWeights[superTopic];

				// Go over each sub-topic until the weight is LESS than
				//  the sample. Note that we're subtracting weights
				//  in the same order we added them...
				subTopic = 0;
				cumulativeWeight -=	currentSuperSubWeights[0];

				while (sample < cumulativeWeight) {
					subTopic++;
					cumulativeWeight -= currentSuperSubWeights[subTopic];
				}
		
				if (subTopic == numSubTopics) {
					currentTypeTopicCounts[ 1 + superTopic ]++;
					tokensPerTopic[ 1 + superTopic ]++;
				}
				else {
					currentTypeTopicCounts[ 1 + numSuperTopics + subTopic ]++;
					tokensPerTopic[ 1 + numSuperTopics + subTopic ]++;
				}
			}

			// Save the choice into the Gibbs state
	    
			superTopics[position] = superTopic;
			subTopics[position] = subTopic;

			// Put the new super/sub topics into the counts
	    
			superSubCounts[superTopic][subTopic]++;
			superCounts[superTopic]++;

			if (superCounts[superTopic] == 1) {
				superTopicDocumentFrequencies[superTopic]++;
				sumDocumentFrequencies++;
				cacheSuperTopicPrior();
			}
			if (superTopic != numSuperTopics && 
				superSubCounts[superTopic][subTopic] == 1) {
				superSubTopicDocumentFrequencies[superTopic][subTopic]++;
				sumSuperTopicDocumentFrequencies[superTopic]++;
				cacheSuperSubTopicPrior(superTopic);
			}

			tokensPerSuperTopic[superTopic]++;
			tokensPerSuperSubTopic[superTopic][subTopic]++;

			// Update the weight for the new super topic
			superWeights[superTopic] =
                ((double) superCounts[superTopic] +
                 (superTopicBalance * superTopicPriorWeights[superTopic])) /
                ((double) superCounts[superTopic] + subTopicBalance);

		}

    }

    public String printTopWords (int numWords, boolean useNewLines) {

		StringBuilder output = new StringBuilder();

		IDSorter[] sortedTypes = new IDSorter[numTypes];
		IDSorter[] sortedSubTopics = new IDSorter[numSubTopics];
		String[] topicTerms = new String[1 + numSuperTopics + numSubTopics];

		int subTopic, superTopic;

		for (int topic = 0; topic < topicTerms.length; topic++) {
			for (int type = 0; type < numTypes; type++)
				sortedTypes[type] = new IDSorter (type, 
									   (((double) typeTopicCounts[type][topic]) /
										tokensPerTopic[topic]));
			Arrays.sort (sortedTypes);

			StringBuilder terms = new StringBuilder();
			for (int i = 0; i < numWords; i++) {
				terms.append(instances.getDataAlphabet().lookupObject(sortedTypes[i].getID()));
				terms.append(" ");
			}
			topicTerms[topic] = terms.toString();
		}

		int maxSubTopics = 10;
		if (numSubTopics < 10) { maxSubTopics = numSubTopics; }

		output.append("Root: " + "[" + tokensPerSuperTopic[numSuperTopics] + "/" + 
					  superTopicDocumentFrequencies[numSuperTopics] + "]" +
					  topicTerms[0] + "\n");

		for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
			for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
				sortedSubTopics[subTopic] =
					new IDSorter(subTopic, tokensPerSuperSubTopic[superTopic][subTopic]);
			}

			Arrays.sort(sortedSubTopics);

			output.append("\nSuper-topic " + superTopic + 
						  " [" + tokensPerSuperTopic[superTopic] + "/" + 
						  superTopicDocumentFrequencies[superTopic] + " " + 
						  tokensPerSuperSubTopic[superTopic][numSubTopics] + "/" + 
						  superSubTopicDocumentFrequencies[superTopic][numSubTopics] + "]\t" + 
						  topicTerms[1 + superTopic] + "\n");

			for (int i = 0; i < maxSubTopics; i++) {
				subTopic = sortedSubTopics[i].getID();
				output.append(subTopic + ":\t" +
							  tokensPerSuperSubTopic[superTopic][subTopic] + "/" +
							  formatter.format(superSubTopicDocumentFrequencies[superTopic][subTopic]) + "\t" +
							  topicTerms[1 + numSuperTopics + subTopic] + "\n");
			}
		}

		return output.toString();
    }
    
    public void printState (File f) throws IOException {
		PrintWriter out = new PrintWriter (new BufferedWriter (new FileWriter(f)));
		printState (out);
		out.close();
    }
    
    public void printState (PrintWriter out) {

		Alphabet alphabet = instances.getDataAlphabet();
		out.println ("#doc pos typeindex type super-topic sub-topic");

		for (int doc = 0; doc < superTopics.length; doc++) {
			StringBuilder output = new StringBuilder();
			
			FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();
			for (int position = 0; position < superTopics[doc].length; position++) {
				int type = fs.getIndexAtPosition(position);
				output.append(doc); output.append(' ');
				output.append(position); output.append(' ');
				output.append(type); output.append(' ');
				output.append(alphabet.lookupObject(type)); output.append(' ');
				output.append(superTopics[doc][position]); output.append(' ');
				output.append(subTopics[doc][position]); output.append("\n");
			}

			out.print(output);
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
        //   Gamma( sum_i alpha_i )  prod_i Gamma( alpha_i + N_i )
        //  prod_i Gamma( alpha_i )   Gamma( sum_i (alpha_i + N_i) )

        // So the log likelihood is 
        //  logGamma ( sum_i alpha_i ) - logGamma ( sum_i (alpha_i + N_i) ) + 
        //   sum_i [ logGamma( alpha_i + N_i) - logGamma( alpha_i ) ]

        // Do the documents first

        int superTopic, subTopic;

        double[] superTopicLogGammas = new double[numSuperTopics + 1];
        double[][] superSubTopicLogGammas = new double[numSuperTopics][numSubTopics + 1];

        for (superTopic=0; superTopic < numSuperTopics; superTopic++) {
            superTopicLogGammas[ superTopic ] = Dirichlet.logGamma(superTopicPriorWeights[superTopic]);

            for (subTopic=0; subTopic < numSubTopics; subTopic++) {
                superSubTopicLogGammas[ superTopic ][ subTopic ] =
                    Dirichlet.logGamma(subTopicBalance * superSubTopicPriorWeights[superTopic][subTopic]);
            }
			superSubTopicLogGammas[ superTopic ][ numSubTopics ] =
				Dirichlet.logGamma(subTopicBalance * superSubTopicPriorWeights[superTopic][numSubTopics]);
        }
		superTopicLogGammas[ numSuperTopics ] = Dirichlet.logGamma(superTopicPriorWeights[numSuperTopics]);

        int[] superTopicCounts = new int[ numSuperTopics + 1];
        int[][] superSubTopicCounts = new int[ numSuperTopics ][ numSubTopics + 1];

        int[] docSuperTopics;
        int[] docSubTopics;

        for (int doc=0; doc < superTopics.length; doc++) {
            
            docSuperTopics = superTopics[doc];
            docSubTopics = subTopics[doc];

            for (int token=0; token < docSuperTopics.length; token++) {
                superTopic = docSuperTopics[ token ];
                subTopic =   docSubTopics[ token ];

                superTopicCounts[ superTopic ]++;
				if (superTopic != numSuperTopics) {
					superSubTopicCounts[ superTopic ][ subTopic ]++;
				}
            }

            for (superTopic=0; superTopic < numSuperTopics; superTopic++) {
                if (superTopicCounts[superTopic] > 0) {
                    logLikelihood += (Dirichlet.logGamma(superTopicBalance * superTopicPriorWeights[superTopic] +
														 superTopicCounts[superTopic]) -
                                      superTopicLogGammas[ superTopic ]);
                    
                    for (subTopic=0; subTopic < numSubTopics; subTopic++) {
                        if (superSubTopicCounts[superTopic][subTopic] > 0) {
                            logLikelihood += (Dirichlet.logGamma(subTopicBalance * superSubTopicPriorWeights[superTopic][subTopic] +
																 superSubTopicCounts[superTopic][subTopic]) -
                                              superSubTopicLogGammas[ superTopic ][ subTopic ]);
                        }
                    }
                    
					// Account for words assigned to super-topic
		    
					logLikelihood += (Dirichlet.logGamma(subTopicBalance * superSubTopicPriorWeights[superTopic][numSubTopics] +
														 superSubTopicCounts[superTopic][numSubTopics]) -
									  superSubTopicLogGammas[ superTopic ][ numSubTopics ]);

					// The term for the sums
                    logLikelihood += 
                        Dirichlet.logGamma(subTopicBalance) -
                        Dirichlet.logGamma(subTopicBalance + superTopicCounts[superTopic]);

                    Arrays.fill(superSubTopicCounts[superTopic], 0);
                }
            }

			// Account for words assigned to the root topic
			logLikelihood += (Dirichlet.logGamma(superTopicBalance * superTopicPriorWeights[numSuperTopics] +
												 superTopicCounts[numSuperTopics]) -
							  superTopicLogGammas[ numSuperTopics ]);

            // subtract the (count + parameter) sum term
            logLikelihood -= Dirichlet.logGamma(superTopicBalance + docSuperTopics.length);
            Arrays.fill(superTopicCounts, 0);
            
        }

        // add the parameter sum term for every document all at once.
        logLikelihood += superTopics.length * Dirichlet.logGamma(superTopicBalance);

        // And the topics

        // Count the number of type-topic pairs
        int nonZeroTypeTopics = 0;

        for (int type=0; type < numTypes; type++) {
            // reuse this array as a pointer
            int[] topicCounts = typeTopicCounts[type];

            for (int topic=0; topic < numSuperTopics + numSubTopics + 1; topic++) {
                if (topicCounts[topic] > 0) {
                    nonZeroTypeTopics++;
                    logLikelihood += Dirichlet.logGamma(beta + topicCounts[topic]);
                }
            }
        }

		for (int topic=0; topic < numSuperTopics + numSubTopics + 1; topic++) {
            logLikelihood -= 
				Dirichlet.logGamma( (beta * (numSuperTopics + numSubTopics + 1)) +
											tokensPerTopic[ topic ] );
		}
	
        logLikelihood += 
			(Dirichlet.logGamma(beta * (numSuperTopics + numSubTopics + 1))) -
			(Dirichlet.logGamma(beta) * nonZeroTypeTopics);
	
		return logLikelihood;
    }

    public static void main (String[] args) throws IOException {
		CommandOption.setSummary(HierarchicalPAM.class, "Train a three level hierarchy of topics");
		CommandOption.process(HierarchicalPAM.class, args);

        InstanceList instances = InstanceList.load (new File(inputFile.value));
        InstanceList testing = null;

        HierarchicalPAM pam = new HierarchicalPAM (numSuperTopicsOption.value, numSubTopicsOption.value,
												   superTopicBalanceOption.value, subTopicBalanceOption.value);
        pam.estimate (instances, testing, 1000, 100, 0, 250, null, new Randoms());
		if (stateFile.wasInvoked()) {
			pam.printState(new File(stateFile.value));
		}
    }
}
