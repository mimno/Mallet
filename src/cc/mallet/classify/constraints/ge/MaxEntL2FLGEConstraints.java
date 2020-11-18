/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.constraints.ge;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.google.errorprone.annotations.Var;

/**
 * Expectation constraint for use with GE.
 * Penalizes L_2^2 difference from target expectation. 
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */

public class MaxEntL2FLGEConstraints extends MaxEntFLGEConstraints {
  
  private boolean normalize;
  
  public MaxEntL2FLGEConstraints(int numFeatures, int numLabels, boolean useValues, boolean normalize) {
    super(numFeatures, numLabels, useValues);
    this.normalize = normalize;
  }
  
  public double getValue() {
    @Var
    double value = 0.0;
    for (ObjectCursor<MaxEntFLGEConstraint> fi : constraints.values()) {
      MaxEntFLGEConstraint constraint = fi.value;
      if ( constraint.count > 0.0) {
        // value due to current constraint
        @Var
        double featureValue = 0.0;
        for (int labelIndex = 0; labelIndex < numLabels; ++labelIndex) {
          double ex;
          if (normalize) {
            ex = constraint.expectation[labelIndex]/constraint.count;
          }
          else {
            ex = constraint.expectation[labelIndex];
          }
          featureValue -= Math.pow(constraint.target[labelIndex] - ex,2);
        }
        assert(!Double.isNaN(featureValue) &&
               !Double.isInfinite(featureValue));
        value += featureValue * constraint.weight;
      }
    }
    return value;
  }
  
  @Override
  public void addConstraint(int fi, double[] ex, double weight) {
    constraints.put(fi,new MaxEntL2FLGEConstraint(ex,weight));
  }
  
  protected class MaxEntL2FLGEConstraint extends MaxEntFLGEConstraint {
    public MaxEntL2FLGEConstraint(double[] target, double weight) {
      super(target, weight);
    }

    @Override
    public double getValue(int li) {
      assert(this.count != 0);
      
      if (normalize) {
        return 2 * this.weight * (target[li] / count - expectation[li] / (count * count));
      }
      else {
        return 2 * this.weight * (target[li] - expectation[li]);
      }
    }
  }
}
