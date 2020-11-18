/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.constraints.pr;

import java.util.BitSet;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.google.errorprone.annotations.Var;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * Abstract expectation constraint for use with Posterior Regularization (PR).
 * 
 * @author Gregory Druck
 */

public abstract class MaxEntFLPRConstraints implements MaxEntPRConstraint {
  protected boolean useValues;
  protected int numFeatures;
  protected int numLabels;
  
  // maps between input feature indices and constraints
  protected IntObjectHashMap<MaxEntFLPRConstraint> constraints;
  
  // cache of set of constrained features that fire at last FeatureVector
  // provided in preprocess call
  protected IntArrayList indexCache;
  protected DoubleArrayList valueCache;

  public MaxEntFLPRConstraints(int numFeatures, int numLabels, boolean useValues) {
    this.useValues = useValues;
    this.numFeatures = numFeatures;
    this.numLabels = numLabels;
    this.constraints = new IntObjectHashMap<MaxEntFLPRConstraint>();
    this.indexCache = new IntArrayList();
    this.valueCache = new DoubleArrayList();
  }

  public abstract void addConstraint(int fi, double[] ex, double weight);
  
  public void incrementExpectations(FeatureVector input, double[] dist, double weight) {
    preProcess(input);
    for (int li = 0; li < numLabels; li++) {
      double p = weight * dist[li];
      for (int i = 0; i < indexCache.size(); i++) {
        if (useValues) {
          constraints.get(indexCache.get(i)).expectation[li] += p * valueCache.get(i); 
        }
        else {
          constraints.get(indexCache.get(i)).expectation[li] += p; 
        }
      }
    }
  }

  public void zeroExpectations() {
    for (IntObjectCursor<MaxEntFLPRConstraint> fi : constraints) {
      fi.value.expectation = new double[numLabels];
    }
  }

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
    indexCache.clear();
    if (useValues) valueCache.clear();
    @Var
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

  protected abstract class MaxEntFLPRConstraint {
    
    protected double count;
    protected double weight;
    protected double[] target;
    protected double[] expectation;
    
    public MaxEntFLPRConstraint(double[] target, double weight) {
      this.count = 0;
      this.weight = weight;
      this.target = target;
      this.expectation = null;
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
  }
}
