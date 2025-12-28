/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;


import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;

import cc.mallet.types.Alphabet;
import cc.mallet.types.AugmentableFeatureVector;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureSequenceWithBigrams;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * Like Latent Dirichlet Allocation, but with integrated phrase discovery.
 * @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 * based on C code by Xuerui Wang.
 */

public class TopicalNGrams {

	int numTopics;
	Alphabet uniAlphabet;
	Alphabet biAlphabet;
	double alpha, beta, gamma, delta, tAlpha, vBeta, vGamma, delta1, delta2;
	InstanceList ilist; // containing FeatureSequenceWithBigrams in the data field of each instance
	int[][] topics; // {0...T-1}, the topic index, indexed by <document index, sequence index>
	int[][] grams; // {0,1}, the bigram status, indexed by <document index, sequence index> TODO: Make this boolean?
	int numTypes; // number of unique unigrams
	int numBitypes; // number of unique bigrams
	int numTokens; // total number of word occurrences
	// "totalNgram"
	int biTokens; // total number of tokens currently generated as bigrams (only used for progress messages)
	// "docTopic"
	int[][] docTopicCounts; // indexed by <document index, topic index>
	// Used to calculate p(x|w,t).  "ngramCount"
	int[][][] typeNgramTopicCounts; // indexed by <feature index, ngram status, topic index>
	// Used to calculate p(w|t) and p(w|t,w), "topicWord" and "topicNgramWord"
	int[][] unitypeTopicCounts; // indexed by <feature index, topic index>
	int[][] bitypeTopicCounts; // index by <bifeature index, topic index>
	// "sumWords"
	int[] tokensPerTopic; // indexed by <topic index>
	// "sumNgramWords"
	int[][] bitokensPerTopic; // indexed by <feature index, topic index>, where the later is the conditioned word

	public TopicalNGrams (int numberOfTopics)
	{
		this (numberOfTopics, 50.0, 0.01, 0.01, 0.03, 0.2, 1000);
	}

	public TopicalNGrams (int numberOfTopics, double alphaSum, double beta, double gamma, double delta,
			      double delta1, double delta2)
	{
		this.numTopics = numberOfTopics;
		this.alpha     = alphaSum / numTopics; // smoothing over the choice of topic
		this.beta      = beta;                  // smoothing over the choice of unigram words
		this.gamma     = gamma;                // smoothing over the choice of bigram words
		this.delta     = delta;                // smoothing over the choice of unigram/bigram generation
		this.delta1    = delta1;   // TODO: Clean this up.
		this.delta2    = delta2;
		System.out.println("alpha :"+alphaSum);
		System.out.println("beta :"+beta);
		System.out.println("gamma :"+gamma);
		System.out.println("delta :"+delta);
		System.out.println("delta1 :"+delta1);
		System.out.println("delta2 :"+delta2);
	}

