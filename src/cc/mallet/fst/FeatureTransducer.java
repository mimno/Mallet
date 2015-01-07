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
import java.util.logging.Logger;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Multinomial;
import cc.mallet.types.Sequence;

import cc.mallet.util.MalletLogger;

public class FeatureTransducer extends Transducer
{
	private static Logger logger = MalletLogger.getLogger(FeatureTransducer.class.getName());
	
	// These next two dictionaries may be the same
	Alphabet inputAlphabet;
	Alphabet outputAlphabet;
	ArrayList<State> states = new ArrayList<State> ();
	ArrayList<State> initialStates = new ArrayList<State> ();
	HashMap<String,State> name2state = new HashMap<String,State> ();
	Multinomial.Estimator initialStateCounts;
	Multinomial.Estimator finalStateCounts;
	boolean trainable = false;

	public FeatureTransducer (Alphabet inputAlphabet,
														Alphabet outputAlphabet)
	{
		this.inputAlphabet = inputAlphabet;
		this.outputAlphabet = outputAlphabet;
		// xxx When should these be frozen?
	}

	public FeatureTransducer (Alphabet dictionary)
	{
		this (dictionary, dictionary);
	}

	public FeatureTransducer ()
	{
		this (new Alphabet ());
	}

	public Alphabet getInputAlphabet () { return inputAlphabet; }
	public Alphabet getOutputAlphabet () { return outputAlphabet; }
	
	public void addState (String name, double initialWeight, double finalWeight,
												int[] inputs, int[] outputs, double[] weights,
												String[] destinationNames)
	{
		if (name2state.get(name) != null)
			throw new IllegalArgumentException ("State with name `"+name+"' already exists.");
		State s = new State (name, states.size(), initialWeight, finalWeight,
												 inputs, outputs, weights, destinationNames, this);
		states.add (s);
		if (initialWeight < IMPOSSIBLE_WEIGHT)
			initialStates.add (s);
		name2state.put (name, s);
		setTrainable (false);
	}

	public void addState (String name, double initialWeight, double finalWeight,
												Object[] inputs, Object[] outputs, double[] weights,
												String[] destinationNames)
	{
		this.addState (name, initialWeight, finalWeight,
									 inputAlphabet.lookupIndices (inputs, true),
									 outputAlphabet.lookupIndices (outputs, true),
									 weights, destinationNames);
	}

	public int numStates () { return states.size(); }

	public Transducer.State getState (int index) {
		return states.get(index); }
	
	public Iterator<State> initialStateIterator () { return initialStates.iterator (); }

	public boolean isTrainable () { return trainable; }

	public void setTrainable (boolean f)
	{
		trainable = f;
		if (f) {
			// This wipes away any previous counts we had.
			// It also potentially allocates an esimator of a new size if
			// the number of states has increased.
			initialStateCounts = new Multinomial.LaplaceEstimator (states.size());
			finalStateCounts = new Multinomial.LaplaceEstimator (states.size());
		} else {
			initialStateCounts = null;
			finalStateCounts = null;
		}
		for (int i = 0; i < numStates(); i++)
			((State)getState(i)).setTrainable(f);
	}

	public void reset ()
	{
		if (trainable) {
			initialStateCounts.reset ();
			finalStateCounts.reset ();
			for (int i = 0; i < numStates(); i++)
				((State)getState(i)).reset ();
		}
	}

	public void estimate ()
	{
		if (initialStateCounts == null || finalStateCounts == null)
			throw new IllegalStateException ("This transducer not currently trainable.");
		Multinomial initialStateDistribution = initialStateCounts.estimate ();
		Multinomial finalStateDistribution = finalStateCounts.estimate ();
		for (int i = 0; i < states.size(); i++) {
			State s = states.get (i);
			s.initialWeight = initialStateDistribution.logProbability (i);
			s.finalWeight = finalStateDistribution.logProbability (i);
			s.estimate ();
		}
	}

  // Note that this is a non-static inner class, so we have access to all of
  // FeatureTransducer's instance variables.
	public class State extends Transducer.State
	{
		String name;
		int index;
		double initialWeight, finalWeight;
		Transition[] transitions;
		gnu.trove.map.hash.TIntObjectHashMap input2transitions;
		Multinomial.Estimator transitionCounts;
		FeatureTransducer transducer;

		// Note that you cannot add transitions to a state once it is created.
		protected State (String name, int index, double initialWeight, double finalWeight,
										 int[] inputs, int[] outputs, double[] weights,
										 String[] destinationNames, FeatureTransducer transducer)
		{
			assert (inputs.length == outputs.length
							&& inputs.length == weights.length
							&& inputs.length == destinationNames.length);
			this.transducer = transducer;
			this.name = name;
			this.index = index;
			this.initialWeight = initialWeight;
			this.finalWeight = finalWeight;
			this.transitions = new Transition[inputs.length];
			this.input2transitions = new gnu.trove.map.hash.TIntObjectHashMap ();
			transitionCounts = null;
			for (int i = 0; i < inputs.length; i++) {
				// This constructor places the transtion into this.input2transitions
				transitions[i] = new Transition (inputs[i], outputs[i],
																				 weights[i], this, destinationNames[i]);
				transitions[i].index = i;
			}
		}
		
		public Transducer getTransducer () { return transducer; } 
		public double getInitialWeight () { return initialWeight; }
		public double getFinalWeight () { return finalWeight; }
		public void setInitialWeight (double v) { initialWeight = v; }
		public void setFinalWeight (double v) { finalWeight = v; }

