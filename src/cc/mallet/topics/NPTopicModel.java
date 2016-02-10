/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.	For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.topics.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

import gnu.trove.*;

/**
 * A non-parametric topic model that uses the "minimal path" assumption
 *  to reduce bookkeeping.
 * 
 * @author David Mimno
 */

public class NPTopicModel implements Serializable {

	private static Logger logger = MalletLogger.getLogger(NPTopicModel.class.getName());
	
	// the training instances and their topic assignments
	protected ArrayList<TopicAssignment> data;  

	// the alphabet for the input data
	protected Alphabet alphabet; 

	// the alphabet for the topics
	protected LabelAlphabet topicAlphabet; 
	
	// The largest topic ID seen so far
	protected int maxTopic;
	// The current number of topics
	protected int numTopics;

	// The size of the vocabulary
	protected int numTypes;

	// Prior parameters
	protected double alpha;
	protected double gamma;
	protected double beta;   // Prior on per-topic multinomial distribution over words
	protected double betaSum;
	public static final double DEFAULT_BETA = 0.01;
	
	// Statistics needed for sampling.
	protected TIntIntHashMap[] typeTopicCounts; // indexed by <feature index, topic index>
	protected TIntIntHashMap tokensPerTopic; // indexed by <topic index>

	// The number of documents that contain at least one
	//  token with a given topic.
	protected TIntIntHashMap docsPerTopic;
	protected int totalDocTopics = 0;
	
	public int showTopicsInterval = 50;
	public int wordsPerTopic = 10;
	
	protected Randoms random;
	protected NumberFormat formatter;
	protected boolean printLogLikelihood = false;
	
	/** @param alpha this parameter balances the local document topic counts with 
	 *                the global distribution over topics.
	 *  @param gamma this parameter is the weight on a completely new, never-before-seen topic
	 *                in the global distribution.
	 *  @param beta  this parameter controls the variability of the topic-word distributions
	 */
	public NPTopicModel (double alpha, double gamma, double beta) {

		this.data = new ArrayList<TopicAssignment>();
		this.topicAlphabet = AlphabetFactory.labelAlphabetOfSize(1);

		this.alpha = alpha;
		this.gamma = gamma;
		this.beta = beta;
		this.random = new Randoms();
		
		tokensPerTopic = new TIntIntHashMap();
		docsPerTopic = new TIntIntHashMap();
		
		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		logger.info("Non-Parametric LDA");
	}
	
	public void setTopicDisplay(int interval, int n) {
		this.showTopicsInterval = interval;
		this.wordsPerTopic = n;
	}

	public void setRandomSeed(int seed) {
		random = new Randoms(seed);
	}

	public void addInstances (InstanceList training, int initialTopics) {

		alphabet = training.getDataAlphabet();
		numTypes = alphabet.size();
		
		betaSum = beta * numTypes;
		
		typeTopicCounts = new TIntIntHashMap[numTypes];
		for (int type=0; type < numTypes; type++) {
			typeTopicCounts[type] = new TIntIntHashMap();
		}

		numTopics = initialTopics;
		
		int doc = 0;

		for (Instance instance : training) {
			doc++;

			TIntIntHashMap topicCounts = new TIntIntHashMap();

			FeatureSequence tokens = (FeatureSequence) instance.getData();
			LabelSequence topicSequence =
				new LabelSequence(topicAlphabet, new int[ tokens.size() ]);
			
			int[] topics = topicSequence.getFeatures();
			for (int position = 0; position < tokens.size(); position++) {

				int topic = random.nextInt(numTopics);
				tokensPerTopic.adjustOrPutValue(topic, 1, 1);
				topics[position] = topic;

				// Keep track of the number of docs with at least one token
				//  in a given topic.
				if (! topicCounts.containsKey(topic)) {
					docsPerTopic.adjustOrPutValue(topic, 1, 1);
					totalDocTopics++;
					topicCounts.put(topic, 1);
				}
				else {
					topicCounts.adjustValue(topic, 1);
				}
				
				int type = tokens.getIndexAtPosition(position);
				typeTopicCounts[type].adjustOrPutValue(topic, 1, 1);
			}

			TopicAssignment t = new TopicAssignment (instance, topicSequence);
			data.add (t);
		}

		maxTopic = numTopics - 1;

	}

