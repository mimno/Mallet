package cc.mallet.topics;

import cc.mallet.classify.MaxEnt;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelSequence;
import cc.mallet.util.Randoms;

import java.util.List;

/**
 * A parallel Dirichlet-multinomial regression topic model runnable task.
 * 
 * @author David Mimno
 */

public class DMRRunnable extends WorkerRunnable implements Runnable {

	MaxEnt dmrParameters;
	int numFeatures;
	int defaultFeatureIndex;
	DMRTopicModel model;

	public DMRRunnable (int numTopics, DMRTopicModel model, double beta, Randoms random, List<TopicAssignment> data, int[][] typeTopicCounts, int[] tokensPerTopic, int startDoc, int numDocs) {
		this.data = data;
		
		this.model = model;

		this.dmrParameters = model.dmrParameters;
		this.numFeatures = model.numFeatures;
		this.defaultFeatureIndex = model.defaultFeatureIndex;
		this.numTopics = numTopics;
		this.numTypes = typeTopicCounts.length;

		if (Integer.bitCount(numTopics) == 1) {
			// exact power of 2
			topicMask = numTopics - 1;
			topicBits = Integer.bitCount(topicMask);
		}
		else {
			// otherwise add an extra bit
			topicMask = Integer.highestOneBit(numTopics) * 2 - 1;
			topicBits = Integer.bitCount(topicMask);
		}

		this.typeTopicCounts = typeTopicCounts;
		this.tokensPerTopic = tokensPerTopic;
		
		this.alpha = new double[numTopics];
		this.beta = beta;
		this.betaSum = beta * numTypes;
		this.random = random;
		
		this.startDoc = startDoc;
		this.numDocs = numDocs;

		cachedCoefficients = new double[ numTopics ];
	}
		
	public void run () {

		try {
			
			if (! isFinished) { System.out.println("already running!"); return; }
			
			isFinished = false;
			
			// Initialize the smoothing-only sampling bucket
			smoothingOnlyMass = 0;
			
			// Initialize the cached coefficients, using only smoothing.
			//  These values will be selectively replaced in documents with
			//  non-zero counts in particular topics.
						
			for (int doc = startDoc;
				 doc < data.size() && doc < startDoc + numDocs;
				 doc++) {
				
				/*
				  if (doc % 10000 == 0) {
				  System.out.println("processing doc " + doc);
				  }
				*/
				
				Instance instance = data.get(doc).instance;
				alpha = model.alphaCache[doc];
				alphaSum = model.alphaSumCache[doc];
				smoothingOnlyMass = 0.0;

				// Use only the default features to set the topic prior (use no document features)
				for (int topic=0; topic < numTopics; topic++) {			
					smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
					cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);
				}
				
				FeatureSequence tokenSequence =
					(FeatureSequence) instance.getData();
				LabelSequence topicSequence =
					(LabelSequence) data.get(doc).topicSequence;
				
				sampleTopicsForOneDoc (tokenSequence, topicSequence, true);
			}
			
			if (shouldBuildLocalCounts) {
				buildLocalTypeTopicCounts();
			}

			shouldSaveState = false;
			isFinished = true;

		} catch (Exception e) {
			isFinished = true;
			e.printStackTrace();
		}
	}
	
}