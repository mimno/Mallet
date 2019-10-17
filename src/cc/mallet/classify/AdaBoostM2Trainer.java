/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.util.Random;
import java.util.Arrays;
import java.util.logging.*;

import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;

/**
 * This version of AdaBoost can handle multi-class problems.  For
 * binary classification, can also use <tt>AdaBoostTrainer</tt>.
 *
 * <p>Yoav Freund and Robert E. Schapire
 * "Experiments with a New Boosting Algorithm"
 * In Journal of Machine Learning: Proceedings of the 13th International Conference, 1996
 * http://www.cs.princeton.edu/~schapire/papers/FreundSc96b.ps.Z
 *
 * @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class AdaBoostM2Trainer extends ClassifierTrainer<AdaBoostM2>
{
	private static Logger logger = MalletLogger.getLogger(AdaBoostM2Trainer.class.getName());
	private static int MAX_NUM_RESAMPLING_ITERATIONS = 10;

	ClassifierTrainer weakLearner;
	int numRounds;
	
	AdaBoostM2 classifier;
	@Override public AdaBoostM2 getClassifier () { return classifier; }

	public AdaBoostM2Trainer (ClassifierTrainer weakLearner, int numRounds)
	{
		if (! (weakLearner instanceof Boostable))
			throw new IllegalArgumentException ("weak learner not boostable");
		if (numRounds <= 0)
			throw new IllegalArgumentException ("number of rounds must be positive");
		this.weakLearner = weakLearner;
		this.numRounds = numRounds;
	}

	public AdaBoostM2Trainer (ClassifierTrainer weakLearner)
	{
		this (weakLearner, 100);
	}

	/**
	 * Boosting method that resamples instances using their weights
	 */    
	@Override public AdaBoostM2 train (InstanceList trainingList)
	{
		FeatureSelection selectedFeatures = trainingList.getFeatureSelection();
		if (selectedFeatures != null)
			throw new UnsupportedOperationException("FeatureSelection not yet implemented.");

		int numClasses = trainingList.getTargetAlphabet().size();
		int numInstances = trainingList.size();
		// Construct the set "B", a list of instances of size 
		// (numInstances * (numClasses - 1)).
		// Each instance in this list will have weights
		// (mislabel distribution) associated with classes
		// the intance doesn't belong to.
		InstanceList trainingInsts = new InstanceList(trainingList.getPipe());
		// Set the initial weights to be uniform
		double[] weights = new double[numInstances * (numClasses - 1)];
		double w = 1.0 / weights.length;
		Arrays.fill(weights, w);
		int[] classIndices = new int[weights.length];
		int numAdded = 0;
		for (int i = 0; i < numInstances; i++) {
			Instance inst = trainingList.get(i);
			int trueClassIndex = inst.getLabeling().getBestIndex();
			for (int j = 0; j < numClasses; j++) {
				if (j != trueClassIndex) {
					trainingInsts.add(inst, 1);
					classIndices[numAdded] = j;
					numAdded++;
				}
			}
		}
		java.util.Random random = new java.util.Random();
		Classifier[] weakLearners = new Classifier[numRounds];
		double[] classifierWeights = new double[numRounds];
		double[] exponents = new double[weights.length];
		int[] instIndices = new int[weights.length];
		for (int i = 0; i < instIndices.length; i++)
			instIndices[i] = i;
		// Boosting iterations
		for (int round = 0; round < numRounds; round++) {
			logger.info("===========  AdaBoostM2Trainer round " + (round+1) + " begin");
			// Sample instances from set B using the 
			// weight vector to train the weak learner
			double epsilon;
			InstanceList roundTrainingInsts = new InstanceList(trainingInsts.getPipe());
			int resamplingIterations = 0;
			do {
				epsilon = 0;
				int[] sampleIndices = sampleWithWeights(instIndices, weights, random);
        roundTrainingInsts = new InstanceList(trainingInsts.getPipe(), sampleIndices.length);
				for (int i = 0; i < sampleIndices.length; i++) {
					Instance inst = trainingInsts.get(sampleIndices[i]);
					roundTrainingInsts.add(inst, 1);
				}
				weakLearners[round] = weakLearner.train(roundTrainingInsts);
				// Calculate the pseudo-loss of weak learner
				for (int i = 0; i < trainingInsts.size(); i++) {
					Instance inst = trainingInsts.get(i);
					Classification c = weakLearners[round].classify(inst);
					double htCorrect = c.valueOfCorrectLabel();
					double htWrong = c.getLabeling().value(classIndices[i]);
					epsilon += weights[i] * (1 - htCorrect + htWrong);
					exponents[i] = 1 + htCorrect - htWrong;
				}
				epsilon *= 0.5;
				resamplingIterations++;
			}
			while (Maths.almostEquals(epsilon, 0) && resamplingIterations < MAX_NUM_RESAMPLING_ITERATIONS);
			// Stop boosting when pseudo-loss is 0, ignoring 
			// weak classifier trained this round
			if (Maths.almostEquals(epsilon, 0)) {
				logger.info("AdaBoostM2Trainer stopped at " + (round+1) + " / " 
						+ numRounds + " pseudo-loss=" + epsilon);
				// If we are in the first round, have to use the weak classifier in any case
				int numClassifiersToUse = (round == 0) ? 1 : round;
				if (round == 0)
					classifierWeights[0] = 1;
				double[] classifierWeights2 = new double[numClassifiersToUse];
				Classifier[] weakLearners2 = new Classifier[numClassifiersToUse];
				System.arraycopy(classifierWeights, 0, classifierWeights2, 0, numClassifiersToUse);
				System.arraycopy(weakLearners, 0, weakLearners2, 0, numClassifiersToUse);
				for (int i = 0; i < classifierWeights2.length; i++) {
					logger.info("AdaBoostM2Trainer weight[weakLearner[" + i + "]]=" 
							+ classifierWeights2[i]);
				}
				this.classifier = new AdaBoostM2 (trainingInsts.getPipe(), weakLearners2, classifierWeights2);
				return this.classifier;
			}
			double beta = epsilon / (1 - epsilon);
			classifierWeights[round] = Math.log(1.0 / beta);
			// Update and normalize weights
			double sum = 0;
			for (int i = 0; i < weights.length; i++) {
				weights[i] *= Math.pow(beta, 0.5 * exponents[i]);
				sum += weights[i];
			}
			MatrixOps.timesEquals(weights, 1.0 / sum);
			logger.info("===========  AdaBoostM2Trainer round " + (round+1) 
					+ " finished, pseudo-loss = " + epsilon);
		}
		for (int i = 0; i < classifierWeights.length; i++)
			logger.info("AdaBoostM2Trainer weight[weakLearner[" + i + "]]=" + classifierWeights[i]);
		this.classifier = new AdaBoostM2 (trainingInsts.getPipe(), weakLearners, classifierWeights);
		return classifier;
	}


	// returns an array of ints of the same size as data,
	// where the the samples are randomly chosen from data
	// using the distribution of the weights vector
	private int[] sampleWithWeights(int[] data, double[] weights, java.util.Random random) {
		if (weights.length != data.length)
			throw new IllegalArgumentException("length of weight vector must equal number of data points");	        
		double sumOfWeights = 0;
		for (int i = 0; i < data.length; i++) {
			if (weights[i] < 0)
				throw new IllegalArgumentException("weight vector must be non-negative");
			sumOfWeights += weights[i];
		}
		if (sumOfWeights <= 0)
			throw new IllegalArgumentException("weights must sum to positive value");
		int[] sample = new int[data.length];
		double[] probabilities = new double[data.length];
		double sumProbs = 0;
		for (int i = 0; i < data.length; i++) {
			sumProbs += random.nextDouble();
			probabilities[i] = sumProbs;
		}
		MatrixOps.timesEquals(probabilities, sumOfWeights / sumProbs);
		// make sure rounding didn't mess things up
		probabilities[data.length - 1] = sumOfWeights;
		// do sampling
		int a = 0; int b = 0; sumProbs = 0;
		while (a < data.length && b < data.length) {
			sumProbs += weights[b];	  
			while (a < data.length && probabilities[a] <= sumProbs) {
				sample[a] = data[b];
				a++;
			}
			b++;
		}        
		return sample;        
	}
}
