/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.pr.constraints;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.ObjectCursor;

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
  protected IntObjectHashMap<OneLabelPRConstraint> constraints;
  // maps between input feature indices and constraint indices
  protected IntIntHashMap constraintIndices;
  protected StateLabelMap map;
  protected boolean normalized;
  
  // cache of set of constrained features that fire at last FeatureVector
  // provided in preprocess call
  protected IntArrayList cache;

  public OneLabelL2PRConstraints(boolean normalized) {
    this.constraints = new IntObjectHashMap<OneLabelPRConstraint>();
    this.constraintIndices = new IntIntHashMap();
    this.cache = new IntArrayList();
    this.normalized = normalized;
  }
  
  protected OneLabelL2PRConstraints(IntObjectHashMap<OneLabelPRConstraint> constraints,
      IntIntHashMap constraintIndices, StateLabelMap map, boolean normalized) {
    this.constraints = new IntObjectHashMap<OneLabelPRConstraint>();
    for (IntObjectCursor<OneLabelPRConstraint> keyVal : constraints) {
      this.constraints.put(keyVal.key, keyVal.value.copy());
    }
    
    //this.constraints = constraints;
    this.constraintIndices = constraintIndices;
    this.map = map;
    this.cache = new IntArrayList();
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
    cache.clear();
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
      int j = constraintIndices.get(cache.get(i));
      // TODO binary features
      if (normalized) {
        dot += parameters[j + constraints.size() * li2] / constraints.get(cache.get(i)).count;
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
      constraints.get(cache.get(i)).expectation[li2] += prob;
    }
  }
  
  public void getExpectations(double[] expectations) {
    assert(expectations.length == numDimensions());
    for (IntCursor fi : constraintIndices.keys()) {
      int ci = constraintIndices.get(fi.value);
      OneLabelPRConstraint constraint = constraints.get(fi.value);
      for (int li = 0; li < constraint.expectation.length; li++) {
        expectations[ci + li * constraints.size()] = constraint.expectation[li];
      }
    }
  }
  
  public void addExpectations(double[] expectations) {
    assert(expectations.length == numDimensions());
    for (IntCursor fi : constraintIndices.keys()) {
      int ci = constraintIndices.get(fi.value);
      OneLabelPRConstraint constraint = constraints.get(fi.value);
      for (int li = 0; li < constraint.expectation.length; li++) {
        constraint.expectation[li] += expectations[ci + li * constraints.size()];
      }
    }
  }

  public void zeroExpectations() {
    for (ObjectCursor<OneLabelPRConstraint> fi : constraints.values()) {
      fi.value.expectation = new double[map.getNumLabels()];
    }
  }

  public double getAuxiliaryValueContribution(double[] parameters) {
    double value = 0;
    for (IntObjectCursor<OneLabelPRConstraint> fi : constraints) {
      int ci = constraintIndices.get(fi.key);
      for (int li = 0; li < map.getNumLabels(); li++) {
        double param = parameters[ci + li * constraints.size()];
        value += fi.value.target[li] * param - (param * param) / (2 * fi.value.weight);
      }
    }
    return value;
  }

  // TODO
  public double getCompleteValueContribution(double[] parameters) {
    double value = 0;
    for (IntObjectCursor<OneLabelPRConstraint> fi : constraints) {
      OneLabelPRConstraint constraint = fi.value;
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
    for (IntObjectCursor<OneLabelPRConstraint> fi : constraints) {
      int ci = constraintIndices.get(fi.key);
      OneLabelPRConstraint constraint = fi.value;
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