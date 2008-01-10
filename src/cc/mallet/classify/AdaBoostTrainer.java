/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.util.Random;
import java.util.logging.*;

import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;

/**
 * This version of AdaBoost should be used only for binary classification.
 * Use AdaBoost.M2 for multi-class problems.
 *
 * <p>Robert E. Schapire.
 * "A decision-theoretic generalization of on-line learning and 
 * an application to boosting"
 * In Journal of Computer and System Sciences 
 * http://www.cs.princeton.edu/~schapire/uncompress-papers.cgi/FreundSc95.ps
 *
 * @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class AdaBoostTrainer extends ClassifierTrainer<AdaBoost>
{
	private static Logger logger = MalletLogger.getLogger(AdaBoostTrainer.class.getName());
	private static int MAX_NUM_RESAMPLING_ITERATIONS = 10;

	ClassifierTrainer weakLearner;
	int numRounds;
	AdaBoost classifier;
	public AdaBoost getClassifier () { return classifier; }

	public AdaBoostTrainer (ClassifierTrainer weakLearner, int numRounds)
	{
		if (! (weakLearner instanceof Boostable))
			throw new IllegalArgumentException ("weak learner not boostable");
		if (numRounds <= 0)
			throw new IllegalArgumentException ("number of rounds must be positive");
		this.weakLearner = weakLearner;
		this.numRounds = numRounds;
	}

	public AdaBoostTrainer (ClassifierTrainer weakLearner)
	{
		this (weakLearner, 100);
	}

	/**
	 * Boosting method that resamples instances using their weights
	 */    
	public AdaBoost train (InstanceList trainingList)
	{
		FeatureSelection selectedFeatures = trainingList.getFeatureSelection();
		if (selectedFeatures != null)
			throw new UnsupportedOperationException("FeatureSelection not yet implemented.");

		java.util.Random random = new java.util.Random();
		// Set the initial weights to be uniform
		double w = 1.0 / trainingList.size();
		InstanceList trainingInsts = new InstanceList();
		for (int i = 0; i < trainingList.size(); i++)
			trainingInsts.add(trainingList.get(i), w);

		boolean[] correct = new boolean[trainingInsts.size()];
		int numClasses = trainingInsts.getTargetAlphabet().size();
		if (numClasses != 2)
			logger.info("AdaBoostTrainer.train: WARNING: more than two classes");
		Classifier[] weakLearners = new Classifier[numRounds];
		double[] alphas = new double[numRounds];
		InstanceList roundTrainingInsts = new InstanceList();

		// Boosting iterations
		for (int round = 0; round < numRounds; round++) {
			logger.info("===========  AdaBoostTrainer round " + (round+1) + " begin");
			// Keep resampling the training instances (using the distribution
			// of instance weights) on which to train the weak learner until
			// either we exceed the preset number of maximum iterations, or
			// the weak learner makes a non-zero error on trainingInsts
			// (this makes sure we sample at least some 'hard' instances).
			int resamplingIterations = 0;
			double err;
			do {
				err = 0;
				roundTrainingInsts = trainingInsts.sampleWithInstanceWeights(random);
				weakLearners[round] = weakLearner.train (roundTrainingInsts);

				// Calculate error
				for (int i = 0; i < trainingInsts.size(); i++) {
					Instance inst = trainingInsts.get(i);
					if (weakLearners[round].classify(inst).bestLabelIsCorrect())
						correct[i] = true;
					else {
						correct[i] = false;
						err += trainingInsts.getInstanceWeight(i);
					}
				}
				resamplingIterations++;
			}
			while (Maths.almostEquals(err, 0) && resamplingIterations < MAX_NUM_RESAMPLING_ITERATIONS);

			// Stop boosting when error is too big or 0,
			// ignoring weak classifier trained this round
			if (Maths.almostEquals(err, 0) || err > 0.5) {
				logger.info("AdaBoostTrainer stopped at " + (round+1) + " / " 
						+ numRounds + " rounds: numClasses=" 
						+ numClasses + " error=" + err);
				// If we are in the first round, have to use the weak classifier in any case
				int numClassifiersToUse = (round == 0) ? 1 : round;
				if (round == 0)
					alphas[0] = 1;
				double[] betas = new double[numClassifiersToUse];
				Classifier[] weakClassifiers = new Classifier[numClassifiersToUse];
				System.arraycopy(alphas, 0, betas, 0, numClassifiersToUse);
				System.arraycopy(weakLearners, 0, weakClassifiers, 0, numClassifiersToUse);
				for (int i = 0; i < betas.length; i++)
					logger.info("AdaBoostTrainer weight[weakLearner[" + i + "]]=" + betas[i]);

				return new AdaBoost (roundTrainingInsts.getPipe(), weakClassifiers, betas);
			}

			// Calculate the weight to assign to this weak classifier
			// This formula is really designed for binary classifiers that don't
			// give a confidence score.  Use AdaBoostMH for multi-class or 
			// multi-labeled data.
			alphas[round] = Math.log((1 - err) / err);
			double reweightFactor = err / (1 - err);
			double sum = 0;
			// Decrease weights of correctly classified instances
			for (int i = 0; i < trainingInsts.size(); i++) {
				w = trainingInsts.getInstanceWeight(i);
				if (correct[i])
					w *= reweightFactor;
				trainingInsts.setInstanceWeight (i, w);
				sum += w;
			}
			// Normalize the instance weights
			for (int i = 0; i < trainingInsts.size(); i++) {
				trainingInsts.setInstanceWeight (i, trainingInsts.getInstanceWeight(i) / sum);
			}
			logger.info("===========  AdaBoostTrainer round " + (round+1) 
					+ " finished, weak classifier training error = " + err);
		}
		for (int i = 0; i < alphas.length; i++)
			logger.info("AdaBoostTrainer weight[weakLearner[" + i + "]]=" + alphas[i]);
		this.classifier = new AdaBoost (roundTrainingInsts.getPipe(), weakLearners, alphas);
		return classifier;
	}


}
