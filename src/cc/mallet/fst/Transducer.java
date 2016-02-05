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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cc.mallet.types.SequencePairAlignment;

import cc.mallet.pipe.Pipe;

import cc.mallet.util.MalletLogger;
import cc.mallet.util.Sequences;

/**
 * A base class for all sequence models, analogous to {@link classify.Classifier}.
 */
public abstract class Transducer implements Serializable
{
  // Variable name key:
  // "ip" = "input position"
  // "op" = "output position"

	private static Logger logger = MalletLogger.getLogger(Transducer.class.getName());
	//public static final double ZERO_COST = 0;
	//public static final double INFINITE_COST = Double.POSITIVE_INFINITY;
	public static final double CERTAIN_WEIGHT = Double.POSITIVE_INFINITY; // TODO Remove this because it should never be used; results in NaN's
	public static final double IMPOSSIBLE_WEIGHT = Double.NEGATIVE_INFINITY;

	// A factory instance from which we can ask for a newSumLattice(...)
	SumLatticeFactory sumLatticeFactory;
	// A factory instance from which we can ask for a newMaxLattice(...)
	MaxLatticeFactory maxLatticeFactory;
	
	/** A pipe that should produce a Sequence in the "data" slot, (and possibly one in the "target" slot also */
	protected Pipe inputPipe;
	/** A pipe that should expect the Transducer's output sequence in the "target" slot,
			and should produce something printable in the "source" slot that
			indicates the results of transduction. */
	protected Pipe outputPipe;
	
	
	/**
	 * Initializes default sum-product and max-product inference engines. 
	 */
	public Transducer ()
	{
		sumLatticeFactory = new SumLatticeDefault.Factory();
		maxLatticeFactory = new MaxLatticeDefault.Factory();
	}

	public Transducer (Pipe inputPipe, Pipe outputPipe)
	{
		this();
		this.inputPipe = inputPipe;
		this.outputPipe = outputPipe;
	}

	public Pipe getInputPipe () { return inputPipe; }
	public Pipe getOutputPipe () { return outputPipe; }
	
	public void setSumLatticeFactory (SumLatticeFactory fbf) { sumLatticeFactory = fbf; } 
	public void setMaxLatticeFactory (MaxLatticeFactory vf) { maxLatticeFactory = vf; }
	public SumLatticeFactory getSumLatticeFactory () { return sumLatticeFactory; }
	public MaxLatticeFactory getMaxLatticeFactory () { return maxLatticeFactory; }


	/** Take input sequence from instance.data and put the output sequence in instance.target. 
	 *  Like transduce(Instance), but put best output sequence into instance.target rather than instance.data. */
	// TODO Consider a different method name. 
	public Instance label (Instance instance)
	{
		if (inputPipe != null)
			instance = inputPipe.instanceFrom(instance);
		// TODO Use MaxLatticeFactory instead of hardcoding 
		instance.setTarget(new MaxLatticeDefault(this, (Sequence)instance.getData()).bestOutputSequence());
		if (outputPipe != null)
			instance = outputPipe.instanceFrom(instance);
		return instance;
	}

	/** Take input sequence from instance.data and put the output sequence in instance.data. */
	public Instance transduce (Instance instance)
	{
		if (inputPipe != null)
			instance = inputPipe.instanceFrom(instance);
		// TODO Use MaxLatticeFactory instead of hardcoding 
		instance.setData(new MaxLatticeDefault(this, (Sequence)instance.getData()).bestOutputSequence());
		if (outputPipe != null)
			instance = outputPipe.instanceFrom(instance);
		return instance;
	}

	/**
	 * Converts the given sequence into another sequence according to this transducer.
	 *  For exmaple, probabilistic transducer may do something like Viterbi here.
	 *  Subclasses of transducer may specify that they only accept special kinds of sequence.
	 * @param input Input sequence
	 * @return Sequence output by this transudcer 
	 */
	public Sequence transduce (Sequence input)
	{
		return maxLatticeFactory.newMaxLattice(this, (Sequence)input).bestOutputSequence();
	}

	public abstract int numStates ();
	public abstract State getState (int index);

	// Note that this method is allowed to return states with impossible (-infinity) initialWeights.
	public abstract Iterator initialStateIterator ();

