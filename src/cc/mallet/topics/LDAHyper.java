/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;

/**
 * Latent Dirichlet Allocation with optimized hyperparameters \alpha.
 * @author David Mimno, Andrew McCallum
 */


public class LDAHyper {

	int numTopics; // Number of topics to be fit
	double[] alpha;  // Dirichlet(alpha,alpha,...) is the distribution over topics
	double alphaSum;
	double beta;   // Prior on per-topic multinomial distribution over words
	double betaSum;
	InstanceList instances, testing;  // the data field of the instances is expected to hold a FeatureSequence
	int[][] topics; // indexed by <document index, sequence index>
	int numTypes;
	int numTokens;
	int[] oneDocTopicCounts; // indexed by <document index, topic index>
	int[][] typeTopicCounts; // indexed by <feature index, topic index>
	int[] tokensPerTopic; // indexed by <topic index>

	// for dirichlet estimation
	int[] docLengthCounts; // histogram of document sizes
	int[][] topicDocCounts; // histogram of document/topic counts

	int numIterations = 1000;
	int burninPeriod = 200;
	int saveSampleInterval = 10;    
	int optimizeInterval = 50;
	int showTopicsInterval = 50;

	Randoms random;

	Runtime runtime;
	NumberFormat formatter;

	public LDAHyper (int numberOfTopics) {
		this (numberOfTopics, numberOfTopics, 0.01);
	}

