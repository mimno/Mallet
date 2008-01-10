package cc.mallet.fst;

import cc.mallet.optimize.Optimizer;
import cc.mallet.types.InstanceList;

/** A TransducerTrainer that does no training, but simply acts as a container for a Transducer;
 * for use in situations that require a TransducerTrainer, such as the TransducerEvaluator methods. */
public class NoopTransducerTrainer extends TransducerTrainer {
	
	Transducer transducer;
	
	public NoopTransducerTrainer (Transducer tranducer) {
		this.transducer = transducer;
	}

	@Override
	public int getIteration() {
		return -1;
	}

	@Override
	public Transducer getTransducer() {
		return transducer;
	}

	@Override
	public boolean isFinishedTraining() {
		return true;
	}

	@Override
	public boolean trainIncremental(InstanceList trainingSet) {
		return true;
	}

	@Override
	public boolean train(InstanceList trainingSet, int numIterations) {
		return true;
	}

}