	public void estimate (InstanceList documents, int numIterations, int showTopicsInterval,
                        int outputModelInterval, String outputModelFilename,
                        Randoms r)
	{
		ilist = documents;
		uniAlphabet = ilist.getDataAlphabet();
		biAlphabet = ((FeatureSequenceWithBigrams)ilist.get(0).getData()).getBiAlphabet();
		numTypes = uniAlphabet.size();
		numBitypes = biAlphabet.size();
		int numDocs = ilist.size();
		topics = new int[numDocs][];
		grams = new int[numDocs][];
		docTopicCounts = new int[numDocs][numTopics];
		typeNgramTopicCounts = new int[numTypes][2][numTopics];
		unitypeTopicCounts = new int[numTypes][numTopics];
		bitypeTopicCounts = new int[numBitypes][numTopics];
		tokensPerTopic = new int[numTopics];
		bitokensPerTopic = new int[numTypes][numTopics];
		tAlpha = alpha * numTopics;
		vBeta = beta * numTypes;
		vGamma = gamma * numTypes;

		long startTime = System.currentTimeMillis();

		// Initialize with random assignments of tokens to topics
		// and finish allocating this.topics and this.tokens
		int topic, gram, seqLen, fi;
		for (int di = 0; di < numDocs; di++) {
			FeatureSequenceWithBigrams fs = (FeatureSequenceWithBigrams) ilist.get(di).getData();
			seqLen = fs.getLength();
			numTokens += seqLen;
			topics[di] = new int[seqLen];
			grams[di] = new int[seqLen];
			// Randomly assign tokens to topics
			int prevFi = -1, prevTopic = -1;
			for (int si = 0; si < seqLen; si++) {
				// randomly sample a topic for the word at position si
				topic = r.nextInt(numTopics);
				// if a bigram is allowed at position si, then sample a gram status for it.
				gram = (fs.getBiIndexAtPosition(si) == -1 ? 0 : r.nextInt(2));
				if (gram != 0) biTokens++;
				topics[di][si] = topic;
				grams[di][si] = gram;
				docTopicCounts[di][topic]++;
				fi = fs.getIndexAtPosition(si);
				if (prevFi != -1)
					typeNgramTopicCounts[prevFi][gram][prevTopic]++;
				if (gram == 0) {
					unitypeTopicCounts[fi][topic]++;
					tokensPerTopic[topic]++;
				}	else {
					bitypeTopicCounts[fs.getBiIndexAtPosition(si)][topic]++;
					bitokensPerTopic[prevFi][topic]++;
				}
				prevFi = fi;  prevTopic = topic;
			}
		}

    for (int iterations = 0; iterations < numIterations; iterations++) {
      sampleTopicsForAllDocs (r);
      if (iterations % 10 == 0) System.out.print (iterations);	else System.out.print (".");
			System.out.flush();
			if (showTopicsInterval != 0 && iterations % showTopicsInterval == 0 && iterations > 0) {
				System.out.println ();
				printTopWords (5, false);
			}
      if (outputModelInterval != 0 && iterations % outputModelInterval == 0 && iterations > 0) {
        this.write (new File(outputModelFilename+'.'+iterations));
      }
		}

		System.out.println ("\nTotal time (sec): " + ((System.currentTimeMillis() - startTime)/1000.0));
	}

	/* One iteration of Gibbs sampling, across all documents. */
	private void sampleTopicsForAllDocs (Randoms r)
	{
		double[] uniTopicWeights = new double[numTopics];
		double[] biTopicWeights = new double[numTopics*2];
		// Loop over every word in the corpus
		for (int di = 0; di < topics.length; di++) {
			sampleTopicsForOneDoc ((FeatureSequenceWithBigrams)ilist.get(di).getData(),
			                       topics[di], grams[di], docTopicCounts[di],
			                       uniTopicWeights, biTopicWeights,
			                       r);
		}
	}


