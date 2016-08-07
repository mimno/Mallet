/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.pr.constraints;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.BitSet;
import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * A set of constraints on distributions over single
 * labels conditioned on the presence of input features. 
 * 
 * This is to be used with PR, and penalizes
 * L_2^2 difference from target expectations.
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */

public class OneLabelL2PRConstraints implements PRConstraint {

  // maps between input feature indices and constraints
  protected TIntObjectHashMap<OneLabelPRConstraint> constraints;
  // maps between input feature indices and constraint indices
  protected TIntIntHashMap constraintIndices;
  protected StateLabelMap map;
  protected boolean normalized;
  
  // cache of set of constrained features that fire at last FeatureVector
  // provided in preprocess call
  protected TIntArrayList cache;

  public OneLabelL2PRConstraints(boolean normalized) {
    this.constraints = new TIntObjectHashMap<OneLabelPRConstraint>();
    this.constraintIndices = new TIntIntHashMap();
    this.cache = new TIntArrayList();
    this.normalized = normalized;
  }
  
  protected OneLabelL2PRConstraints(TIntObjectHashMap<OneLabelPRConstraint> constraints,
      TIntIntHashMap constraintIndices, StateLabelMap map, boolean normalized) {
    this.constraints = new TIntObjectHashMap<OneLabelPRConstraint>();
    for (int key : constraints.keys()) {
      this.constraints.put(key, constraints.get(key).copy());
    }
    
    //this.constraints = constraints;
    this.constraintIndices = constraintIndices;
    this.map = map;
    this.cache = new TIntArrayList();
    this.normalized = normalized;
  }
  
  public PRConstraint copy() {
    return new OneLabelL2PRConstraints(this.constraints, this.constraintIndices, this.map, this.normalized); 
  }

  public void addConstraint(int fi, double[] target, double weight) {
    constraints.put(fi,new OneLabelPRConstraint(target,weight));
    constraintIndices.put(fi, constraintIndices.size());
  }
  
  public int numDimensions() {
    assert(map != null);
    return map.getNumLabels() * constraints.size();
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
      }
      ii++;
    }
    return bitSet;
  }    
  
  public double getScore(FeatureVector input, int inputPosition,
      int srcIndex, int destIndex, double[] parameters) {
    double dot = 0;
    int li2 = map.getLabelIndex(destIndex);
    for (int i = 0; i < cache.size(); i++) {
      int j = constraintIndices.get(cache.getQuick(i));
      // TODO binary features
      if (normalized) {
        dot += parameters[j + constraints.size() * li2] / constraints.get(cache.getQuick(i)).count; 
      }
      else {
        dot += parameters[j + constraints.size() * li2]; 
      }
    }
    return dot;
  }

  public void incrementExpectations(FeatureVector input, int inputPosition,
      int srcIndex, int destIndex, double prob) {
    int li2 = map.getLabelIndex(destIndex);
    for (int i = 0; i < cache.size(); i++) {
      constraints.get(cache.getQuick(i)).expectation[li2] += prob;
    }
  }
  
  public void getExpectations(double[] expectations) {
    assert(expectations.length == numDimensions());
    for (int fi : constraintIndices.keys()) {
      int ci = constraintIndices.get(fi);
      OneLabelPRConstraint constraint = constraints.get(fi);
      for (int li = 0; li < constraint.expectation.length; li++) {
        expectations[ci + li * constraints.size()] = constraint.expectation[li];
      }
    }
  }
  
  public void addExpectations(double[] expectations) {
    assert(expectations.length == numDimensions());
    for (int fi : constraintIndices.keys()) {
      int ci = constraintIndices.get(fi);
      OneLabelPRConstraint constraint = constraints.get(fi);
      for (int li = 0; li < constraint.expectation.length; li++) {
        constraint.expectation[li] += expectations[ci + li * constraints.size()];
      }
    }
  }

  public void zeroExpectations() {
    for (int fi : constraints.keys()) {
      constraints.get(fi).expectation = new double[map.getNumLabels()];
    }
  }

  public double getAuxiliaryValueContribution(double[] parameters) {
    double value = 0;
    for (int fi : constraints.keys()) {
      int ci = constraintIndices.get(fi);
      for (int li = 0; li < map.getNumLabels(); li++) {
        double param = parameters[ci + li * constraints.size()];
        value += constraints.get(fi).target[li] * param - (param * param) / (2 * constraints.get(fi).weight);
      }
    }
    return value;
  }

  // TODO
  public double getCompleteValueContribution(double[] parameters) {
    double value = 0;
    for (int fi : constraints.keys()) {
      OneLabelPRConstraint constraint = constraints.get(fi);
      for (int li = 0; li < map.getNumLabels(); li++) {
        if (normalized) {
          value +=  constraint.weight * Math.pow(constraint.target[li] - constraint.expectation[li]/constraint.count,2) / 2;
        }
        else {
          value +=  constraint.weight * Math.pow(constraint.target[li] - constraint.expectation[li],2) / 2;
        }
      }
    }
    return value;
  }

  public void getGradient(double[] parameters, double[] gradient) {
    for (int fi : constraints.keys()) {
      int ci = constraintIndices.get(fi);
      OneLabelPRConstraint constraint = constraints.get(fi);
      for (int li = 0; li < map.getNumLabels(); li++) {
        if (normalized) {
          gradient[ci + li * constraints.size()] = 
            constraint.target[li] - constraint.expectation[li] / constraint.count - 
            parameters[ci + li * constraints.size()] / constraint.weight;
        }
        else {
          gradient[ci + li * constraints.size()] = 
            constraint.target[li] - constraint.expectation[li] - 
            parameters[ci + li * constraints.size()] / constraint.weight;
        }
      }
    }
  }
  
  protected class OneLabelPRConstraint {
    
    protected double[] target;
    protected double[] expectation;
    protected double count;
    protected double weight;
    
    public OneLabelPRConstraint(double[] target, double weight) {
      this.target = target;
      this.weight = weight;
      this.expectation = null;
      this.count = 0;
    }
    
    public OneLabelPRConstraint copy() {
      OneLabelPRConstraint copy = new OneLabelPRConstraint(target,weight);
      copy.count = count;
      copy.expectation = new double[target.length];
      return copy;
    }
  }
}