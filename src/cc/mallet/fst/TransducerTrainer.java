package cc.mallet.fst;

import java.util.ArrayList;

import cc.mallet.optimize.Optimizer;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public abstract class TransducerTrainer {
	// The list of evaluators to be run every once in a while
	ArrayList<TransducerEvaluator> evaluators;
	
	public abstract Transducer getTransducer();
	public abstract int getIteration();
	public abstract boolean isFinishedTraining();
	
	public boolean train (InstanceList trainingSet) {
		return train (trainingSet, Integer.MAX_VALUE);
	}
	
	/** Train the tranducer associated with this TransducerTrainer.  
	 * You should be able to call this method with different trainingSet objects.
	 * Whether this causes the TransducerTrainer to combine both trainingSets or
	 * to view the second as a new alternative is at the discretion of the particular
	 * TransducerTrainer subclass involved. */
	public abstract boolean train (InstanceList trainingSet, int numIterations);

	// TODO Consider adding or removing these
	//public abstract boolean train ();
	//public abstract boolean train (int numIterations);
	
	// Management of evaluators
	public void addEvaluator (TransducerEvaluator te) {	evaluators.add(te);	}
	public void removeEvaluator (TransducerEvaluator te) { evaluators.remove(te);	}
	/** This method should be called by subclasses whenever evaluators should be run.
	 * Do not worry too much about them being run too often, because the evaluators
	 * themselves can control/limit when they actually do their work with TransducerEvaluator.precondition(). */
	protected void runEvaluators () {
		for (TransducerEvaluator te : evaluators) 
			te.evaluate(this);
	}
	
	public interface ByOptimization {
		public Optimizer getOptimizer ();
		// Remove the above, and only have public Optimizer getOptimizer (InstanceList trainingSet); 
	}
	
	// Implied above; can always make a per-instance training method use a batch instance list
	//public interface ByBatch {}
	
	// TODO Consider making this an interface also, like ByOptimization
	public static abstract class ByIncrements extends TransducerTrainer {
		public abstract boolean trainIncremental (InstanceList incrementalTrainingSet);
	}
		
	public static abstract class ByInstanceIncrements extends ByIncrements {
		public abstract boolean trainIncremental (Instance trainingInstance);

	}
}
