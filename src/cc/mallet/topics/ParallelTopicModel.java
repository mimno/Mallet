/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.	For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Formatter;
import java.util.Locale;

import java.util.concurrent.*;
import java.util.logging.*;
import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.topics.TopicAssignment;
import cc.mallet.util.Randoms;
import cc.mallet.util.MalletLogger;

/**
 * Simple parallel threaded implementation of LDA,
 *  following Newman, Asuncion, Smyth and Welling, Distributed Algorithms for Topic Models
 *  JMLR (2009), with SparseLDA sampling scheme and data structure from
 *  Yao, Mimno and McCallum, Efficient Methods for Topic Model Inference on Streaming Document Collections, KDD (2009).
 * 
 * @author David Mimno, Andrew McCallum
 */

public class ParallelTopicModel implements Serializable {

	public static final int UNASSIGNED_TOPIC = -1;

	public static Logger logger = MalletLogger.getLogger(ParallelTopicModel.class.getName());
	
	public ArrayList<TopicAssignment> data;  // the training instances and their topic assignments
	public Alphabet alphabet; // the alphabet for the input data
	public LabelAlphabet topicAlphabet;  // the alphabet for the topics
	
	public int numTopics; // Number of topics to be fit

	// These values are used to encode type/topic counts as
	//  count/topic pairs in a single int.
	public int topicMask;
	public int topicBits;

	public int numTypes;
	public int totalTokens;

	public double[] alpha;	 // Dirichlet(alpha,alpha,...) is the distribution over topics
	public double alphaSum;
	public double beta;   // Prior on per-topic multinomial distribution over words
	public double betaSum;

	public boolean usingSymmetricAlpha = false;

	public static final double DEFAULT_BETA = 0.01;
	
	public int[][] typeTopicCounts; // indexed by <feature index, topic index>
	public int[] tokensPerTopic; // indexed by <topic index>

	// for dirichlet estimation
	public int[] docLengthCounts; // histogram of document sizes
	public int[][] topicDocCounts; // histogram of document/topic counts, indexed by <topic index, sequence position index>

	public int numIterations = 1000;
	public int burninPeriod = 200; 
	public int saveSampleInterval = 10; 
	public int optimizeInterval = 50; 
	public int temperingInterval = 0;

	public int showTopicsInterval = 50;
	public int wordsPerTopic = 7;

	public int saveStateInterval = 0;
	public String stateFilename = null;

	public int saveModelInterval = 0;
	public String modelFilename = null;
	
	public int randomSeed = -1;
	public NumberFormat formatter;
	public boolean printLogLikelihood = true;

	// The number of times each type appears in the corpus
	int[] typeTotals;
	// The max over typeTotals, used for beta optimization
	int maxTypeCount; 
	
	int numThreads = 1;
	
	public ParallelTopicModel (int numberOfTopics) {
		this (numberOfTopics, numberOfTopics, DEFAULT_BETA);
	}
	
	public ParallelTopicModel (int numberOfTopics, double alphaSum, double beta) {
		this (newLabelAlphabet (numberOfTopics), alphaSum, beta);
	}
	
	private static LabelAlphabet newLabelAlphabet (int numTopics) {
		LabelAlphabet ret = new LabelAlphabet();
		for (int i = 0; i < numTopics; i++)
			ret.lookupIndex("topic"+i);
		return ret;
	}

	public ParallelTopicModel (LabelAlphabet topicAlphabet, double alphaSum, double beta)
	{
		this.data = new ArrayList<TopicAssignment>();
		this.topicAlphabet = topicAlphabet;
		this.numTopics = topicAlphabet.size();

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


		this.alphaSum = alphaSum;
		this.alpha = new double[numTopics];
		Arrays.fill(alpha, alphaSum / numTopics);
		this.beta = beta;
		
		tokensPerTopic = new int[numTopics];
		
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		logger.info("Coded LDA: " + numTopics + " topics, " + topicBits + " topic bits, " + 
					Integer.toBinaryString(topicMask) + " topic mask");
	}
	
	public Alphabet getAlphabet() { return alphabet; }
	public LabelAlphabet getTopicAlphabet() { return topicAlphabet; }
	public int getNumTopics() { return numTopics; }
	public ArrayList<TopicAssignment> getData() { return data; }
	
	public int[][] getTypeTopicCounts() { return typeTopicCounts; }
	public int[] getTokensPerTopic() { return tokensPerTopic; }

	public void setNumIterations (int numIterations) {
		this.numIterations = numIterations;
	}

	public void setBurninPeriod (int burninPeriod) {
		this.burninPeriod = burninPeriod;
	}

	public void setTopicDisplay(int interval, int n) {
		this.showTopicsInterval = interval;
		this.wordsPerTopic = n;
	}

	public void setRandomSeed(int seed) {
		randomSeed = seed;
	}

	/** Interval for optimizing Dirichlet hyperparameters */
	public void setOptimizeInterval(int interval) {
		this.optimizeInterval = interval;

		// Make sure we always have at least one sample
		//  before optimizing hyperparameters
		if (saveSampleInterval > optimizeInterval) {
			saveSampleInterval = optimizeInterval;
		}
	}

	public void setSymmetricAlpha(boolean b) {
		usingSymmetricAlpha = b;
	}
	
	public void setTemperingInterval(int interval) {
		temperingInterval = interval;
	}

	public void setNumThreads(int threads) {
		this.numThreads = threads;
	}

	/** Define how often and where to save a text representation of the current state.
	 *  Files are GZipped.
	 *
	 * @param interval Save a copy of the state every <code>interval</code> iterations.
	 * @param filename Save the state to this file, with the iteration number as a suffix
	 */
	public void setSaveState(int interval, String filename) {
		this.saveStateInterval = interval;
		this.stateFilename = filename;
	}

	/** Define how often and where to save a serialized model.
	 *
	 * @param interval Save a serialized model every <code>interval</code> iterations.
	 * @param filename Save to this file, with the iteration number as a suffix
	 */
	public void setSaveSerializedModel(int interval, String filename) {
		this.saveModelInterval = interval;
		this.modelFilename = filename;
	}