		private void setTrainable (boolean f)
		{
			if (f)
				transitionCounts = new Multinomial.LaplaceEstimator (transitions.length);
			else
				transitionCounts = null;
		}

		// Temporarily here for debugging
		public Multinomial.Estimator getTransitionEstimator()
		{
			return transitionCounts;
		}

		private void reset ()
		{
			if (transitionCounts != null)
				transitionCounts.reset();
		}

		public int getIndex () { return index; }

		public Transducer.TransitionIterator transitionIterator (Sequence input,
																														 int inputPosition,
																														 Sequence output,
																														 int outputPosition)
		{
			if (inputPosition < 0 || outputPosition < 0 || output != null)
				throw new UnsupportedOperationException ("Not yet implemented.");
			if (input == null)
				return transitionIterator ();
      return transitionIterator (input, inputPosition);
		}

		public Transducer.TransitionIterator transitionIterator (Sequence inputSequence,
																														 int inputPosition)
		{
			int inputIndex = inputAlphabet.lookupIndex (inputSequence.get(inputPosition), false);
			if (inputIndex == -1)
				throw new IllegalArgumentException ("Input not in dictionary.");
			return transitionIterator (inputIndex);
		}

		public Transducer.TransitionIterator transitionIterator (Object o)
		{
			int inputIndex = inputAlphabet.lookupIndex (o, false);
			if (inputIndex == -1)
				throw new IllegalArgumentException ("Input not in dictionary.");
			return transitionIterator (inputIndex);
		}
		
		public Transducer.TransitionIterator transitionIterator (int input)
		{
			return new TransitionIterator (this, input);
		}
		
		public Transducer.TransitionIterator transitionIterator ()
		{
			return new TransitionIterator (this);
		}

		public String getName ()
		{
			return name;
		}

		public void incrementInitialCount (double count)
		{
			if (initialStateCounts == null)
				throw new IllegalStateException ("Transducer is not currently trainable.");
			initialStateCounts.increment (index, count);
		}

		public void incrementFinalCount (double count)
		{
			if (finalStateCounts == null)
				throw new IllegalStateException ("Transducer is not currently trainable.");
			finalStateCounts.increment (index, count);
		}

		private void estimate ()
		{
			if (transitionCounts == null)
				throw new IllegalStateException ("Transducer is not currently trainable.");
			Multinomial transitionDistribution = transitionCounts.estimate ();
			for (int i = 0; i < transitions.length; i++)
				transitions[i].weight = transitionDistribution.logProbability (i);
		}

		private static final long serialVersionUID = 1;
	}

	@SuppressWarnings("serial")
  protected class TransitionIterator extends Transducer.TransitionIterator
	{
		// If "index" is >= -1 we are going through all FeatureState.transitions[] by index.
		// If "index" is -2, we are following the chain of FeatureTransition.nextWithSameInput,
		// and "transition" is already initialized to the first transition.
		// If "index" is -3, we are following the chain of FeatureTransition.nextWithSameInput,
		// and the next transition should be found by following the chain.
		int index;
		Transition transition;
		State source;
		int input;

		// Iterate through all transitions, independent of input
		public TransitionIterator (State source)
		{
			//System.out.println ("FeatureTransitionIterator over all");
			this.source = source;
			this.input = -1;
			this.index = -1;
			this.transition = null;
		}
		
		public TransitionIterator (State source, int input)
		{
			//System.out.println ("SymbolTransitionIterator over "+input);
			this.source = source;
			this.input = input;
			this.index = -2;
			this.transition = (Transition) source.input2transitions.get (input);
		}

		public boolean hasNext ()
		{
			if (index >= -1) {
				//System.out.println ("hasNext index " + index);
				return (index < source.transitions.length-1);
			}
      return (index == -2 ? transition != null : transition.nextWithSameInput != null);
		};

		public Transducer.State nextState ()
		{
			if (index >= -1)
				transition = source.transitions[++index];
			else if (index == -2)
				index = -3;
			else
				transition = transition.nextWithSameInput;
			return transition.getDestinationState();
		}

		public int getIndex () { return index; }
		public Object getInput () { return inputAlphabet.lookupObject(transition.input); }
		public Object getOutput () { return outputAlphabet.lookupObject(transition.output); }
		public double getWeight () { return transition.weight; }
		public Transducer.State getSourceState () { return source; }
		public Transducer.State getDestinationState () {
			return transition.getDestinationState ();	}

		public void incrementCount (double count) {
			logger.info ("FeatureTransducer incrementCount "+count);
			source.transitionCounts.increment (transition.index, count); }
		
	}

	// Note: this class has a natural ordering that is inconsistent with equals.
	protected class Transition
	{
		int input, output;
		double weight;
		int index;
		String destinationName;
		State destination = null;
		Transition nextWithSameInput;

		public Transition (int input, int output, double weight,
											 State sourceState, String destinationName)
		{
			this.input = input;
			this.output = output;
			this.weight = weight;
			this.nextWithSameInput = (Transition) sourceState.input2transitions.get (input);
			sourceState.input2transitions.put (input, this);
			// this.index is set by the caller of this constructor
			this.destinationName = destinationName;
		}

		public State getDestinationState ()
		{
			if (destination == null) {
				destination = name2state.get (destinationName);
				assert (destination != null);
			}
			return destination;
		}

	}

	private static final long serialVersionUID = 1;

	

}
