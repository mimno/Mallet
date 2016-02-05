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
import java.io.*;

import cc.mallet.fst.*;
import cc.mallet.types.*;

/**
	 Calculates the effectiveness of "constrained viterbi" in
	 propagating corrections in one segment of a sequence to other
	 segments.
*/
public class ConfidenceCorrectorEvaluator
{
	Object[] startTags; // to identify segment start/end boundaries
	Object[] inTags;
	
	public ConfidenceCorrectorEvaluator (Object[] startTags, Object[] inTags) {
		this.startTags = startTags;
		this.inTags = inTags;
	}
	
	/**
		 Returns true if predSequence contains errors outside of correctedSegment.
	 */
	private boolean containsErrorInUncorrectedSegments (Sequence trueSequence,
																											Sequence predSequence,
																											Sequence correctedSequence, 
																											Segment correctedSegment) {
		for (int i=0; i < trueSequence.size(); i++) {
			if (correctedSegment.indexInSegment(i)) {
				if (!correctedSequence.get (i).equals (trueSequence.get (i))) {
					System.err.println ("\nTruth: ");
					for (int j=0; j < trueSequence.size(); j++)
						System.err.print (trueSequence.get (j) + " ");
					System.err.println ("\nPredicted: ");
					for (int j=0; j < trueSequence.size(); j++)
						System.err.print (predSequence.get (j) + " ");
					System.err.println ("\nCorrected: ");
					for (int j=0; j < trueSequence.size(); j++)
						System.err.print (correctedSequence.get (j) + " ");
					throw new IllegalStateException ("Corrected sequence does not have correct labels for corrected segment: " + correctedSegment);
				}
			}
			else {
				if (!predSequence.get (i).equals (trueSequence.get (i)))
					return true;
			}
		}
		return false;
	}

	/**
		 Only evaluates over sequences which contain errors.  Examine
		 region not directly corrected by <code>correctedSegments </code>
		 to measure effects of error propagation.
		 @param model used to segment input sequence
		 @param predictions list of the corrected segmentation
		 @param ilist list of testing data
		 @param correctedSegments list of {@link Segment}s in each
		 sequence that were corrected...currently only allows one segment
		 per instance.
		 @param uncorrected true if we only evaluate sequences where
		 errors remain after correction
	*/
	public void evaluate (Transducer model, ArrayList predictions, InstanceList ilist,
												ArrayList correctedSegments, String description,
												PrintStream outputStream, boolean errorsInUncorrected) {
		if (predictions.size() != ilist.size () || correctedSegments.size() != ilist.size ())
			throw new IllegalArgumentException ("number of predicted sequence (" +
																					predictions.size() + ") and number of corrected segments (" +
																					correctedSegments.size() + ") must be equal to length of instancelist (" +
																					ilist.size() + ")");
		int numIncorrect2Correct = 0; // overall correction improvement
		int numCorrect2Incorrect = 0; // overall correction deprovement
		int numPropagatedIncorrect2Correct = 0; // count of propagated corrections
		int numPredictedCorrect = 0; // num tokens predicted correctly
		int numCorrectedCorrect = 0; // num tokens predicted correctly after correction
		// accuracy outside of corrected segment before and after propagation
	  int numUncorrectedCorrectBeforePropagation = 0; 
		int numUncorrectedCorrectAfterPropagation = 0; 
		int totalTokens = 0;
		int totalTokensInUncorrectedRegion = 0;
		int numCorrectedSequences = 0; // count of sequences corrected
		
		for (int i=0; i < ilist.size(); i++) {
			Instance instance = ilist.get (i);
			Sequence input = (Sequence) instance.getData ();
			Sequence trueSequence = (Sequence) instance.getTarget ();
			Sequence predSequence = (Sequence) new MaxLatticeDefault (model, input).bestOutputSequence();
			Sequence correctedSequence = (Sequence) predictions.get (i);
			Segment correctedSegment = (Segment) correctedSegments.get (i);
			// if any condition is true, do not evaluate this sequence
			if (correctedSegment == null ||
					(errorsInUncorrected && !containsErrorInUncorrectedSegments (
						trueSequence, predSequence, correctedSequence, correctedSegment))) 
				continue;
			numCorrectedSequences++;
			totalTokens += trueSequence.size();
			boolean[] predictedMatches = getMatches (trueSequence, predSequence);
			boolean[] correctedMatches = getMatches (trueSequence, correctedSequence);
			for (int j=0; j < predictedMatches.length; j++) {
				numPredictedCorrect += predictedMatches[j] ? 1 : 0;
				numCorrectedCorrect += correctedMatches[j] ? 1 : 0;				
				if (predictedMatches[j] && !correctedMatches[j])
					numCorrect2Incorrect++;
				else if (!predictedMatches[j] && correctedMatches[j])
					numIncorrect2Correct++;
				// outside corrected segment
				if (j < correctedSegment.getStart() || j > correctedSegment.getEnd()) {
					totalTokensInUncorrectedRegion++;
					if (!predictedMatches[j] && correctedMatches[j]) 
						numPropagatedIncorrect2Correct++;
					numUncorrectedCorrectBeforePropagation += predictedMatches[j] ? 1 : 0;
					numUncorrectedCorrectAfterPropagation += correctedMatches[j] ? 1 : 0;
				}			  				
			}
		}		
		double tokenAccuracyBeforeCorrection = (double)numPredictedCorrect / totalTokens;
		double tokenAccuracyAfterCorrection = (double)numCorrectedCorrect / totalTokens;
		double uncorrectedRegionAccuracyBeforeCorrection = (double)numUncorrectedCorrectBeforePropagation / totalTokensInUncorrectedRegion;
		double uncorrectedRegionAccuracyAfterCorrection = (double)numUncorrectedCorrectAfterPropagation / totalTokensInUncorrectedRegion;
		
		outputStream.println (description + "\nEvaluating effect of error-propagation in sequences containing at least one token error:" +
													"\ntotal number correctedsequences: " +
													numCorrectedSequences + 
													"\ntotal number tokens: " +
													totalTokens + 
													"\ntotal number tokens in \"uncorrected region\":" +
													totalTokensInUncorrectedRegion + 
													"\ntotal number correct tokens before correction:" +
													numPredictedCorrect + 
													"\ntotal number correct tokens after correction:" +
													numCorrectedCorrect + 
													"\ntoken accuracy before correction: " +
												 	tokenAccuracyBeforeCorrection +
													"\ntoken accuracy after correction: " +
													tokenAccuracyAfterCorrection +
													"\nnumber tokens corrected by propagation: " +
													numPropagatedIncorrect2Correct +
													"\nnumber tokens made incorrect by propagation: " +
													numCorrect2Incorrect +
													"\ntoken accuracy of \"uncorrected region\" before propagation: " +
													uncorrectedRegionAccuracyBeforeCorrection +
													"\ntoken accuracy of \"uncorrected region\" after propagataion: " +
													uncorrectedRegionAccuracyAfterCorrection);
		
	}
	

	/**
		 Returns a boolean array listing where two sequences have matching
		 values.
	 */
 	private boolean[] getMatches (Sequence s1, Sequence s2) {
	 	if (s1.size() != s2.size())
			throw new IllegalArgumentException ("s1.size: " + s1.size() + " s2.size: " + s2.size());
		boolean[] ret = new boolean [s1.size()];
		for (int i=0; i < s1.size(); i++) 
			ret[i] = s1.get (i).equals (s2.get(i));
		return ret;
	}
}
