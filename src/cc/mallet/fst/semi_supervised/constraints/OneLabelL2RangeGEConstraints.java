/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.constraints;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

import cc.mallet.fst.SumLattice;
import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * A set of constraints on individual input feature label pairs.   
 * 
 * This is to be used with GE, and penalizes the
 * L_2^2 difference between model and target distributions.
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */

public class OneLabelL2RangeGEConstraints implements GEConstraint {

  // maps between input feature indices and constraints
  protected TIntObjectHashMap<OneLabelL2IndGEConstraint> constraints;
  protected StateLabelMap map;
  
  // cache of set of constrained features that fire at last FeatureVector
  // provided in preprocess call
  protected TIntArrayList cache;

  public OneLabelL2RangeGEConstraints() {
    this.constraints = new TIntObjectHashMap<OneLabelL2IndGEConstraint>();
    this.cache = new TIntArrayList();
  }
  
  protected OneLabelL2RangeGEConstraints(TIntObjectHashMap<OneLabelL2IndGEConstraint> constraints, StateLabelMap map) {
    this.constraints = constraints;
    this.map = map;
    this.cache = new TIntArrayList();
  }
  
  public void addConstraint(int fi, int li, double lower, double upper, double weight) {
    if (!constraints.containsKey(fi)) {
      constraints.put(fi,new OneLabelL2IndGEConstraint());
    }
    constraints.get(fi).add(li, lower, upper, weight);
  }
  
  public boolean isOneStateConstraint() {
    return true;
  }
  
  public void setStateLabelMap(StateLabelMap map) {
    this.map = map;
  }
  
  public void preProcess(FeatureVector fv) {
    cache.resetQuick();
    int fi;
    // cache constrained input features
    for (int loc = 0; loc < fv.numLocations(); loc++) {
      fi = fv.indexAtLocation(loc);
      if (constraints.containsKey(fi)) {
        cache.add(fi);
      }
    }
    if (constraints.containsKey(fv.getAlphabet().size())) {
      cache.add(fv.getAlphabet().size());
    }
  }
  
  // find examples that contain constrained input features
  public BitSet preProcess(InstanceList data) {
    // count
    int ii = 0;
    int fi;
    FeatureVector fv;
    BitSet bitSet = new BitSet(data.size());
    for (Instance instance : data) {
      FeatureVectorSequence fvs = (FeatureVectorSequence)instance.getData();
      for (int ip = 0; ip < fvs.size(); ip++) {
        fv = fvs.get(ip);
        for (int loc = 0; loc < fv.numLocations(); loc++) {
          fi = fv.indexAtLocation(loc);
          if (constraints.containsKey(fi)) {
            constraints.get(fi).count += 1;
            bitSet.set(ii);
          }
        }
        if (constraints.containsKey(fv.getAlphabet().size())) {
          bitSet.set(ii);
          constraints.get(fv.getAlphabet().size()).count += 1;
        }
      }

      ii++;
    }
    return bitSet;
  }    
  
  public double getCompositeConstraintFeatureValue(FeatureVector fv, int ip, int si1, int si2) {
    double value = 0;
    int li2 = map.getLabelIndex(si2);
    for (int i = 0; i < cache.size(); i++) {
      value += constraints.get(cache.getQuick(i)).getGradientContribution(li2);
    }
    return value;
  }

  public double getValue() {
    double value = 0.0;
    for (int fi : constraints.keys()) {
      OneLabelL2IndGEConstraint constraint = constraints.get(fi);
      if ( constraint.count > 0.0) {
        // value due to current constraint
        for (int labelIndex = 0; labelIndex < map.getNumLabels(); ++labelIndex) {
          value -= constraint.getValueContribution(labelIndex);
        }
      }
    }
    assert(!Double.isNaN(value) && !Double.isInfinite(value));
    return value;
  }

  public void zeroExpectations() {
    for (int fi : constraints.keys()) {
      constraints.get(fi).expectation = new double[constraints.get(fi).getNumConstrainedLabels()];
    }
  }
  
  public void computeExpectations(ArrayList<SumLattice> lattices) {
    double[][] gammas;    
    TIntArrayList cache = new TIntArrayList();
    for (int i = 0; i < lattices.size(); i++) {
      if (lattices.get(i) == null) { continue; }
      SumLattice lattice = lattices.get(i);
      FeatureVectorSequence fvs = (FeatureVectorSequence)lattice.getInput();
      gammas = lattice.getGammas();
      for (int ip = 0; ip < fvs.size(); ++ip) {
        cache.resetQuick();
        FeatureVector fv = fvs.getFeatureVector(ip);
        int fi;
        for (int loc = 0; loc < fv.numLocations(); loc++) {
          fi = fv.indexAtLocation(loc);
          // binary constraint features
          if (constraints.containsKey(fi)) {
            cache.add(fi);
          }
        }
        if (constraints.containsKey(fv.getAlphabet().size())) {
          cache.add(fv.getAlphabet().size());
        }
        for (int s = 0; s < map.getNumStates(); ++s) {
          int li = map.getLabelIndex(s);
          if (li != StateLabelMap.START_LABEL) {
            double gammaProb = Math.exp(gammas[ip+1][s]);
            for (int j = 0; j < cache.size(); j++) {
              constraints.get(cache.getQuick(j)).incrementExpectation(li,gammaProb);
            }
          }
        }
      }
    }
  }
  
  public GEConstraint copy() {
    return new OneLabelL2RangeGEConstraints(this.constraints, this.map); 
  }
  
  protected class OneLabelL2IndGEConstraint {
    
    protected int index;
    protected double count;
    protected ArrayList<Double> lower;
    protected ArrayList<Double> upper;
    protected ArrayList<Double> weights;
    protected HashMap<Integer,Integer> labelMap;
    protected double[] expectation;

    public OneLabelL2IndGEConstraint() {
      lower = new ArrayList<Double>();
      upper = new ArrayList<Double>();
      weights = new ArrayList<Double>();
      labelMap = new HashMap<Integer,Integer>();
      index = 0;
      count = 0;
    }
    
    public void add(int label, double lower, double upper, double weight) {
      this.lower.add(lower);
      this.upper.add(upper);
      this.weights.add(weight);
      labelMap.put(label, index);
      index++;
    }
    
    public void incrementExpectation(int li, double value) {
      if (labelMap.containsKey(li)) {
        int i = labelMap.get(li);
        expectation[i] += value;
      }
    }
    
    public double getValueContribution(int li) {
      if (labelMap.containsKey(li)) {
        int i = labelMap.get(li);
        assert(this.count != 0);
        
        double ex = this.expectation[i] / this.count;
        if (ex < lower.get(i)) {
          return weights.get(i) * Math.pow(lower.get(i) - ex,2);
        }
        else if (ex > upper.get(i)) {
          return weights.get(i) * Math.pow(upper.get(i) - ex,2);         
        }
      }
      return 0;
    }
    
    public int getNumConstrainedLabels() {
      return index;
    }
    
    public double getGradientContribution(int li) {
      if (labelMap.containsKey(li)) {
        int i = labelMap.get(li);
        assert(this.count != 0);
        
        double ex = this.expectation[i] / this.count;
        if (ex < lower.get(i)) {
          return 2 * weights.get(i) * (lower.get(i) / count - expectation[i] / (count * count));
        }
        else if (ex > upper.get(i)) {
          return 2 * weights.get(i) * (upper.get(i) / count - expectation[i] / (count * count));          
        }
      }
      return 0;
    }
  }
}