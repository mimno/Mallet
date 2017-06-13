package cc.mallet.topics;

import cc.mallet.util.Randoms;
import cc.mallet.util.CommandOption;
import cc.mallet.types.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;

public class WordEmbeddings {
	
	static CommandOption.String inputFile = new CommandOption.String(WordEmbeddings.class, "input", "FILENAME", true, null,
																	 "The filename from which to read the list of training instances.  Use - for stdin.  " +
																	 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String outputFile = new CommandOption.String(WordEmbeddings.class, "output", "FILENAME", true, "weights.txt",
																	  "The filename to write text-formatted word vectors.", null);
	
	static CommandOption.String outputContextFile = new CommandOption.String(WordEmbeddings.class, "output-context", "FILENAME", true, "NONE",
																	  "The filename to write text-formatted context vectors.", null);
	
	static CommandOption.Integer numDimensions = new CommandOption.Integer(WordEmbeddings.class, "num-dimensions", "INTEGER", true, 50,
																	   "The number of dimensions to fit.", null);

	static CommandOption.Integer windowSizeOption = new CommandOption.Integer(WordEmbeddings.class, "window-size", "INTEGER", true, 5,
																		"The number of adjacent words to consider.", null);


	static CommandOption.Integer numThreads = new CommandOption.Integer(WordEmbeddings.class, "num-threads", "INTEGER", true, 1,
																		"The number of threads for parallel training.", null);

	static CommandOption.Integer numIterationsOption = new CommandOption.Integer(WordEmbeddings.class, "num-iters", "INTEGER", true, 3,
																		"The number of passes through the training data.", null);

	static CommandOption.Double samplingFactorOption = new CommandOption.Double(WordEmbeddings.class, "frequency-factor", "NUMBER", true, 0.0001,
																		"Down-sample words that account for more than ~2.5x this proportion or the corpus.", null);

	static CommandOption.Integer numSamples = new CommandOption.Integer(WordEmbeddings.class, "num-samples", "INTEGER", true, 5,
																		"The number of negative samples to use in training.", null);

	static CommandOption.String exampleWord = new CommandOption.String(WordEmbeddings.class, "example-word", "STRING", true, null,
																	   "If defined, periodically show the closest vectors to this word.", null);

	static CommandOption.String orderingOption = new CommandOption.String(WordEmbeddings.class, "ordering", "STRING", true, "linear",
																	   "\"linear\" reads documents in order, \"shuffled\" reads in random order, \"random\" selects documents at random and may repeat/drop documents", null);

	public static final int LINEAR_ORDERING = 0;
	public static final int SHUFFLED_ORDERING = 1;
	public static final int RANDOM_ORDERING = 2;
	

	Alphabet vocabulary;
        
	int numWords;
	int numColumns;
	double[] weights;
	double[] negativeWeights;
	int stride;
	
	int numIterations;

	int[] wordCounts;
	double[] retentionProbability;
	double[] samplingDistribution;
	int[] samplingTable;
	int samplingTableSize = 100000000;
	double samplingSum = 0.0;
	int totalWords = 0;

	double maxExpValue = 6.0;
	double minExpValue = -6.0;
	double[] sigmoidCache;
	int sigmoidCacheSize = 1000;

	int windowSize = 5;
	
	IDSorter[] sortedWords = null;
	
	int orderingStrategy = LINEAR_ORDERING;

	public int getMinDocumentLength() {
		return minDocumentLength;
	}

	public void setMinDocumentLength(int minDocumentLength) {
		if (minDocumentLength <= 0) {
			throw new IllegalArgumentException("Minimum document length must be at least 1.");
		}
		this.minDocumentLength = minDocumentLength;
	}

	private int minDocumentLength = 10;

	public void setNumIterations(int i) { numIterations = i; }

	public String getQueryWord() {
		return queryWord;
	}

	public void setQueryWord(String queryWord) {
		this.queryWord = queryWord;
	}

	String queryWord = "the";

	Randoms random = new Randoms();

	public WordEmbeddings() { }

	public WordEmbeddings(Alphabet a, int numColumns, int windowSize) {
		vocabulary = a;
                
		numWords = vocabulary.size();

		System.out.format("Vocab size: %d\n", numWords);

		this.numColumns = numColumns;
		
		this.stride = numColumns;
		
		weights = new double[numWords * stride];
		negativeWeights = new double[numWords * stride];

		for (int word = 0; word < numWords; word++) {
			for (int col = 0; col < numColumns; col++) {
				weights[word * stride + col] = (random.nextDouble() - 0.5f) / numColumns;
				negativeWeights[word * stride + col] = 0.0;
			}
		}

		wordCounts = new int[numWords];
		samplingDistribution = new double[numWords];
		retentionProbability = new double[numWords];
		samplingTable = new int[samplingTableSize];

		this.windowSize = windowSize;

		sigmoidCache = new double[sigmoidCacheSize + 1];

		for (int i = 0; i < sigmoidCacheSize; i++) {
			double value = ((double) i / sigmoidCacheSize) * (maxExpValue - minExpValue) + minExpValue;
			sigmoidCache[i] = 1.0 / (1.0 + Math.exp(-value));
		}
		
	}
	
	public void initializeSortables() {
		sortedWords = new IDSorter[numWords];
		for (int word = 0; word < numWords; word++) {
			sortedWords[word] = new IDSorter(word, 0.0);
		}
	}

	public void countWords(InstanceList instances, double samplingFactor) {
		for (Instance instance: instances) {
			
			FeatureSequence tokens = (FeatureSequence) instance.getData();
			int length = tokens.getLength();

			for (int position = 0; position < length; position++) {
				int type = tokens.getIndexAtPosition(position);
				wordCounts[type]++;
			}
			
			totalWords += length;
		}
		
		for (int word = 0; word < numWords; word++) {
			// Word2Vec style sampling weight
			double frequencyScore = (double) wordCounts[word] / (samplingFactor * totalWords);
			retentionProbability[word] = Math.min((Math.sqrt(frequencyScore) + 1) / frequencyScore, 1.0);
		}

		if (sortedWords == null) {
			initializeSortables();
		}
		for (int word = 0; word < numWords; word++) {
			sortedWords[word].set(word, wordCounts[word]);
		}
		Arrays.sort(sortedWords);
		
		samplingDistribution[0] = Math.pow(wordCounts[ sortedWords[0].getID() ], 0.75);
		for (int word = 1; word < numWords; word++) {
			samplingDistribution[word] = samplingDistribution[word-1] + Math.pow(wordCounts[ sortedWords[word].getID() ], 0.75);
		}
		samplingSum = samplingDistribution[numWords-1];

		int order = 0;
		for (int i = 0; i < samplingTableSize; i++) {
			samplingTable[i] = sortedWords[order].getID();
			while (samplingSum * i / samplingTableSize > samplingDistribution[order]) {
				order++;
			}
		}

		System.out.println("done counting: " + totalWords);
	}

	public void train(InstanceList instances, int numThreads, int numSamples) {

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);

		WordEmbeddingRunnable[] runnables = new WordEmbeddingRunnable[numThreads];
		for (int thread = 0; thread < numThreads; thread++) {
			runnables[thread] = new WordEmbeddingRunnable(this, instances, numSamples, numThreads, thread);
			runnables[thread].setOrdering(orderingStrategy);
			executor.submit(runnables[thread]);
		}

		long startTime = System.currentTimeMillis();
		double difference = 0.0;

		boolean finished = false;
		while (! finished) {
                               
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
                                                
			}

			int wordsSoFar = 0;
                                        
			// Are all the threads done?
			boolean anyRunning = false;
			for (int thread = 0; thread < numThreads; thread++) {
				if (runnables[thread].shouldRun) { anyRunning = true; }
				wordsSoFar += runnables[thread].wordsSoFar;
				//System.out.format("%.3f ", runnables[thread].getMeanError());
			}

			long runningMillis = System.currentTimeMillis() - startTime;
			System.out.format("%d\t%d\t%fk w/s %f avg\n", wordsSoFar, runningMillis, (double) wordsSoFar / runningMillis, 
							  averageAbsWeight());
			//variances();
			difference = 0.0;

			if (! anyRunning || wordsSoFar > numIterations * totalWords) {
				finished = true;
				for (int thread = 0; thread < numThreads; thread++) {
					runnables[thread].shouldRun = false;
				}
			}

			if (queryWord != null && vocabulary.contains(queryWord)) {
				findClosest(copy(queryWord));
			}
		}
		executor.shutdownNow();
	}

	public void findClosest(double[] targetVector) {
		if (sortedWords == null) {
			initializeSortables();
		}

		double targetSquaredSum = 0.0;
		for (int col = 0; col < numColumns; col++) {
			targetSquaredSum += targetVector[col] * targetVector[col];
		}
		double targetNormalizer = 1.0 / Math.sqrt(targetSquaredSum);

		for (int word = 0; word < numWords; word++) {
			
			double innerProduct = 0.0;
			
			double wordSquaredSum = 0.0;
			for (int col = 0; col < numColumns; col++) {
				wordSquaredSum += weights[word * stride + col] * weights[word * stride + col];
			}
			double wordNormalizer = 1.0 / Math.sqrt(wordSquaredSum);

			for (int col = 0; col < numColumns; col++) {
				innerProduct += targetVector[col] * weights[word * stride + col];
			}
			innerProduct *= targetNormalizer * wordNormalizer;

			sortedWords[word].set(word, innerProduct);
		}

		Arrays.sort(sortedWords);
		
		for (int i = 0; i < 10; i++) {
			System.out.format("%f\t%d\t%s\n", sortedWords[i].getWeight(), sortedWords[i].getID(), vocabulary.lookupObject(sortedWords[i].getID()));
		}
	}
	
	public double averageAbsWeight() {
		double sum = 0.0;
		for (int word = 0; word < numWords; word++) {
			for (int col = 0; col < numColumns; col++) {
				sum += Math.abs(weights[word * stride + col]);
			}
		}
		return sum / (numWords * numColumns);
	}

	public double[] variances() {
		double[] means = new double[numColumns];
		for (int word = 0; word < numWords; word++) {
			for (int col = 0; col < numColumns; col++) {
				means[col] += weights[word * stride + col];
			}
		}
		for (int col = 0; col < numColumns; col++) {
			means[col] /= numWords;
		}
		
		double[] squaredSums = new double[numColumns];
		double diff;
		for (int word = 0; word < numWords; word++) {
			for (int col = 0; col < numColumns; col++) {
				diff = weights[word * stride + col] - means[col];
				squaredSums[col] += diff * diff;
			}
		}
		for (int col = 0; col < numColumns; col++) {
			squaredSums[col] /= (numWords - 1);
			System.out.format("%f\t", squaredSums[col]);
		}
		System.out.println();
		return squaredSums;
	}

	public void write(PrintWriter out) {
		for (int word = 0; word < numWords; word++) {
			Formatter buffer = new Formatter(Locale.US);
			buffer.format("%s", vocabulary.lookupObject(word));
			for (int col = 0; col < numColumns; col++) {
				buffer.format(" %.6f", weights[word * stride + col]);
			}
			out.println(buffer);
		}
	}

	public void writeContext(PrintWriter out) {
		for (int word = 0; word < numWords; word++) {
			Formatter buffer = new Formatter(Locale.US);
			buffer.format("%s", vocabulary.lookupObject(word));
			for (int col = 0; col < numColumns; col++) {
				buffer.format(" %.6f", negativeWeights[word * stride + col]);
			}
			out.println(buffer);
		}
	}

	public double[] copy(String word) {
		return copy(vocabulary.lookupIndex(word));
	}

	public double[] copy(int word) {
		double[] result = new double[numColumns];

		for (int col = 0; col < numColumns; col++) {
			result[col] = weights[word * stride + col];
		}
		
		return result;
	}

	public double[] add(double[] result, String word) {
		return add(result, vocabulary.lookupIndex(word));
	}

	public double[] add(double[] result, int word) {
		for (int col = 0; col < numColumns; col++) {
			result[col] += weights[word * stride + col];
		}
		
		return result;
	}

	public double[] subtract(double[] result, String word) {
		return subtract(result, vocabulary.lookupIndex(word));
	}

	public double[] subtract(double[] result, int word) {
		for (int col = 0; col < numColumns; col++) {
			result[col] -= weights[word * stride + col];
		}
		
		return result;
	}

	public static void main (String[] args) throws Exception {
		// Process the command-line options
		CommandOption.setSummary (WordEmbeddings.class,
								  "Train continuous word embeddings using the skip-gram method with negative sampling.");
		CommandOption.process (WordEmbeddings.class, args);

		InstanceList instances = InstanceList.load(new File(inputFile.value));

		WordEmbeddings matrix = new WordEmbeddings(instances.getDataAlphabet(), numDimensions.value, windowSizeOption.value);
		matrix.queryWord = exampleWord.value;
		matrix.setNumIterations(numIterationsOption.value);
		matrix.countWords(instances, samplingFactorOption.value);
		if (orderingOption.value != null) {
			if (orderingOption.value.startsWith("s")) { matrix.orderingStrategy = SHUFFLED_ORDERING; }
			else if (orderingOption.value.startsWith("l")) { matrix.orderingStrategy = LINEAR_ORDERING; }
			else if (orderingOption.value.startsWith("r")) { matrix.orderingStrategy = RANDOM_ORDERING; }
			else {
				System.err.println("Unrecognized ordering: " + orderingOption.value + ", using linear.");
			}
		}
		
		matrix.train(instances, numThreads.value, numSamples.value);
		
		PrintWriter out = new PrintWriter(outputFile.value);
		matrix.write(out);
		out.close();
		
		if (outputContextFile.value != null) {
			out = new PrintWriter(outputContextFile.value);
			matrix.writeContext(out);
			out.close();
		}
	}

}