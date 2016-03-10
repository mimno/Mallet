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
	
	static CommandOption.Integer numDimensions = new CommandOption.Integer(WordEmbeddings.class, "num-dimensions", "INTEGER", true, 50,
																	   "The number of dimensions to fit.", null);

	static CommandOption.Integer windowSizeOption = new CommandOption.Integer(WordEmbeddings.class, "window-size", "INTEGER", true, 5,
																		"The number of adjacent words to consider.", null);


	static CommandOption.Integer numThreads = new CommandOption.Integer(WordEmbeddings.class, "num-threads", "INTEGER", true, 1,
																		"The number of threads for parallel training.", null);

	static CommandOption.Integer numSamples = new CommandOption.Integer(WordEmbeddings.class, "num-samples", "INTEGER", true, 5,
																		"The number of negative samples to use in training.", null);

	static CommandOption.String exampleWord = new CommandOption.String(WordEmbeddings.class, "example-word", "STRING", true, null,
																	   "If defined, periodically show the closest vectors to this word.", null);


	Alphabet vocabulary;
        
	int numWords;
	int numColumns;
	double[] weights;
	double[] squaredGradientSums;
	int stride;

	int[] wordCounts;
	double[] samplingDistribution;
	int[] samplingTable;
	int samplingTableSize = 100000000;
	double samplingSum = 0.0f;
	int totalWords = 0;

	double maxExpValue = 6.0;
	double minExpValue = -6.0;
	double[] sigmoidCache;
	int sigmoidCacheSize = 1000;

	int windowSize = 5;

	public String getQueryWord()
	{
		return queryWord;
	}

	public void setQueryWord(String queryWord)
	{
		this.queryWord = queryWord;
	}

	private String queryWord = "the";

	Randoms random = new Randoms();

	public WordEmbeddings() { }

	public WordEmbeddings(Alphabet a, int numColumns, int windowSize) {
		vocabulary = a;
                
		numWords = vocabulary.size();

		System.out.format("Vocab size: %d\n", numWords);

		this.numColumns = numColumns;
		
		this.stride = 2 * numColumns;
		
		weights = new double[numWords * stride];
		squaredGradientSums = new double[numWords * stride];

		for (int word = 0; word < numWords; word++) {
			for (int col = 0; col < 2 * numColumns; col++) {
				weights[word * stride + col] = (random.nextDouble() - 0.5f) / numColumns;
			}
		}

		wordCounts = new int[numWords];
		samplingDistribution = new double[numWords];
		samplingTable = new int[samplingTableSize];

		this.windowSize = windowSize;

		sigmoidCache = new double[sigmoidCacheSize + 1];

		for (int i = 0; i < sigmoidCacheSize; i++) {
			double value = ((double) i / sigmoidCacheSize) * (maxExpValue - minExpValue) + minExpValue;
			sigmoidCache[i] = 1.0 / (1.0 + Math.exp(-value));
		}
	}

	public void countWords(InstanceList instances) {
		for (Instance instance: instances) {
			
			FeatureSequence tokens = (FeatureSequence) instance.getData();
			int length = tokens.getLength();

			for (int position = 0; position < length; position++) {
				int type = tokens.getIndexAtPosition(position);
				wordCounts[type]++;
			}
			
			totalWords += length;
		}

		double normalizer = 1.0f / totalWords;

		samplingDistribution[0] = Math.pow(normalizer * wordCounts[0], 0.75);
		for (int word = 1; word < numWords; word++) {
			samplingDistribution[word] = samplingDistribution[word-1] + Math.pow(normalizer * wordCounts[word], 0.75);
		}
		samplingSum = samplingDistribution[numWords-1];

		int word = 0;
		for (int i = 0; i < samplingTableSize; i++) {
			while (samplingSum * i / samplingTableSize > samplingDistribution[word]) {
				word++;
			}
			samplingTable[i] = word;
		}

		System.out.println("done counting");
	}

	public void train(InstanceList instances, int numThreads, int numSamples) {

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);

		WordEmbeddingRunnable[] runnables = new WordEmbeddingRunnable[numThreads];
		for (int thread = 0; thread < numThreads; thread++) {
			runnables[thread] = new WordEmbeddingRunnable(this, instances, numSamples, numThreads, thread);
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
			for (int thread = 0; thread < numThreads; thread++) {
				wordsSoFar += runnables[thread].wordsSoFar;
				System.out.format("%.3f ", runnables[thread].getMeanError());
			}

			long runningMillis = System.currentTimeMillis() - startTime;
			System.out.format("%d\t%d\t%fk w/s %f loss %f avg\n", wordsSoFar, runningMillis, (double) wordsSoFar / runningMillis, 
							  difference / 10000, averageAbsWeight());
			difference = 0.0;

			if (wordsSoFar > 5 * totalWords) {
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
		IDSorter[] sortedWords = new IDSorter[numWords];

		double targetSquaredSum = 0.0;
		for (int col = 0; col < numColumns; col++) {
			targetSquaredSum += targetVector[col] * targetVector[col];
		}
		double targetNormalizer = 1.0 / Math.sqrt(targetSquaredSum);

		System.out.println(targetSquaredSum);

		for (int word = 0; word < numWords; word++) {
			
			double innerProduct = 0.0;
			
			double wordSquaredSum = 0.0;
			for (int col = 0; col < numColumns; col++) {
				wordSquaredSum += weights[word * stride + col] * weights[word * stride + col];
			}
			double wordNormalizer = 1.0 / Math.sqrt(wordSquaredSum);

			for (int col = 0; col < numColumns; col++) {
				innerProduct += targetNormalizer * targetVector[col] * wordNormalizer * weights[word * stride + col];
			}

			sortedWords[word] = new IDSorter(word, innerProduct);
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
		matrix.countWords(instances);
		matrix.train(instances, numThreads.value, numSamples.value);
		
		PrintWriter out = new PrintWriter(outputFile.value);
		matrix.write(out);
		out.close();
	}

}