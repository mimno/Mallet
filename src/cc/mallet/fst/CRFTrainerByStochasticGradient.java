package cc.mallet.fst;

import cc.mallet.fst.TransducerTrainer.ByInstanceIncrements;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;

public class CRFTrainerByStochasticGradient extends ByInstanceIncrements
{
	CRF crf;
	double learningRate;
	int iterationCount = 0;
	boolean converged = false;
	CRF.Factors expectations, constraints;

	public CRFTrainerByStochasticGradient (CRF crf, double learningRate) {
		this.crf = crf;
		this.learningRate = learningRate;
		this.expectations = new CRF.Factors(crf);
		this.constraints = new CRF.Factors(crf);
	}
		
	public int getIteration() { return iterationCount; }
	public Transducer getTransducer() {	return crf;	}
	public boolean isFinishedTraining() {	return converged; }
	public void setLearningRate (double r) { this.learningRate = r; }
	public double getLearningRate () { return this.learningRate; }

	public boolean train(InstanceList trainingSet, int numIterations) {
		while (numIterations-- > 0) {
			iterationCount++;
			for (Instance instance : trainingSet)
				this.trainIncremental (instance);
		}
		// TODO Figure out how to measure convergence
		return false;
	}

	public boolean train(InstanceList trainingSet, int batchSize, int numIterations) {
		assert (expectations.structureMatches(crf.parameters));
		assert (constraints.structureMatches(crf.parameters));
		assert (batchSize <= trainingSet.size()); 
		while (numIterations-- > 0) {
			iterationCount++;
			for (int b = 0; b < trainingSet.size()/batchSize; b++) {
				constraints.zero();
				expectations.zero();
				for (int i = b; i < b+batchSize && i < trainingSet.size(); i++) {
					Instance trainingInstance = trainingSet.get(i);
					FeatureVectorSequence fvs = (FeatureVectorSequence) trainingInstance.getData();
					Sequence labelSequence = (Sequence) trainingInstance.getTarget();
					new SumLatticeDefault (crf, fvs, labelSequence, constraints.new Incrementor());
					new SumLatticeDefault (crf, fvs, null, expectations.new Incrementor());
				}
				// Calculate parameter gradient given these instances: (constraints - expectations)
				constraints.plusEquals(expectations, -1);
				// Change the parameters a little by this difference, obeying weightsFrozen
				crf.parameters.plusEquals(constraints, learningRate, true);
			}
		}
		return false;
	}

	// TODO Add some way to train by batches of instances, where the batch memberships are determined externally
	public boolean trainIncremental(InstanceList trainingSet) {
		this.train(trainingSet, 1);
		return false;
	}

	public boolean trainIncremental(Instance trainingInstance) 
	{
		assert (expectations.structureMatches(crf.parameters));
		//assert (constraints.structureMatches(crf.parameters)); redundant
		FeatureVectorSequence fvs = (FeatureVectorSequence) trainingInstance.getData();
		Sequence labelSequence = (Sequence) trainingInstance.getTarget();
		// Gather constraints
		constraints.zero();
		new SumLatticeDefault (crf, fvs, labelSequence, constraints.new Incrementor());
		// Gather expectations
		expectations.zero();
		new SumLatticeDefault (crf, fvs, null, expectations.new Incrementor());
		// Calculate parameter gradient given this one instance: (constraints - expectations)
		constraints.plusEquals(expectations, -1);
		// Change the parameters a little by this difference, obeying weightsFrozen
		crf.parameters.plusEquals(constraints, learningRate, true);
		return false;
	}

}
