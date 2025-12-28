/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
@author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
*/
package cc.mallet.fst;



import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import cc.mallet.types.ArraySequence;
import cc.mallet.types.Sequence;
import cc.mallet.types.SequencePairAlignment;

import cc.mallet.fst.Transducer.State;
import cc.mallet.fst.Transducer.TransitionIterator;

import cc.mallet.util.MalletLogger;
import cc.mallet.util.search.AStar;
import cc.mallet.util.search.AStarState;
import cc.mallet.util.search.SearchNode;
import cc.mallet.util.search.SearchState;

/** Default, full dynamic programming version of the Viterbi "Max-(Product)-Lattice" algorithm.
 * 
 * @author Fernando Pereira
 * @author Andrew McCallum 
 */
public class MaxLatticeDefault implements MaxLattice
{
	private static Logger logger = MalletLogger.getLogger(MaxLatticeDefault.class.getName());
	//{ logger.setLevel(Level.INFO); }

	private Transducer t;
	private Sequence<Object> input, providedOutput;
	private int latticeLength;
	private ViterbiNode[][] lattice;
	private WeightCache first, last;
	private WeightCache[] caches;
	private int numCaches, maxCaches;
	
	public Transducer getTransducer () { return t; }
	public Sequence getInput() { return input; }
	public Sequence getProvidedOutput() { return providedOutput; }

	private class ViterbiNode implements AStarState {
		int inputPosition;								// Position of input used to enter this node
		State state;											// Transducer state from which this node entered
		Object output;										// Transducer output produced on entering this node
		double delta = Transducer.IMPOSSIBLE_WEIGHT;
		ViterbiNode maxWeightPredecessor = null;
		ViterbiNode (int inputPosition, State state) {
			this.inputPosition = inputPosition;
			this.state = state;
		}
		// The one method required by AStarState
		public double completionCost () { return -delta; }
		public boolean isFinal() {
			return inputPosition == 0 && state.getInitialWeight() > Transducer.IMPOSSIBLE_WEIGHT;
		}
		private class PreviousStateIterator extends AStarState.NextStateIterator {
			private int prev;
			private boolean found;
			private double weight;
			private double[] weights;
			private PreviousStateIterator() {
				prev = 0;
				if (inputPosition > 0) {
					int j = state.getIndex();
					weights = new double[t.numStates()];
					WeightCache c = getCache(inputPosition-1);
					for (int s = 0; s < t.numStates(); s++)
						weights[s] = c.weight[s][j];
				}
			}
			private void lookAhead() {
				if (weights != null && !found) {
					for (; prev < t.numStates(); prev++)
						if (weights[prev] > Transducer.IMPOSSIBLE_WEIGHT) {
							found = true;
							return;
						}
				}
			}
			public boolean hasNext() {
				lookAhead();
				return weights != null && prev < t.numStates();
			}

			public SearchState nextState() {
				lookAhead();
				weight = weights[prev++];
				found = false;
				return getViterbiNode(inputPosition-1, prev-1);
			}

			// Required by SearchState, super-interface of AStarState
			public double cost() {
				return -weight;
			}
			public double weight() {
				return weight;
			}
		}

		public NextStateIterator getNextStates() {
			return new PreviousStateIterator();
		}
	}

	private class WeightCache {
		private WeightCache prev, next;
		private double weight[][];
		private int position;
		private WeightCache(int position) {
			weight = new double[t.numStates()][t.numStates()];
			init(position);
		}
		private void init(int position) {
			this.position = position;
			for (int i = 0; i < t.numStates(); i++)
				for (int j = 0; j < t.numStates(); j++)
					weight[i][j] = Transducer.IMPOSSIBLE_WEIGHT;
		}
	}

	private WeightCache getCache(int position) {
		WeightCache cache = caches[position];
		if (cache == null) {            // No cache for this position
//			System.out.println("cache " + numCaches + "/" + maxCaches);
			if (numCaches < maxCaches)  { // Create another cache
				cache = new WeightCache(position);
				if (numCaches++ == 0)
					first = last = cache;
			}
			else {                        // Steal least used cache
				cache = last;
				caches[cache.position] = null;
				cache.init(position);
			}
			for (int i = 0; i < t.numStates(); i++) {
				if (lattice[position][i] == null || lattice[position][i].delta == Transducer.IMPOSSIBLE_WEIGHT)
					continue;
				State s = t.getState(i);
				TransitionIterator iter =
					s.transitionIterator (input, position, providedOutput, position);
				while (iter.hasNext()) {
					State d = iter.next();
					cache.weight[i][d.getIndex()] = iter.getWeight();
				}
			}        
			caches[position] = cache;
		}
		if (cache != first) {           // Move to front
			if (cache == last)
				last = cache.prev;
			if (cache.prev != null)
				cache.prev.next = cache.next;
			cache.next = first;
			cache.prev = null;
			first.prev = cache;
			first = cache;
		}
		return cache;
	}

