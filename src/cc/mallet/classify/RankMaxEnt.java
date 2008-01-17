/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify;
//package edu.umass.cs.mallet.users.culotta.cluster.classify;

//import edu.umass.cs.mallet.base.classify.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.DenseVector;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;


/**
 * Rank Maximum Entropy classifier. This classifier chooses among a set of
 * Instances with binary labels. Expects Instance data to be a
 * FeatureVectorSequence, and the target to be a String representation of the
 * index of the true best FeatureVectorSequence. Note that the Instance target
 * may be a Labels to indicate a tie for the best Instance.
 * 
 * @author Aron Culotta <a
 *         href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

public class RankMaxEnt extends MaxEnt
{
	
	// The default feature is always the feature with highest index
	public RankMaxEnt (Pipe dataPipe,
										 double[] parameters,
										 FeatureSelection featureSelection,
										 FeatureSelection[] perClassFeatureSelection)
	{
		super (dataPipe, parameters, featureSelection, perClassFeatureSelection);
	}
	
	public RankMaxEnt (Pipe dataPipe,
										 double[] parameters,
										 FeatureSelection featureSelection)	{
		this (dataPipe, parameters, featureSelection, null);
	}
	
	public RankMaxEnt (Pipe dataPipe,
															double[] parameters,
										 FeatureSelection[] perClassFeatureSelection)
	{
		this (dataPipe, parameters, null, perClassFeatureSelection);		
	}
	
	public RankMaxEnt (Pipe dataPipe, double[] parameters)
	{
		this (dataPipe, parameters, null, null);
	}

	/** returns unnormalized scores, corresponding to the score an
	 * element of the InstanceList being the "top" instance
	 * @param instance instance with data field a {@link InstanceList}.
	 * @param scores has length = number of Instances in Instance.data,
	 * which is of type InstanceList */
	public void getUnnormalizedClassificationScores (Instance instance, double[] scores)
	{
		FeatureVectorSequence fvs = (FeatureVectorSequence)instance.getData();
		assert (scores.length == fvs.size());
		int numFeatures = instance.getDataAlphabet().size()+1;

		for (int instanceNumber=0; instanceNumber < fvs.size(); instanceNumber++) {
			FeatureVector fv = (FeatureVector)fvs.get(instanceNumber);
			// Make sure the feature vector's feature dictionary matches
			// what we are expecting from our data pipe (and thus our notion
			// of feature probabilities.
			assert (fv.getAlphabet ()
							== this.instancePipe.getDataAlphabet ());
			
			// Include the feature weights according to each label xxx is
			// this correct ? we only calculate the dot prod of the feature
			// vector with the "positiveLabel" weights
			// xxx include multiple labels
			scores[instanceNumber] = parameters[0*numFeatures + defaultFeatureIndex]
																 + MatrixOps.rowDotProduct (parameters, numFeatures,
																														0, fv,
																														defaultFeatureIndex,
																														(perClassFeatureSelection == null
																														 ? featureSelection
																														 : perClassFeatureSelection[0]));
		}
	}
	
	public void getClassificationScores (Instance instance, double[] scores)
	{
		FeatureVectorSequence fvs = (FeatureVectorSequence)instance.getData();
		int numFeatures = instance.getDataAlphabet().size()+1;
		int numLabels = fvs.size();
		assert (scores.length == fvs.size());

		for (int instanceNumber=0; instanceNumber < fvs.size(); instanceNumber++) {
			FeatureVector fv = (FeatureVector)fvs.get(instanceNumber);
			// Make sure the feature vector's feature dictionary matches
			// what we are expecting from our data pipe (and thus our notion
			// of feature probabilities.
			assert (fv.getAlphabet ()
							== this.instancePipe.getDataAlphabet ());
			
			// Include the feature weights according to each label
			scores[instanceNumber] = parameters[0*numFeatures + defaultFeatureIndex]
																 + MatrixOps.rowDotProduct (parameters, numFeatures,
																														0, fv,
																														defaultFeatureIndex,
																														(perClassFeatureSelection == null
																														 ? featureSelection
																														 : perClassFeatureSelection[0]));
		}
	
		// Move scores to a range where exp() is accurate, and normalize
		double max = MatrixOps.max (scores);
		double sum = 0;
		for (int li = 0; li < numLabels; li++)
			sum += (scores[li] = Math.exp (scores[li] - max));
		for (int li = 0; li < numLabels; li++) {
			scores[li] /= sum;
			// xxxNaN assert (!Double.isNaN(scores[li]));
		}
	}
	
	/**
	 * Used by RankMaxEntTrainer to calculate the value when the labeling contains ties. Does not include scores of tied elements in normalization.
	 * @param instance
	 * @param scores
	 * @param bestLabels Indices of Instances ties for 1st place.
	 */
	public void getClassificationScoresForTies (Instance instance, double[] scores, int[] bestLabels)
	{
		getClassificationScores(instance, scores);

		// Set all bestLabel probs to 0 except for first and renormalize
		for (int i = 1; i < bestLabels.length; i++) 
			scores[bestLabels[i]] = 0.0;
		double sum = 0.0;
		for (int li = 0; li < scores.length; li++) 
			sum += scores[li];
		for (int li = 0; li < scores.length; li++) 
			scores[li] /= sum;		
	}
	public Classification classify (Instance instance)
	{
		FeatureVectorSequence fvs = (FeatureVectorSequence) instance.getData();
		int numClasses = fvs.size();
		double[] scores = new double[numClasses];
		getClassificationScores (instance, scores);
		// Create and return a Classification object
		return new Classification (instance, this,
															 createLabelVector (getLabelAlphabet(),
																									scores));
	}
	
	/** Constructs a LabelVector which is a distribution over indices of
	 * the "positive" Instance. */
	private LabelVector createLabelVector (LabelAlphabet labelAlphabet, double[] scores) {
		if (labelAlphabet.growthStopped())
			labelAlphabet.startGrowth();
		
		for (int i=0; i < scores.length; i++) 
			labelAlphabet.lookupIndex(String.valueOf(i), true);

		double[] allScores = new double[labelAlphabet.size()];

		for (int i=0; i < labelAlphabet.size(); i++) 
			allScores[i] = 0.0;

		for (int i=0; i < scores.length; i++) {
			int index = labelAlphabet.lookupIndex(String.valueOf(i), true);
			allScores[index] = scores[i];
		}
		return new LabelVector(labelAlphabet, allScores);
	}
	
	public void print () 
	{		
		final Alphabet dict = getAlphabet();
		final LabelAlphabet labelDict = (LabelAlphabet)getLabelAlphabet();
		
		int numFeatures = dict.size() + 1;
		int numLabels = labelDict.size();
		
		// Include the feature weights according to each label
		//for (int li = 0; li < numLabels; li++) {
		System.out.println ("FEATURES FOR CLASS "+labelDict.lookupObject (0));
		System.out.println (" <default> "+parameters [defaultFeatureIndex]);
		for (int i = 0; i < defaultFeatureIndex; i++) {
			Object name = dict.lookupObject (i);
			double weight = parameters [i];
			System.out.println (" "+name+" "+weight);
		}		
	}	
	
	// SERIALIZATION

	  private static final long serialVersionUID = 1;
	  private static final int CURRENT_SERIAL_VERSION = 1;

	  private void writeObject (ObjectOutputStream out) throws IOException {
	    out.defaultWriteObject ();
	    out.writeInt (CURRENT_SERIAL_VERSION);
	  }

	  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
	    in.defaultReadObject ();
	    int version = in.readInt ();
	  }	
}