	private void sampleTopicsForOneDoc (FeatureSequenceWithBigrams oneDocTokens,
	                                    int[] oneDocTopics, int[] oneDocGrams,
		                                  int[] oneDocTopicCounts, // indexed by topic index
		                                  double[] uniTopicWeights, // length==numTopics
	                                    double[] biTopicWeights, // length==numTopics*2: joint topic/gram sampling
	                                    Randoms r)
	{
		int[] currentTypeTopicCounts;
		int[] currentBitypeTopicCounts;
		int[] previousBitokensPerTopic;
		int type, bitype, oldGram, nextGram, newGram, oldTopic, newTopic;
		double topicWeightsSum, tw;
		// xxx int docLen = oneDocTokens.length;
		int docLen = oneDocTokens.getLength();
		// Iterate over the positions (words) in the document
		for (int si = 0; si < docLen; si++) {
			type = oneDocTokens.getIndexAtPosition(si);
			bitype = oneDocTokens.getBiIndexAtPosition(si);
			//if (bitype == -1) System.out.println ("biblock "+si+" at "+uniAlphabet.lookupObject(type));
			oldTopic = oneDocTopics[si];
			oldGram = oneDocGrams[si];
			nextGram = (si == docLen-1) ? -1 : oneDocGrams[si+1];
			//nextGram = (si == docLen-1) ? -1 : (oneDocTokens.getBiIndexAtPosition(si+1) == -1 ? 0 : 1);
			boolean bigramPossible = (bitype != -1);
			assert (!(!bigramPossible && oldGram == 1));
			if (!bigramPossible) {
				// Remove this token from all counts
				oneDocTopicCounts[oldTopic]--;
				tokensPerTopic[oldTopic]--;
				unitypeTopicCounts[type][oldTopic]--;
				if (si != docLen-1) {
					typeNgramTopicCounts[type][nextGram][oldTopic]--;
					assert (typeNgramTopicCounts[type][nextGram][oldTopic] >= 0);
				}
				assert (oneDocTopicCounts[oldTopic] >= 0);
				assert (tokensPerTopic[oldTopic] >= 0);
				assert (unitypeTopicCounts[type][oldTopic] >= 0);
				// Build a distribution over topics for this token
				Arrays.fill (uniTopicWeights, 0.0);
				topicWeightsSum = 0;
				currentTypeTopicCounts = unitypeTopicCounts[type];
				for (int ti = 0; ti < numTopics; ti++) {
					tw = ((currentTypeTopicCounts[ti] + beta) / (tokensPerTopic[ti] + vBeta))
						    * ((oneDocTopicCounts[ti] + alpha)); // additional term is constance across all topics
					topicWeightsSum += tw;
					uniTopicWeights[ti] = tw;
				}
				// Sample a topic assignment from this distribution
				newTopic = r.nextDiscrete (uniTopicWeights, topicWeightsSum);
				// Put that new topic into the counts
				oneDocTopics[si] = newTopic;
				oneDocTopicCounts[newTopic]++;
				unitypeTopicCounts[type][newTopic]++;
				tokensPerTopic[newTopic]++;
				if (si != docLen-1)
					typeNgramTopicCounts[type][nextGram][newTopic]++;
			} else {
				// Bigram is possible
				int prevType = oneDocTokens.getIndexAtPosition(si-1);
				int prevTopic = oneDocTopics[si-1];
				// Remove this token from all counts
				oneDocTopicCounts[oldTopic]--;
				typeNgramTopicCounts[prevType][oldGram][prevTopic]--;
				if (si != docLen-1)
					typeNgramTopicCounts[type][nextGram][oldTopic]--;
				if (oldGram == 0) {
					unitypeTopicCounts[type][oldTopic]--;
					tokensPerTopic[oldTopic]--;
				} else {
					bitypeTopicCounts[bitype][oldTopic]--;
					bitokensPerTopic[prevType][oldTopic]--;
					biTokens--;
				}
				assert (oneDocTopicCounts[oldTopic] >= 0);
				assert (typeNgramTopicCounts[prevType][oldGram][prevTopic] >= 0);
				assert (si == docLen-1 || typeNgramTopicCounts[type][nextGram][oldTopic] >= 0);
				assert (unitypeTopicCounts[type][oldTopic] >= 0);
				assert (tokensPerTopic[oldTopic] >= 0);
				assert (bitypeTopicCounts[bitype][oldTopic] >= 0);
				assert (bitokensPerTopic[prevType][oldTopic] >= 0);
				assert (biTokens >= 0);
				// Build a joint distribution over topics and ngram-status for this token
				Arrays.fill (biTopicWeights, 0.0);
				topicWeightsSum = 0;
				currentTypeTopicCounts = unitypeTopicCounts[type];
				currentBitypeTopicCounts = bitypeTopicCounts[bitype];
				previousBitokensPerTopic = bitokensPerTopic[prevType];
				for (int ti = 0; ti < numTopics; ti++) {
					newTopic = ti << 1; // just using this variable as an index into [ti*2+gram]
					// The unigram outcome
					tw =
					    (currentTypeTopicCounts[ti] + beta) / (tokensPerTopic[ti] + vBeta)
					  * (oneDocTopicCounts[ti] + alpha)
						* (typeNgramTopicCounts[prevType][0][prevTopic] + delta1);
					topicWeightsSum += tw;
					biTopicWeights[newTopic] = tw;
					// The bigram outcome
					newTopic++;
					tw =
					    (currentBitypeTopicCounts[ti] + gamma) / (previousBitokensPerTopic[ti] + vGamma)
					    * (oneDocTopicCounts[ti] + alpha)
					    * (typeNgramTopicCounts[prevType][1][prevTopic] + delta2);
					topicWeightsSum += tw;
					biTopicWeights[newTopic] = tw;
				}
				// Sample a topic assignment from this distribution
				newTopic = r.nextDiscrete (biTopicWeights, topicWeightsSum);
				// Put that new topic into the counts
				newGram = newTopic % 2;
				newTopic /= 2;
				// Put that new topic into the counts
				oneDocTopics[si] = newTopic;
				oneDocGrams[si] = newGram;
				oneDocTopicCounts[newTopic]++;
				typeNgramTopicCounts[prevType][newGram][prevTopic]++;
				if (si != docLen-1)
					typeNgramTopicCounts[type][nextGram][newTopic]++;
				if (newGram == 0) {
					unitypeTopicCounts[type][newTopic]++;
					tokensPerTopic[newTopic]++;
				} else {
					bitypeTopicCounts[bitype][newTopic]++;
					bitokensPerTopic[prevType][newTopic]++;
					biTokens++;
				}
			}
		}
	}