	protected ViterbiNode getViterbiNode (int ip, int stateIndex)
	{
		if (lattice[ip][stateIndex] == null)
			lattice[ip][stateIndex] = new ViterbiNode (ip, t.getState (stateIndex));
		return lattice[ip][stateIndex];
	}
	
	public MaxLatticeDefault (Transducer t, Sequence inputSequence) 
	{
		this (t, inputSequence, null, 100000);
	}
	
	public MaxLatticeDefault (Transducer t, Sequence inputSequence, Sequence outputSequence) 
	{
		this (t, inputSequence, outputSequence, 100000);
	}

	/** Initiate Viterbi decoding of the inputSequence, contrained to match non-null parts of the outputSequence.
	 * maxCaches indicates how much state information to memoize in n-best decoding. */
	public MaxLatticeDefault (Transducer t, Sequence inputSequence, Sequence outputSequence, int maxCaches) 
	{
		// This method initializes the forward path, but does not yet do the backward pass.
		this.t = t;
		if (maxCaches < 1)
			maxCaches = 1;
		this.maxCaches = maxCaches;
		assert (inputSequence != null);
		if (logger.isLoggable (Level.FINE)) {
			logger.fine ("Starting ViterbiLattice");
			logger.fine ("Input: ");
			for (int ip = 0; ip < inputSequence.size(); ip++)
				logger.fine (" " + inputSequence.get(ip));
			logger.fine ("\nOutput: ");
			if (outputSequence == null)
				logger.fine ("null");
			else
				for (int op = 0; op < outputSequence.size(); op++)
					logger.fine (" " + outputSequence.get(op));
			logger.fine ("\n");
		}

		this.input = inputSequence;
		this.providedOutput = outputSequence;
		latticeLength = input.size()+1;
		int numStates = t.numStates();
		lattice = new ViterbiNode[latticeLength][numStates];
		caches = new WeightCache[latticeLength-1];

		// Viterbi Forward
		logger.fine ("Starting Viterbi");
		boolean anyInitialState = false;
		for (int i = 0; i < numStates; i++) {
			double initialWeight = t.getState(i).getInitialWeight();
			if (initialWeight > Transducer.IMPOSSIBLE_WEIGHT) {
				ViterbiNode n = getViterbiNode (0, i);
				n.delta = initialWeight;
				anyInitialState = true;
			}
		}

		if (!anyInitialState) {
			logger.warning ("Viterbi: No initial states!");
		}

		for (int ip = 0; ip < latticeLength-1; ip++)
			for (int i = 0; i < numStates; i++) {
				if (lattice[ip][i] == null || lattice[ip][i].delta == Transducer.IMPOSSIBLE_WEIGHT)
					continue;
				State s = t.getState(i);
				TransitionIterator iter = s.transitionIterator (input, ip, providedOutput, ip);
				if (logger.isLoggable (Level.FINE))
					logger.fine (" Starting Viterbi transition iteration from state "
							+ s.getName() + " on input " + input.get(ip));
				while (iter.hasNext()) {
					State destination = iter.next();
					if (logger.isLoggable (Level.FINE))
						logger.fine ("Viterbi[inputPos="+ip
								+"][source="+s.getName()
								+"][dest="+destination.getName()+"]");
					ViterbiNode destinationNode = getViterbiNode (ip+1, destination.getIndex());
					destinationNode.output = iter.getOutput();
					double weight = lattice[ip][i].delta + iter.getWeight();
					if (ip == latticeLength-2) {
						weight += destination.getFinalWeight();
					}
					if (weight > destinationNode.delta) {
						if (logger.isLoggable (Level.FINE))
							logger.fine ("Viterbi[inputPos="+ip
									+"][source][dest="+destination.getName()
									+"] weight increased to "+weight+" by source="+
									s.getName());
						destinationNode.delta = weight;
						destinationNode.maxWeightPredecessor = lattice[ip][i];
					}
				}
			}
	}
	
	public double getDelta (int ip, int stateIndex) {
		if (lattice != null) {
			return getViterbiNode (ip, stateIndex).delta;
		}
    throw new RuntimeException ("Attempt to called getDelta() when lattice not stored.");
	}

	private List<SequencePairAlignment<Object,ViterbiNode>> viterbiNodeAlignmentCache = null;

