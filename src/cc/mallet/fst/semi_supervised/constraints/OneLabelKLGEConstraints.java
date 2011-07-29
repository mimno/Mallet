/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.constraints;

import gnu.trove.TIntObjectHashMap;

import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.Maths;

/** 
 * A set of constraints on distributions over consecutive
 * labels conditioned an input features.  
 * 
 * This is to be used with GE, and penalizes the
 * KL divergence between model and target distributions.
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */
public class OneLabelKLGEConstraints extends OneLabelGEConstraints {

  public OneLabelKLGEConstraints() {
    super();
  }
  
  private OneLabelKLGEConstraints(TIntObjectHashMap<OneLabelGEConstraint> constraints, StateLabelMap map) {
    super(constraints,map);
  }
  
  public GEConstraint copy() {
    return new OneLabelKLGEConstraints(this.constraints, this.map); 
  }
  
  @Override
  public void addConstraint(int fi, double[] target, double weight) {
    assert(Maths.almostEquals(MatrixOps.sum(target),1));
    constraints.put(fi,new OneLabelGEKLConstraint(target,weight));
  }

  @Override
  public double getValue() {
    double value = 0.0;
    for (int fi : constraints.keys()) {
      OneLabelGEConstraint constraint = constraints.get(fi);
      if (constraint.count > 0.0) {
        double constraintValue = 0.0;
        for (int labelIndex = 0; labelIndex < map.getNumLabels(); ++labelIndex) {
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
  
  protected class OneLabelGEKLConstraint extends OneLabelGEConstraint {
    
    public OneLabelGEKLConstraint(double[] target, double weight) {
      super(target,weight);
    }
    
    public double getValue(int li) {
      assert(this.count != 0);
      if (this.target[li] == 0 && this.expectation[li] == 0) {
        return 0;
      }
      return this.weight * (this.target[li] / ( this.expectation[li] ));
    }
  }
}