	/** Some transducers are "generative", meaning that you can get a
	 sequence out of them without giving them an input sequence.  In
	 this case State.transitionIterator() should return all available
	 transitions, but attempts to obtain the input and weight fields may
	 throw an exception. */
	 // TODO Why could obtaining "weight" be a problem???
	public boolean canIterateAllTransitions () { return false; }

	/** If true, this is a "generative transducer".  In this case
	 State.transitionIterator() should return transitions that have
	 valid input and cost fields.  True returned here should imply
	 that canIterateAllTransitions() is true. */
	public boolean isGenerative () { return false; }

	/**
	 * Runs inference across all the instances and returns the average token
	 * accuracy.
	 */
	public double averageTokenAccuracy (InstanceList ilist)
	{
		double accuracy = 0;
		for (int i = 0; i < ilist.size(); i++) {
			Instance instance = ilist.get(i);
			Sequence input = (Sequence) instance.getData();
			Sequence output = (Sequence) instance.getTarget();
			assert (input.size() == output.size());
			Sequence predicted = maxLatticeFactory.newMaxLattice(this, input).bestOutputSequence();
			double pathAccuracy = Sequences.elementwiseAccuracy(output, predicted); 
			accuracy += pathAccuracy;
			logger.fine ("Transducer path accuracy = "+pathAccuracy);
		}
		return accuracy/ilist.size();
	}

	// Treat the costs as if they are -log(probabilies); we will
	// normalize them if necessary
	public SequencePairAlignment generatePath ()
	{
		if (isGenerative() == false)
			throw new IllegalStateException ("Transducer is not generative.");
		ArrayList initialStates = new ArrayList ();
		Iterator iter = initialStateIterator ();
		while (iter.hasNext()) { initialStates.add (iter.next()); }
		// xxx Not yet finished.
		throw new UnsupportedOperationException ();
	}

	/**
	 * Returns the index of the input state name, returns -1 if name not found.
	 */
	public int stateIndexOfString (String s)
	{
		for (int i = 0; i < this.numStates(); i++) {
			String state = this.getState (i).getName();
			if (state.equals (s))
				return i;
		}
		return -1;		
	}

	private void printStates () {
		for (int i = 0; i < this.numStates(); i++) 
			logger.fine (i + ":" + this.getState (i).getName());
	}

	public void print () {
		logger.fine ("Transducer "+this);
		printStates();
	}

	// Serialization of Transducer

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;

