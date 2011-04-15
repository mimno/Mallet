/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import cc.mallet.fst.CRF;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.fst.semi_supervised.constraints.GEConstraint;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;

/**
 * Optimizable for CRF using Generalized Expectation constraints that
 * consider either a single label or a pair of labels of a linear chain CRF.
 * 
 * See:
 * "Generalized Expectation Criteria for Semi-Supervised Learning of Conditional Random Fields"
 * Gideon Mann and Andrew McCallum
 * ACL 2008
 *
 * @author Gregory Druck
 */

public class CRFOptimizableByGE implements Optimizable.ByGradientValue {

  private static final int DEFAULT_GPV = 10;
  
  private CRF crf;
  private ArrayList<GEConstraint> constraints;
  private InstanceList data;
  
  private int numThreads;
  private double gpv;
  private double weight;
  
  // indicator that keeps track of whether
  // the gradient / value need to be re-computed
  private int cache;
  private double cachedValue;
  private CRF.Factors cachedGradient;
  
  // lists of source states / transition indices
  // for each destination state
  // used in GELattice
  private int[][] reverseTrans;
  private int[][] reverseTransIndices;
  
  // instances in which at least one
  // constraint fires 
  private BitSet instancesWithConstraints;
  
  private ThreadPoolExecutor executor;
  
  /**
   * @param crf CRF
   * @param constraints List of GEConstraints
   * @param data Unlabeled data.
   * @param map Map between states and labels.
   * @param numThreads Number of threads to use for training (DEFAULT=1) 
   */
  public CRFOptimizableByGE(CRF crf, ArrayList<GEConstraint> constraints, InstanceList data, StateLabelMap map, int numThreads) {
    this(crf,constraints,data,map,numThreads,1);
  }
  
  public CRFOptimizableByGE(CRF crf, ArrayList<GEConstraint> constraints, InstanceList data, StateLabelMap map, int numThreads, double weight) {
    this.crf = crf;
    this.constraints = constraints;
    this.cache = Integer.MAX_VALUE;
    this.cachedValue = Double.NaN;
    this.cachedGradient = new CRF.Factors(crf);
    this.data = data;
    this.numThreads = numThreads;
    this.weight = weight;
    
    instancesWithConstraints = new BitSet(data.size());
    
    for (GEConstraint constraint : constraints) {
      constraint.setStateLabelMap(map);
      BitSet bitset = constraint.preProcess(data);
      instancesWithConstraints.or(bitset);
    }
    this.gpv = DEFAULT_GPV;
    
    if (numThreads > 1) {
      this.executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(numThreads);
    }
    
    createReverseTransitionMatrices(crf);
  }

  /**
   * Initializes data structures for mapping between a
   * destination state and its source states / transition indices.
   * 
   * @param crf CRF
   */
  public void createReverseTransitionMatrices(CRF crf) {
    int[] counts = new int[crf.numStates()];
    for (int si = 0; si < crf.numStates(); si++) {
      CRF.State prevState = (CRF.State)crf.getState(si);
      for (int di = 0; di < prevState.numDestinations(); di++) {
        int sj = prevState.getDestinationState(di).getIndex();
        counts[sj]++;
      }
    }
  
    this.reverseTrans = new int[crf.numStates()][];
    this.reverseTransIndices = new int[crf.numStates()][];
    for (int i = 0; i < counts.length; i++) {
      this.reverseTrans[i] = new int[counts[i]];
      this.reverseTransIndices[i] = new int[counts[i]];
    }
    
    int[] indices = new int[crf.numStates()];
    for (int si = 0; si < crf.numStates(); si++) {
      CRF.State prevState = (CRF.State)crf.getState(si);
      for (int di = 0; di < prevState.numDestinations(); di++) {
        int sj = prevState.getDestinationState(di).getIndex();
        this.reverseTrans[sj][indices[sj]] = si;
        this.reverseTransIndices[sj][indices[sj]] = di;
        indices[sj]++;
      }
    }
  }
  
  public int getNumParameters() {
    return crf.getNumParameters();
  }

  public void getParameters(double[] buffer) {
    crf.getParameters().getParameters(buffer);
  }

  public double getParameter(int index) {
    return crf.getParameters().getParameter(index);
  }

