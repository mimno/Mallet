/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.io.Serializable;
import java.util.HashMap;
import java.util.logging.Logger;

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

public class MaxEntGETrainer extends ClassifierTrainer<MaxEnt> implements ClassifierTrainer.ByOptimization<MaxEnt>, Boostable, Serializable {

  private static final long serialVersionUID = 1L;
  private static Logger logger = MalletLogger.getLogger(MaxEntGETrainer.class.getName());
  private static Logger progressLogger = MalletProgressMessageLogger.getLogger(MaxEntGETrainer.class.getName()+"-pl");

  private int numIterations = Integer.MAX_VALUE;
  private double temperature = 1;
  private double gaussianPriorVariance = 1;
  private String constraintsFile;
  private HashMap<Integer,double[]> constraints;
  private InstanceList trainingList = null;
  private MaxEnt classifier = null;
  private MaxEntOptimizableByGE ge = null;
  private Optimizer opt = null;

  public MaxEntGETrainer() {}
  
  public MaxEntGETrainer(HashMap<Integer,double[]> constraints) {
    this.constraints = constraints;
  }
  
  public MaxEntGETrainer(HashMap<Integer,double[]> constraints, MaxEnt classifier) {
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
  
  public Optimizable getOptimizable () {
    return ge;
  }

  public Optimizer getOptimizer () {
    return opt;
  }

  /**
   * Specifies the maximum number of iterations to run during a single call
   * to <code>train</code> or <code>trainWithFeatureInduction</code>.
   * @return This trainer
   */
  public void setNumIterations (int i) {
    numIterations = i;
  }
  
  public int getIteration () {
    if (ge == null)
      return 0;
    else
      return Integer.MAX_VALUE;
  }

  public MaxEnt train (InstanceList trainingList) {
    return train (trainingList, numIterations);
  }

  public MaxEnt train (InstanceList train, int numIterations) {
    trainingList = train;
    
    if (constraints == null && constraintsFile != null) {
      constraints = FeatureConstraintUtil.readConstraintsFromFile(constraintsFile, trainingList);
      System.err.println("number of constraints: " + constraints.size());
    }
    
    ge = new MaxEntOptimizableByGE(trainingList,constraints,classifier);
    ge.setTemperature(temperature);
    ge.setGaussianPriorVariance(gaussianPriorVariance);
    opt = new LimitedMemoryBFGS(ge);
    
    logger.fine ("trainingList.size() = "+trainingList.size());
    boolean converged;

    for (int i = 0; i < numIterations; i++) {
      try {
        converged = opt.optimize (1);
      } catch (Exception e) {
        e.printStackTrace();
        logger.info ("Catching exception; saying converged.");
        converged = true;
      }
      if (converged)
        break;
    }

    if (numIterations == Integer.MAX_VALUE) {
      // Run it again because in our and Sam Roweis' experience, BFGS can still
      // eke out more likelihood after first convergence by re-running without
      // being restricted by its gradient history.
      opt = new LimitedMemoryBFGS(ge);
      try {
        opt.optimize ();
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

