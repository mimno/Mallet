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
	 Estimates the confidence of an entire sequence by the
	 "disagreement" among a committee of CRFs.
 */
public class QBCSequenceConfidenceEstimator extends TransducerSequenceConfidenceEstimator
{
	
	private static Logger logger = MalletLogger.getLogger(
		QBCSequenceConfidenceEstimator.class.getName());


	Transducer[] committee;
	
	public QBCSequenceConfidenceEstimator (Transducer model, Transducer[] committee) {
		super(model);
		this.committee = committee;
	}

	/**
		 Calculates the confidence in the tagging of a {@link Instance}.
	 */
	public double estimateConfidenceFor (Instance instance,
																			 Object[] startTags,
																			 Object[] inTags) {
		Sequence[] predictions = new Sequence[committee.length];
		
		for (int i = 0; i < committee.length; i++)  
			predictions[i] = new MaxLatticeDefault (committee[i], (Sequence)instance.getData()).bestOutputSequence();

		// Higher return value means higher confidence this sequence is correct.
		double avg = avgVoteEntropy(predictions);
		return -1.0 * avg;
	}

	/** Calculate the "vote entropy" for each token and average. Vote
	 * entropy is defined as
	 *
	 * - \frac{1}{log(min(k, |C|)) \sum_c \frac{V(c,e)}{k} log(\frac{V(c,e)}{k})
	 *
	 * where k is committee size, e is Instance, c is class, and V(c,e)
	 * is the number of committee members assigning class c to input e.
	 */
	private double avgVoteEntropy (Sequence[] predictions) {
		double sum = 0.0;		
		for (int i = 0; i < predictions[0].size(); i++) {
			HashMap label2Count = new HashMap();
			for (int j = 0; j < predictions.length; j++) {
				String label = predictions[j].get(i).toString();
				Integer count = (Integer)label2Count.get(label);
				if (count == null)
					count = new Integer(0);
				label2Count.put(label, new Integer(count.intValue() + 1));					
			}
			sum += voteEntropy(label2Count);
		}
		return (double)sum / predictions[0].size();
	}

	private double voteEntropy (HashMap label2Count) {
		Iterator iter = label2Count.keySet().iterator();
		double sum = 0.0;
		while (iter.hasNext()) {
			String label = (String)iter.next();
			int count = ((Integer)label2Count.get(label)).intValue();
			double quot = (double)count / committee.length;
 			sum += quot * Math.log(quot);
		}
		double ret = (double) -1.0 * sum / Math.log((double)committee.length);
		return ret;
	}
}
