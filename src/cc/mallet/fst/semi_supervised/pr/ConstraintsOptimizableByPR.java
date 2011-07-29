/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.pr;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

import cc.mallet.fst.CRF;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.Transducer.TransitionIterator;
import cc.mallet.fst.semi_supervised.pr.constraints.PRConstraint;
import cc.mallet.optimize.Optimizable.ByGradientValue;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

/**
 * Optimizable for E-step/I-projection in Posterior Regularization (PR).
 *
 * @author Kedar Bellare
 * @author Gregory Druck
 */

public class ConstraintsOptimizableByPR implements Serializable, ByGradientValue {
	private static Logger logger = MalletLogger.getLogger(ConstraintsOptimizableByPR.class.getName());
 
  private static final long serialVersionUID = 1;
	
  protected boolean cacheStale;
	protected int numParameters;
	protected int numThreads;
	protected InstanceList trainingSet;
	protected double cachedValue = -123456789;
	protected double[] cachedGradient;
	protected CRF crf;
	protected ThreadPoolExecutor executor;
	protected double[][][][] cachedDots;
	PRAuxiliaryModel model;

	public ConstraintsOptimizableByPR(CRF crf, InstanceList ilist, PRAuxiliaryModel model) {
		this(crf,ilist,model,1);
	}
	
	public ConstraintsOptimizableByPR(CRF crf, InstanceList ilist, PRAuxiliaryModel model, int numThreads) {
		this.crf = crf;
		this.trainingSet = ilist;

		this.model = model;
		this.numParameters = model.numParameters();
		cachedGradient = new double[numParameters];
    this.cacheStale = true;
		
		this.numThreads = numThreads;
		this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);
	
		cacheDotProducts();
		
	}

	public void cacheDotProducts() {
	  cachedDots = new double[trainingSet.size()][][][];
	  for (int i = 0; i < trainingSet.size(); i++) {
	    FeatureVectorSequence input = (FeatureVectorSequence)trainingSet.get(i).getData();

	    cachedDots[i] = new double[input.size()][crf.numStates()][crf.numStates()];
	    for (int j = 0; j < input.size(); j++) {
	      for (int k = 0; k < crf.numStates(); k++) {
	        for (int l = 0; l < crf.numStates(); l++) {
	          cachedDots[i][j][k][l] = Transducer.IMPOSSIBLE_WEIGHT;
	        }
	      }
	    }

	    for (int j = 0; j < input.size(); j++) {
	      for (int k = 0; k < crf.numStates(); k++) {
	        TransitionIterator iter = crf.getState(k).transitionIterator(input, j);
	        while (iter.hasNext()) {
	          int l = iter.next().getIndex();
	          cachedDots[i][j][k][l] = iter.getWeight();
	        }
	      }
	    }
	  }
	}
	
	public int getNumParameters() {
		return numParameters;
	}

	public void getParameters(double[] params) {
		model.getParameters(params);
	}

	public double getParameter(int index) {
		return model.getParameter(index);
	}

	public void setParameters(double[] params) {
	  cacheStale = true;
		model.setParameters(params);
	}

	public void setParameter(int index, double value) {
	  cacheStale = true;
		model.setParameter(index, value);
	}

	protected double getExpectationValue() {
		model.zeroExpectations();

		// updating tasks
		ArrayList<Callable<Double>> tasks = new ArrayList<Callable<Double>>();
		int increment = trainingSet.size() / numThreads;
		int start = 0;
		int end = increment;
	  for (int taskIndex = 0; taskIndex < numThreads; taskIndex++) {
			tasks.add(new ExpectationTask(start,end,model.copy()));
			start = end;
			if (taskIndex == numThreads - 2) {
				end = trainingSet.size();
			}
			else {
				end = start + increment;
			}
		}
		
		double value = 0;
		try {
		  List<Future<Double>> results = executor.invokeAll(tasks);
		
			// compute value
			for (Future<Double> f : results) {
				try {
					value += f.get();
				} catch (ExecutionException ee) {
					ee.printStackTrace();
				}
			}
	  } catch (InterruptedException ie) {
		  ie.printStackTrace();
	  }

	  // combine results
	  combine(model,tasks);

		// mu*b - w*||mu||^2
		value += model.getValue();
		return value;
	}

	/**
	 * Returns the log probability of the training sequence labels and the prior
	 * over parameters.
	 */
	public double getValue() {
		if (cacheStale) {
		  cachedValue = getExpectationValue();
		  model.getValueGradient(cachedGradient);
		  cacheStale = false;
			logger.info("getValue (auxiliary distribution) = " + cachedValue);
		}
		return cachedValue;
	}

	public double getCompleteValueContribution() {
		if (cacheStale) {
			getValue();
		}
		double value = model.getCompleteValueContribution();
		return value;
	}
	
	public void getValueGradient(double[] buffer) {
		if (cacheStale) {
			getValue();
		}
		System.arraycopy(cachedGradient, 0, buffer, 0, cachedGradient.length);
	}

	private void combine(PRAuxiliaryModel orig, ArrayList<Callable<Double>> tasks) {
	  for (int i = 0; i < tasks.size(); i++) {
	  	ExpectationTask task = (ExpectationTask)tasks.get(i);
	  	PRAuxiliaryModel model = task.getModelCopy();
	  	for (int ci = 0; ci < model.numConstraints(); ci++) {
	  		PRConstraint origConstraint = orig.getConstraint(ci);
	  		PRConstraint copyConstraint = model.getConstraint(ci);
	  		double[] expectation = new double[origConstraint.numDimensions()];
	  		copyConstraint.getExpectations(expectation);
	  		origConstraint.addExpectations(expectation);
	  	}
	  }
	}

  public void shutdown() {
    executor.shutdown();
  }
  
  public double[][][][] getCachedDots() { 
    return cachedDots;
  }
  
  public PRAuxiliaryModel getAuxModel() { 
    return model;
  }
	
	private class ExpectationTask implements Callable<Double> {
		
		private int start;
		private int end;
    private PRAuxiliaryModel modelCopy;
    
    public ExpectationTask(int start, int end, PRAuxiliaryModel modelCopy) {
    	this.start = start;
    	this.end = end;
    	this.modelCopy = modelCopy;
    }
		
		public Double call() throws Exception {
			double value = 0;
	    for (int ii = start; ii < end; ii++) {
	      Instance inst = trainingSet.get(ii);
				Sequence input = (Sequence) inst.getData();
				// logZ			
				value -= new SumLatticePR(crf, ii, input, null, modelCopy, cachedDots[ii], true, null, null, false).getTotalWeight();
			}
	    return value;
		}
		
		public PRAuxiliaryModel getModelCopy() {
			return modelCopy;
		}
	}
}