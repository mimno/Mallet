/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.	For further
information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import cc.mallet.pipe.FeatureSequence2FeatureVector;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureCounter;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Labeling;
import cc.mallet.types.RankedFeatureVector;
import cc.mallet.util.Randoms;
import gnu.trove.TIntIntHashMap;

/**
* Latent Dirichlet Allocation with different inference methods
* First, training a topic model on training data, then sample topics for test data (inference)
* Second, training a topic model, sample topics for test doc one by one (inferenceOneByOne)
* Third, train a topic model, sample topics for test documents, then retrain on whole dataset (estimateAll)
*
* @author Limin Yao, David Mimno, Andrew McCallum 
*/

public class LDAStream implements Serializable {

	// Analogous to a cc.mallet.classify.Classification
	public class Topication implements Serializable {
		public Instance instance;
		public LDAStream model;
		public LabelSequence topicSequence;
		public Labeling topicDistribution; // not actually constructed by model fitting, but could be added for "test" documents.

		public Topication (Instance instance, LDAStream model, LabelSequence topicSequence) {
			this.instance = instance;
			this.model = model;
			this.topicSequence = topicSequence;
		}
	}

	protected ArrayList<Topication> data;  // the training instances and their topic assignments
	protected ArrayList<Topication> test; // the test instances and their topic assignments
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


	protected int iterationsSoFar = 0;
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

	public LDAStream (int numberOfTopics) {
		this (numberOfTopics, numberOfTopics, DEFAULT_BETA);
	}

	public LDAStream (int numberOfTopics, double alphaSum, double beta) {
		this (numberOfTopics, alphaSum, beta, new Randoms());
	}

	private static LabelAlphabet newLabelAlphabet (int numTopics) {
		LabelAlphabet ret = new LabelAlphabet();
		for (int i = 0; i < numTopics; i++) {
			ret.lookupIndex("topic"+i);
		}
		return ret;
	}

	public LDAStream (int numberOfTopics, double alphaSum, double beta, Randoms random) {
		this (newLabelAlphabet (numberOfTopics), alphaSum, beta, random);
	}

	public LDAStream (LabelAlphabet topicAlphabet, double alphaSum, double beta, Randoms random)
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

