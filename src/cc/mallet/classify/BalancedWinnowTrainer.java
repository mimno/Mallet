/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


package cc.mallet.classify;

import java.io.Serializable;
import java.util.Arrays;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Labeling;



/**
 * An implementation of the training methods of a BalancedWinnow
 * on-line classifier. Given a labeled instance (x, y) the algorithm 
 * computes dot(x, wi), for w1, ... , wc where wi is the weight 
 * vector for class i.  The instance is classified as class j
 * if the value of dot(x, wj) is the largest among the c dot 
 * products.
 *
 * <p>The weight vectors are updated whenever the the classifier 
 * makes a mistake or just barely got the correct answer (highest
 * dot product is within delta percent higher than the second highest).
 * Suppose the classifier guessed j and answer was j'. For each 
 * feature i that is present, multiply w_ji by (1-epsilon) and 
 * multiply w_j'i by (1+epsilon)
 *
 * <p>The above procedure is done multiple times to the training
 * examples (default is 5), and epsilon is cut by the cooling
 * rate at each iteration (default is cutting epsilon by half).
 *
 * @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class BalancedWinnowTrainer extends ClassifierTrainer<BalancedWinnow> implements Boostable, Serializable
{
	private static final long serialVersionUID = 1L;

	/**
	 * 0.5
	 */
	public static final double DEFAULT_EPSILON = .5;
	/**
	 * 0.1
	 */
	public static final double DEFAULT_DELTA = .1;
	/**
	 * 30
	 */
	public static final int DEFAULT_MAX_ITERATIONS = 30;
	/**
	 * 0.5
	 */
	public static final double DEFAULT_COOLING_RATE = .5;

	double m_epsilon;
	double m_delta;
	int m_maxIterations;
	double m_coolingRate;
	/**
	 * Array of weights, one for each class and feature, initialized to 1.
	 * For each class, there is an additional default "feature" weight
	 * that is set to 1 in every example (it remains constant; this is
	 * used to prevent the instance from having 0 dot product with a class).
	 */
	double[][] m_weights;
	
	BalancedWinnow classifier;
	public BalancedWinnow getClassifier () { return classifier; }


	/**
	 * Default constructor. Sets all features to defaults.
	 */
	public BalancedWinnowTrainer()
	{
		this(DEFAULT_EPSILON, DEFAULT_DELTA, DEFAULT_MAX_ITERATIONS,	DEFAULT_COOLING_RATE);
	}

	/**
	 * @param epsilon percentage by which to increase/decrease weight vectors
	 * when an example is misclassified.
	 * @param delta percentage by which the highest (and correct) dot product 
	 * should exceed the second highest dot product before we consider an example
	 * to be correctly classified (margin width) when adjusting weights.
	 * @param maxIterations maximum number of times to loop through training examples.
	 * @param coolingRate percentage of epsilon to decrease after each iteration
	 */
	public BalancedWinnowTrainer(double epsilon, 
			double delta, 
			int maxIterations,
			double coolingRate)
	{
		m_epsilon = epsilon;
		m_delta = delta;
		m_maxIterations = maxIterations;
		m_coolingRate = coolingRate;
	}

	/**
	 * Trains the classifier on the instance list, updating 
	 * class weight vectors as appropriate
	 * @param trainingList Instance list to be trained on
	 * @return Classifier object containing learned weights
	 */
	public BalancedWinnow train (InstanceList trainingList)
	{
		FeatureSelection selectedFeatures = trainingList.getFeatureSelection();
		if (selectedFeatures != null)
			// xxx Attend to FeatureSelection!!!
			throw new UnsupportedOperationException ("FeatureSelection not yet implemented.");

		double epsilon = m_epsilon;
		Alphabet dict = (Alphabet) trainingList.getDataAlphabet ();
		int numLabels = trainingList.getTargetAlphabet().size();
		int numFeats = dict.size();
		m_weights = new double [numLabels][numFeats+1];

		// init weights to 1
		for(int i = 0; i < numLabels; i++)
			Arrays.fill(m_weights[i], 1.0);

		// Loop through training instances multiple times
		double[] results = new double[numLabels];
		for (int iter = 0; iter < m_maxIterations; iter++) {

			// loop through all instances
			for (int ii = 0; ii < trainingList.size(); ii++) {
				Instance inst = trainingList.get(ii);
				Labeling labeling = inst.getLabeling ();
				FeatureVector fv = (FeatureVector) inst.getData();
				int fvisize = fv.numLocations();
				int correctIndex = labeling.getBestIndex();
				Arrays.fill(results, 0);

				// compute dot(x, wi) for each class i
				for(int lpos = 0; lpos < numLabels; lpos++) {
					for(int fvi = 0; fvi < fvisize; fvi++) {
						int fi = fv.indexAtLocation(fvi);
						double vi = fv.valueAtLocation(fvi);
						results[lpos] += vi * m_weights[lpos][fi];
					}

					// This extra value comes from the extra
					// "feature" present in all examples
					results[lpos] += m_weights[lpos][numFeats];
				}

				// Get indices of the classes with the 2 highest dot products
				int predictedIndex = 0;
				int secondHighestIndex = 0;
				double max = Double.MIN_VALUE;
				double secondMax = Double.MIN_VALUE;
				for (int i = 0; i < numLabels; i++) {
					if (results[i] > max) {
						secondMax = max;
						max = results[i];
						secondHighestIndex = predictedIndex;
						predictedIndex = i;
					}
					else if (results[i] > secondMax) {
						secondMax = results[i];
						secondHighestIndex = i;
					}
				}

				// Adjust weights if this example is mispredicted
				// or just barely correct
				if (predictedIndex != correctIndex) {
					for (int fvi = 0; fvi < fvisize; fvi++) {
						int fi = fv.indexAtLocation(fvi);
						m_weights[predictedIndex][fi] *= (1 - epsilon);
						m_weights[correctIndex][fi] *= (1 + epsilon);
					}
					m_weights[predictedIndex][numFeats] *= (1 - epsilon);
					m_weights[correctIndex][numFeats] *= (1 + epsilon);
				}
				else if (max/secondMax - 1 < m_delta) {
					for (int fvi = 0; fvi < fvisize; fvi++) {
						int fi = fv.indexAtLocation(fvi);
						m_weights[secondHighestIndex][fi] *= (1 - epsilon);
						m_weights[correctIndex][fi] *= (1 + epsilon);
					}
					m_weights[secondHighestIndex][numFeats] *= (1 - epsilon);
					m_weights[correctIndex][numFeats] *= (1 + epsilon);
				}
			}
			// Cut epsilon by the cooling rate
			epsilon *= (1-m_coolingRate);
		}        
		this.classifier = new BalancedWinnow (trainingList.getPipe(), m_weights);
		return classifier;
	}

}

