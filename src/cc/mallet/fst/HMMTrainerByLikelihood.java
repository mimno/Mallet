package cc.mallet.fst;

import java.util.logging.Logger;

import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

public class HMMTrainerByLikelihood extends TransducerTrainer {
	private static Logger logger = MalletLogger
			.getLogger(HMMTrainerByLikelihood.class.getName());

	HMM hmm;
	InstanceList trainingSet, unlabeledSet;
	int iterationCount = 0;
	boolean converged = false;

	public HMMTrainerByLikelihood(HMM hmm) {
		this.hmm = hmm;
	}

	@Override
	public Transducer getTransducer() {
		return hmm;
	}

	@Override
	public int getIteration() {
		return iterationCount;
	}

	@Override
	public boolean isFinishedTraining() {
		return converged;
	}

	@Override
	public boolean train(InstanceList trainingSet, int numIterations) {
		return train(trainingSet, null, numIterations);
	}

	public boolean train(InstanceList trainingSet, InstanceList unlabeledSet,
			int numIterations) {
		if (hmm.emissionEstimator == null)
			hmm.reset();

		converged = false;
		double threshold = 0.001;
		double logLikelihood = Double.NEGATIVE_INFINITY, prevLogLikelihood;
		for (int iter = 0; iter < numIterations; iter++) {
			prevLogLikelihood = logLikelihood;
			logLikelihood = 0;
			for (Instance inst : trainingSet) {
				FeatureSequence input = (FeatureSequence) inst.getData();
				FeatureSequence output = (FeatureSequence) inst.getTarget();
				double obsLikelihood = new SumLatticeDefault(hmm, input,
						output, hmm.new Incrementor()).getTotalWeight();
				logLikelihood += obsLikelihood;
			}
			logger.info("getValue() (observed log-likelihood) = "
					+ logLikelihood);

			if (unlabeledSet != null) {
				int numEx = 0;
				for (Instance inst : unlabeledSet) {
					numEx++;
					if (numEx % 100 == 0) {
						System.err.print(numEx + ". ");
						System.err.flush();
					}
					FeatureSequence input = (FeatureSequence) inst.getData();
					double hiddenLikelihood = new SumLatticeDefault(hmm, input,
							null, hmm.new Incrementor()).getTotalWeight();
					logLikelihood += hiddenLikelihood;
				}
				System.err.println();
			}
			logger.info("getValue() (log-likelihood) = " + logLikelihood);

			hmm.estimate();
			iterationCount++;
			logger.info("HMM finished one iteration of maximizer, i=" + iter);

			runEvaluators();

			if (Math.abs(logLikelihood - prevLogLikelihood) < threshold) {
				converged = true;
				logger.info("HMM training has converged, i=" + iter);
				break;
			}
		}

		return converged;
	}
}
