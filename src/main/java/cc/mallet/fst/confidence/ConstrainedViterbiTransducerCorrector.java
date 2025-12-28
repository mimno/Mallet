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
import java.util.ArrayList;

import cc.mallet.fst.*;
import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

/**
 * Corrects a subset of the {@link Segment}s produced by a {@link
 * Transducer}. It's most useful to find the {@link Segment}s that the
 * {@link Transducer} is least confident in and correct those using
 * the true {@link Labeling}
 * (<code>correctLeastConfidenceSegments</code>).  The corrected
 * segment then propagates to other labelings in the sequence using
 * "constrained viterbi" -- a viterbi calculation that requires the
 * path to pass through the corrected segment states.
 */
public class ConstrainedViterbiTransducerCorrector implements TransducerCorrector
{
	private static Logger logger = MalletLogger.getLogger(ConstrainedViterbiTransducerCorrector.class.getName());

	TransducerConfidenceEstimator confidenceEstimator;
	Transducer model;
	ArrayList leastConfidentSegments;
	
	public ConstrainedViterbiTransducerCorrector (TransducerConfidenceEstimator confidenceEstimator,
															Transducer model) {
		this.confidenceEstimator = confidenceEstimator;
		this.model = model;
	}

	public ConstrainedViterbiTransducerCorrector (Transducer model) {
		this (new ConstrainedForwardBackwardConfidenceEstimator (model), model);
	}	

	public java.util.Vector getSegmentConfidences () {return confidenceEstimator.getSegmentConfidences();}

	/**
		 Returns the least confident segments from each sequence in the
		 previous call to <code>correctLeastConfidentSegments</code>
	 */
	public ArrayList getLeastConfidentSegments () {
		return this.leastConfidentSegments;
	}

	/**
		 Returns the least confident segments in <code>ilist</code>
		 @param ilist test instances
		 @param startTags indicate the beginning of segments
		 @param continueTages indicate "inside" of segments
		 @return list of {@link Segment}s, one for each instance, that is least confident
	*/
	public ArrayList getLeastConfidentSegments (InstanceList ilist, Object[] startTags, Object[] continueTags) {
		ArrayList ret = new ArrayList ();
		for (int i=0; i < ilist.size(); i++) {
			Segment[] orderedSegments = confidenceEstimator.rankSegmentsByConfidence (
				ilist.get (i), startTags, continueTags);
			ret.add (orderedSegments[0]);
		}
		return ret;
	}
		

	public ArrayList correctLeastConfidentSegments (InstanceList ilist,
																									Object[] startTags,
																									Object[] continueTags) {
		return correctLeastConfidentSegments (ilist, startTags, continueTags, false);
	}
		
