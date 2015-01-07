/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.constraints;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.ArrayList;
import java.util.BitSet;

import cc.mallet.fst.SumLattice;
import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/** 
 * A set of constraints on distributions over pairs of consecutive
 * labels conditioned on the presence of input features.    
 * 
 * Subclasses are to be used with GE.
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */
public abstract class TwoLabelGEConstraints implements GEConstraint {

  protected ArrayList<TwoLabelGEConstraint> constraintsList;
  protected TIntIntHashMap constraintsMap;
  protected StateLabelMap map;
  protected TIntArrayList cache;

  public TwoLabelGEConstraints() {
    this.constraintsList = new ArrayList<TwoLabelGEConstraint>();
    this.constraintsMap = new TIntIntHashMap();
    this.map = null;
    this.cache = new TIntArrayList();
  }
  
  protected TwoLabelGEConstraints(ArrayList<TwoLabelGEConstraint> constraintsList, TIntIntHashMap constraintsMap, StateLabelMap map) {
    this.constraintsList = constraintsList;
    this.constraintsMap = constraintsMap;
    this.map = map;
    this.cache = new TIntArrayList();
  }
  
  /**
   * @param fi Input feature index
   * @param target Target distribution over pairs of labels
   * @param weight Weight of this constraint
   */
  public abstract void addConstraint(int fi, double[][] target, double weight);
  
  public boolean isOneStateConstraint() {
    return false;
  }
  
  public void setStateLabelMap(StateLabelMap map) {
    this.map = map;
  }
  
  public void preProcess(FeatureVector fv) {
    cache.resetQuick();
    int fi;
    for (int loc = 0; loc < fv.numLocations(); loc++) {
      fi = fv.indexAtLocation(loc);
      if (constraintsMap.containsKey(fi)) {
        cache.add(constraintsMap.get(fi));
      }
    }
  }
  
  public BitSet preProcess(InstanceList data) {
    // count
    BitSet bitSet = new BitSet(data.size());
    int ii = 0;
    for (Instance instance : data) {
      FeatureVectorSequence fvs = (FeatureVectorSequence)instance.getData();
      for (int ip = 1; ip < fvs.size(); ip++) {
        for (int fi : constraintsMap.keys()) {
          // binary constraint features
          if (fvs.get(ip).location(fi) >= 0) {
            constraintsList.get(constraintsMap.get(fi)).count += 1;
            bitSet.set(ii);
          }
        }
      }
      ii++;
    }
    return bitSet;
  }    
  
  public double getCompositeConstraintFeatureValue(FeatureVector fv, int ip, int si1, int si2) {
    // to avoid complications with the start state,
    // only consider transitions into states at 
    // position >= 1
    if (ip == 0) {
      return 0;
    }
    
    double value = 0;
    int li1 = map.getLabelIndex(si1);
    if (li1 == StateLabelMap.START_LABEL) {
      return 0;
    }
    
    int li2 = map.getLabelIndex(si2);
    for (int i = 0; i < cache.size(); i++) {
      value += constraintsList.get(cache.getQuick(i)).getValue(li1,li2);
    }
    return value;
  }

  public abstract double getValue();

  public void zeroExpectations() {
    for (TwoLabelGEConstraint constraint : constraintsList) {
      constraint.expectation = new double[map.getNumLabels()][map.getNumLabels()];
    }
  }
  
  public void computeExpectations(ArrayList<SumLattice> lattices) {
    double[][][] xis;
    TIntArrayList cache = new TIntArrayList();
    for (int i = 0; i < lattices.size(); i++) {
      if (lattices.get(i) == null) { continue; }
      FeatureVectorSequence fvs = (FeatureVectorSequence)lattices.get(i).getInput();
      SumLattice lattice = lattices.get(i);
      xis = lattice.getXis();
      for (int ip = 1; ip < fvs.size(); ++ip) {
        cache.resetQuick();
        FeatureVector fv = fvs.getFeatureVector(ip);
        int fi;
        for (int loc = 0; loc < fv.numLocations(); loc++) {
          fi = fv.indexAtLocation(loc);
          // binary constraint features
          if (constraintsMap.containsKey(fi)) {
            cache.add(constraintsMap.get(fi));
          }
        }
        for (int prev = 0; prev < map.getNumStates(); ++prev) {
          int liPrev = map.getLabelIndex(prev);
          if (liPrev != StateLabelMap.START_LABEL) {
            for (int curr = 0; curr < map.getNumStates(); ++curr) {
              int liCurr = map.getLabelIndex(curr);
              if (liCurr != StateLabelMap.START_LABEL) {
                double prob = Math.exp(xis[ip][prev][curr]);
                for (int j = 0; j < cache.size(); j++) {
                  constraintsList.get(cache.getQuick(j)).expectation[liPrev][liCurr] += prob;
                }
              }
            }
          }
        }
      }
    }
  }
  
  protected abstract class TwoLabelGEConstraint {
    
    protected double[][] target;
    protected double[][] expectation;
    protected double count;
    protected double weight;
    
    public TwoLabelGEConstraint(double[][] target, double weight) {
      this.target = target;
      this.weight = weight;
      this.expectation = null;
      this.count = 0;
    }
    
    public abstract double getValue(int liPrev, int liCurr);
  }
}