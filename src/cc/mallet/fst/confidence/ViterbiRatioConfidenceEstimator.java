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
	 Estimates the confidence of an entire sequence by the ration of the
	 probabilities of the first and second best Viterbi paths.
 */
public class ViterbiRatioConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	
	private static Logger logger = MalletLogger.getLogger(
		SegmentProductConfidenceEstimator.class.getName());


	public ViterbiRatioConfidenceEstimator (Transducer model) {
		super(model);
	}

	/**
		 Calculates the confidence in the tagging of an {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags,
																			 Object[] inTags) {
		SumLatticeDefault lattice = new SumLatticeDefault (model, (Sequence)instance.getData());
		//ViterbiPathNBest bestViterbis = new ViterbiPathNBest (model, (Sequence)instance.getData(), 2);
 		//double[] costs = bestViterbis.costNBest();
		MaxLatticeDefault vlat = new MaxLatticeDefault (model, (Sequence)instance.getData(), null, 2);
		List<SequencePairAlignment<Object,Object>> alignments = vlat.bestOutputAlignments(2);
		double cost1 = alignments.get(0).getWeight();
		double cost2 = alignments.get(1).getWeight();
		double latticeCost = lattice.getTotalWeight();
		return (Math.exp (-cost1 + latticeCost) / Math.exp(-cost2 + latticeCost));
	}
}

