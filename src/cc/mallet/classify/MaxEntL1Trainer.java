package cc.mallet.classify;

import cc.mallet.optimize.Optimizer;
import cc.mallet.optimize.OrthantWiseLimitedMemoryBFGS;
import cc.mallet.types.InstanceList;

public class MaxEntL1Trainer extends MaxEntTrainer {
	private static final long serialVersionUID = 1L;

	double l1Weight = 1.0;

	public MaxEntL1Trainer() {
		super(Double.MAX_VALUE);
	}

	public MaxEntL1Trainer(double l1wt) {
		super(Double.MAX_VALUE);
		this.l1Weight = l1wt;
	}

	public MaxEntL1Trainer(MaxEnt initClassifier) {
		super(initClassifier);
		this.gaussianPriorVariance = Double.MAX_VALUE;
	}

	public Optimizer getOptimizer() {
		if (optimizer == null && optimizable != null)
			optimizer = new OrthantWiseLimitedMemoryBFGS(optimizable, l1Weight);
		return optimizer;
	}

	// commented by Limin Yao, use L1 regularization instead
	public Optimizer getOptimizer(InstanceList trainingSet) {
		if (trainingSet != this.trainingSet || optimizable == null) {
			getOptimizable(trainingSet);
			optimizer = null;
		}
		if (optimizer == null)
			optimizer = new OrthantWiseLimitedMemoryBFGS(optimizable, l1Weight);
		return optimizer;
	}
}
