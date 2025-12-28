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
import java.util.logging.*;

import cc.mallet.fst.*;
import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

/**
 * Corrects a subset of the {@link Segment}s produced by a {@link
 * Transducer}. It's most useful to find the {@link Segment}s that the
 * {@link Transducer} is least confident in and correct those using
 * the true {@link Labeling}
 * (<code>correctLeastConfidenceSegments</code>).  Unlike in {@link
 * ConstrainedViterbi}, the corrected segment does not affect the
 * labeling of other segments in the sequence. For comparison.
 */
public class IsolatedSegmentTransducerCorrector implements TransducerCorrector
{
	private static Logger logger = MalletLogger.getLogger(IsolatedSegmentTransducerCorrector.class.getName());

	TransducerConfidenceEstimator confidenceEstimator;
	Transducer model;
	
	public IsolatedSegmentTransducerCorrector (TransducerConfidenceEstimator confidenceEstimator,
															Transducer model) {
		this.confidenceEstimator = confidenceEstimator;
		this.model = model;
	}

	public IsolatedSegmentTransducerCorrector (Transducer model) {
		this (new ConstrainedForwardBackwardConfidenceEstimator (model), model);
	}	
	

	/**
		 @param ilist original Transducer InstanceList
		 @param startTags start segment tags (B-)
		 @param continueTags continue segment tags (I-)
		 TransducerConfidenceEstimator}
		 @return a list of {@link Sequence}s corresponding to the
		 corrected tagging of each Instance in <code>ilist</code>. Note
		 that these corrections will not affect tokens outside of the
		 corrected segment.
	*/
	public ArrayList correctLeastConfidentSegments (InstanceList ilist, Object[] startTags,
																										Object[] continueTags) {
		ArrayList correctedPredictionList = new ArrayList ();
		for (int i=0; i < ilist.size(); i++) {
			logger.fine ("correcting instance# " + i + " / " + ilist.size());
			Instance instance = ilist.get (i);
			Segment[] orderedSegments = new Segment[1];
			orderedSegments = confidenceEstimator.rankSegmentsByConfidence (instance, startTags, continueTags);
			Segment leastConfidentSegment = orderedSegments[0];
			logger.fine ("Ordered Segments:\nTrue sequence: " + leastConfidentSegment.getTruth());
			for (int j=0; j < orderedSegments.length; j++) {
				logger.fine (orderedSegments[j].toString());
			}
			// _do not_ run constrained viterbi on this sequence with the
			// constraint that this segment is tagged correctly.
			// instead, simply replace the labeling of the corrected
			// segment.
			MultiSegmentationEvaluator eval = new MultiSegmentationEvaluator (new InstanceList[0], new String[0], startTags, continueTags);
			Sequence truth = leastConfidentSegment.getTruth();
			Sequence predicted = leastConfidentSegment.getPredicted();
			int numIncorrect = eval.numIncorrectSegments (truth, predicted);
			String[] sequence = new String[truth.size()];
			for (int j=0; j < truth.size(); j++) {
				if (j <= leastConfidentSegment.getEnd() && j >= leastConfidentSegment.getStart())
					sequence[j] = (String)truth.get (j);
				else sequence[j] = (String) predicted.get (j);
			}
			ArraySequence segmentCorrectedOutput = new ArraySequence (sequence);
			
			logger.fine ("Original prediction: ");			
			for (int j=0; j < predicted.size(); j++)
				logger.fine ((String)predicted.get (j) + "\t");
			logger.fine ("\nCorrected prediction: ");			
			for (int j=0; j < segmentCorrectedOutput.size(); j++)
				logger.fine ((String)segmentCorrectedOutput.get (j) + "\t");
			logger.fine ("");
			if (numIncorrect > -1)
				correctedPredictionList.add (segmentCorrectedOutput);
			else
				correctedPredictionList.add (null);
		}
		return correctedPredictionList;
	}
}