  public void setParameters(double[] params) {
    crf.getParameters().setParameters(params);
    crf.weightsValueChanged();
  }

  public void setParameter(int index, double value) {
    crf.getParameters().setParameter(index, value);
    crf.weightsValueChanged();
  }

  public void cacheValueAndGradient() {
    
    // compute and cache lattices
    //System.gc();
    //System.err.println("Used Memory "+String.format("%.3f", (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1000000.) + " before lattice");   
    ArrayList<SumLatticeDefault> lattices = new ArrayList<SumLatticeDefault>();
    if (numThreads == 1) {
      for (int ii = 0; ii < data.size(); ii++) {
        if (instancesWithConstraints.get(ii)) {
          SumLatticeDefault lattice = new SumLatticeDefault(
              this.crf, (FeatureVectorSequence)data.get(ii).getData(),
              null, null, true);
          lattices.add(lattice);
        }
        else {
          lattices.add(null);
        }
      }
    }
    else {
      // mutli-threaded version
      ArrayList<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
      
      if (data.size() < numThreads) {
        numThreads = data.size();
      }
      
      int increment = data.size() / numThreads;
      int start = 0;
      int end = increment;
      for (int thread = 0; thread < numThreads; thread++) {
        tasks.add(new SumLatticeTask(crf,data,instancesWithConstraints,start,end));
        start += increment;
        if (thread == numThreads - 2) {
          end = data.size();
        }
        else {
          end += increment;
        }
      }
      
      try {
        // run all threads and wait for them to finish
        executor.invokeAll(tasks);
      } catch (InterruptedException ie) {
        ie.printStackTrace();
      }
      
      for (Callable<Void> task : tasks) {
        lattices.addAll(((SumLatticeTask)task).getLattices());
      }
      assert(lattices.size() == data.size()) : lattices.size() + " " + data.size();
    }
    System.err.println("Done computing lattices.");
    
    for (GEConstraint constraint : constraints) {
      constraint.zeroExpectations();
      constraint.computeExpectations(lattices);
    }
    System.err.println("Done computing expectations.");
    //System.gc();
    //System.err.println("Used Memory "+String.format("%.3f", (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1000000.) + " after lattice");  
    
    // compute GE value
    this.cachedValue = 0;
    for (GEConstraint constraint : constraints) {
      this.cachedValue += constraint.getValue();
    }
    
    cachedGradient.zero();
    
    // compute GE gradient
    if (numThreads == 1) {
      for (int ii = 0; ii < data.size(); ii++) {
        if (instancesWithConstraints.get(ii)) {
          SumLatticeDefault lattice = lattices.get(ii);
          FeatureVectorSequence fvs = (FeatureVectorSequence)data.get(ii).getData();
          new GELattice(fvs, lattice.getGammas(), lattice.getXis(), crf, reverseTrans, reverseTransIndices, cachedGradient,this.constraints, false);
        }
      }
    }
    else {
      // multi-threaded version
      ArrayList<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
      
      if (data.size() < numThreads) {
        numThreads = data.size();
      }
      
      int increment = data.size() / numThreads;
      int start = 0;
      int end = increment;
      for (int thread = 0; thread < numThreads; thread++) {
        ArrayList<GEConstraint> constraintsCopy = new ArrayList<GEConstraint>();
        for (GEConstraint constraint : constraints) {
          constraintsCopy.add(constraint.copy());
        }
        
        tasks.add(new GELatticeTask(crf,data,lattices,constraintsCopy,instancesWithConstraints,
          reverseTrans,reverseTransIndices,start,end));
        start += increment;
        if (thread == numThreads - 2) {
          end = data.size();
        }
        else {
          end += increment;
        }
        
      }
      
      try {
        // run all threads and wait for them to finish
        executor.invokeAll(tasks);
      } catch (InterruptedException ie) {
        ie.printStackTrace();
      }
      
      for (Callable<Void> task : tasks) {
        cachedGradient.plusEquals(((GELatticeTask)task).getGradient(), 1);
      }
    }

    System.err.println("Done computing gradient.");

    this.cachedValue += crf.getParameters().gaussianPrior(gpv);
    cachedGradient.plusEqualsGaussianPriorGradient(crf.getParameters(), gpv);
    
    System.err.println("Done computing regularization.");   
    
    if (weight != 1) {
      this.cachedValue *= weight;
    }
    System.err.println("GE Value = " + this.cachedValue);
  }
  
