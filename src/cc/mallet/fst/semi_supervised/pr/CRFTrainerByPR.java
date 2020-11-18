/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.pr;

import java.util.ArrayList;
import java.util.BitSet;

import com.google.errorprone.annotations.Var;

import cc.mallet.fst.CRF;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.fst.semi_supervised.pr.constraints.PRConstraint;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.InstanceList;

/**
 * Posterior regularization trainer.
 *
 * @author Gregory Druck
 */

public class CRFTrainerByPR extends TransducerTrainer implements TransducerTrainer.ByOptimization {

  private boolean converged;
  private int iter;
  private int numThreads;
  private double pGpv;
  private double tolerance;
  private double value;
  private double qValue;
  private ArrayList<PRConstraint> constraints;
  private LimitedMemoryBFGS bfgs;
  private CRF crf;
  private StateLabelMap stateLabelMap;
  
  public CRFTrainerByPR(CRF crf, ArrayList<PRConstraint> constraints) {
  	this(crf,constraints,1);
  }
  
  public CRFTrainerByPR(CRF crf, ArrayList<PRConstraint> constraints, int numThreads) {
    this.crf = crf;
    this.iter = 0;
    this.value = Double.NEGATIVE_INFINITY;
    this.constraints = constraints;
    this.pGpv = 10;
    this.tolerance = 0.001;
    this.numThreads = numThreads;
    this.stateLabelMap = new StateLabelMap(crf.getOutputAlphabet(),true);
  }
  
  @Override
  public int getIteration() {
    return iter;
  }

  @Override
  public Transducer getTransducer() {
    return crf;
  }

  @Override
  public boolean isFinishedTraining() {
    return converged;
  }
  
  // map between states in CRF FST and labels
  public void setStateLabelMap(StateLabelMap map) {
    this.stateLabelMap = map;
  }
  
  public void setPGaussianPriorVariance(double pGpv) {
    this.pGpv = pGpv;
  }
  
  public void setTolerance(double tolerance) {
  	this.tolerance = tolerance;
  }
  
  @Override
  public boolean train(InstanceList train, int numIterations) {
  	return train(train,0,numIterations);
  }
  
  public boolean train(InstanceList train, int minIter, int maxIter) {
  	return train(train,minIter,maxIter,Integer.MAX_VALUE);
  }
  
  public boolean train(@Var InstanceList train, int minIter, int maxIter, int maxIterPerStep) {
    @Var
    double oldValue = 0;
    int max = iter + maxIter;
    
    BitSet constrainedInstances = new BitSet();
    for (PRConstraint constraint : constraints) {
      constrainedInstances.or(constraint.preProcess(train));
      constraint.setStateLabelMap(stateLabelMap);
    }

    @Var
    int removed = 0;
    InstanceList tempTrain = train.cloneEmpty();
    for (int ii = 0; ii < train.size(); ii++) {
      if (constrainedInstances.get(ii)) {
        tempTrain.add(train.get(ii));
      }
      else {
        removed++;
      }
    }
    train = tempTrain;
    System.err.println("Removed " + removed + " instances that do not contain constraints.");
    
    PRAuxiliaryModel model = new PRAuxiliaryModel(crf,constraints);

    for (; iter < max; iter++) {
    	long startTime = System.currentTimeMillis();
    	
      // train q
      ConstraintsOptimizableByPR opt = new ConstraintsOptimizableByPR(crf, train, model, numThreads);
      bfgs = new LimitedMemoryBFGS(opt);
      try {
        bfgs.optimize(maxIterPerStep);
      } catch (Exception e) {
        e.printStackTrace();
      }
      opt.shutdown();
      
      /*
      for (int j = 0; j < constraints.size(); j++) {
        constraints.get(j).print();
      }
      */
      
      qValue = opt.getCompleteValueContribution();
      assert(qValue > 0);
      
      // use to train p
      CRFOptimizableByKL optP = new CRFOptimizableByKL(crf, train, model, opt.getCachedDots(), numThreads, 1);
      optP.setGaussianPriorVariance(pGpv);
      LimitedMemoryBFGS bfgsP = new LimitedMemoryBFGS(optP);
      
      try {
        bfgsP.optimize(maxIterPerStep);
      } catch (Exception e) {
        e.printStackTrace();
      }
      optP.shutdown();
      
      value = optP.getValue() - qValue;
      assert(value < 0);
      System.err.println("Total value = " + value + " (pValue = " + optP.getValue() + ") (qValue = " + (-qValue) + ")");
      
      System.err.println("Time for iteration " + String.format("%.2f",((System.currentTimeMillis() - startTime) / 1000.)) + "s");
      
      // stopping criteria from BFGS
      //System.err.println("Convergence test: " + (2.0*Math.abs(value-oldValue)) + " <= " + (tolerance * (Math.abs(value)+Math.abs(oldValue) + 1e-5)));
      if((iter >= minIter) && 2.0*Math.abs(value-oldValue) <= tolerance *
          (Math.abs(value)+Math.abs(oldValue) + 1e-5)){
      	System.err.println("AP value difference below tolerance (oldValue: " 
          + oldValue + "newValue: " + value);
      	
        break;
      }
      
      
      oldValue = value;
      
      runEvaluators();
    }
    converged = true;
    return converged;
  }
  
  public double getTotalValue() {
  	return value;
  }
  
  public double getQValue() {
  	return qValue;
  }

  public Optimizer getOptimizer() {
    return bfgs;
  }
}
