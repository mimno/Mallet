package cc.mallet.fst.semi_supervised;

import java.util.ArrayList;

import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFCacheStaleIndicator;
import cc.mallet.fst.CRFOptimizableByBatchLabelLikelihood;
import cc.mallet.fst.CRFOptimizableByGradientValues;
import cc.mallet.fst.CRFOptimizableByLabelLikelihood;
import cc.mallet.fst.CRFTrainerByLabelLikelihood;
import cc.mallet.fst.CRFTrainerByThreadedLabelLikelihood;
import cc.mallet.fst.ThreadedOptimizable;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.fst.semi_supervised.CRFOptimizableByGE;
import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.fst.semi_supervised.constraints.GEConstraint;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.InstanceList;

public class CRFTrainerByLikelihoodAndGE extends TransducerTrainer {

  private boolean initSupervised;
  private boolean converged;
  private double geWeight;
  private double gpv;
  private int supIterations;
  private int numThreads;
  private int iteration;
  private CRF crf;
  private ArrayList<GEConstraint> constraints;
  private StateLabelMap map;
  
  public CRFTrainerByLikelihoodAndGE(CRF crf, ArrayList<GEConstraint> constraints, StateLabelMap map) {
    this.crf = crf;
    this.constraints = constraints;
    this.map = map;
    this.iteration = 0;
    this.converged = false;
    this.geWeight = 1.0;
    this.initSupervised = false;
    this.gpv = 10.0;
    this.numThreads = 1;
    this.supIterations = Integer.MAX_VALUE;
  }
  
  public void setGEWeight(double weight) {
    this.geWeight = weight;
  }
  
  public void setGaussianPriorVariance(double gpv) {
    this.gpv = gpv;
  }
  
  public void setInitSupervised(boolean flag) {
    this.initSupervised = flag;
  }
  
  public void setSupervisedIterations(int iterations) {
    this.supIterations = iterations;
  }
  
  public void setNumThreads(int numThreads) {
    this.numThreads = numThreads;
  }
  
  @Override
  public Transducer getTransducer() {
    return crf;
  }

  @Override
  public int getIteration() {
    return iteration;
  }

  @Override
  public boolean isFinishedTraining() {
    return converged;
  }
  
  public boolean train(InstanceList trainingSet, InstanceList unlabeledSet, int numIterations) {
    System.err.println(trainingSet.size());
    System.err.println(unlabeledSet.size());
    if (initSupervised) {
      
      // train supervised
      if (numThreads == 1) {
        CRFTrainerByLabelLikelihood trainer = new CRFTrainerByLabelLikelihood(crf);
        trainer.setAddNoFactors(true);
        trainer.setGaussianPriorVariance(gpv);
        trainer.train(trainingSet,supIterations);
      }
      else {
        CRFTrainerByThreadedLabelLikelihood trainer = new CRFTrainerByThreadedLabelLikelihood(crf,numThreads);
        trainer.setAddNoFactors(true);
        trainer.setGaussianPriorVariance(gpv);
        trainer.train(trainingSet,supIterations);
        trainer.shutdown();
      }
      runEvaluators();
    }
    
    // train semi-supervised
    Optimizable.ByGradientValue optLikelihood;
    if (numThreads == 1) {
      optLikelihood = new CRFOptimizableByLabelLikelihood(crf,trainingSet);
      ((CRFOptimizableByLabelLikelihood)optLikelihood).setGaussianPriorVariance(gpv);
    }
    else {
      CRFOptimizableByBatchLabelLikelihood likelihood = new CRFOptimizableByBatchLabelLikelihood(crf,trainingSet,numThreads);
      optLikelihood = new ThreadedOptimizable(likelihood,trainingSet,crf.getParameters().getNumFactors(),
        new CRFCacheStaleIndicator(crf));
      likelihood.setGaussianPriorVariance(gpv);
    }

    CRFOptimizableByGE ge = new CRFOptimizableByGE(crf,constraints,unlabeledSet,map,numThreads,geWeight);
    // turn off the prior... it already appears above!
    ge.setGaussianPriorVariance(Double.POSITIVE_INFINITY);

    CRFOptimizableByGradientValues opt = 
      new CRFOptimizableByGradientValues(crf,new Optimizable.ByGradientValue[] { optLikelihood, ge });
    
    LimitedMemoryBFGS optimizer = new LimitedMemoryBFGS(opt);

    try {
      converged = optimizer.optimize(numIterations);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    optimizer.reset();
    try {
      converged = optimizer.optimize(numIterations);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    if (numThreads > 1) {
      ((ThreadedOptimizable)optLikelihood).shutdown();
      ge.shutdown();
    }
    
    return converged;
  }
  
  @Override
  public boolean train(InstanceList trainingSet, int numIterations) {
    throw new RuntimeException("Must use train(InstanceList trainingSet, InstanceList unlabeledSet, int numIterations) instead");
  }
}