	/**
		 Returns an ArrayList of corrected Sequences.  Also stores
		 leastConfidentSegments, an ArrayList of the segments to correct,
		 where null entries mean no segment was corrected for that
		 sequence.
		 @param ilist test instances
		 @param startTags indicate the beginning of segments
		 @param continueTages indicate "inside" of segments
		 @param findIncorrect true if we should cycle through least
		 confident segments until find an incorrect one
		 @return list of {@link Sequence}s corresponding to the corrected
		 tagging of each instance in <code>ilist</code>
	*/
	public ArrayList correctLeastConfidentSegments (InstanceList ilist, Object[] startTags,
																										Object[] continueTags, boolean findIncorrect) {

		ArrayList correctedPredictionList = new ArrayList ();
		this.leastConfidentSegments = new ArrayList ();

		logger.info (this.getClass().getName() + " ranking confidence using " +
								 confidenceEstimator.getClass().getName());
		for (int i=0; i < ilist.size(); i++) {
			logger.fine ("correcting instance# " + i + " / " + ilist.size());
			Instance instance = ilist.get (i);
			Segment[] orderedSegments = new Segment[1];
			Sequence input = (Sequence) instance.getData ();
			Sequence truth = (Sequence) instance.getTarget ();
			Sequence predicted = new MaxLatticeDefault (model, input).bestOutputSequence();

			int numIncorrect = 0;
			for (int j=0; j < predicted.size(); j++) 
				numIncorrect += (!predicted.get(j).equals (truth.get(j))) ? 1 : 0;
			if (numIncorrect == 0) { // nothing to correct
				this.leastConfidentSegments.add (null);
				correctedPredictionList.add (predicted);
				continue;				
			}
			// rank segments by confidence
			orderedSegments = confidenceEstimator.rankSegmentsByConfidence (
				instance, startTags, continueTags);
			logger.fine ("Ordered Segments:\n");
			for (int j=0; j < orderedSegments.length; j++) {
				logger.fine (orderedSegments[j].toString());
			}
			logger.fine ("Correcting Segment: True Sequence:");			
			for (int j=0; j < truth.size(); j++)
				logger.fine ((String)truth.get (j) + "\t");
			logger.fine ("");
			logger.fine ("Ordered Segments:\n");
			for (int j=0; j < orderedSegments.length; j++) {
				logger.fine (orderedSegments[j].toString());
			}
			// if <code>findIncorrect</code>, find the least confident
			// segment that is incorrectly labeled
			// else, use least confident segment
			Segment leastConfidentSegment = orderedSegments[0];
			if (findIncorrect) {
				for (int j=0; j < orderedSegments.length; j++) {
					if (!orderedSegments[j].correct()) {
						leastConfidentSegment = orderedSegments[j];
						break;
					}
				}
			}

			if (findIncorrect && leastConfidentSegment.correct()) {
				logger.warning ("cannot find incorrect segment, probably because error is in background state\n");
				this.leastConfidentSegments.add (null);
				correctedPredictionList.add (predicted);
				continue;				
			}
			this.leastConfidentSegments.add (leastConfidentSegment);

			if (leastConfidentSegment == null) { // nothing extracted
				correctedPredictionList.add (predicted);
				continue;
			}
				
			// create segmentCorrectedOutput, which has the true labels for
			// the leastConfidentSegment and null for other positions
			String[] sequence = new String[truth.size()];
			int numCorrectedTokens = 0;
			for (int j=0; j < sequence.length; j++)
				sequence[j] = null;
			for (int j=0; j < truth.size(); j++) {
				// if in segment
				if (leastConfidentSegment.indexInSegment (j)) {
					sequence[j] = (String)truth.get (j);
					numCorrectedTokens++;
				}
			}
			if (leastConfidentSegment.endsPrematurely ()) {
				sequence[leastConfidentSegment.getEnd()+1] =
					(String)truth.get (leastConfidentSegment.getEnd()+1);
				numCorrectedTokens++;
			}
			logger.fine ("Constrained Segment Sequence\n");
			for (int j=0; j < sequence.length; j++) {
 				logger.fine (sequence[j]);
 			}
			ArraySequence segmentCorrectedOutput = new ArraySequence (sequence);

			// run constrained viterbi on this sequence with the
			// constraint that this segment is tagged correctly
			Sequence correctedPrediction = new MaxLatticeDefault (model, 
				orderedSegments[0].getInput (),	segmentCorrectedOutput).bestOutputSequence();

			int numIncorrectAfterCorrection = 0;
			for (int j=0; j < truth.size(); j++) 
				numIncorrectAfterCorrection += (!correctedPrediction.get(j).equals (truth.get(j))) ? 1 : 0;
			logger.fine ("Num incorrect tokens in original prediction: " + numIncorrect);
			logger.fine ("Num corrected tokens: " + numCorrectedTokens);
			logger.fine ("Num incorrect tokens after correction-propagation: " + numIncorrectAfterCorrection);

			// print sequence info
			logger.fine ("Correcting Segment: True Sequence:");			
			for (int j=0; j < truth.size(); j++)
				logger.fine ((String)truth.get (j) + "\t");
			logger.fine ("\nOriginal prediction: ");			
			for (int j=0; j < predicted.size(); j++)
				logger.fine ((String)predicted.get (j) + "\t");
			logger.fine ("\nCorrected prediction: ");			
			for (int j=0; j < correctedPrediction.size(); j++)
				logger.fine ((String)correctedPrediction.get (j) + "\t");
			logger.fine ("");
			correctedPredictionList.add (correctedPrediction);
		}
		return correctedPredictionList;
	}
}
