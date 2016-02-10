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
import cc.mallet.optimize.Optimizable.ByGradientValue;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

/**
 * M-step/M-projection for PR.
 *
 * @author Kedar Bellare
 * @author Gregory Druck
 */

public class CRFOptimizableByKL implements Serializable, ByGradientValue {
	private static Logger logger = MalletLogger.getLogger(CRFOptimizableByKL.class.getName());

	private static final long serialVersionUID = 1L;
	
  protected int cachedValueWeightsStamp;
  protected int cachedGradientWeightsStamp;
  protected int numParameters;
  protected int numThreads;
  protected double weight;
  protected double gaussianPriorVariance = 1.0;
  protected double cachedValue = -123456789;
  protected double[] cachedGradient;
  protected List<double[]> initialProbList, finalProbList;
  protected List<double[][][]> transitionProbList;
	protected InstanceList trainingSet;
	protected CRF crf;
	protected CRF.Factors constraints, expectations;
	protected ThreadPoolExecutor executor;
	protected PRAuxiliaryModel auxModel;

	public CRFOptimizableByKL(CRF crf, InstanceList trainingSet,
			PRAuxiliaryModel auxModel, double[][][][] cachedDots, int numThreads, double weight) {
		this.crf = crf;
		this.trainingSet = trainingSet;

		this.numParameters = crf.getParameters().getNumFactors();
		this.cachedGradient = new double[numParameters];

		this.cachedValueWeightsStamp = -1;
		this.cachedGradientWeightsStamp = -1;
		
		assert(weight > 0);
		this.weight = weight;
		
		gatherConstraints(auxModel, cachedDots);
		
		this.numThreads = numThreads;
		this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);
	}

	private double[] toProbabilities(double weights[]) {
	  double probs[] = new double[weights.length];
	  for (int i = 0; i < weights.length; i++)
	    probs[i] = Math.exp(weights[i]);
	  // TODO this shouldn't be necessary
	  MatrixOps.normalize(probs);
	  return probs;
	}

	private void toProbabilities(double weights[][][]) {
	  for (int i = 0; i < weights.length; i++)
	    for (int j = 0; j < weights[i].length; j++)
	      for (int k = 0; k < weights[i][j].length; k++)
	        weights[i][j][k] = Math.exp(weights[i][j][k]);
	}

	@SuppressWarnings("unchecked")
	protected void gatherConstraints(
			PRAuxiliaryModel auxModel, double[][][][] cachedDots) {
		initialProbList = new ArrayList<double[]>();
		finalProbList = new ArrayList<double[]>();
		transitionProbList = new ArrayList<double[][][]>();

		constraints = new CRF.Factors(crf.getParameters());
		expectations = new CRF.Factors(crf.getParameters());

		constraints.zero();
		for (int ii = 0; ii < trainingSet.size(); ii++) {
		  Instance inst = trainingSet.get(ii);
			Sequence input = (Sequence) inst.getData();

			SumLatticePR geLatt = 
				new SumLatticePR(crf, ii, input, null, auxModel, cachedDots[ii], false, null, null, true);
			double gammas[][] = geLatt.getGammas();

			double initialProbs[] = toProbabilities(gammas[0]);
			initialProbList.add(initialProbs);

			double finalProbs[] = toProbabilities(gammas[gammas.length - 1]);
			finalProbList.add(finalProbs);

			double transitionProbs[][][] = geLatt.getXis();
			toProbabilities(transitionProbs);
			transitionProbList.add(transitionProbs);

			new SumLatticeKL(crf, input, initialProbs,
					finalProbs, transitionProbs, null, constraints.new Incrementor());
		}
	}

	@SuppressWarnings("unchecked")
	protected double getExpectationValue() {
		expectations.zero();
		
		// updating tasks
		ArrayList<Callable<Double>> tasks = new ArrayList<Callable<Double>>();
		int increment = trainingSet.size() / numThreads;
		int start = 0;
		int end = increment;
	  for (int taskIndex = 0; taskIndex < numThreads; taskIndex++) {
	  	// same structure, but with zero values
	  	CRF.Factors exCopy = new CRF.Factors(expectations);
	  	tasks.add(new ExpectationTask(start,end,exCopy));
	  	
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
    for (Callable<Double> task : tasks) {
      this.expectations.plusEquals(((ExpectationTask)task).getExpectationsCopy(), 1);
    }

		return value;
	}

	public double getValue() {
		if (crf.getWeightsValueChangeStamp() != cachedValueWeightsStamp) {
			cachedValueWeightsStamp = crf.getWeightsValueChangeStamp();
			long startingTime = System.currentTimeMillis();

			cachedValue = getExpectationValue();

			// Incorporate prior on parameters
			double priorValue = crf.getParameters().gaussianPrior(gaussianPriorVariance);
			cachedValue += priorValue;
      logger.info("Gaussian prior = " + priorValue);
			
      cachedValue *= weight;
      
			assert (!(Double.isNaN(cachedValue) || Double.isInfinite(cachedValue))) : "Label likelihood is NaN/Infinite";

			logger.info("getValue() (loglikelihood, optimizable by klDiv) = "+ cachedValue);
			long endingTime = System.currentTimeMillis();
			logger.fine("Inference milliseconds = " + (endingTime - startingTime));
		}
		return cachedValue;
	}

	public void getValueGradient(double[] buffer) {
		if (cachedGradientWeightsStamp != crf.getWeightsValueChangeStamp()) {
			cachedGradientWeightsStamp = crf.getWeightsValueChangeStamp();
			getValue();

			expectations.plusEquals(constraints, -1.0);
			expectations.plusEqualsGaussianPriorGradient(crf.getParameters(), -gaussianPriorVariance);
			expectations.assertNotNaNOrInfinite();
			expectations.getParameters(cachedGradient);
			MatrixOps.timesEquals(cachedGradient, -weight);
		}

		System.arraycopy(cachedGradient, 0, buffer, 0, cachedGradient.length);
	}

	public int getNumParameters() {
		return numParameters;
	}

	public void getParameters(double[] buffer) {
		crf.getParameters().getParameters(buffer);
	}

	public double getParameter(int index) {
		return crf.getParameters().getParameter(index);
	}

	public void setParameters(double[] buff) {
		crf.getParameters().setParameters(buff);
		crf.weightsValueChanged();
	}

	public void setParameter(int index, double value) {
		crf.getParameters().setParameter(index, value);
		crf.weightsValueChanged();
	}
	
  public void setGaussianPriorVariance(double value) {
    gaussianPriorVariance = value;
  }
  
  public void shutdown() {
    executor.shutdown();
  }

  private class ExpectationTask implements Callable<Double> {

  	private int start;
  	private int end;
  	private CRF.Factors expectationsCopy;
  	
  	public ExpectationTask(int start, int end, CRF.Factors exCopy) {
  	  this.start = start;
  	  this.end = end;
  	  this.expectationsCopy = exCopy;
  	}
  	
  	public CRF.Factors getExpectationsCopy() {
  		return expectationsCopy;
  	}
  	
		public Double call() throws Exception {
			double value = 0;
			
			for (int ii = start; ii < end; ii++) {
				Instance inst = trainingSet.get(ii);
				Sequence input = (Sequence) inst.getData();
				double initProbs[] = initialProbList.get(ii);
				double finalProbs[] = finalProbList.get(ii);
				double transProbs[][][] = transitionProbList.get(ii);

	      double[][][] cachedDots = new double[input.size()][crf.numStates()][crf.numStates()];
	      for (int j = 0; j < input.size(); j++) {
	        for (int k = 0; k < crf.numStates(); k++) {
	          for (int l = 0; l < crf.numStates(); l++) {
	            cachedDots[j][k][l] = Transducer.IMPOSSIBLE_WEIGHT;
	          }
	        }
	      }
				
				double labeledWeight = new SumLatticeKL(crf, input, initProbs, 
				    finalProbs, transProbs, cachedDots, null).getTotalWeight();
				value += labeledWeight;

				//double unlabeledWeight = new SumLatticeDefault(crf, input, 
				//    expectationsCopy.new Incrementor()).getTotalWeight();
        double unlabeledWeight = new SumLatticeDefaultCachedDot(crf, input, null, 
            cachedDots, expectationsCopy.new Incrementor(), false, null).getTotalWeight();

				value -= unlabeledWeight;
			}
			return value;
		}
  }

  
}
