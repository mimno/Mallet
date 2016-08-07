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
import java.util.List;
import java.util.ArrayList;
import java.util.zip.*;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.*;
import java.text.NumberFormat;


/**
 * Latent Dirichlet Allocation.
 * @author David Mimno, Andrew McCallum
 */

public class MultinomialHMM {

    int numTopics; // Number of topics to be fit
    int numStates; // Number of hidden states
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

    TIntObjectHashMap<TIntIntHashMap> documentTopics;
    int[] documentSequenceIDs;    
    int[] documentStates;

    int[][] stateTopicCounts;
    int[] stateTopicTotals;
    int[][] stateStateTransitions;
    int[] stateTransitionTotals;

    int[] initialStateCounts;

    // Keep track of the most times each topic is
    //  used in any document
    int[] maxTokensPerTopic;

    // The size of the largest document
    int maxDocLength;

    // Rather than calculating log gammas for every state and every topic
    //  we cache log predictive distributions for every possible state
    //  and document.
    double[][][] topicLogGammaCache;
    double[][] docLogGammaCache;

    int numIterations = 1000;
    int burninPeriod = 200;
    int saveSampleInterval = 10;
    int optimizeInterval = 0;
    int showTopicsInterval = 50;

    String[] topicKeys;

    Randoms random;

    NumberFormat formatter;
    
    public MultinomialHMM (int numberOfTopics, String topicsFilename, int numStates) throws IOException {
	formatter = NumberFormat.getInstance();
	formatter.setMaximumFractionDigits(5);
	
	System.out.println("LDA HMM: " + numberOfTopics);
	
	documentTopics = new TIntObjectHashMap<TIntIntHashMap>();

	this.numTopics = numberOfTopics;
	this.alphaSum = numberOfTopics;
	this.alpha = new double[numberOfTopics];
	Arrays.fill(alpha, alphaSum / numTopics);

	topicKeys = new String[numTopics];

	// This initializes numDocs as well
	loadTopicsFromFile(topicsFilename);

	documentStates = new int[ numDocs ];
	documentSequenceIDs = new int[ numDocs ];

	maxTokensPerTopic = new int[ numTopics ];
	maxDocLength = 0;
	
	//int[] histogram = new int[380];
	//int totalTokens = 0;

	for (int doc=0; doc < numDocs; doc++) {
	    if (! documentTopics.containsKey(doc)) { continue; }
	    
	    TIntIntHashMap topicCounts = documentTopics.get(doc);
	    
	    int count = 0;
	    for (int topic: topicCounts.keys()) {
		int topicCount = topicCounts.get(topic);
		//histogram[topicCount]++;
		//totalTokens += topicCount;

		if (topicCount > maxTokensPerTopic[topic]) {
		    maxTokensPerTopic[topic] = topicCount;
		}
		count += topicCount;
	    }
	    if (count > maxDocLength) {
		maxDocLength = count;
	    }
	}

	/*
	double runningTotal = 0.0;
	for (int i=337; i >= 0; i--) {
	    runningTotal += i * histogram[i];
	    System.out.format("%d\t%d\t%.3f\n", i, histogram[i], 
			      runningTotal / totalTokens);
	}
	*/

	this.numStates = numStates; 
	this.initialStateCounts = new int[numStates];

	topicLogGammaCache = new double[numStates][numTopics][];
	for (int state=0; state < numStates; state++) {
	    for (int topic=0; topic < numTopics; topic++) {
		topicLogGammaCache[state][topic] = new double[ maxTokensPerTopic[topic] + 1 ];
		//topicLogGammaCache[state][topic] = new double[21];

	    }
	}
	System.out.println( maxDocLength );
	docLogGammaCache = new double[numStates][ maxDocLength + 1 ];

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

	gammaSum = gamma * numStates;
	
	stateTopicCounts = new int[numStates][numTopics];
	stateTopicTotals = new int[numStates];
	stateStateTransitions = new int[numStates][numStates];
	stateTransitionTotals = new int[numStates];

	pi = 1000.0;
	sumPi = numStates * pi;

	int maxTokens = 0;
	int totalTokens = 0;

	numSequences = 0;

	int sequenceID;
	int currentSequenceID = -1;

	// The code to cache topic distributions 
	//  takes an int-int hashmap as a mask to only update
	//  the distributions for topics that have actually changed.
	// Here we create a dummy count hash that has all the topics.
	TIntIntHashMap allTopicsDummy = new TIntIntHashMap();
	for (int topic = 0; topic < numTopics; topic++) {
	    allTopicsDummy.put(topic, 1);
	}

	for (int state=0; state < numStates; state++) {
	    recacheStateTopicDistribution(state, allTopicsDummy);
	}

	for (int doc = 0; doc < numDocs; doc++) {
	    sampleState(doc, random, true);
	}

    }

