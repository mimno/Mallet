/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.fst;


import java.util.logging.Logger;
import java.util.Arrays;
import java.io.PrintStream;
import java.text.DecimalFormat;

import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

/**
 * @author Charles Sutton
 * @version $Id: PerClassAccuracyEvaluator.java,v 1.1 2007/10/22 21:37:48 mccallum Exp $
 */
public class PerClassAccuracyEvaluator extends TransducerEvaluator {

  private static Logger logger = MalletLogger.getLogger(TokenAccuracyEvaluator.class.getName());

  public PerClassAccuracyEvaluator (InstanceList[] instanceLists, String[] descriptions) {
		super (instanceLists, descriptions);
	}
	
  public PerClassAccuracyEvaluator (InstanceList i1, String d1) {
  	this (new InstanceList[] {i1}, new String[] {d1});
  }

  public PerClassAccuracyEvaluator (InstanceList i1, String d1, InstanceList i2, String d2) {
  	this (new InstanceList[] {i1, i2}, new String[] {d1, d2});
  }

  public void evaluateInstanceList (TransducerTrainer tt, InstanceList data, String description)
  {
  	Transducer model = tt.getTransducer();
    Alphabet dict = model.getInputPipe().getTargetAlphabet();
    int numLabels = dict.size();
    int[] numCorrectTokens = new int [numLabels];
    int[] numPredTokens = new int [numLabels];
    int[] numTrueTokens = new int [numLabels];

    logger.info("Per-token results for " + description);
    for (int i = 0; i < data.size(); i++) {
      Instance instance = data.get(i);
      Sequence input = (Sequence) instance.getData();
      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = model.transduce (input);
      assert (predOutput.size() == trueOutput.size());
      for (int j = 0; j < trueOutput.size(); j++) {
        int idx = dict.lookupIndex(trueOutput.get(j));
        numTrueTokens[idx]++;
        numPredTokens[dict.lookupIndex(predOutput.get(j))]++;
        if (trueOutput.get(j).equals(predOutput.get(j)))
          numCorrectTokens[idx]++;
      }
    }

    DecimalFormat f = new DecimalFormat ("0.####");
    double[] allf = new double [numLabels];
    for (int i = 0; i < numLabels; i++) {
      Object label = dict.lookupObject(i);
      double precision = ((double) numCorrectTokens[i]) / numPredTokens[i];
      double recall = ((double) numCorrectTokens[i]) / numTrueTokens[i];
      double f1 = (2 * precision * recall) / (precision + recall);
      if (!Double.isNaN (f1)) allf [i] = f1;
      logger.info(description +" label " + label + " P " + f.format (precision)
                  + " R " + f.format(recall) + " F1 "+ f.format (f1));
    }

    logger.info ("Macro-average F1 "+f.format (MatrixOps.mean (allf)));

  }

}
