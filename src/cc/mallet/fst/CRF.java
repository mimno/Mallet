/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
		@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.fst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.regex.*;
import java.util.logging.*;
import java.io.*;
import java.text.DecimalFormat;

import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;
import cc.mallet.util.ArrayUtils;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;
import cc.mallet.types.MatrixOps;


/* There are several different kinds of numeric values:

   "weights" range from -Inf to Inf.  High weights make a path more
   likely.  These don't appear directly in Transducer.java, but appear
	 as parameters to many subclasses, such as CRFs.  Weights are also
	 often summed, or combined in a dot product with feature vectors.

	 "unnormalized costs" range from -Inf to Inf.  High costs make a
	 path less likely.  Unnormalized costs can be obtained from negated
	 weights or negated sums of weights.  These are often returned by a
	 TransitionIterator's getValue() method.  The LatticeNode.alpha
	 values are unnormalized costs.

	 "normalized costs" range from 0 to Inf.  High costs make a path
	 less likely.  Normalized costs can safely be considered as the
	 -log(probability) of some event.  They can be obtained by
	 subtracting a (negative) normalizer from unnormalized costs, for
	 example, subtracting the total cost of a lattice.  Typically
	 initialCosts and finalCosts are examples of normalized costs, but
	 they are also allowed to be unnormalized costs.  The gammas[][],
	 stateGammas[], and transitionXis[][] are all normalized costs, as
	 well as the return value of Lattice.getValue().

	 "probabilities" range from 0 to 1.  High probabilities make a path
	 more likely.  They are obtained from normalized costs by taking the
	 log and negating.  

	 "sums of probabilities" range from 0 to positive numbers.  They are
	 the sum of several probabilities.  These are passed to the
	 incrementCount() methods.

 */