	public void printTopWords (int numWords, boolean useNewLines)
	{
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

		for (int ti = 0; ti < numTopics; ti++) {
			// Unigrams
			WordProb[] wp = new WordProb[numTypes];
			for (int wi = 0; wi < numTypes; wi++)
				wp[wi] = new WordProb (wi, (double)unitypeTopicCounts[wi][ti]);
			Arrays.sort (wp);
			int numToPrint = Math.min(wp.length, numWords);
			if (useNewLines) {
				System.out.println ("\nTopic "+ti+" unigrams");
				for (int i = 0; i < numToPrint; i++)
					System.out.println (uniAlphabet.lookupObject(wp[i].wi).toString()
					                    + " " + wp[i].p/tokensPerTopic[ti]);
			} else {
				System.out.print ("Topic "+ti+": ");
				for (int i = 0; i < numToPrint; i++)
					System.out.print (uniAlphabet.lookupObject(wp[i].wi).toString() + " ");
			}

			// Bigrams
			/*
			wp = new WordProb[numBitypes];
			int bisum = 0;
			for (int wi = 0; wi < numBitypes; wi++) {
				wp[wi] = new WordProb (wi, ((double)bitypeTopicCounts[wi][ti]));
				bisum += bitypeTopicCounts[wi][ti];
			}
			Arrays.sort (wp);
			numToPrint = Math.min(wp.length, numWords);
			if (useNewLines) {
				System.out.println ("\nTopic "+ti+" bigrams");
				for (int i = 0; i < numToPrint; i++)
					System.out.println (biAlphabet.lookupObject(wp[i].wi).toString() + " " + wp[i].p/bisum);
			} else {
				System.out.print ("          ");
				for (int i = 0; i < numToPrint; i++)
					System.out.print (biAlphabet.lookupObject(wp[i].wi).toString() + " ");
				System.out.println();
			}
			*/

			// Ngrams
			AugmentableFeatureVector afv = new AugmentableFeatureVector(new Alphabet(), 10000, false);
			for (int di = 0; di < topics.length; di++) {
				FeatureSequenceWithBigrams fs = (FeatureSequenceWithBigrams) ilist.get(di).getData();
				for (int si = topics[di].length-1; si >= 0; si--) {
					if (topics[di][si] == ti && grams[di][si] == 1) {
						String gramString = uniAlphabet.lookupObject(fs.getIndexAtPosition(si)).toString();
						while (grams[di][si] == 1 && --si >= 0)
							gramString = uniAlphabet.lookupObject(fs.getIndexAtPosition(si)).toString() + "_" + gramString;
						afv.add(gramString, 1.0);
					}
				}
			}
			//System.out.println ("pre-sorting");
			int numNgrams = afv.numLocations();
			//System.out.println ("post-sorting "+numNgrams);
			wp = new WordProb[numNgrams];
			int ngramSum = 0;
			for (int loc = 0; loc < numNgrams; loc++) {
				wp[loc] = new WordProb (afv.indexAtLocation(loc), afv.valueAtLocation(loc));
				ngramSum += wp[loc].p;
			}
			Arrays.sort (wp);
			int numUnitypeTokens = 0, numBitypeTokens = 0, numUnitypeTypes = 0, numBitypeTypes = 0;
			for (int fi = 0; fi < numTypes; fi++) {
				numUnitypeTokens += unitypeTopicCounts[fi][ti];
				if (unitypeTopicCounts[fi][ti] != 0)
					numUnitypeTypes++;
			}
			for (int fi = 0; fi < numBitypes; fi++) {
				numBitypeTokens += bitypeTopicCounts[fi][ti];
				if (bitypeTopicCounts[fi][ti] != 0)
					numBitypeTypes++;
			}

			if (useNewLines) {
				System.out.println ("\nTopic "+ti+" unigrams "+numUnitypeTokens+"/"+numUnitypeTypes+" bigrams "+numBitypeTokens+"/"+numBitypeTypes
				                    +" phrases "+Math.round(afv.oneNorm())+"/"+numNgrams);
				for (int i = 0; i < Math.min(numNgrams,numWords); i++)
					System.out.println (afv.getAlphabet().lookupObject(wp[i].wi).toString() + " " + wp[i].p/ngramSum);
			} else {
				System.out.print (" (unigrams "+numUnitypeTokens+"/"+numUnitypeTypes+" bigrams "+numBitypeTokens+"/"+numBitypeTypes
				                  +" phrases "+Math.round(afv.oneNorm())+"/"+numNgrams+")\n         ");
				//System.out.print (" (unique-ngrams="+numNgrams+" ngram-count="+Math.round(afv.oneNorm())+")\n         ");
				for (int i = 0; i < Math.min(numNgrams, numWords); i++)
					System.out.print (afv.getAlphabet().lookupObject(wp[i].wi).toString() + " ");
				System.out.println();
			}

		}

	}