	// gsc: fixed serialization, writing/reading *LatticeFactory objects
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(inputPipe);
		out.writeObject(outputPipe);
		out.writeObject(sumLatticeFactory);
		out.writeObject(maxLatticeFactory);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		inputPipe = (Pipe) in.readObject();
		outputPipe = (Pipe) in.readObject();
		sumLatticeFactory = (SumLatticeFactory) in.readObject();
		maxLatticeFactory = (MaxLatticeFactory) in.readObject();
	}

	/**
	 * An abstract class used to represent the states of the transducer.
	 */
	public abstract static class State implements Serializable
	{
		public abstract String getName();
		public abstract int getIndex ();
		public abstract double getInitialWeight ();
		public abstract void setInitialWeight (double c);
		public abstract double getFinalWeight ();
		public abstract void setFinalWeight (double c);
		public abstract Transducer getTransducer ();

		// Pass negative positions for a sequence to request "epsilon
		// transitions" for either input or output.  (-position-1) should be
		// the position in the sequence after which we are trying to insert
		// the espilon transition.
		public abstract TransitionIterator transitionIterator
		(Sequence input,	int inputPosition, Sequence output, int outputPosition);


		// Pass negative input position for a sequence to request "epsilon
		// transitions".  (-position-1) should be the position in the
		// sequence after which we are trying to insert the espilon transition.
		public TransitionIterator transitionIterator (Sequence input, int inputPosition) {
			return transitionIterator (input, inputPosition, null, 0);
		}

		// For generative transducers:
		// Return all possible transitions, independent of input
		public TransitionIterator transitionIterator () {
			return transitionIterator (null, 0, null, 0);
		}

		// Serialization
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
		}
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
		}
	}
	
	
	/** Methods to be called by inference methods to indicate partial counts of sufficient statistics.
	 * That is, how much probability mass is falling on a transition, or in an initial state or a final state. */
	public interface Incrementor {
		public void incrementTransition (TransitionIterator ti, double count);
		public void incrementInitialState (State s, double count);
		public void incrementFinalState (State s, double count);
	}

	/**
	 * An abstract class to iterate over the states of the transducer. 
	 */
	public abstract static class TransitionIterator implements Iterator<State>, Serializable
	{
		public abstract boolean hasNext ();
		@Deprecated // What is this method for?  I've forgotten. -akm 11/2007
		public int numberNext() { return -1;}
		@Deprecated
		public abstract State nextState ();	// returns the destination state
		public State next () { return nextState(); }
		public void remove () {
			throw new UnsupportedOperationException (); }
		/** An implementation-specific index for this transition object,
		 		can be used to index into arrays of per-transition parameters. */
		public abstract int getIndex();
		/** The input symbol or object appearing on this transition. */
		public abstract Object getInput ();
		/** The output symbol or object appearing on this transition. */
		public abstract Object getOutput ();
		/** The weight (between infinity and -infinity) associated with taking this transition with this input/output. */
		public abstract double getWeight ();
		/** The state we were in before taking this transition. */
		public abstract State getSourceState ();
		/** The state we are in after taking this transition. */
		public abstract State getDestinationState ();
		/** The number of input positions that this transition consumes. 
		 * This allows for transition that consume variable amounts of the sequences. */
		public int getInputPositionIncrement () { return 1; }
		/** The number of output positions that this transition produces. 
		 * This allows for transition that consume variable amounts of the sequences. */
		public int getOutputPositionIncrement () { return 1; }
		public Transducer getTransducer () { return getSourceState().getTransducer(); }
		// I hate that I need this; there's really no other way -cas
		public String describeTransition (double cutoff) { return ""; }

		// Serialization
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
		}
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			@SuppressWarnings("unused")
			int version = in.readInt ();
		}

	}


	/* sumLogProb()

		 We need to be able to sum probabilities that are represented as
		 weights (which are log(probabilities)).  Naively, we would just
		 convert them into probabilities, sum them, and then convert them
		 back into weights.  This would be:

		 double sumLogProb (double a, double b) {
		   return Math.log (Math.exp(a) + Math.exp(b));
		 }

		 But this would fail when a or b is too negative.  The machine would have the
		 resolution to represent the final weight, but not the resolution to
		 represent the intermediate exponentiated weights, and we
		 would get infinity as our answer.

		 What we want is a method for getting the sum by exponentiating a
		 number that is not too large.  We can do this with the following.
		 Starting with the equation above, then:

		 sumProb = log (exp(a) + exp(b))
		 exp(sumProb) = exp(a) + exp(b)
		 exp(sumProb)/exp(a) = 1 + exp(b)/exp(a)
		 exp(sumProb-a) = 1 + exp(b-a)
		 sumProb - a = log(1 + exp(b-a))
		 sumProb = a + log(1 + exp(b-a))
		 
		 We want to make sure that "b-a" is negative or a small positive
		 number.  We can assure this by noticing that we could have
		 equivalently derived

		 sumProb = b + log (1 + exp(a-b)),

		 and we can simply select among the two alternative equations the
		 one that would have the smallest (or most negative) exponent.
	 */

	public static double no_longer_needed_sumNegLogProb (double a, double b)
	{
		if (a == Double.POSITIVE_INFINITY && b == Double.POSITIVE_INFINITY)
			return Double.POSITIVE_INFINITY;
		else if (a > b)
			return b - Math.log (1 + Math.exp(b-a));
		else
			return a - Math.log (1 + Math.exp(a-b));
	}

	/**
	 * Returns <tt>Math.log(Math.exp(a) + Math.exp(b))</tt>.
	 * <p>
	 * <tt>a, b</tt> represent weights.
	 */
	public static double sumLogProb (double a, double b)
	{
		if (a == Double.NEGATIVE_INFINITY) {
			if (b == Double.NEGATIVE_INFINITY)
				return Double.NEGATIVE_INFINITY;
      return b;
		}
		else if (b == Double.NEGATIVE_INFINITY)
			return a;
		else if (a > b)
			return a + Math.log (1 + Math.exp(b-a));
		else
			return b + Math.log (1 + Math.exp(a-b));
	}

	public static double less_efficient_sumLogProb (double a, double b)
	{
		if (a == Double.NEGATIVE_INFINITY && b == Double.NEGATIVE_INFINITY)
			return Double.NEGATIVE_INFINITY;
		else if (a > b)
			return a + Math.log (1 + Math.exp(b-a));
		else
			return b + Math.log (1 + Math.exp(a-b));
	}


}
