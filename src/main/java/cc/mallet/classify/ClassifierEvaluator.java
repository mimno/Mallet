package cc.mallet.classify;

import java.util.logging.Logger;

import cc.mallet.fst.TransducerEvaluator;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

public abstract class ClassifierEvaluator 
{
  private static Logger logger = MalletLogger.getLogger(ClassifierEvaluator.class.getName());

	InstanceList[] instanceLists;
	String[] instanceListDescriptions;
	
	public ClassifierEvaluator (InstanceList[] instanceLists, String[] instanceListDescriptions) {
		this.instanceLists = instanceLists;
		this.instanceListDescriptions = instanceListDescriptions;
	}
	
	public ClassifierEvaluator (InstanceList instanceList1, String instanceListDescription1) {
		this(new InstanceList[] {instanceList1}, new String[] {instanceListDescription1});
	}
	
	public ClassifierEvaluator (InstanceList instanceList1, String instanceListDescription1,
			InstanceList instanceList2, String instanceListDescription2) {
		this(new InstanceList[] {instanceList1, instanceList2}, new String[] {instanceListDescription1, instanceListDescription2});
	}

	public ClassifierEvaluator (InstanceList instanceList1, String instanceListDescription1,
			InstanceList instanceList2, String instanceListDescription2,
			InstanceList instanceList3, String instanceListDescription3) {
		this(new InstanceList[] {instanceList1, instanceList2, instanceList3}, 
				new String[] {instanceListDescription1, instanceListDescription2, instanceListDescription3});
	}

	
	/**
   * Evaluates a ClassifierTrainer and its Classifier on the instance lists specified in the constructor.               .
   * <P>
   * The default implementation calls the evaluator's <TT>evaluateInstanceList</TT> on each instance list.
   *
   * @param ct The TransducerTrainer to evaluate.
   */
	public void evaluate (ClassifierTrainer ct)	{
		this.preamble(ct);
		for (int k = 0; k < instanceLists.length; k++)
			if (instanceLists[k] != null)
				evaluateInstanceList (ct, instanceLists[k], instanceListDescriptions[k]);
	}
	
	protected void preamble (ClassifierTrainer ct) {
		if (ct instanceof ClassifierTrainer.ByOptimization) {
			Optimizable opt;
			int iteration = ((ClassifierTrainer.ByOptimization)ct).getIteration();
			if ((opt = ((ClassifierTrainer.ByOptimization)ct).getOptimizer().getOptimizable()) instanceof Optimizable.ByValue) 
				logger.info ("Evaluator iteration="+iteration+" cost="+((Optimizable.ByValue)opt).getValue());
			else
				logger.info ("Evaluator iteration="+iteration+" cost=NA (not Optimizable.ByValue)");
		}
	}

	
	public abstract void evaluateInstanceList (ClassifierTrainer trainer, InstanceList instances, String description);

}