	public void sample (int iterations) throws IOException {

		for (int iteration = 1; iteration <= iterations; iteration++) {

			long iterationStart = System.currentTimeMillis();

			// Loop over every document in the corpus
			for (int doc = 0; doc < data.size(); doc++) {
				FeatureSequence tokenSequence =
					(FeatureSequence) data.get(doc).instance.getData();
				LabelSequence topicSequence =
					(LabelSequence) data.get(doc).topicSequence;

				sampleTopicsForOneDoc (tokenSequence, topicSequence);
			}
		
            long elapsedMillis = System.currentTimeMillis() - iterationStart;
			logger.info(iteration + "\t" + elapsedMillis + "ms\t" + numTopics);

			// Occasionally print more information
			if (showTopicsInterval != 0 && iteration % showTopicsInterval == 0) {
				logger.info("<" + iteration + "> #Topics: " + numTopics + "\n" +
							topWords (wordsPerTopic));
			}

		}
	}
	
	protected void sampleTopicsForOneDoc (FeatureSequence tokenSequence,
										  FeatureSequence topicSequence) {

		int[] topics = topicSequence.getFeatures();

		TIntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		TIntIntHashMap localTopicCounts = new TIntIntHashMap();

		//		populate topic counts
		for (int position = 0; position < docLength; position++) {
			localTopicCounts.adjustOrPutValue(topics[position], 1, 1);
		}

		double score, sum;
		double[] topicTermScores = new double[numTopics + 1];

		// Store a list of all the topics that currently exist.
		int[] allTopics = docsPerTopic.keys();
			
		//	Iterate over the positions (words) in the document 
		for (int position = 0; position < docLength; position++) {
			type = tokenSequence.getIndexAtPosition(position);
			oldTopic = topics[position];

			// Grab the relevant row from our two-dimensional array
			currentTypeTopicCounts = typeTopicCounts[type];

			//	Remove this token from all counts. 
			
			int currentCount = localTopicCounts.get(oldTopic);

			// Was this the only token of this topic in the doc?
			if (currentCount == 1) {
				localTopicCounts.remove(oldTopic);
				
				// Was this the only doc with this topic?
				int docCount = docsPerTopic.get(oldTopic);
				if (docCount == 1) {
					// This should be the very last token
					assert(tokensPerTopic.get(oldTopic) == 1);
					
					// Get rid of the topic
					docsPerTopic.remove(oldTopic);
					totalDocTopics--;
					tokensPerTopic.remove(oldTopic);
					numTopics--;

					allTopics = docsPerTopic.keys();
					topicTermScores = new double[numTopics + 1];
				}
				else {
					// This is the last in the doc, but the topic still exists
					docsPerTopic.adjustValue(oldTopic, -1);
					totalDocTopics--;
					tokensPerTopic.adjustValue(oldTopic, -1);
				}
			}
			else {
				// There is at least one other token in this doc
				//  with this topic.
				localTopicCounts.adjustValue(oldTopic, -1);
				tokensPerTopic.adjustValue(oldTopic, -1);
			}

			if (currentTypeTopicCounts.get(oldTopic) == 1) {
				currentTypeTopicCounts.remove(oldTopic);
			}
			else {
				currentTypeTopicCounts.adjustValue(oldTopic, -1);
			}

			// Now calculate and add up the scores for each topic for this word
			sum = 0.0;

			// First do the topics that currently exist
			for (int i = 0; i < numTopics; i++) {
				int topic = allTopics[i];

				topicTermScores[i] =
					(localTopicCounts.get(topic) + 
					 alpha * (docsPerTopic.get(topic) / 
							  (totalDocTopics + gamma))) *
					(currentTypeTopicCounts.get(topic) + beta) /
					(tokensPerTopic.get(topic) + betaSum);

				sum += topicTermScores[i];
			}

			// Add the weight for a new topic
			topicTermScores[numTopics] =
				alpha * gamma / ( numTypes * (totalDocTopics + gamma) );
			
			sum += topicTermScores[numTopics];

			// Choose a random point between 0 and the sum of all topic scores
			double sample = random.nextUniform() * sum;

			// Figure out which topic contains that point
			newTopic = -1;
			
			int i = -1;
			while (sample > 0.0) {
				i++;
				sample -= topicTermScores[i];
			}

			if (i < numTopics) {
				newTopic = allTopics[i];

				topics[position] = newTopic;
				currentTypeTopicCounts.adjustOrPutValue(newTopic, 1, 1);
				tokensPerTopic.adjustValue(newTopic, 1);
				
				if (localTopicCounts.containsKey(newTopic)) {
					localTopicCounts.adjustValue(newTopic, 1);
				}
				else {
					// This is not a new topic, but it is new for this doc.
					localTopicCounts.put(newTopic, 1);
					docsPerTopic.adjustValue(newTopic, 1);
					totalDocTopics++;
				}
			}
			else {
				// completely new topic: first generate an ID

				newTopic = maxTopic + 1;
				maxTopic = newTopic;

				numTopics++;
				
				topics[position] = newTopic;
				localTopicCounts.put(newTopic, 1);
				
				docsPerTopic.put(newTopic, 1);
				totalDocTopics++;
				
				currentTypeTopicCounts.put(newTopic, 1);
                tokensPerTopic.put(newTopic, 1);
				
				allTopics = docsPerTopic.keys();
			    topicTermScores = new double[numTopics + 1];
			}
		}
	}
	
