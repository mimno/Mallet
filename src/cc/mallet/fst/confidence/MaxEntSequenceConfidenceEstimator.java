/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
		@author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
*/

package cc.mallet.fst.confidence;

import java.util.*;

import cc.mallet.classify.*;
import cc.mallet.fst.*;
import cc.mallet.pipe.*;
import cc.mallet.types.*;

/**
 * Estimates the confidence of a {@link Sequence} extracted by a {@link
 * Transducer} using a {@link MaxEnt} classifier to classify Sequences
 * as "correct" or "incorrect." xxx needs some interface work.
 */
public class MaxEntSequenceConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	MaxEntTrainer meTrainer;
	MaxEnt meClassifier;
	Pipe pipe;
	String correct, incorrect;
	
	public MaxEntSequenceConfidenceEstimator (Transducer model, double gaussianVariance) {
		super(model);
		meTrainer = new MaxEntTrainer (gaussianVariance);
	}

	public MaxEntSequenceConfidenceEstimator (Transducer model) {
		this (model, 10.0);
	}

	public MaxEnt getClassifier () { return this.meClassifier; }
	/**
		 Train underlying classifier on <code>ilist</code>. Assumes ilist
		 has targst <code>correct</code> or <code>incorrect</code>.
		 @param ilist training list to build correct/incorrect classifier
		 @param correct "correct" label
		 @param incorrect "incorrect" label
	 */
	public MaxEnt trainClassifier (InstanceList ilist, String correct, String incorrect) {
		this.meClassifier = (MaxEnt) meTrainer.train (ilist);
		this.pipe = ilist.getPipe ();
		this.correct = correct;
		this.incorrect = incorrect;
		InfoGain ig = new InfoGain (ilist);
		int igl = Math.min (30, ig.numLocations());
		for (int i = 0; i < igl; i++)
			System.out.println ("InfoGain["+ig.getObjectAtRank(i)+"]="+ig.getValueAtRank(i));
		return this.meClassifier;
	}
	

	/**
		 Calculates the confidence in the tagging of an {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags, Object[] inTags) {		
		
		Classification c = null;
		if (Alphabet.alphabetsMatch(instance, this.pipe)) 
			c = this.meClassifier.classify (new SequenceConfidenceInstance (instance));							
		else
			c = this.meClassifier.classify (instance);
		return c.getLabelVector().value (this.correct);
	}

	public PipedInstanceWithConfidence[] rankPipedInstancesByConfidence (InstanceList ilist,
																																	Object[] startTags,
																																	Object[] continueTags) {
		ArrayList confidenceList = new ArrayList ();
		for (int i=0; i < ilist.size(); i++) {
			Instance instance = ilist.get (i);
			boolean correctInstance = ((Labeling)instance.getTarget()).getBestLabel().toString().equals (this.correct);
			System.err.println ("Instance is " + (correctInstance ? "correct" : "incorrect"));
			confidenceList.add (new PipedInstanceWithConfidence (instance,
																													 estimateConfidenceFor (instance, startTags, continueTags),
																													 correctInstance));
		}
		Collections.sort (confidenceList);
		PipedInstanceWithConfidence[] ret = new PipedInstanceWithConfidence[1];
		ret = (PipedInstanceWithConfidence[]) confidenceList.toArray (ret);
		return ret;
	}

}
