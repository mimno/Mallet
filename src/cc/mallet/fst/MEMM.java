/* Copyright (C) 2004 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
		@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>

    MEMM might have been simply implemented with a MaxEnt classifier object at each state,
    but I chose not to do that so that tied features could be used in different parts of the
    FSM, just as in CRF.  So, the expectation-gathering is done (in MEMM-style) without
    forward-backward, just with local (normalized) distributions over destination states
    from source states, but there is a global MaximizebleMEMM, and all the MEMMs parameters
    are set together as part of a single optimization.
 */

package cc.mallet.fst;


import java.io.Serializable;
import java.util.BitSet;
import java.util.logging.Logger;
import java.text.DecimalFormat;

import cc.mallet.classify.MaxEnt;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

public class MEMM extends CRF implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(MEMM.class.getName());
	private boolean gatheringTrainingData = false;

  // After training sets have been gathered in the states, record which
  //   InstanceList we've gathers, so we don't double-count instances.
  private InstanceList trainingGatheredFor;


  public MEMM (Pipe inputPipe, Pipe outputPipe)
	{
		super (inputPipe, outputPipe);
	}

	public MEMM (Alphabet inputAlphabet, Alphabet outputAlphabet)
	{
		super (inputAlphabet, outputAlphabet);
	}

  public MEMM (CRF crf)
  {
    super (crf);
  }

	protected CRF.State newState (String name, int index,
	                               double initialCost, double finalCost,
	                               String[] destinationNames,
	                               String[] labelNames,
	                               String[][] weightNames,
	                               CRF crf)
	{
		return new State (name, index, initialCost, finalCost,
		                  destinationNames, labelNames, weightNames, crf);
	}


	public boolean train (InstanceList training, InstanceList validation, InstanceList testing,
												TransducerEvaluator eval, int numIterations)
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
			setWeightsDimensionAsIn (training);
		} else {
			setWeightsDimensionDensely ();
		}




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

		MaximizableMEMM maximizable = new MaximizableMEMM (training, this);
		// Gather the constraints
		maximizable.gatherExpectationsOrConstraints (true);
		Optimizer maximizer = new LimitedMemoryBFGS(maximizable);
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
			if (eval != null) {
				continueTraining = eval.evaluate (this, (converged || i == numIterations-1), i,
																					converged, maximizable.getValue(), training, validation, testing);
				if (!continueTraining)
					break;
			}
			if (converged) {
				logger.info ("CRF training has converged, i="+i);
				break;
			}
		}
		logger.info ("About to setTrainable(false)");
		// Free the memory of the expectations and constraints
		setTrainable (false);
		logger.info ("Done setTrainable(false)");
		return converged;
	}


  void gatherTrainingSets (InstanceList training)
  {
    if (trainingGatheredFor != null) {
      // It would be easy enough to support this, just got through all the states and set trainingSet to null.
      throw new UnsupportedOperationException ("Training with multiple sets not supported.");
    }

    trainingGatheredFor = training;
    gatheringTrainingData = true;
    for (int i = 0; i < training.size(); i++) {
        Instance instance = training.get(i);
        FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
        FeatureSequence output = (FeatureSequence) instance.getTarget();
        // Do it for the paths consistent with the labels...
        new SumLatticeDefault (this, input, output, true);
     }
     gatheringTrainingData = false;
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

	public OptimizableCRF getMaximizableCRF (InstanceList ilist)
	{
		return new MaximizableMEMM (ilist, this);
	}


  public void printInstanceLists ()
  {
    for (int i = 0; i < numStates (); i++) {
      State state = (State) getState (i);
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


  public static class State extends CRF.State implements Serializable
	{
		InstanceList trainingSet;

		protected State (String name, int index,
										 double initialCost, double finalCost,
										 String[] destinationNames,
										 String[] labelNames,
										 String[][] weightNames,
										 CRF crf)
		{
		  super (name, index, initialCost, finalCost, destinationNames, labelNames, weightNames, crf);
		}

		// Necessary because the CRF4 implementation will return CRF4.TransitionIterator
		public Transducer.TransitionIterator transitionIterator (
			Sequence inputSequence, int inputPosition,
			Sequence outputSequence, int outputPosition)
		{
			if (inputPosition < 0 || outputPosition < 0)
				throw new UnsupportedOperationException ("Epsilon transitions not implemented.");
			if (inputSequence == null)
				throw new UnsupportedOperationException ("CRFs are not generative models; must have an input sequence.");
			return new TransitionIterator (
				this, (FeatureVectorSequence)inputSequence, inputPosition,
				(outputSequence == null ? null : (String)outputSequence.get(outputPosition)), crf);
		}


    public void incrementFinalCount (double count)
    {
      if (!((MEMM)crf).gatheringTrainingData) {
        super.incrementFinalCount (count);
      }
    }


    public void incrementInitialCount (double count)
    {
      if (!((MEMM)crf).gatheringTrainingData) {
        super.incrementInitialCount (count);
      }
    }


	}

	protected static class TransitionIterator extends CRF.TransitionIterator implements Serializable
	{
    private double sum;

		public TransitionIterator (State source,
															 FeatureVectorSequence inputSeq,
															 int inputPosition,
															 String output, CRF memm)
		{
			super (source, inputSeq, inputPosition, output, memm);
			normalizeCosts ();
		}

		public TransitionIterator (State source,
															 FeatureVector fv,
															 String output, CRF memm)
		{
			super (source, fv, output, memm);
			normalizeCosts ();
		}

		private void normalizeCosts ()
		{
			// Normalize the next-state costs, so they are -(log-probabilities)
			// This is the heart of the difference between the locally-normalized MEMM
			// and the globally-normalized CRF
		  sum = INFINITE_COST;
			for (int i = 0; i < weights.length; i++)
				sum = sumNegLogProb (sum, weights[i]);
			assert (!Double.isNaN (sum));
			if (!Double.isInfinite (sum)) {
			  for (int i = 0; i < weights.length; i++)
				  weights[i] -= sum;
      }
		}

    public void incrementCount (double count)
    {
      if (((MEMM) crf).gatheringTrainingData) {
        if (!crf.someTrainingDone && count != 0) {
          // Create the source state's trainingSet if it doesn't exist yet.
          if (((MEMM.State) source).trainingSet == null)
          // New InstanceList with a null pipe, because it doesn't do any processing of input.
            ((MEMM.State) source).trainingSet = new InstanceList (null);
          // xxx We should make sure we don't add duplicates (through a second call to setWeightsDimenstion..!
          // xxx Note that when the training data still allows ambiguous outgoing transitions
          // this will add the same FV more than once to the source state's trainingSet, each
          // with >1.0 weight.  Not incorrect, but inefficient.
//        System.out.println ("From: "+source.getName()+" ---> "+getOutput()+" : "+getInput());
          ((MEMM.State) source).trainingSet.add (this.getInput (), this.getOutput (), null, null, count);
        }
      } else {
        super.incrementCount (count);
      }
    }

    public String describeTransition (double cutoff)
    {
      DecimalFormat f = new DecimalFormat ("0.###");
      return super.describeTransition (cutoff) + "Log Z = "+f.format(sum)+"\n";
    }
	}


	public class MaximizableMEMM extends OptimizableCRF implements Optimizable.ByGradientValue
	{

		protected MaximizableMEMM (InstanceList trainingData, MEMM memm)
		{
			super (trainingData, memm);
		}

		// if constraints=false, return log probability of the training labels
		protected double gatherExpectationsOrConstraints (boolean constraints)
		{
			// Instance values must either always or never be included in
			// the total values; we can't just sometimes skip a value
			// because it is infinite, this throws off the total values.
			boolean initializingInfiniteValues = false;

			if (infiniteValues == null) {
				infiniteValues = new BitSet ();
				initializingInfiniteValues = true;
			}

			double labelLogProb = 0;
			for (int i = 0; i < crf.numStates(); i++) {
				MEMM.State s = (State) crf.getState (i);

				if (s.trainingSet == null) {
					System.out.println ("Empty training set for state "+s.name);
					continue;
				}

				for (int j = 0; j < s.trainingSet.size(); j++) {
					Instance instance = s.trainingSet.get (j);
					double instWeight = s.trainingSet.getInstanceWeight (j);
					FeatureVector fv = (FeatureVector) instance.getData ();
					String labelString = (String) instance.getTarget ();
					TransitionIterator iter = new TransitionIterator (s, fv, constraints?labelString:null, crf);
					while (iter.hasNext ()) {
						State destination = (MEMM.State) iter.nextState();  // Just to advance the iterator
						double cost = iter.getCost();
						iter.incrementCount (Math.exp(-cost) * instWeight);
						if (!constraints && iter.getOutput() == labelString) {
							if (!Double.isInfinite (cost))
								labelLogProb += -instWeight * cost; // xxx   ?????
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

      // Force initial & final costs to 0 ???
      for (int i = 0; i < crf.numStates(); i++) {
        MEMM.State s = (State) crf.getState (i);
        s.initialExpectation = s.initialConstraint;
        s.finalExpectation = s.finalConstraint;
      }

			return labelLogProb;
		}

		// log probability of the training sequence labels, and fill in expectations[]
		protected double getExpectationValue ()
		{
			return gatherExpectationsOrConstraints (false);
		}

	}
}