  public void printDocumentTopics (File f) throws IOException
  {
    printDocumentTopics (new PrintWriter (new FileWriter (f)));
  }

  public void printDocumentTopics (PrintWriter pw) {
  }

  public void printDocumentTopics (PrintWriter pw, double threshold, int max)
  {
    pw.println ("#doc source topic proportions");
    int docLen;
    double topicDist[] = new double[numTopics];
    for (int di = 0; di < topics.length; di++) {
      pw.print (di); pw.print (' ');
      pw.print (ilist.get(di).getSource().toString()); pw.print (' ');
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
		pw.println ("#doc pos typeindex type bigrampossible? topic bigram");
		for (int di = 0; di < topics.length; di++) {
			FeatureSequenceWithBigrams fs = (FeatureSequenceWithBigrams) ilist.get(di).getData();
			for (int si = 0; si < topics[di].length; si++) {
				int type = fs.getIndexAtPosition(si);
				pw.print(di); pw.print(' ');
				pw.print(si); pw.print(' ');
				pw.print(type); pw.print(' ');
				pw.print(uniAlphabet.lookupObject(type)); pw.print(' ');
				pw.print(fs.getBiIndexAtPosition(si)==-1 ? 0 : 1); pw.print(' ');
				pw.print(topics[di][si]); pw.print(' ');
				pw.print(grams[di][si]); pw.println();
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

	private void writeIntArray2 (int[][] a, ObjectOutputStream out) throws IOException {
		out.writeInt (a.length);
		int d2 = a[0].length;
		out.writeInt (d2);
		for (int i = 0; i < a.length; i++)
			for (int j = 0; j < d2; j++)
				out.writeInt (a[i][j]);
	}

	private int[][] readIntArray2 (ObjectInputStream in) throws IOException {
		int d1 = in.readInt();
		int d2 = in.readInt();
		int[][] a = new int[d1][d2];
		for (int i = 0; i < d1; i++)
			for (int j = 0; j < d2; j++)
				a[i][j] = in.readInt();
		return a;
	}

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (ilist);
		out.writeInt (numTopics);
		out.writeDouble (alpha);
		out.writeDouble (beta);
		out.writeDouble (gamma);
		out.writeDouble (delta);
		out.writeDouble (tAlpha);
		out.writeDouble (vBeta);
		out.writeDouble (vGamma);
		out.writeInt (numTypes);
		out.writeInt (numBitypes);
		out.writeInt (numTokens);
		out.writeInt (biTokens);
		for (int di = 0; di < topics.length; di ++)
			for (int si = 0; si < topics[di].length; si++)
				out.writeInt (topics[di][si]);
		for (int di = 0; di < topics.length; di ++)
			for (int si = 0; si < topics[di].length; si++)
				out.writeInt (grams[di][si]);
		writeIntArray2 (docTopicCounts, out);
		for (int fi = 0; fi < numTypes; fi++)
			for (int n = 0; n < 2; n++)
				for (int ti = 0; ti < numTopics; ti++)
					out.writeInt (typeNgramTopicCounts[fi][n][ti]);
		writeIntArray2 (unitypeTopicCounts, out);
		writeIntArray2 (bitypeTopicCounts, out);
		for (int ti = 0; ti < numTopics; ti++)
			out.writeInt (tokensPerTopic[ti]);
		writeIntArray2 (bitokensPerTopic, out);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int featuresLength;
		int version = in.readInt ();
		ilist = (InstanceList) in.readObject ();
		numTopics = in.readInt();
		alpha = in.readDouble();
		beta = in.readDouble();
		gamma = in.readDouble();
		delta = in.readDouble();
		tAlpha = in.readDouble();
		vBeta = in.readDouble();
		vGamma = in.readDouble();
		numTypes = in.readInt();
		numBitypes = in.readInt();
		numTokens = in.readInt();
		biTokens = in.readInt();
		int numDocs = ilist.size();
		topics = new int[numDocs][];
		grams = new int[numDocs][];
		for (int di = 0; di < ilist.size(); di++) {
			int docLen = ((FeatureSequence)ilist.get(di).getData()).getLength();
			topics[di] = new int[docLen];
			for (int si = 0; si < docLen; si++)
				topics[di][si] = in.readInt();
		}
		for (int di = 0; di < ilist.size(); di++) {
			int docLen = ((FeatureSequence)ilist.get(di).getData()).getLength();
			grams[di] = new int[docLen];
			for (int si = 0; si < docLen; si++)
				grams[di][si] = in.readInt();
		}
		docTopicCounts = readIntArray2 (in);
		typeNgramTopicCounts = new int[numTypes][2][numTopics];
		for (int fi = 0; fi < numTypes; fi++)
			for (int n = 0; n < 2; n++)
				for (int ti = 0; ti < numTopics; ti++)
					typeNgramTopicCounts[fi][n][ti] = in.readInt();
		unitypeTopicCounts = readIntArray2 (in);
		bitypeTopicCounts = readIntArray2 (in);
		tokensPerTopic = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++)
			tokensPerTopic[ti] = in.readInt();
		bitokensPerTopic = readIntArray2 (in);
	}


  // Just for testing.  Recommend instead is mallet/bin/vectors2topics
  public static void main (String[] args)
	{
		InstanceList ilist = InstanceList.load (new File(args[0]));
		int numIterations = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
		int numTopWords = args.length > 2 ? Integer.parseInt(args[2]) : 20;
		System.out.println ("Data loaded.");
		TopicalNGrams tng = new TopicalNGrams (10);
		tng.estimate (ilist, 200, 1, 0, null, new Randoms());
		tng.printTopWords (60, true);
	}

}
