package cc.mallet.topics;


/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */

import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;

/**
 * Four Level Pachinko Allocation with MLE learning, 
 *  based on Andrew's Latent Dirichlet Allocation.
 * @author David Mimno
 */

public class PAM4L {

	// Parameters
	int numSuperTopics; // Number of topics to be fit
	int numSubTopics;

	double[] alpha;  // Dirichlet(alpha,alpha,...) is the distribution over supertopics
	double alphaSum;
	double[][] subAlphas;
	double[] subAlphaSums;
	double beta;   // Prior on per-topic multinomial distribution over words
	double vBeta;

	// Data
	InstanceList ilist;  // the data field of the instances is expected to hold a FeatureSequence
	int numTypes;
	int numTokens;

	// Gibbs sampling state
	//  (these could be shorts, or we could encode both in one int)
	int[][] superTopics; // indexed by <document index, sequence index>
	int[][] subTopics; // indexed by <document index, sequence index>

	// Per-document state variables
	int[][] superSubCounts; // # of words per <super, sub>
	int[] superCounts; // # of words per <super>
	double[] superWeights; // the component of the Gibbs update that depends on super-topics
	double[] subWeights;   // the component of the Gibbs update that depends on sub-topics
	double[][] superSubWeights; // unnormalized sampling distribution
	double[] cumulativeSuperWeights; // a cache of the cumulative weight for each super-topic

	// Per-word type state variables
	int[][] typeSubTopicCounts; // indexed by <feature index, topic index>
	int[] tokensPerSubTopic; // indexed by <topic index>

	// [for debugging purposes]
	int[] tokensPerSuperTopic; // indexed by <topic index>
	int[][] tokensPerSuperSubTopic;

	// Histograms for MLE
	int[][] superTopicHistograms; // histogram of # of words per supertopic in documents
	//  eg, [17][4] is # of docs with 4 words in sT 17...
	int[][][] subTopicHistograms; // for each supertopic, histogram of # of words per subtopic

	Runtime runtime;
	NumberFormat formatter;

	public PAM4L (int superTopics, int subTopics) {
		this (superTopics, subTopics, 50.0, 0.001);
	}

	public PAM4L (int superTopics, int subTopics,
	              double alphaSum, double beta) {
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		this.numSuperTopics = superTopics;
		this.numSubTopics = subTopics;

		this.alphaSum = alphaSum;
		this.alpha = new double[superTopics];
		Arrays.fill(alpha, alphaSum / numSuperTopics);

		subAlphas = new double[superTopics][subTopics];
		subAlphaSums = new double[superTopics];

		// Initialize the sub-topic alphas to a symmetric dirichlet.
		for (int superTopic = 0; superTopic < superTopics; superTopic++) {
			Arrays.fill(subAlphas[superTopic], 1.0);
		}
		Arrays.fill(subAlphaSums, subTopics);

		this.beta = beta; // We can't calculate vBeta until we know how many word types...

		runtime = Runtime.getRuntime();
	}

