package cc.mallet.classify;

import cc.mallet.types.Instance;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;

/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/**
 * Classifer for an ensemble of classifers, combined with learned weights.
 * The procedure is to obtain the score from each classifier (typically p(y|x)),
 * perform the weighted sum of these scores, then exponentiate the summed
 * score for each class, and re-normalize the resulting per-class scores.
 * In other words, the scores of the ensemble classifiers are treated as
 * input features in a Maximum Entropy classifier. 
 * @author <a href="mailto:mccallum@cs.umass.edu">Andrew McCallum</a>
 */
public class ClassifierEnsemble extends Classifier
{
  Classifier[] ensemble;
  double[] weights;

  public ClassifierEnsemble (Classifier[] classifiers, double[] weights)
  {
    this.ensemble = new Classifier[classifiers.length];
    for (int i = 0; i < classifiers.length; i++) {
      if (i > 0 && ensemble[i-1].getLabelAlphabet() != classifiers[i].getLabelAlphabet())
        throw new IllegalStateException("LabelAlphabet's do not match.");
      ensemble[i] = classifiers[i];
    }
    System.arraycopy (classifiers, 0, ensemble, 0, classifiers.length);
    this.weights = (double[]) weights.clone();
  }

  public Classification classify (Instance instance)
  {
    int numLabels = ensemble[0].getLabelAlphabet().size();
    double[] scores = new double[numLabels];
    // Run each classifier on the instance, summing each one's per-class score, with a weight
    for (int i = 0; i < ensemble.length; i++) {
      Classification c = ensemble[i].classify(instance);
      c.getLabelVector().addTo(scores, weights[i]);
    }
    // Exponentiate and normalize scores
    expNormalize (scores);
    return new Classification (instance, this, new LabelVector (ensemble[0].getLabelAlphabet(), scores));
  }

  private static void expNormalize (double[] a)
  {
    double max = MatrixOps.max (a);
    double sum = 0;
    for (int i = 0; i < a.length; i++) {
      assert(!Double.isNaN(a[i]));
      a[i] = Math.exp (a[i] - max);
      sum += a[i];
    }
    for (int i = 0; i < a.length; i++) {
      a[i] /= sum;
    }
  }

}
