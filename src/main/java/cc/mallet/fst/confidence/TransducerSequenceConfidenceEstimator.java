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

import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

import com.google.errorprone.annotations.Var;

import cc.mallet.fst.MaxLatticeDefault;
import cc.mallet.fst.Segment;
import cc.mallet.fst.Transducer;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

/**
 * Abstract class that estimates the confidence of a {@link Sequence}
 * extracted by a {@link Transducer}.Note that this is different from
 * {@link TransducerConfidenceEstimator}, which estimates the
 * confidence for a single {@link Segment}.
 */
abstract public class TransducerSequenceConfidenceEstimator
{
	private static Logger logger = MalletLogger.getLogger(TransducerSequenceConfidenceEstimator.class.getName());

	protected Transducer model; // the trained Transducer which
															// performed the extractions.

	public TransducerSequenceConfidenceEstimator (Transducer model) {
		this.model = model;
	}
	
	/**
		 Calculates the confidence in the tagging of a {@link Sequence}.
	 */
	abstract public double estimateConfidenceFor (
		Instance instance, Object[] startTags, Object[] inTags);


	/**
		 Ranks all {@link Sequences}s in this {@link InstanceList} by
		 confidence estimate.
		 @param ilist list of segmentation instances
		 @param startTags represent the labels for the start states (B-)
		 of all segments
		 @param continueTags represent the labels for the continue state
		 (I-) of all segments
		 @return array of {@link InstanceWithConfidence}s ordered by
		 non-decreasing confidence scores, as calculated by
		 <code>estimateConfidenceFor</code>
	 */
	public InstanceWithConfidence[] rankInstancesByConfidence (InstanceList ilist,
																														 Object[] startTags,
																														 Object[] continueTags) {
		ArrayList confidenceList = new ArrayList ();
		for (int i=0; i < ilist.size(); i++) {
			Instance instance = ilist.get (i);
			Sequence predicted = new MaxLatticeDefault (model, (Sequence)instance.getData()).bestOutputSequence();
			double confidence = estimateConfidenceFor (instance, startTags, continueTags);
			confidenceList.add (new InstanceWithConfidence ( instance, confidence, predicted));
			logger.info ("instance#"+i+" confidence="+confidence);
		}
		Collections.sort (confidenceList);
		@Var
		InstanceWithConfidence[] ret = new InstanceWithConfidence[1];
		ret = (InstanceWithConfidence[]) confidenceList.toArray (ret);
		return ret;
	}
}
