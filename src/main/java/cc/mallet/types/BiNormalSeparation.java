/*
 * Copyright (C) 2015 Univ. of Massachusetts Amherst, Computer Science Dept. This file is
 * part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 * http://www.cs.umass.edu/~mccallum/mallet This software is provided under the terms of
 * the Common Public License, version 1.0, as published by http://www.opensource.org. For
 * further information, see the file `LICENSE' included with this distribution.
 */

package cc.mallet.types;

import cc.mallet.util.StatFunctions;

/**
 * Bi-Normal Separation is a feature weighting algorithm introduced in:
 *
 * An Extensive Empirical Study of Feature Selection Metrics for Text Classification,
 * George Forman, Journal of Machine Learning Research, 3:1289--1305, 2003.
 *
 * http://www.hpl.hp.com/techreports/2002/HPL-2002-88R2.html
 *
 * It was been shown to have excellent performance when used for feature selection and
 * feature weighting on binary SVM classification tasks.
 *
 * @author Clint Burford
 */
public class BiNormalSeparation extends RankedFeatureVector {
  /**
   * BNS is not defined for cases where the TPR or FPR is close to 0. These thresholds
   * prevent math errors.
   */
  private static final double BNS_MIN_RATE = 0.0005;
  private static final double BNS_MAX_RATE = 1 - BNS_MIN_RATE;

  /**
   * Calculates feature weights for features in the given instance list.
   */
  private static double[] calculateWeights(InstanceList instanceList) {
    int numFeatures = instanceList.getAlphabet().size();
    double[] weights = new double[numFeatures];
    double[] truePositives = new double[numFeatures];
    double[] falsePositives = new double[numFeatures];
    double numPos = 0;
    double numNeg = 0;
    Label posLabel = null;
    for (Instance instance : instanceList) {
      if (posLabel == null)
        posLabel = (Label) instance.getTarget();
      boolean isPos = false;
      if (posLabel.equals(instance.getTarget())) {
        isPos = true;
        numPos++;
      } else {
        numNeg++;
      }
      FeatureVector fv = (FeatureVector) instance.getData();
      for (int index : fv.getIndices()) {
        if (isPos)
          truePositives[index]++;
        else
          falsePositives[index]++;
      }
    }
    for (int i = 0; i < numFeatures; i++) {
      double tpr = 0.5;
      if (numPos > 0) {
        tpr = Math.max(Math.min(BNS_MAX_RATE, truePositives[i] / numPos), BNS_MIN_RATE);
      }
      double fpr = 0.5;
      if (numNeg > 0) {
        fpr = Math.max(Math.min(BNS_MAX_RATE, falsePositives[i] / numNeg), BNS_MIN_RATE);
      }
      weights[i] =
          Math.abs(StatFunctions.qnorm(tpr, false) - StatFunctions.qnorm(fpr, false));
    }
    return weights;
  }

  /**
   * Create a new feature ranking for the given instance list.
   */
  public BiNormalSeparation(InstanceList ilist) {
    super(ilist.getDataAlphabet(), calculateWeights(ilist));
  }

  /**
   * Factory class.
   */
  public static class Factory implements RankedFeatureVector.Factory {
    /**
     * Create a new feature ranking for the given instance list.
     *
     * @see cc.mallet.types.RankedFeatureVector.Factory#newRankedFeatureVector(cc.mallet.types.InstanceList)
     */
    @Override
    public RankedFeatureVector newRankedFeatureVector(InstanceList instanceList) {
      return new BiNormalSeparation(instanceList);
    }
  }
}