	public void addInstances (InstanceList training) {

		alphabet = training.getDataAlphabet();
		numTypes = alphabet.size();
		
		betaSum = beta * numTypes;
		
		typeTopicCounts = new int[numTypes][];

		// Get the total number of occurrences of each word type
		//int[] typeTotals = new int[numTypes];
		typeTotals = new int[numTypes];

		int doc = 0;
		for (Instance instance : training) {
			doc++;
			FeatureSequence tokens = (FeatureSequence) instance.getData();
			for (int position = 0; position < tokens.getLength(); position++) {
				int type = tokens.getIndexAtPosition(position);
				typeTotals[ type ]++;
			}
		}

		maxTypeCount = 0;

		// Allocate enough space so that we never have to worry about
		//  overflows: either the number of topics or the number of times
		//  the type occurs.
		for (int type = 0; type < numTypes; type++) {
			if (typeTotals[type] > maxTypeCount) { maxTypeCount = typeTotals[type]; }
			typeTopicCounts[type] = new int[ Math.min(numTopics, typeTotals[type]) ];
		}
		
		doc = 0;

		Randoms random = null;
		if (randomSeed == -1) {
			random = new Randoms();
		}
		else {
			random = new Randoms(randomSeed);
		}

		for (Instance instance : training) {
			doc++;

			FeatureSequence tokens = (FeatureSequence) instance.getData();
			LabelSequence topicSequence =
				new LabelSequence(topicAlphabet, new int[ tokens.size() ]);
			
			int[] topics = topicSequence.getFeatures();
			for (int position = 0; position < topics.length; position++) {

				int topic = random.nextInt(numTopics);
				topics[position] = topic;
				
			}

			TopicAssignment t = new TopicAssignment (instance, topicSequence);
			data.add (t);
		}
		
		buildInitialTypeTopicCounts();
		initializeHistograms();
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
					topics[position] = Integer.parseInt(fields[5]);
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
		
		buildInitialTypeTopicCounts();
		initializeHistograms();
	}

	public void buildInitialTypeTopicCounts () {

		// Clear the topic totals
		Arrays.fill(tokensPerTopic, 0);
		
		// Clear the type/topic counts, only 
		//  looking at the entries before the first 0 entry.

		for (int type = 0; type < numTypes; type++) {
			
			int[] topicCounts = typeTopicCounts[type];
			
			int position = 0;
			while (position < topicCounts.length && 
				   topicCounts[position] > 0) {
				topicCounts[position] = 0;
				position++;
			}

		}

		for (TopicAssignment document : data) {

			FeatureSequence tokens = (FeatureSequence) document.instance.getData();
			FeatureSequence topicSequence =  (FeatureSequence) document.topicSequence;

			int[] topics = topicSequence.getFeatures();
			for (int position = 0; position < tokens.size(); position++) {

				int topic = topics[position];
				
				if (topic == UNASSIGNED_TOPIC) { continue; }

				tokensPerTopic[topic]++;
				
				// The format for these arrays is 
				//  the topic in the rightmost bits
				//  the count in the remaining (left) bits.
				// Since the count is in the high bits, sorting (desc)
				//  by the numeric value of the int guarantees that
				//  higher counts will be before the lower counts.
				
				int type = tokens.getIndexAtPosition(position);
				int[] currentTypeTopicCounts = typeTopicCounts[ type ];
		
				// Start by assuming that the array is either empty
				//  or is in sorted (descending) order.
				
				// Here we are only adding counts, so if we find 
				//  an existing location with the topic, we only need
				//  to ensure that it is not larger than its left neighbor.
				
				int index = 0;
				int currentTopic = currentTypeTopicCounts[index] & topicMask;
				int currentValue;
				
				while (currentTypeTopicCounts[index] > 0 && currentTopic != topic) {
					index++;
					if (index == currentTypeTopicCounts.length) {
						logger.info("overflow on type " + type);
					}
					currentTopic = currentTypeTopicCounts[index] & topicMask;
				}
				currentValue = currentTypeTopicCounts[index] >> topicBits;
				
				if (currentValue == 0) {
					// new value is 1, so we don't have to worry about sorting
					//  (except by topic suffix, which doesn't matter)
					
					currentTypeTopicCounts[index] =
						(1 << topicBits) + topic;
				}
				else {
					currentTypeTopicCounts[index] =
						((currentValue + 1) << topicBits) + topic;
					
					// Now ensure that the array is still sorted by 
					//  bubbling this value up.
					while (index > 0 &&
						   currentTypeTopicCounts[index] > currentTypeTopicCounts[index - 1]) {
						int temp = currentTypeTopicCounts[index];
						currentTypeTopicCounts[index] = currentTypeTopicCounts[index - 1];
						currentTypeTopicCounts[index - 1] = temp;
						
						index--;
					}
				}
			}
		}
	}
	

