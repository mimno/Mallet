/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.constraints;

import java.util.ArrayList;

import gnu.trove.TIntIntHashMap;

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

public class TwoLabelL2GEConstraints extends TwoLabelGEConstraints {

  public TwoLabelL2GEConstraints() {
    super();
  }
  
  private TwoLabelL2GEConstraints(ArrayList<TwoLabelGEConstraint> constraintsList, TIntIntHashMap constraintsMap, StateLabelMap map) {
    super(constraintsList,constraintsMap,map);
  }
  
  public GEConstraint copy() {
    return new TwoLabelL2GEConstraints(this.constraintsList, this.constraintsMap, this.map); 
  }
  
  @Override
  public void addConstraint(int fi, double[][] target, double weight) {
    constraintsList.add(new TwoLabelL2GEConstraint(target,weight));
    constraintsMap.put(fi, constraintsList.size()-1);
  }

  @Override
  public double getValue() {
    double value = 0.0;
    for (int fi : constraintsMap.keys()) {
      TwoLabelGEConstraint constraint = constraintsList.get(constraintsMap.get(fi));
      if (constraint.count > 0.0) {
        double constraintValue = 0.0;
        for (int prevLi = 0; prevLi < map.getNumLabels(); prevLi++) {
          for (int currLi = 0; currLi < map.getNumLabels(); currLi++) {
            constraintValue -= Math.pow(constraint.target[prevLi][currLi] -
                constraint.expectation[prevLi][currLi]/constraint.count,2);
          }
        }
        assert(!Double.isNaN(constraintValue) &&
               !Double.isInfinite(constraintValue));

        value += constraintValue * constraint.weight;
      }
    }
    return value;
  }
  
  protected class TwoLabelL2GEConstraint extends TwoLabelGEConstraint {
    
    public TwoLabelL2GEConstraint(double[][] target, double weight) {
      super(target,weight);
    }
    
    public double getValue(int liPrev, int liCurr) {
      assert(this.count != 0);
      return 2 * this.weight * (target[liPrev][liCurr] / count - expectation[liPrev][liCurr] / (count * count));
    }
  }
}
