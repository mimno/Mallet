/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.fst;

import java.util.HashMap;
import java.util.logging.Logger;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;

import cc.mallet.util.MalletLogger;

/**
 * Reports the percentage of instances for which the entire predicted sequence was
 *  correct.
 *
 * Created: May 12, 2004
 * 
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: InstanceAccuracyEvaluator.java,v 1.1 2007/10/22 21:37:48 mccallum Exp $
 */
public class InstanceAccuracyEvaluator extends TransducerEvaluator {
  
  private static final Logger logger = MalletLogger.getLogger (InstanceAccuracyEvaluator.class.getName());

	private HashMap<String,Double> accuracy = new HashMap<String,Double>();
  
  public void evaluateInstanceList (TransducerTrainer tt, InstanceList data, String description)
  {
    int correct = 0;
    for (int i = 0; i < data.size(); i++) {
      Instance instance = data.get(i);
      Sequence input = (Sequence) instance.getData();
      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = tt.getTransducer().transduce (input);
      assert (predOutput.size() == trueOutput.size());
      if (sequencesMatch (trueOutput, predOutput))
        correct++;
      }
    double acc = ((double)correct) / data.size();
		accuracy.put(description, acc);

    logger.info (description+" Num instances = "+data.size()+"  Num correct = "+correct+" Per-instance accuracy = "+acc);
  }
  
  public double getAccuracy(String description) {
	  return accuracy.get(description).doubleValue();
  }

  private boolean sequencesMatch (Sequence trueOutput, Sequence predOutput)
  {
    for (int j = 0; j < trueOutput.size(); j++) {
      Object tru = trueOutput.get(j);
      Object pred = predOutput.get(j);
      if (!tru.toString().equals (pred.toString())) {
        return false;
      }
    }
    return true;
  }
}
