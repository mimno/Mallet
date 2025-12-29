/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.logging.Logger;

import cc.mallet.classify.Classifier;
import cc.mallet.classify.MaxEnt;
import cc.mallet.classify.MaxEntOptimizableByLabelDistribution;
import cc.mallet.classify.constraints.pr.MaxEntL2FLPRConstraints;
import cc.mallet.classify.constraints.pr.MaxEntPRConstraint;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.NullLabel;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;


/**
 * Penalty (soft) version of Posterior Regularization (PR) for training MaxEnt.
 * 
 * @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 */

public class MaxEntPRTrainer extends ClassifierTrainer<MaxEnt> implements ClassifierTrainer.ByOptimization<MaxEnt> {

  private static Logger logger = MalletLogger.getLogger(MaxEntPRTrainer.class.getName());
  
  // for using this from the command line
  private boolean normalize = true;
  private boolean useValues = false;
  private int minIterations = 10;
  private int maxIterations = 500;
  private double qGPV;
  private String constraintsFile;

	private boolean converged = false;
	private int numIterations = 0;
	private double tolerance = 0.001;
	private double pGPV;
	private ArrayList<MaxEntPRConstraint> constraints;
	private MaxEnt p;
	private PRAuxClassifier q;
  
	public MaxEntPRTrainer() {}
	
	public MaxEntPRTrainer(ArrayList<MaxEntPRConstraint> constraints) {
	  this.constraints = constraints;
	}
	
	public void setPGaussianPriorVariance(double pGPV) {
	  this.pGPV = pGPV;
	}
	
	public void setQGaussianPriorVariance(double qGPV) {
	  this.qGPV = qGPV;
	}
	
  public void setConstraintsFile(String filename) {
    this.constraintsFile = filename;
  }
  
  public void setUseValues(boolean flag) {
    this.useValues = flag;
  }
  
  public void setMinIterations(int minIterations) {
    this.minIterations = minIterations;
  }
  
  public void setMaxIterations(int minIterations) {
    this.maxIterations = minIterations;
  }
  
  public void setNormalize(boolean normalize) {
    this.normalize = normalize;
  }

  public Optimizer getOptimizer() {
    throw new RuntimeException("Not yet implemented!");
  }

  public int getIteration() {
    return numIterations;
  }
  
  @Override
  public boolean isFinishedTraining() {
    return converged;
  }

  @Override
  public MaxEnt getClassifier() {
    return p;
  }

  @Override
  public MaxEnt train(InstanceList trainingSet) {
    return train(trainingSet,maxIterations);
  }
	
  public MaxEnt train(InstanceList trainingSet, int maxIterations) {
    return train(trainingSet,Math.min(maxIterations,minIterations),maxIterations);
  }
  