    private void recacheStateTopicDistribution(int state, TIntIntHashMap topicCounts) {
	int[] currentStateTopicCounts = stateTopicCounts[state];
	double[][] currentStateCache = topicLogGammaCache[state];
	double[] cache;

	for (int topic: topicCounts.keys()) {
	    cache = currentStateCache[topic];
	    
	    cache[0] = 0.0;
	    for (int i=1; i < cache.length; i++) {
                    cache[i] =
                        cache[ i-1 ] +
                        Math.log( alpha[topic] + i - 1 + 
				  currentStateTopicCounts[topic] );
	    }

	}

	docLogGammaCache[state][0] = 0.0;
	for (int i=1; i < docLogGammaCache[state].length; i++) {
                docLogGammaCache[state][i] =
                    docLogGammaCache[state][ i-1 ] +
                    Math.log( alphaSum + i - 1 + 
			      stateTopicTotals[state] );
	}
    }

    public void sample() throws IOException {

	long startTime = System.currentTimeMillis();
		
	for (int iterations = 1; iterations <= numIterations; iterations++) {
	    long iterationStart = System.currentTimeMillis();

	    //System.out.println (printStateTransitions());
	    for (int doc = 0; doc < numDocs; doc++) {
		sampleState (doc, random, false);
		
		//if (doc % 10000 == 0) { System.out.println (printStateTransitions()); }
	    }

	    System.out.print((System.currentTimeMillis() - iterationStart) + " ");
	    
	    if (iterations % 10 == 0) {
		System.out.println ("<" + iterations + "> ");
		
		PrintWriter out = 
		    new PrintWriter(new BufferedWriter(new FileWriter("state_state_matrix." + iterations)));
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
	BufferedReader in;
	if (stateFilename.endsWith(".gz")) {
	    in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(stateFilename))));
	}
	else {
	    in = new BufferedReader(new FileReader(new File(stateFilename)));
	}

	numDocs = 0;

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

	    if (! documentTopics.containsKey(doc)) {
		documentTopics.put(doc, new TIntIntHashMap());
	    }

	    if (documentTopics.get(doc).containsKey(topic)) {
		documentTopics.get(doc).increment(topic);
	    }
	    else {
		documentTopics.get(doc).put(topic, 1);
	    }

