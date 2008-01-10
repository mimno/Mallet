/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.classify;

import java.util.ArrayList;

import cc.mallet.classify.Classifier;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;
import cc.mallet.types.LabelVector;
import cc.mallet.util.ArrayListUtils;

public class ConfidencePredictingClassifier extends Classifier
{
	Classifier underlyingClassifier;
	Classifier confidencePredictingClassifier;
	double totalCorrect;
	double totalIncorrect;
	double	totalIncorrectIncorrect;
	double	totalIncorrectCorrect;
	int numCorrectInstances;
	int numIncorrectInstances;
	int numConfidenceCorrect;
	int numFalsePositive;
	int numFalseNegative;
	
	public ConfidencePredictingClassifier (Classifier underlyingClassifier, Classifier confidencePredictingClassifier)
	{
		super (underlyingClassifier.getInstancePipe());
		this.underlyingClassifier = underlyingClassifier;
		this.confidencePredictingClassifier = confidencePredictingClassifier;
		// for testing confidence accuracy
		totalCorrect = 0.0;
		totalIncorrect = 0.0;
		totalIncorrectIncorrect = 0.0;
		totalIncorrectCorrect = 0.0;
		numCorrectInstances = 0;
		numIncorrectInstances = 0;
		numConfidenceCorrect = 0;
		numFalsePositive = 0;
		 numFalseNegative = 0;

	}

	public Classification classify (Instance instance)
	{
		Classification c = underlyingClassifier.classify (instance);
		Classification cpc = confidencePredictingClassifier.classify (c);
		LabelVector lv = c.getLabelVector();
		int bestIndex = lv.getBestIndex();
		double [] values = new double[lv.numLocations()];
		//// Put score of "correct" into score of the winning class...
		// xxx Can't set lv - it's immutable.
		//     Must create copy and new classification object
		// lv.set (bestIndex, cpc.getLabelVector().value("correct"));
		//for (int i = 0; i < lv.numLocations(); i++)
		//	if (i != bestIndex)
		//		lv.set (i, 0.0);

		// Put score of "correct" in winning class and
		// set rest to 0
		for (int i = 0; i < lv.numLocations(); i++) {
			if (i != bestIndex)
				values[i] = 0.0;
			else values[i] = cpc.getLabelVector().value("correct");
		}
		//return c;
		
		if(c.bestLabelIsCorrect()){
			numCorrectInstances++;
			totalCorrect+=cpc.getLabelVector().value("correct");
			totalIncorrectCorrect+=cpc.getLabelVector().value("incorrect");
			String correct = new String("correct");
			if(correct.equals(cpc.getLabelVector().getBestLabel().toString()))
				numConfidenceCorrect++;
			else numFalseNegative++;
		}
		
		else{
			numIncorrectInstances++;
			totalIncorrect+=cpc.getLabelVector().value("correct");
			totalIncorrectIncorrect+=cpc.getLabelVector().value("incorrect");
			if((new String("incorrect")).equals(cpc.getLabelVector().getBestLabel().toString())) 
				numConfidenceCorrect++;
			else numFalsePositive++;
		}
		
		return new Classification(instance, this, new LabelVector(lv.getLabelAlphabet(), values));
//		return cpc;
	}
	
	public void printAverageScores() {
			System.out.println("Mean score of correct for correct instances = " + meanCorrect());
			System.out.println("Mean score of correct for incorrect instances = " + meanIncorrect());
			System.out.println("Mean score of incorrect for correct instances = " +
												 this.totalIncorrectCorrect/this.numCorrectInstances);
			System.out.println("Mean score of incorrect for incorrect instances = " +
												 this.totalIncorrectIncorrect/this.numIncorrectInstances);
	}

	public void printConfidenceAccuracy() {
		System.out.println("Confidence predicting accuracy = " +
											 ((double)numConfidenceCorrect/(numIncorrectInstances + numCorrectInstances))+ " false negatives: "+ numFalseNegative + "/"+numCorrectInstances + " false positives: "+ numFalsePositive +" / " +numIncorrectInstances);
	}
	public double meanCorrect()
	{
		if(this.numCorrectInstances==0)
			return 0.0;
		return (this.totalCorrect/(double)this.numCorrectInstances);
	}

	public double meanIncorrect()
	{
		if(this.numIncorrectInstances==0)
			return 0.0;
		return (this.totalIncorrect/(double)this.numIncorrectInstances);
	}

}

