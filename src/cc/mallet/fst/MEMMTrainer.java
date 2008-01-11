package cc.mallet.fst;

import java.util.BitSet;
import java.util.logging.Logger;

import cc.mallet.fst.MEMM.State;
import cc.mallet.fst.MEMM.TransitionIterator;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.IndexedSparseVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.SparseVector;
import cc.mallet.util.MalletLogger;

public class MEMMTrainer extends TransducerTrainer 
{
	private static Logger logger = MalletLogger.getLogger(MEMMTrainer.class.getName());

	MEMM memm;
	private boolean gatheringTrainingData = false;
	// After training sets have been gathered in the states, record which
	//   InstanceList we've gathers, so we don't double-count instances.
	private InstanceList trainingGatheredFor;
	boolean useSparseWeights = true;
	MEMMOptimizableByLabelLikelihood omemm;
	
	public MEMMTrainer (MEMM memm) {
		this.memm = memm;
	}

	public MEMMOptimizableByLabelLikelihood getOptimizableMEMM (InstanceList trainingSet) {
		return new MEMMOptimizableByLabelLikelihood (memm, trainingSet);
	}

	public MEMMTrainer setUseSparseWeights (boolean f) { useSparseWeights = f;  return this; }

	public boolean train (InstanceList training) {
		return train (training, Integer.MAX_VALUE);
	}

	public boolean train (InstanceList training, int numIterations)
	{
		if (numIterations <= 0)
			return false;
		assert (training.size() > 0);

		// Allocate space for the parameters, and place transition FeatureVectors in
		// per-source-state InstanceLists.
		// Here, gatheringTrainingSets will be true, and these methods will result
		// in new InstanceList's being created in each source state, and the FeatureVectors
		// of their outgoing transitions to be added to them as the data field in the Instances.
		if (trainingGatheredFor != training) {
			gatherTrainingSets (training);
		}
		if (useSparseWeights) {
			memm.setWeightsDimensionAsIn (training, false);
		} else {
			memm.setWeightsDimensionDensely ();
		}


		/*
		if (false) {
			// Expectation-based placement of training data would go here.
			for (int i = 0; i < training.size(); i++) {
				Instance instance = training.get(i);
				FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
				FeatureSequence output = (FeatureSequence) instance.getTarget();
				// Do it for the paths consistent with the labels...
				gatheringConstraints = true;
				new SumLatticeDefault (this, input, output, true);
				// ...and also do it for the paths selected by the current model (so we will get some negative weights)
				gatheringConstraints = false;
				if (this.someTrainingDone)
					// (do this once some training is done)
					new SumLatticeDefault (this, input, null, true);
			}
			gatheringWeightsPresent = false;
			SparseVector[] newWeights = new SparseVector[weights.length];
			for (int i = 0; i < weights.length; i++) {
				int numLocations = weightsPresent[i].cardinality ();
				logger.info ("CRF weights["+weightAlphabet.lookupObject(i)+"] num features = "+numLocations);
				int[] indices = new int[numLocations];
				for (int j = 0; j < numLocations; j++) {
					indices[j] = weightsPresent[i].nextSetBit (j == 0 ? 0 : indices[j-1]+1);
					//System.out.println ("CRF4 has index "+indices[j]);
				}
				newWeights[i] = new IndexedSparseVector (indices, new double[numLocations],
						numLocations, numLocations, false, false, false);
				newWeights[i].plusEqualsSparse (weights[i]);
			}
			weights = newWeights;
		}
		*/

		omemm = new MEMMOptimizableByLabelLikelihood (memm, training);
		// Gather the constraints
		omemm.gatherExpectationsOrConstraints (true);
		Optimizer maximizer = new LimitedMemoryBFGS(omemm);
		int i;
		boolean continueTraining = true;
		boolean converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		for (i = 0; i < numIterations; i++) {
			try {
				converged = maximizer.optimize (1);
				logger.info ("CRF finished one iteration of maximizer, i="+i);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				logger.info ("Catching exception; saying converged.");
				converged = true;
			}
			if (converged) {
				logger.info ("CRF training has converged, i="+i);
				break;
			}
		}
		logger.info ("About to setTrainable(false)");
		return converged;
	}


	void gatherTrainingSets (InstanceList training)
	{
		if (trainingGatheredFor != null) {
			// It would be easy enough to support this, just go through all the states and set trainingSet to null.
			throw new UnsupportedOperationException ("Training with multiple sets not supported.");
		}

		trainingGatheredFor = training;
		for (int i = 0; i < training.size(); i++) {
			Instance instance = training.get(i);
			FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			// Do it for the paths consistent with the labels...
			new SumLatticeDefault (memm, input, output, new Transducer.Incrementor() {
				public void incrementFinalState(Transducer.State s, double count) { }
				public void incrementInitialState(Transducer.State s, double count) { }
				public void incrementTransition(Transducer.TransitionIterator ti, double count) {
					MEMM.State source = (MEMM.State) ti.getSourceState();
					if (count != 0) {
						// Create the source state's trainingSet if it doesn't exist yet.
						if (((MEMM.State) source).trainingSet == null)
							// New InstanceList with a null pipe, because it doesn't do any processing of input.
							((MEMM.State) source).trainingSet = new InstanceList (null);
						// TODO We should make sure we don't add duplicates (through a second call to setWeightsDimenstion..!
						// TODO Note that when the training data still allows ambiguous outgoing transitions
						// this will add the same FV more than once to the source state's trainingSet, each
						// with >1.0 weight.  Not incorrect, but inefficient.
//						System.out.println ("From: "+source.getName()+" ---> "+getOutput()+" : "+getInput());
						((MEMM.State) source).trainingSet.add (ti.getInput (), ti.getOutput (), null, null, count);
					}
				}
			});
		}
	}


