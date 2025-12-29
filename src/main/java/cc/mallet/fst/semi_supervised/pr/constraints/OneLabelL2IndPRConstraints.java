/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.pr.constraints;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.google.errorprone.annotations.Var;

import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * A set of constraints on individual input feature label pairs.  
 * 
 * This is to be used with PR, and penalizes
 * L_2^2 difference from target expectations.
 * 
 * Multiple constraints are grouped together here
 * to make things more efficient.
 * 
 * @author Gregory Druck
 */

public class OneLabelL2IndPRConstraints implements PRConstraint {
  protected boolean normalized;
  protected int numDimensions;
  // maps between input feature indices and constraints
  protected IntObjectHashMap<OneLabelL2IndPRConstraint> constraints;
  protected StateLabelMap map;

  
  // cache of set of constrained features that fire at last FeatureVector
  // provided in preprocess call
  protected IntArrayList cache;

  public OneLabelL2IndPRConstraints(boolean normalized) {
    this.normalized = normalized;
    this.numDimensions = 0;
    this.constraints = new IntObjectHashMap<OneLabelL2IndPRConstraint>();
    // this will be set by the PRTrainer
    this.map = null;
    this.cache = new IntArrayList();
  }
  
  protected OneLabelL2IndPRConstraints(IntObjectHashMap<OneLabelL2IndPRConstraint> constraints,
      StateLabelMap map, boolean normalized) {
    this.normalized = normalized;
    this.numDimensions = 0;
    // copy constraints
    this.constraints = new IntObjectHashMap<OneLabelL2IndPRConstraint>();
    for (IntObjectCursor<OneLabelL2IndPRConstraint> keyVal : constraints) {
      this.constraints.put(keyVal.key, keyVal.value.copy());
      numDimensions += keyVal.value.getNumConstrainedLabels();
    }
    this.map = map;
    this.cache = new IntArrayList();
  }
  
  public PRConstraint copy() {
    return new OneLabelL2IndPRConstraints(this.constraints, this.map, this.normalized); 
  }

  public void addConstraint(int fi, int li, double target, double weight) {
    if (!constraints.containsKey(fi)) {
      constraints.put(fi,new OneLabelL2IndPRConstraint());
    }
    constraints.get(fi).add(li, target, weight, numDimensions);
    numDimensions++;
  }
  
  public int numDimensions() {
    return numDimensions;
  }
  
  public void setStateLabelMap(StateLabelMap map) {
    this.map = map;
  }
  
  public void preProcess(FeatureVector fv) {
    cache.clear();
    @Var
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
    @Var
    int ii = 0;
    @Var
    int fi;
    @Var
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
    @Var
    double dot = 0;
    int li2 = map.getLabelIndex(destIndex);
    for (int i = 0; i < cache.size(); i++) {
      int fi = cache.get(i);
      OneLabelL2IndPRConstraint constraint = constraints.get(fi);
      dot += constraint.getScore(li2, parameters);
    }
    return dot;
  }

  public void incrementExpectations(FeatureVector input, int inputPosition,
      int srcIndex, int destIndex, double prob) {
    int li2 = map.getLabelIndex(destIndex);
    for (int i = 0; i < cache.size(); i++) {
      constraints.get(cache.get(i)).incrementExpectation(li2, prob);
    }
  }
  
  public void getExpectations(double[] expectations) {
    assert(expectations.length == numDimensions()) : expectations.length + " " + numDimensions();
    for (ObjectCursor<OneLabelL2IndPRConstraint> cursor : constraints.values()) {
      cursor.value.getExpectations(expectations);
    }
  }
  
  public void addExpectations(double[] expectations) {
    assert(expectations.length == numDimensions());
    for (ObjectCursor<OneLabelL2IndPRConstraint> cursor : constraints.values()) {
      cursor.value.addExpectations(expectations);
    }
  }

  public void zeroExpectations() {
    for (ObjectCursor<OneLabelL2IndPRConstraint> cursor : constraints.values()) {
      cursor.value.zeroExpectation();
    }
  }

  public double getAuxiliaryValueContribution(double[] parameters) {
    @Var
    double value = 0;
    for (ObjectCursor<OneLabelL2IndPRConstraint> fi : constraints.values()) {
      OneLabelL2IndPRConstraint constraint = fi.value;
      value += constraint.getProjectionValueContrib(parameters);
    }
    return value;
  }

