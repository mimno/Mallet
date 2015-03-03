/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.constraints;

import gnu.trove.map.hash.TIntObjectHashMap;

import cc.mallet.fst.semi_supervised.StateLabelMap;

/** 
 * A set of constraints on distributions over consecutive
 * labels conditioned an input features.  
 * 
 * This is to be used with GE, and penalizes the
 * L_2^2 difference between model and target distributions.
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */
public class OneLabelL2GEConstraints extends OneLabelGEConstraints {

  public OneLabelL2GEConstraints() {
    super();
  }
  
  private OneLabelL2GEConstraints(TIntObjectHashMap<OneLabelGEConstraint> constraints, StateLabelMap map) {
    super(constraints,map);
  }
  
  public GEConstraint copy() {
    return new OneLabelL2GEConstraints(this.constraints, this.map); 
  }
  
  @Override
  public void addConstraint(int fi, double[] target, double weight) {
    constraints.put(fi,new OneLabelGEL2Constraint(target,weight));
  }

  @Override
  public double getValue() {
    double value = 0.0;
    for (int fi : constraints.keys()) {
      OneLabelGEConstraint constraint = constraints.get(fi);
      if ( constraint.count > 0.0) {
        // value due to current constraint
        double featureValue = 0.0;
        for (int labelIndex = 0; labelIndex < map.getNumLabels(); ++labelIndex) {
          double ex = constraint.expectation[labelIndex]/constraint.count;
          featureValue -= Math.pow(constraint.target[labelIndex] - ex,2);
        }
        assert(!Double.isNaN(featureValue) &&
               !Double.isInfinite(featureValue));
        value += featureValue * constraint.weight;
      }
    }
    return value;
  }
  
  protected class OneLabelGEL2Constraint extends OneLabelGEConstraint {
    
    public OneLabelGEL2Constraint(double[] target, double weight) {
      super(target,weight);
    }
    
    public double getValue(int li) {
      assert(this.count != 0);
      return 2 * this.weight * (target[li] / count - expectation[li] / (count * count));
    }
  }
}
