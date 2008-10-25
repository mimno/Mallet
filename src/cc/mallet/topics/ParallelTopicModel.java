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
import java.util.concurrent.*;

import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.topics.TopicAssignment;
import cc.mallet.util.Randoms;

/**
 * Simple parallel threaded implementation of LDA,
 *  following the UCI NIPS paper.
 * 
 * @author David Mimno, Andrew McCallum
 */

public class ParallelTopicModel {
	
	protected ArrayList<TopicAssignment> data;  // the training instances and their topic assignments
	protected Alphabet alphabet; // the alphabet for the input data
	protected LabelAlphabet topicAlphabet;  // the alphabet for the topics
	
	protected int numTopics; // Number of topics to be fit

	// These values are used to encode type/topic counts as
	//  count/topic pairs in a single int.
	protected int topicMask;
	protected int topicBits;

	protected int numTypes;

	protected double[] alpha;	 // Dirichlet(alpha,alpha,...) is the distribution over topics
	protected double alphaSum;
	protected double beta;   // Prior on per-topic multinomial distribution over words
	protected double betaSum;

	public static final double DEFAULT_BETA = 0.01;
	
	protected int[][] typeTopicCounts; // indexed by <feature index, topic index>
	protected int[] tokensPerTopic; // indexed by <topic index>

	// for dirichlet estimation
	protected int[] docLengthCounts; // histogram of document sizes
	protected int[][] topicDocCounts; // histogram of document/topic counts, indexed by <topic index, sequence position index>

	public int numIterations = 1000;
	public int burninPeriod = 200; 
	public int saveSampleInterval = 10; 
	public int optimizeInterval = 50; 
	public int showTopicsInterval = 50;
	public int wordsPerTopic = 7;

	protected int saveStateInterval = 0;
	protected String stateFilename = null;
	
	protected Randoms random;
	protected NumberFormat formatter;
	protected boolean printLogLikelihood = false;
	
	int numThreads = 1;
	
	public ParallelTopicModel (int numberOfTopics) {
		this (numberOfTopics, numberOfTopics, DEFAULT_BETA);
	}
	
	public ParallelTopicModel (int numberOfTopics, double alphaSum, double beta) {
		this (numberOfTopics, alphaSum, beta, new Randoms());
	}
	
	private static LabelAlphabet newLabelAlphabet (int numTopics) {
		LabelAlphabet ret = new LabelAlphabet();
		for (int i = 0; i < numTopics; i++)
			ret.lookupIndex("topic"+i);
		return ret;
	}
	
	public ParallelTopicModel (int numberOfTopics, double alphaSum, double beta, Randoms random) {
		this (newLabelAlphabet (numberOfTopics), alphaSum, beta, random);
	}
	
	public ParallelTopicModel (LabelAlphabet topicAlphabet, double alphaSum, double beta, Randoms random)
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
		this.random = random;
		
