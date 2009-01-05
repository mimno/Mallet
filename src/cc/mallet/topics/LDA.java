/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import java.util.Arrays;
import java.io.*;

import cc.mallet.types.*;
import cc.mallet.util.ArrayUtils;
import cc.mallet.util.Randoms;

/**
 * Latent Dirichlet Allocation.
 * @author Andrew McCallum
 * @deprecated Use LDAHyper instead.
 */

// Think about support for incrementally adding more documents...
// (I think this means we might want to use FeatureSequence directly).
// We will also need to support a growing vocabulary!

public class LDA implements Serializable {

	int numTopics; // Number of topics to be fit
	double alpha;  // Dirichlet(alpha,alpha,...) is the distribution over topics
	double beta;   // Prior on per-topic multinomial distribution over words
	double tAlpha;
	double vBeta;
	InstanceList ilist;  // the data field of the instances is expected to hold a FeatureSequence
	int[][] topics; // indexed by <document index, sequence index>
	int numTypes;
	int numTokens;
	int[][] docTopicCounts; // indexed by <document index, topic index>
	int[][] typeTopicCounts; // indexed by <feature index, topic index>
	int[] tokensPerTopic; // indexed by <topic index>

	public LDA (int numberOfTopics)
	{
		this (numberOfTopics, 50.0, 0.01);
	}

	public LDA (int numberOfTopics, double alphaSum, double beta)
	{
		this.numTopics = numberOfTopics;
		this.alpha = alphaSum / numTopics;
		this.beta = beta;
	}

	public void estimate (InstanceList documents, int numIterations, int showTopicsInterval,
                        int outputModelInterval, String outputModelFilename,
                        Randoms r)
	{
		ilist = documents.shallowClone();
		numTypes = ilist.getDataAlphabet().size ();
		int numDocs = ilist.size();
		topics = new int[numDocs][];
		docTopicCounts = new int[numDocs][numTopics];
		typeTopicCounts = new int[numTypes][numTopics];
		tokensPerTopic = new int[numTopics];
		tAlpha = alpha * numTopics;
		vBeta = beta * numTypes;

		long startTime = System.currentTimeMillis();

		// Initialize with random assignments of tokens to topics
		// and finish allocating this.topics and this.tokens
		int topic, seqLen;
    FeatureSequence fs;
    for (int di = 0; di < numDocs; di++) {
      try {
        fs = (FeatureSequence) ilist.get(di).getData();
      } catch (ClassCastException e) {
        System.err.println ("LDA and other topic models expect FeatureSequence data, not FeatureVector data.  "
                            +"With text2vectors, you can obtain such data with --keep-sequence or --keep-bisequence.");
        throw e;
      }
      seqLen = fs.getLength();
			numTokens += seqLen;
			topics[di] = new int[seqLen];
			// Randomly assign tokens to topics
			for (int si = 0; si < seqLen; si++) {
				topic = r.nextInt(numTopics);
				topics[di][si] = topic;
				docTopicCounts[di][topic]++;
				typeTopicCounts[fs.getIndexAtPosition(si)][topic]++;
				tokensPerTopic[topic]++;
			}
		}
    
    this.estimate(0, numDocs, numIterations, showTopicsInterval, outputModelInterval, outputModelFilename, r);
		// 124.5 seconds
		// 144.8 seconds after using FeatureSequence instead of tokens[][] array
		// 121.6 seconds after putting "final" on FeatureSequence.getIndexAtPosition()
		// 106.3 seconds after avoiding array lookup in inner loop with a temporary variable

	}
	