	public void sumTypeTopicCounts (WorkerRunnable[] runnables) {

		// Clear the topic totals
		Arrays.fill(tokensPerTopic, 0);
		
		// Clear the type/topic counts, only 
		//  looking at the entries before the first 0 entry.

		for (int type = 0; type < numTypes; type++) {
			
			int[] targetCounts = typeTopicCounts[type];
			
			int position = 0;
			while (position < targetCounts.length && 
				   targetCounts[position] > 0) {
				targetCounts[position] = 0;
				position++;
			}

		}

		for (int thread = 0; thread < numThreads; thread++) {

			// Handle the total-tokens-per-topic array

			int[] sourceTotals = runnables[thread].getTokensPerTopic();
			for (int topic = 0; topic < numTopics; topic++) {
				tokensPerTopic[topic] += sourceTotals[topic];
			}
			
			// Now handle the individual type topic counts
			
			int[][] sourceTypeTopicCounts = 
				runnables[thread].getTypeTopicCounts();
			
			for (int type = 0; type < numTypes; type++) {

				// Here the source is the individual thread counts,
				//  and the target is the global counts.

				int[] sourceCounts = sourceTypeTopicCounts[type];
				int[] targetCounts = typeTopicCounts[type];

				int sourceIndex = 0;
				while (sourceIndex < sourceCounts.length &&
					   sourceCounts[sourceIndex] > 0) {
					
					int topic = sourceCounts[sourceIndex] & topicMask;
					int count = sourceCounts[sourceIndex] >> topicBits;

					int targetIndex = 0;
					int currentTopic = targetCounts[targetIndex] & topicMask;
					int currentCount;
					
					while (targetCounts[targetIndex] > 0 && currentTopic != topic) {
						targetIndex++;
						if (targetIndex == targetCounts.length) {
							logger.info("overflow in merging on type " + type);
						}
						currentTopic = targetCounts[targetIndex] & topicMask;
					}
					currentCount = targetCounts[targetIndex] >> topicBits;
					
					targetCounts[targetIndex] =
						((currentCount + count) << topicBits) + topic;
					
					// Now ensure that the array is still sorted by 
					//  bubbling this value up.
					while (targetIndex > 0 &&
						   targetCounts[targetIndex] > targetCounts[targetIndex - 1]) {
						int temp = targetCounts[targetIndex];
						targetCounts[targetIndex] = targetCounts[targetIndex - 1];
						targetCounts[targetIndex - 1] = temp;
						
						targetIndex--;
					}
					
					sourceIndex++;
				}
				
			}
		}

		/* // Debuggging code to ensure counts are being 
		   // reconstructed correctly.

		for (int type = 0; type < numTypes; type++) {
			
			int[] targetCounts = typeTopicCounts[type];
			
			int index = 0;
			int count = 0;
			while (index < targetCounts.length &&
				   targetCounts[index] > 0) {
				count += targetCounts[index] >> topicBits;
				index++;
			}
			
			if (count != typeTotals[type]) {
				System.err.println("Expected " + typeTotals[type] + ", found " + count);
			}
			
		}
		*/
	}
	

	/** 
	 *  Gather statistics on the size of documents 
	 *  and create histograms for use in Dirichlet hyperparameter
	 *  optimization.
	 */
	private void initializeHistograms() {

		int maxTokens = 0;
		totalTokens = 0;
		int seqLen;

		for (int doc = 0; doc < data.size(); doc++) {
			FeatureSequence fs = (FeatureSequence) data.get(doc).instance.getData();
			seqLen = fs.getLength();
			if (seqLen > maxTokens)
				maxTokens = seqLen;
			totalTokens += seqLen;
		}

		logger.info("max tokens: " + maxTokens);
		logger.info("total tokens: " + totalTokens);

		docLengthCounts = new int[maxTokens + 1];
		topicDocCounts = new int[numTopics][maxTokens + 1];
	}
	
	public void optimizeAlpha(WorkerRunnable[] runnables) {

		// First clear the sufficient statistic histograms

		Arrays.fill(docLengthCounts, 0);
		for (int topic = 0; topic < topicDocCounts.length; topic++) {
			Arrays.fill(topicDocCounts[topic], 0);
		}

		for (int thread = 0; thread < numThreads; thread++) {
			int[] sourceLengthCounts = runnables[thread].getDocLengthCounts();
			int[][] sourceTopicCounts = runnables[thread].getTopicDocCounts();

			for (int count=0; count < sourceLengthCounts.length; count++) {
				if (sourceLengthCounts[count] > 0) {
					docLengthCounts[count] += sourceLengthCounts[count];
					sourceLengthCounts[count] = 0;
				}
			}

			for (int topic=0; topic < numTopics; topic++) {

				if (! usingSymmetricAlpha) {
					for (int count=0; count < sourceTopicCounts[topic].length; count++) {
						if (sourceTopicCounts[topic][count] > 0) {
							topicDocCounts[topic][count] += sourceTopicCounts[topic][count];
							sourceTopicCounts[topic][count] = 0;
						}
					}
				}
				else {
					// For the symmetric version, we only need one 
					//  count array, which I'm putting in the same 
					//  data structure, but for topic 0. All other
					//  topic histograms will be empty.
					// I'm duplicating this for loop, which 
					//  isn't the best thing, but it means only checking
					//  whether we are symmetric or not numTopics times, 
					//  instead of numTopics * longest document length.
					for (int count=0; count < sourceTopicCounts[topic].length; count++) {
						if (sourceTopicCounts[topic][count] > 0) {
							topicDocCounts[0][count] += sourceTopicCounts[topic][count];
							//			 ^ the only change
							sourceTopicCounts[topic][count] = 0;
						}
					}
				}
			}
		}

		if (usingSymmetricAlpha) {
			alphaSum = Dirichlet.learnSymmetricConcentration(topicDocCounts[0],
															 docLengthCounts,
															 numTopics,
															 alphaSum);
			for (int topic = 0; topic < numTopics; topic++) {
				alpha[topic] = alphaSum / numTopics;
			}
		}
		else {
			alphaSum = Dirichlet.learnParameters(alpha, topicDocCounts, docLengthCounts, 1.001, 1.0, 1);
		}
	}

	public void temperAlpha(WorkerRunnable[] runnables) {
		
		// First clear the sufficient statistic histograms

		Arrays.fill(docLengthCounts, 0);
		for (int topic = 0; topic < topicDocCounts.length; topic++) {
			Arrays.fill(topicDocCounts[topic], 0);
		}

		for (int thread = 0; thread < numThreads; thread++) {
			int[] sourceLengthCounts = runnables[thread].getDocLengthCounts();
			int[][] sourceTopicCounts = runnables[thread].getTopicDocCounts();

			for (int count=0; count < sourceLengthCounts.length; count++) {
				if (sourceLengthCounts[count] > 0) {
					sourceLengthCounts[count] = 0;
				}
			}

			for (int topic=0; topic < numTopics; topic++) {

				for (int count=0; count < sourceTopicCounts[topic].length; count++) {
					if (sourceTopicCounts[topic][count] > 0) {
						sourceTopicCounts[topic][count] = 0;
					}
				}
			}
		}

		for (int topic = 0; topic < numTopics; topic++) {
			alpha[topic] = 1.0;
		}
		alphaSum = numTopics;
	}

