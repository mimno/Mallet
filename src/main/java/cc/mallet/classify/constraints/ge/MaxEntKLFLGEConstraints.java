/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.constraints.ge;

import cc.mallet.types.MatrixOps;
import cc.mallet.util.Maths;

import com.carrotsearch.hppc.cursors.ObjectCursor;

/**
 * Expectation constraint for use with GE.
 * Penalizes KL divergence from target distribution. 
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */

public class MaxEntKLFLGEConstraints extends MaxEntFLGEConstraints {
  
  public MaxEntKLFLGEConstraints(int numFeatures, int numLabels, boolean useValues) {
    super(numFeatures, numLabels, useValues);
  }

  public double getValue() {
    double value = 0.0;
    for (ObjectCursor<MaxEntFLGEConstraint> fi : constraints.values()) {
      MaxEntFLGEConstraint constraint = fi.value;
      if (constraint.count > 0.0) {
        double constraintValue = 0.0;
        for (int labelIndex = 0; labelIndex < numLabels; ++labelIndex) {
          if (constraint.target[labelIndex] > 0.0) {
            // if target is non-zero and expectation is 0, infinite penalty
            if (constraint.expectation[labelIndex] == 0.0) {
              return Double.NEGATIVE_INFINITY;
            }
            else {
              // p*log(q) - p*log(p)
              // negative KL
              constraintValue += constraint.target[labelIndex] * 
                  (Math.log(constraint.expectation[labelIndex]/constraint.count) - 
                  Math.log(constraint.target[labelIndex]));
            }
          }
        }
        assert(!Double.isNaN(constraintValue) &&
               !Double.isInfinite(constraintValue));

        value += constraintValue * constraint.weight;
      }
    }
    return value;
  }

  @Override
  public void addConstraint(int fi, double[] ex, double weight) {
    assert(Maths.almostEquals(MatrixOps.sum(ex),1));
    constraints.put(fi,new MaxEntKLFLGEConstraint(ex,weight));
  }
  
  protected class MaxEntKLFLGEConstraint extends MaxEntFLGEConstraint {
    public MaxEntKLFLGEConstraint(double[] target, double weight) {
      super(target, weight);
    }

    @Override
    public double getValue(int li) {
      assert(this.count != 0);
      if (this.target[li] == 0 && this.expectation[li] == 0) {
        return 0;
      }
      return this.weight * (this.target[li] / this.expectation[li]);
    }
  }
}
