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

import java.util.HashMap;

import cc.mallet.fst.*;
import cc.mallet.types.*;

/** Calculates the confidence in an extracted segment by taking the
 * average of P(s_i|o) for each state in the segment. */
public class GammaAverageConfidenceEstimator extends TransducerConfidenceEstimator
{
	HashMap string2stateIndex;
	
	public GammaAverageConfidenceEstimator (Transducer model) {
		super(model);
		string2stateIndex = new HashMap();
		// store state indices
		for (int i=0; i < model.numStates(); i++) {
			string2stateIndex.put (model.getState(i).getName(), Integer.valueOf (i));
		}
	}
	
	/**
		 Calculates the confidence in the tagging of a {@link Segment}.
		 @return 0-1 confidence value. higher = more confident.
	 */
	public double estimateConfidenceFor (Segment segment, SumLatticeDefault cachedLattice) {
		Sequence predSequence = segment.getPredicted ();
		Sequence input = segment.getInput ();
		SumLatticeDefault lattice = (cachedLattice==null) ? new SumLatticeDefault (model, input) :
                                             cachedLattice;
		double confidence = 0;
		for (int i=segment.getStart(); i <= segment.getEnd(); i++) {
			int stateIndex = stateIndexOfString((String)predSequence.get(i));
			if (stateIndex == -1) // Unknown label.
				return 0.0;
			confidence += lattice.getGammaProbability(i+1, model.getState(stateIndex));
		}
		return confidence/(double)segment.size();
	}
	
	private int stateIndexOfString (String s)
	{
		Integer index = (Integer) string2stateIndex.get (s);
		if (index == null)
			return -1;
		return index.intValue();
	}
}