  public void setGaussianPriorVariance(double variance) {
    this.gpv = variance;
  }
  
  public void getValueGradient(double[] buffer) {
    if (crf.getWeightsValueChangeStamp() != cache) {
      cacheValueAndGradient();
      cache = crf.getWeightsValueChangeStamp();
    }
    cachedGradient.getParameters(buffer);
    if (weight != 1) {
      MatrixOps.timesEquals(buffer, weight);
    }
  }

  public double getValue() {
    if (crf.getWeightsValueChangeStamp() != cache) {
      cacheValueAndGradient();
      cache = crf.getWeightsValueChangeStamp();
    }
    return this.cachedValue;
  }
  
  /**
   * Should be called after training is complete 
   * to shutdown all threads.
   */
  public void shutdown() { 
    if (executor == null) return;
    executor.shutdown();
    try {
      executor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    assert(executor.shutdownNow().size() == 0) : "All tasks didn't finish";
  }
}

class SumLatticeTask implements Callable<Void> {

  private int start;
  private int end;
  private ArrayList<SumLatticeDefault> lattices;
  private InstanceList data;
  private CRF crf;
  private BitSet instancesWithConstraints;
  
  
  public SumLatticeTask(CRF crf, InstanceList data, BitSet instancesWithConstraints, int start, int end) {
    this.crf = crf;
    this.data = data;
    this.start = start;
    this.end = end;
    this.lattices = new ArrayList<SumLatticeDefault>();
    this.instancesWithConstraints = instancesWithConstraints;
  }
  
  public ArrayList<SumLatticeDefault> getLattices() {
    return this.lattices;
  }

  public Void call() throws Exception {
    for (int ii = start; ii < end; ii++) {
      if (instancesWithConstraints.get(ii)) {
        Instance instance = data.get(ii);
        SumLatticeDefault lattice = new SumLatticeDefault(
          this.crf, (FeatureVectorSequence)instance.getData(),
          null, null, true);
        lattices.add(lattice);
      }
      else {
        lattices.add(null);
      }
    }
    return null;
  }
}

class GELatticeTask implements Callable<Void> {

  private int start;
  private int end;
  private ArrayList<GEConstraint> constraints;
  private ArrayList<SumLatticeDefault> lattices;
  private InstanceList data;
  private CRF crf;
  private CRF.Factors gradient;
  private BitSet instancesWithConstraints;
  private int[][] reverseTrans;
  private int[][] reverseTransIndices;
  
  
  /**
   * @param crf CRF
   * @param data Unlabeled data
   * @param lattices Cached SumLattices
   * @param constraints List of GEConstraints
   * @param instancesWithConstraints BitSet which indices whether any constraints fire for an instance
   * @param reverseTrans Source state indices for each destination state
   * @param reverseTransIndices Transition indices for each destination state
   * @param start Position in unlabeled data where this thread starts computing
   * @param end Position in unlabeled data where this thread stops computing
   */
  public GELatticeTask(CRF crf, InstanceList data, ArrayList<SumLatticeDefault> lattices, 
      ArrayList<GEConstraint> constraints, BitSet instancesWithConstraints, 
      int[][] reverseTrans, int[][] reverseTransIndices,
      int start, int end) {
    this.crf = crf;
    this.data = data;
    this.lattices = lattices;
    this.constraints = constraints;
    this.start = start;
    this.end = end;
    this.gradient = new CRF.Factors(crf);
    this.instancesWithConstraints = instancesWithConstraints;
    this.reverseTrans = reverseTrans;
    this.reverseTransIndices = reverseTransIndices;
  }
  
  public CRF.Factors getGradient() {
    return this.gradient;
  }

  public Void call() throws Exception {
    for (int ii = start; ii < end; ii++) {
      if (instancesWithConstraints.get(ii)) {
        SumLatticeDefault lattice = lattices.get(ii);
        FeatureVectorSequence fvs = (FeatureVectorSequence)data.get(ii).getData();
        new GELattice(fvs, lattice.getGammas(), lattice.getXis(),
          crf, reverseTrans, reverseTransIndices, gradient,this.constraints, false);
      }
    }
    return null;
  }
}