	public void addDocuments(InstanceList additionalDocuments, 
	                         int numIterations, int showTopicsInterval,
	                         int outputModelInterval, String outputModelFilename,
	                         Randoms r)
	{
		if (ilist == null) throw new IllegalStateException ("Must already have some documents first.");
		for (Instance inst : additionalDocuments)
			ilist.add(inst);
		assert (ilist.getDataAlphabet() == additionalDocuments.getDataAlphabet());
		assert (additionalDocuments.getDataAlphabet().size() >= numTypes);
		numTypes = additionalDocuments.getDataAlphabet().size();
		int numNewDocs = additionalDocuments.size();
		int numOldDocs = topics.length;
		int numDocs = numOldDocs+ numNewDocs;
		// Expand various arrays to make space for the new data.
		int[][] newTopics = new int[numDocs][];
		for (int i = 0; i < topics.length; i++) 
			newTopics[i] = topics[i];

		topics = newTopics; // The rest of this array will be initialized below.
		int[][] newDocTopicCounts = new int[numDocs][numTopics];
		for (int i = 0; i < docTopicCounts.length; i++)
			newDocTopicCounts[i] = docTopicCounts[i];
		docTopicCounts = newDocTopicCounts; // The rest of this array will be initialized below.
		int [][] newTypeTopicCounts = new int[numTypes][numTopics];
		for (int i = 0; i < typeTopicCounts.length; i++)
			for (int j = 0; j < numTopics; j++)
				newTypeTopicCounts[i][j] = typeTopicCounts[i][j]; // This array further populated below
		
		FeatureSequence fs;
		for (int di = numOldDocs; di < numDocs; di++) {
      try {
        fs = (FeatureSequence) additionalDocuments.get(di-numOldDocs).getData();
      } catch (ClassCastException e) {
        System.err.println ("LDA and other topic models expect FeatureSequence data, not FeatureVector data.  "
                            +"With text2vectors, you can obtain such data with --keep-sequence or --keep-bisequence.");
        throw e;
      }
      int seqLen = fs.getLength();
			numTokens += seqLen;
			topics[di] = new int[seqLen];
			// Randomly assign tokens to topics
			for (int si = 0; si < seqLen; si++) {
				int topic = r.nextInt(numTopics);
				topics[di][si] = topic;
				docTopicCounts[di][topic]++;
				typeTopicCounts[fs.getIndexAtPosition(si)][topic]++;
				tokensPerTopic[topic]++;
			}
		}
	}
	