	public void estimate (InstanceList documents, int numIterations, int optimizeInterval, 
	                      int showTopicsInterval,
	                      int outputModelInterval, String outputModelFilename,
	                      Randoms r)
	{
		ilist = documents;
		numTypes = ilist.getDataAlphabet().size ();
		int numDocs = ilist.size();
		superTopics = new int[numDocs][];
		subTopics = new int[numDocs][];

		//		Allocate several arrays for use within each document
		//		to cut down memory allocation and garbage collection time

		superSubCounts = new int[numSuperTopics][numSubTopics];
		superCounts = new int[numSuperTopics];
		superWeights = new double[numSuperTopics];
		subWeights = new double[numSubTopics];
		superSubWeights = new double[numSuperTopics][numSubTopics];
		cumulativeSuperWeights = new double[numSuperTopics];

		typeSubTopicCounts = new int[numTypes][numSubTopics];
		tokensPerSubTopic = new int[numSubTopics];
		tokensPerSuperTopic = new int[numSuperTopics];
		tokensPerSuperSubTopic = new int[numSuperTopics][numSubTopics];
		vBeta = beta * numTypes;

		long startTime = System.currentTimeMillis();

		int maxTokens = 0;

		//		Initialize with random assignments of tokens to topics
		//		and finish allocating this.topics and this.tokens

		int superTopic, subTopic, seqLen;

		for (int di = 0; di < numDocs; di++) {

			FeatureSequence fs = (FeatureSequence) ilist.get(di).getData();

			seqLen = fs.getLength();
			if (seqLen > maxTokens) { 
				maxTokens = seqLen;
			}

			numTokens += seqLen;
			superTopics[di] = new int[seqLen];
			subTopics[di] = new int[seqLen];

			// Randomly assign tokens to topics
			for (int si = 0; si < seqLen; si++) {
				// Random super-topic
				superTopic = r.nextInt(numSuperTopics);
				superTopics[di][si] = superTopic;
				tokensPerSuperTopic[superTopic]++;

				// Random sub-topic
				subTopic = r.nextInt(numSubTopics);
				subTopics[di][si] = subTopic;

				// For the sub-topic, we also need to update the 
				//  word type statistics
				typeSubTopicCounts[ fs.getIndexAtPosition(si) ][subTopic]++;
				tokensPerSubTopic[subTopic]++;

				tokensPerSuperSubTopic[superTopic][subTopic]++;
			}
		}

		System.out.println("max tokens: " + maxTokens);

		//		These will be initialized at the first call to 
		//		clearHistograms() in the loop below.

		superTopicHistograms = new int[numSuperTopics][maxTokens + 1];
		subTopicHistograms = new int[numSuperTopics][numSubTopics][maxTokens + 1];

		//		Finally, start the sampler!

		for (int iterations = 0; iterations < numIterations; iterations++) {
			long iterationStart = System.currentTimeMillis();

			clearHistograms();
			sampleTopicsForAllDocs (r);

			// There are a few things we do on round-numbered iterations
			//  that don't make sense if this is the first iteration.

			if (iterations > 0) {
				if (showTopicsInterval != 0 && iterations % showTopicsInterval == 0) {
					System.out.println ();
					printTopWords (5, false);
				}
				if (outputModelInterval != 0 && iterations % outputModelInterval == 0) {
					//this.write (new File(outputModelFilename+'.'+iterations));
				}
				if (optimizeInterval != 0 && iterations % optimizeInterval == 0) {
					long optimizeTime = System.currentTimeMillis();
					for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
						learnParameters(subAlphas[superTopic],
								subTopicHistograms[superTopic],
								superTopicHistograms[superTopic]);
						subAlphaSums[superTopic] = 0.0;
						for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
							subAlphaSums[superTopic] += subAlphas[superTopic][subTopic];
						}
					}
					System.out.print("[o:" + (System.currentTimeMillis() - optimizeTime) + "]");
				}
			}

			if (iterations > 1107) {
				printWordCounts();
			}

			if (iterations % 10 == 0)
				System.out.println ("<" + iterations + "> ");

			System.out.print((System.currentTimeMillis() - iterationStart) + " ");

			//else System.out.print (".");
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