	public LDAHyper (int numberOfTopics, double alphaSum, double beta)
	{
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		System.out.println("optimizingLDA: " + numberOfTopics);

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

	public void estimate () {

		if (random == null) {
			random = new Randoms();
		}

		numTypes = instances.getDataAlphabet().size ();
		int numDocs = instances.size();
		topics = new int[numDocs][];
		oneDocTopicCounts = new int[numTopics];
		typeTopicCounts = new int[numTypes][numTopics];
		tokensPerTopic = new int[numTopics];
		betaSum = beta * numTypes;

		long startTime = System.currentTimeMillis();

		int maxTokens = 0;
		int totalTokens = 0;

//		Initialize with random assignments of tokens to topics
//		and finish allocating this.topics and this.tokens
		int topic, seqLen;
		for (int di = 0; di < numDocs; di++) {
			FeatureSequence fs = (FeatureSequence) instances.get(di).getData();
			seqLen = fs.getLength();
			if (seqLen > maxTokens) { 
				maxTokens = seqLen;
			}
			totalTokens += seqLen;

			numTokens += seqLen;
			topics[di] = new int[seqLen];
			// Randomly assign tokens to topics
			for (int si = 0; si < seqLen; si++) {
				topic = random.nextInt(numTopics);
				topics[di][si] = topic;

				typeTopicCounts[ fs.getIndexAtPosition(si) ][topic]++;
				tokensPerTopic[topic]++;
			}
		}

		System.out.println("max tokens: " + maxTokens);
		System.out.println("total tokens: " + totalTokens);

//		These will be initialized at the first call to 
//		clearHistograms() in the loop below.
		docLengthCounts = new int[maxTokens + 1];
		topicDocCounts = new int[numTopics][maxTokens + 1];

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
			if (iterations > burninPeriod && 
					optimizeInterval != 0 &&
					iterations % optimizeInterval == 0) {

				long optimizeTime = System.currentTimeMillis();
				alphaSum = learnParameters(alpha, topicDocCounts, docLengthCounts);
				//System.out.print("[o:" + (System.currentTimeMillis() - optimizeTime) + "]");
				clearHistograms();
			}

			sampleTopicsForAllDocs (random, iterations > burninPeriod && iterations % saveSampleInterval == 0);

			System.out.print((System.currentTimeMillis() - iterationStart) + " ");
			if (iterations % 10 == 0) {
				System.out.println ("<" + iterations + "> ");
				System.out.println (modelLogLikelihood());
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

	/** Use the fixed point iteration described by Tom Minka. */
	public double learnParameters(double[] parameters, int[][] observations, int[] observationLengths) {
		int i, k;

		double parametersSum = 0;

		//  Initialize the parameter sum

		for (k=0; k < parameters.length; k++) {
			parametersSum += parameters[k];
		}

		double oldParametersK;
		double currentDigamma;
		double denominator;

		int nonZeroLimit;
		int[] nonZeroLimits = new int[observations.length];
		Arrays.fill(nonZeroLimits, -1);

		// The histogram arrays go up to the size of the largest document,
		//  but the non-zero values will almost always cluster in the low end.
		//  We avoid looping over empty arrays by saving the index of the largest
		//  non-zero value.

		int[] histogram;

		for (i=0; i<observations.length; i++) {
			histogram = observations[i];

			StringBuffer out = new StringBuffer();
			for (k = 0; k < histogram.length; k++) {
				if (histogram[k] > 0) {
					nonZeroLimits[i] = k;
					out.append(k + ":" + histogram[k] + " ");
				}
			}
			//System.out.println(out);
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

				parametersSum += parameters[k];
			}
		}

		return parametersSum;
	}

	/* One iteration of Gibbs sampling, across all documents. */
	private void sampleTopicsForAllDocs (Randoms r, boolean shouldSaveState) {

		int[] topicCounts = new int[numTopics];

		double[] topicWeights = new double[numTopics];
		//		Loop over every word in the corpus
		for (int di = 0; di < topics.length; di++) {

			sampleTopicsForOneDoc ((FeatureSequence)instances.get(di).getData(),
					topics[di], topicWeights, r, shouldSaveState);

		}
	}

	private void sampleTopicsForOneDoc (FeatureSequence oneDocTokens, int[] oneDocTopics, // indexed by seq position
			double[] topicWeights, Randoms r, boolean shouldSaveState) {

		long startTime = System.currentTimeMillis();

		int[] currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double topicWeightsSum;
		int docLen = oneDocTokens.getLength();

		double tw;

		//		populate topic counts
		Arrays.fill(oneDocTopicCounts, 0);
		for (int si = 0; si < docLen; si++) {
			oneDocTopicCounts[oneDocTopics[si]]++;
		}

		//		Iterate over the positions (words) in the document
		for (int si = 0; si < docLen; si++) {
			type = oneDocTokens.getIndexAtPosition(si);
			oldTopic = oneDocTopics[si];

			// Remove this token from all counts
			oneDocTopicCounts[oldTopic]--;
			typeTopicCounts[type][oldTopic]--;
			tokensPerTopic[oldTopic]--;

			// Build a distribution over topics for this token
			topicWeightsSum = 0;
			currentTypeTopicCounts = typeTopicCounts[type];
			for (int ti = 0; ti < numTopics; ti++) {
				tw = ((currentTypeTopicCounts[ti] + beta) / (tokensPerTopic[ti] + betaSum))
				* ((oneDocTopicCounts[ti] + alpha[ti])); // (/docLen-1+tAlpha); is constant across all topics
				topicWeightsSum += tw;
				topicWeights[ti] = tw;
			}
			// Sample a topic assignment from this distribution
			newTopic = random.nextDiscrete (topicWeights, topicWeightsSum);

			// Put that new topic into the counts
			oneDocTopics[si] = newTopic;
			oneDocTopicCounts[newTopic]++;
			typeTopicCounts[type][newTopic]++;
			tokensPerTopic[newTopic]++;
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
			for (int si = 0; si < topics[di].length; si++) {
				int type = fs.getIndexAtPosition(si);
				pw.print(di); pw.print(' ');
				pw.print(si); pw.print(' ');
				pw.print(type); pw.print(' ');
				pw.print(a.lookupObject(type)); pw.print(' ');
				pw.print(topics[di][si]); pw.println();
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
			for (int si = 0; si < topics[di].length; si++)
				out.writeInt (topics[di][si]);
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
			for (int si = 0; si < docLen; si++)
				topics[di][si] = in.readInt();
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

//		The likelihood of the model is a combination of a 
//		Dirichlet-multinomial for the words in each topic
//		and a Dirichlet-multinomial for the topics in each
//		document.

//		The likelihood function of a dirichlet multinomial is
//		Gamma( sum_i alpha_i )  prod_i Gamma( alpha_i + N_i )
//		prod_i Gamma( alpha_i )   Gamma( sum_i (alpha_i + N_i) )

//		So the log likelihood is 
//		logGamma ( sum_i alpha_i ) - logGamma ( sum_i (alpha_i + N_i) ) + 
//		sum_i [ logGamma( alpha_i + N_i) - logGamma( alpha_i ) ]

//		Do the documents first

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

		//		add the parameter sum term
		logLikelihood += topics.length * Dirichlet.logGammaStirling(alphaSum);

		//		And the topics

		//		Count the number of type-topic pairs
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

		InstanceList testing = args.length > 2 ? InstanceList.load (new File(args[2])) : null;

		LDAHyper lda = new LDAHyper (numTopics);
		lda.setTrainingInstances(training);

		lda.estimate();


	}

}
