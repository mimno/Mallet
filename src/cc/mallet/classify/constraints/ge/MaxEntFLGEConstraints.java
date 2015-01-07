/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.constraints.ge;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.BitSet;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * Abstract expectation constraint for use with Generalized Expectation (GE).
 * 
 * @author Gregory Druck
 */

public abstract class MaxEntFLGEConstraints implements MaxEntGEConstraint {

  protected boolean useValues;
  protected int numLabels;
  protected int numFeatures;
  
  // maps between input feature indices and constraints
  protected TIntObjectHashMap<MaxEntFLGEConstraint> constraints;
  
  // cache of set of constrained features that fire at last FeatureVector
  // provided in preprocess call
  protected TIntArrayList indexCache;
  protected TDoubleArrayList valueCache;
  
  public MaxEntFLGEConstraints(int numFeatures, int numLabels, boolean useValues) {
    this.numFeatures = numFeatures;
    this.numLabels = numLabels;
    this.useValues = useValues;
    this.constraints = new TIntObjectHashMap<MaxEntFLGEConstraint>();
    this.indexCache = new TIntArrayList();
    this.valueCache = new TDoubleArrayList();
  }

  public abstract void addConstraint(int fi, double[] ex, double weight);
  
  public double getCompositeConstraintFeatureValue(FeatureVector input, int label) {
    double value = 0;
    for (int i = 0; i < indexCache.size(); i++) {
      if (useValues) {
        value += constraints.get(indexCache.getQuick(i)).getValue(label) * valueCache.getQuick(i);
      }
      else {
        value += constraints.get(indexCache.getQuick(i)).getValue(label);
      }
    }
    return value;
  }

  public void computeExpectations(FeatureVector input, double[] dist, double weight) {
    preProcess(input);
    for (int li = 0; li < numLabels; li++) {
      double p = weight * dist[li];
      for (int i = 0; i < indexCache.size(); i++) {
        if (useValues) {
          constraints.get(indexCache.getQuick(i)).expectation[li] += p * valueCache.getQuick(i); 
        }
        else {
          constraints.get(indexCache.getQuick(i)).expectation[li] += p; 
        }
      }
    }
  }

  public void zeroExpectations() {
    for (int fi : constraints.keys()) {
      constraints.get(fi).expectation = new double[numLabels];
    }
  }

  public BitSet preProcess(InstanceList data) {
    // count
    int ii = 0;
    int fi;
    FeatureVector fv;
    BitSet bitSet = new BitSet(data.size());
    for (Instance instance : data) {
      double weight = data.getInstanceWeight(instance);
      fv = (FeatureVector)instance.getData();
      for (int loc = 0; loc < fv.numLocations(); loc++) {
        fi = fv.indexAtLocation(loc);
        if (constraints.containsKey(fi)) {
          if (useValues) {
            constraints.get(fi).count += weight * fv.valueAtLocation(loc);
          }
          else {
            constraints.get(fi).count += weight; 
          }
          bitSet.set(ii);
        }
      }
      ii++;
      // default feature, for label regularization
      if (constraints.containsKey(numFeatures)) {
        bitSet.set(ii);
        constraints.get(numFeatures).count += weight; 
      }
    }
    return bitSet;
  }

  public void preProcess(FeatureVector input) {
    indexCache.resetQuick();
    if (useValues) valueCache.resetQuick();
    int fi;
    // cache constrained input features
    for (int loc = 0; loc < input.numLocations(); loc++) {
      fi = input.indexAtLocation(loc);
      if (constraints.containsKey(fi)) {
        indexCache.add(fi);
        if (useValues) valueCache.add(input.valueAtLocation(loc));
      }
    }
    
    // default feature, for label regularization
    if (constraints.containsKey(numFeatures)) {
      indexCache.add(numFeatures);
      if (useValues) valueCache.add(1);
    }
  }

  protected abstract class MaxEntFLGEConstraint {
    
    protected double[] target;
    protected double[] expectation;
    protected double count;
    protected double weight;
    
    public MaxEntFLGEConstraint(double[] target, double weight) {
      this.target = target;
      this.weight = weight;
      this.expectation = null;
      this.count = 0;
    }
    
    public double[] getTarget() { 
      return target;
    }
    
    public double[] getExpectation() { 
      return expectation;
    }
    
    public double getCount() { 
      return count;
    }
    
    public double getWeight() { 
      return weight;
    }
    
    public abstract double getValue(int li);
  }
}