	// 
	// Methods for displaying and saving results
	//

	public String topWords (int numWords) {

		StringBuilder output = new StringBuilder();

		IDSorter[] sortedWords = new IDSorter[numTypes];

		for (int topic: docsPerTopic.keys()) {
			for (int type = 0; type < numTypes; type++) {
				sortedWords[type] = new IDSorter(type, typeTopicCounts[type].get(topic));
			}

			Arrays.sort(sortedWords);
			
			output.append(topic + "\t" + tokensPerTopic.get(topic) + "\t");
			for (int i=0; i < numWords; i++) {
				if (sortedWords[i].getWeight() < 1.0) {
					break;
				}
				output.append(alphabet.lookupObject(sortedWords[i].getID()) + " ");
			}
			output.append("\n");
		}

		return output.toString();
	}

	public void printState (File f) throws IOException {
		PrintStream out =
			new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(f))));
		printState(out);
		out.close();
	}
	
	public void printState (PrintStream out) {

		out.println ("#doc source pos typeindex type topic");

		for (int doc = 0; doc < data.size(); doc++) {
			FeatureSequence tokenSequence =	(FeatureSequence) data.get(doc).instance.getData();
			LabelSequence topicSequence =	(LabelSequence) data.get(doc).topicSequence;

			String source = "NA";
			if (data.get(doc).instance.getSource() != null) {
				source = data.get(doc).instance.getSource().toString();
			}

			for (int position = 0; position < topicSequence.getLength(); position++) {
				int type = tokenSequence.getIndexAtPosition(position);
				int topic = topicSequence.getIndexAtPosition(position);
				out.print(doc); out.print(' ');
				out.print(source); out.print(' '); 
				out.print(position); out.print(' ');
				out.print(type); out.print(' ');
				out.print(alphabet.lookupObject(type)); out.print(' ');
				out.print(topic); out.println();
			}
		}
	}
	
	public static void main (String[] args) throws IOException {

		InstanceList training = InstanceList.load (new File(args[0]));

		int numTopics = args.length > 1 ? Integer.parseInt(args[1]) : 200;

		NPTopicModel lda = new NPTopicModel (5.0, 10.0, 0.1);
		lda.addInstances(training, numTopics);
		lda.sample(1000);
	}
	
}
