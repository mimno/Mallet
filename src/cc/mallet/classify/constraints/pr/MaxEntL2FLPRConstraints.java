/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.constraints.pr;

import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.carrotsearch.hppc.IntIntHashMap;

import cc.mallet.types.FeatureVector;

/**
 * Expectation constraint for use with Posterior Regularization (PR).
 * Penalizes L_2^2 difference from target distribution.
 * 
 * @author Gregory Druck
 */

public class MaxEntL2FLPRConstraints extends MaxEntFLPRConstraints {
  
  private IntIntHashMap constraintIndices;
  private boolean normalize;
  
  public MaxEntL2FLPRConstraints(int numFeatures, int numLabels, boolean useValues, boolean normalize) {
    super(numFeatures, numLabels, useValues);
    this.constraintIndices = new IntIntHashMap();
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
    for (IntObjectCursor<MaxEntFLPRConstraint> fi : constraints) {
      int ci = constraintIndices.get(fi.key);
      for (int li = 0; li < numLabels; li++) {
        double param =  parameters[ci + li * constraints.size()];
        // targets dot parameters
        value += fi.value.target[li] * param;
        // regularization
        value -= param * param / (2  * fi.value.weight);
      }
    }
    return value;
  }

  public void getGradient(double[] parameters, double[] gradient) {
    for (IntObjectCursor<MaxEntFLPRConstraint> fi : constraints) {
      int ci = constraintIndices.get(fi.key);
      double norm;
      if (normalize) {
        norm = fi.value.count;
      }
      else {
        norm = 1;
      }
      for (int li = 0; li < numLabels; li++) {
        double param =  parameters[ci + li * constraints.size()];
        gradient[ci + li * constraints.size()] = fi.value.target[li] - fi.value.expectation[li] / norm;
        // regularization
        gradient[ci + li * constraints.size()] -= param / fi.value.weight;
      }
    }
  }

  public double getCompleteValueContribution() {
    double value = 0;
    for (IntObjectCursor<MaxEntFLPRConstraint> fi : constraints) {
      double norm;
      if (normalize) {
        norm = fi.value.count;
      }
      else {
        norm = 1;
      }
      for (int li = 0; li < numLabels; li++) {
        value -= fi.value.weight * Math.pow(fi.value.target[li] - fi.value.expectation[li] / norm, 2) / 2;
      }
    }
    return value;
  }

  public double getScore(FeatureVector input, int label, double[] parameters) {
    double score = 0;
    for (int i = 0; i < indexCache.size(); i++) {
      int ci = constraintIndices.get(indexCache.get(i));
      double param = parameters[ci + label * constraints.size()];
      
      double norm;
      if (normalize) {
        norm = constraints.get(indexCache.get(i)).count;
      }
      else {
        norm = 1;
      }
      
      if (useValues) {
        score += param * valueCache.get(i) / norm;
      }
      else {
        score += param / norm;
      }
    }
    return score;
  }
}
