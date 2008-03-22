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

import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;

/**
 * Latent Dirichlet Allocation with optimized hyperparameters
 * 
 * @author David Mimno, Andrew McCallum
 */

public class LDAHyper {
	
	// Analogous to a cc.mallet.classify.Classification
	public class Topication {
		public Instance instance;
		public LDAHyper model;
		public LabelSequence topicSequence;
		public Labeling topicDistribution; // not actually constructed by model fitting, but could be added for "test" documents.
		
		public Topication (Instance instance, LDAHyper model, LabelSequence topicSequence) {
			this.instance = instance;
			this.model = model;
			this.topicSequence = topicSequence;
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

	// Instance list for empirical likelihood calculation
	protected InstanceList testing = null;
	
	// An array to put the topic counts for
	//  the current document. Defined here to avoid
	//   garbage collection overhead.
	protected int[] oneDocTopicCounts; // indexed by <document index, topic index>

	protected int[][] typeTopicCounts; // indexed by <feature index, topic index>
	protected int[] tokensPerTopic; // indexed by <topic index>

	protected double[] topicWeights;

	// for dirichlet estimation
	protected int[] docLengthCounts; // histogram of document sizes
	protected int[][] topicDocCounts; // histogram of document/topic counts, indexed by <topic index, sequence position index>

	protected int iterationsSoFar = 0;
	protected int numIterations = 1000;
	protected int burninPeriod = 200;
	protected int saveSampleInterval = 10;	
	protected int optimizeInterval = 50;
	protected int showTopicsInterval = 50;
	protected int wordsPerTopic = 5;

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
		topicWeights = new double[numTopics];
		
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		System.out.println("LDA: " + numTopics + " topics");
	}
	
	public Alphabet getAlphabet() { return alphabet; }
	public LabelAlphabet getTopicAlphabet() { return topicAlphabet; }
	public int getNumTopics() { return numTopics; }
	public ArrayList<Topication> getData() { return data; }
	public int getCountFeatureTopic (int featureIndex, int topicIndex) { return typeTopicCounts[featureIndex][topicIndex]; }
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
	
	private int instanceLength (Instance instance) {
		return ((FeatureSequence)instance.getData()).size();
	}
	
	// Can be safely called multiple times.  This method will complain if it can't handle the situation
	private void initializeForTypes (Alphabet alphabet) {
		if (this.alphabet == null) {
			this.alphabet = alphabet;
			this.numTypes = alphabet.size();
			this.typeTopicCounts = new int[numTypes][numTopics];
			this.betaSum = beta * numTypes;
		} else if (alphabet != this.alphabet) {
			throw new IllegalArgumentException ("Cannot change Alphabet.");
		} else if (alphabet.size() != this.numTypes) {
			this.numTypes = alphabet.size();
			int[][] newTypeTopicCounts = new int[numTypes][numTopics];
			for (int i = 0; i < typeTopicCounts.length; i++)
				newTypeTopicCounts[i] = typeTopicCounts[i];
			this.betaSum = beta * numTypes;
		}	// else, nothing changed, nothing to be done
	}
	