		//		124.5 seconds
		//		144.8 seconds after using FeatureSequence instead of tokens[][] array
		//		121.6 seconds after putting "final" on FeatureSequence.getIndexAtPosition()
		//		106.3 seconds after avoiding array lookup in inner loop with a temporary variable

	}

	private void clearHistograms() {
		for (int superTopic = 0; superTopic < numSuperTopics; superTopic++) {
			Arrays.fill(superTopicHistograms[superTopic], 0);
			for (int subTopic = 0; subTopic < numSubTopics; subTopic++) {
				Arrays.fill(subTopicHistograms[superTopic][subTopic], 0);
			}
		}
	}

	/** Use the fixed point iteration described by Tom Minka. */
	public void learnParameters(double[] parameters, int[][] observations, int[] observationLengths) {
		int i, k;

		double parametersSum = 0;

		//		Initialize the parameter sum

		for (k=0; k < parameters.length; k++) {
			parametersSum += parameters[k];
		}

		double oldParametersK;
		double currentDigamma;
		double denominator;

		int[] histogram;

		int nonZeroLimit;
		int[] nonZeroLimits = new int[observations.length];
		Arrays.fill(nonZeroLimits, -1);

		//		The histogram arrays go up to the size of the largest document,
		//		but the non-zero values will almost always cluster in the low end.
		//		We avoid looping over empty arrays by saving the index of the largest
		//		non-zero value.

		for (i=0; i<observations.length; i++) {
			histogram = observations[i];
			for (k = 0; k < histogram.length; k++) {
				if (histogram[k] > 0) {
					nonZeroLimits[i] = k;
				}
			}
		}

		for (int iteration=0; iteration<200; iteration++) {

			// Calculate the denominator
			denominator = 0;
			currentDigamma = 0;

			// Iterate over the histogram:
			for (i=1; i<observationLengths.length; i++) {
				currentDigamma += 1 / (parametersSum + i - 1);
				denominator += observationLengths[i] * currentDigamma;
			}

			/*
   if (Double.isNaN(denominator)) {
	System.out.println(parameterSum);
	for (i=1; i < observationLengths.length; i++) {
	    System.out.print(observationLengths[i] + " ");
	}
	System.out.println();
   }
			 */

			// Calculate the individual parameters

			parametersSum = 0;

			for (k=0; k<parameters.length; k++) {

				// What's the largest non-zero element in the histogram?
				nonZeroLimit = nonZeroLimits[k];

				// If there are no tokens assigned to this super-sub pair
				//  anywhere in the corpus, bail.

				if (nonZeroLimit == -1) {
					parameters[k] = 0.000001;
					parametersSum += 0.000001;
					continue;
				}

				oldParametersK = parameters[k];
				parameters[k] = 0;
				currentDigamma = 0;

				histogram = observations[k];

				for (i=1; i <= nonZeroLimit; i++) {
					currentDigamma += 1 / (oldParametersK + i - 1);
					parameters[k] += histogram[i] * currentDigamma;
				}

				parameters[k] *= oldParametersK / denominator;

				if (Double.isNaN(parameters[k])) {
					System.out.println("parametersK *= " + 
							oldParametersK + " / " +
							denominator);
					for (i=1; i < histogram.length; i++) {
						System.out.print(histogram[i] + " ");
					}
					System.out.println();
				}

				parametersSum += parameters[k];
			}
		}
	}

	/* One iteration of Gibbs sampling, across all documents. */
	private void sampleTopicsForAllDocs (Randoms r)
	{
//		Loop over every word in the corpus
		for (int di = 0; di < superTopics.length; di++) {

			sampleTopicsForOneDoc ((FeatureSequence)ilist.get(di).getData(),
					superTopics[di], subTopics[di], r);
		}
	}

	private void sampleTopicsForOneDoc (FeatureSequence oneDocTokens,
	                                    int[] superTopics, // indexed by seq position
	                                    int[] subTopics,
	                                    Randoms r) {

//		long startTime = System.currentTimeMillis();

		int[] currentTypeSubTopicCounts;
		int[] currentSuperSubCounts;
		double[] currentSuperSubWeights;
		double[] currentSubAlpha;

		int type, subTopic, superTopic;
		double currentSuperWeight, cumulativeWeight, sample;

		int docLen = oneDocTokens.getLength();

		for (int t = 0; t < numSuperTopics; t++) {
			Arrays.fill(superSubCounts[t], 0);
		}

		Arrays.fill(superCounts, 0);


//		populate topic counts
		for (int si = 0; si < docLen; si++) {
			superSubCounts[ superTopics[si] ][ subTopics[si] ]++;
			superCounts[ superTopics[si] ]++;
		}

//		Iterate over the positions (words) in the document

		for (int si = 0; si < docLen; si++) {

			type = oneDocTokens.getIndexAtPosition(si);
			superTopic = superTopics[si];
			subTopic = subTopics[si];

			// Remove this token from all counts
			superSubCounts[superTopic][subTopic]--;
			superCounts[superTopic]--;
			typeSubTopicCounts[type][subTopic]--;
			tokensPerSuperTopic[superTopic]--;
			tokensPerSubTopic[subTopic]--;
			tokensPerSuperSubTopic[superTopic][subTopic]--;

			// Build a distribution over super-sub topic pairs 
			//   for this token

			// Clear the data structures
			for (int t = 0; t < numSuperTopics; t++) {
				Arrays.fill(superSubWeights[t], 0.0);
			}
			Arrays.fill(superWeights, 0.0);
			Arrays.fill(subWeights, 0.0);
			Arrays.fill(cumulativeSuperWeights, 0.0);

			// Avoid two layer (ie [][]) array accesses
			currentTypeSubTopicCounts = typeSubTopicCounts[type];

			// The conditional probability of each super-sub pair is proportional
			//  to an expression with three parts, one that depends only on the 
			//  super-topic, one that depends only on the sub-topic and the word type,
			//  and one that depends on the super-sub pair.

			// Calculate each of the super-only factors first

			for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
				superWeights[superTopic] = ((double) superCounts[superTopic] + alpha[superTopic]) /
				((double) superCounts[superTopic] + subAlphaSums[superTopic]);
			}

			// Next calculate the sub-only factors

			for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
				subWeights[subTopic] = ((double) currentTypeSubTopicCounts[subTopic] + beta) / 
				((double) tokensPerSubTopic[subTopic] + vBeta);
			}

			// Finally, put them together

			cumulativeWeight = 0.0;

			for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
				currentSuperSubWeights = superSubWeights[superTopic];
				currentSuperSubCounts = superSubCounts[superTopic];
				currentSubAlpha = subAlphas[superTopic];
				currentSuperWeight = superWeights[superTopic];

				for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
					currentSuperSubWeights[subTopic] =
						currentSuperWeight *
						subWeights[subTopic] * 
						((double) currentSuperSubCounts[subTopic] + currentSubAlpha[subTopic]);
					cumulativeWeight += currentSuperSubWeights[subTopic];
				}

				cumulativeSuperWeights[superTopic] = cumulativeWeight;
			}

			// Sample a topic assignment from this distribution
			sample = r.nextUniform() * cumulativeWeight;

			// Go over the row sums to find the super-topic...
			superTopic = 0;
			while (sample > cumulativeSuperWeights[superTopic]) {
				superTopic++;
			}

			// Now read across to find the sub-topic
			currentSuperSubWeights = superSubWeights[superTopic];
			cumulativeWeight = cumulativeSuperWeights[superTopic] -
			currentSuperSubWeights[0];

			// Go over each sub-topic until the weight is LESS than
			//  the sample. Note that we're subtracting weights
			//  in the same order we added them...
			subTopic = 0;
			while (sample < cumulativeWeight) {
				subTopic++;
				cumulativeWeight -= currentSuperSubWeights[subTopic];
			}

			// Save the choice into the Gibbs state

			superTopics[si] = superTopic;
			subTopics[si] = subTopic;

			// Put the new super/sub topics into the counts

			superSubCounts[superTopic][subTopic]++;
			superCounts[superTopic]++;
			typeSubTopicCounts[type][subTopic]++;
			tokensPerSuperTopic[superTopic]++;
			tokensPerSubTopic[subTopic]++;
			tokensPerSuperSubTopic[superTopic][subTopic]++;
		}

		//		Update the topic count histograms
		//		for dirichlet estimation

		for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {

			superTopicHistograms[superTopic][ superCounts[superTopic] ]++;
			currentSuperSubCounts = superSubCounts[superTopic];

			for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
				subTopicHistograms[superTopic][subTopic][ currentSuperSubCounts[subTopic] ]++;
			}
		}
	}

	public void printWordCounts () {
		int subTopic, superTopic;

		StringBuffer output = new StringBuffer();

		for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
			for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
				output.append (tokensPerSuperSubTopic[superTopic][subTopic] + " (" +
						formatter.format(subAlphas[superTopic][subTopic]) + ")\t");
			}
			output.append("\n");
		}

		System.out.println(output);
	}

	public void printTopWords (int numWords, boolean useNewLines) {

		IDSorter[] wp = new IDSorter[numTypes];
		IDSorter[] sortedSubTopics = new IDSorter[numSubTopics];
		String[] subTopicTerms = new String[numSubTopics];

		int subTopic, superTopic;

		for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
			for (int wi = 0; wi < numTypes; wi++)
				wp[wi] = new IDSorter (wi, (((double) typeSubTopicCounts[wi][subTopic]) /
						tokensPerSubTopic[subTopic]));
			Arrays.sort (wp);

			StringBuffer topicTerms = new StringBuffer();
			for (int i = 0; i < numWords; i++) {
				topicTerms.append(ilist.getDataAlphabet().lookupObject(wp[i].wi));
				topicTerms.append(" ");
			}
			subTopicTerms[subTopic] = topicTerms.toString();

			if (useNewLines) {
				System.out.println ("\nTopic " + subTopic);
				for (int i = 0; i < numWords; i++)
					System.out.println (ilist.getDataAlphabet().lookupObject(wp[i].wi).toString() +
							"\t" + formatter.format(wp[i].p));
			} else {
				System.out.println ("Topic "+ subTopic +":\t[" + tokensPerSubTopic[subTopic] + "]\t" +
						subTopicTerms[subTopic]);
			}
		}

		int maxSubTopics = 10;
		if (numSubTopics < 10) { maxSubTopics = numSubTopics; }

		for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
			for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
				sortedSubTopics[subTopic] = new IDSorter(subTopic, subAlphas[superTopic][subTopic]);
			}

			Arrays.sort(sortedSubTopics);

			System.out.println("\nSuper-topic " + superTopic + 
					"[" + tokensPerSuperTopic[superTopic] + "]\t");
			for (int i = 0; i < maxSubTopics; i++) {
				subTopic = sortedSubTopics[i].wi;
				System.out.println(subTopic + ":\t" +
						formatter.format(subAlphas[superTopic][subTopic]) + "\t" +
						subTopicTerms[subTopic]);
			}
		}
	}

	public void printDocumentTopics (File f) throws IOException {
		printDocumentTopics (new PrintWriter (new BufferedWriter( new FileWriter (f))), 0.0, -1);
	}

	// This looks broken. -DM
	public void printDocumentTopics (PrintWriter pw, double threshold, int max) {

		pw.println ("#doc source subtopic-proportions , supertopic-proportions");
		int docLen;
		double superTopicDist[] = new double [numSuperTopics];
		double subTopicDist[] = new double [numSubTopics];
		for (int di = 0; di < superTopics.length; di++) {
			pw.print (di); pw.print (' ');
			docLen = superTopics[di].length;
			if (ilist.get(di).getSource() != null){
				pw.print (ilist.get(di).getSource().toString()); 
			}
			else {
				pw.print("null-source");
			}
			pw.print (' ');
      docLen = subTopics[di].length;
      // populate per-document topic counts
  		for (int si = 0; si < docLen; si++) {
  			superTopicDist[superTopics[di][si]] += 1.0;
  			subTopicDist[subTopics[di][si]] += 1.0;
  		}
      for (int ti = 0; ti < numSuperTopics; ti++)
      	superTopicDist[ti] /= docLen;
      for (int ti = 0; ti < numSubTopics; ti++)
      	subTopicDist[ti] /= docLen;
      
      // print the subtopic prortions, sorted
      if (max < 0) max = numSubTopics;
      for (int tp = 0; tp < max; tp++) {
        double maxvalue = 0;
        int maxindex = -1;
        for (int ti = 0; ti < numSubTopics; ti++)
          if (subTopicDist[ti] > maxvalue) {
            maxvalue = subTopicDist[ti];
            maxindex = ti;
          }
        if (maxindex == -1 || subTopicDist[maxindex] < threshold)
          break;
        pw.print (maxindex+" "+subTopicDist[maxindex]+" ");
        subTopicDist[maxindex] = 0;
      }
      pw.print (" , ");
      // print the supertopic prortions, sorted
      if (max < 0) max = numSuperTopics;
      for (int tp = 0; tp < max; tp++) {
        double maxvalue = 0;
        int maxindex = -1;
        for (int ti = 0; ti < numSuperTopics; ti++)
          if (superTopicDist[ti] > maxvalue) {
            maxvalue = superTopicDist[ti];
            maxindex = ti;
          }
        if (maxindex == -1 || superTopicDist[maxindex] < threshold)
          break;
        pw.print (maxindex+" "+superTopicDist[maxindex]+" ");
        superTopicDist[maxindex] = 0;
      }
      pw.println ();

		}
	}
	

	public void printState (File f) throws IOException
	{
		printState (new PrintWriter (new BufferedWriter (new FileWriter(f))));
	}

	public void printState (PrintWriter pw)
	{
		Alphabet a = ilist.getDataAlphabet();
		pw.println ("#doc pos typeindex type super-topic sub-topic");
		for (int di = 0; di < superTopics.length; di++) {
			FeatureSequence fs = (FeatureSequence) ilist.get(di).getData();
			for (int si = 0; si < superTopics[di].length; si++) {
				int type = fs.getIndexAtPosition(si);
				pw.print(di); pw.print(' ');
				pw.print(si); pw.print(' ');
				pw.print(type); pw.print(' ');
				pw.print(a.lookupObject(type)); pw.print(' ');
				pw.print(superTopics[di][si]); pw.print(' ');
				pw.print(subTopics[di][si]); pw.println();
			}
		}
		pw.close();
	}

	/*
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
out.writeObject (alpha);
out.writeDouble (beta);
out.writeDouble (vBeta);
for (int di = 0; di < topics.length; di ++)
   for (int si = 0; si < topics[di].length; si++)
	out.writeInt (topics[di][si]);
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
alpha = (double[]) in.readObject();
beta = in.readDouble();
vBeta = in.readDouble();
int numDocs = ilist.size();
topics = new int[numDocs][];
for (int di = 0; di < ilist.size(); di++) {
   int docLen = ((FeatureSequence)ilist.getInstance(di).getData()).getLength();
	    topics[di] = new int[docLen];
	    for (int si = 0; si < docLen; si++)
		topics[di][si] = in.readInt();
}

int numTypes = ilist.getDataAlphabet().size();
typeTopicCounts = new int[numTypes][numTopics];
for (int fi = 0; fi < numTypes; fi++)
   for (int ti = 0; ti < numTopics; ti++)
	typeTopicCounts[fi][ti] = in.readInt();
tokensPerTopic = new int[numTopics];
for (int ti = 0; ti < numTopics; ti++)
   tokensPerTopic[ti] = in.readInt();
 }
	 */

	// Recommended to use mallet/bin/vectors2topics instead.
	public static void main (String[] args) throws IOException
	{
		InstanceList ilist = InstanceList.load (new File(args[0]));
		int numIterations = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
		int numTopWords = args.length > 2 ? Integer.parseInt(args[2]) : 20;
		int numSuperTopics = args.length > 3 ? Integer.parseInt(args[3]) : 10;
		int numSubTopics = args.length > 4 ? Integer.parseInt(args[4]) : 10;
		System.out.println ("Data loaded.");
		PAM4L pam = new PAM4L (numSuperTopics, numSubTopics);
		pam.estimate (ilist, numIterations, 50, 0, 50, null, new Randoms());  // should be 1100
		pam.printTopWords (numTopWords, true);
//		pam.printDocumentTopics (new File(args[0]+".pam"));
	}

	class IDSorter implements Comparable {
		int wi; double p;
		public IDSorter (int wi, double p) { this.wi = wi; this.p = p; }
		public final int compareTo (Object o2) {
			if (p > ((IDSorter) o2).p)
				return -1;
			else if (p == ((IDSorter) o2).p)
				return 0;
			else return 1;
		}
	}

}