	public void optimizeBeta(WorkerRunnable[] runnables) {
		// The histogram starts at count 0, so if all of the
		//  tokens of the most frequent type were assigned to one topic,
		//  we would need to store a maxTypeCount + 1 count.
		int[] countHistogram = new int[maxTypeCount + 1];
		
		// Now count the number of type/topic pairs that have
		//  each number of tokens.

		int index;
		for (int type = 0; type < numTypes; type++) {
			int[] counts = typeTopicCounts[type];
			index = 0;
			while (index < counts.length &&
				   counts[index] > 0) {
				int count = counts[index] >> topicBits;
				countHistogram[count]++;
				index++;
			}
		}
			
		// Figure out how large we need to make the "observation lengths"
		//  histogram.
		int maxTopicSize = 0;
		for (int topic = 0; topic < numTopics; topic++) {
			if (tokensPerTopic[topic] > maxTopicSize) {
				maxTopicSize = tokensPerTopic[topic];
			}
		}

		// Now allocate it and populate it.
		int[] topicSizeHistogram = new int[maxTopicSize + 1];
		for (int topic = 0; topic < numTopics; topic++) {
			topicSizeHistogram[ tokensPerTopic[topic] ]++;
		}

		betaSum = Dirichlet.learnSymmetricConcentration(countHistogram,
														topicSizeHistogram,
														numTypes,
														betaSum);
		beta = betaSum / numTypes;
		

		logger.info("[beta: " + formatter.format(beta) + "] ");
		// Now publish the new value
		for (int thread = 0; thread < numThreads; thread++) {
			runnables[thread].resetBeta(beta, betaSum);
		}
		
	}

	public void estimate () throws IOException {

		long startTime = System.currentTimeMillis();

		WorkerRunnable[] runnables = new WorkerRunnable[numThreads];

		int docsPerThread = data.size() / numThreads;
		int offset = 0;

		if (numThreads > 1) {
		
			for (int thread = 0; thread < numThreads; thread++) {
				int[] runnableTotals = new int[numTopics];
				System.arraycopy(tokensPerTopic, 0, runnableTotals, 0, numTopics);
				
				int[][] runnableCounts = new int[numTypes][];
				for (int type = 0; type < numTypes; type++) {
					int[] counts = new int[typeTopicCounts[type].length];
					System.arraycopy(typeTopicCounts[type], 0, counts, 0, counts.length);
					runnableCounts[type] = counts;
				}
				
				// some docs may be missing at the end due to integer division
				if (thread == numThreads - 1) {
					docsPerThread = data.size() - offset;
				}
				
				Randoms random = null;
				if (randomSeed == -1) {
					random = new Randoms();
				}
				else {
					random = new Randoms(randomSeed);
				}

				runnables[thread] = new WorkerRunnable(numTopics,
													   alpha, alphaSum, beta,
													   random, data,
													   runnableCounts, runnableTotals,
													   offset, docsPerThread);
				
				runnables[thread].initializeAlphaStatistics(docLengthCounts.length);
				
				offset += docsPerThread;
			
			}
		}
		else {
			
			// If there is only one thread, copy the typeTopicCounts
			//  arrays directly, rather than allocating new memory.

			Randoms random = null;
			if (randomSeed == -1) {
				random = new Randoms();
			}
			else {
				random = new Randoms(randomSeed);
			}

			runnables[0] = new WorkerRunnable(numTopics,
											  alpha, alphaSum, beta,
											  random, data,
											  typeTopicCounts, tokensPerTopic,
											  offset, docsPerThread);

			runnables[0].initializeAlphaStatistics(docLengthCounts.length);

			// If there is only one thread, we 
			//  can avoid communications overhead.
			// This switch informs the thread not to 
			//  gather statistics for its portion of the data.
			runnables[0].makeOnlyThread();
		}

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
	
		for (int iteration = 1; iteration <= numIterations; iteration++) {

			long iterationStart = System.currentTimeMillis();

			if (showTopicsInterval != 0 && iteration != 0 && iteration % showTopicsInterval == 0) {
				logger.info("\n" + displayTopWords (wordsPerTopic, false));
			}

			if (saveStateInterval != 0 && iteration % saveStateInterval == 0) {
				this.printState(new File(stateFilename + '.' + iteration));
			}

			if (saveModelInterval != 0 && iteration % saveModelInterval == 0) {
				this.write(new File(modelFilename + '.' + iteration));
			}

			if (numThreads > 1) {
			
				// Submit runnables to thread pool
				
				for (int thread = 0; thread < numThreads; thread++) {
					if (iteration > burninPeriod && optimizeInterval != 0 &&
						iteration % saveSampleInterval == 0) {
						runnables[thread].collectAlphaStatistics();
					}
					
					logger.fine("submitting thread " + thread);
					executor.submit(runnables[thread]);
					//runnables[thread].run();
				}
				
				// I'm getting some problems that look like 
				//  a thread hasn't started yet when it is first
				//  polled, so it appears to be finished. 
				// This only occurs in very short corpora.
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					
				}
				
				boolean finished = false;
				while (! finished) {
					
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						
					}
					
					finished = true;
					
					// Are all the threads done?
					for (int thread = 0; thread < numThreads; thread++) {
						//logger.info("thread " + thread + " done? " + runnables[thread].isFinished);
						finished = finished && runnables[thread].isFinished;
					}
					
				}
				
				//System.out.print("[" + (System.currentTimeMillis() - iterationStart) + "] ");
				
				sumTypeTopicCounts(runnables);
				
				//System.out.print("[" + (System.currentTimeMillis() - iterationStart) + "] ");
				
				for (int thread = 0; thread < numThreads; thread++) {
					int[] runnableTotals = runnables[thread].getTokensPerTopic();
					System.arraycopy(tokensPerTopic, 0, runnableTotals, 0, numTopics);
					
					int[][] runnableCounts = runnables[thread].getTypeTopicCounts();
					for (int type = 0; type < numTypes; type++) {
						int[] targetCounts = runnableCounts[type];
						int[] sourceCounts = typeTopicCounts[type];
						
						int index = 0;
						while (index < sourceCounts.length) {
							
							if (sourceCounts[index] != 0) {
								targetCounts[index] = sourceCounts[index];
							}
							else if (targetCounts[index] != 0) {
								targetCounts[index] = 0;
							}
							else {
								break;
							}
							
							index++;
						}
						//System.arraycopy(typeTopicCounts[type], 0, counts, 0, counts.length);
					}
				}
			}
			else {
				if (iteration > burninPeriod && optimizeInterval != 0 &&
					iteration % saveSampleInterval == 0) {
					runnables[0].collectAlphaStatistics();
				}
				runnables[0].run();
			}