	//	System.err.println("LDA: " + numTopics + " topics");
	}


	public Alphabet getAlphabet() { return alphabet; }
	public LabelAlphabet getTopicAlphabet() { return topicAlphabet; }
	public int getNumTopics() { return numTopics; }
	public ArrayList<Topication> getData() { return data; }
	public ArrayList<Topication> getTest() { return test; }
	public TIntIntHashMap getFeatureTopic(int featureIndex) {return typeTopicCounts[featureIndex]; }
	public int getCountFeatureTopic (int featureIndex, int topicIndex) { return typeTopicCounts[featureIndex].get(topicIndex); }
	public int getCountTokensPerTopic (int topicIndex) { return tokensPerTopic[topicIndex]; }

	/** Held-out instances for empirical likelihood calculation */
	public void setTestingInstances(InstanceList testing) {
		this.testing = testing;
	}

	public InstanceList getTestingInstance(){
		return testing;
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

	private int instanceLength (Instance instance) {
		FeatureSequence fs = (FeatureSequence) instance.getData();
		return fs.size();
	}


	// Can be safely called multiple times.  This method will complain if it can't handle the situation
	private void initializeForTypes (Alphabet alphabet) {
		if (this.alphabet == null) {
			this.alphabet = alphabet;
			this.numTypes = alphabet.size();
			this.typeTopicCounts = new TIntIntHashMap[numTypes];
			for (int fi = 0; fi < numTypes; fi++) {
				typeTopicCounts[fi] = new TIntIntHashMap();
			}
			this.betaSum = beta * numTypes;
		} else if (alphabet != this.alphabet) {
			throw new IllegalArgumentException ("Cannot change Alphabet.");
		} else if (alphabet.size() != this.numTypes) {
			this.numTypes = alphabet.size();
			TIntIntHashMap[] newTypeTopicCounts = new TIntIntHashMap[numTypes];
			for (int i = 0; i < typeTopicCounts.length; i++) {
				newTypeTopicCounts[i] = typeTopicCounts[i];
			}
			for (int i = typeTopicCounts.length; i < numTypes; i++) {
				newTypeTopicCounts[i] = new TIntIntHashMap();
			}
			this.betaSum = beta * numTypes;
		}	// else, nothing changed, nothing to be done
	}

	public void addInstances (InstanceList training) {
		initializeForTypes (training.getDataAlphabet());
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : training) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					topics[i] = r.nextInt(numTopics);
				}
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
	private void initializeHistogramsAndCachedValues() {

		int maxTokens = 0;
		int totalTokens = 0;
		int seqLen;

		for (int doc = 0; doc < data.size(); doc++) {
			FeatureSequence fs = (FeatureSequence) data.get(doc).instance.getData();
			seqLen = fs.getLength();
			if (seqLen > maxTokens) {
				maxTokens = seqLen;
			}
			totalTokens += seqLen;
		}
		// Initialize the smoothing-only sampling bucket
		smoothingOnlyMass = 0;
		for (int topic = 0; topic < numTopics; topic++) {
			smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
		}

		// Initialize the cached coefficients, using only smoothing.
		cachedCoefficients = new double[ numTopics ];
		for (int topic=0; topic < numTopics; topic++) {
			cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);
		}

		System.err.println("max tokens: " + maxTokens);
		System.err.println("total tokens: " + totalTokens);

		docLengthCounts = new int[maxTokens + 1];
		topicDocCounts = new int[numTopics][maxTokens + 1];
	}

	public void estimate () throws IOException {
		estimate (numIterations);
		System.out.print("Start calculating the empiricallikelihood of testing instance");
		if (testing != null) {
			  double el = empiricalLikelihood(1000, testing);
			  System.out.println("The empiricalLikelihood of french corpus obtained from lda is: "+  el);
		}
	}

	public void estimate (int iterationsThisRound) throws IOException {

		long startTime = System.currentTimeMillis();
		int maxIteration = iterationsSoFar + iterationsThisRound;

		for ( ; iterationsSoFar <= maxIteration; iterationsSoFar++) {
			long iterationStart = System.currentTimeMillis();

			if (showTopicsInterval != 0 && iterationsSoFar != 0 && iterationsSoFar % showTopicsInterval == 0) {
				System.out.println();
				printTopWords (System.out, wordsPerTopic, false);

				/*
				  if (testing != null) {
				  double el = empiricalLikelihood(1000, testing);
				  }
				  double ll = modelLogLikelihood();
				  double mi = topicLabelMutualInformation();
				  System.out.println(ll + "\t" + el + "\t" + mi);
				*/
			}

			if (saveStateInterval != 0 && iterationsSoFar % saveStateInterval == 0) {
				this.printState(data, new File(stateFilename + '.' + iterationsSoFar));
				//added by Limin Yao
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
				LabelSequence topicSequence = data.get(di).topicSequence;
				sampleTopicsForOneDoc (tokenSequence, topicSequence,
									   iterationsSoFar >= burninPeriod && iterationsSoFar % saveSampleInterval == 0,
									   true);
			}

			System.out.print((System.currentTimeMillis() - iterationStart)/1000 + "s ");
			//System.out.println(topicTermCount + "\t" + betaTopicCount + "\t" + smoothingOnlyCount);
			if (iterationsSoFar % 10 == 0) {
				System.out.println ("<" + iterationsSoFar + "> ");
				if (printLogLikelihood) {
					System.out.println (modelLogLikelihood());
				}
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
		for (int topic = 0; topic < topicDocCounts.length; topic++) {
			Arrays.fill(topicDocCounts[topic], 0);
		}
	}


	//what do we have:
	//typeTopicCounts, tokensPerTopic, topic-sequence of training and test data
	public void estimateAll(int iteration) throws IOException {
		//re-Gibbs sampling on all data
		data.addAll(test);
		initializeHistogramsAndCachedValues();
		estimate(iteration);
	}

	//inference on testdata, one problem is how to deal with unseen words
	//unseen words is in the Alphabet, but typeTopicsCount entry is null
	//added by Limin Yao
	/**
	 * @param maxIteration
	 * @param
	 */
	public void inference(int maxIteration){
		this.test = new ArrayList<Topication>();  //initialize test
		//initial sampling on testdata
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : testing) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				//sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				FeatureSequence fs = (FeatureSequence) instance.getData();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					int type = fs.getIndexAtPosition(i);
					topics[i] = r.nextInt(numTopics);
				/*	if(typeTopicCounts[type].size() != 0) {
						topics[i] = r.nextInt(numTopics);
					} else {
						topics[i] = -1;  // for unseen words
					}*/
				}
			}
			topicSequences.add (topicSequence);
		}

		//construct test
		assert (testing.size() == topicSequences.size());
		for (int i = 0; i < testing.size(); i++) {
			Topication t = new Topication (testing.get(i), this, topicSequences.get(i));
			test.add (t);
			// Include sufficient statistics for this one doc
			// add count on new data to n[k][w] and n[k][*]
			// pay attention to unseen words
			FeatureSequence tokenSequence = (FeatureSequence) t.instance.getData();
			LabelSequence topicSequence = t.topicSequence;
			for (int pi = 0; pi < topicSequence.getLength(); pi++) {
				int topic = topicSequence.getIndexAtPosition(pi);
				int type = tokenSequence.getIndexAtPosition(pi);
				if(topic != -1) // type seen in training
				{
					typeTopicCounts[type].adjustOrPutValue(topic, 1, 1);
				    tokensPerTopic[topic]++;
				}
			}
		}

		long startTime = System.currentTimeMillis();
		//loop
		int iter = 0;
		for ( ; iter <= maxIteration; iter++) {
			if(iter%100==0)
			{
				System.out.print("Iteration: " + iter);
				System.out.println();
			}
			int numDocs = test.size(); // TODO
			for (int di = 0; di < numDocs; di++) {
				FeatureSequence tokenSequence = (FeatureSequence) test.get(di).instance.getData();
				LabelSequence topicSequence = test.get(di).topicSequence;
				sampleTopicsForOneTestDoc (tokenSequence, topicSequence);
			}
		}

		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal inferencing time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}

	//inference method 3, for each doc, for each iteration, for each word
	//compare against inference(that is method2): for each iter, for each doc, for each word
	public void inferenceOneByOne(int maxIteration){
		this.test = new ArrayList<Topication>();  //initialize test
		//initial sampling on testdata
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : testing) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				//sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				FeatureSequence fs = (FeatureSequence) instance.getData();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					int type = fs.getIndexAtPosition(i);
					topics[i] = r.nextInt(numTopics);
					typeTopicCounts[type].adjustOrPutValue(topics[i], 1, 1);
					tokensPerTopic[topics[i]]++;
				/*	if(typeTopicCounts[type].size() != 0) {
						topics[i] = r.nextInt(numTopics);
						typeTopicCounts[type].adjustOrPutValue(topics[i], 1, 1);
						tokensPerTopic[topics[i]]++;
					} else {
						topics[i] = -1;  // for unseen words
					}*/
				}
			}
			topicSequences.add (topicSequence);
		}

		//construct test
		assert (testing.size() == topicSequences.size());
		for (int i = 0; i < testing.size(); i++) {
			Topication t = new Topication (testing.get(i), this, topicSequences.get(i));
			test.add (t);
		}

		long startTime = System.currentTimeMillis();
		//loop
		int iter = 0;
		int numDocs = test.size(); // TODO
		for (int di = 0; di < numDocs; di++) {
			iter = 0;
			FeatureSequence tokenSequence = (FeatureSequence) test.get(di).instance.getData();
			LabelSequence topicSequence = test.get(di).topicSequence;
			for( ; iter <= maxIteration; iter++) {
				sampleTopicsForOneTestDoc (tokenSequence, topicSequence);
			}
			if(di%100==0)
			{
				System.out.print("Docnum: " + di);
				System.out.println();
			}
		}

		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal inferencing time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}

	//first training a topic model on training data,
	//inference on test data, count typeTopicCounts
	// re-sampling on all data
	public void inferenceAll(int maxIteration){
		this.test = new ArrayList<Topication>();  //initialize test
		//initial sampling on testdata
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : testing) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				//sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				FeatureSequence fs = (FeatureSequence) instance.getData();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					int type = fs.getIndexAtPosition(i);
					topics[i] = r.nextInt(numTopics);
					typeTopicCounts[type].adjustOrPutValue(topics[i], 1, 1);
				    tokensPerTopic[topics[i]]++;
				}
			}
			topicSequences.add (topicSequence);
		}

		//construct test
		assert (testing.size() == topicSequences.size());
		for (int i = 0; i < testing.size(); i++) {
			Topication t = new Topication (testing.get(i), this, topicSequences.get(i));
			test.add (t);
		}

		long startTime = System.currentTimeMillis();
		//loop
		int iter = 0;
		for ( ; iter <= maxIteration; iter++) {
			if(iter%100==0)
			{
				System.out.print("Iteration: " + iter);
				System.out.println();
			}
			int numDocs = test.size(); // TODO
			for (int di = 0; di < numDocs; di++) {
				FeatureSequence tokenSequence = (FeatureSequence) test.get(di).instance.getData();
				LabelSequence topicSequence = test.get(di).topicSequence;
				sampleTopicsForOneTestDocAll (tokenSequence, topicSequence);
			}
		}

		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal inferencing time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}

	//called by inferenceAll, using unseen words in testdata
	private void sampleTopicsForOneTestDocAll(FeatureSequence tokenSequence,
			LabelSequence topicSequence) {
		// TODO Auto-generated method stub
		int[] oneDocTopics = topicSequence.getFeatures();

		TIntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double tw;
		double[] topicWeights = new double[numTopics];
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		//		populate topic counts
		int[] localTopicCounts = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++){
			localTopicCounts[ti] = 0;
		}
		for (int position = 0; position < docLength; position++) {
			localTopicCounts[oneDocTopics[position]] ++;
		}

		// Iterate over the positions (words) in the document
		for (int si = 0; si < docLength; si++) {
			type = tokenSequence.getIndexAtPosition(si);
			oldTopic = oneDocTopics[si];

			// Remove this token from all counts
			localTopicCounts[oldTopic] --;

			currentTypeTopicCounts = typeTopicCounts[type];
			assert(currentTypeTopicCounts.get(oldTopic) >= 0);

			if (currentTypeTopicCounts.get(oldTopic) == 1) {
				currentTypeTopicCounts.remove(oldTopic);
			}
			else {
				currentTypeTopicCounts.adjustValue(oldTopic, -1);
			}
			tokensPerTopic[oldTopic]--;

			// Build a distribution over topics for this token
			Arrays.fill (topicWeights, 0.0);
			topicWeightsSum = 0;

			for (int ti = 0; ti < numTopics; ti++) {
				tw = ((currentTypeTopicCounts.get(ti) + beta) / (tokensPerTopic[ti] + betaSum))
				      * ((localTopicCounts[ti] + alpha[ti])); // (/docLen-1+tAlpha); is constant across all topics
				topicWeightsSum += tw;
				topicWeights[ti] = tw;
			}
			// Sample a topic assignment from this distribution
			newTopic = random.nextDiscrete (topicWeights, topicWeightsSum);

			// Put that new topic into the counts
			oneDocTopics[si] = newTopic;
			currentTypeTopicCounts.adjustOrPutValue(newTopic, 1, 1);
			localTopicCounts[newTopic] ++;
			tokensPerTopic[newTopic]++;
		}
	}

	//previously I do not consider unseen words, now this includes unseen words, 
	// because in initialization, I sample a initial topic for all words, commented by Limin Yao
	private void sampleTopicsForOneTestDoc(FeatureSequence tokenSequence,
			LabelSequence topicSequence) {
		// TODO Auto-generated method stub
		int[] oneDocTopics = topicSequence.getFeatures();

		TIntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double tw;
		double[] topicWeights = new double[numTopics];
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		//		populate topic counts
		int[] localTopicCounts = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++){
			localTopicCounts[ti] = 0;
		}
		for (int position = 0; position < docLength; position++) {
			if(oneDocTopics[position] != -1) {
				localTopicCounts[oneDocTopics[position]] ++;
			}
		}

		// Iterate over the positions (words) in the document
		for (int si = 0; si < docLength; si++) {
			type = tokenSequence.getIndexAtPosition(si);
			oldTopic = oneDocTopics[si];
			if(oldTopic == -1) {
				continue;
			}

			// Remove this token from all counts
     		localTopicCounts[oldTopic] --;
     		currentTypeTopicCounts = typeTopicCounts[type];
			assert(currentTypeTopicCounts.get(oldTopic) >= 0);

			if (currentTypeTopicCounts.get(oldTopic) == 1) {
				currentTypeTopicCounts.remove(oldTopic);
			}
			else {
				currentTypeTopicCounts.adjustValue(oldTopic, -1);
			}
			tokensPerTopic[oldTopic]--;

			// Build a distribution over topics for this token
			Arrays.fill (topicWeights, 0.0);
			topicWeightsSum = 0;

			for (int ti = 0; ti < numTopics; ti++) {
				tw = ((currentTypeTopicCounts.get(ti) + beta) / (tokensPerTopic[ti] + betaSum))
				      * ((localTopicCounts[ti] + alpha[ti])); // (/docLen-1+tAlpha); is constant across all topics
				topicWeightsSum += tw;
				topicWeights[ti] = tw;
			}
			// Sample a topic assignment from this distribution
			newTopic = random.nextDiscrete (topicWeights, topicWeightsSum);

			// Put that new topic into the counts
			oneDocTopics[si] = newTopic;
			currentTypeTopicCounts.adjustOrPutValue(newTopic, 1, 1);
			localTopicCounts[newTopic] ++;
			tokensPerTopic[newTopic]++;
		}
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
				if (adjustedValue == 0) {
					currentTypeTopicCounts.remove(oldTopic);
				} else if (adjustedValue == -1) {
					throw new IllegalStateException ("Token count in topic went negative.");
				}
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

			topicTermIndices = currentTypeTopicCounts.keys();  // all topic assignments for current type/word
			topicTermValues = currentTypeTopicCounts.getValues(); // corresponding topic counts

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
				while (sample > 0) { // traverse all previous topics assigned to the current type/word
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
		for (int type = 0; type < numTypes; type++) {
			sortedTypes[type] = new IDSorter(type, typeTopicCounts[type].get(topic));
		}
		Arrays.sort(sortedTypes);
		return sortedTypes;
	}

	public void printTopWords (File file, int numWords, boolean useNewLines) throws IOException {
		PrintStream out = new PrintStream (file);
		printTopWords(out, numWords, useNewLines);
		out.close();
	}

	public void oldPrintTopWords (PrintStream out, int numWords, boolean usingNewLines) {
		for (int topic = 0; topic < numTopics; topic++) {
			IDSorter[] sortedTypes = getSortedTopicWords(topic);
			if (usingNewLines) {
				out.print ("Topic " + topic + "\n");
				for (int i = 0; i < numWords && i < sortedTypes.length; i++) {
					out.print (alphabet.lookupObject(sortedTypes[i].getID()) + "\t" +
								   sortedTypes[i].getWeight() + "\n");
				}
				out.print("\n");
			}	else {
				out.print (topic + "\t" + formatter.format(alpha[topic]) + "\t");
				for (int i = 0; i < numWords && i < sortedTypes.length; i++) {
					out.print (alphabet.lookupObject(sortedTypes[i].getID()) + " ");
				}
				out.print ("\n");
			}
		}
	}

	public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {
		FeatureCounter[] wordCountsPerTopic = new FeatureCounter[numTopics];
		for (int ti = 0; ti < numTopics; ti++) {
			wordCountsPerTopic[ti] = new FeatureCounter(alphabet);
		}
		for (int fi = 0; fi < numTypes; fi++) {
			int[] topics = typeTopicCounts[fi].keys();
			for (int i = 0; i < topics.length; i++) {
				wordCountsPerTopic[topics[i]].increment(fi, typeTopicCounts[fi].get(topics[i]));
				//System.out.print (" "+typeTopicCounts[fi].get(topics[i]));
			}
			//System.out.println();
		}
		for (int ti = 0; ti < numTopics; ti++) {
			RankedFeatureVector rfv = wordCountsPerTopic[ti].toRankedFeatureVector();
			if (usingNewLines) {
				out.println ("Topic " + ti);
				int max = rfv.numLocations(); if (max > numWords) {
					max = numWords;
				}
				for (int ri = 0; ri < max; ri++) {
					int fi = rfv.getIndexAtRank(ri);
					out.println (alphabet.lookupObject(fi).toString()+"\t"+(int)rfv.getValueAtRank(ri));
				}
			} else {
				out.print (ti + "\t" + formatter.format(alpha[ti]) + "\t");
				int max = rfv.numLocations(); if (max > numWords) max = numWords;
				for (int ri = 0; ri < max; ri++) {
					out.print (alphabet.lookupObject(rfv.getIndexAtRank(ri)).toString()+" ");
				}
				out.print ("\n");
			}
		}
	}
