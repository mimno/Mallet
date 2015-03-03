/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.constraints.pr;

import gnu.trove.map.hash.TIntIntHashMap;
import cc.mallet.types.FeatureVector;

/**
 * Expectation constraint for use with Posterior Regularization (PR).
 * Penalizes L_2^2 difference from target distribution.
 * 
 * @author Gregory Druck
 */

public class MaxEntL2FLPRConstraints extends MaxEntFLPRConstraints {
  
  private TIntIntHashMap constraintIndices;
  private boolean normalize;
  
  public MaxEntL2FLPRConstraints(int numFeatures, int numLabels, boolean useValues, boolean normalize) {
    super(numFeatures, numLabels, useValues);
    this.constraintIndices = new TIntIntHashMap();
    this.normalize = normalize;
  }
  
  @Override
  public void addConstraint(int fi, double[] ex, double weight) {
    constraints.put(fi,new MaxEntL2FLPRConstraint(ex,weight));
    constraintIndices.put(fi, constraintIndices.size());
  }
  
  protected class MaxEntL2FLPRConstraint extends MaxEntFLPRConstraint {
    public MaxEntL2FLPRConstraint(double[] target, double weight) {
      super(target, weight);
    }
  }

  public int numDimensions() {
    return constraints.size() * numLabels;
  }

  public double getAuxiliaryValueContribution(double[] parameters) {
    double value = 0;
    for (int fi : constraints.keys()) {
      int ci = constraintIndices.get(fi);
      for (int li = 0; li < numLabels; li++) {
        double param =  parameters[ci + li * constraints.size()];
        // targets dot parameters
        value += constraints.get(fi).target[li] * param;
        // regularization
        value -= param * param / (2  * constraints.get(fi).weight);
      }
    }
    return value;
  }

  public void getGradient(double[] parameters, double[] gradient) {
    for (int fi : constraints.keys()) {
      int ci = constraintIndices.get(fi);
      double norm;
      if (normalize) {
        norm = constraints.get(fi).count;
      }
      else {
        norm = 1;
      }
      for (int li = 0; li < numLabels; li++) {
        double param =  parameters[ci + li * constraints.size()];
        gradient[ci + li * constraints.size()] = constraints.get(fi).target[li] - constraints.get(fi).expectation[li] / norm;
        // regularization
        gradient[ci + li * constraints.size()] -= param / constraints.get(fi).weight;
      }
    }
  }

  public double getCompleteValueContribution() {
    double value = 0;
    for (int fi : constraints.keys()) {
      double norm;
      if (normalize) {
        norm = constraints.get(fi).count;
      }
      else {
        norm = 1;
      }
      for (int li = 0; li < numLabels; li++) {
        value -= constraints.get(fi).weight * Math.pow(constraints.get(fi).target[li] - constraints.get(fi).expectation[li] / norm, 2) / 2;
      }
    }
    return value;
  }

  public double getScore(FeatureVector input, int label, double[] parameters) {
    double score = 0;
    for (int i = 0; i < indexCache.size(); i++) {
      int ci = constraintIndices.get(indexCache.getQuick(i));
      double param = parameters[ci + label * constraints.size()];
      
      double norm;
      if (normalize) {
        norm = constraints.get(indexCache.getQuick(i)).count;
      }
      else {
        norm = 1;
      }
      
      if (useValues) {
        score += param * valueCache.getQuick(i) / norm;
      }
      else {
        score += param / norm;
      }
    }
    return score;
  }
}
