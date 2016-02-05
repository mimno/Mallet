/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */



/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.classify;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelVector;

/** 
 * Classification methods of Winnow2 algorithm.
 * @see WinnowTrainer
 */
public class Winnow extends Classifier{
	/**
	 *array of weights, one for each feature, initialized to 1
	 */
	double [][] weights;
	/**
	 *threshold for sum of wi*xi in formulating guess 
	 */
	double theta;
	
	/**
	 * Passes along data pipe and weights from 
	 * {@link #WinnowTrainer WinnowTrainer}
	 * @param dataPipe needed for dictionary, labels, feature vectors, etc
	 * @param newWeights weights calculated during training phase
	 * @param theta value used for threshold
	 * @param idim i dimension of weights array
	 * @param jdim j dimension of weights array
	 */
	public Winnow (Pipe dataPipe,
								 double [][]newWeights, double theta, 
								 int idim, int jdim){
		super (dataPipe);
		this.theta = theta;
		this.weights = new double[idim][jdim];
		for(int i=0; i<idim; i++)
	    for(int j=0; j<jdim; j++)
				this.weights[i][j] = newWeights[i][j];
	}
	
	/**
	 * Classifies an instance using Winnow's weights
	 * @param instance an instance to be classified
	 * @return an object containing the classifier's guess
     */
	public Classification classify (Instance instance){
		int numClasses = getLabelAlphabet().size();
		double[] scores = new double[numClasses];
		FeatureVector fv = (FeatureVector) instance.getData ();
		// Make sure the feature vector's feature dictionary matches
		// what we are expecting from our data pipe (and thus our notion
		// of feature probabilities.
		assert (instancePipe == null || fv.getAlphabet () == this.instancePipe.getDataAlphabet ());
		int fvisize = fv.numLocations();
		
		// Set the scores by summing wi*xi
		for (int fvi = 0; fvi < fvisize; fvi++) {
			int fi = fv.indexAtLocation (fvi);
			for (int ci = 0; ci < numClasses; ci++)
		    scores[ci] += this.weights[ci][fi];
		}
		
		
		// Create and return a Classification object
		return new Classification (instance, this,
															 new LabelVector (getLabelAlphabet(),
																								scores));
	}		
}

