/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.Dirichlet;

/**
 * Latent Dirichlet Allocation.
 * @author David Mimno, Andrew McCallum
 */

public class MultinomialHMM {

	int numTopics; // Number of topics to be fit
	int numStates; // Number of hidden states
	int numTypes;
	int numTokens;
	int numDocs;
	int numSequences;

	// Dirichlet(alpha,alpha,...) is the distribution over topics
	double[] alpha;
	double alphaSum;

	// Prior on per-topic multinomial distribution over words
	double beta;
	double betaSum;

	// Prior on the state-state transition distributions
	double gamma;
	double gammaSum;

	double pi;
	double sumPi;

	InstanceList instances, testing;  // the data field of the instances is expected to hold a FeatureSequence

	int[][] topics; // indexed by <document index, sequence index>
	int[] oneDocTopicCounts; // indexed by <document index, topic index>
	int[][] typeTopicCounts; // indexed by <feature index, topic index>
	int[] tokensPerTopic; // indexed by <topic index>

	int[] documentSequenceIDs;

	int[] documentStates;
	int[][] stateTopicCounts;
	int[] stateTopicTotals;
	int[][] stateStateTransitions;
	int[] stateTransitionTotals;

	int[] initialStateCounts;

	// These two arrays are only used by the sampler within
	//  a single document, but declare them here to avoid 
	//   garbage collection.
	int[] docTopicCounts;
	double[] topicWeights;

	// for dirichlet estimation
	int[] docLengthCounts; // histogram of document sizes
	int[][] topicDocCounts; // histogram of document/topic counts

	int numIterations = 1000;
	int burninPeriod = 200;
	int saveSampleInterval = 10;    
	int optimizeInterval = 0;
	int showTopicsInterval = 50;

	String[] topicKeys;

	Randoms random;

	Runtime runtime;
	NumberFormat formatter;

	public MultinomialHMM (int numberOfTopics) {
		this (numberOfTopics, numberOfTopics, 0.01);
	}

	public MultinomialHMM (int numberOfTopics, double alphaSum, double beta)
	{
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		System.out.println("LDA HMM: " + numberOfTopics);

		this.numTopics = numberOfTopics;
		this.alphaSum = alphaSum;
		this.alpha = new double[numberOfTopics];
		Arrays.fill(alpha, alphaSum / numTopics);
		this.beta = beta;

		runtime = Runtime.getRuntime();
	}

	public void setTrainingInstances(InstanceList training) {
		this.instances = training;
	}

	/** Held-out instances for empirical likelihood calculation */
	public void setTestingInstances(InstanceList testing) {
		this.testing = testing;
	}

	public void setNumStates(int s) {
		this.numStates = s;
	}

	public void setGamma(double g) {
		this.gamma = g;
	}

	public void setNumIterations (int numIterations) {
		this.numIterations = numIterations;
	}

	public void setBurninPeriod (int burninPeriod) {
		this.burninPeriod = burninPeriod;
	}

	public void setTopicDisplayInterval(int interval) {
		this.showTopicsInterval = interval;
	}

	public void setRandomSeed(int seed) {
		random = new Randoms(seed);
	}

	public void setOptimizeInterval(int interval) {
		this.optimizeInterval = interval;
	}