	    if (doc >= numDocs) { numDocs = doc + 1; }
	}
	in.close();

	System.out.println("loaded topics, " + numDocs + " documents");
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

    /*
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
    */
    

    public void loadSequenceIDsFromFile(String sequenceFilename) throws IOException {

	int doc = 0;

	int sequenceID;
	int currentSequenceID = -1;

        BufferedReader in = new BufferedReader(new FileReader(new File(sequenceFilename)));
        String line = null;
        while ((line = in.readLine()) != null) {

	    // We assume that the sequences are in the instance list
	    //  in order.

	    String[] fields = line.split("\\t");

	    sequenceID = Integer.parseInt(fields[0]);

	    documentSequenceIDs[doc] = sequenceID;

	    if (sequenceID != currentSequenceID) {
		numSequences ++;
	    }

	    currentSequenceID = sequenceID;

	    doc++;
	}
	in.close();

	if (doc != numDocs) { System.out.println("Warning: number of documents with topics (" + numDocs + ") is not equal to number of docs with sequence IDs (" + doc + ")"); }

	System.out.println("loaded sequence");
    }

    private void sampleState (int doc, Randoms r, boolean initializing) {

	/*
	if (doc % 10000 == 0) {
	    if (initializing) {
		System.out.println("initializing doc " + doc);
	    }
	    else {
		System.out.println("sampling doc " + doc);
	    }
	}
	*/

	long startTime = System.currentTimeMillis();
	
	// It's possible this document contains no words, 
	//  in which case it has no topics, and no entry in the
	//  documentTopics hash.
	if (! documentTopics.containsKey(doc)) { return; }

        TIntIntHashMap topicCounts = documentTopics.get(doc);

	// if we are in initializing mode, this is meaningless,
	//  but it won't hurt.
	int oldState = documentStates[doc];
	int[] currentStateTopicCounts = stateTopicCounts[oldState];

	// Look at the document features (topics).
	//  If we're not in initializing mode, reduce the topic counts
	//  of the current (old) state.
	
	int docLength = 0;
	
	for (int topic: topicCounts.keys()) {
	    int topicCount = topicCounts.get(topic);
	    if (! initializing) {
		currentStateTopicCounts[topic] -= topicCount;
	    }
	    docLength += topicCount;
	}

	if (! initializing) {
	    stateTopicTotals[oldState] -= docLength;
	    recacheStateTopicDistribution(oldState, topicCounts);
	}


	int previousSequenceID = -1;
	if (doc > 0) {
	    previousSequenceID = documentSequenceIDs[ doc-1 ];
	}

        int sequenceID = documentSequenceIDs[ doc ];

	int nextSequenceID = -1; 
	if (! initializing && 
	    doc < numDocs - 1) { 
	    nextSequenceID = documentSequenceIDs[ doc+1 ];
	}

	double[] stateLogLikelihoods = new double[numStates];
	double[] samplingDistribution = new double[numStates];

	int nextState, previousState;

	if (initializing) {
	    // Initializing the states is the same as sampling them,
	    //  but we only look at the previous state and we don't decrement
	    //  any counts.

	    if (previousSequenceID != sequenceID) {
		// New sequence, start from scratch

		for (int state = 0; state < numStates; state++) {
                    stateLogLikelihoods[state] = Math.log( (initialStateCounts[state] + pi) /
                                                           (numSequences - 1 + sumPi) );
                }
	    }
	    else {
		// Continuation
                previousState = documentStates[ doc-1 ];

                for (int state = 0; state < numStates; state++) {
                    stateLogLikelihoods[state] = Math.log( stateStateTransitions[previousState][state] + gamma );

                    if (Double.isInfinite(stateLogLikelihoods[state])) {
                        System.out.println("infinite end");
                    }
                }
	    }
	}
	else {

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
			System.out.println("infinite middle: " + doc);
			System.out.println(previousState + " -> " + 
					   state + " -> " + nextState);
			System.out.println(stateStateTransitions[previousState][state] + " -> " +
					   stateStateTransitions[state][nextState] + " / " + 
					   stateTransitionTotals[state]);
			
		    }
		}
		
	    }
	}

	double max = Double.NEGATIVE_INFINITY;

	for (int state = 0; state < numStates; state++) {
	    
	    stateLogLikelihoods[state] -= stateTransitionTotals[state] / 10;
	    
	    currentStateTopicCounts = stateTopicCounts[state];
	    double[][] currentStateLogGammaCache = topicLogGammaCache[state];

	    int totalTokens = 0;
	    for (int topic: topicCounts.keys()) {
		int count = topicCounts.get(topic);

		// Cached Sampling Distribution
		stateLogLikelihoods[state] += currentStateLogGammaCache[topic][count];

		
		/*
		  // Hybrid version

		if (count < currentStateLogGammaCache[topic].length) {
		    stateLogLikelihoods[state] += currentStateLogGammaCache[topic][count];
		}
		else {
		    int i = currentStateLogGammaCache[topic].length - 1;

		    stateLogLikelihoods[state] += 
			currentStateLogGammaCache[topic][ i ];

		    for (; i < count; i++) {
			stateLogLikelihoods[state] +=
			    Math.log(alpha[topic] + currentStateTopicCounts[topic] + i);
		    }
		}
		*/

		/*
		for (int j=0; j < count; j++) {
		    stateLogLikelihoods[state] +=
			Math.log( (alpha[topic] + currentStateTopicCounts[topic] + j) /
				  (alphaSum + stateTopicTotals[state] + totalTokens) );

		    if (Double.isNaN(stateLogLikelihoods[state])) {
			System.out.println("NaN: "  + alpha[topic] + " + " +
					   currentStateTopicCounts[topic] + " + " + 
					   j + ") /\n" + 
					   "(" + alphaSum + " + " + 
					   stateTopicTotals[state] + " + " + totalTokens);
		    }
		    
		    totalTokens++;
		}
		*/
	    }
	    
	    // Cached Sampling Distribution
	    stateLogLikelihoods[state] -= docLogGammaCache[state][ docLength ];
		
	    /*
	    // Hybrid version
	    if (docLength < docLogGammaCache[state].length) {
		stateLogLikelihoods[state] -= docLogGammaCache[state][docLength];
	    }
	    else {
		int i = docLogGammaCache[state].length - 1;
		
		stateLogLikelihoods[state] -=
		    docLogGammaCache[state][ i ];
		
		for (; i < docLength; i++) {
		    stateLogLikelihoods[state] -=
			Math.log(alphaSum + stateTopicTotals[state] + i);
		    
		}
	    }
	    */

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
	    stateTopicCounts[newState][topic] += topicCounts.get(topic);
	}
	stateTopicTotals[newState] += docLength;
	recacheStateTopicDistribution(newState, topicCounts);


	if (initializing) {
	    // If we're initializing the states, don't bother
	    //  looking at the next state.
	    
	    if (previousSequenceID != sequenceID) {
		initialStateCounts[newState]++;
	    }
	    else {
		previousState = documentStates[doc-1];
                stateStateTransitions[previousState][newState]++;
		stateTransitionTotals[newState]++;
	    }
	}
	else {
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

    public static void main (String[] args) throws IOException {

	if (args.length != 4) {
	    System.err.println("Usage: MultinomialHMM [num topics] [lda state file] [lda keys file] [sequence metadata file]");
	    System.exit(0);
	}

	int numTopics = Integer.parseInt(args[0]);

	MultinomialHMM hmm =
	    new MultinomialHMM (numTopics, args[1], 150);

	hmm.setGamma(1.0);
	hmm.setRandomSeed(1);

	hmm.loadAlphaFromFile(args[2]);
	hmm.loadSequenceIDsFromFile(args[3]);

	hmm.initialize();

	hmm.sample();
    }
    
}