	/* Perform several rounds of Gibbs sampling on the documents in the given range. */ 
	public void estimate (int docIndexStart, int docIndexLength,
	                      int numIterations, int showTopicsInterval,
                        int outputModelInterval, String outputModelFilename,
                        Randoms r)
	{
		long startTime = System.currentTimeMillis();
		for (int iterations = 0; iterations < numIterations; iterations++) {
			if (iterations % 10 == 0) System.out.print (iterations);	else System.out.print (".");
			System.out.flush();
			if (showTopicsInterval != 0 && iterations % showTopicsInterval == 0 && iterations > 0) {
				System.out.println ();
				printTopWords (5, false);
			}
      if (outputModelInterval != 0 && iterations % outputModelInterval == 0 && iterations > 0) {
        this.write (new File(outputModelFilename+'.'+iterations));
      }
      sampleTopicsForDocs(docIndexStart, docIndexLength, r);
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

	/* One iteration of Gibbs sampling, across all documents. */
	public void sampleTopicsForAllDocs (Randoms r)
	{
		double[] topicWeights = new double[numTopics];
		// Loop over every word in the corpus
		for (int di = 0; di < topics.length; di++) {
			sampleTopicsForOneDoc ((FeatureSequence)ilist.get(di).getData(),
			                       topics[di], docTopicCounts[di], topicWeights, r);
		}
	}

	/* One iteration of Gibbs sampling, across all documents. */
	public void sampleTopicsForDocs (int start, int length, Randoms r)
	{
		assert (start+length <= docTopicCounts.length);
		double[] topicWeights = new double[numTopics];
		// Loop over every word in the corpus
		for (int di = start; di < start+length; di++) {
			sampleTopicsForOneDoc ((FeatureSequence)ilist.get(di).getData(),
			                       topics[di], docTopicCounts[di], topicWeights, r);
		}
	}

	/*
	public double[] assignTopics (int[] testTokens, Random r)
	{
		int[] testTopics = new int[testTokens.length];
		int[] testTopicCounts = new int[numTopics];
		int numTokens = MatrixOps.sum(testTokens);
		double[] topicWeights = new double[numTopics];
		// Randomly assign topics to the words and
		// incorporate this document in the global counts
		int topic;
		for (int si = 0; si < testTokens.length; si++) {
			topic = r.nextInt (numTopics);
			testTopics[si] = topic; // analogous to this.topics
			testTopicCounts[topic]++; // analogous to this.docTopicCounts
			typeTopicCounts[testTokens[si]][topic]++;
			tokensPerTopic[topic]++;
		}
		// Repeatedly sample topic assignments for the words in this document
		for (int iterations = 0; iterations < numTokens*2; iterations++)
			sampleTopicsForOneDoc (testTokens, testTopics, testTopicCounts, topicWeights, r);
		// Remove this document from the global counts
		// and also fill topicWeights with an unnormalized distribution over topics for whole doc
		Arrays.fill (topicWeights, 0.0);
		for (int si = 0; si < testTokens.length; si++) {
			topic = testTopics[si];
			typeTopicCounts[testTokens[si]][topic]--;
			tokensPerTopic[topic]--;
			topicWeights[topic]++;
		}
		// Normalize the distribution over topics for whole doc
		for (int ti = 0; ti < numTopics; ti++)
			topicWeights[ti] /= testTokens.length;
		return topicWeights;
	}
*/

  private void sampleTopicsForOneDoc (FeatureSequence oneDocTokens, int[] oneDocTopics, // indexed by seq position
	                                    int[] oneDocTopicCounts, // indexed by topic index
	                                    double[] topicWeights, Randoms r)
	{
		int[] currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double topicWeightsSum;
		int docLen = oneDocTokens.getLength();
		double tw;
		// Iterate over the positions (words) in the document
		for (int si = 0; si < docLen; si++) {
			type = oneDocTokens.getIndexAtPosition(si);
			oldTopic = oneDocTopics[si];
			// Remove this token from all counts
			oneDocTopicCounts[oldTopic]--;
			typeTopicCounts[type][oldTopic]--;
			tokensPerTopic[oldTopic]--;
			// Build a distribution over topics for this token
			Arrays.fill (topicWeights, 0.0);
			topicWeightsSum = 0;
			currentTypeTopicCounts = typeTopicCounts[type];
			for (int ti = 0; ti < numTopics; ti++) {
				tw = ((currentTypeTopicCounts[ti] + beta) / (tokensPerTopic[ti] + vBeta))
				      * ((oneDocTopicCounts[ti] + alpha)); // (/docLen-1+tAlpha); is constant across all topics
				topicWeightsSum += tw;
				topicWeights[ti] = tw;
			}
			// Sample a topic assignment from this distribution
			newTopic = r.nextDiscrete (topicWeights, topicWeightsSum);

			// Put that new topic into the counts
			oneDocTopics[si] = newTopic;
			oneDocTopicCounts[newTopic]++;
			typeTopicCounts[type][newTopic]++;
			tokensPerTopic[newTopic]++;
		}
	}
	
	public int[][] getDocTopicCounts(){
		return docTopicCounts;
	}
	
	public int[][] getTypeTopicCounts(){
		return typeTopicCounts;
	}

	public int[] getTokensPerTopic(){
		return tokensPerTopic;
	}

	public void printTopWords (int numWords, boolean useNewLines)
	{
		class WordProb implements Comparable {
			int wi;
			double p;
			public WordProb (int wi, double p) { this.wi = wi; this.p = p; }
			public final int compareTo (Object o2) {
				if (p > ((WordProb)o2).p)
					return -1;
				else if (p == ((WordProb)o2).p)
					return 0;
				else return 1;
			}
		}

		WordProb[] wp = new WordProb[numTypes];
		for (int ti = 0; ti < numTopics; ti++) {
			for (int wi = 0; wi < numTypes; wi++)
				wp[wi] = new WordProb (wi, ((double)typeTopicCounts[wi][ti]) / tokensPerTopic[ti]);
			Arrays.sort (wp);
			if (useNewLines) {
				System.out.println ("\nTopic "+ti);
				for (int i = 0; i < numWords; i++)
					System.out.println (ilist.getDataAlphabet().lookupObject(wp[i].wi).toString() + " " + wp[i].p);
			} else {
				System.out.print ("Topic "+ti+": ");
				for (int i = 0; i < numWords; i++)
					System.out.print (ilist.getDataAlphabet().lookupObject(wp[i].wi).toString() + " ");
				System.out.println();
			}
		}
	}

  public void printDocumentTopics (File f) throws IOException
  {
    printDocumentTopics (new PrintWriter (new FileWriter (f)));
  }

  public void printDocumentTopics (PrintWriter pw) {
    printDocumentTopics (pw, 0.0, -1);
  }

  public void printDocumentTopics (PrintWriter pw, double threshold, int max)
  {
    pw.println ("#doc source topic proportion ...");
    int docLen;
    double topicDist[] = new double[topics.length];
    for (int di = 0; di < topics.length; di++) {
      pw.print (di); pw.print (' ');
			if (ilist.get(di).getSource() != null){
				pw.print (ilist.get(di).getSource().toString()); 
			}
			else {
				pw.print("null-source");
			}
			pw.print (' ');
      docLen = topics[di].length;
      for (int ti = 0; ti < numTopics; ti++)
        topicDist[ti] = (((float)docTopicCounts[di][ti])/docLen);
      if (max < 0) max = numTopics;
      for (int tp = 0; tp < max; tp++) {
        double maxvalue = 0;
        int maxindex = -1;
        for (int ti = 0; ti < numTopics; ti++)
          if (topicDist[ti] > maxvalue) {
            maxvalue = topicDist[ti];
            maxindex = ti;
          }
        if (maxindex == -1 || topicDist[maxindex] < threshold)
          break;
        pw.print (maxindex+" "+topicDist[maxindex]+" ");
        topicDist[maxindex] = 0;
      }
      pw.println (' ');
    }
  }



  public void printState (File f) throws IOException
  {
	  PrintWriter writer = new PrintWriter (new FileWriter(f));
	  printState (writer);
	  writer.close();
  }


  public void printState (PrintWriter pw)
  {
	  Alphabet a = ilist.getDataAlphabet();
	  pw.println ("#doc pos typeindex type topic");
	  for (int di = 0; di < topics.length; di++) {
		  FeatureSequence fs = (FeatureSequence) ilist.get(di).getData();
		  for (int si = 0; si < topics[di].length; si++) {
			  int type = fs.getIndexAtPosition(si);
			  pw.print(di); pw.print(' ');
			  pw.print(si); pw.print(' ');
			  pw.print(type); pw.print(' ');
			  pw.print(a.lookupObject(type)); pw.print(' ');
			  pw.print(topics[di][si]); pw.println();
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
		out.writeObject (ilist);
		out.writeInt (numTopics);
		out.writeDouble (alpha);
		out.writeDouble (beta);
		out.writeDouble (tAlpha);
		out.writeDouble (vBeta);
		for (int di = 0; di < topics.length; di ++)
			for (int si = 0; si < topics[di].length; si++)
				out.writeInt (topics[di][si]);
		for (int di = 0; di < topics.length; di ++)
			for (int ti = 0; ti < numTopics; ti++)
				out.writeInt (docTopicCounts[di][ti]);
		for (int fi = 0; fi < numTypes; fi++)
			for (int ti = 0; ti < numTopics; ti++)
				out.writeInt (typeTopicCounts[fi][ti]);
		for (int ti = 0; ti < numTopics; ti++)
			out.writeInt (tokensPerTopic[ti]);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int featuresLength;
		int version = in.readInt ();
		ilist = (InstanceList) in.readObject ();
		numTopics = in.readInt();
		alpha = in.readDouble();
		beta = in.readDouble();
		tAlpha = in.readDouble();
		vBeta = in.readDouble();
		int numDocs = ilist.size();
		topics = new int[numDocs][];
		for (int di = 0; di < ilist.size(); di++) {
			int docLen = ((FeatureSequence)ilist.get(di).getData()).getLength();
			topics[di] = new int[docLen];
			for (int si = 0; si < docLen; si++)
				topics[di][si] = in.readInt();
		}
		docTopicCounts = new int[numDocs][numTopics];
		for (int di = 0; di < ilist.size(); di++)
			for (int ti = 0; ti < numTopics; ti++)
				docTopicCounts[di][ti] = in.readInt();
		int numTypes = ilist.getDataAlphabet().size();
		typeTopicCounts = new int[numTypes][numTopics];
		for (int fi = 0; fi < numTypes; fi++)
			for (int ti = 0; ti < numTopics; ti++)
				typeTopicCounts[fi][ti] = in.readInt();
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++)
			tokensPerTopic[ti] = in.readInt();
	}

	public InstanceList getInstanceList ()
	{
		return ilist;
	}

	// Recommended to use mallet/bin/vectors2topics instead.
	public static void main (String[] args) throws IOException
	{
		InstanceList ilist = InstanceList.load (new File(args[0]));
		int numIterations = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
		int numTopWords = args.length > 2 ? Integer.parseInt(args[2]) : 20;
		System.out.println ("Data loaded.");
		LDA lda = new LDA (10);
		lda.estimate (ilist, numIterations, 50, 0, null, new Randoms());  // should be 1100
		lda.printTopWords (numTopWords, true);
		lda.printDocumentTopics (new File(args[0]+".lda"));
	}

}
