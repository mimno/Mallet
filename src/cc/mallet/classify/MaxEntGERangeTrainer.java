/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import cc.mallet.classify.constraints.ge.MaxEntGEConstraint;
import cc.mallet.classify.constraints.ge.MaxEntKLFLGEConstraints;
import cc.mallet.classify.constraints.ge.MaxEntL2FLGEConstraints;
import cc.mallet.classify.constraints.ge.MaxEntRangeL2FLGEConstraints;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.MalletProgressMessageLogger;

/**
 * Training of MaxEnt models with labeled features using
 * Generalized Expectation Criteria.
 * 
 * Based on: 
 * "Learning from Labeled Features using Generalized Expectation Criteria"
 * Gregory Druck, Gideon Mann, Andrew McCallum
 * SIGIR 2008
 * 
 * @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 * 
 * Better explanations of parameters is given in MaxEntOptimizableByGE
 */

public class MaxEntGERangeTrainer extends ClassifierTrainer<MaxEnt> implements ClassifierTrainer.ByOptimization<MaxEnt>, Boostable, Serializable {

  private static final long serialVersionUID = 1L;
  private static Logger logger = MalletLogger.getLogger(MaxEntGERangeTrainer.class.getName());
  private static Logger progressLogger = MalletProgressMessageLogger.getLogger(MaxEntGERangeTrainer.class.getName()+"-pl");

  // these are for using this code from the command line
  private boolean normalize = true;
  private boolean useValues = false;
  private String constraintsFile;
  
  private int numIterations = 0;
  private int maxIterations = Integer.MAX_VALUE;
  private double temperature = 1;
  private double gaussianPriorVariance = 1;

  protected ArrayList<MaxEntGEConstraint> constraints;
  private InstanceList trainingList = null;
  private MaxEnt classifier = null;
  private MaxEntOptimizableByGE ge = null;
  private Optimizer opt = null;

  public MaxEntGERangeTrainer() {}
  
  public MaxEntGERangeTrainer(ArrayList<MaxEntGEConstraint> constraints) {
    this.constraints = constraints;
  }
  
  public MaxEntGERangeTrainer(ArrayList<MaxEntGEConstraint> constraints, MaxEnt classifier) {
    this.constraints = constraints;
    this.classifier = classifier;
  }
  
  public void setConstraintsFile(String filename) {
    this.constraintsFile = filename;
  }
  
  public void setTemperature(double temp) {
    this.temperature = temp;
  }
  
  public void setGaussianPriorVariance(double variance) {
    this.gaussianPriorVariance = variance;
  }
  
  public MaxEnt getClassifier () {
    return classifier;
  }

  public void setUseValues(boolean flag) {
    this.useValues = flag;
  }
  
  public void setNormalize(boolean normalize) {
    this.normalize = normalize;
  }
  
  public Optimizable.ByGradientValue getOptimizable (InstanceList trainingList) {
    if (ge == null) {
      ge = new MaxEntOptimizableByGE(trainingList,constraints,classifier);
      ge.setTemperature(temperature);
      ge.setGaussianPriorVariance(gaussianPriorVariance);
    }
    return ge;
  }

  public Optimizer getOptimizer () {
    getOptimizable(trainingList);
    if (opt == null) {
      opt = new LimitedMemoryBFGS(ge);
    }
    return opt;
  }
  
  public void setOptimizer(Optimizer opt) { 
    this.opt = opt;
  }

  /**
   * Specifies the maximum number of iterations to run during a single call
   * to <code>train</code> or <code>trainWithFeatureInduction</code>.
   * @return This trainer
   */
  public void setMaxIterations (int iter) {
    maxIterations = iter;
  }
  
  public int getIteration () {
    return numIterations;
  }

  public MaxEnt train (InstanceList trainingList) {
    return train (trainingList, maxIterations);
  }

  public MaxEnt train (InstanceList train, int maxIterations) {
    trainingList = train;

    if (constraints == null && constraintsFile != null) {
      HashMap<Integer,double[][]> constraintsMap = 
        FeatureConstraintUtil.readRangeConstraintsFromFile(constraintsFile, trainingList);

      logger.info("number of constraints: " + constraintsMap.size());
      constraints = new ArrayList<MaxEntGEConstraint>();

      MaxEntRangeL2FLGEConstraints geConstraints = new MaxEntRangeL2FLGEConstraints(train.getDataAlphabet().size(),
        train.getTargetAlphabet().size(),useValues,normalize);
      for (int fi : constraintsMap.keySet()) {
        double[][] dist = constraintsMap.get(fi);
        for (int li = 0; li < dist.length; li++) {
          if (!Double.isInfinite(dist[li][0])) {
            geConstraints.addConstraint(fi, li, dist[li][0], dist[li][1], 1);
          }
        }
      }
      constraints.add(geConstraints);
    }
    
    getOptimizable(trainingList);
    getOptimizer();
    
    if (opt instanceof LimitedMemoryBFGS) {
      ((LimitedMemoryBFGS)opt).reset();
    }    
    
    logger.fine ("trainingList.size() = "+trainingList.size());

    try {
      opt.optimize(maxIterations);
      numIterations += maxIterations;
    } catch (Exception e) {
      e.printStackTrace();
      logger.info ("Catching exception; saying converged.");
    }

    if (maxIterations == Integer.MAX_VALUE && opt instanceof LimitedMemoryBFGS) {
      // Run it again because in our and Sam Roweis' experience, BFGS can still
      // eke out more likelihood after first convergence by re-running without
      // being restricted by its gradient history.
      ((LimitedMemoryBFGS)opt).reset();
      try {
        opt.optimize(maxIterations);
        numIterations += maxIterations;
      } catch (Exception e) {
        e.printStackTrace();
        logger.info ("Catching exception; saying converged.");
      }
    }
    progressLogger.info("\n"); //  progress messages are on one line; move on.
    
    classifier = ge.getClassifier();
    return classifier;
  }
}