/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.util.logging.*;
import java.util.*;
import java.io.*;

import cc.mallet.classify.Classifier;
import cc.mallet.optimize.ConjugateGradient;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.optimize.OrthantWiseLimitedMemoryBFGS;
import cc.mallet.optimize.tests.*;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.ExpGain;
import cc.mallet.types.FeatureInducer;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.GradientGain;
import cc.mallet.types.InfoGain;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Labeling;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.RankedFeatureVector;
import cc.mallet.types.Vector;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.MalletProgressMessageLogger;
import cc.mallet.util.Maths;

//Does not currently handle instances that are labeled with distributions
//instead of a single label.
/**
 * The trainer for a Maximum Entropy classifier.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class MaxEntTrainer extends ClassifierTrainer<MaxEnt>
	implements ClassifierTrainer.ByOptimization<MaxEnt>, Boostable, Serializable {

	private static Logger logger = MalletLogger.getLogger(MaxEntTrainer.class.getName());
	private static Logger progressLogger = MalletProgressMessageLogger.getLogger(MaxEntTrainer.class.getName()+"-pl");

	int numIterations = Integer.MAX_VALUE;

	public static final String EXP_GAIN = "exp";
	public static final String GRADIENT_GAIN = "grad";
	public static final String INFORMATION_GAIN = "info";

	// xxx Why does TestMaximizable fail when this variance is very small?
	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1;
	static final double DEFAULT_L1_WEIGHT = 0.0;
	static final Class DEFAULT_MAXIMIZER_CLASS = LimitedMemoryBFGS.class;

	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double l1Weight = DEFAULT_L1_WEIGHT;

	Class maximizerClass = DEFAULT_MAXIMIZER_CLASS;

	InstanceList trainingSet = null;
	MaxEnt initialClassifier;

	MaxEntOptimizableByLabelLikelihood optimizable = null;
	Optimizer optimizer = null;

	// 
	// CONSTRUCTORS
	//

	public MaxEntTrainer () {}

	/** Construct a MaxEnt trainer using a trained classifier as
	 *   initial values.
	 */ 
	public MaxEntTrainer (MaxEnt theClassifierToTrain) {
		this.initialClassifier = theClassifierToTrain;
	}

	/** Constructs a trainer with a parameter to avoid overtraining.  1.0 is
	 * the default value. */
	public MaxEntTrainer (double gaussianPriorVariance) {
		this.gaussianPriorVariance = gaussianPriorVariance;
	}

	//
	//  CLASSIFIER OBJECT: stores parameters
	// 

	public MaxEnt getClassifier () {
		if (optimizable != null)
			return optimizable.getClassifier();
		return initialClassifier;
	}

	/**
	 *  Initialize parameters using the provided classifier. 
	 */
	public void setClassifier (MaxEnt theClassifierToTrain) {
		// Is this necessary?  What is the caller is about to set the training set to something different? -akm
		assert (trainingSet == null || Alphabet.alphabetsMatch(theClassifierToTrain, trainingSet));
		if (this.initialClassifier != theClassifierToTrain) {
			this.initialClassifier = theClassifierToTrain;
			optimizable = null;
			optimizer = null;
		}
	}

	//
	//  OPTIMIZABLE OBJECT: implements value and gradient functions
	//

	public Optimizable getOptimizable () {
		return optimizable;
	}

	public MaxEntOptimizableByLabelLikelihood getOptimizable (InstanceList trainingSet) {
		return getOptimizable(trainingSet, getClassifier());
	}

	public MaxEntOptimizableByLabelLikelihood getOptimizable (InstanceList trainingSet, MaxEnt initialClassifier) {

		if (trainingSet != this.trainingSet || this.initialClassifier != initialClassifier) {

			this.trainingSet = trainingSet;
			this.initialClassifier = initialClassifier;

			if (optimizable == null || optimizable.trainingList != trainingSet) {
				optimizable = new MaxEntOptimizableByLabelLikelihood (trainingSet, initialClassifier);

				if (l1Weight == 0.0) {
					optimizable.setGaussianPriorVariance(gaussianPriorVariance);
				}
				else {
					// the prior term for L1-regularized classifiers 
					//  is implemented as part of the optimizer, 
					//  so don't include a prior calculation in the value and 
					//  gradient functions.
					optimizable.useNoPrior();
				}

				optimizer = null;
			}
		}

		return optimizable;
	}

	//
	//  OPTIMIZER OBJECT: maximizes value function
	//

	public Optimizer getOptimizer () {
		if (optimizer == null && optimizable != null) {
			optimizer = new ConjugateGradient(optimizable);
		}

		return optimizer;
	}

	/** This method is called by the train method. 
	 *   This is the main entry point for the optimizable and optimizer
	 *   compontents.
	 */
	public Optimizer getOptimizer (InstanceList trainingSet) {

		// If the data is not set, or has changed, 
		//  initialize the optimizable object and 
		//  replace the optimizer.
		if (trainingSet != this.trainingSet ||
			optimizable == null) {

			getOptimizable(trainingSet);
			optimizer = null;
		}

		// Build a new optimizer
		if (optimizer == null) {
			// If l1Weight is 0, this devolves to 
			//  standard L-BFGS, but the implementation
			//  may be faster.
			optimizer = new LimitedMemoryBFGS(optimizable); 
			//OrthantWiseLimitedMemoryBFGS(optimizable, l1Weight);
		}
		return optimizer;
	}

	/**
	 * Specifies the maximum number of iterations to run during a single call
	 * to <code>train</code> or <code>trainWithFeatureInduction</code>.  Not
	 * currently functional.
	 * @return This trainer
	 */
	// XXX Since we maximize before using numIterations, this doesn't work.
	// Is that a bug?  If so, should the default numIterations be higher?
	public MaxEntTrainer setNumIterations (int i) {
		numIterations = i;
		return this;
	}

	public int getIteration () {
		if (optimizable == null)
			return 0;
		else
		  return Integer.MAX_VALUE;
//			return optimizer.getIteration ();
	}

	/**
	 * Sets a parameter to prevent overtraining.  A smaller variance for the prior
	 * means that feature weights are expected to hover closer to 0, so extra
	 * evidence is required to set a higher weight.
	 * @return This trainer
	 */
	public MaxEntTrainer setGaussianPriorVariance (double gaussianPriorVariance) {
		this.gaussianPriorVariance = gaussianPriorVariance;
		return this;
	}

	/** 
	 *  Use an L1 prior. Larger values mean parameters will be closer to 0.
	 *   Note that this setting overrides any Gaussian prior.
	 */
	public MaxEntTrainer setL1Weight(double l1Weight) {
		this.l1Weight = l1Weight;
		return this;
	}

	public MaxEnt train (InstanceList trainingSet) {
		return train (trainingSet, numIterations);
	}

	public MaxEnt train (InstanceList trainingSet, int numIterations)
	{
		logger.fine ("trainingSet.size() = "+trainingSet.size());
		boolean converged;
		getOptimizer (trainingSet);  // This will set this.optimizer, this.optimizable

		for (int i = 0; i < numIterations; i++) {
			try {
				converged = optimizer.optimize (1);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				logger.info ("Catching exception; saying converged.");
				converged = true;
			}
			if (converged)
				break;
		}

		if (numIterations == Integer.MAX_VALUE) {
			// Run it again because in our and Sam Roweis' experience, BFGS can still
			// eke out more likelihood after first convergence by re-running without
			// being restricted by its gradient history.
			optimizer = null;
			getOptimizer(trainingSet);
			try {
				optimizer.optimize ();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				logger.info ("Catching exception; saying converged.");
			}
		}
		//TestMaximizable.testValueAndGradientCurrentParameters (mt);
		progressLogger.info("\n"); //  progress messages are on one line; move on.
		//logger.info("MaxEnt ngetValueCalls:"+getValueCalls()+"\nMaxEnt ngetValueGradientCalls:"+getValueGradientCalls());
		return optimizable.getClassifier();
	}

	/**
	 * <p>Trains a maximum entropy model using feature selection and feature induction
	 * (adding conjunctions of features as new features).</p>
	 *
	 * @param trainingData A list of <code>Instance</code>s whose <code>data</code>
	 * fields are binary, augmentable <code>FeatureVector</code>s.
	 * and whose <code>target</code> fields are <code>Label</code>s.
	 * @param validationData [not currently used] As <code>trainingData</code>,
	 * or <code>null</code>.
	 * @param testingData As <code>trainingData</code>, or <code>null</code>.
	 * @param evaluator The evaluator to track training progress and decide whether
	 * to continue, or <code>null</code>.
	 * @param totalIterations The maximum total number of training iterations,
	 * including those taken during feature induction.
	 * @param numIterationsBetweenFeatureInductions How many iterations to train
	 * between one round of feature induction and the next; this should usually
	 * be fairly small, like 5 or 10, to avoid overfitting with current features.
	 * @param numFeatureInductions How many rounds of feature induction to run
	 * before beginning normal training.
	 * @param numFeaturesPerFeatureInduction The maximum number of features to
	 * choose during each round of featureInduction.
	 *
	 * @return The trained <code>MaxEnt</code> classifier
	 */
	/*
	// added - cjmaloof@linc.cis.upenn.edu
	public Classifier trainWithFeatureInduction (InstanceList trainingData,
			int totalIterations,
			int numIterationsBetweenFeatureInductions,
			int numFeatureInductions,
			int numFeaturesPerFeatureInduction) {

		return trainWithFeatureInduction (trainingData,
				null,
				totalIterations,
				numIterationsBetweenFeatureInductions,
				numFeatureInductions,
				numFeaturesPerFeatureInduction,
				EXP_GAIN);
	}
	*/

	/**
	 * <p>Like the other version of <code>trainWithFeatureInduction</code>, but
	 * allows some default options to be changed.</p>
	 *
	 * @param maxent An initial partially-trained classifier (default <code>null</code>).
	 * This classifier may be modified during training.
	 * @param gainName The estimate of gain (log-likelihood increase) we want our chosen
	 * features to maximize.
	 * Should be one of <code>MaxEntTrainer.EXP_GAIN</code>,
	 * <code>MaxEntTrainer.GRADIENT_GAIN</code>, or
	 * <code>MaxEntTrainer.INFORMATION_GAIN</code> (default <code>EXP_GAIN</code>).
	 *
	 * @return The trained <code>MaxEnt</code> classifier
	 */
	/* // Temporarily removed until I figure out how to handle induceFeaturesFor (testData)
	public Classifier trainWithFeatureInduction (InstanceList trainingData,
			int totalIterations,
			int numIterationsBetweenFeatureInductions,
			int numFeatureInductions,
			int numFeaturesPerFeatureInduction,
			String gainName) {

		// XXX This ought to be a parameter, except that setting it to true can
		// crash training ("Jump too small").
		boolean saveParametersDuringFI = false;
		Alphabet inputAlphabet = trainingData.getDataAlphabet();
		Alphabet outputAlphabet = trainingData.getTargetAlphabet();
		int trainingIteration = 0;
		int numLabels = outputAlphabet.size();
		MaxEnt maxent = getClassifier();

		// Initialize feature selection
		FeatureSelection globalFS = trainingData.getFeatureSelection();
		if (globalFS == null) {
			// Mask out all features; some will be added later by FeatureInducer.induceFeaturesFor(.)
			globalFS = new FeatureSelection (trainingData.getDataAlphabet());
			trainingData.setFeatureSelection (globalFS);
		}
		//if (validationData != null) validationData.setFeatureSelection (globalFS);
		//if (testingData != null) testingData.setFeatureSelection (globalFS);
		getOptimizer(trainingData); // This will initialize this.me so getClassifier() below works
		maxent.setFeatureSelection(globalFS);

		// Run feature induction
		for (int featureInductionIteration = 0;	featureInductionIteration < numFeatureInductions;	featureInductionIteration++) {

			// Print out some feature information
			logger.info ("Feature induction iteration "+featureInductionIteration);

			// Train the model a little bit.  We don't care whether it converges; we
			// execute all feature induction iterations no matter what.
			if (featureInductionIteration != 0) {
				// Don't train until we have added some features
				setNumIterations(numIterationsBetweenFeatureInductions);
				train (trainingData);
			}
			trainingIteration += numIterationsBetweenFeatureInductions;

			logger.info ("Starting feature induction with "+(1+inputAlphabet.size())+
					" features over "+numLabels+" labels.");

			// Create the list of error tokens
			InstanceList errorInstances = new InstanceList (trainingData.getDataAlphabet(),
					trainingData.getTargetAlphabet());

			// This errorInstances.featureSelection will get examined by FeatureInducer,
			// so it can know how to add "new" singleton features
			errorInstances.setFeatureSelection (globalFS);
			List errorLabelVectors = new ArrayList();    // these are length-1 vectors
			for (int i = 0; i < trainingData.size(); i++) {
				Instance instance = trainingData.get(i);
				FeatureVector inputVector = (FeatureVector) instance.getData();
				Label trueLabel = (Label) instance.getTarget();

				// Having trained using just the current features, see how we classify
				// the training data now.
				Classification classification = maxent.classify(instance);
				if (!classification.bestLabelIsCorrect()) {
					errorInstances.add(inputVector, trueLabel, null, null);
					errorLabelVectors.add(classification.getLabelVector());
				}
			}
			logger.info ("Error instance list size = "+errorInstances.size());
			int s = errorLabelVectors.size();

			LabelVector[] lvs = new LabelVector[s];
			for (int i = 0; i < s; i++) {
				lvs[i] = (LabelVector)errorLabelVectors.get(i);
			}

			RankedFeatureVector.Factory gainFactory = null;
			if (gainName.equals (EXP_GAIN))
				gainFactory = new ExpGain.Factory (lvs, gaussianPriorVariance);
			else if (gainName.equals(GRADIENT_GAIN))
				gainFactory =	new GradientGain.Factory (lvs);
			else if (gainName.equals(INFORMATION_GAIN))
				gainFactory =	new InfoGain.Factory ();
			else
				throw new IllegalArgumentException("Unsupported gain name: "+gainName);

			FeatureInducer klfi =
				new FeatureInducer (gainFactory,
						errorInstances,
						numFeaturesPerFeatureInduction,
						2*numFeaturesPerFeatureInduction,
						2*numFeaturesPerFeatureInduction);

			// Note that this adds features globally, but not on a per-transition basis
			klfi.induceFeaturesFor (trainingData, false, false);
			if (testingData != null) klfi.induceFeaturesFor (testingData, false, false);
			logger.info ("MaxEnt FeatureSelection now includes "+globalFS.cardinality()+" features");
			klfi = null;

			double[] newParameters = new double[(1+inputAlphabet.size()) * outputAlphabet.size()];

			// XXX (Executing this block often causes an error during training; I don't know why.)
			if (saveParametersDuringFI) {
				// Keep current parameter values
				// XXX This relies on the implementation detail that the most recent features
				// added to an Alphabet get the highest indices.

				// Count parameters per output label
				int oldParamCount = maxent.parameters.length / outputAlphabet.size();
				int newParamCount = 1+inputAlphabet.size();
				// Copy params into the proper locations
				for (int i=0; i<outputAlphabet.size(); i++) {
					System.arraycopy(maxent.parameters, i*oldParamCount,
							newParameters, i*newParamCount,
							oldParamCount);
				}
				for (int i=0; i<oldParamCount; i++)
					if (maxent.parameters[i] != newParameters[i]) {
						System.out.println(maxent.parameters[i]+" "+newParameters[i]);
						System.exit(0);
					}
			}

			maxent.parameters = newParameters;
			maxent.defaultFeatureIndex = inputAlphabet.size();
		}

		// Finished feature induction
		logger.info("Ended with "+globalFS.cardinality()+" features.");
		setNumIterations(totalIterations - trainingIteration);
		train (trainingData);
		return maxent;
	}
*/


	public String toString() {
		StringBuilder builder = new StringBuilder();

		builder.append("MaxEntTrainer");
		if (numIterations < Integer.MAX_VALUE) {
			builder.append(",numIterations=" + numIterations);
		}
		if (l1Weight != 0.0) {
			builder.append(",l1Weight=" + l1Weight);
		}
		else {
			builder.append(",gaussianPriorVariance=" + gaussianPriorVariance);
		}

		return builder.toString();
	}
}
