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

import java.util.logging.*;
import java.util.*;

import cc.mallet.fst.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

/**
	 Estimates the confidence of an entire sequence by combining the
	 output of a segment confidence estimator for each segment.
 */
public class SegmentProductConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	TransducerConfidenceEstimator segmentEstimator;
	
	private static Logger logger = MalletLogger.getLogger(
		SegmentProductConfidenceEstimator.class.getName());


	public SegmentProductConfidenceEstimator (Transducer model,
																						TransducerConfidenceEstimator segmentConfidenceEstimator) {
		super(model);
		this.segmentEstimator = segmentConfidenceEstimator;
	}

	/**
		 Calculates the confidence in the tagging of a {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags,
																			 Object[] inTags) {
		SegmentIterator iter = new SegmentIterator (model, instance, startTags, inTags);
		double instanceConfidence = 1;
		while (iter.hasNext()) {
			Segment s = (Segment) iter.nextSegment();
			instanceConfidence *= segmentEstimator.estimateConfidenceFor (s);			
		}
		return instanceConfidence;
	}
}