	public void addInstances (InstanceList training) {
		initializeForTypes (training.getDataAlphabet());
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : training) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
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
			for (int token = 0; token < topicSequence.getLength(); token++) {
				int topic = topicSequence.getIndexAtPosition(token);
				typeTopicCounts[tokenSequence.getIndexAtPosition(token)][topic]++;
				tokensPerTopic[topic]++;
			}
		}
		initializeHistograms();
	}

	/** 
	 *  Gather statistics on the size of documents 
	 *  and create histograms for use in Dirichlet hyperparameter
	 *  optimization.
	 */
	private void initializeHistograms() {

		int maxTokens = 0;
		int totalTokens = 0;
		int topic, seqLen;

		for (int doc = 0; doc < data.size(); doc++) {
			FeatureSequence fs = (FeatureSequence) data.get(doc).instance.getData();
			seqLen = fs.getLength();
			if (seqLen > maxTokens)
				maxTokens = seqLen;
			totalTokens += seqLen;
		}
		System.out.println("max tokens: " + maxTokens);
		System.out.println("total tokens: " + totalTokens);

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
				this.printState(new File(stateFilename + '.' + iterationsSoFar));
			}

			/*
			  if (outputModelInterval != 0 && iterations % outputModelInterval == 0) {
			  this.write (new File(outputModelFilename+'.'+iterations));
			  }
			*/
			if (iterationsSoFar > burninPeriod && optimizeInterval != 0 &&	iterationsSoFar % optimizeInterval == 0) {
				long optimizeTime = System.currentTimeMillis();
				alphaSum = Dirichlet.learnParameters(alpha, topicDocCounts, docLengthCounts);
				//System.out.print("[o:" + (System.currentTimeMillis() - optimizeTime) + "]");
				clearHistograms();
			}

			// Loop over every document in the corpus
			for (int di = 0; di < data.size(); di++) {
				FeatureSequence tokenSequence = (FeatureSequence) data.get(di).instance.getData();
				LabelSequence topicSequence = (LabelSequence) data.get(di).topicSequence;
				sampleTopicsForOneDoc (tokenSequence, topicSequence,
									   iterationsSoFar > burninPeriod && iterationsSoFar % saveSampleInterval == 0,
									   true);
			}
		
			System.out.print((System.currentTimeMillis() - iterationStart) + " ");
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
	 *   then readjustToipcsAndParameters should be true.  The topics will be re-sampled and sufficient statistics changes.
	 *  If operating on a new or a test document, and featureSequence & topicSequence are not already accounted for in the sufficient statistics, 
	 *   then readjustToipcsAndParameters should be false.  The current topic assignments will be ignored, and the sufficient statistics
	 *   will not be changed.
	 *  If you want to estimate the Dirichlet alpha based on the per-document topic multinomials sampled this round, 
	 *   then saveStateForAlphaEstimation should be true. */
	private void sampleTopicsForOneDoc (FeatureSequence featureSequence, 
			FeatureSequence topicSequence,
			boolean saveStateForAlphaEstimation, boolean readjustTopicsAndStats) 
	{
		long startTime = System.currentTimeMillis();
	
		int[] oneDocTopics = topicSequence.getFeatures();

		int[] currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double topicWeightsSum;
		int docLen = featureSequence.getLength();

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

			if (readjustTopicsAndStats) {
				// Remove this token from all counts
				oneDocTopicCounts[oldTopic]--;
				typeTopicCounts[type][oldTopic]--;
				tokensPerTopic[oldTopic]--;
			}

			// Build a distribution over topics for this token
			topicWeightsSum = 0;
			currentTypeTopicCounts = typeTopicCounts[type];
			for (int topic = 0; topic < numTopics; topic++) {
				weight = ((currentTypeTopicCounts[topic] + beta) /
						  (tokensPerTopic[topic] + betaSum))
					* ((oneDocTopicCounts[topic] + alpha[topic]));
				topicWeightsSum += weight;
				topicWeights[topic] = weight;
			}

			// Sample a topic assignment from this distribution
			newTopic = random.nextDiscrete (topicWeights, topicWeightsSum);
		
			if (readjustTopicsAndStats) {
				// Put that new topic into the counts
				oneDocTopics[token] = newTopic;
				oneDocTopicCounts[newTopic]++;
				typeTopicCounts[type][newTopic]++;
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

	public IDSorter[] getSortedTopicWords(int topic) {
		IDSorter[] sortedTypes = new IDSorter[ numTypes ];
		for (int type = 0; type < numTypes; type++)
			sortedTypes[type] = new IDSorter(type, typeTopicCounts[type][topic]);
		Arrays.sort(sortedTypes);
		return sortedTypes;
	}

	public void printTopWords (File file, int numWords, boolean useNewLines) throws IOException {
		PrintStream out = new PrintStream (file);
		printTopWords(out, numWords, useNewLines);
		out.close();
	}
	
	public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {
		for (int topic = 0; topic < numTopics; topic++) {
			IDSorter[] sortedTypes = getSortedTopicWords(topic);
			if (usingNewLines) {
				out.print ("Topic " + topic + "\n");
				for (int i = 0; i < numWords && i < sortedTypes.length; i++)
					out.print (alphabet.lookupObject(sortedTypes[i].getID()) + "\t" +
								   formatter.format(sortedTypes[i].getWeight()) + "\n");
				out.print("\n");
			}	else {
				out.print (topic + "\t" + formatter.format(alpha[topic]) + "\t");
				for (int i = 0; i < numWords && i < sortedTypes.length; i++)
					out.print (alphabet.lookupObject(sortedTypes[i].getID()) + " ");
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
			FeatureSequence topicSequence = (FeatureSequence) data.get(di).instance.getData();
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
		}
		
	}
	
	public void printState (File f) throws IOException {
		PrintStream out =
			new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(f))));
		printState(out);
		out.close();
	}
	
	public void printState (PrintStream out) {
		out.println ("#doc pos typeindex type topic");
		for (int di = 0; di < data.size(); di++) {
			FeatureSequence tokenSequence =	(FeatureSequence) data.get(di).instance.getData();
			LabelSequence topicSequence =	(LabelSequence) data.get(di).topicSequence;
			for (int token = 0; token < topicSequence.getLength(); token++) {
				int type = tokenSequence.getIndexAtPosition(token);
				int topic = topicSequence.getIndexAtPosition(token);
				out.print(di); out.print(' ');
				out.print(token); out.print(' ');
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

		for (int fi = 0; fi < numTypes; fi++)
			for (int ti = 0; ti < numTopics; ti++)
				out.writeInt (typeTopicCounts[fi][ti]);

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
		int numDocs = data.size();

		int numTypes = alphabet.size();
		typeTopicCounts = new int[numTypes][numTopics];
		for (int fi = 0; fi < numTypes; fi++)
			for (int ti = 0; ti < numTopics; ti++)
				typeTopicCounts[fi][ti] = in.readInt();
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++)
			tokensPerTopic[ti] = in.readInt();
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
						(beta + typeTopicCounts[type][topic]) /
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
					likelihoods[doc][sample] += multinomial[type];
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
			// reuse this array as a pointer
			topicCounts = typeTopicCounts[type];

			for (int topic=0; topic < numTopics; topic++) {
				if (topicCounts[topic] > 0) {
					nonZeroTypeTopics++;
					logLikelihood += Dirichlet.logGammaStirling(beta + topicCounts[topic]);
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
		lda.addInstances(training);
		lda.estimate();
	}
	
}