	/**
   * Perform the backward pass of Viterbi, returning the n-best sequences of
   * ViterbiNodes. Each ViterbiNode contains the state, output symbol, and other
   * information. Note that the length of each ViterbiNode Sequence is
   * inputLength+1, because the first element of the sequence is the start
   * state, and the first input/output symbols occur on the transition from a
   * start-state to the next state. These first input/output symbols are stored
   * in the second ViterbiNode in the sequence. The last ViterbiNode in the
   * sequence corresponds to the final state and has the last input/output
   * symbols.
   */
	public List<SequencePairAlignment<Object,ViterbiNode>> bestViterbiNodeSequences (int n) {
		if (viterbiNodeAlignmentCache != null && viterbiNodeAlignmentCache.size() >= n)
			return viterbiNodeAlignmentCache;
		int numFinal = 0;
		for (int i = 0; i < t.numStates(); i++) {
			if (lattice[latticeLength-1][i] != null && lattice[latticeLength-1][i].delta > Transducer.IMPOSSIBLE_WEIGHT)
				numFinal++;
		}
		ViterbiNode[] finalNodes = new ViterbiNode[numFinal];
		int f = 0;
		for (int i = 0; i < t.numStates(); i++) {
			if (lattice[latticeLength-1][i] != null && lattice[latticeLength-1][i].delta > Transducer.IMPOSSIBLE_WEIGHT)
				finalNodes[f++] = lattice[latticeLength-1][i];
		}
		AStar search = new AStar(finalNodes, latticeLength * t.numStates());
		List<SequencePairAlignment<Object,ViterbiNode>> outputs = new ArrayList<SequencePairAlignment<Object,ViterbiNode>>(n);
		for (int i = 0; i < n && search.hasNext(); i++) {
		  // gsc: removing unnecessary cast
			SearchNode ans = search.next();
			double weight = -ans.getCost();
			ViterbiNode[] seq = new ViterbiNode[latticeLength];
			// Commented out so we get the start state ViterbiNode -akm 12/2007
			//ans = ans.getParent(); // ans now corresponds to the Viterbi node after the first transition
			for (int j = 0; j < latticeLength; j++) {
				ViterbiNode v = (ViterbiNode)ans.getState();
				assert(v.inputPosition == j);  // was == j+1
				seq[j] = v;
				ans = ans.getParent();
			}
			outputs.add(new SequencePairAlignment<Object,ViterbiNode>(input, new ArraySequence<ViterbiNode>(seq), weight));
		}
		viterbiNodeAlignmentCache = outputs;
		return outputs;
	}


	private List<SequencePairAlignment<Object,State>> stateAlignmentCache = null;

	/**
   * Perform the backward pass of Viterbi, returning the n-best sequences of
   * States. Note that the length of each State Sequence is inputLength+1,
   * because the first element of the sequence is the start state, and the first
   * input/output symbols occur on the transition from a start state to the next
   * state. The last State in the sequence corresponds to the final state.
   */	
	public List<SequencePairAlignment<Object,State>> bestStateAlignments (int n) {
		if (stateAlignmentCache != null && stateAlignmentCache.size() >= n)
			return stateAlignmentCache;
		bestViterbiNodeSequences(n); // ensure that viterbiNodeAlignmentCache has at least size n
		ArrayList<SequencePairAlignment<Object,State>> ret = new ArrayList<SequencePairAlignment<Object,State>>(n);
		for (int i = 0; i < n; i++) {
			State[] ss = new State[latticeLength];
			Sequence<ViterbiNode> vs = viterbiNodeAlignmentCache.get(i).output();
			for (int j = 0; j < latticeLength; j++)
				ss[j] = vs.get(j).state; // Here is where we grab the state from the ViterbiNode
			ret.add(new SequencePairAlignment<Object,State>(input, new ArraySequence<State>(ss), viterbiNodeAlignmentCache.get(i).getWeight()));
		}
		stateAlignmentCache = ret;
		return ret;
	}
	
	public SequencePairAlignment<Object,State> bestStateAlignment () {
		return bestStateAlignments(1).get(0);
	}

	public List<Sequence<State>> bestStateSequences(int n) {
		List<SequencePairAlignment<Object,State>> a = bestStateAlignments(n);
		ArrayList<Sequence<State>> ret = new ArrayList<Sequence<State>>(n);
		for (int i = 0; i < n; i++)
			ret.add (a.get(i).output());
		return ret;
	}
	
	public Sequence<State> bestStateSequence() {
		return bestStateAlignments(1).get(0).output();
	}
	
	private List<SequencePairAlignment<Object,Object>> outputAlignmentCache = null;