// added by Limin Yao, print phi file for classification
	public void printTopicWords(File file, boolean b, double threshold) throws IOException
	{
		// TODO Auto-generated method stub

		InstanceList phi = new InstanceList(alphabet,null);
		for (int ti = 0; ti < numTopics; ti++) {

		    int[] tmpFeatureIndex = new int[numTypes];
		    double[] tmpValues = new double[numTypes];
		    int indice=0;
			for(int fi = 0; fi < numTypes; fi ++) {
				double val = typeTopicCounts[fi].get(ti);
				val /= tokensPerTopic[ti];
				if(val < threshold){
					continue;
				}
				tmpFeatureIndex[indice] = fi;
				tmpValues[indice] = val;
				indice++;
			}
			int[] featureIndex = new int[indice];
			double[] values = new double[indice];
			for (int i = 0; i < indice; i++) {
				featureIndex[i] = tmpFeatureIndex[i];
				values[i] = tmpValues[i];
			}
            FeatureVector fv = new FeatureVector(alphabet,featureIndex,values);
            Instance topicInstance = new Instance(fv,null,null,null);
            phi.add(topicInstance);
		}
		phi.save(file);
	}


	public void printTest(File f) throws IOException
	{
		//testset is in sequence now, it should be converted to featurevector
		FeatureSequence2FeatureVector fseq2fv = new FeatureSequence2FeatureVector();

		//Serialization of
 		InstanceList test = new InstanceList(alphabet,null);

 		for (int i = 0; i < testing.size(); i++){

 			//get FeatureSequence of Instance, then convert it to FeatureVector use fseq2fv
			Instance instanceSeq = testing.get(i);
            if(instanceSeq.isLocked()){
            	instanceSeq.unLock();
            }
            Instance instance = fseq2fv.pipe(instanceSeq);
            instance.setTarget(null);
            test.add(instance);
    	}
        test.save(f);
	}

	public void printDocumentTopics (ArrayList<Topication> dataset, File f) throws IOException {
		printDocumentTopics (dataset, new PrintWriter (new FileWriter (f) ) );
	}

	public void printDocumentTopics (ArrayList<Topication> dataset, PrintWriter pw) {
		printDocumentTopics (dataset, pw, 0.0, -1);
	}

	/**
	 *  @param pw          A print writer
	 *  @param threshold   Only print topics with proportion greater than this number
	 *  @param max         Print no more than this many topics
	 */
	public void printDocumentTopics (ArrayList<Topication> dataset, PrintWriter pw, double threshold, int max)	{
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

		for (int di = 0; di < dataset.size(); di++) {
			LabelSequence topicSequence = dataset.get(di).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();

			pw.print (di); pw.print (' ');

			if (dataset.get(di).instance.getSource() != null) {
				pw.print (dataset.get(di).instance.getSource());
			}
			else {
				pw.print ("null-source");
			}

			pw.print (' ');
			docLen = currentDocTopics.length;

			// Count up the tokens
			int realDocLen = 0;
			for (int token=0; token < docLen; token++) {
				if(currentDocTopics[token] != -1) {
					topicCounts[ currentDocTopics[token] ]++;
					realDocLen ++;
				}
			}
			assert(realDocLen == docLen);

			// And normalize
			for (int topic = 0; topic < numTopics; topic++) {
				sortedTopics[topic].set(topic, (float) topicCounts[topic] / realDocLen);
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
        pw.close();
	}

	public void printSerialDocumentTopics (ArrayList<Topication> dataset, File f, double threshold, int max) {
		//generate the serialized form of doc-topic proportion
		InstanceList docTopics = new InstanceList(topicAlphabet, null);
		int[] topicCounts = new int[ numTopics ];
		int docLen;
		double[] topicDistribution = new double[numTopics];
		for (int di = 0; di < dataset.size(); di++) {
			LabelSequence topicSequence = dataset.get(di).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();
			docLen = currentDocTopics.length;
			// Count up the tokens
			int realDocLen = 0;
			for (int token=0; token < docLen; token++) {
				if(currentDocTopics[token] != -1) {
					topicCounts[ currentDocTopics[token] ]++;
					realDocLen ++;
				}
			}
			assert(realDocLen == docLen);

			// And normalize
			for (int topic = 0; topic < numTopics; topic++) {
				topicDistribution[topic] = (float) topicCounts[topic] / realDocLen;
			}
			docTopics.add(new Instance (new FeatureVector(topicAlphabet,topicDistribution), null, null, dataset.get(di).instance.getSource()));
			Arrays.fill(topicCounts, 0);
		}
        docTopics.save(f);
	}

	/**
	 * This function is called to build the training data,
	 * each instance i.e. featurevector is with a distribuion label,
	 * which is obtained from theta, i.e. doc-topic proportion
	 * @param dataset: contains only feature vector
	 * @param f: storing the training data
	 * @param threshold
	 * @param max
	 */
	public void printTraining (ArrayList<Topication> dataset, File f, double threshold, int max) {
		//generate training data as the input of MaxEnt classifier
		InstanceList training = new InstanceList(alphabet, topicAlphabet);
		int[] topicCounts = new int[ numTopics ];
		int docLen;
		double[] topicDistribution = new double[numTopics];
		FeatureSequence2FeatureVector fseq2fv = new FeatureSequence2FeatureVector();
		for (int di = 0; di < dataset.size(); di++) {
			//dealing with data, convert feature sequence to featurevector
			Instance instanceSeq = dataset.get(di).instance;
            if(instanceSeq.isLocked()){
            	instanceSeq.unLock();
            }
            Instance instance = fseq2fv.pipe(instanceSeq);
            FeatureVector fv = (FeatureVector) instance.getData();

            //dealing with labelsequence, convert it to doc-topic proportion
			LabelSequence topicSequence = dataset.get(di).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();
			docLen = currentDocTopics.length;
			// Count up the tokens
			for (int token=0; token < docLen; token++) {
					topicCounts[ currentDocTopics[token] ]++;
			}
			// And normalize
			int num=0;
			for (int topic = 0; topic < numTopics; topic++) {
				num+=topicCounts[topic];
				topicDistribution[topic] = (float) topicCounts[topic] / docLen;
			}

			LabelVector lv = new LabelVector(topicAlphabet,topicDistribution);
			training.add(new Instance (fv, lv, null, dataset.get(di).instance.getSource()));

			Arrays.fill(topicCounts, 0);
		}
        training.save(f);
	}



	public void printState (ArrayList<Topication> dataset, File f) throws IOException {
		PrintStream out =
			new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(f))));
		printState(dataset, out);
		out.close();
	}

	public void printState (ArrayList<Topication> dataset, PrintStream out) {

		out.println ("#doc source pos typeindex type topic");

		for (int di = 0; di < dataset.size(); di++) {
			FeatureSequence tokenSequence =	(FeatureSequence) dataset.get(di).instance.getData();
			LabelSequence topicSequence =	dataset.get(di).topicSequence;

			String source = "NA";
			if (dataset.get(di).instance.getSource() != null) {
				source = dataset.get(di).instance.getSource().toString();
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

	public void read (File f) throws ClassNotFoundException {
		try {
			ObjectInputStream iis = new ObjectInputStream (new FileInputStream(f));
			iis.readObject();
		}
		catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
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

		for (int fi = 0; fi < numTypes; fi++) {
			out.writeObject (typeTopicCounts[fi]);
		}

		for (int ti = 0; ti < numTopics; ti++) {
			out.writeInt (tokensPerTopic[ti]);
		}
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
		for (int fi = 0; fi < numTypes; fi++) {
			typeTopicCounts[fi] = (TIntIntHashMap) in.readObject();
		}
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++) {
			tokensPerTopic[ti] = in.readInt();
		}
	}

	//for inference, write model to a model file
	public void writeModel (ObjectOutputStream out) throws IOException {
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
		//out.writeBoolean(printLogLikelihood);

		out.writeObject(docLengthCounts);
		out.writeObject(topicDocCounts);

		for (int fi = 0; fi < numTypes; fi++) {
			out.writeObject (typeTopicCounts[fi]);
		}

		for (int ti = 0; ti < numTopics; ti++) {
			out.writeInt (tokensPerTopic[ti]);
		}

		out.close();//close the OutputStream
	}

	// for inference, loading model
	public void loadModel (ObjectInputStream in) throws IOException, ClassNotFoundException {
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
		//printLogLikelihood = in.readBoolean();

		docLengthCounts = (int[]) in.readObject();
		topicDocCounts = (int[][]) in.readObject();

		//int numDocs = data.size();
		this.numTypes = alphabet.size();

		typeTopicCounts = new TIntIntHashMap[numTypes];
		for (int fi = 0; fi < numTypes; fi++) {
			typeTopicCounts[fi] = (TIntIntHashMap) in.readObject();
		}
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++) {
			tokensPerTopic[ti] = in.readInt();
		}
	}


	public double topicLabelMutualInformation() {
		int doc, level, label, topic, token, type;
		int[] docTopics;

		int targetAlphabetSize = data.get(0).instance.getTargetAlphabet().size();
		int[][] topicLabelCounts = new int[ numTopics ][ targetAlphabetSize ];
		int[] topicCounts = new int[ numTopics ];
		int[] labelCounts = new int[ targetAlphabetSize ];
		int total = 0;

		for (doc=0; doc < data.size(); doc++) {
			label = data.get(doc).instance.getLabeling().getBestIndex();

			LabelSequence topicSequence = data.get(doc).topicSequence;
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
			LabelSequence topicSequence =	data.get(doc).topicSequence;

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

			for (int topic=0; topic < numTopics; topic++) {
				if (topicCounts[topic] > 0) {
					nonZeroTypeTopics++;
					logLikelihood += Dirichlet.logGammaStirling(beta + typeTopicCounts[type].get(topic));
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

	public double getBeta() {
		// TODO Auto-generated method stub
		return beta;
	}

	public double getBetaSum() {
		return betaSum;
	}

	public double getAlphaSum() {
		return alphaSum;
	}

	public double[] getAlpha() {
		// TODO Auto-generated method stub
		return alpha;
	}

	// Recommended to use mallet/bin/vectors2topics instead.
/*	public static void main (String[] args) throws IOException {

		InstanceList training = InstanceList.load (new File(args[0]));

		int numTopics = args.length > 1 ? Integer.parseInt(args[1]) : 200;

		InstanceList testing =
			args.length > 2 ? InstanceList.load (new File(args[2])) : null;

		LDAStream lda = new LDAStream (numTopics, 50.0, 0.01);
		lda.addInstances(training);
		lda.estimate();
	}*/

}