			long elapsedMillis = System.currentTimeMillis() - iterationStart;
			if (elapsedMillis < 1000) {
				logger.fine(elapsedMillis + "ms ");
			}
			else {
				logger.fine((elapsedMillis/1000) + "s ");
			}   

			if (iteration > burninPeriod && optimizeInterval != 0 &&
				iteration % optimizeInterval == 0) {

				optimizeAlpha(runnables);
				optimizeBeta(runnables);
				
				logger.fine("[O " + (System.currentTimeMillis() - iterationStart) + "] ");
			}
			
			if (iteration % 10 == 0) {
				if (printLogLikelihood) {
					logger.info ("<" + iteration + "> LL/token: " + formatter.format(modelLogLikelihood() / totalTokens));
				}
				else {
					logger.info ("<" + iteration + ">");
				}
			}
		}

		executor.shutdownNow();
	
		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;

		StringBuilder timeReport = new StringBuilder();
		timeReport.append("\nTotal time: ");
		if (days != 0) { timeReport.append(days); timeReport.append(" days "); }
		if (hours != 0) { timeReport.append(hours); timeReport.append(" hours "); }
		if (minutes != 0) { timeReport.append(minutes); timeReport.append(" minutes "); }
		timeReport.append(seconds); timeReport.append(" seconds");
		
		logger.info(timeReport.toString());
	}
	
	public void printTopWords (File file, int numWords, boolean useNewLines) throws IOException {
		PrintStream out = new PrintStream (file);
		printTopWords(out, numWords, useNewLines);
		out.close();
	}
	

	/**
	 *  Return an array of sorted sets (one set per topic). Each set 
	 *   contains IDSorter objects with integer keys into the alphabet.
	 *   To get direct access to the Strings, use getTopWords().
	 */
	public ArrayList<TreeSet<IDSorter>> getSortedWords () {
	
		ArrayList<TreeSet<IDSorter>> topicSortedWords = new ArrayList<TreeSet<IDSorter>>(numTopics);

		// Initialize the tree sets
		for (int topic = 0; topic < numTopics; topic++) {
			topicSortedWords.add(new TreeSet<IDSorter>());
		}

		// Collect counts
		for (int type = 0; type < numTypes; type++) {

			int[] topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {

				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;

				topicSortedWords.get(topic).add(new IDSorter(type, count));

				index++;
			}
		}

		return topicSortedWords;
	}

	/** Return an array (one element for each topic) of arrays of words, which
	 *  are the most probable words for that topic in descending order. These
	 *  are returned as Objects, but will probably be Strings.
	 *
	 *  @param numWords The maximum length of each topic's array of words (may be less).
	 */
	
	public Object[][] getTopWords(int numWords) {

		ArrayList<TreeSet<IDSorter>> topicSortedWords = getSortedWords();
		Object[][] result = new Object[ numTopics ][];

		for (int topic = 0; topic < numTopics; topic++) {
			
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);
			
			// How many words should we report? Some topics may have fewer than
			//  the default number of words with non-zero weight.
			int limit = numWords;
			if (sortedWords.size() < numWords) { limit = sortedWords.size(); }

			result[topic] = new Object[limit];

			Iterator<IDSorter> iterator = sortedWords.iterator();
			for (int i=0; i < limit; i++) {
				IDSorter info = iterator.next();
				result[topic][i] = alphabet.lookupObject(info.getID());
			}
		}

		return result;
	}

	public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {
		out.print(displayTopWords(numWords, usingNewLines));
	}

	public String displayTopWords (int numWords, boolean usingNewLines) {

		StringBuilder out = new StringBuilder();

		ArrayList<TreeSet<IDSorter>> topicSortedWords = getSortedWords();

		// Print results for each topic
		for (int topic = 0; topic < numTopics; topic++) {
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);
			int word = 0;
			Iterator<IDSorter> iterator = sortedWords.iterator();

			if (usingNewLines) {
				out.append (topic + "\t" + formatter.format(alpha[topic]) + "\n");
				while (iterator.hasNext() && word < numWords) {
					IDSorter info = iterator.next();
					out.append(alphabet.lookupObject(info.getID()) + "\t" + formatter.format(info.getWeight()) + "\n");
					word++;
				}
			}
			else {
				out.append (topic + "\t" + formatter.format(alpha[topic]) + "\t");

				while (iterator.hasNext() && word < numWords) {
					IDSorter info = iterator.next();
					out.append(alphabet.lookupObject(info.getID()) + " ");
					word++;
				}
				out.append ("\n");
			}
		}

		return out.toString();
	}
	
	public void topicXMLReport (PrintWriter out, int numWords) {
		ArrayList<TreeSet<IDSorter>> topicSortedWords = getSortedWords();
		out.println("<?xml version='1.0' ?>");
		out.println("<topicModel>");
		for (int topic = 0; topic < numTopics; topic++) {
			out.println("  <topic id='" + topic + "' alpha='" + alpha[topic] +
						"' totalTokens='" + tokensPerTopic[topic] + "'>");
			int word = 1;
			Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
			while (iterator.hasNext() && word <= numWords) {
				IDSorter info = iterator.next();
				out.println("	<word rank='" + word + "'>" +
						  alphabet.lookupObject(info.getID()) +
						  "</word>");
				word++;
			}
			out.println("  </topic>");
		}
		out.println("</topicModel>");
	}

	public void topicPhraseXMLReport(PrintWriter out, int numWords) {
		int numTopics = this.getNumTopics();
		gnu.trove.TObjectIntHashMap<String>[] phrases = new gnu.trove.TObjectIntHashMap[numTopics];
		Alphabet alphabet = this.getAlphabet();
		
		// Get counts of phrases
		for (int ti = 0; ti < numTopics; ti++)
			phrases[ti] = new gnu.trove.TObjectIntHashMap<String>();
		for (int di = 0; di < this.getData().size(); di++) {
			TopicAssignment t = this.getData().get(di);
			Instance instance = t.instance;
			FeatureSequence fvs = (FeatureSequence) instance.getData();
			boolean withBigrams = false;
			if (fvs instanceof FeatureSequenceWithBigrams) withBigrams = true;
			int prevtopic = -1;
			int prevfeature = -1;
			int topic = -1;
			StringBuffer sb = null;
			int feature = -1;
			int doclen = fvs.size();
			for (int pi = 0; pi < doclen; pi++) {
				feature = fvs.getIndexAtPosition(pi);
				topic = this.getData().get(di).topicSequence.getIndexAtPosition(pi);
				if (topic == prevtopic && (!withBigrams || ((FeatureSequenceWithBigrams)fvs).getBiIndexAtPosition(pi) != -1)) {
					if (sb == null)
						sb = new StringBuffer (alphabet.lookupObject(prevfeature).toString() + " " + alphabet.lookupObject(feature));
					else {
						sb.append (" ");
						sb.append (alphabet.lookupObject(feature));
					}
				} else if (sb != null) {
					String sbs = sb.toString();
					//logger.info ("phrase:"+sbs);
					if (phrases[prevtopic].get(sbs) == 0)
						phrases[prevtopic].put(sbs,0);
					phrases[prevtopic].increment(sbs);
					prevtopic = prevfeature = -1;
					sb = null;
				} else {
					prevtopic = topic;
					prevfeature = feature;
				}
			}
		}
		// phrases[] now filled with counts
		
		// Now start printing the XML
		out.println("<?xml version='1.0' ?>");
		out.println("<topics>");

		ArrayList<TreeSet<IDSorter>> topicSortedWords = getSortedWords();
		double[] probs = new double[alphabet.size()];
		for (int ti = 0; ti < numTopics; ti++) {
			out.print("  <topic id=\"" + ti + "\" alpha=\"" + alpha[ti] +
					"\" totalTokens=\"" + tokensPerTopic[ti] + "\" ");

			// For gathering <term> and <phrase> output temporarily 
			// so that we can get topic-title information before printing it to "out".
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			PrintStream pout = new PrintStream (bout);
			// For holding candidate topic titles
			AugmentableFeatureVector titles = new AugmentableFeatureVector (new Alphabet());

			// Print words
			int word = 0;
			Iterator<IDSorter> iterator = topicSortedWords.get(ti).iterator();
			while (iterator.hasNext() && word < numWords) {
				IDSorter info = iterator.next();
				pout.println("	<word weight=\""+(info.getWeight()/tokensPerTopic[ti])+"\" count=\""+Math.round(info.getWeight())+"\">"
							+ alphabet.lookupObject(info.getID()) +
						  "</word>");
				word++;
				if (word < 20) // consider top 20 individual words as candidate titles
					titles.add(alphabet.lookupObject(info.getID()), info.getWeight());
			}

			/*
			for (int type = 0; type < alphabet.size(); type++)
				probs[type] = this.getCountFeatureTopic(type, ti) / (double)this.getCountTokensPerTopic(ti);
			RankedFeatureVector rfv = new RankedFeatureVector (alphabet, probs);
			for (int ri = 0; ri < numWords; ri++) {
				int fi = rfv.getIndexAtRank(ri);
				pout.println ("	  <term weight=\""+probs[fi]+"\" count=\""+this.getCountFeatureTopic(fi,ti)+"\">"+alphabet.lookupObject(fi)+	"</term>");
				if (ri < 20) // consider top 20 individual words as candidate titles
					titles.add(alphabet.lookupObject(fi), this.getCountFeatureTopic(fi,ti));
			}
			*/

			// Print phrases
			Object[] keys = phrases[ti].keys();
			int[] values = phrases[ti].getValues();
			double counts[] = new double[keys.length];
			for (int i = 0; i < counts.length; i++)	counts[i] = values[i];
			double countssum = MatrixOps.sum (counts);	
			Alphabet alph = new Alphabet(keys);
			RankedFeatureVector rfv = new RankedFeatureVector (alph, counts);
			int max = rfv.numLocations() < numWords ? rfv.numLocations() : numWords;
			for (int ri = 0; ri < max; ri++) {
				int fi = rfv.getIndexAtRank(ri);
				pout.println ("	<phrase weight=\""+counts[fi]/countssum+"\" count=\""+values[fi]+"\">"+alph.lookupObject(fi)+	"</phrase>");
				// Any phrase count less than 20 is simply unreliable
				if (ri < 20 && values[fi] > 20) 
					titles.add(alph.lookupObject(fi), 100*values[fi]); // prefer phrases with a factor of 100 
			}
			
			// Select candidate titles
			StringBuffer titlesStringBuffer = new StringBuffer();
			rfv = new RankedFeatureVector (titles.getAlphabet(), titles);
			int numTitles = 10; 
			for (int ri = 0; ri < numTitles && ri < rfv.numLocations(); ri++) {
				// Don't add redundant titles
				if (titlesStringBuffer.indexOf(rfv.getObjectAtRank(ri).toString()) == -1) {
					titlesStringBuffer.append (rfv.getObjectAtRank(ri));
					if (ri < numTitles-1)
						titlesStringBuffer.append (", ");
				} else
					numTitles++;
			}
			out.println("titles=\"" + titlesStringBuffer.toString() + "\">");
			out.print(bout.toString());
			out.println("  </topic>");
		}
		out.println("</topics>");
	}

	

	/**
	 *  Write the internal representation of type-topic counts  
	 *   (count/topic pairs in descending order by count) to a file.
	 */
	public void printTypeTopicCounts(File file) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );

		for (int type = 0; type < numTypes; type++) {

			StringBuilder buffer = new StringBuilder();

			buffer.append(type + " " + alphabet.lookupObject(type));

			int[] topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {

				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;
				
				buffer.append(" " + topic + ":" + count);

				index++;
			}

			out.println(buffer);
		}

		out.close();
	}

	public void printTopicWordWeights(File file) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printTopicWordWeights(out);
		out.close();
	}

	/**
	 * Print an unnormalized weight for every word in every topic.
	 *  Most of these will be equal to the smoothing parameter beta.
	 */
	public void printTopicWordWeights(PrintWriter out) throws IOException {
		// Probably not the most efficient way to do this...

		for (int topic = 0; topic < numTopics; topic++) {
			for (int type = 0; type < numTypes; type++) {

				int[] topicCounts = typeTopicCounts[type];
				
				double weight = beta;

				int index = 0;
				while (index < topicCounts.length &&
					   topicCounts[index] > 0) {

					int currentTopic = topicCounts[index] & topicMask;
					
					
					if (currentTopic == topic) {
						weight += topicCounts[index] >> topicBits;
						break;
					}

					index++;
				}

				out.println(topic + "\t" + alphabet.lookupObject(type) + "\t" + weight);

			}
		}
	}
	
	/** Get the smoothed distribution over topics for a training instance. 
	 */
	public double[] getTopicProbabilities(int instanceID) {
		LabelSequence topics = data.get(instanceID).topicSequence;
		return getTopicProbabilities(topics);
	}

	/** Get the smoothed distribution over topics for a topic sequence, 
	 * which may be from the training set or from a new instance with topics
	 * assigned by an inferencer.
	 */
	public double[] getTopicProbabilities(LabelSequence topics) {
		double[] topicDistribution = new double[numTopics];

		// Loop over the tokens in the document, counting the current topic
		//  assignments.
		for (int position = 0; position < topics.getLength(); position++) {
			topicDistribution[ topics.getIndexAtPosition(position) ]++;
		}

		// Add the smoothing parameters and normalize
		double sum = 0.0;
		for (int topic = 0; topic < numTopics; topic++) {
			topicDistribution[topic] += alpha[topic];
			sum += topicDistribution[topic];
		}

		// And normalize
		for (int topic = 0; topic < numTopics; topic++) {
			topicDistribution[topic] /= sum;
		}

		return topicDistribution;
	}

	public void printDocumentTopics (File file) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printDocumentTopics (out);
		out.close();
	}

	public void printDocumentTopics (PrintWriter out) {
		printDocumentTopics (out, 0.0, -1);
	}

	/**
	 *  @param out		  A print writer
	 *  @param threshold   Only print topics with proportion greater than this number
	 *  @param max		 Print no more than this many topics
	 */
	public void printDocumentTopics (PrintWriter out, double threshold, int max)	{
		out.print ("#doc name topic proportion ...\n");
		int docLen;
		int[] topicCounts = new int[ numTopics ];

		IDSorter[] sortedTopics = new IDSorter[ numTopics ];
		for (int topic = 0; topic < numTopics; topic++) {
			// Initialize the sorters with dummy values
			sortedTopics[topic] = new IDSorter(topic, topic);
		}

		if (max < 0 || max > numTopics) {
			max = numTopics;
		}

		for (int doc = 0; doc < data.size(); doc++) {
			LabelSequence topicSequence = (LabelSequence) data.get(doc).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();

			StringBuilder builder = new StringBuilder();

			builder.append(doc);
			builder.append("\t");

			if (data.get(doc).instance.getName() != null) {
				builder.append(data.get(doc).instance.getName()); 
			}
			else {
				builder.append("no-name");
			}

			builder.append("\t");
			docLen = currentDocTopics.length;

			// Count up the tokens
			for (int token=0; token < docLen; token++) {
				topicCounts[ currentDocTopics[token] ]++;
			}

			// And normalize
			for (int topic = 0; topic < numTopics; topic++) {
				sortedTopics[topic].set(topic, (alpha[topic] + topicCounts[topic]) / (docLen + alphaSum) );
			}
			
			Arrays.sort(sortedTopics);

			for (int i = 0; i < max; i++) {
				if (sortedTopics[i].getWeight() < threshold) { break; }
				
				builder.append(sortedTopics[i].getID() + "\t" + 
							   sortedTopics[i].getWeight() + "\t");
			}
			out.println(builder);

			Arrays.fill(topicCounts, 0);
		}
		
	}
	
	public void printState (File f) throws IOException {
		PrintStream out =
			new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(f))));
		printState(out);
		out.close();
	}
	
	public void printState (PrintStream out) {

		out.println ("#doc source pos typeindex type topic");
		out.print("#alpha : ");
		for (int topic = 0; topic < numTopics; topic++) {
			out.print(alpha[topic] + " ");
		}
		out.println();
		out.println("#beta : " + beta);

		for (int doc = 0; doc < data.size(); doc++) {
			FeatureSequence tokenSequence =	(FeatureSequence) data.get(doc).instance.getData();
			LabelSequence topicSequence =	(LabelSequence) data.get(doc).topicSequence;

			String source = "NA";
			if (data.get(doc).instance.getSource() != null) {
				source = data.get(doc).instance.getSource().toString();
			}

			Formatter output = new Formatter(new StringBuilder(), Locale.US);

			for (int pi = 0; pi < topicSequence.getLength(); pi++) {
				int type = tokenSequence.getIndexAtPosition(pi);
				int topic = topicSequence.getIndexAtPosition(pi);

				output.format("%d %s %d %d %s %d\n", doc, source, pi, type, alphabet.lookupObject(type), topic);

				/*
				out.print(doc); out.print(' ');
				out.print(source); out.print(' '); 
				out.print(pi); out.print(' ');
				out.print(type); out.print(' ');
				out.print(alphabet.lookupObject(type)); out.print(' ');
				out.print(topic); out.println();
				*/
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
			topicLogGammas[ topic ] = Dirichlet.logGammaStirling( alpha[topic] );
		}
	
		for (int doc=0; doc < data.size(); doc++) {
			LabelSequence topicSequence =	(LabelSequence) data.get(doc).topicSequence;

			docTopics = topicSequence.getFeatures();

			for (int token=0; token < docTopics.length; token++) {
				topicCounts[ docTopics[token] ]++;
			}

			for (int topic=0; topic < numTopics; topic++) {
				if (topicCounts[topic] > 0) {
					logLikelihood += (Dirichlet.logGammaStirling(alpha[topic] + topicCounts[topic]) -
									  topicLogGammas[ topic ]);
				}
			}

			// subtract the (count + parameter) sum term
			logLikelihood -= Dirichlet.logGammaStirling(alphaSum + docTopics.length);

			Arrays.fill(topicCounts, 0);
		}

		// add the parameter sum term
		logLikelihood += data.size() * Dirichlet.logGammaStirling(alphaSum);

		// And the topics

		// Count the number of type-topic pairs that are not just (logGamma(beta) - logGamma(beta))
		int nonZeroTypeTopics = 0;

		for (int type=0; type < numTypes; type++) {
			// reuse this array as a pointer

			topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {
				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;
				
				nonZeroTypeTopics++;
				logLikelihood += Dirichlet.logGammaStirling(beta + count);

				if (Double.isNaN(logLikelihood)) {
					logger.warning("NaN in log likelihood calculation");
					return 0;
				}
				else if (Double.isInfinite(logLikelihood)) {
					logger.warning("infinite log likelihood");
					return 0;
				}

				index++;
			}
		}
	
		for (int topic=0; topic < numTopics; topic++) {
			logLikelihood -= 
				Dirichlet.logGammaStirling( (beta * numTypes) +
											tokensPerTopic[ topic ] );

			if (Double.isNaN(logLikelihood)) {
				logger.info("NaN after topic " + topic + " " + tokensPerTopic[ topic ]);
				return 0;
			}
			else if (Double.isInfinite(logLikelihood)) {
				logger.info("Infinite value after topic " + topic + " " + tokensPerTopic[ topic ]);
				return 0;
			}

		}
	
		// logGamma(|V|*beta) for every topic
		logLikelihood += 
			Dirichlet.logGammaStirling(beta * numTypes) * numTopics;

		// logGamma(beta) for all type/topic pairs with non-zero count
		logLikelihood -=
			Dirichlet.logGammaStirling(beta) * nonZeroTypeTopics;

		if (Double.isNaN(logLikelihood)) {
			logger.info("at the end");
		}
		else if (Double.isInfinite(logLikelihood)) {
			logger.info("Infinite value beta " + beta + " * " + numTypes);
			return 0;
		}

		return logLikelihood;
	}


	/** Return a tool for estimating topic distributions for new documents */
	public TopicInferencer getInferencer() {
		return new TopicInferencer(typeTopicCounts, tokensPerTopic,
								   data.get(0).instance.getDataAlphabet(),
								   alpha, beta, betaSum);
	}

	/** Return a tool for evaluating the marginal probability of new documents
	 *   under this model */
	public MarginalProbEstimator getProbEstimator() {
		return new MarginalProbEstimator(numTopics, alpha, alphaSum, beta,
										 typeTopicCounts, tokensPerTopic);
	}

	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		
		out.writeObject(data);
		out.writeObject(alphabet);
		out.writeObject(topicAlphabet);

		out.writeInt(numTopics);

		out.writeInt(topicMask);
		out.writeInt(topicBits);

		out.writeInt(numTypes);

		out.writeObject(alpha);
		out.writeDouble(alphaSum);
		out.writeDouble(beta);
		out.writeDouble(betaSum);

		out.writeObject(typeTopicCounts);
		out.writeObject(tokensPerTopic);

		out.writeObject(docLengthCounts);
		out.writeObject(topicDocCounts);

		out.writeInt(numIterations);
		out.writeInt(burninPeriod);
		out.writeInt(saveSampleInterval);
		out.writeInt(optimizeInterval);
		out.writeInt(showTopicsInterval);
		out.writeInt(wordsPerTopic);

		out.writeInt(saveStateInterval);
		out.writeObject(stateFilename);

		out.writeInt(saveModelInterval);
		out.writeObject(modelFilename);

		out.writeInt(randomSeed);
		out.writeObject(formatter);
		out.writeBoolean(printLogLikelihood);

		out.writeInt(numThreads);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		
		int version = in.readInt ();

		data = (ArrayList<TopicAssignment>) in.readObject ();
		alphabet = (Alphabet) in.readObject();
		topicAlphabet = (LabelAlphabet) in.readObject();
		
		numTopics = in.readInt();
		
		topicMask = in.readInt();
		topicBits = in.readInt();
		
		numTypes = in.readInt();
		
		alpha = (double[]) in.readObject();
		alphaSum = in.readDouble();
		beta = in.readDouble();
		betaSum = in.readDouble();
		
		typeTopicCounts = (int[][]) in.readObject();
		tokensPerTopic = (int[]) in.readObject();
		
		docLengthCounts = (int[]) in.readObject();
		topicDocCounts = (int[][]) in.readObject();
	
		numIterations = in.readInt();
		burninPeriod = in.readInt();
		saveSampleInterval = in.readInt();
		optimizeInterval = in.readInt();
		showTopicsInterval = in.readInt();
		wordsPerTopic = in.readInt();

		saveStateInterval = in.readInt();
		stateFilename = (String) in.readObject();
		
		saveModelInterval = in.readInt();
		modelFilename = (String) in.readObject();
		
		randomSeed = in.readInt();
		formatter = (NumberFormat) in.readObject();
		printLogLikelihood = in.readBoolean();

		numThreads = in.readInt();
	}

	public void write (File serializedModelFile) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(serializedModelFile));
			oos.writeObject(this);
			oos.close();
		} catch (IOException e) {
			System.err.println("Problem serializing ParallelTopicModel to file " +
							   serializedModelFile + ": " + e);
		}
	}

	public static ParallelTopicModel read (File f) throws Exception {

		ParallelTopicModel topicModel = null;

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
		topicModel = (ParallelTopicModel) ois.readObject();
		ois.close();

		topicModel.initializeHistograms();

		return topicModel;
	}
	
	public static void main (String[] args) {
		
		try {
			
			InstanceList training = InstanceList.load (new File(args[0]));
			
			int numTopics = args.length > 1 ? Integer.parseInt(args[1]) : 200;
			
			ParallelTopicModel lda = new ParallelTopicModel (numTopics, 50.0, 0.01);
			lda.printLogLikelihood = true;
			lda.setTopicDisplay(50, 7);
			lda.addInstances(training);
			
			lda.setNumThreads(Integer.parseInt(args[2]));
			lda.estimate();
			logger.info("printing state");
			lda.printState(new File("state.gz"));
			logger.info("finished printing");

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
}