	public void initialize () {

		if (random == null) {
			random = new Randoms();
		}

		numTypes = instances.getDataAlphabet().size ();
		numDocs = instances.size();

		topics = new int[numDocs][];
		oneDocTopicCounts = new int[numTopics];
		typeTopicCounts = new int[numTypes][numTopics];
		tokensPerTopic = new int[numTopics];

		topicKeys = new String[numTopics];

		docTopicCounts = new int[numTopics];
		topicWeights = new double[numTopics];

		betaSum = beta * numTypes;
		gammaSum = gamma * numStates;

		stateTopicCounts = new int[numStates][numTopics];
		stateTopicTotals = new int[numStates];
		stateStateTransitions = new int[numStates][numStates];
		stateTransitionTotals = new int[numStates];

		initialStateCounts = new int[numStates];

		pi = 1000.0;
		sumPi = numStates * pi;


		documentStates = new int[numDocs];
		documentSequenceIDs = new int[numDocs];

		int maxTokens = 0;
		int totalTokens = 0;

		numSequences = 0;

		int sequenceID;
		int currentSequenceID = -1;

		// Initialize with random assignments of tokens to topics
		// and finish allocating this.topics and this.tokens
		int topic, seqLen;
		for (int doc = 0; doc < numDocs; doc++) {
			FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();

			// Choose a random state

			documentStates[doc] = random.nextInt(numStates);

			// All other initialization will be done later, loading a saved
			//  topic state from files.

			seqLen = fs.getLength();
			if (seqLen > maxTokens) { 
				maxTokens = seqLen;
			}
			totalTokens += seqLen;

			numTokens += seqLen;
			topics[doc] = new int[seqLen];
		}

		System.out.println("max tokens: " + maxTokens);
		System.out.println("total tokens: " + totalTokens);

		// These will be initialized at the first call to 
		//  clearHistograms() in the loop below.
		docLengthCounts = new int[maxTokens + 1];
		topicDocCounts = new int[numTopics][maxTokens + 1];
	}


