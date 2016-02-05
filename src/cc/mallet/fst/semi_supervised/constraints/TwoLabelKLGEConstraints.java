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
 * KL divergence between model and target distributions.
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */
public class TwoLabelKLGEConstraints extends TwoLabelGEConstraints {

  public TwoLabelKLGEConstraints() {
    super();
  }
  
  private TwoLabelKLGEConstraints(ArrayList<TwoLabelGEConstraint> constraintsList, TIntIntHashMap constraintsMap, StateLabelMap map) {
    super(constraintsList,constraintsMap,map);
  }
  
  public GEConstraint copy() {
    return new TwoLabelKLGEConstraints(this.constraintsList, this.constraintsMap, this.map); 
  }
  
  @Override
  public void addConstraint(int fi, double[][] target, double weight) {
    constraintsList.add(new TwoLabelKLGEConstraint(target,weight));
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
            if (constraint.target[prevLi][currLi] > 0.0) {
              if (constraint.expectation[prevLi][currLi] == 0.0) {
                return Double.NEGATIVE_INFINITY;
              }
              else {
                // p*log(q) - p*log(p)
                // negative KL
                constraintValue += constraint.target[prevLi][currLi] * (
                    Math.log(constraint.expectation[prevLi][currLi]/constraint.count) - 
                    Math.log(constraint.target[prevLi][currLi]));
              }
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

  protected class TwoLabelKLGEConstraint extends TwoLabelGEConstraint {

    public TwoLabelKLGEConstraint(double[][] target, double weight) {
      super(target,weight);
    }
    
    @Override
    public double getValue(int liPrev, int liCurr) {
      assert(this.count != 0);
      if (this.target[liPrev][liCurr] == 0 && this.expectation[liPrev][liCurr] == 0) {
        return 0;
      }
      return this.weight * (this.target[liPrev][liCurr] / ( this.expectation[liPrev][liCurr] ));
    }
  }
}