	public boolean train (InstanceList training, InstanceList validation, InstanceList testing,
			TransducerEvaluator eval, int numIterations,
			int numIterationsPerProportion,
			double[] trainingProportions)
	{
		throw new UnsupportedOperationException();
	}

	public boolean trainWithFeatureInduction (InstanceList trainingData,
			InstanceList validationData, InstanceList testingData,
			TransducerEvaluator eval, int numIterations,
			int numIterationsBetweenFeatureInductions,
			int numFeatureInductions,
			int numFeaturesPerFeatureInduction,
			double trueLabelProbThreshold,
			boolean clusteredFeatureInduction,
			double[] trainingProportions,
			String gainName)
	{
		throw new UnsupportedOperationException();
	}


	public void printInstanceLists ()
	{
		for (int i = 0; i < memm.numStates(); i++) {
			State state = (State) memm.getState (i);
			InstanceList training = state.trainingSet;
			System.out.println ("State "+i+" : "+state.getName());
			if (training == null) {
				System.out.println ("No data");
				continue;
			}
			for (int j = 0; j < training.size(); j++) {
				Instance inst = training.get (j);
				System.out.println ("From : "+state.getName()+" To : "+inst.getTarget());
				System.out.println ("Instance "+j);
				System.out.println (inst.getTarget());
				System.out.println (inst.getData());
			}
		}
	}

	public class MEMMOptimizableByLabelLikelihood extends CRFOptimizableByLabelLikelihood implements Optimizable.ByGradientValue
	{
		BitSet infiniteValues = null;

		protected MEMMOptimizableByLabelLikelihood (MEMM memm, InstanceList trainingData)
		{
			super (memm, trainingData);
			expectations = new CRF.Factors (memm);
			constraints = new CRF.Factors (memm);
		}
		
		// if constraints=false, return log probability of the training labels
		protected double gatherExpectationsOrConstraints (boolean gatherConstraints)
		{
			// Instance values must either always or never be included in
			// the total values; we can't just sometimes skip a value
			// because it is infinite, this throws off the total values.
			boolean initializingInfiniteValues = false;
			CRF.Factors factors = gatherConstraints ? constraints : expectations;
			CRF.Factors.Incrementor factorIncrementor = factors.new Incrementor ();

			if (infiniteValues == null) {
				infiniteValues = new BitSet ();
				initializingInfiniteValues = true;
			}

			double labelLogProb = 0;
			for (int i = 0; i < memm.numStates(); i++) {
				MEMM.State s = (State) memm.getState (i);

				if (s.trainingSet == null) {
					System.out.println ("Empty training set for state "+s.name);
					continue;
				}

				for (int j = 0; j < s.trainingSet.size(); j++) {
					Instance instance = s.trainingSet.get (j);
					double instWeight = s.trainingSet.getInstanceWeight (j);
					FeatureVector fv = (FeatureVector) instance.getData ();
					String labelString = (String) instance.getTarget ();
					TransitionIterator iter = new TransitionIterator (s, fv, gatherConstraints?labelString:null, memm);
					while (iter.hasNext ()) {
						State destination = (MEMM.State) iter.nextState();  // Just to advance the iterator
						double weight = iter.getWeight();
						factorIncrementor.incrementTransition(iter, Math.exp(weight) * instWeight);
						//iter.incrementCount (Math.exp(weight) * instWeight);
						if (!gatherConstraints && iter.getOutput() == labelString) {
							if (!Double.isInfinite (weight))
								labelLogProb += instWeight * weight; // xxx   ?????
							else {
								logger.warning ("State "+i+" transition "+j+" has infinite cost; skipping.");
								if (initializingInfiniteValues)
									throw new IllegalStateException ("Infinite-cost transitions not yet supported"); //infiniteValues.set (j);
								else if (!infiniteValues.get(j))
									throw new IllegalStateException ("Instance i used to have non-infinite value, "
											+"but now it has infinite value.");
							}
						}
					}
				}
			}

			// Force initial & final weight parameters to 0 by making sure that 
			// whether factor refers to expectation or constraint, they have the same value.
			for (int i = 0; i < memm.numStates(); i++) {
				factors.initialWeights[i] = 0.0;
				factors.finalWeights[i] = 0.0;
			}

			return labelLogProb;
		}

		// log probability of the training sequence labels, and fill in expectations[]
		protected double getExpectationValue ()
		{
			return gatherExpectationsOrConstraints (false);
		}

	}
	
	
	@Override
	public int getIteration() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Transducer getTransducer() {
		return memm;
	}

	@Override
	public boolean isFinishedTraining() {
		// TODO Auto-generated method stub
		return false;
	}


}