		tokensPerTopic = new int[numTopics];
		
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		System.err.println("Coded LDA: " + numTopics + " topics, " + topicBits + " topic bits, " + 
						   Integer.toBinaryString(topicMask) + " topic mask");
	}
	
	public Alphabet getAlphabet() { return alphabet; }
	public LabelAlphabet getTopicAlphabet() { return topicAlphabet; }
	public int getNumTopics() { return numTopics; }
	public ArrayList<TopicAssignment> getData() { return data; }
	
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
		random = new Randoms(seed);
	}

	public void setOptimizeInterval(int interval) {
		this.optimizeInterval = interval;
	}

	public void setNumThreads(int threads) {
		this.numThreads = threads;
	}

	/** Define how often and where to save the state 
	 *
	 * @param interval Save a copy of the state every <code>interval</code> iterations.
	 * @param filename Save the state to this file, with the iteration number as a suffix
	 */
	public void setSaveState(int interval, String filename) {
		this.saveStateInterval = interval;
		this.stateFilename = filename;
	}

	public void addInstances (InstanceList training) {

		alphabet = training.getDataAlphabet();
		numTypes = alphabet.size();
		
		betaSum = beta * numTypes;
		
		typeTopicCounts = new int[numTypes][];

		// Get the total number of occurrences of each word type
		int[] typeTotals = new int[numTypes];

		int doc = 0;
		for (Instance instance : training) {
			doc++;
			FeatureSequence tokens = (FeatureSequence) instance.getData();
			for (int position = 0; position < tokens.getLength(); position++) {
				int type = tokens.getIndexAtPosition(position);
				typeTotals[ type ]++;
			}
		}

		// Allocate enough space so that we never have to worry about
		//  overflows: either the number of topics or the number of times
		//  the type occurs.
		for (int type = 0; type < numTypes; type++) {
			typeTopicCounts[type] = new int[ Math.min(numTopics, typeTotals[type]) ];
		}
		
		doc = 0;

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
						System.out.println("overflow on type " + type);
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
							System.out.println("overflow in merging on type " + type);
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
	}
	

	/** 
	 *  Gather statistics on the size of documents 
	 *  and create histograms for use in Dirichlet hyperparameter
	 *  optimization.
	 */
	private void initializeHistograms() {

		int maxTokens = 0;
		int totalTokens = 0;
		int seqLen;

		for (int doc = 0; doc < data.size(); doc++) {
			FeatureSequence fs = (FeatureSequence) data.get(doc).instance.getData();
			seqLen = fs.getLength();
			if (seqLen > maxTokens)
				maxTokens = seqLen;
			totalTokens += seqLen;
		}

		System.err.println("max tokens: " + maxTokens);
		System.err.println("total tokens: " + totalTokens);

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

				for (int count=0; count < sourceTopicCounts[topic].length; count++) {
					if (sourceTopicCounts[topic][count] > 0) {
						topicDocCounts[topic][count] += sourceTopicCounts[topic][count];
						sourceTopicCounts[topic][count] = 0;
					}
				}
			}
		}

		alphaSum = Dirichlet.learnParameters(alpha, topicDocCounts, docLengthCounts);
	}

	public void estimate () throws IOException {

		long startTime = System.currentTimeMillis();

		WorkerRunnable[] runnables = new WorkerRunnable[numThreads];

		int docsPerThread = data.size() / numThreads;
		int offset = 0;

		for (int thread = 0; thread < numThreads; thread++) {
			int[] runnableTotals = new int[numTopics];
			System.arraycopy(tokensPerTopic, 0, runnableTotals, 0, numTopics);

			int[][] runnableCounts = new int[numTypes][];
			for (int type = 0; type < numTypes; type++) {
				int[] counts = new int[typeTopicCounts[type].length];
				System.arraycopy(typeTopicCounts[type], 0, counts, 0, counts.length);
				runnableCounts[type] = counts;
			}
			
			runnables[thread] = new WorkerRunnable(numTopics,
												   alpha, alphaSum, beta,
												   new Randoms(), data,
												   runnableCounts, runnableTotals,
												   offset, docsPerThread);

			runnables[thread].initializeAlphaStatistics(docLengthCounts.length);

			offset += docsPerThread;
		}

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
	
		for (int iteration = 1; iteration <= numIterations; iteration++) {

			long iterationStart = System.currentTimeMillis();

			if (showTopicsInterval != 0 && iteration != 0 && iteration % showTopicsInterval == 0) {
				System.out.println();
				printTopWords (System.out, wordsPerTopic, false);
			}

			if (saveStateInterval != 0 && iteration % saveStateInterval == 0) {
				this.printState(new File(stateFilename + '.' + iteration));
			}

			// Submit runnables to thread pool

			for (int thread = 0; thread < numThreads; thread++) {
				if (iteration > burninPeriod && optimizeInterval != 0 &&
					iteration % saveSampleInterval == 0) {
					runnables[thread].collectAlphaStatistics();
				}
				
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
					//System.out.println("thread " + thread + " done? " + runnables[thread].isFinished);
					finished = finished && runnables[thread].isFinished;
				}

			}

			if (iteration > burninPeriod && optimizeInterval != 0 &&
				iteration % optimizeInterval == 0) {

				optimizeAlpha(runnables);
				
				System.out.print("[O " + (System.currentTimeMillis() - iterationStart) + "] ");
			}
			
			System.out.print("[" + (System.currentTimeMillis() - iterationStart) + "] ");
			
			sumTypeTopicCounts(runnables);

			System.out.print("[" + (System.currentTimeMillis() - iterationStart) + "] ");

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

            long elapsedMillis = System.currentTimeMillis() - iterationStart;
            if (elapsedMillis < 1000) {
				System.out.print(elapsedMillis + "ms ");
			}
            else {
                System.out.print((elapsedMillis/1000) + "s ");
			}   

			if (iteration % 10 == 0) {
				System.out.println ("<" + iteration + "> ");
				if (printLogLikelihood) System.out.println (modelLogLikelihood());
			}
			System.out.flush();
		}

		executor.shutdownNow();
	
		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}
	
	public void printTopWords (File file, int numWords, boolean useNewLines) throws IOException {
		PrintStream out = new PrintStream (file);
		printTopWords(out, numWords, useNewLines);
		out.close();
	}
	
	public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {

		FeatureCounter[] wordCountsPerTopic = new FeatureCounter[numTopics];

		for (int topic = 0; topic < numTopics; topic++) {
			wordCountsPerTopic[topic] = new FeatureCounter(alphabet);
		}

		for (int type = 0; type < numTypes; type++) {

			int[] topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {
				   
				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;

				wordCountsPerTopic[topic].increment(type, count);

				index++;
			}
		}

		for (int topic = 0; topic < numTopics; topic++) {
			RankedFeatureVector rfv = wordCountsPerTopic[topic].toRankedFeatureVector();
			if (usingNewLines) {
				out.println ("Topic " + topic);
				int max = rfv.numLocations(); if (max > numWords) max = numWords;
				for (int ri = 0; ri < max; ri++) {
					int type = rfv.getIndexAtRank(ri);
					out.println (alphabet.lookupObject(type).toString()+"\t"+(int)rfv.getValueAtRank(ri));
				}
			} else {
				out.print (topic + "\t" + formatter.format(alpha[topic]) + "\t");
				for (int ri = 0; ri < numWords; ri++) 
					out.print (alphabet.lookupObject(rfv.getIndexAtRank(ri)).toString()+" ");
				out.print ("\n");
			}
		}
	}

	public void printDocumentTopics (File f) throws IOException {
		printDocumentTopics (new PrintWriter (new FileWriter (f) ) );
	}

	public void printDocumentTopics (PrintWriter pw) {
		printDocumentTopics (pw, 0.0, -1);
	}

	/**
	 *  @param pw          A print writer
	 *  @param threshold   Only print topics with proportion greater than this number
	 *  @param max         Print no more than this many topics
	 */
	public void printDocumentTopics (PrintWriter pw, double threshold, int max)	{
		pw.print ("#doc source topic proportion ...\n");
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

		for (int di = 0; di < data.size(); di++) {
			LabelSequence topicSequence = (LabelSequence) data.get(di).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();

			pw.print (di); pw.print (' ');

			if (data.get(di).instance.getSource() != null) {
				pw.print (data.get(di).instance.getSource()); 
			}
			else {
				pw.print ("null-source");
			}

			pw.print (' ');
			docLen = currentDocTopics.length;

			// Count up the tokens
			for (int token=0; token < docLen; token++) {
				topicCounts[ currentDocTopics[token] ]++;
			}

			// And normalize
			for (int topic = 0; topic < numTopics; topic++) {
				sortedTopics[topic].set(topic, (float) topicCounts[topic] / docLen);
			}
			
			Arrays.sort(sortedTopics);

			for (int i = 0; i < max; i++) {
				if (sortedTopics[i].getWeight() < threshold) { break; }
				
				pw.print (sortedTopics[i].getID() + " " + 
						  sortedTopics[i].getWeight() + " ");
			}
			pw.print (" \n");

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

		for (int di = 0; di < data.size(); di++) {
			FeatureSequence tokenSequence =	(FeatureSequence) data.get(di).instance.getData();
			LabelSequence topicSequence =	(LabelSequence) data.get(di).topicSequence;

			String source = "NA";
			if (data.get(di).instance.getSource() != null) {
				source = data.get(di).instance.getSource().toString();
			}

			for (int pi = 0; pi < topicSequence.getLength(); pi++) {
				int type = tokenSequence.getIndexAtPosition(pi);
				int topic = topicSequence.getIndexAtPosition(pi);
				out.print(di); out.print(' ');
				out.print(source); out.print(' '); 
				out.print(pi); out.print(' ');
				out.print(type); out.print(' ');
				out.print(alphabet.lookupObject(type)); out.print(' ');
				out.print(topic); out.println();
			}
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

		// Count the number of type-topic pairs
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
					System.out.println(count);
					System.exit(1);
				}

				index++;
			}
		}
	
		for (int topic=0; topic < numTopics; topic++) {
			logLikelihood -= 
				Dirichlet.logGammaStirling( (beta * numTopics) +
											tokensPerTopic[ topic ] );
			if (Double.isNaN(logLikelihood)) {
				System.out.println("after topic " + topic + " " + tokensPerTopic[ topic ]);
				System.exit(1);
			}

		}
	
		logLikelihood += 
			(Dirichlet.logGammaStirling(beta * numTopics)) -
			(Dirichlet.logGammaStirling(beta) * nonZeroTypeTopics);

		if (Double.isNaN(logLikelihood)) {
			System.out.println("at the end");
			System.exit(1);
		}


		return logLikelihood;
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
			System.out.println("printing state");
			lda.printState(new File("state.gz"));
			System.out.println("finished printing");

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
}
