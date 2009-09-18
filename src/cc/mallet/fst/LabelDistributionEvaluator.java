/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst;

import java.util.logging.Logger;

import com.wcohen.secondstring.PrintfFormat;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

/**
 * Prints predicted and true label distribution.
 *
 * Created: March 31st, 2009
 * 
 * @author <A HREF="mailto:gdruck@cs.umass.edu>gdruck@cs.umass.edu</A>
 */

public class LabelDistributionEvaluator extends TransducerEvaluator {

  private static final Logger logger = MalletLogger.getLogger (InstanceAccuracyEvaluator.class.getName());
  
  public LabelDistributionEvaluator (InstanceList[] instanceLists, String[] descriptions) {
    super (instanceLists, descriptions);
  }
  
  @Override
  public void evaluateInstanceList(TransducerTrainer transducer,
      InstanceList instances, String description) {
    double[] predCounts = new double[instances.getTargetAlphabet().size()];
    double[] trueCounts = new double[instances.getTargetAlphabet().size()];

    int total = 0;
    for (int i = 0; i < instances.size(); i++) {
      Instance instance = instances.get(i);
      Sequence trueOutput = (Sequence) instance.getTarget();
      Sequence predOutput = (Sequence) transducer.getTransducer().transduce((Sequence)instance.getData());
      for (int j = 0; j < predOutput.size(); j++) {
        total++;
        predCounts[instances.getTargetAlphabet().lookupIndex(predOutput.get(j))]++;
        trueCounts[instances.getTargetAlphabet().lookupIndex(trueOutput.get(j))]++;
      }
    }

    for (int li = 0; li < predCounts.length; li++) {
      double ppred = predCounts[li] / total;
      double ptrue = trueCounts[li] / total;
      logger.info(description + " " + instances.getTargetAlphabet().lookupObject(li) + " predicted: " + round(ppred,4) + " - true: " + round(ptrue,4));
    }
  }
  
  private static String round(double val, int n) {
    String format = "%." + n + "f";
    return new PrintfFormat(format).sprintf(val);
  }
}
