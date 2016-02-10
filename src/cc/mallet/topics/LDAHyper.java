/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.	For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import gnu.trove.TIntIntHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Iterator;

import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;

/**
 * Latent Dirichlet Allocation with optimized hyperparameters
 * 
 * @author David Mimno, Andrew McCallum
 * @deprecated Use ParallelTopicModel instead, which uses substantially faster data structures even for non-parallel operation.
 */

public class LDAHyper implements Serializable {
	
	// Analogous to a cc.mallet.classify.Classification
	public class Topication implements Serializable {
		public Instance instance;
		public LDAHyper model;
		public LabelSequence topicSequence;
		public Labeling topicDistribution; // not actually constructed by model fitting, but could be added for "test" documents.
		
		public Topication (Instance instance, LDAHyper model, LabelSequence topicSequence) {
			this.instance = instance;
			this.model = model;
			this.topicSequence = topicSequence;
		}

		// Maintainable serialization
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject (instance);
			out.writeObject (model);
			out.writeObject (topicSequence);
			out.writeObject (topicDistribution);
		}
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			instance = (Instance) in.readObject();
			model = (LDAHyper) in.readObject();
			topicSequence = (LabelSequence) in.readObject();
			topicDistribution = (Labeling) in.readObject();
		}
	}

	protected ArrayList<Topication> data;  // the training instances and their topic assignments
	protected Alphabet alphabet; // the alphabet for the input data
	protected LabelAlphabet topicAlphabet;  // the alphabet for the topics
	
	protected int numTopics; // Number of topics to be fit
	protected int numTypes;

	protected double[] alpha;	 // Dirichlet(alpha,alpha,...) is the distribution over topics
	protected double alphaSum;
	protected double beta;   // Prior on per-topic multinomial distribution over words
	protected double betaSum;
	public static final double DEFAULT_BETA = 0.01;
	
	protected double smoothingOnlyMass = 0.0;
	protected double[] cachedCoefficients;
	int topicTermCount = 0;
	int betaTopicCount = 0;
	int smoothingOnlyCount = 0;

	// Instance list for empirical likelihood calculation
	protected InstanceList testing = null;
	
	// An array to put the topic counts for the current document. 
	// Initialized locally below.  Defined here to avoid
	// garbage collection overhead.
	protected int[] oneDocTopicCounts; // indexed by <document index, topic index>

	protected gnu.trove.TIntIntHashMap[] typeTopicCounts; // indexed by <feature index, topic index>
	protected int[] tokensPerTopic; // indexed by <topic index>

	// for dirichlet estimation
	protected int[] docLengthCounts; // histogram of document sizes
	protected int[][] topicDocCounts; // histogram of document/topic counts, indexed by <topic index, sequence position index>

	public int iterationsSoFar = 0;
	public int numIterations = 1000;
	public int burninPeriod = 20; // was 50; //was 200;
	public int saveSampleInterval = 5; // was 10;	
	public int optimizeInterval = 20; // was 50;
	public int showTopicsInterval = 10; // was 50;
	public int wordsPerTopic = 7;

	protected int outputModelInterval = 0;
	protected String outputModelFilename;

	protected int saveStateInterval = 0;
	protected String stateFilename = null;
	
	protected Randoms random;
	protected NumberFormat formatter;
	protected boolean printLogLikelihood = false;
	
	public LDAHyper (int numberOfTopics) {
		this (numberOfTopics, numberOfTopics, DEFAULT_BETA);
	}
	
	public LDAHyper (int numberOfTopics, double alphaSum, double beta) {
		this (numberOfTopics, alphaSum, beta, new Randoms());
	}
	
	private static LabelAlphabet newLabelAlphabet (int numTopics) {
		LabelAlphabet ret = new LabelAlphabet();
		for (int i = 0; i < numTopics; i++)
			ret.lookupIndex("topic"+i);
		return ret;
	}
	
	public LDAHyper (int numberOfTopics, double alphaSum, double beta, Randoms random) {
		this (newLabelAlphabet (numberOfTopics), alphaSum, beta, random);
	}
	
	public LDAHyper (LabelAlphabet topicAlphabet, double alphaSum, double beta, Randoms random)
	{
		this.data = new ArrayList<Topication>();
		this.topicAlphabet = topicAlphabet;
		this.numTopics = topicAlphabet.size();
		this.alphaSum = alphaSum;
		this.alpha = new double[numTopics];
		Arrays.fill(alpha, alphaSum / numTopics);
		this.beta = beta;
		this.random = random;
		
		oneDocTopicCounts = new int[numTopics];
		tokensPerTopic = new int[numTopics];
		
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		System.err.println("LDA: " + numTopics + " topics");
	}
	
	public Alphabet getAlphabet() { return alphabet; }
	public LabelAlphabet getTopicAlphabet() { return topicAlphabet; }
	public int getNumTopics() { return numTopics; }
	public ArrayList<Topication> getData() { return data; }
	public int getCountFeatureTopic (int featureIndex, int topicIndex) { return typeTopicCounts[featureIndex].get(topicIndex); }
	public int getCountTokensPerTopic (int topicIndex) { return tokensPerTopic[topicIndex]; }
	
	/** Held-out instances for empirical likelihood calculation */
	public void setTestingInstances(InstanceList testing) {
		this.testing = testing;
	}

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

	public void setModelOutput(int interval, String filename) {
		this.outputModelInterval = interval;
		this.outputModelFilename = filename;
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
	
	protected int instanceLength (Instance instance) {
		return ((FeatureSequence)instance.getData()).size();
	}
	
	// Can be safely called multiple times.  This method will complain if it can't handle the situation
	private void initializeForTypes (Alphabet alphabet) {
		if (this.alphabet == null) {
			this.alphabet = alphabet;
			this.numTypes = alphabet.size();
			this.typeTopicCounts = new TIntIntHashMap[numTypes];
			for (int fi = 0; fi < numTypes; fi++) 
				typeTopicCounts[fi] = new TIntIntHashMap();
			this.betaSum = beta * numTypes;
		} else if (alphabet != this.alphabet) {
			throw new IllegalArgumentException ("Cannot change Alphabet.");
		} else if (alphabet.size() != this.numTypes) {
			this.numTypes = alphabet.size();
			TIntIntHashMap[] newTypeTopicCounts = new TIntIntHashMap[numTypes];
			for (int i = 0; i < typeTopicCounts.length; i++)
				newTypeTopicCounts[i] = typeTopicCounts[i];
			for (int i = typeTopicCounts.length; i < numTypes; i++)
				newTypeTopicCounts[i] = new TIntIntHashMap();
			// TODO AKM July 18:  Why wasn't the next line there previously?
			// this.typeTopicCounts = newTypeTopicCounts;
			this.betaSum = beta * numTypes;
		}	// else, nothing changed, nothing to be done
	}
	
	private void initializeTypeTopicCounts () {
		TIntIntHashMap[] newTypeTopicCounts = new TIntIntHashMap[numTypes];
		for (int i = 0; i < typeTopicCounts.length; i++)
			newTypeTopicCounts[i] = typeTopicCounts[i];
		for (int i = typeTopicCounts.length; i < numTypes; i++)
			newTypeTopicCounts[i] = new TIntIntHashMap();
		this.typeTopicCounts = newTypeTopicCounts;
	}
	
	public void addInstances (InstanceList training) {
		initializeForTypes (training.getDataAlphabet());
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : training) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false)
				// This method not yet obeying its last "false" argument, and must be for this to work
				sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			else {
				Randoms r = new Randoms();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++)
					topics[i] = r.nextInt(numTopics);
			}
			topicSequences.add (topicSequence);
		}
		addInstances (training, topicSequences);
	}

	public void addInstances (InstanceList training, List<LabelSequence> topics) {
		initializeForTypes (training.getDataAlphabet());
		assert (training.size() == topics.size());
		for (int i = 0; i < training.size(); i++) {
			Topication t = new Topication (training.get(i), this, topics.get(i));
			data.add (t);
			// Include sufficient statistics for this one doc
			FeatureSequence tokenSequence = (FeatureSequence) t.instance.getData();
			LabelSequence topicSequence = t.topicSequence;
			for (int pi = 0; pi < topicSequence.getLength(); pi++) {
				int topic = topicSequence.getIndexAtPosition(pi);
				typeTopicCounts[tokenSequence.getIndexAtPosition(pi)].adjustOrPutValue(topic, 1, 1);
				tokensPerTopic[topic]++;
			}
		}
		initializeHistogramsAndCachedValues();
	}

	/** 
	 *  Gather statistics on the size of documents 
	 *  and create histograms for use in Dirichlet hyperparameter
	 *  optimization.
	 */
	protected void initializeHistogramsAndCachedValues() {

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
		// Initialize the smoothing-only sampling bucket
		smoothingOnlyMass = 0;
		for (int topic = 0; topic < numTopics; topic++) 
			smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);

		// Initialize the cached coefficients, using only smoothing.
		cachedCoefficients = new double[ numTopics ];
		for (int topic=0; topic < numTopics; topic++)
			cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);

		System.err.println("max tokens: " + maxTokens);
		System.err.println("total tokens: " + totalTokens);

		docLengthCounts = new int[maxTokens + 1];
		topicDocCounts = new int[numTopics][maxTokens + 1];
	}
	
	public void estimate () throws IOException {
		estimate (numIterations);
	}
	
	public void estimate (int iterationsThisRound) throws IOException {

		long startTime = System.currentTimeMillis();
		int maxIteration = iterationsSoFar + iterationsThisRound;
	
		for ( ; iterationsSoFar <= maxIteration; iterationsSoFar++) {
			long iterationStart = System.currentTimeMillis();

			if (showTopicsInterval != 0 && iterationsSoFar != 0 && iterationsSoFar % showTopicsInterval == 0) {
				System.out.println();
				printTopWords (System.out, wordsPerTopic, false);

				if (testing != null) {
					  double el = empiricalLikelihood(1000, testing);
					  double ll = modelLogLikelihood();
					  double mi = topicLabelMutualInformation();
					  System.out.println(ll + "\t" + el + "\t" + mi);
				}
			}

			if (saveStateInterval != 0 && iterationsSoFar % saveStateInterval == 0) {
				this.printState(new File(stateFilename + '.' + iterationsSoFar));
			}

			/*
			  if (outputModelInterval != 0 && iterations % outputModelInterval == 0) {
			  this.write (new File(outputModelFilename+'.'+iterations));
			  }
			*/

			// TODO this condition should also check that we have more than one sample to work with here
			// (The number of samples actually obtained is not yet tracked.)
			if (iterationsSoFar > burninPeriod && optimizeInterval != 0 &&
				iterationsSoFar % optimizeInterval == 0) {

				alphaSum = Dirichlet.learnParameters(alpha, topicDocCounts, docLengthCounts);

				smoothingOnlyMass = 0.0;
				for (int topic = 0; topic < numTopics; topic++) {
					smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
					cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);
				}
				clearHistograms();
			}

			// Loop over every document in the corpus
			topicTermCount = betaTopicCount = smoothingOnlyCount = 0;
			int numDocs = data.size(); // TODO consider beginning by sub-sampling?
			for (int di = 0; di < numDocs; di++) {
				FeatureSequence tokenSequence = (FeatureSequence) data.get(di).instance.getData();
				LabelSequence topicSequence = (LabelSequence) data.get(di).topicSequence;
				sampleTopicsForOneDoc (tokenSequence, topicSequence,
									   iterationsSoFar >= burninPeriod && iterationsSoFar % saveSampleInterval == 0,
									   true);
			}

			long elapsedMillis = System.currentTimeMillis() - iterationStart;
			if (elapsedMillis < 1000) {
				System.out.print(elapsedMillis + "ms ");
			}
			else {
				System.out.print((elapsedMillis/1000) + "s ");
			}

			//System.out.println(topicTermCount + "\t" + betaTopicCount + "\t" + smoothingOnlyCount);
			if (iterationsSoFar % 10 == 0) {
				System.out.println ("<" + iterationsSoFar + "> ");
				if (printLogLikelihood) System.out.println (modelLogLikelihood());
			}
			System.out.flush();
		}
	
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
	
	private void clearHistograms() {
		Arrays.fill(docLengthCounts, 0);
		for (int topic = 0; topic < topicDocCounts.length; topic++)
			Arrays.fill(topicDocCounts[topic], 0);
	}

	/** If topicSequence assignments are already set and accounted for in sufficient statistics, 
	 *   then readjustTopicsAndStats should be true.  The topics will be re-sampled and sufficient statistics changes.
	 *  If operating on a new or a test document, and featureSequence & topicSequence are not already accounted for in the sufficient statistics, 
	 *   then readjustTopicsAndStats should be false.  The current topic assignments will be ignored, and the sufficient statistics
	 *   will not be changed.
	 *  If you want to estimate the Dirichlet alpha based on the per-document topic multinomials sampled this round, 
	 *   then saveStateForAlphaEstimation should be true. */
	private void oldSampleTopicsForOneDoc (FeatureSequence featureSequence, 
			FeatureSequence topicSequence,
			boolean saveStateForAlphaEstimation, boolean readjustTopicsAndStats) 
	{
		long startTime = System.currentTimeMillis();
	
		int[] oneDocTopics = topicSequence.getFeatures();

		TIntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double[] topicDistribution;
		double topicDistributionSum;
		int docLen = featureSequence.getLength();
		int adjustedValue;
		int[] topicIndices, topicCounts;

		double weight;
	
		// populate topic counts
		Arrays.fill(oneDocTopicCounts, 0);

		if (readjustTopicsAndStats) {
			for (int token = 0; token < docLen; token++) {
				oneDocTopicCounts[ oneDocTopics[token] ]++;
			}
		}

		// Iterate over the tokens (words) in the document
		for (int token = 0; token < docLen; token++) {
			type = featureSequence.getIndexAtPosition(token);
			oldTopic = oneDocTopics[token];
			currentTypeTopicCounts = typeTopicCounts[type];
			assert (currentTypeTopicCounts.size() != 0);

			if (readjustTopicsAndStats) {
				// Remove this token from all counts
				oneDocTopicCounts[oldTopic]--;
				adjustedValue = currentTypeTopicCounts.adjustOrPutValue(oldTopic, -1, -1);
				if (adjustedValue == 0) currentTypeTopicCounts.remove(oldTopic);
				else if (adjustedValue == -1) throw new IllegalStateException ("Token count in topic went negative.");
				tokensPerTopic[oldTopic]--;
			}

			// Build a distribution over topics for this token
			topicIndices = currentTypeTopicCounts.keys();
			topicCounts = currentTypeTopicCounts.getValues();
			topicDistribution = new double[topicIndices.length]; 
			// TODO Yipes, memory allocation in the inner loop!  But note that .keys and .getValues is doing this too.
			topicDistributionSum = 0;
			for (int i = 0; i < topicCounts.length; i++) {
				int topic = topicIndices[i];
				weight = ((topicCounts[i] + beta) /	(tokensPerTopic[topic] + betaSum))	* ((oneDocTopicCounts[topic] + alpha[topic]));
				topicDistributionSum += weight;
				topicDistribution[topic] = weight;
			}

			// Sample a topic assignment from this distribution
			newTopic = topicIndices[random.nextDiscrete (topicDistribution, topicDistributionSum)];
		
			if (readjustTopicsAndStats) {
				// Put that new topic into the counts
				oneDocTopics[token] = newTopic;
				oneDocTopicCounts[newTopic]++;
				typeTopicCounts[type].adjustOrPutValue(newTopic, 1, 1);
				tokensPerTopic[newTopic]++;
			}
		}

		if (saveStateForAlphaEstimation) {
			// Update the document-topic count histogram,	for dirichlet estimation
			docLengthCounts[ docLen ]++;
			for (int topic=0; topic < numTopics; topic++) {
				topicDocCounts[topic][ oneDocTopicCounts[topic] ]++;
			}
		}
	}
	
	protected void sampleTopicsForOneDoc (FeatureSequence tokenSequence,
										  FeatureSequence topicSequence,
										  boolean shouldSaveState,
										  boolean readjustTopicsAndStats /* currently ignored */) {

		int[] oneDocTopics = topicSequence.getFeatures();

		TIntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		//		populate topic counts
		TIntIntHashMap localTopicCounts = new TIntIntHashMap();
		for (int position = 0; position < docLength; position++) {
			localTopicCounts.adjustOrPutValue(oneDocTopics[position], 1, 1);
		}

		//		Initialize the topic count/beta sampling bucket
		double topicBetaMass = 0.0;
		for (int topic: localTopicCounts.keys()) {
			int n = localTopicCounts.get(topic);

			//			initialize the normalization constant for the (B * n_{t|d}) term
			topicBetaMass += beta * n /	(tokensPerTopic[topic] + betaSum);	

			//			update the coefficients for the non-zero topics
			cachedCoefficients[topic] =	(alpha[topic] + n) / (tokensPerTopic[topic] + betaSum);
		}

		double topicTermMass = 0.0;

		double[] topicTermScores = new double[numTopics];
		int[] topicTermIndices;
		int[] topicTermValues;
		int i;
		double score;

		//	Iterate over the positions (words) in the document 
		for (int position = 0; position < docLength; position++) {
			type = tokenSequence.getIndexAtPosition(position);
			oldTopic = oneDocTopics[position];

			currentTypeTopicCounts = typeTopicCounts[type];
			assert(currentTypeTopicCounts.get(oldTopic) >= 0);

			//	Remove this token from all counts. 
			//   Note that we actually want to remove the key if it goes
			//    to zero, not set it to 0.
			if (currentTypeTopicCounts.get(oldTopic) == 1) {
				currentTypeTopicCounts.remove(oldTopic);
			}
			else {
				currentTypeTopicCounts.adjustValue(oldTopic, -1);
			}

			smoothingOnlyMass -= alpha[oldTopic] * beta / 
				(tokensPerTopic[oldTopic] + betaSum);
			topicBetaMass -= beta * localTopicCounts.get(oldTopic) /
				(tokensPerTopic[oldTopic] + betaSum);
			
			if (localTopicCounts.get(oldTopic) == 1) {
				localTopicCounts.remove(oldTopic);
			}
			else {
				localTopicCounts.adjustValue(oldTopic, -1);
			}

			tokensPerTopic[oldTopic]--;
			
			smoothingOnlyMass += alpha[oldTopic] * beta / 
				(tokensPerTopic[oldTopic] + betaSum);
			topicBetaMass += beta * localTopicCounts.get(oldTopic) /
				(tokensPerTopic[oldTopic] + betaSum);
			
			cachedCoefficients[oldTopic] = 
				(alpha[oldTopic] + localTopicCounts.get(oldTopic)) /
				(tokensPerTopic[oldTopic] + betaSum);

			topicTermMass = 0.0;

			topicTermIndices = currentTypeTopicCounts.keys();
			topicTermValues = currentTypeTopicCounts.getValues();

			for (i=0; i < topicTermIndices.length; i++) {
				int topic = topicTermIndices[i];
				score =
					cachedCoefficients[topic] * topicTermValues[i];
				//				((alpha[topic] + localTopicCounts.get(topic)) * 
				//				topicTermValues[i]) /
				//				(tokensPerTopic[topic] + betaSum);
				
				//				Note: I tried only doing this next bit if 
				//				score > 0, but it didn't make any difference,
				//				at least in the first few iterations.
				
				topicTermMass += score;
				topicTermScores[i] = score;
				//				topicTermIndices[i] = topic;
			}
			//			indicate that this is the last topic
			//			topicTermIndices[i] = -1;
			
			double sample = random.nextUniform() * (smoothingOnlyMass + topicBetaMass + topicTermMass);
			double origSample = sample;

//			Make sure it actually gets set
			newTopic = -1;

			if (sample < topicTermMass) {
				//topicTermCount++;

				i = -1;
				while (sample > 0) {
					i++;
					sample -= topicTermScores[i];
				}
				newTopic = topicTermIndices[i];

			}
			else {
				sample -= topicTermMass;

				if (sample < topicBetaMass) {
					//betaTopicCount++;

					sample /= beta;

					topicTermIndices = localTopicCounts.keys();
					topicTermValues = localTopicCounts.getValues();

					for (i=0; i < topicTermIndices.length; i++) {
						newTopic = topicTermIndices[i];

						sample -= topicTermValues[i] /
							(tokensPerTopic[newTopic] + betaSum);

						if (sample <= 0.0) {
							break;
						}
					}

				}
				else {
					//smoothingOnlyCount++;

					sample -= topicBetaMass;

					sample /= beta;

					for (int topic = 0; topic < numTopics; topic++) {
						sample -= alpha[topic] / 
							(tokensPerTopic[topic] + betaSum);

						if (sample <= 0.0) {
							newTopic = topic;
							break;
						}
					}

				}

			}

			if (newTopic == -1) {
				System.err.println("LDAHyper sampling error: "+ origSample + " " + sample + " " + smoothingOnlyMass + " " + 
						topicBetaMass + " " + topicTermMass);
				newTopic = numTopics-1; // TODO is this appropriate
				//throw new IllegalStateException ("LDAHyper: New topic not sampled.");
			}
			//assert(newTopic != -1);

			//			Put that new topic into the counts
			oneDocTopics[position] = newTopic;
			currentTypeTopicCounts.adjustOrPutValue(newTopic, 1, 1);

			smoothingOnlyMass -= alpha[newTopic] * beta / 
				(tokensPerTopic[newTopic] + betaSum);
			topicBetaMass -= beta * localTopicCounts.get(newTopic) /
				(tokensPerTopic[newTopic] + betaSum);

			localTopicCounts.adjustOrPutValue(newTopic, 1, 1);
			tokensPerTopic[newTopic]++;

			//			update the coefficients for the non-zero topics
			cachedCoefficients[newTopic] =
				(alpha[newTopic] + localTopicCounts.get(newTopic)) /
				(tokensPerTopic[newTopic] + betaSum);

			smoothingOnlyMass += alpha[newTopic] * beta / 
				(tokensPerTopic[newTopic] + betaSum);
			topicBetaMass += beta * localTopicCounts.get(newTopic) /
				(tokensPerTopic[newTopic] + betaSum);

			assert(currentTypeTopicCounts.get(newTopic) >= 0);

		}

		//		Clean up our mess: reset the coefficients to values with only
		//		smoothing. The next doc will update its own non-zero topics...
		for (int topic: localTopicCounts.keys()) {
			cachedCoefficients[topic] =
				alpha[topic] / (tokensPerTopic[topic] + betaSum);
		}

		if (shouldSaveState) {
			//			Update the document-topic count histogram,
			//			for dirichlet estimation
			docLengthCounts[ docLength ]++;
			for (int topic: localTopicCounts.keys()) {
				topicDocCounts[topic][ localTopicCounts.get(topic) ]++;
			}
		}
	}


	public IDSorter[] getSortedTopicWords(int topic) {
		IDSorter[] sortedTypes = new IDSorter[ numTypes ];
		for (int type = 0; type < numTypes; type++)
			sortedTypes[type] = new IDSorter(type, typeTopicCounts[type].get(topic));
		Arrays.sort(sortedTypes);
		return sortedTypes;
	}

	public void printTopWords (File file, int numWords, boolean useNewLines) throws IOException {
		PrintStream out = new PrintStream (file);
		printTopWords(out, numWords, useNewLines);
		out.close();
	}

	// TreeSet implementation is ~70x faster than RankedFeatureVector -DM
	
	public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {

		for (int topic = 0; topic < numTopics; topic++) {
                        
			TreeSet<IDSorter> sortedWords = new TreeSet<IDSorter>();
			for (int type = 0; type < numTypes; type++) {
				if (typeTopicCounts[type].containsKey(topic)) {
					sortedWords.add(new IDSorter(type, typeTopicCounts[type].get(topic)));
				}
			}

			if (usingNewLines) {
				out.println ("Topic " + topic);
                                
				int word = 1;
				Iterator<IDSorter> iterator = sortedWords.iterator();
				while (iterator.hasNext() && word < numWords) {
					IDSorter info = iterator.next();
                                        
					out.println(alphabet.lookupObject(info.getID()) + "\t" +
								(int) info.getWeight());
					word++;
				}
			}
			else {
				out.print (topic + "\t" + formatter.format(alpha[topic]) + "\t" + tokensPerTopic[topic] + "\t");

				int word = 1;
				Iterator<IDSorter> iterator = sortedWords.iterator();
				while (iterator.hasNext() && word < numWords) {
                    IDSorter info = iterator.next();

                    out.print(alphabet.lookupObject(info.getID()) + " ");
                    word++;
                }

				out.println();
			}
		}
	}

	public void topicXMLReport (PrintWriter out, int numWords) {

		out.println("<?xml version='1.0' ?>");
		out.println("<topicModel>");

		for (int topic = 0; topic < numTopics; topic++) {
                        
			out.println("  <topic id='" + topic + "' alpha='" + alpha[topic] +
						"' totalTokens='" + tokensPerTopic[topic] + "'>");

			TreeSet<IDSorter> sortedWords = new TreeSet<IDSorter>();
			for (int type = 0; type < numTypes; type++) {
				if (typeTopicCounts[type].containsKey(topic)) {
					sortedWords.add(new IDSorter(type, typeTopicCounts[type].get(topic)));
				}
			}

			
			int word = 1;
			Iterator<IDSorter> iterator = sortedWords.iterator();
			while (iterator.hasNext() && word < numWords) {
				IDSorter info = iterator.next();
				
				out.println("    <word rank='" + word + "'>" +
						  alphabet.lookupObject(info.getID()) +
						  "</word>");
				word++;
			}

			out.println("  </topic>");
		}

		out.println("</topicModel>");
	}
	
	public void topicXMLReportPhrases (PrintStream out, int numWords) {
		int numTopics = this.getNumTopics();
		gnu.trove.TObjectIntHashMap<String>[] phrases = new gnu.trove.TObjectIntHashMap[numTopics];
		Alphabet alphabet = this.getAlphabet();
		
		// Get counts of phrases
		for (int ti = 0; ti < numTopics; ti++)
			phrases[ti] = new gnu.trove.TObjectIntHashMap<String>();
		for (int di = 0; di < this.getData().size(); di++) {
			LDAHyper.Topication t = this.getData().get(di);
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
					//System.out.println ("phrase:"+sbs);
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
			for (int type = 0; type < alphabet.size(); type++)
				probs[type] = this.getCountFeatureTopic(type, ti) / (double)this.getCountTokensPerTopic(ti);
			RankedFeatureVector rfv = new RankedFeatureVector (alphabet, probs);
			for (int ri = 0; ri < numWords; ri++) {
				int fi = rfv.getIndexAtRank(ri);
				pout.println ("      <term weight=\""+probs[fi]+"\" count=\""+this.getCountFeatureTopic(fi,ti)+"\">"+alphabet.lookupObject(fi)+	"</term>");
				if (ri < 20) // consider top 20 individual words as candidate titles
					titles.add(alphabet.lookupObject(fi), this.getCountFeatureTopic(fi,ti));
			}

			// Print phrases
			Object[] keys = phrases[ti].keys();
			int[] values = phrases[ti].getValues();
			double counts[] = new double[keys.length];
			for (int i = 0; i < counts.length; i++)	counts[i] = values[i];
			double countssum = MatrixOps.sum (counts);	
			Alphabet alph = new Alphabet(keys);
			rfv = new RankedFeatureVector (alph, counts);
			//out.println ("topic "+ti);
			int max = rfv.numLocations() < numWords ? rfv.numLocations() : numWords;
			//System.out.println ("topic "+ti+" numPhrases="+rfv.numLocations());
			for (int ri = 0; ri < max; ri++) {
				int fi = rfv.getIndexAtRank(ri);
				pout.println ("      <phrase weight=\""+counts[fi]/countssum+"\" count=\""+values[fi]+"\">"+alph.lookupObject(fi)+	"</phrase>");
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
			out.print(pout.toString());
			out.println("  </topic>");
		}
		out.println("</topics>");
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
	
	// Turbo topics
	/*
	private class CorpusWordCounts {
		Alphabet unigramAlphabet;
		FeatureCounter unigramCounts = new FeatureCounter(unigramAlphabet);
		public CorpusWordCounts(Alphabet alphabet) {
			unigramAlphabet = alphabet;
		}
		private double mylog(double x) { return (x == 0) ? -1000000.0 : Math.log(x); }
		// The likelihood ratio significance test
		private double significanceTest(int thisUnigramCount, int nextUnigramCount, int nextBigramCount, int nextTotalCount, int minCount) {
      if (nextBigramCount < minCount) return -1.0;
      assert(nextUnigramCount >= nextBigramCount);
      double log_pi_vu = mylog(nextBigramCount) - mylog(thisUnigramCount);
      double log_pi_vnu = mylog(nextUnigramCount - nextBigramCount) - mylog(nextTotalCount - nextBigramCount);
      double log_pi_v_old = mylog(nextUnigramCount) - mylog(nextTotalCount);
      double log_1mp_v = mylog(1 - Math.exp(log_pi_vnu));
      double log_1mp_vu = mylog(1 - Math.exp(log_pi_vu));
      return 2 * (nextBigramCount * log_pi_vu + 
      		(nextUnigramCount - nextBigramCount) * log_pi_vnu - 
      		nextUnigramCount * log_pi_v_old + 
      		(thisUnigramCount- nextBigramCount) * (log_1mp_vu - log_1mp_v));
		}
		public int[] significantBigrams(int word) {
		}
	}
	*/
	
	
	
	public void write (File f) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(f));
			oos.writeObject(this);
			oos.close();
		}
		catch (IOException e) {
			System.err.println("LDAHyper.write: Exception writing LDAHyper to file " + f + ": " + e);
		}
	}
	
	public static LDAHyper read (File f) {
		LDAHyper lda = null;
		try {
			ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
			lda = (LDAHyper) ois.readObject();
			lda.initializeTypeTopicCounts();  // To work around a bug in Trove?
			ois.close();
		}
		catch (IOException e) {
			System.err.println("Exception reading file " + f + ": " + e);
		}
		catch (ClassNotFoundException e) {
			System.err.println("Exception reading file " + f + ": " + e);
		}
		return lda;
	}
	
	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;

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

		out.writeDouble(smoothingOnlyMass);
		out.writeObject(cachedCoefficients);

		out.writeInt(iterationsSoFar);
		out.writeInt(numIterations);

		out.writeInt(burninPeriod);
		out.writeInt(saveSampleInterval);
		out.writeInt(optimizeInterval);
		out.writeInt(showTopicsInterval);
		out.writeInt(wordsPerTopic);
		out.writeInt(outputModelInterval);
		out.writeObject(outputModelFilename);
		out.writeInt(saveStateInterval);
		out.writeObject(stateFilename);

		out.writeObject(random);
		out.writeObject(formatter);
		out.writeBoolean(printLogLikelihood);

		out.writeObject(docLengthCounts);
		out.writeObject(topicDocCounts);

		for (int fi = 0; fi < numTypes; fi++)
			out.writeObject (typeTopicCounts[fi]);

		for (int ti = 0; ti < numTopics; ti++)
			out.writeInt (tokensPerTopic[ti]);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int featuresLength;
		int version = in.readInt ();

		data = (ArrayList<Topication>) in.readObject ();
		alphabet = (Alphabet) in.readObject();
		topicAlphabet = (LabelAlphabet) in.readObject();

		numTopics = in.readInt();
		alpha = (double[]) in.readObject();
		beta = in.readDouble();
		betaSum = in.readDouble();

		smoothingOnlyMass = in.readDouble();
		cachedCoefficients = (double[]) in.readObject();

		iterationsSoFar = in.readInt();
		numIterations = in.readInt();

		burninPeriod = in.readInt();
		saveSampleInterval = in.readInt();
		optimizeInterval = in.readInt();
		showTopicsInterval = in.readInt();
		wordsPerTopic = in.readInt();
		outputModelInterval = in.readInt();
		outputModelFilename = (String) in.readObject();
		saveStateInterval = in.readInt();
		stateFilename = (String) in.readObject();

		random = (Randoms) in.readObject();
		formatter = (NumberFormat) in.readObject();
		printLogLikelihood = in.readBoolean();

		docLengthCounts = (int[]) in.readObject();
		topicDocCounts = (int[][]) in.readObject();

		int numDocs = data.size();
		this.numTypes = alphabet.size();

		typeTopicCounts = new TIntIntHashMap[numTypes];
		for (int fi = 0; fi < numTypes; fi++)
			typeTopicCounts[fi] = (TIntIntHashMap) in.readObject();
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++)
			tokensPerTopic[ti] = in.readInt();
	}


	public double topicLabelMutualInformation() {
		int doc, level, label, topic, token, type;
		int[] docTopics;

		if (data.get(0).instance.getTargetAlphabet() == null) {
			return 0.0;
		}

		int targetAlphabetSize = data.get(0).instance.getTargetAlphabet().size();
		int[][] topicLabelCounts = new int[ numTopics ][ targetAlphabetSize ];
		int[] topicCounts = new int[ numTopics ];
		int[] labelCounts = new int[ targetAlphabetSize ];
		int total = 0;

		for (doc=0; doc < data.size(); doc++) {
			label = data.get(doc).instance.getLabeling().getBestIndex();

			LabelSequence topicSequence = (LabelSequence) data.get(doc).topicSequence;
			docTopics = topicSequence.getFeatures();

			for (token = 0; token < docTopics.length; token++) {
				topic = docTopics[token];
				topicLabelCounts[ topic ][ label ]++;
				topicCounts[topic]++;
				labelCounts[label]++;
				total++;
			}
		}

		/* // This block will print out the best topics for each label

		IDSorter[] wp = new IDSorter[numTypes];

		for (topic = 0; topic < numTopics; topic++) {

		for (type = 0; type < numTypes; type++) {
		wp[type] = new IDSorter (type, (((double) typeTopicCounts[type][topic]) /
		tokensPerTopic[topic]));
		}
		Arrays.sort (wp);

		StringBuffer terms = new StringBuffer();
		for (int i = 0; i < 8; i++) {
		terms.append(instances.getDataAlphabet().lookupObject(wp[i].id));
		terms.append(" ");
		}

		System.out.println(terms);
		for (label = 0; label < topicLabelCounts[topic].length; label++) {
		System.out.println(topicLabelCounts[ topic ][ label ] + "\t" +
		instances.getTargetAlphabet().lookupObject(label));
		}
		System.out.println();
		}

		*/

		double topicEntropy = 0.0;
		double labelEntropy = 0.0;
		double jointEntropy = 0.0;
		double p;
		double log2 = Math.log(2);

		for (topic = 0; topic < topicCounts.length; topic++) {
			if (topicCounts[topic] == 0) { continue; }
			p = (double) topicCounts[topic] / total;
			topicEntropy -= p * Math.log(p) / log2;
		}

		for (label = 0; label < labelCounts.length; label++) {
			if (labelCounts[label] == 0) { continue; }
			p = (double) labelCounts[label] / total;
			labelEntropy -= p * Math.log(p) / log2;
		}

		for (topic = 0; topic < topicCounts.length; topic++) {
			for (label = 0; label < labelCounts.length; label++) {
				if (topicLabelCounts[ topic ][ label ] == 0) { continue; }
				p = (double) topicLabelCounts[ topic ][ label ] / total;
				jointEntropy -= p * Math.log(p) / log2;
			}
		}

		return topicEntropy + labelEntropy - jointEntropy;


	}

	public double empiricalLikelihood(int numSamples, InstanceList testing) {
		double[][] likelihoods = new double[ testing.size() ][ numSamples ];
		double[] multinomial = new double[numTypes];
		double[] topicDistribution, currentSample, currentWeights;
		Dirichlet topicPrior = new Dirichlet(alpha);	   

		int sample, doc, topic, type, token, seqLen;
		FeatureSequence fs;

		for (sample = 0; sample < numSamples; sample++) {
			topicDistribution = topicPrior.nextDistribution();
			Arrays.fill(multinomial, 0.0);

			for (topic = 0; topic < numTopics; topic++) {
				for (type=0; type<numTypes; type++) {
					multinomial[type] += 
						topicDistribution[topic] *
						(beta + typeTopicCounts[type].get(topic)) /
						(betaSum + tokensPerTopic[topic]);
				}
			}

			// Convert to log probabilities
			for (type=0; type<numTypes; type++) {
				assert(multinomial[type] > 0.0);
				multinomial[type] = Math.log(multinomial[type]);
			}

			for (doc=0; doc<testing.size(); doc++) {
				fs = (FeatureSequence) testing.get(doc).getData();
				seqLen = fs.getLength();

				for (token = 0; token < seqLen; token++) {
					type = fs.getIndexAtPosition(token);

					// Adding this check since testing instances may
					//   have types not found in training instances, 
					//  as pointed out by Steven Bethard.
					if (type < numTypes) {
						likelihoods[doc][sample] += multinomial[type];
					}
				}
			}
		}

		double averageLogLikelihood = 0.0;
		double logNumSamples = Math.log(numSamples);
		for (doc=0; doc<testing.size(); doc++) {
			double max = Double.NEGATIVE_INFINITY;
			for (sample = 0; sample < numSamples; sample++) {
				if (likelihoods[doc][sample] > max) {
					max = likelihoods[doc][sample];
				}
			}

			double sum = 0.0;
			for (sample = 0; sample < numSamples; sample++) {
				sum += Math.exp(likelihoods[doc][sample] - max);
			}

			averageLogLikelihood += Math.log(sum) + max - logNumSamples;
		}

		return averageLogLikelihood;

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
			int[] usedTopics = typeTopicCounts[type].keys();

			for (int topic : usedTopics) {
				int count = typeTopicCounts[type].get(topic);
				if (count > 0) {
					nonZeroTypeTopics++;
					logLikelihood +=
						Dirichlet.logGammaStirling(beta + count);
				}
			}
		}
	
		for (int topic=0; topic < numTopics; topic++) {
			logLikelihood -= 
				Dirichlet.logGammaStirling( (beta * numTopics) +
											tokensPerTopic[ topic ] );
		}
	
		logLikelihood += 
			(Dirichlet.logGammaStirling(beta * numTopics)) -
			(Dirichlet.logGammaStirling(beta) * nonZeroTypeTopics);
	
		return logLikelihood;
	}
	
	// Recommended to use mallet/bin/vectors2topics instead.
	public static void main (String[] args) throws IOException {

		InstanceList training = InstanceList.load (new File(args[0]));

		int numTopics = args.length > 1 ? Integer.parseInt(args[1]) : 200;

		InstanceList testing = 
			args.length > 2 ? InstanceList.load (new File(args[2])) : null;

		LDAHyper lda = new LDAHyper (numTopics, 50.0, 0.01);

		lda.printLogLikelihood = true;
		lda.setTopicDisplay(50,7);
		lda.addInstances(training);
		lda.estimate();
	}
	
}