  public double getCompleteValueContribution(double[] parameters) {
    @Var
    double value = 0;
    for (ObjectCursor<OneLabelL2IndPRConstraint> fi : constraints.values()) {
      OneLabelL2IndPRConstraint constraint = fi.value;
      value += constraint.getCompleteValueContrib();
    }
    return value;
  }

  public void getGradient(double[] parameters, double[] gradient) {
    for (ObjectCursor<OneLabelL2IndPRConstraint> fi : constraints.values()) {
      OneLabelL2IndPRConstraint constraint = fi.value;
      constraint.getGradient(parameters, gradient);
    }
  }
  
  protected class OneLabelL2IndPRConstraint {
    protected int index;
    protected double count;
    protected ArrayList<Integer> labels;
    protected ArrayList<Integer> paramIndices;
    protected ArrayList<Double> targets;
    protected ArrayList<Double> weights;
    protected HashMap<Integer,Integer> labelMap;
    protected double[] expectation;
    
    public OneLabelL2IndPRConstraint() {
      index = 0;
      count = 0;
      labels = new ArrayList<Integer>();
      paramIndices = new ArrayList<Integer>();
      targets = new ArrayList<Double>();
      weights = new ArrayList<Double>();
      labelMap = new HashMap<Integer,Integer>();
    }
    
    public OneLabelL2IndPRConstraint copy() {
      OneLabelL2IndPRConstraint copy = new OneLabelL2IndPRConstraint();
      copy.index = index;
      copy.count = count;
      copy.labels = labels;
      copy.paramIndices = paramIndices;
      copy.targets = targets;
      copy.weights = weights;
      copy.labelMap = labelMap;
      // this will be incremented in the copy
      copy.expectation = new double[index];
      return copy;
    }
    
    public void add(int label, double target, double weight, int paramIndex) {
      targets.add(target);
      weights.add(weight);
      labels.add(label);
      paramIndices.add(paramIndex);
      labelMap.put(label, index);
      index++;
    }
    
    public void zeroExpectation() {
      this.expectation = new double[labels.size()];
    }
    
    public void getExpectations(double[] expectations) {
      for (int i = 0; i < paramIndices.size(); i++) {
        expectations[paramIndices.get(i)] = expectation[i];
      }
    }
    
    public void addExpectations(double[] expectations) {
      for (int i = 0; i < paramIndices.size(); i++) {
        expectation[i] += expectations[paramIndices.get(i)];
      }
    }
    
    public void incrementExpectation(int li, double value) {
      if (labelMap.containsKey(li)) {
        int i = labelMap.get(li);
        expectation[i] += value;
      }
    }
    
    public double getScore(int li, double[] parameters) {
      if (labelMap.containsKey(li)) {
        int i = labelMap.get(li);
        if (normalized) {
          return parameters[paramIndices.get(i)] / count;
        }
        else {
          return parameters[paramIndices.get(i)];
        }
      }
      return 0;
    }
    
    public double getProjectionValueContrib(double[] parameters) {
      @Var
      double value = 0;
      for (int i = 0; i < paramIndices.size(); i++) {
        double param = parameters[paramIndices.get(i)];
        value += targets.get(i) * param - (param * param) / (2 * weights.get(i));
      }
      return value;
    }
    
    public double getCompleteValueContrib() {
      @Var
      double value = 0;
      for (int i = 0; i < paramIndices.size(); i++) {
        if (normalized) {
          value += weights.get(i) * Math.pow(targets.get(i) - expectation[i]/count,2) / 2;
        }
        else {
          value += weights.get(i) * Math.pow(targets.get(i) - expectation[i],2) / 2;
        }
      }
      return value;
    }
    
    public void getGradient(double[] parameters, double[] gradient) {
      for (int i = 0; i < paramIndices.size(); i++) {
        int pi = paramIndices.get(i);
        if (normalized) {
          gradient[pi] += targets.get(i) - expectation[i] / count - 
            parameters[pi] / weights.get(i);
        }
        else {
          gradient[pi] += targets.get(i) - expectation[i] - 
            parameters[pi] / weights.get(i);
        }
      }
    }
    
    public int getNumConstrainedLabels() {
      return index;
    }
  }
}