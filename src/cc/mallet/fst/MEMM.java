/* Copyright (C) 2004 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
		@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>

    MEMM might have been simply implemented with a MaxEnt classifier object at each state,
    but I chose not to do that so that tied features could be used in different parts of the
    FSM, just as in CRF.  So, the expectation-gathering is done (in MEMM-style) without
    forward-backward, just with local (normalized) distributions over destination states
    from source states, but there is a global MaximizebleMEMM, and all the MEMMs parameters
    are set together as part of a single optimization.
 */

package cc.mallet.fst;


import java.io.Serializable;
import java.util.BitSet;
import java.util.logging.Logger;
import java.text.DecimalFormat;

import cc.mallet.classify.MaxEnt;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

public class MEMM extends CRF implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(MEMM.class.getName());


	public MEMM (Pipe inputPipe, Pipe outputPipe)
	{
		super (inputPipe, outputPipe);
	}

	public MEMM (Alphabet inputAlphabet, Alphabet outputAlphabet)
	{
		super (inputAlphabet, outputAlphabet);
	}

	public MEMM (CRF crf)
	{
		super (crf);
	}

	protected CRF.State newState (String name, int index,
			double initialWeight, double finalWeight,
			String[] destinationNames,
			String[] labelNames,
			String[][] weightNames,
			CRF crf)
	{
		return new State (name, index, initialWeight, finalWeight,
				destinationNames, labelNames, weightNames, crf);
	}




	public static class State extends CRF.State implements Serializable
	{
		InstanceList trainingSet;

		protected State (String name, int index,
				double initialCost, double finalCost,
				String[] destinationNames,
				String[] labelNames,
				String[][] weightNames,
				CRF crf)
		{
			super (name, index, initialCost, finalCost, destinationNames, labelNames, weightNames, crf);
		}

		// Necessary because the CRF4 implementation will return CRF4.TransitionIterator
		public Transducer.TransitionIterator transitionIterator (
				Sequence inputSequence, int inputPosition,
				Sequence outputSequence, int outputPosition)
		{
			if (inputPosition < 0 || outputPosition < 0)
				throw new UnsupportedOperationException ("Epsilon transitions not implemented.");
			if (inputSequence == null)
				throw new UnsupportedOperationException ("CRFs are not generative models; must have an input sequence.");
			return new TransitionIterator (
					this, (FeatureVectorSequence)inputSequence, inputPosition,
					(outputSequence == null ? null : (String)outputSequence.get(outputPosition)), crf);
		}

	}

	protected static class TransitionIterator extends CRF.TransitionIterator implements Serializable
	{
		private double sum;

		public TransitionIterator (State source,
				FeatureVectorSequence inputSeq,
				int inputPosition,
				String output, CRF memm)
		{
			super (source, inputSeq, inputPosition, output, memm);
			normalizeCosts ();
		}

		public TransitionIterator (State source,
				FeatureVector fv,
				String output, CRF memm)
		{
			super (source, fv, output, memm);
			normalizeCosts ();
		}

		private void normalizeCosts ()
		{
			// Normalize the next-state costs, so they are -(log-probabilities)
			// This is the heart of the difference between the locally-normalized MEMM
			// and the globally-normalized CRF
			sum = Transducer.IMPOSSIBLE_WEIGHT;
			for (int i = 0; i < weights.length; i++)
				sum = sumLogProb (sum, weights[i]);
			assert (!Double.isNaN (sum));
			if (!Double.isInfinite (sum)) {
				for (int i = 0; i < weights.length; i++)
					weights[i] = sum;
			}
		}

		public String describeTransition (double cutoff)
		{
			DecimalFormat f = new DecimalFormat ("0.###");
			return super.describeTransition (cutoff) + "Log Z = "+f.format(sum)+"\n";
		}
	}



}