	public List<SequencePairAlignment<Object,Object>> bestOutputAlignments (int n) {
		if (outputAlignmentCache != null && outputAlignmentCache.size() >= n)
			return outputAlignmentCache;
		bestViterbiNodeSequences(n); // ensure that viterbiNodeAlignmentCache has at least size n
		ArrayList<SequencePairAlignment<Object,Object>> ret = new ArrayList<SequencePairAlignment<Object,Object>>(n);
		for (int i = 0; i < n; i++) {
			Object[] ss = new Object[latticeLength-1];
			Sequence<ViterbiNode> vs = viterbiNodeAlignmentCache.get(i).output();
			for (int j = 0; j < latticeLength-1; j++)
				ss[j] = vs.get(j+1).output; // Here is where we grab the output from the ViterbiNode destination
			ret.add(new SequencePairAlignment<Object,Object>(input, new ArraySequence<Object>(ss), viterbiNodeAlignmentCache.get(i).getWeight()));
		}
		outputAlignmentCache = ret;
		return ret;
	}	
	
	public SequencePairAlignment<Object,Object> bestOutputAlignment () {
		return bestOutputAlignments(1).get(0);
	}

	public List<Sequence<Object>> bestOutputSequences (int n) {
		bestOutputAlignments(n); // ensure that outputAlignmentCache has at least size n
		ArrayList<Sequence<Object>> ret = new ArrayList<Sequence<Object>>(n);
		for (int i = 0; i < n; i++)
			ret.add (outputAlignmentCache.get(i).output());
		return ret;
		// TODO consider caching this result
	}
	
	public Sequence<Object> bestOutputSequence () {
		return bestOutputAlignments(1).get(0).output();
	}
	
	public double bestWeight() {
		return bestOutputAlignments(1).get(0).getWeight();
	}
	
	
	/** Increment states and transitions with a count of 1.0 along the best state sequence.
	 *  This provides for a so-called "Viterbi training" approximation. */
	public void incrementTransducer (Transducer.Incrementor incrementor)
	{
		// We are only going to increment along the single best path ".get(0)" below.
		// We could consider having a version of this method:
		// incrementTransducer(Transducer.Incrementor incrementor, double[] counts)
		// where the number of n-best paths to increment would be determined by counts.length
		SequencePairAlignment<Object,ViterbiNode> viterbiNodeAlignment = this.bestViterbiNodeSequences(1).get(0);
		int sequenceLength = viterbiNodeAlignment.output().size();
		assert (sequenceLength == viterbiNodeAlignment.input().size()); // Not sure this works for unequal input/output lengths
		// Increment the initial state
		incrementor.incrementInitialState(viterbiNodeAlignment.output().get(0).state, 1.0);
		// Increment the final state
		incrementor.incrementFinalState(viterbiNodeAlignment.output().get(sequenceLength-1).state, 1.0);
		for (int ip = 0; ip < viterbiNodeAlignment.input().size()-1; ip++) {
			TransitionIterator iter =
				viterbiNodeAlignment.output().get(ip).state.transitionIterator (input, ip, providedOutput, ip);
			// xxx This assumes that a transition is completely
			// identified, and made unique by its destination state and
			// output.  This may not be true!
			int numIncrements = 0;
			while (iter.hasNext()) {
				if (iter.next().equals (viterbiNodeAlignment.output().get(ip+1).state)
						&& iter.getOutput().equals (viterbiNodeAlignment.output().get(ip).output)) {
					incrementor.incrementTransition(iter, 1.0);
					numIncrements++;
				}
			}
			if (numIncrements > 1)
				throw new IllegalStateException ("More than one satisfying transition found.");
			if (numIncrements == 0)
				throw new IllegalStateException ("No satisfying transition found.");
		}
	}

	public double elementwiseAccuracy (Sequence referenceOutput)
	{
		int accuracy = 0;
		Sequence output = bestOutputSequence();
		assert (referenceOutput.size() == output.size());
		for (int i = 0; i < output.size(); i++) {
			//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
			if (referenceOutput.get(i).toString().equals (output.get(i).toString())) {
				accuracy++;
			}
		}
		logger.info ("Number correct: " + accuracy + " out of " + output.size());
		return ((double)accuracy)/output.size();
	}

	public double tokenAccuracy (Sequence referenceOutput, PrintWriter out)
	{
		Sequence output = bestOutputSequence();
		int accuracy = 0;
		String testString;
		assert (referenceOutput.size() == output.size());
		for (int i = 0; i < output.size(); i++) {
			//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
			testString = output.get(i).toString();
			if (out != null) {
				out.println(testString);
			}
			if (referenceOutput.get(i).toString().equals (testString)) {
				accuracy++;
			}
		}
		logger.info ("Number correct: " + accuracy + " out of " + output.size());
		return ((double)accuracy)/output.size();
	}

	
	public static class Factory extends MaxLatticeFactory implements Serializable
	{
		public MaxLattice newMaxLattice (Transducer trans, Sequence inputSequence, Sequence outputSequence)
		{
			return new MaxLatticeDefault (trans, inputSequence, outputSequence);
		}

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 1;

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.writeInt(CURRENT_SERIAL_VERSION);
		}
		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
			in.readInt();
		}


	}

}