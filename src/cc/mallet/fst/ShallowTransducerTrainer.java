package cc.mallet.fst;

import cc.mallet.types.InstanceList;

/*
 * Wraps around an already trained <tt>Transducer</tt> model. <p>
 * 
 * Use this class to pass to the <tt>*Evaluator.evaluateInstanceList</tt> when we 
 * don't have access to the *Trainer that was used to train the Transducer model.
 */
public class ShallowTransducerTrainer extends TransducerTrainer {

	Transducer transducer;
	
	public ShallowTransducerTrainer(Transducer transducer) {
		this.transducer = transducer;
	}
	
	public int getIteration() { return 0; }
	public Transducer getTransducer() { return transducer; }
	public boolean isFinishedTraining() { return false; }
	public boolean train(InstanceList trainingSet, int numIterations) { throw new IllegalStateException("Cannot use this class for training"); }

}
