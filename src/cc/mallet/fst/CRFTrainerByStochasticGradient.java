package cc.mallet.fst;

import java.util.ArrayList;
import java.util.Collections;

import cc.mallet.fst.TransducerTrainer.ByInstanceIncrements;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;

/**
 * Trains CRF by stochastic gradient. Most effective on large training sets.
 * 
 * @author kedarb
 * 
 */
public class CRFTrainerByStochasticGradient extends ByInstanceIncrements {
	protected CRF crf;

	// t is the decaying factor. lambda is some regularization depending on the
	// training set size and the gaussian prior.
	protected double learningRate, t, lambda;

	protected int iterationCount = 0;

	protected boolean converged = false;

	protected CRF.Factors expectations, constraints;

	public CRFTrainerByStochasticGradient (CRF crf, InstanceList trainingSample) {
		this.crf = crf;
		this.expectations = new CRF.Factors(crf);
		this.constraints = new CRF.Factors(crf);
		this.setLearningRateByLikelihood(trainingSample);
	}


	public CRFTrainerByStochasticGradient (CRF crf, double learningRate) {
		this.crf = crf;
		this.learningRate = learningRate;
		this.expectations = new CRF.Factors(crf);
		this.constraints = new CRF.Factors(crf);
	}

	public int getIteration() {
		return iterationCount;
	}

	public Transducer getTransducer() {
		return crf;
	}

	public boolean isFinishedTraining() {
		return converged;
	}

	// Best way to choose learning rate is to run training on a sample and set
	// it to the rate that produces maximum increase in likelihood or accuracy.
	// Then, to be conservative just halve the learning rate.
	// In general, eta = 1/(lambda*t) where
	// lambda=priorVariance*numTrainingInstances
	// After an initial eta_0 is set, t_0 = 1/(lambda*eta_0)
	// After each training step eta = 1/(lambda*(t+t_0)), t=0,1,2,..,Infinity
	/** Automatically set the learning rate to one that would be good */
	public void setLearningRateByLikelihood (InstanceList trainingSample) {
		int numIterations = 5; // was 10 -akm 1/25/08
		double bestLearningRate = Double.NEGATIVE_INFINITY;
		double bestLikelihoodChange = Double.NEGATIVE_INFINITY;

		double currLearningRate = 5e-11;
		while (currLearningRate < 1) {
			currLearningRate *= 2;
			crf.parameters.zero();
			double beforeLikelihood = computeLikelihood(trainingSample);
			double likelihoodChange = trainSample(trainingSample, numIterations, currLearningRate)	- beforeLikelihood;
			System.out.println("likelihood change = " + likelihoodChange	+ " for learningrate=" + currLearningRate);

			if (likelihoodChange > bestLikelihoodChange) {
				bestLikelihoodChange = likelihoodChange;
				bestLearningRate = currLearningRate;
			}
		}

		// reset the parameters
		crf.parameters.zero();
		// conservative estimate for learning rate
		bestLearningRate /= 2;
		System.out.println("Setting learning rate to " + bestLearningRate);
		setLearningRate(bestLearningRate);
	}

	private double trainSample (InstanceList trainingSample, int numIterations,	double rate) {
		double lambda = trainingSample.size();
		double t = 1 / (lambda * rate);

		double loglik = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < numIterations; i++) {
			loglik = 0.0;
			for (int j = 0; j < trainingSample.size(); j++) {
				rate = 1 / (lambda * t);
				loglik += trainIncrementalLikelihood(trainingSample.get(j), rate);
				t += 1.0;
			}
		}

		return loglik;
	}

	private double computeLikelihood (InstanceList trainingSample) {
		double loglik = 0.0;
		for (int i = 0; i < trainingSample.size(); i++) {
			Instance trainingInstance = trainingSample.get(i);
			FeatureVectorSequence fvs = (FeatureVectorSequence) trainingInstance.getData();
			Sequence labelSequence = (Sequence) trainingInstance.getTarget();
			loglik += new SumLatticeDefault(crf, fvs, labelSequence, null).getTotalWeight();
			loglik -= new SumLatticeDefault(crf, fvs, null, null).getTotalWeight();
		}
		constraints.zero();
		expectations.zero();
		return loglik;
	}

	public void setLearningRate (double r) {
		this.learningRate = r;
	}

	public double getLearningRate () {
		return this.learningRate;
	}

	public boolean train (InstanceList trainingSet, int numIterations) {
		assert (expectations.structureMatches(crf.parameters));
		assert (constraints.structureMatches(crf.parameters));
		lambda = 1.0 / trainingSet.size();
		t = 1.0 / (lambda * learningRate);
		converged = false;

		ArrayList<Integer> trainingIndices = new ArrayList<Integer>();
		for (int i = 0; i < trainingSet.size(); i++)
			trainingIndices.add(i);

		double oldLoglik = Double.NEGATIVE_INFINITY;
		while (numIterations-- > 0) {
			iterationCount++;

			// shuffle the indices
			Collections.shuffle(trainingIndices);

			double loglik = 0.0;
			for (int i = 0; i < trainingSet.size(); i++) {
				learningRate = 1.0 / (lambda * t);
				loglik += trainIncrementalLikelihood(trainingSet.get(trainingIndices.get(i)));
				t += 1.0;
			}

			System.out.println("loglikelihood[" + numIterations + "] = "	+ loglik);

			if (Math.abs(loglik - oldLoglik) < 1e-3) {
				converged = true;
				break;
			}
			oldLoglik = loglik;

			Runtime.getRuntime().gc();
			runEvaluators();
		}

		return converged;
	}

	// TODO Add some way to train by batches of instances, where the batch
	// memberships are determined externally?  Or provide some easy interface for creating batches.
	public boolean trainIncremental (InstanceList trainingSet) {
		this.train(trainingSet, 1);
		return false;
	}

	public boolean trainIncremental (Instance trainingInstance) {
		assert (expectations.structureMatches(crf.parameters));
		trainIncrementalLikelihood(trainingInstance);
		return false;
	}

	/** Adjust the parameters by default learning rate according to the gradient of this single Instance, 
	 * and return the true label sequence likelihood. */
	public double trainIncrementalLikelihood (Instance trainingInstance) {
		return trainIncrementalLikelihood(trainingInstance, learningRate);
	}

	/** Adjust the parameters by learning rate according to the gradient of this single Instance, 
	 * and return the true label sequence likelihood. */
	public double trainIncrementalLikelihood (Instance trainingInstance, double rate) {
		double singleLoglik;
		constraints.zero();
		expectations.zero();
		FeatureVectorSequence fvs = (FeatureVectorSequence) trainingInstance.getData();
		Sequence labelSequence = (Sequence) trainingInstance.getTarget();
		singleLoglik = new SumLatticeDefault(crf, fvs, labelSequence,	constraints.new Incrementor()).getTotalWeight();
		singleLoglik -= new SumLatticeDefault(crf, fvs, null,	expectations.new Incrementor()).getTotalWeight();
		// Calculate parameter gradient given these instances: (constraints - expectations)
		constraints.plusEquals(expectations, -1);
		// Change the parameters a little by this difference, obeying weightsFrozen
		crf.parameters.plusEquals(constraints, rate, true);

		return singleLoglik;
	}
}