	public void estimate() throws IOException {

		long startTime = System.currentTimeMillis();

		for (int iterations = 1; iterations <= numIterations; iterations++) {
			long iterationStart = System.currentTimeMillis();

			if (showTopicsInterval != 0 && iterations % showTopicsInterval == 0) {
				System.out.println();
				System.out.println(printTopWords (5, false));

				/*
		if (testing != null) {
		    double el = empiricalLikelihood(1000, testing);
		}
		double ll = modelLogLikelihood();
		double mi = printTopicLabels();
		System.out.println(ll + "\t" + el + "\t" + mi);
				 */
			}
			/*
	      if (outputModelInterval != 0 && iterations % outputModelInterval == 0) {
	      this.write (new File(outputModelFilename+'.'+iterations));
	      }
			 */

			//System.out.println (printStateTransitions());
			for (int doc = 0; doc < topics.length; doc++) {
				sampleTopicsForOneDoc (doc, random, (iterations > burninPeriod &&
						iterations % saveSampleInterval == 0));

				//if (doc % 10000 == 0) { System.out.println (printStateTransitions()); }
			}

			System.out.print((System.currentTimeMillis() - iterationStart) + " ");

			if (iterations % 10 == 0) {
				System.out.println ("<" + iterations + "> ");

				PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("state_state_matrix." + iterations)));
				out.print(stateTransitionMatrix());
				out.close();

				out = new PrintWriter(new BufferedWriter(new FileWriter("state_topics." + iterations)));
				out.print(stateTopics());
				out.close();

				if (iterations % 10 == 0) {
					out = new PrintWriter(new BufferedWriter(new FileWriter("states." + iterations)));

					for (int doc = 0; doc < documentStates.length; doc++) {
						out.println(documentStates[doc]);
					}

					out.close();
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

	public void loadTopicsFromFile(String stateFilename) throws IOException {
		BufferedReader in = 
			new BufferedReader(new FileReader(new File(stateFilename)));

		String line = null;
		while ((line = in.readLine()) != null) {
			if (line.startsWith("#")) {
				continue;
			}

			String[] fields = line.split(" ");
			int doc = Integer.parseInt(fields[0]);
			int token = Integer.parseInt(fields[1]);
			int type = Integer.parseInt(fields[2]);
			int topic = Integer.parseInt(fields[4]);

			// Now add the new topic

			topics[doc][token] = topic;

			stateTopicCounts[ documentStates[doc] ][ topic ]++;
			stateTopicTotals[ documentStates[doc] ]++;

			typeTopicCounts[ type ][ topic ]++;
			tokensPerTopic[topic]++;
		}
		in.close();

		System.out.println("loaded topics");
	}

	public void loadAlphaFromFile(String alphaFilename) throws IOException {

		// Now restore the saved alpha parameters
		alphaSum = 0.0;

		BufferedReader in = new BufferedReader(new FileReader(new File(alphaFilename)));
		String line = null;
		while ((line = in.readLine()) != null) {
			if (line.equals("")) { continue; }

			String[] fields = line.split("\\s+");

			int topic = Integer.parseInt(fields[0]);
			alpha[topic] = 1.0; // Double.parseDouble(fields[1]);
			alphaSum += alpha[topic];

			StringBuffer topicKey = new StringBuffer();
			for (int i=2; i<fields.length; i++) {
				topicKey.append(fields[i] + " ");
			}
			topicKeys[topic] = topicKey.toString();
		}
		in.close();

		System.out.println("loaded alpha");
	}

	public void loadStatesFromFile(String stateFilename) throws IOException {

		int doc = 0;

		int state;

		BufferedReader in = new BufferedReader(new FileReader(new File(stateFilename)));
		String line = null;
		while ((line = in.readLine()) != null) {

			// We assume that the sequences are in the instance list
			//  in order.

			state = Integer.parseInt(line);
			documentStates[doc] = state;

			// Additional bookkeeping will be performed when we load sequence IDs, 
			// so states MUST be loaded before sequences.

			doc++;
		}
		in.close();

		System.out.println("loaded states");
	}


	public void loadSequenceIDsFromFile(String sequenceFilename) throws IOException {

		int doc = 0;

		int sequenceID;
		int currentSequenceID = -1;

		BufferedReader in = new BufferedReader(new FileReader(new File(sequenceFilename)));
		String line = null;
		while ((line = in.readLine()) != null) {

			// We assume that the sequences are in the instance list
			//  in order.

			sequenceID = Integer.parseInt(line);

			documentSequenceIDs[doc] = sequenceID;

			if (sequenceID == currentSequenceID) {
				// this is a continuation of the previous sequence

				stateStateTransitions[ documentStates[doc-1] ][ documentStates[doc] ]++;
				stateTransitionTotals[ documentStates[doc-1] ]++;

			}
			else {
				initialStateCounts[ documentStates[doc] ]++;
				numSequences ++;
			}

			currentSequenceID = sequenceID;

			doc++;

		}
		in.close();

		System.out.println("loaded sequence");
	}

	private void sampleTopicsForOneDoc (int doc, Randoms r, boolean shouldSaveState) {

		long startTime = System.currentTimeMillis();

		FeatureSequence oneDocTokens = 
			(FeatureSequence)instances.get(doc).getData();

		int oldState = documentStates[doc];

		int[] oneDocTopics = topics[doc];

		Arrays.fill(docTopicCounts, 0);

		int[] currentTypeTopicCounts;
		int[] currentStateTopicCounts = stateTopicCounts[oldState];

		int type;
		double topicWeightsSum;
		int docLen = oneDocTokens.getLength();

		double tw;

		// Iterate over the positions (words) in the document

		for (int token = 0; token < docLen; token++) {
			int topic = oneDocTopics[token];

			currentStateTopicCounts[topic]--;
			docTopicCounts[topic]++;
		}

		// At this point, we have shifted all of the counts for the tokens
		// for this document out of the current state's stateTopicCounts.

		int previousSequenceID = -1; 
		if (doc > 0) {
			previousSequenceID = documentSequenceIDs[ doc-1 ];
		}

		int sequenceID = documentSequenceIDs[ doc ];

		int nextSequenceID = -1; 
		if (doc < numDocs - 1) { 
			nextSequenceID = documentSequenceIDs[ doc+1 ];
		}

		double[] stateLogLikelihoods = new double[numStates];
		double[] samplingDistribution = new double[numStates];

		int nextState, previousState;

		// There are four cases:

		if (previousSequenceID != sequenceID && sequenceID != nextSequenceID) {
			// 1. This is a singleton document

			initialStateCounts[oldState]--;

			for (int state = 0; state < numStates; state++) {
				stateLogLikelihoods[state] = Math.log( (initialStateCounts[state] + pi) /
						(numSequences - 1 + sumPi) );
			}
		}	    
		else if (previousSequenceID != sequenceID) {
			// 2. This is the beginning of a sequence

			initialStateCounts[oldState]--;

			nextState = documentStates[doc+1];
			stateStateTransitions[oldState][nextState]--;

			assert(stateStateTransitions[oldState][nextState] >= 0);

			stateTransitionTotals[oldState]--;

			for (int state = 0; state < numStates; state++) {
				stateLogLikelihoods[state] = Math.log( (stateStateTransitions[state][nextState] + gamma) * 
						(initialStateCounts[state] + pi) /
						(numSequences - 1 + sumPi) );
				if (Double.isInfinite(stateLogLikelihoods[state])) {
					System.out.println("infinite beginning");
				}

			}
		}
		else if (sequenceID != nextSequenceID) {
			// 3. This is the end of a sequence

			previousState = documentStates[doc-1];
			stateStateTransitions[previousState][oldState]--;

			assert(stateStateTransitions[previousState][oldState] >= 0);

			for (int state = 0; state < numStates; state++) {
				stateLogLikelihoods[state] = Math.log( stateStateTransitions[previousState][state] + gamma );

				if (Double.isInfinite(stateLogLikelihoods[state])) {
					System.out.println("infinite end");
				}
			}
		}
		else {
			// 4. This is the middle of a sequence

			nextState = documentStates[doc+1];
			stateStateTransitions[oldState][nextState]--;
			if (stateStateTransitions[oldState][nextState] < 0) {
				System.out.println(printStateTransitions());
				System.out.println(oldState + " -> " + nextState);

				System.out.println(sequenceID);
			}
			assert (stateStateTransitions[oldState][nextState] >= 0);
			stateTransitionTotals[oldState]--;

			previousState = documentStates[doc-1];
			stateStateTransitions[previousState][oldState]--;
			assert(stateStateTransitions[previousState][oldState] >= 0);

			for (int state = 0; state < numStates; state++) {

				if (previousState == state && state == nextState) {		    
					stateLogLikelihoods[state] =
						Math.log( (stateStateTransitions[previousState][state] + gamma) *
								(stateStateTransitions[state][nextState] + 1 + gamma) / 
								(stateTransitionTotals[state] + 1 + gammaSum) );

				}
				else if (previousState == state) {
					stateLogLikelihoods[state] =
						Math.log( (stateStateTransitions[previousState][state] + gamma) *
								(stateStateTransitions[state][nextState] + gamma) /
								(stateTransitionTotals[state] + 1 + gammaSum) );
				}
				else {
					stateLogLikelihoods[state] =
						Math.log( (stateStateTransitions[previousState][state] + gamma) *
								(stateStateTransitions[state][nextState] + gamma) /
								(stateTransitionTotals[state] + gammaSum) );
				}

				if (Double.isInfinite(stateLogLikelihoods[state])) {
					System.out.println("infinite middle");
				}
			}

		}

		double max = Double.NEGATIVE_INFINITY;

		for (int state = 0; state < numStates; state++) {

			stateLogLikelihoods[state] -= stateTransitionTotals[state] / 10;

			currentStateTopicCounts = stateTopicCounts[state];

			int totalTokens = 0;
			for (int topic = 0; topic < numTopics; topic++) {

				for (int j=0; j < docTopicCounts[topic]; j++) {
					stateLogLikelihoods[state] +=
						Math.log( (alpha[topic] + currentStateTopicCounts[topic] + j) /
								(alphaSum + stateTopicTotals[state] + totalTokens) );

					if (Double.isNaN(stateLogLikelihoods[state])) {
						System.out.println("NaN: "  + alpha[topic] + " + " + currentStateTopicCounts[topic] + " + " + j + ") /\n" + 
								"(" + alphaSum + " + " + stateTopicTotals[state] + " + " + totalTokens);
					}

					totalTokens++;
				}
			}

			if (stateLogLikelihoods[state] > max) {
				max = stateLogLikelihoods[state];
			}

		}

		double sum = 0.0;
		for (int state = 0; state < numStates; state++) {
			if (Double.isNaN(samplingDistribution[state])) {
				System.out.println(stateLogLikelihoods[state]);
			}

			assert(! Double.isNaN(samplingDistribution[state]));

			samplingDistribution[state] = 
				Math.exp(stateLogLikelihoods[state] - max);
			sum += samplingDistribution[state];

			if (Double.isNaN(samplingDistribution[state])) {
				System.out.println(stateLogLikelihoods[state]);
			}

			assert(! Double.isNaN(samplingDistribution[state]));

			if (doc % 100 == 0) {
				//System.out.println(samplingDistribution[state]);
			}
		}

		int newState = r.nextDiscrete(samplingDistribution, sum);

		documentStates[doc] = newState;

		for (int topic = 0; topic < numTopics; topic++) {
			stateTopicCounts[newState][topic] += docTopicCounts[topic];
		}
		stateTopicTotals[newState] += oneDocTokens.getLength();


		if (previousSequenceID != sequenceID && sequenceID != nextSequenceID) {
			// 1. This is a singleton document

			initialStateCounts[newState]++;
		}	    
		else if (previousSequenceID != sequenceID) {
			// 2. This is the beginning of a sequence

			initialStateCounts[newState]++;

			nextState = documentStates[doc+1];
			stateStateTransitions[newState][nextState]++;
			stateTransitionTotals[newState]++;
		}
		else if (sequenceID != nextSequenceID) {
			// 3. This is the end of a sequence

			previousState = documentStates[doc-1];
			stateStateTransitions[previousState][newState]++;
		}
		else {
			// 4. This is the middle of a sequence

			previousState = documentStates[doc-1];
			stateStateTransitions[previousState][newState]++;

			nextState = documentStates[doc+1];
			stateStateTransitions[newState][nextState]++;
			stateTransitionTotals[newState]++;

		}



		if (shouldSaveState) {

			// Update the document-topic count histogram,
			//  for dirichlet estimation
			docLengthCounts[ docLen ]++;
			for (int topic=0; topic < numTopics; topic++) {
				topicDocCounts[topic][ oneDocTopicCounts[topic] ]++;
			}
		}
	}

	public String printStateTransitions() {
		StringBuffer out = new StringBuffer();

		IDSorter[] sortedTopics = new IDSorter[numTopics];

		for (int s = 0; s < numStates; s++) {

			for (int topic=0; topic<numTopics; topic++) {
				sortedTopics[topic] = new IDSorter(topic, (double) stateTopicCounts[s][topic] / stateTopicTotals[s]);
			}
			Arrays.sort(sortedTopics);

			out.append("\n" + s + "\n");

			for (int i=0; i<4; i++) {
				int topic = sortedTopics[i].getID();
				out.append(stateTopicCounts[s][topic] + "\t" + topicKeys[topic] + "\n");
			}

			out.append("\n");

			out.append("[" + initialStateCounts[s] + "/" + numSequences + "] ");

			out.append("[" + stateTransitionTotals[s] + "]");
			for (int t = 0; t < numStates; t++) {
				out.append("\t");
				if (s == t) {
					out.append("[" + stateStateTransitions[s][t] + "]");
				}
				else {
					out.append(stateStateTransitions[s][t]);
				}
			}
			out.append("\n");
		}

		return out.toString();
	}

	public String stateTransitionMatrix() {
		StringBuffer out = new StringBuffer();

		for (int s = 0; s < numStates; s++) {
			for (int t = 0; t < numStates; t++) {
				out.append(stateStateTransitions[s][t]);
				out.append("\t");
			}
			out.append("\n");
		}

		return out.toString();
	}

	public String stateTopics() {
		StringBuffer out = new StringBuffer();

		for (int s = 0; s < numStates; s++) {
			for (int topic=0; topic<numTopics; topic++) {
				out.append(stateTopicCounts[s][topic] + "\t");
			}
			out.append("\n");
		}

		return out.toString();
	}

	public String printTopWords (int numWords, boolean useNewLines, File file) throws IOException {
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
		out.println(printTopWords(numWords, useNewLines));
		out.close();

		return null; 
	}

	public String printTopWords (int numWords, boolean useNewLines) {

		class WordProb implements Comparable {
			int wi; double p;
			public WordProb (int wi, double p) { this.wi = wi; this.p = p; }
			public final int compareTo (Object o2) {
				if (p > ((WordProb)o2).p)
					return -1;
				else if (p == ((WordProb)o2).p)
					return 0;
				else return 1;
			}
		}

		StringBuffer output = new StringBuffer();

		WordProb[] wp = new WordProb[numTypes];
		for (int ti = 0; ti < numTopics; ti++) {
			for (int wi = 0; wi < numTypes; wi++)
				wp[wi] = new WordProb (wi, ((double)typeTopicCounts[wi][ti]) / tokensPerTopic[ti]);
			Arrays.sort (wp);
			if (useNewLines) {
				output.append ("\nTopic " + ti + "\n");
				for (int i = 0; i < numWords; i++)
					output.append (instances.getDataAlphabet().lookupObject(wp[i].wi).toString() + "\t" +
							formatter.format(wp[i].p) + "\n");
			} else {
				output.append (ti+"\t" + formatter.format(alpha[ti]) + "\t");
				for (int i = 0; i < numWords; i++)
					output.append (instances.getDataAlphabet().lookupObject(wp[i].wi).toString() + " ");
				output.append("\n");
			}
		}

		return output.toString();
	}

	public void printDocumentTopics (File f) throws IOException {
		printDocumentTopics (new PrintWriter (new FileWriter (f)));
	}

	public void printDocumentTopics (PrintWriter pw) {

		StringBuffer output = new StringBuffer();
		output.append("#doc name [topic_counts]\n");
		output.append("#");
		for (int topic=0; topic < numTopics; topic++) {
			output.append(alpha[topic] + "\t");
		}
		pw.println(output);

		int docLen;
		int[] topicCount = new int[numTopics];
		for (int doc = 0; doc < topics.length; doc++) {

			output = new StringBuffer();

			for (int token = 0; token < topics[doc].length; token++) {
				topicCount[ topics[doc][token] ]++;
			}

			output.append(doc + "\t" + 
					instances.get(doc).getName() + "\t");
			for (int topic=0; topic < numTopics; topic++) {
				output.append(topicCount[topic] + "\t");
			}

			pw.println(output);

			Arrays.fill(topicCount, 0);
		}
	}

	public void printState (File f) throws IOException {
		printState (new PrintWriter (new FileWriter(f)));
	}

	public void printState (PrintWriter pw) {
		Alphabet a = instances.getDataAlphabet();
		pw.println ("#doc pos typeindex type topic");
		for (int di = 0; di < topics.length; di++) {
			FeatureSequence fs = (FeatureSequence) instances.get(di).getData();
			for (int token = 0; token < topics[di].length; token++) {
				int type = fs.getIndexAtPosition(token);
				pw.print(di); pw.print(' ');
				pw.print(token); pw.print(' ');
				pw.print(type); pw.print(' ');
				pw.print(a.lookupObject(type)); pw.print(' ');
				pw.print(topics[di][token]); pw.println();
			}
		}
		pw.close();
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
		out.writeObject (instances);
		out.writeInt (numTopics);
		out.writeObject (alpha);
		out.writeDouble (beta);
		out.writeDouble (betaSum);
		for (int di = 0; di < topics.length; di ++)
			for (int token = 0; token < topics[di].length; token++)
				out.writeInt (topics[di][token]);
		/*for (int di = 0; di < topics.length; di ++)
	  for (int ti = 0; ti < numTopics; ti++)
	  out.writeInt (docTopicCounts[di][ti]);*/
		for (int fi = 0; fi < numTypes; fi++)
			for (int ti = 0; ti < numTopics; ti++)
				out.writeInt (typeTopicCounts[fi][ti]);
		for (int ti = 0; ti < numTopics; ti++)
			out.writeInt (tokensPerTopic[ti]);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int featuresLength;
		int version = in.readInt ();
		instances = (InstanceList) in.readObject ();
		numTopics = in.readInt();
		alpha = (double[]) in.readObject();
		beta = in.readDouble();
		betaSum = in.readDouble();
		int numDocs = instances.size();
		topics = new int[numDocs][];
		for (int di = 0; di < instances.size(); di++) {
			int docLen = ((FeatureSequence)instances.get(di).getData()).getLength();
			topics[di] = new int[docLen];
			for (int token = 0; token < docLen; token++)
				topics[di][token] = in.readInt();
		}
		/*
	  docTopicCounts = new int[numDocs][numTopics];
	  for (int di = 0; di < instances.size(); di++)
	  for (int ti = 0; ti < numTopics; ti++)
	  docTopicCounts[di][ti] = in.readInt();
		 */
		int numTypes = instances.getDataAlphabet().size();
		typeTopicCounts = new int[numTypes][numTopics];
		for (int fi = 0; fi < numTypes; fi++)
			for (int ti = 0; ti < numTopics; ti++)
				typeTopicCounts[fi][ti] = in.readInt();
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++)
			tokensPerTopic[ti] = in.readInt();
	}


	public double printTopicLabels() {
		int doc, level, label, topic, token, type;
		int[] docTopics;

		int[][] topicLabelCounts = new int[ numTopics ][ instances.getTargetAlphabet().size() ];
		int[] topicCounts = new int[ numTopics ];
		int[] labelCounts = new int[ instances.getTargetAlphabet().size() ];
		int total = 0;

		for (doc=0; doc < instances.size(); doc++) {
			label = instances.get(doc).getLabeling().getBestIndex();
			docTopics = topics[doc];

			for (token = 0; token < docTopics.length; token++) {
				topic = docTopics[token];
				topicLabelCounts[ topic ][ label ]++;
				topicCounts[topic]++;
				labelCounts[label]++;
				total++;
			}
		}

		/*

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
		//   Gamma( sum_i alpha_i )  prod_i Gamma( alpha_i + N_i )
		//  prod_i Gamma( alpha_i )   Gamma( sum_i (alpha_i + N_i) )

		// So the log likelihood is 
		//  logGamma ( sum_i alpha_i ) - logGamma ( sum_i (alpha_i + N_i) ) + 
		//   sum_i [ logGamma( alpha_i + N_i) - logGamma( alpha_i ) ]

		// Do the documents first

		int[] topicCounts = new int[numTopics];
		double[] topicLogGammas = new double[numTopics];
		int[] docTopics;

		for (int topic=0; topic < numTopics; topic++) {
			topicLogGammas[ topic ] = Dirichlet.logGammaStirling(alpha[topic]);
		}

		for (int doc=0; doc < topics.length; doc++) {

			docTopics = topics[doc];

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
		logLikelihood += topics.length * Dirichlet.logGammaStirling(alphaSum);

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

		InstanceList training = //InstanceList.load (new File(args[0]));
			InstanceList.load(new File("/iesl/canvas/mimno/employment/all-instances/resumes.mallet"));

		int numTopics = args.length > 1 ? Integer.parseInt(args[1]) : 500;

		InstanceList testing = args.length > 2 ? InstanceList.load (new File(args[2])) : null;

		MultinomialHMM hmm = new MultinomialHMM (numTopics);

		hmm.setTrainingInstances(training);
		hmm.setGamma(1.0);
		hmm.setNumStates(150);

		hmm.setRandomSeed(1);

		hmm.initialize();
		//hmm.loadStatesFromFile("/iesl/canvas/mimno/mallet/saved.state");
		hmm.loadTopicsFromFile("/iesl/canvas/mimno/employment/all-instances/500.state");
		hmm.loadAlphaFromFile("/iesl/canvas/mimno/employment/all-instances/500.keys");
		hmm.loadSequenceIDsFromFile("/iesl/canvas/mimno/employment/all-instances/resumes.resume_ids");

		hmm.estimate();
	}


	public class IDSorter implements Comparable {
		int id; double p;
		public IDSorter (int id, double p) { this.id = id; this.p = p; }
		public IDSorter (int id, int p) { this.id = id; this.p = p; }
		public final int compareTo (Object o2) {
			if (p > ((IDSorter) o2).p)
				return -1;
			else if (p == ((IDSorter) o2).p)
				return 0;
			else return 1;
		}
		public int getID() {return id;}
		public double getWeight() {return p;}
	}

}
