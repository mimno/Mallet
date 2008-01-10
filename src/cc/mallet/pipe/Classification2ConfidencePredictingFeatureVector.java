/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.pipe;

import java.util.ArrayList;
import java.util.logging.*;

import cc.mallet.classify.*;
import cc.mallet.classify.evaluate.*;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;
import cc.mallet.util.PropertyList;

  /** Pipe features from underlying classifier to
   * the confidence prediction instance list
   */
public class Classification2ConfidencePredictingFeatureVector extends Pipe
{
	public Classification2ConfidencePredictingFeatureVector ()
	{
		super (new Alphabet(), new LabelAlphabet());
	}
	
	public Instance pipe (Instance carrier)
	{
		Classification classification = (Classification) carrier.getData();
		PropertyList features = null;
		LabelVector lv = classification.getLabelVector();
		Label bestLabel = lv.getBestLabel();
		Instance inst = (Instance)classification.getInstance();
		FeatureVector fv = (FeatureVector)inst.getData();
		Alphabet fdict = fv.getAlphabet();
		
		double winningThreshold = .990;
		double varianceThreshold = .15;
		double secondThreshold = .03;
		
		double winningScore = lv.getValueAtRank(0);
		double marginOfVictory = winningScore - lv.getValueAtRank(1);
		
		// attempts to use the confusion matrix of the training list
		// as some prior knowledge in training
		
		features = PropertyList.add ("winningScore", winningScore, features);
		features = PropertyList.add ("secondScore", lv.getValueAtRank(1), features);
		for(int i=0; i<lv.numLocations(); i++) {
//			features = PropertyList.add (lv.getLabelAtRank(i).toString() +"HasRank"+i, 1.0, features);
			features = PropertyList.add (lv.getLabelAtRank(i).toString() +"HasValue", lv.valueAtLocation (i), features);
			}
		
		features = PropertyList.add ("MarginOfVictory", marginOfVictory, features);
		features = PropertyList.add("numFeatures", ((double)fv.numLocations()/fdict.size()), features);
		features = PropertyList.add (bestLabel.toString() + "IsFirst-" + lv.getLabelAtRank(1).toString()+"IsSecond", 1.0, features);
		features = PropertyList.add ("Range", winningScore - lv.getValueAtRank(lv.numLocations()-1), features);
		features = PropertyList.add (bestLabel.toString()+"IsFirst", 1.0, features);
		features = PropertyList.add (lv.getLabelAtRank(1).toString() + "IsSecond", 1.0, features);			

		// loop through original feature vector
		// and add each feature to PropertyList
//		features = PropertyList.add ("winningScore", winningScore, features);
//		features = PropertyList.add ("secondScore", lv.getValueAtRank(1), features);
//		features = PropertyList.add (bestLabel.toString()+"IsFirst", 1.0, features);
//		features = PropertyList.add (lv.getLabelAtRank(1).toString() + "IsSecond", 1.0, features);			

		// xxx this hurt performance. is this correct function call?
//			for(int loc = 0; loc < fv.numLocations(); loc++) 
//				features = PropertyList.add(fdict.lookupObject(loc).toString(), 1.0, features);
		
			//features = PropertyList.add ("winningClassPrecision", confusionMatrix.getPrecision(lv.getBestIndex()) , features);			
//			features = PropertyList.add ("confusionBetweenTop2", confusionMatrix.getConfusionBetween(lv.getBestIndex(), lv.getIndexAtRank(1)) , features);
			//features = PropertyList.add ("Variance",getScoreVariance(lv), features);
			
			
// use cutoffs of some metrics
/*
	if(winningScore < winningThreshold){
	features = PropertyList.add ("WinningScoreBelowX", 1.0, features);
	bestScoreLessThanX++;
	if(classification.bestLabelIsCorrect()) {
	reallyWrong++;
	}
				}			
				if(marginOfVictory < .9)
				features = PropertyList.add ("MarginOfVictoryBelow.9", 1.0, features);
				if(getScoreVariance(lv) < varianceThreshold) {
				features = PropertyList.add ("VarianceBelowX", 1.0, features);
				varianceLessThanX++;
				}
				if(lv.getValueAtRank(1) > secondThreshold) {
				features = PropertyList.add ("SecondScoreAboveX", 1.0, features);
				secondScoreGreaterThanX++;			    
				}
*/			
			
			/*
			// all the confidence predicting features
			features = PropertyList.add ("winningScore", winningScore, features);
			
			features = PropertyList.add(bestLabel.toString()+"IsFirst", 1.0, features);
			features = PropertyList.add (lv.getLabelAtRank(1).toString() + "IsSecond", 1.0, features);			
			
			features = PropertyList.add ("secondScore", lv.getValueAtRank(1), features);

			for(int i=0; i<lv.numLocations(); i++) {
				features = PropertyList.add (lv.getLabelAtRank(i).toString() +"HasRank"+i, lv.getValueAtRank(i), features);
			}

			if(marginOfVictory < .9)
			 	features = PropertyList.add ("MarginOfVictoryBelow.9", 1.0, features);

			if(winningScore < winningThreshold){
			 	features = PropertyList.add ("WinningScoreBelowX", 1.0, features);
				bestScoreLessThanX++;
			}
			if(getScoreVariance(lv) < varianceThreshold) {
			 	features = PropertyList.add ("VarianceBelowX", 1.0, features);
				varianceLessThanX++;
			}
			if(lv.getValueAtRank(1) > secondThreshold) {
			        features = PropertyList.add ("SecondScoreAboveX", 1.0, features);
				secondScoreGreaterThanX++;			    
			}
			LabelAlphabet vocab = lv.getLabelAlphabet();
 			for(int i=0; i<vocab.size(); i++) {
			 	features = PropertyList.add(vocab.lookupObject(i).toString()+"'sScore", lv.valueAtLocation(i), features);
			}

			features = PropertyList.add("numFeatures", ((double)fv.numLocations()/fdict.size()), features);

			features = PropertyList.add (bestLabel.toString() + "IsFirst-" + lv.getLabelAtRank(1).toString()+"IsSecond", 1.0, features);
			
					features = PropertyList.add("marginOfVictory", lv.getBestValue() - lv.getValueAtRank(1), features);
*/
/*
	// xxx these features either had 0 info gain or had a negative
	// impact on performance
					features = PropertyList.add ("scoreVariance", getScoreVariance(lv), features);
					features = PropertyList.add ("scoreMean", getScoreMean(lv), features);
*/
			// loop through original feature vector
			// and add each feature to PropertyList
			// xxx this hurt performance. is this correct function call?
			//for(int loc = 0; loc < fv.numLocations(); loc++) 
			//	features = PropertyList.add(fdict.lookupObject(loc).toString(), 1.0, features);
			
			
			// ...
			// ...
		
		carrier.setTarget(((LabelAlphabet)getTargetAlphabet()).lookupLabel(classification.bestLabelIsCorrect() ? "correct" : "incorrect"));
		carrier.setData(new FeatureVector ((Alphabet) getDataAlphabet(), features, false));

		carrier.setName(inst.getName());
		carrier.setSource(inst.getSource());
		return carrier;
	}
	
	private double getScoreMean(LabelVector lv)
	{
		double sum = 0.0;
		for(int i=0; i<lv.numLocations(); i++) {
			sum += lv.getValueAtRank(i);
		}
		return sum / lv.numLocations();
	}
	
	private double getScoreVariance(LabelVector lv)
	{
		double mean = getScoreMean(lv);
			double squaredDifference = 0.0;
			for(int i=0; i<lv.numLocations(); i++) {
				squaredDifference += (mean - lv.getValueAtRank(i)) * (mean - lv.getValueAtRank(i));
			}
			return squaredDifference / lv.numLocations();
	}
}

	