public class CRF extends Transducer implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(CRF.class.getName());

	static final String LABEL_SEPARATOR = ",";

	protected Alphabet inputAlphabet;
	protected Alphabet outputAlphabet;
	
	protected ArrayList<State> states = new ArrayList<State> ();
	protected ArrayList<State> initialStates = new ArrayList<State> ();
	protected HashMap<String,State> name2state = new HashMap<String,State> ();
	
	protected Factors parameters = new Factors ();

	//SparseVector[] weights;
	//double[] defaultWeights;	// parameters for default feature
	//Alphabet weightAlphabet = new Alphabet ();
	//boolean[] weightsFrozen;

	// FeatureInduction can fill this in
	protected FeatureSelection globalFeatureSelection;
	// "featureSelections" is on a per- weights[i] basis, and over-rides
	// (permanently disabling) FeatureInducer's and
	// setWeightsDimensionsAsIn() from using these features on these transitions
	protected FeatureSelection[] featureSelections;
	
	// Store here the induced feature conjunctions so that these conjunctions can be added to test instances before transduction
	protected ArrayList<FeatureInducer> featureInducers = new ArrayList<FeatureInducer>();

	// An integer index that gets incremented each time this CRFs parameters get changed
	protected int weightsValueChangeStamp = 0;
	// An integer index that gets incremented each time this CRFs parameters' structure get changed
	protected int weightsStructureChangeStamp = 0;
	
	protected int cachedNumParametersStamp = -1; // A copy of weightsStructureChangeStamp the last time numParameters was calculated
	protected int numParameters;
	
	
	/** A simple, transparent container to hold the parameters or sufficient statistics for the CRF. */
	public static class Factors implements Serializable {
		Alphabet weightAlphabet;
		public SparseVector[] weights; // parameters on transitions, indexed by "weight index"
		public double[] defaultWeights;// parameters for default features, indexed by "weight index" 
		public boolean[] weightsFrozen; // flag, if true indicating that the weights of this "weight index" should not be changed by learning, indexed by "weight index" 
		public double [] initialWeights; // indexed by state index
		public double [] finalWeights; // indexed by state index
		
		/** Construct a new empty Factors with a new empty weightsAlphabet, 0-length initialWeights and finalWeights, and the other arrays null. */
		public Factors () {
			weightAlphabet = new Alphabet();
			initialWeights = new double[0];
			finalWeights = new double[0];
			// Leave the rest as null.  They will get set later by addState() and addWeight()
			// Alternatively, we could create zero-length arrays
		}
		
		/** Construct new Factors by mimicking the structure of the other one, but with zero values. 
		 * Always simply point to the other's Alphabet; do not clone it. */
		public Factors (Factors other) {
			weightAlphabet = other.weightAlphabet;
			weights = new SparseVector[other.weights.length];
			for (int i = 0; i < weights.length; i++)
				weights[i] = (SparseVector) other.weights[i].cloneMatrixZeroed();
			defaultWeights = new double[other.defaultWeights.length];
			weightsFrozen = other.weightsFrozen; // We don't copy here because we want "expectation" and "constraint" factors to get changes to a CRF.parameters factor.  Alternatively we declare freezing to be a change of structure, and force reallocation of "expectations", etc.
			initialWeights = new double[other.initialWeights.length];
			finalWeights = new double[other.finalWeights.length];
		}
		
		/** Construct new Factors by copying the other one. */
		public Factors (Factors other, boolean cloneAlphabet) {
			weightAlphabet = cloneAlphabet ? (Alphabet) other.weightAlphabet.clone() : other.weightAlphabet;
			weights = new SparseVector[other.weights.length];
			for (int i = 0; i < weights.length; i++)
				weights[i] = (SparseVector) other.weights[i].cloneMatrix();
			defaultWeights = other.defaultWeights.clone();
			weightsFrozen = other.weightsFrozen;
			initialWeights = other.initialWeights.clone();
			finalWeights = other.finalWeights.clone();
		}
		
		/** Construct a new Factors with the same structure as the parameters of 'crf', but with values initialized to zero.
		 * This method is typically used to allocate storage for sufficient statistics, expectations, constraints, etc. */
		public Factors (CRF crf) {
			// TODO Change this implementation to this(crf.parameters)
			weightAlphabet = crf.parameters.weightAlphabet; // TODO consider cloning this instead
			weights = new SparseVector[crf.parameters.weights.length];
			for (int i = 0; i < weights.length; i++)
				weights[i] = (SparseVector) crf.parameters.weights[i].cloneMatrixZeroed();
			defaultWeights = new double[crf.parameters.weights.length];
			weightsFrozen = crf.parameters.weightsFrozen;
			assert (crf.numStates() == crf.parameters.initialWeights.length);
			assert (crf.parameters.initialWeights.length == crf.parameters.finalWeights.length);
			initialWeights = new double[crf.parameters.initialWeights.length];
			finalWeights = new double[crf.parameters.finalWeights.length];
		}
		
		public int getNumFactors () {
			assert (initialWeights.length == finalWeights.length);
			assert (defaultWeights.length == weights.length);
			int ret = initialWeights.length + finalWeights.length + defaultWeights.length;
			for (int i = 0; i < weights.length; i++)
				ret += weights[i].numLocations();
			return ret;
		}
		
		public void zero () {
			for (int i = 0; i < weights.length; i++)
				weights[i].setAll(0);
			Arrays.fill(defaultWeights, 0);
			Arrays.fill(initialWeights, 0);
			Arrays.fill(finalWeights, 0);
		}
		
		public boolean structureMatches (Factors other) {
			if (weightAlphabet.size() != other.weightAlphabet.size()) return false;
			if (weights.length != other.weights.length) return false;
			// gsc: checking each SparseVector's size within weights.
			for (int i = 0; i < weights.length; i++)
				if (weights[i].numLocations() != other.weights[i].numLocations()) return false;
			// Note that we are not checking the indices of the SparseVectors in weights
			if (defaultWeights.length != other.defaultWeights.length) return false;
			assert (initialWeights.length == finalWeights.length);
			if (initialWeights.length != other.initialWeights.length) return false;
			return true;
		}
		
		public void assertNotNaN () {
			for (int i = 0; i < weights.length; i++)
				assert (!weights[i].isNaN());
			assert (!MatrixOps.isNaN(defaultWeights));
			assert (!MatrixOps.isNaN(initialWeights));
			assert (!MatrixOps.isNaN(finalWeights));
		}
		
		// gsc: checks all weights to make sure there are no NaN or Infinite values,
    // this method can be called for checking the weights of constraints and
    // expectations but not for crf.parameters since it can have infinite
    // weights associated with states that are not likely.
		public void assertNotNaNOrInfinite () {
			for (int i = 0; i < weights.length; i++)
				assert (!weights[i].isNaNOrInfinite());
			assert (!MatrixOps.isNaNOrInfinite(defaultWeights));
			assert (!MatrixOps.isNaNOrInfinite(initialWeights));
			assert (!MatrixOps.isNaNOrInfinite(finalWeights));
		}
		
		public void plusEquals (Factors other, double factor) {
			plusEquals(other, factor, false);
		}
		
		public void plusEquals (Factors other, double factor, boolean obeyWeightsFrozen) {
			for (int i = 0; i < weights.length; i++) {
				if (obeyWeightsFrozen && weightsFrozen[i]) continue;
				this.weights[i].plusEqualsSparse(other.weights[i], factor);
				this.defaultWeights[i] += other.defaultWeights[i] * factor;
			}
			for (int i = 0; i < initialWeights.length; i++) {
				this.initialWeights[i] += other.initialWeights[i] * factor;
				this.finalWeights[i] += other.finalWeights[i] * factor;
			}
		}
		
		/** Return the log(p(parameters)) according to a zero-mean Gaussian with given variance. */
		public double gaussianPrior (double variance) {
			double value = 0;
			double priorDenom = 2 * variance;
			assert (initialWeights.length == finalWeights.length);
			for (int i = 0; i < initialWeights.length; i++) {
				if (!Double.isInfinite(initialWeights[i])) value -= initialWeights[i] * initialWeights[i] / priorDenom;
				if (!Double.isInfinite(finalWeights[i])) value -= finalWeights[i] * finalWeights[i] / priorDenom;
			}
			double w;
			for (int i = 0; i < weights.length; i++) {
				if (!Double.isInfinite(defaultWeights[i])) value -= defaultWeights[i] * defaultWeights[i] / priorDenom;
				for (int j = 0; j < weights[i].numLocations(); j++) {
					w = weights[i].valueAtLocation (j);
					if (!Double.isInfinite(w)) value -= w * w / priorDenom;
				}
			}
			return value;
		}

		public void plusEqualsGaussianPriorGradient (Factors other, double variance) {
			assert (initialWeights.length == finalWeights.length);
			for (int i = 0; i < initialWeights.length; i++) {
				// gsc: checking initial/final weights of crf.parameters as well since we could
        // have a state machine where some states have infinite initial and/or final weight
				if (!Double.isInfinite(initialWeights[i]) && !Double.isInfinite(other.initialWeights[i])) 
          initialWeights[i] -= other.initialWeights[i] / variance;
				if (!Double.isInfinite(finalWeights[i]) && !Double.isInfinite(other.finalWeights[i])) 
          finalWeights[i] -= other.finalWeights[i] / variance;
			}
			double w, ow;
			for (int i = 0; i < weights.length; i++) {
				if (weightsFrozen[i]) continue;
				// TODO Note that there doesn't seem to be a way to freeze the initialWeights and finalWeights 
				// TODO Should we also obey FeatureSelection here?  No need; it is enforced by the creation of the weights.
				if (!Double.isInfinite(defaultWeights[i])) defaultWeights[i] -= other.defaultWeights[i] / variance;
				for (int j = 0; j < weights[i].numLocations(); j++) {
					w = weights[i].valueAtLocation (j);
					ow = other.weights[i].valueAtLocation (j);
					if (!Double.isInfinite(w)) weights[i].setValueAtLocation(j, w - (ow/variance));
				}
			}
		}

		/** Return the log(p(parameters)) according to a a hyperbolic curve that is a smooth approximation to an L1 prior. */
		public double hyberbolicPrior (double slope, double sharpness) {
			double value = 0;
			assert (initialWeights.length == finalWeights.length);
			for (int i = 0; i < initialWeights.length; i++) {
				if (!Double.isInfinite(initialWeights[i]))
					value -= (slope / sharpness	* Math.log (Maths.cosh (sharpness * -initialWeights[i])));
				if (!Double.isInfinite(finalWeights[i]))
					value -= (slope / sharpness * Math.log (Maths.cosh (sharpness * -finalWeights[i])));
			}
			double w;
			for (int i = 0; i < weights.length; i++) {
				value -= (slope / sharpness	* Math.log (Maths.cosh (sharpness * defaultWeights[i])));
				for (int j = 0; j < weights[i].numLocations(); j++) {
					w = weights[i].valueAtLocation(j);
					if (!Double.isInfinite(w))
						value -= (slope / sharpness	* Math.log (Maths.cosh (sharpness * w)));
				}
			}
			return value;
		}
		
		public void plusEqualsHyperbolicPriorGradient (Factors other, double slope, double sharpness) {
			// TODO This method could use some careful checking over, especially for flipped negations
			assert (initialWeights.length == finalWeights.length);
			double ss = slope * sharpness;
			for (int i = 0; i < initialWeights.length; i++) {
				// gsc: checking initial/final weights of crf.parameters as well since we could
        // have a state machine where some states have infinite initial and/or final weight
				if (!Double.isInfinite(initialWeights[i]) && !Double.isInfinite(other.initialWeights[i]))
          initialWeights[i] += ss * Maths.tanh (-other.initialWeights[i]);
				if (!Double.isInfinite(finalWeights[i]) && !Double.isInfinite(other.finalWeights[i]))
          finalWeights[i] += ss * Maths.tanh (-other.finalWeights[i]);
			}
			double w, ow;
			for (int i = 0; i < weights.length; i++) {
				if (weightsFrozen[i]) continue;
				// TODO Note that there doesn't seem to be a way to freeze the initialWeights and finalWeights 
				// TODO Should we also obey FeatureSelection here?  No need; it is enforced by the creation of the weights.
				if (!Double.isInfinite(defaultWeights[i])) defaultWeights[i] += ss * Maths.tanh(-other.defaultWeights[i]);
				for (int j = 0; j < weights[i].numLocations(); j++) {
					w = weights[i].valueAtLocation (j);
					ow = other.weights[i].valueAtLocation (j);
					if (!Double.isInfinite(w)) weights[i].setValueAtLocation(j, w + (ss * Maths.tanh(-ow)));
				}
			}
		}

		
		/** Instances of this inner class can be passed to various inference methods, which can then 
		 * gather/increment sufficient statistics counts into the containing Factor instance. */
		public class Incrementor implements Transducer.Incrementor {
			public void incrementFinalState(Transducer.State s, double count) {
				finalWeights[s.getIndex()] += count;
			}
			public void incrementInitialState(Transducer.State s, double count) {
				initialWeights[s.getIndex()] += count;
			}
			public void incrementTransition(Transducer.TransitionIterator ti, double count) {
				int index = ti.getIndex();
				CRF.State source = (CRF.State)ti.getSourceState(); 
				int nwi = source.weightsIndices[index].length;
				int weightsIndex;
				for (int wi = 0; wi < nwi; wi++) {
					weightsIndex = source.weightsIndices[index][wi];
				// For frozen weights, don't even gather their sufficient statistics; this is how we ensure that the gradient for these will be zero
					if (weightsFrozen[weightsIndex]) continue; 
					// TODO Should we also obey FeatureSelection here?  No need; it is enforced by the creation of the weights.
					weights[weightsIndex].plusEqualsSparse ((FeatureVector)ti.getInput(), count);
					defaultWeights[weightsIndex] += count;
				}
				}
			}
		
		public double getParametersAbsNorm ()
		{
			double ret = 0;
			for (int i = 0; i < initialWeights.length; i++) {
				if (initialWeights[i] > Transducer.IMPOSSIBLE_WEIGHT)
					ret += Math.abs(initialWeights[i]);
				if (finalWeights[i] > Transducer.IMPOSSIBLE_WEIGHT)
					ret += Math.abs(finalWeights[i]);
			}
			for (int i = 0; i < weights.length; i++) {
				ret += Math.abs(defaultWeights[i]);
				int nl = weights[i].numLocations();
				for (int j = 0; j < nl; j++)
					ret += Math.abs(weights[i].valueAtLocation(j));
			}
			return ret;
		}
		
		public class WeightedIncrementor implements Transducer.Incrementor {
			double instanceWeight = 1.0;
			public WeightedIncrementor (double instanceWeight) { 
				this.instanceWeight = instanceWeight; 
			}
			public void incrementFinalState(Transducer.State s, double count) {
				finalWeights[s.getIndex()] += count * instanceWeight;
			}
			public void incrementInitialState(Transducer.State s, double count) {
				initialWeights[s.getIndex()] += count * instanceWeight;
			}
			public void incrementTransition(Transducer.TransitionIterator ti, double count) {
				int index = ti.getIndex();
				CRF.State source = (CRF.State)ti.getSourceState(); 
				int nwi = source.weightsIndices[index].length;
				int weightsIndex;
				count *= instanceWeight;
				for (int wi = 0; wi < nwi; wi++) {
					weightsIndex = source.weightsIndices[index][wi];
				// For frozen weights, don't even gather their sufficient statistics; this is how we ensure that the gradient for these will be zero
					if (weightsFrozen[weightsIndex]) continue; 
					// TODO Should we also obey FeatureSelection here?  No need; it is enforced by the creation of the weights.
					weights[weightsIndex].plusEqualsSparse ((FeatureVector)ti.getInput(), count);
					defaultWeights[weightsIndex] += count;
				}
			}
		}
		

		
		public void getParameters (double[] buffer)
		{
			if (buffer.length != getNumFactors ())
				throw new IllegalArgumentException ("Expected size of buffer: " + getNumFactors() + ", actual size: " + buffer.length);
			int pi = 0;
			for (int i = 0; i < initialWeights.length; i++) {
				buffer[pi++] = initialWeights[i];
				buffer[pi++] = finalWeights[i];
			}
			for (int i = 0; i < weights.length; i++) {
				buffer[pi++] = defaultWeights[i];
				int nl = weights[i].numLocations();
				for (int j = 0; j < nl; j++)
					buffer[pi++] = weights[i].valueAtLocation(j);
			}
		}

		public double getParameter (int index) {
			int numStateParms = 2 * initialWeights.length;
			if (index < numStateParms) {
				if (index % 2 == 0)
					return initialWeights[index/2];
				else
					return finalWeights[index/2];
			} else {
				index -= numStateParms;
				for (int i = 0; i < weights.length; i++) {
					if (index == 0)
						return this.defaultWeights[i];
					index--;
					if (index < weights[i].numLocations())
						return weights[i].valueAtLocation (index);
					else
						index -= weights[i].numLocations();
				}
				throw new IllegalArgumentException ("index too high = "+index);
			}
		}

		public void setParameters (double [] buff) {
			assert (buff.length == getNumFactors());
			int pi = 0;
			for (int i = 0; i < initialWeights.length; i++) {
				initialWeights[i] = buff[pi++];
				finalWeights[i] = buff[pi++];
			}
			for (int i = 0; i < weights.length; i++) {
				this.defaultWeights[i] = buff[pi++];
				int nl = weights[i].numLocations();
				for (int j = 0; j < nl; j++)
					weights[i].setValueAtLocation (j, buff[pi++]);
			}
		}

		public void setParameter (int index, double value) {
			int numStateParms = 2 * initialWeights.length;
			if (index < numStateParms) {
				if (index % 2 == 0)
					initialWeights[index/2] = value;
				else
					finalWeights[index/2] = value;
			} else {
				index -= numStateParms;
				for (int i = 0; i < weights.length; i++) {

					if (index == 0) {
						defaultWeights[i] = value;
						return;
					} else
						index--;
					if (index < weights[i].numLocations()) {
						weights[i].setValueAtLocation (index, value);
					} else
						index -= weights[i].numLocations();
				}
				throw new IllegalArgumentException ("index too high = "+index);
			}
		}
		
		// gsc: Serialization for Factors
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 1;
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject (weightAlphabet);
			out.writeObject (weights);
			out.writeObject (defaultWeights);
			out.writeObject (weightsFrozen);
			out.writeObject (initialWeights);
			out.writeObject (finalWeights);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			weightAlphabet = (Alphabet) in.readObject ();
			weights = (SparseVector[]) in.readObject ();
			defaultWeights = (double[]) in.readObject ();
			weightsFrozen = (boolean[]) in.readObject ();
			initialWeights = (double[]) in.readObject ();
			finalWeights = (double[]) in.readObject ();
		}
	}

	
	public CRF (Pipe inputPipe, Pipe outputPipe)
	{
		super (inputPipe, outputPipe);
		this.inputAlphabet = inputPipe.getDataAlphabet();
		this.outputAlphabet = inputPipe.getTargetAlphabet();
		//inputAlphabet.stopGrowth();
	}

	public CRF (Alphabet inputAlphabet, Alphabet outputAlphabet)
	{
		super (new Noop(inputAlphabet, outputAlphabet), null);
		inputAlphabet.stopGrowth();
		logger.info ("CRF input dictionary size = "+inputAlphabet.size());
		//xxx outputAlphabet.stopGrowth();
		this.inputAlphabet = inputAlphabet;
		this.outputAlphabet = outputAlphabet;
	}

	/** Create a CRF whose states and weights are a copy of those from another CRF. */
	public CRF (CRF other)
	{
		// This assumes that "other" has non-null inputPipe and outputPipe. We'd need to add another constructor to handle this if not.
		this (other.getInputPipe (), other.getOutputPipe ());
		copyStatesAndWeightsFrom (other);
		assertWeightsLength ();
	}

	private void copyStatesAndWeightsFrom (CRF initialCRF)
	{
		this.parameters = new Factors (initialCRF.parameters, true);  // This will copy all the transition weights
		this.parameters.weightAlphabet = (Alphabet) initialCRF.parameters.weightAlphabet.clone();
		//weightAlphabet = (Alphabet) initialCRF.weightAlphabet.clone ();
		//weights = new SparseVector [initialCRF.weights.length];
		
		states.clear ();
		// Clear these, because they will be filled by this.addState()
		this.parameters.initialWeights = new double[0];
		this.parameters.finalWeights = new double[0];
	
		for (int i = 0; i < initialCRF.states.size(); i++) {
			State s = (State) initialCRF.getState (i);
			String[][] weightNames = new String[s.weightsIndices.length][];
			for (int j = 0; j < weightNames.length; j++) {
				int[] thisW = s.weightsIndices[j];
				weightNames[j] = (String[]) initialCRF.parameters.weightAlphabet.lookupObjects(thisW, new String [s.weightsIndices[j].length]);
			}
			addState (s.name, initialCRF.parameters.initialWeights[i], initialCRF.parameters.finalWeights[i], 
					s.destinationNames, s.labels, weightNames);
		}

		featureSelections = (FeatureSelection[]) initialCRF.featureSelections.clone ();
		// yyy weightsFrozen = (boolean[]) initialCRF.weightsFrozen.clone();
	}

	public Alphabet getInputAlphabet () { return inputAlphabet; }
	public Alphabet getOutputAlphabet () { return outputAlphabet; }
	
	/** This method should be called whenever the CRFs weights (parameters) have their structure/arity/number changed. */
	public void weightsStructureChanged () {
		weightsStructureChangeStamp++;
		weightsValueChangeStamp++;
	}
	
	/** This method should be called whenever the CRFs weights (parameters) are changed. */
	public void weightsValueChanged () {
		weightsValueChangeStamp++;
	}

	// This method can be over-ridden in subclasses of CRF to return subclasses of CRF.State
	protected CRF.State newState (String name, int index,
			double initialWeight, double finalWeight,
			String[] destinationNames,
			String[] labelNames,
			String[][] weightNames,
			CRF crf)
	{
		return new State (name, index, initialWeight, finalWeight,
				destinationNames, labelNames, weightNames, crf);
	}


	public void addState (String name, double initialWeight, double finalWeight,
			String[] destinationNames,
			String[] labelNames,
			String[][] weightNames)
	{
		assert (weightNames.length == destinationNames.length);
		assert (labelNames.length == destinationNames.length);
		weightsStructureChanged();
		if (name2state.get(name) != null)
			throw new IllegalArgumentException ("State with name `"+name+"' already exists.");
		parameters.initialWeights = MatrixOps.append(parameters.initialWeights, initialWeight);
		parameters.finalWeights = MatrixOps.append(parameters.finalWeights, finalWeight);
		State s = newState (name, states.size(), initialWeight, finalWeight,
				destinationNames, labelNames, weightNames, this);
		s.print ();
		states.add (s);
		if (initialWeight > IMPOSSIBLE_WEIGHT)
			initialStates.add (s);
		name2state.put (name, s);
	}

	public void addState (String name, double initialWeight, double finalWeight,
			String[] destinationNames,
			String[] labelNames,
			String[] weightNames)
	{
		String[][] newWeightNames = new String[weightNames.length][1];
		for (int i = 0; i < weightNames.length; i++)
			newWeightNames[i][0] = weightNames[i];
		this.addState (name, initialWeight, finalWeight, destinationNames, labelNames, newWeightNames);
	}

	// Default gives separate parameters to each transition
	public void addState (String name, double initialWeight, double finalWeight,
			String[] destinationNames,
			String[] labelNames)
	{
		assert (destinationNames.length == labelNames.length);
		String[] weightNames = new String[labelNames.length];
		for (int i = 0; i < labelNames.length; i++)
			weightNames[i] = name + "->" + destinationNames[i] + ":" + labelNames[i];
		this.addState (name, initialWeight, finalWeight, destinationNames, labelNames, weightNames);
	}

	// Add a state with parameters equal zero, and labels on out-going arcs
	// the same name as their destination state names.
	public void addState (String name, String[] destinationNames)
	{
		this.addState (name, 0, 0, destinationNames, destinationNames);
	}

	// Add a group of states that are fully connected with each other,
	// with parameters equal zero, and labels on their out-going arcs
	// the same name as their destination state names.
	public void addFullyConnectedStates (String[] stateNames)
	{
		for (int i = 0; i < stateNames.length; i++)
			addState (stateNames[i], stateNames);
	}

	public void addFullyConnectedStatesForLabels ()
	{
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
					outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
		}
		addFullyConnectedStates (labels);
	}

	public void addStartState ()
	{
		addStartState ("<START>");
	}

	public void addStartState (String name)
	{
		for (int i = 0; i < numStates (); i++)
			parameters.initialWeights[i] = IMPOSSIBLE_WEIGHT;

		String[] dests = new String [numStates()];
		for (int i = 0; i < dests.length; i++)
			dests[i] = getState(i).getName();

		addState (name, 0, 0.0, dests, dests); // initialWeight of 0.0
	}

	public void setAsStartState (State state)
	{
		for (int i = 0; i < numStates(); i++) {
			Transducer.State other = getState (i);
			if (other == state) {
				other.setInitialWeight (0);
			} else {
				other.setInitialWeight (IMPOSSIBLE_WEIGHT);
			}
		}
		weightsValueChanged();
	}

	private boolean[][] labelConnectionsIn (InstanceList trainingSet)
	{
		return labelConnectionsIn (trainingSet, null);
	}

	private boolean[][] labelConnectionsIn (InstanceList trainingSet, String start)
	{
		int numLabels = outputAlphabet.size();
		boolean[][] connections = new boolean[numLabels][numLabels];
		for (int i = 0; i < trainingSet.size(); i++) {
			Instance instance = trainingSet.get(i);
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			for (int j = 1; j < output.size(); j++) {
				int sourceIndex = outputAlphabet.lookupIndex (output.get(j-1));
				int destIndex = outputAlphabet.lookupIndex (output.get(j));
				assert (sourceIndex >= 0 && destIndex >= 0);
				connections[sourceIndex][destIndex] = true;
			}
		}

		// Handle start state
		if (start != null) {
			int startIndex = outputAlphabet.lookupIndex (start);
			for (int j = 0; j < outputAlphabet.size(); j++) {
				connections[startIndex][j] = true;
			}
		}

		return connections;
	}

	/** Add states to create a first-order Markov model on labels,
			adding only those transitions the occur in the given
			trainingSet. */
	public void addStatesForLabelsConnectedAsIn (InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) numDestinations++;
			String[] destinationNames = new String[numDestinations];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					destinationNames[destinationIndex++] = (String)outputAlphabet.lookupObject(j);
			addState ((String)outputAlphabet.lookupObject(i), destinationNames);
		}
	}

	/** Add as many states as there are labels, but don't create separate weights
			for each source-destination pair of states.  Instead have all the incoming
			transitions to a state share the same weights. */
	public void addStatesForHalfLabelsConnectedAsIn (InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) numDestinations++;
			String[] destinationNames = new String[numDestinations];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					destinationNames[destinationIndex++] = (String)outputAlphabet.lookupObject(j);
			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
					destinationNames, destinationNames, destinationNames);
		}
	}

	/** Add as many states as there are labels, but don't create
			separate observational-test-weights for each source-destination
			pair of states---instead have all the incoming transitions to a
			state share the same observational-feature-test weights.
			However, do create separate default feature for each transition,
			(which acts as an HMM-style transition probability). */
	public void addStatesForThreeQuarterLabelsConnectedAsIn (InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) numDestinations++;
			String[] destinationNames = new String[numDestinations];
			String[][] weightNames = new String[numDestinations][];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) {
					String labelName = (String)outputAlphabet.lookupObject(j);
					destinationNames[destinationIndex] = labelName;
					weightNames[destinationIndex] = new String[2];
					// The "half-labels" will include all observational tests
					weightNames[destinationIndex][0] = labelName;
					// The "transition" weights will include only the default feature
					String wn = (String)outputAlphabet.lookupObject(i) + "->" + (String)outputAlphabet.lookupObject(j);
					weightNames[destinationIndex][1] = wn;
					int wi = getWeightsIndex (wn);
					// A new empty FeatureSelection won't allow any features here, so we only
					// get the default feature for transitions
					featureSelections[wi] = new FeatureSelection(trainingSet.getDataAlphabet());
					destinationIndex++;
				}
			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
					destinationNames, destinationNames, weightNames);
		}
	}

	public void addFullyConnectedStatesForThreeQuarterLabels (InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		for (int i = 0; i < numLabels; i++) {
			String[] destinationNames = new String[numLabels];
			String[][] weightNames = new String[numLabels][];
			for (int j = 0; j < numLabels; j++) {
				String labelName = (String)outputAlphabet.lookupObject(j);
				destinationNames[j] = labelName;
				weightNames[j] = new String[2];
				// The "half-labels" will include all observational tests
				weightNames[j][0] = labelName;
				// The "transition" weights will include only the default feature
				String wn = (String)outputAlphabet.lookupObject(i) + "->" + (String)outputAlphabet.lookupObject(j);
				weightNames[j][1] = wn;
				int wi = getWeightsIndex (wn);
				// A new empty FeatureSelection won't allow any features here, so we only
				// get the default feature for transitions
				featureSelections[wi] = new FeatureSelection(trainingSet.getDataAlphabet());
			}
			addState ((String)outputAlphabet.lookupObject(i), 0.0, 0.0,
					destinationNames, destinationNames, weightNames);
		}
	}

	public void addFullyConnectedStatesForBiLabels ()
	{
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
					outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
		}
		for (int i = 0; i < labels.length; i++) {
			for (int j = 0; j < labels.length; j++) {
				String[] destinationNames = new String[labels.length];
				for (int k = 0; k < labels.length; k++)
					destinationNames[k] = labels[j]+LABEL_SEPARATOR+labels[k];
				addState (labels[i]+LABEL_SEPARATOR+labels[j], 0.0, 0.0,
						destinationNames, labels);
			}
		}
	}

	/** Add states to create a second-order Markov model on labels,
			adding only those transitions the occur in the given
			trainingSet. */
	public void addStatesForBiLabelsConnectedAsIn (InstanceList trainingSet)
	{
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn (trainingSet);
		for (int i = 0; i < numLabels; i++) {
			for (int j = 0; j < numLabels; j++) {
				if (!connections[i][j])
					continue;
				int numDestinations = 0;
				for (int k = 0; k < numLabels; k++)
					if (connections[j][k]) numDestinations++;
				String[] destinationNames = new String[numDestinations];
				String[] labels = new String[numDestinations];
				int destinationIndex = 0;
				for (int k = 0; k < numLabels; k++)
					if (connections[j][k]) {
						destinationNames[destinationIndex] =
							(String)outputAlphabet.lookupObject(j)+LABEL_SEPARATOR+(String)outputAlphabet.lookupObject(k);
						labels[destinationIndex] = (String)outputAlphabet.lookupObject(k);
						destinationIndex++;
					}
				addState ((String)outputAlphabet.lookupObject(i)+LABEL_SEPARATOR+
						(String)outputAlphabet.lookupObject(j), 0.0, 0.0,
						destinationNames, labels);
			}
		}
	}

	public void addFullyConnectedStatesForTriLabels ()
	{
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
					outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
		}
		for (int i = 0; i < labels.length; i++) {
			for (int j = 0; j < labels.length; j++) {
				for (int k = 0; k < labels.length; k++) {
					String[] destinationNames = new String[labels.length];
					for (int l = 0; l < labels.length; l++)
						destinationNames[l] = labels[j]+LABEL_SEPARATOR+labels[k]+LABEL_SEPARATOR+labels[l];
					addState (labels[i]+LABEL_SEPARATOR+labels[j]+LABEL_SEPARATOR+labels[k], 0.0, 0.0,
							destinationNames, labels);
				}
			}
		}
	}

	public void addSelfTransitioningStateForAllLabels (String name)
	{
		String[] labels = new String[outputAlphabet.size()];
		String[] destinationNames  = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info ("CRF: outputAlphabet.lookup class = "+
					outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = (String) outputAlphabet.lookupObject(i);
			destinationNames[i] = name;
		}
		addState (name, 0.0, 0.0, destinationNames, labels);
	}

	private String concatLabels(String[] labels)
	{
		String sep = "";
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < labels.length; i++)
		{
			buf.append(sep).append(labels[i]);
			sep = LABEL_SEPARATOR;
		}
		return buf.toString();
	}

	private String nextKGram(String[] history, int k, String next)
	{
		String sep = "";
		StringBuffer buf = new StringBuffer();
		int start = history.length + 1 - k;
		for (int i = start; i < history.length; i++)
		{
			buf.append(sep).append(history[i]);
			sep = LABEL_SEPARATOR;
		}
		buf.append(sep).append(next);
		return buf.toString();
	}

	private boolean allowedTransition(String prev, String curr,
			Pattern no, Pattern yes)
	{
		String pair = concatLabels(new String[]{prev, curr});
		if (no != null && no.matcher(pair).matches())
			return false;
		if (yes != null && !yes.matcher(pair).matches())
			return false;
		return true;
	}

	private boolean allowedHistory(String[] history, Pattern no, Pattern yes) {
		for (int i = 1; i < history.length; i++)
			if (!allowedTransition(history[i-1], history[i], no, yes))
				return false;
		return true;
	}

	/**
	 * Assumes that the CRF's output alphabet contains
	 * <code>String</code>s. Creates an order-<em>n</em> CRF with input
	 * predicates and output labels given by <code>trainingSet</code>
	 * and order, connectivity, and weights given by the remaining
	 * arguments.
	 *
	 * @param trainingSet the training instances
	 * @param orders an array of increasing non-negative numbers giving
	 * the orders of the features for this CRF. The largest number
	 * <em>n</em> is the Markov order of the CRF. States are
	 * <em>n</em>-tuples of output labels. Each of the other numbers
	 * <em>k</em> in <code>orders</code> represents a weight set shared
	 * by all destination states whose last (most recent) <em>k</em>
	 * labels agree. If <code>orders</code> is <code>null</code>, an
	 * order-0 CRF is built.
	 * @param defaults If non-null, it must be the same length as
	 * <code>orders</code>, with <code>true</code> positions indicating
	 * that the weight set for the corresponding order contains only the
	 * weight for a default feature; otherwise, the weight set has
	 * weights for all features built from input predicates.
	 * @param start The label that represents the context of the start of
	 * a sequence. It may be also used for sequence labels.  If no label of
	 * this name exists, one will be added. Connection wills be added between
	 * the start label and all other labels, even if <tt>fullyConnected</tt> is
	 * <tt>false</tt>.  This argument may be null, in which case no special
	 * start state is added.
	 * @param forbidden If non-null, specifies what pairs of successive
	 * labels are not allowed, both for constructing <em>n</em>order
	 * states or for transitions. A label pair (<em>u</em>,<em>v</em>)
	 * is not allowed if <em>u</em> + "," + <em>v</em> matches
	 * <code>forbidden</code>.
	 * @param allowed If non-null, specifies what pairs of successive
	 * labels are allowed, both for constructing <em>n</em>order
	 * states or for transitions. A label pair (<em>u</em>,<em>v</em>)
	 * is allowed only if <em>u</em> + "," + <em>v</em> matches
	 * <code>allowed</code>.
	 * @param fullyConnected Whether to include all allowed transitions,
	 * even those not occurring in <code>trainingSet</code>,
	 * @return The name of the start state.
	 * 
	 */
	public String addOrderNStates(InstanceList trainingSet, int[] orders,
			boolean[] defaults, String start,
			Pattern forbidden, Pattern allowed,
			boolean fullyConnected)
	{
		boolean[][] connections = null;
		if (start != null)
			outputAlphabet.lookupIndex (start);
		if (!fullyConnected)
			connections = labelConnectionsIn (trainingSet, start);
		int order = -1;
		if (defaults != null && defaults.length != orders.length)
			throw new IllegalArgumentException("Defaults must be null or match orders");
		if (orders == null)
			order = 0;
		else
		{
			for (int i = 0; i < orders.length; i++)
				if (orders[i] <= order)
					throw new IllegalArgumentException("Orders must be non-negative and in ascending order");
				else 
					order = orders[i];
			if (order < 0) order = 0;
		}
		if (order > 0)
		{
			int[] historyIndexes = new int[order];
			String[] history = new String[order];
			String label0 = (String)outputAlphabet.lookupObject(0);
			for (int i = 0; i < order; i++)
				history[i] = label0;
			int numLabels = outputAlphabet.size();
			while (historyIndexes[0] < numLabels)
			{
				logger.info("Preparing " + concatLabels(history));
				if (allowedHistory(history, forbidden, allowed))
				{
					String stateName = concatLabels(history);
					int nt = 0;
					String[] destNames = new String[numLabels];
					String[] labelNames = new String[numLabels];
					String[][] weightNames = new String[numLabels][orders.length];
					for (int nextIndex = 0; nextIndex < numLabels; nextIndex++)
					{
						String next = (String)outputAlphabet.lookupObject(nextIndex);
						if (allowedTransition(history[order-1], next, forbidden, allowed)
								&& (fullyConnected ||
										connections[historyIndexes[order-1]][nextIndex]))
						{
							destNames[nt] = nextKGram(history, order, next);
							labelNames[nt] = next;
							for (int i = 0; i < orders.length; i++)
							{
								weightNames[nt][i] = nextKGram(history, orders[i]+1, next);
								if (defaults != null && defaults[i]) {
									int wi = getWeightsIndex (weightNames[nt][i]);
									// Using empty feature selection gives us only the
									// default features
									featureSelections[wi] =
										new FeatureSelection(trainingSet.getDataAlphabet());
								}
							}
							nt++;
						}
					}
					if (nt < numLabels)
					{
						String[] newDestNames = new String[nt];
						String[] newLabelNames = new String[nt];
						String[][] newWeightNames = new String[nt][];
						for (int t = 0; t < nt; t++)
						{
							newDestNames[t] = destNames[t];
							newLabelNames[t] = labelNames[t];
							newWeightNames[t] = weightNames[t];
						}
						destNames = newDestNames;
						labelNames = newLabelNames;
						weightNames = newWeightNames;
					}
					for (int i = 0; i < destNames.length; i++)
					{
						StringBuffer b = new StringBuffer();
						for (int j = 0; j < orders.length; j++)
							b.append(" ").append(weightNames[i][j]);
						logger.info(stateName + "->" + destNames[i] +
								"(" + labelNames[i] + ")" + b.toString());
					}
					addState (stateName, 0.0, 0.0, destNames, labelNames, weightNames);
				}
				for (int o = order-1; o >= 0; o--) 
					if (++historyIndexes[o] < numLabels)
					{
						history[o] = (String)outputAlphabet.lookupObject(historyIndexes[o]);
						break;
					} else if (o > 0)
					{
						historyIndexes[o] = 0;
						history[o] = label0;
					}
			}
			for (int i = 0; i < order; i++)
				history[i] = start;
			return concatLabels(history);
		}
		else
		{
			String[] stateNames = new String[outputAlphabet.size()];
			for (int s = 0; s < outputAlphabet.size(); s++)
				stateNames[s] = (String)outputAlphabet.lookupObject(s);
			for (int s = 0; s < outputAlphabet.size(); s++)
				addState(stateNames[s], 0.0, 0.0, stateNames, stateNames, stateNames);
			return start;
		}
	}

	public State getState (String name)
	{
		return (State) name2state.get(name);
	}

	public void setWeights (int weightsIndex, SparseVector transitionWeights)
	{
		weightsStructureChanged();
		if (weightsIndex >= parameters.weights.length || weightsIndex < 0)
			throw new IllegalArgumentException ("weightsIndex "+weightsIndex+" is out of bounds");
		parameters.weights[weightsIndex] = transitionWeights;
	}

	public void setWeights (String weightName, SparseVector transitionWeights)
	{
		setWeights (getWeightsIndex (weightName), transitionWeights);
	}

	public String getWeightsName (int weightIndex)
	{
		return (String) parameters.weightAlphabet.lookupObject (weightIndex);
	}

	public SparseVector getWeights (String weightName)
	{
		return parameters.weights[getWeightsIndex (weightName)];
	}

	public SparseVector getWeights (int weightIndex)
	{
		return parameters.weights[weightIndex];
	}

	public double[] getDefaultWeights () {
		return parameters.defaultWeights;
	}

	public SparseVector[] getWeights () {
		return parameters.weights;
	}

	public void setWeights (SparseVector[] m) {
		weightsStructureChanged();
		parameters.weights = m;
	}

	public void setDefaultWeights (double[] w) {
		weightsStructureChanged();
		parameters.defaultWeights = w;
	}

	public void setDefaultWeight (int widx, double val) {
		weightsValueChanged();
		parameters.defaultWeights[widx] = val; 
	}
	
	
	// Support for making cc.mallet.optimize.Optimizable CRFs
	
	
	
	
	
	

	public boolean isWeightsFrozen (int weightsIndex)
	{
		return parameters.weightsFrozen [weightsIndex];
	}

	/**
	 * Freezes a set of weights to their current values.
	 *  Frozen weights are used for labeling sequences (as in <tt>transduce</tt>),
	 *  but are not be modified by the <tt>train</tt> methods.
	 * @param weightsIndex Index of weight set to freeze.
	 */
	public void freezeWeights (int weightsIndex)
	{
		parameters.weightsFrozen [weightsIndex] = true;
	}

	/**
	 * Freezes a set of weights to their current values.
	 *  Frozen weights are used for labeling sequences (as in <tt>transduce</tt>),
	 *  but are not be modified by the <tt>train</tt> methods.
	 * @param weightsName Name of weight set to freeze.
	 */
	public void freezeWeights (String weightsName)
	{
		int widx = getWeightsIndex (weightsName);
		freezeWeights (widx);
	}

	/**
	 * Unfreezes a set of weights.
	 *  Frozen weights are used for labeling sequences (as in <tt>transduce</tt>),
	 *  but are not be modified by the <tt>train</tt> methods.
	 * @param weightsName Name of weight set to unfreeze.
	 */
	public void unfreezeWeights (String weightsName)
	{
		int widx = getWeightsIndex (weightsName);
		parameters.weightsFrozen[widx] = false;
	}

	public void setFeatureSelection (int weightIdx, FeatureSelection fs)
	{
		featureSelections [weightIdx] = fs;
		weightsStructureChanged(); // Is this necessary? -akm 11/2007
	}

	public void setWeightsDimensionAsIn (InstanceList trainingData) {
		setWeightsDimensionAsIn(trainingData, false);
	}
	
	// gsc: changing this to consider the case when trainingData is a mix of labeled and unlabeled data,
	// and we want to use the unlabeled data as well to set some weights (while using the unsupported trick)
	public void setWeightsDimensionAsIn (InstanceList trainingData, boolean useSomeUnsupportedTrick)
	{
		final BitSet[] weightsPresent;
		int numWeights = 0;
		// The value doesn't actually change, because the "new" parameters will have zero value
		// but the gradient changes because the parameters now have different layout.
		weightsStructureChanged();
		weightsPresent = new BitSet[parameters.weights.length];
		for (int i = 0; i < parameters.weights.length; i++)
			weightsPresent[i] = new BitSet();
		// Put in the weights that are already there
		for (int i = 0; i < parameters.weights.length; i++) 
			for (int j = parameters.weights[i].numLocations()-1; j >= 0; j--)
				weightsPresent[i].set (parameters.weights[i].indexAtLocation(j));
		// Put in the weights in the training set
		for (int i = 0; i < trainingData.size(); i++) {
			Instance instance = trainingData.get(i);
			FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			// trainingData can have unlabeled instances as well
			if (output != null) {
				// Do it for the paths consistent with the labels...
				sumLatticeFactory.newSumLattice (this, input, output, new Transducer.Incrementor() {
					public void incrementTransition (Transducer.TransitionIterator ti, double count) {
						State source = (CRF.State)ti.getSourceState();
						FeatureVector input = (FeatureVector)ti.getInput();
						int index = ti.getIndex();
						int nwi = source.weightsIndices[index].length;
						for (int wi = 0; wi < nwi; wi++) {
							int weightsIndex = source.weightsIndices[index][wi];
							for (int i = 0; i < input.numLocations(); i++) {
								int featureIndex = input.indexAtLocation(i);
								if ((globalFeatureSelection == null || globalFeatureSelection.contains(featureIndex))
										&& (featureSelections == null
												|| featureSelections[weightsIndex] == null
												|| featureSelections[weightsIndex].contains(featureIndex)))
									weightsPresent[weightsIndex].set (featureIndex);
							}
						}
					}
					public void incrementInitialState (Transducer.State s, double count) {	}
					public void incrementFinalState (Transducer.State s, double count) {	}
				});
			}
			// ...and also do it for the paths selected by the current model (so we will get some negative weights)
			if (useSomeUnsupportedTrick && this.getParametersAbsNorm() > 0) {
				if (i == 0)
					logger.info ("CRF: Incremental training detected.  Adding weights for some unsupported features...");
				// (do this once some training is done)
				sumLatticeFactory.newSumLattice (this, input, null, new Transducer.Incrementor() {
					public void incrementTransition (Transducer.TransitionIterator ti, double count) {
						if (count < 0.2) // Only create features for transitions with probability above 0.2 
							return;  // This 0.2 is somewhat arbitrary -akm
						State source = (CRF.State)ti.getSourceState();
						FeatureVector input = (FeatureVector)ti.getInput();
						int index = ti.getIndex();
						int nwi = source.weightsIndices[index].length;
						for (int wi = 0; wi < nwi; wi++) {
							int weightsIndex = source.weightsIndices[index][wi];
							for (int i = 0; i < input.numLocations(); i++) {
								int featureIndex = input.indexAtLocation(i);
								if ((globalFeatureSelection == null || globalFeatureSelection.contains(featureIndex))
										&& (featureSelections == null
												|| featureSelections[weightsIndex] == null
												|| featureSelections[weightsIndex].contains(featureIndex)))
									weightsPresent[weightsIndex].set (featureIndex);
							}
						}
					}
					public void incrementInitialState (Transducer.State s, double count) {	}
					public void incrementFinalState (Transducer.State s, double count) {	}
				});
			}
		}
		SparseVector[] newWeights = new SparseVector[parameters.weights.length];
		for (int i = 0; i < parameters.weights.length; i++) {
			int numLocations = weightsPresent[i].cardinality ();
			logger.info ("CRF weights["+parameters.weightAlphabet.lookupObject(i)+"] num features = "+numLocations);
			int[] indices = new int[numLocations];
			for (int j = 0; j < numLocations; j++) {
				indices[j] = weightsPresent[i].nextSetBit (j == 0 ? 0 : indices[j-1]+1);
				//System.out.println ("CRF4 has index "+indices[j]);
			}
			newWeights[i] = new IndexedSparseVector (indices, new double[numLocations],
					numLocations, numLocations, false, false, false);
			newWeights[i].plusEqualsSparse (parameters.weights[i]);  // Put in the previous weights
			numWeights += (numLocations + 1);
		}
		logger.info("Number of weights = "+numWeights);
		parameters.weights = newWeights;
	}

	public void setWeightsDimensionDensely ()
	{
		weightsStructureChanged();
		SparseVector[] newWeights = new SparseVector [parameters.weights.length];
		int max = inputAlphabet.size();
		int numWeights = 0;
		logger.info ("CRF using dense weights, num input features = "+max);
		for (int i = 0; i < parameters.weights.length; i++) {
			int nfeatures;
			if (featureSelections[i] == null) {
				nfeatures = max;
				newWeights [i] = new SparseVector (null, new double [max],
						max, max, false, false, false);
			} else {
				// Respect the featureSelection
				FeatureSelection fs = featureSelections[i];
				nfeatures = fs.getBitSet ().cardinality ();
				int[] idxs = new int [nfeatures];
				int j = 0, thisIdx = -1;
				while ((thisIdx = fs.nextSelectedIndex (thisIdx + 1)) >= 0) {
					idxs[j++] = thisIdx;
				}
				newWeights[i] = new SparseVector (idxs, new double [nfeatures], nfeatures, nfeatures, false, false, false);
			}
			newWeights [i].plusEqualsSparse (parameters.weights [i]);
			numWeights += (nfeatures + 1);
		}
		logger.info("Number of weights = "+numWeights);
		parameters.weights = newWeights;
	}

	
	
	// Create a new weight Vector if weightName is new.
	public int getWeightsIndex (String weightName)
	{
		int wi = parameters.weightAlphabet.lookupIndex (weightName);
		if (wi == -1)
			throw new IllegalArgumentException ("Alphabet frozen, and no weight with name "+ weightName);
		if (parameters.weights == null) {
			assert (wi == 0);
			parameters.weights = new SparseVector[1];
			parameters.defaultWeights = new double[1];
			featureSelections = new FeatureSelection[1];
			parameters.weightsFrozen = new boolean [1];
			// Use initial capacity of 8
			parameters.weights[0] = new IndexedSparseVector ();
			parameters.defaultWeights[0] = 0;
			featureSelections[0] = null;
			weightsStructureChanged();
		} else if (wi == parameters.weights.length) {
			SparseVector[] newWeights = new SparseVector[parameters.weights.length+1];
			double[] newDefaultWeights = new double[parameters.weights.length+1];
			FeatureSelection[] newFeatureSelections = new FeatureSelection[parameters.weights.length+1];
			for (int i = 0; i < parameters.weights.length; i++) {
				newWeights[i] = parameters.weights[i];
				newDefaultWeights[i] = parameters.defaultWeights[i];
				newFeatureSelections[i] = featureSelections[i];
			}
			newWeights[wi] = new IndexedSparseVector ();
			newDefaultWeights[wi] = 0;
			newFeatureSelections[wi] = null;
			parameters.weights = newWeights;
			parameters.defaultWeights = newDefaultWeights;
			featureSelections = newFeatureSelections;
			parameters.weightsFrozen = ArrayUtils.append (parameters.weightsFrozen, false);
			weightsStructureChanged();
		}
		//setTrainable (false);
		return wi;
	}
	
	private void assertWeightsLength ()
	{
		if (parameters.weights != null) {
			assert parameters.defaultWeights != null;
			assert featureSelections != null;
			assert parameters.weightsFrozen != null;

			int n = parameters.weights.length;
			assert parameters.defaultWeights.length == n;
			assert featureSelections.length == n;
			assert parameters.weightsFrozen.length == n;
		}
	}

	public int numStates () { return states.size(); }

	public Transducer.State getState (int index) {
		return (Transducer.State) states.get(index); }

	public Iterator initialStateIterator () {
		return initialStates.iterator (); }

	public boolean isTrainable () { return true; }

	// gsc: accessor methods
	public int getWeightsValueChangeStamp() {
		return weightsValueChangeStamp;
	}
	
	public Factors getParameters ()
	{
		return parameters;
	}
	// gsc

	public double getParametersAbsNorm ()
	{
		double ret = 0;
		for (int i = 0; i < numStates(); i++) {
			ret += Math.abs (parameters.initialWeights[i]);
			ret += Math.abs (parameters.finalWeights[i]);
		}
		for (int i = 0; i < parameters.weights.length; i++) {
			ret += Math.abs (parameters.defaultWeights[i]);
			ret += parameters.weights[i].absNorm();
		}
		return ret;
	}

	/** Only sets the parameter from the first group of parameters. */
	public void setParameter (int sourceStateIndex, int destStateIndex, int featureIndex, double value)
	{
		weightsValueChanged();
		State source = (State)getState(sourceStateIndex);
		State dest = (State) getState(destStateIndex);
		int rowIndex;
		for (rowIndex = 0; rowIndex < source.destinationNames.length; rowIndex++)
			if (source.destinationNames[rowIndex].equals (dest.name))
				break;
		if (rowIndex == source.destinationNames.length)
			throw new IllegalArgumentException ("No transtition from state "+sourceStateIndex+" to state "+destStateIndex+".");
		int weightsIndex = source.weightsIndices[rowIndex][0];
		if (featureIndex < 0)
			parameters.defaultWeights[weightsIndex] = value;
		else {
			parameters.weights[weightsIndex].setValue (featureIndex, value);
		}
	}

	/** Only gets the parameter from the first group of parameters. */
	public double getParameter (int sourceStateIndex, int destStateIndex, int featureIndex)
	{
		State source = (State)getState(sourceStateIndex);
		State dest = (State) getState(destStateIndex);
		int rowIndex;
		for (rowIndex = 0; rowIndex < source.destinationNames.length; rowIndex++)
			if (source.destinationNames[rowIndex].equals (dest.name))
				break;
		if (rowIndex == source.destinationNames.length)
			throw new IllegalArgumentException ("No transtition from state "+sourceStateIndex+" to state "+destStateIndex+".");
		int weightsIndex = source.weightsIndices[rowIndex][0];
		if (featureIndex < 0)
			return parameters.defaultWeights[weightsIndex];
		else
			return parameters.weights[weightsIndex].value (featureIndex);
	}
	
	public int getNumParameters () {
		if (cachedNumParametersStamp != weightsStructureChangeStamp) {
			this.numParameters = 2 * this.numStates() + this.parameters.defaultWeights.length;
			for (int i = 0; i < parameters.weights.length; i++)
				numParameters += parameters.weights[i].numLocations();
		}
		return this.numParameters;
	}



	/** This method is deprecated.  But I'm keeping it here as a reminder that I need to do something about this induceFeaturesFor() business. */
	@Deprecated
	public Sequence[] predict (InstanceList testing) {
		testing.setFeatureSelection(this.globalFeatureSelection);
		for (int i = 0; i < featureInducers.size(); i++) {
			FeatureInducer klfi = (FeatureInducer)featureInducers.get(i);
			klfi.induceFeaturesFor (testing, false, false);
		}
		Sequence[] ret = new Sequence[testing.size()];
		for (int i = 0; i < testing.size(); i++) {
			Instance instance = testing.get(i);
			Sequence input = (Sequence) instance.getData();
			Sequence trueOutput = (Sequence) instance.getTarget();
			assert (input.size() == trueOutput.size());
			Sequence predOutput = new MaxLatticeDefault(this, input).bestOutputSequence();
			assert (predOutput.size() == trueOutput.size());
			ret[i] = predOutput;
		}
		return ret;
	}


	/** This method is deprecated. */
	@Deprecated
	public void evaluate (TransducerEvaluator eval, InstanceList testing) {
		throw new IllegalStateException ("This method is no longer usable.  Use CRF.induceFeaturesFor() instead.");
		/*
		testing.setFeatureSelection(this.globalFeatureSelection);
		for (int i = 0; i < featureInducers.size(); i++) {
			FeatureInducer klfi = (FeatureInducer)featureInducers.get(i);
			klfi.induceFeaturesFor (testing, false, false);
		}
		eval.evaluate (this, true, 0, true, 0.0, null, null, testing);
		*/
	}
	
	/** When the CRF has done feature induction, these new feature conjunctions must be 
	 * created in the test or validation data in order for them to take effect. */
	public void induceFeaturesFor (InstanceList instances) {
		instances.setFeatureSelection(this.globalFeatureSelection);
		for (int i = 0; i < featureInducers.size(); i++) {
			FeatureInducer klfi = (FeatureInducer)featureInducers.get(i);
			klfi.induceFeaturesFor (instances, false, false);
		}
	}

	
	// TODO Put support to Optimizable here, including getValue(InstanceList)??

	public void print ()
	{
		print (new PrintWriter (new OutputStreamWriter (System.out), true));
	}

	// yyy
	public void print (PrintWriter out)
	{
		out.println ("*** CRF STATES ***");
		for (int i = 0; i < numStates (); i++) {
			State s = (State) getState (i);
			out.print ("STATE NAME=\"");
			out.print (s.name); out.print ("\" ("); out.print (s.destinations.length); out.print (" outgoing transitions)\n");
			out.print ("  "); out.print ("initialWeight = "); out.print (parameters.initialWeights[i]); out.print ('\n');
			out.print ("  "); out.print ("finalWeight = "); out.print (parameters.finalWeights[i]); out.print ('\n');
			out.println ("  transitions:");
			for (int j = 0; j < s.destinations.length; j++) {
				out.print ("    "); out.print (s.name); out.print (" -> "); out.println (s.getDestinationState (j).getName ());
				for (int k = 0; k < s.weightsIndices[j].length; k++) {
					out.print ("        WEIGHTS = \"");
					int widx = s.weightsIndices[j][k];
					out.print (parameters.weightAlphabet.lookupObject (widx).toString ());
					out.print ("\"\n");
				}
			}
			out.println ();
		}

		if (parameters.weights == null)
			out.println ("\n\n*** NO WEIGHTS ***");
		else {		
			out.println ("\n\n*** CRF WEIGHTS ***");
			for (int widx = 0; widx < parameters.weights.length; widx++) {
				out.println ("WEIGHTS NAME = " + parameters.weightAlphabet.lookupObject (widx));
				out.print (": <DEFAULT_FEATURE> = "); out.print (parameters.defaultWeights[widx]); out.print ('\n');
				SparseVector transitionWeights = parameters.weights[widx];
				if (transitionWeights.numLocations () == 0)
					continue;
				RankedFeatureVector rfv = new RankedFeatureVector (inputAlphabet, transitionWeights);
				for (int m = 0; m < rfv.numLocations (); m++) {
					double v = rfv.getValueAtRank (m);
					//int index = rfv.indexAtLocation (rfv.getIndexAtRank (m));  // This doesn't make any sense.  How did this ever work?  -akm 12/2007
					int index = rfv.getIndexAtRank (m);
					Object feature = inputAlphabet.lookupObject (index);
					if (v != 0) {
						out.print (": "); out.print (feature); out.print (" = "); out.println (v);
					}
				}
			}
		}

		out.flush ();
	}



	public void write (File f) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
			oos.writeObject(this);
			oos.close();
		}
		catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}


	// gsc: Serialization for CRF class
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (inputAlphabet);
		out.writeObject (outputAlphabet);
		out.writeObject (states);
		out.writeObject (initialStates);
		out.writeObject (name2state);
		out.writeObject (parameters);
		out.writeObject (globalFeatureSelection);		
		out.writeObject (featureSelections);
		out.writeObject (featureInducers);
		out.writeInt (weightsValueChangeStamp);
		out.writeInt (weightsStructureChangeStamp);
		out.writeInt (cachedNumParametersStamp);
		out.writeInt (numParameters);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		inputAlphabet = (Alphabet) in.readObject ();
		outputAlphabet = (Alphabet) in.readObject ();
		states = (ArrayList<State>) in.readObject ();
		initialStates = (ArrayList<State>) in.readObject ();
		name2state = (HashMap) in.readObject ();
		parameters = (Factors) in.readObject ();
		globalFeatureSelection = (FeatureSelection) in.readObject ();		
		featureSelections = (FeatureSelection[]) in.readObject ();
		featureInducers = (ArrayList<FeatureInducer>) in.readObject ();
		weightsValueChangeStamp = in.readInt ();
		weightsStructureChangeStamp = in.readInt ();
		cachedNumParametersStamp = in.readInt ();
		numParameters = in.readInt ();
	}

	
	// Why is this "static"?  Couldn't it be a non-static inner class? (In Transducer also)  -akm 12/2007
	public static class State extends Transducer.State implements Serializable
	{
		// Parameters indexed by destination state, feature index
		String name;
		int index;
		String[] destinationNames;
		State[] destinations;             // N.B. elements are null until getDestinationState(int) is called
		int[][] weightsIndices;								// contains indices into CRF.weights[],
		String[] labels;
		CRF crf;

		// No arg constructor so serialization works

		protected State() {
			super ();
		}

		protected State (String name, int index,
				double initialWeight, double finalWeight,
				String[] destinationNames,
				String[] labelNames,
				String[][] weightNames,
				CRF crf)
		{
			super ();
			assert (destinationNames.length == labelNames.length);
			assert (destinationNames.length == weightNames.length);
			this.name = name;
			this.index = index;
			// Note: setting these parameters here is actually redundant; they were set already in CRF.addState(...)
			// I'm considering removing initialWeight and finalWeight as arguments to this constructor, but need to think more -akm 12/2007
			// If CRF.State were non-static, then this constructor could add the state to the list of states, and put it in the name2state also.
			crf.parameters.initialWeights[index] = initialWeight;
			crf.parameters.finalWeights[index] = finalWeight;
			this.destinationNames = new String[destinationNames.length];
			this.destinations = new State[labelNames.length];
			this.weightsIndices = new int[labelNames.length][];
			this.labels = new String[labelNames.length];
			this.crf = crf;
			for (int i = 0; i < labelNames.length; i++) {
				// Make sure this label appears in our output Alphabet
				crf.outputAlphabet.lookupIndex (labelNames[i]);
				this.destinationNames[i] = destinationNames[i];
				this.labels[i] = labelNames[i];
				this.weightsIndices[i] = new int[weightNames[i].length];
				for (int j = 0; j < weightNames[i].length; j++)
					this.weightsIndices[i][j] = crf.getWeightsIndex (weightNames[i][j]);
			}
			crf.weightsStructureChanged();
		}

		public Transducer getTransducer () { return crf; }
		public double getInitialWeight () { return crf.parameters.initialWeights[index]; }
		public void setInitialWeight (double c) { crf.parameters.initialWeights[index]= c; }
		public double getFinalWeight () { return crf.parameters.finalWeights[index]; }
		public void setFinalWeight (double c) { crf.parameters.finalWeights[index] = c; }


		public void print ()
		{
			System.out.println ("State #"+index+" \""+name+"\"");
			System.out.println ("initialWeight="+crf.parameters.initialWeights[index]+", finalWeight="+crf.parameters.finalWeights[index]);
			System.out.println ("#destinations="+destinations.length);
			for (int i = 0; i < destinations.length; i++)
				System.out.println ("-> "+destinationNames[i]);
		}

		public int numDestinations () { return destinations.length;}

		public String[] getWeightNames (int index) {
			int[] indices = this.weightsIndices[index];
			String[] ret = new String[indices.length];
			for (int i=0; i < ret.length; i++)
				ret[i] = crf.parameters.weightAlphabet.lookupObject(indices[i]).toString();
			return ret;
		}

		public void addWeight (int didx, String weightName) {
			int widx = crf.getWeightsIndex (weightName);
			weightsIndices[didx] = ArrayUtils.append (weightsIndices[didx], widx);
		}

		public String getLabelName (int index) {
			return labels [index];
		}

		public State getDestinationState (int index)
		{
			State ret;
			if ((ret = destinations[index]) == null) {
				ret = destinations[index] = (State) crf.name2state.get (destinationNames[index]);
				if (ret == null)
					throw new IllegalArgumentException ("this.name="+this.name+" index="+index+" destinationNames[index]="+destinationNames[index]+" name2state.size()="+ crf.name2state.size());
			}
			return ret;
		}


		public Transducer.TransitionIterator transitionIterator (Sequence inputSequence, int inputPosition,
				Sequence outputSequence, int outputPosition)
		{
			if (inputPosition < 0 || outputPosition < 0)
				throw new UnsupportedOperationException ("Epsilon transitions not implemented.");
			if (inputSequence == null)
				throw new UnsupportedOperationException ("CRFs are not generative models; must have an input sequence.");
			return new TransitionIterator (this, (FeatureVectorSequence)inputSequence, inputPosition,
					(outputSequence == null ? null : (String)outputSequence.get(outputPosition)), crf);
		}

		public Transducer.TransitionIterator transitionIterator (FeatureVector fv, String output)
		{
			return new TransitionIterator (this, fv, output, crf);
		}

		public String getName () { return name; }

		// "final" to make it efficient inside incrementTransition
		public final int getIndex () { return index; }

		// Serialization
		// For  class State

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;

		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject(name);
			out.writeInt(index);
			out.writeObject(destinationNames);
			out.writeObject(destinations);
			out.writeObject(weightsIndices);
			out.writeObject(labels);
			out.writeObject(crf);
		}

		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			name = (String) in.readObject();
			index = in.readInt();
			destinationNames = (String[]) in.readObject();
			destinations = (CRF.State[]) in.readObject();
			weightsIndices = (int[][]) in.readObject();
			labels = (String[]) in.readObject();
			crf = (CRF) in.readObject();
		}


	}


	protected static class TransitionIterator extends Transducer.TransitionIterator implements Serializable
	{
		State source;
		int index, nextIndex;
		protected double[] weights;
		FeatureVector input;
		CRF crf;

		public TransitionIterator (State source,
				FeatureVectorSequence inputSeq,
				int inputPosition,
				String output, CRF crf)
		{
			this (source, (FeatureVector)inputSeq.get(inputPosition), output, crf);
		}

		protected TransitionIterator (State source,
				FeatureVector fv,
				String output, CRF crf)
		{
			this.source = source;
			this.crf = crf;
			this.input = fv;
			this.weights = new double[source.destinations.length];
			int nwi, swi;
			for (int transIndex = 0; transIndex < source.destinations.length; transIndex++) {
				// xxx Or do we want output.equals(...) here?
						if (output == null || output.equals(source.labels[transIndex])) {
							// Here is the dot product of the feature weights with the lambda weights
							// for one transition
							weights[transIndex] = 0;
							nwi = source.weightsIndices[transIndex].length;
							for (int wi = 0; wi < nwi; wi++) {
								swi = source.weightsIndices[transIndex][wi];
								weights[transIndex] += (crf.parameters.weights[swi].dotProduct (fv)
										// include with implicit weight 1.0 the default feature
										+ crf.parameters.defaultWeights[swi]);
							}
							assert (!Double.isNaN(weights[transIndex]));
							assert (weights[transIndex] != Double.POSITIVE_INFINITY);
						}
						else
							weights[transIndex] = IMPOSSIBLE_WEIGHT;
			}
			// Prepare nextIndex, pointing at the next non-impossible transition
			nextIndex = 0;
			while (nextIndex < source.destinations.length && weights[nextIndex] == IMPOSSIBLE_WEIGHT)
				nextIndex++;
		}

		public boolean hasNext ()	{ return nextIndex < source.destinations.length; }

		public Transducer.State nextState ()
		{
			assert (nextIndex < source.destinations.length);
			index = nextIndex;
			nextIndex++;
			while (nextIndex < source.destinations.length && weights[nextIndex] == IMPOSSIBLE_WEIGHT)
				nextIndex++;
			return source.getDestinationState (index);
		}

		// These "final"s are just to try to make this more efficient.  Perhaps some of them will have to go away
		public final int getIndex () { return index; }
		public final Object getInput () { return input; }
		public final Object getOutput () { return source.labels[index]; }
		public final double getWeight () { return weights[index]; }
		public final Transducer.State getSourceState () { return source; }
		public final Transducer.State getDestinationState () { return source.getDestinationState (index);	}

		// Serialization
		// TransitionIterator

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private static final int NULL_INTEGER = -1;

		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject (source);
			out.writeInt (index);
			out.writeInt (nextIndex);
			out.writeObject(weights);
			out.writeObject (input);
			out.writeObject(crf);
		}

		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			source = (State) in.readObject();
			index = in.readInt ();
			nextIndex = in.readInt ();
			weights = (double[]) in.readObject();
			input = (FeatureVector) in.readObject();
			crf = (CRF) in.readObject();
		}


		public String describeTransition (double cutoff)
		{
			DecimalFormat f = new DecimalFormat ("0.###");
			StringBuffer buf = new StringBuffer ();
			buf.append ("Value: " + f.format (-getWeight ()) + " <br />\n");

			try {
				int[] theseWeights = source.weightsIndices[index];
				for (int i = 0; i < theseWeights.length; i++) {
					int wi = theseWeights[i];
					SparseVector w = crf.parameters.weights[wi];

					buf.append ("WEIGHTS <br />\n" + crf.parameters.weightAlphabet.lookupObject (wi) + "<br />\n");
					buf.append ("  d.p. = "+f.format (w.dotProduct (input))+"<br />\n");

					double[] vals = new double[input.numLocations ()];
					double[] absVals = new double[input.numLocations ()];
					for (int k = 0; k < vals.length; k++) {
						int index = input.indexAtLocation (k);
						vals[k] = w.value (index) * input.value (index);
						absVals[k] = Math.abs (vals[k]);
					}

					buf.append ("DEFAULT " + f.format (crf.parameters.defaultWeights[wi]) + "<br />\n");
					RankedFeatureVector rfv = new RankedFeatureVector (crf.inputAlphabet, input.getIndices (), absVals);
					for (int rank = 0; rank < absVals.length; rank++) {
						int fidx = rfv.getIndexAtRank (rank);
						Object fname = crf.inputAlphabet.lookupObject (input.indexAtLocation (fidx));
						if (absVals[fidx] < cutoff) break; // Break looping over features
						if (vals[fidx] != 0) {
							buf.append (fname + " " + f.format (vals[fidx]) + "<br />\n");
						}
					}
				}
			} catch (Exception e) {
				System.err.println ("Error writing transition descriptions.");
				e.printStackTrace ();
				buf.append ("ERROR WHILE WRITING OUTPUT...\n");
			}

			return buf.toString ();
		}
	}
}


