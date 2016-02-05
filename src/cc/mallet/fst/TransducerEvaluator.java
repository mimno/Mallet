/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.fst;

import java.util.logging.Logger;

import cc.mallet.types.InstanceList;

import cc.mallet.optimize.Optimizable;

import cc.mallet.util.MalletLogger;

/**
 * An abstract class to evaluate a transducer model.
 */
public abstract class TransducerEvaluator
{
  private static Logger logger = MalletLogger.getLogger(TransducerEvaluator.class.getName());

  // TODO consider storing the TransducerTrainer here also?  Methods like precondition() will be shorter and easier.
	protected InstanceList[] instanceLists;
	protected String[] instanceListDescriptions;
	
	public TransducerEvaluator () {
		instanceLists = new InstanceList[0];
		instanceListDescriptions = new String[0];
	}
	
	public TransducerEvaluator (InstanceList[] instanceLists, String[] instanceListDescriptions) {
		this.instanceLists = instanceLists;
		this.instanceListDescriptions = instanceListDescriptions;
	}

  /**
   * Evaluates a TransducerTrainer and its Transducer on the instance lists specified in the constructor.               .
   * <P>
   * The default implementation calls the evaluator's <TT>evaluateInstanceList</TT> on each instance list.
   *
   * @param tt The TransducerTrainer to evaluate.
   */
	public void evaluate (TransducerTrainer tt)	{
		if (!precondition(tt))
			return;
		this.preamble(tt);
		for (int k = 0; k < instanceLists.length; k++)
			if (instanceLists[k] != null)
				evaluateInstanceList (tt, instanceLists[k], instanceListDescriptions[k]);
	}
	
	protected void preamble (TransducerTrainer tt) {
		int iteration = tt.getIteration();
		Optimizable opt;
		if (tt instanceof TransducerTrainer.ByOptimization 
				&& (opt = ((TransducerTrainer.ByOptimization)tt).getOptimizer().getOptimizable()) instanceof Optimizable.ByValue) 
			logger.info ("Evaluator iteration="+iteration+" cost="+((Optimizable.ByValue)opt).getValue());
		else
			logger.info ("Evaluator iteration="+iteration+" cost=NA (not Optimizable.ByValue)");
	}
	
	/** If this returns false, then the body of the evaluate(TransducerTrainer) method will not run. 
	 * Use this method to implement behaviors such as only evaluating every 5 iterations with
	 * <code>
	 * new TokenAccuracyEvaluator (crft) { public boolean precondition (TransducerTrainer tt) { return tt.getIteration() % 5 == 0; };
	 * </code>*/
	public boolean precondition (TransducerTrainer tt) {
		return true;
	}

  public abstract void evaluateInstanceList (TransducerTrainer transducer, InstanceList instances, String description);


}