  public MaxEnt train(InstanceList data, int minIterations, int maxIterations) {
    if (constraints == null && constraintsFile != null) {
      HashMap<Integer,double[]> constraintsMap = 
        FeatureConstraintUtil.readConstraintsFromFile(constraintsFile, data);
      logger.info("number of constraints: " + constraintsMap.size());
      constraints = new ArrayList<MaxEntPRConstraint>();
      MaxEntL2FLPRConstraints prConstraints = new MaxEntL2FLPRConstraints(data.getDataAlphabet().size(),
          data.getTargetAlphabet().size(),useValues,normalize);
      for (int fi : constraintsMap.keySet()) {
        prConstraints.addConstraint(fi, constraintsMap.get(fi), qGPV);
      }
      constraints.add(prConstraints);
    }

    BitSet instancesWithConstraints = new BitSet(data.size());
    for (MaxEntPRConstraint constraint : constraints) {
      BitSet bitset = constraint.preProcess(data);
      instancesWithConstraints.or(bitset);
    }
    
    InstanceList unlabeled = data.cloneEmpty();
    for (int ii = 0; ii < data.size(); ii++) {
      if (instancesWithConstraints.get(ii)) {
        boolean noLabel = data.get(ii).getTarget() == null;
        if (noLabel) {
          data.get(ii).unLock();
          data.get(ii).setTarget(new NullLabel((LabelAlphabet)data.getTargetAlphabet()));
        }
        unlabeled.add(data.get(ii));
      }
    }

    int numFeatures = unlabeled.getDataAlphabet().size();
    
    // setup model
    int numParameters = (numFeatures + 1) * unlabeled.getTargetAlphabet().size();
    if (p == null) {
    	p = new MaxEnt(unlabeled.getPipe(),new double[numParameters]);
    }

    // setup aux model
    q = new PRAuxClassifier(unlabeled.getPipe(),constraints);
    
    double oldValue = -Double.MAX_VALUE;
    for (numIterations = 0; numIterations < maxIterations; numIterations++) {

      double[][] base = optimizeQ(unlabeled,p,numIterations==0);
      
      double value = optimizePAndComputeValue(unlabeled,q,base,pGPV);
      logger.info("iteration " + numIterations + " total value " + value);

      if (numIterations >= (minIterations-1) && 2.0*Math.abs(value-oldValue) <= tolerance *
          (Math.abs(value)+Math.abs(oldValue) + 1e-5)){
        logger.info("PR value difference below tolerance (oldValue: " + oldValue + " newValue: " + value + ")");
      	converged = true;
        break;
      }
      oldValue = value;
    }
    return p;
  }
  
  private double optimizePAndComputeValue(InstanceList data, PRAuxClassifier q, double[][] base, double pGPV) {
    
    InstanceList dataLabeled = data.cloneEmpty();
    
    double entropy = 0;
    
    int numLabels = data.getTargetAlphabet().size();
    for (int ii = 0; ii < data.size(); ii++) {
      double[] scores = new double[numLabels];
      q.getClassificationScores(data.get(ii), scores);
      for (int li = 0; li < numLabels; li++) {
        if (base != null && base[ii][li] == 0) {
          scores[li] = Double.NEGATIVE_INFINITY;
        }
        else if (base != null) {
          double logP = Math.log(base[ii][li]);
          scores[li] += logP;  
        }
      }
      MatrixOps.expNormalize(scores);
   
      entropy += Maths.getEntropy(scores);

      LabelVector lv = new LabelVector((LabelAlphabet)data.getTargetAlphabet(), scores);
      Instance instance = new Instance(data.get(ii).getData(),lv,null,null);
      dataLabeled.add(instance);
    }
    
    // train supervised
    MaxEntOptimizableByLabelDistribution opt = new  MaxEntOptimizableByLabelDistribution(dataLabeled,p);
    opt.setGaussianPriorVariance(pGPV);

    LimitedMemoryBFGS bfgs = new LimitedMemoryBFGS(opt);
    try { bfgs.optimize(); } catch (Exception e) { e.printStackTrace(); }
    bfgs.reset();
    try { bfgs.optimize(); } catch (Exception e) { e.printStackTrace(); }
    
    double value = 0;
    for (MaxEntPRConstraint constraint : q.getConstraintFeatures()) {
      // plus sign because this returns negative values
      value += constraint.getCompleteValueContribution();
    }
    value += entropy + opt.getValue();
    return value;
  }
  
  private double[][] optimizeQ(InstanceList data, Classifier p, boolean firstIter) {
    int numLabels = data.getTargetAlphabet().size();

    double[][] base;
    if (firstIter) {
      base = null;
    }
    else {
      base = new double[data.size()][numLabels];
      for (int ii = 0; ii < data.size(); ii++) {
        p.classify(data.get(ii)).getLabelVector().addTo(base[ii]);
      }
    }
    
    PRAuxClassifierOptimizable optimizable = new PRAuxClassifierOptimizable(data,base,q);
    
    LimitedMemoryBFGS bfgs = new LimitedMemoryBFGS(optimizable);
    try { bfgs.optimize(); } catch (Exception e) { e.printStackTrace(); }
    bfgs.reset();
    try { bfgs.optimize(); } catch (Exception e) { e.printStackTrace(); }
    
    return base;
  }
}
