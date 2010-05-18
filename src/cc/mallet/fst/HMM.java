/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
 @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.fst;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Multinomial;
import cc.mallet.types.Sequence;

import cc.mallet.pipe.Pipe;

import cc.mallet.util.MalletLogger;

/** A Hidden Markov Model. */
public class HMM extends Transducer implements Serializable {
	private static Logger logger = MalletLogger.getLogger(HMM.class.getName());

	static final String LABEL_SEPARATOR = ",";

	Alphabet inputAlphabet;
	Alphabet outputAlphabet;
	ArrayList<State> states = new ArrayList<State>();
	ArrayList<State> initialStates = new ArrayList<State>();
	HashMap<String, State> name2state = new HashMap<String, State>();
	Multinomial.Estimator[] transitionEstimator;
	Multinomial.Estimator[] emissionEstimator;
	Multinomial.Estimator initialEstimator;
	Multinomial[] transitionMultinomial;
	Multinomial[] emissionMultinomial;
	Multinomial initialMultinomial;

	public HMM(Pipe inputPipe, Pipe outputPipe) {
		this.inputPipe = inputPipe;
		this.outputPipe = outputPipe;
		this.inputAlphabet = inputPipe.getDataAlphabet();
		this.outputAlphabet = inputPipe.getTargetAlphabet();
	}

	public HMM(Alphabet inputAlphabet, Alphabet outputAlphabet) {
		inputAlphabet.stopGrowth();
		logger.info("HMM input dictionary size = " + inputAlphabet.size());
		this.inputAlphabet = inputAlphabet;
		this.outputAlphabet = outputAlphabet;
	}

	public Alphabet getInputAlphabet() {
		return inputAlphabet;
	}

	public Alphabet getOutputAlphabet() {
		return outputAlphabet;
	}

	public void print() {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < numStates(); i++) {
			State s = (State) getState(i);
			sb.append("STATE NAME=\"");
			sb.append(s.name);
			sb.append("\" (");
			sb.append(s.destinations.length);
			sb.append(" outgoing transitions)\n");
			sb.append("  ");
			sb.append("initialWeight= ");
			sb.append(s.initialWeight);
			sb.append('\n');
			sb.append("  ");
			sb.append("finalWeight= ");
			sb.append(s.finalWeight);
			sb.append('\n');
			sb.append("Emission distribution:\n" + emissionMultinomial[i]
					+ "\n\n");
			sb.append("Transition distribution:\n"
					+ transitionMultinomial[i].toString());
		}
		System.out.println(sb.toString());
	}

	public void addState(String name, double initialWeight, double finalWeight,
			String[] destinationNames, String[] labelNames) {
		assert (labelNames.length == destinationNames.length);
		if (name2state.get(name) != null)
			throw new IllegalArgumentException("State with name `" + name
					+ "' already exists.");
		State s = new State(name, states.size(), initialWeight, finalWeight,
				destinationNames, labelNames, this);
		s.print();
		states.add(s);
		if (initialWeight > IMPOSSIBLE_WEIGHT)
			initialStates.add(s);
		name2state.put(name, s);
	}

	/**
	 * Add a state with parameters equal zero, and labels on out-going arcs the
	 * same name as their destination state names.
	 */
	public void addState(String name, String[] destinationNames) {
		this.addState(name, 0, 0, destinationNames, destinationNames);
	}

	/**
	 * Add a group of states that are fully connected with each other, with
	 * parameters equal zero, and labels on their out-going arcs the same name
	 * as their destination state names.
	 */
	public void addFullyConnectedStates(String[] stateNames) {
		for (int i = 0; i < stateNames.length; i++)
			addState(stateNames[i], stateNames);
	}

	public void addFullyConnectedStatesForLabels() {
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			labels[i] = (String) outputAlphabet.lookupObject(i);
		}
		addFullyConnectedStates(labels);
	}

	private boolean[][] labelConnectionsIn(InstanceList trainingSet) {
		int numLabels = outputAlphabet.size();
		boolean[][] connections = new boolean[numLabels][numLabels];
		for (Instance instance : trainingSet) {
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			for (int j = 1; j < output.size(); j++) {
				int sourceIndex = outputAlphabet.lookupIndex(output.get(j - 1));
				int destIndex = outputAlphabet.lookupIndex(output.get(j));
				assert (sourceIndex >= 0 && destIndex >= 0);
				connections[sourceIndex][destIndex] = true;
			}
		}
		return connections;
	}

	/**
	 * Add states to create a first-order Markov model on labels, adding only
	 * those transitions the occur in the given trainingSet.
	 */
	public void addStatesForLabelsConnectedAsIn(InstanceList trainingSet) {
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn(trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					numDestinations++;
			String[] destinationNames = new String[numDestinations];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					destinationNames[destinationIndex++] = (String) outputAlphabet
							.lookupObject(j);
			addState((String) outputAlphabet.lookupObject(i), destinationNames);
		}
	}

	/**
	 * Add as many states as there are labels, but don't create separate weights
	 * for each source-destination pair of states. Instead have all the incoming
	 * transitions to a state share the same weights.
	 */
	public void addStatesForHalfLabelsConnectedAsIn(InstanceList trainingSet) {
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn(trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					numDestinations++;
			String[] destinationNames = new String[numDestinations];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					destinationNames[destinationIndex++] = (String) outputAlphabet
							.lookupObject(j);
			addState((String) outputAlphabet.lookupObject(i), 0.0, 0.0,
					destinationNames, destinationNames);
		}
	}

	/**
	 * Add as many states as there are labels, but don't create separate
	 * observational-test-weights for each source-destination pair of
	 * states---instead have all the incoming transitions to a state share the
	 * same observational-feature-test weights. However, do create separate
	 * default feature for each transition, (which acts as an HMM-style
	 * transition probability).
	 */
	public void addStatesForThreeQuarterLabelsConnectedAsIn(
			InstanceList trainingSet) {
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn(trainingSet);
		for (int i = 0; i < numLabels; i++) {
			int numDestinations = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j])
					numDestinations++;
			String[] destinationNames = new String[numDestinations];
			int destinationIndex = 0;
			for (int j = 0; j < numLabels; j++)
				if (connections[i][j]) {
					String labelName = (String) outputAlphabet.lookupObject(j);
					destinationNames[destinationIndex] = labelName;
					// The "transition" weights will include only the default
					// feature
					// gsc: variable is never used
					// String wn = (String)outputAlphabet.lookupObject(i) + "->"
					// + (String)outputAlphabet.lookupObject(j);
					destinationIndex++;
				}
			addState((String) outputAlphabet.lookupObject(i), 0.0, 0.0,
					destinationNames, destinationNames);
		}
	}

	public void addFullyConnectedStatesForThreeQuarterLabels(
			InstanceList trainingSet) {
		int numLabels = outputAlphabet.size();
		for (int i = 0; i < numLabels; i++) {
			String[] destinationNames = new String[numLabels];
			for (int j = 0; j < numLabels; j++) {
				String labelName = (String) outputAlphabet.lookupObject(j);
				destinationNames[j] = labelName;
			}
			addState((String) outputAlphabet.lookupObject(i), 0.0, 0.0,
					destinationNames, destinationNames);
		}
	}

	public void addFullyConnectedStatesForBiLabels() {
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			labels[i] = outputAlphabet.lookupObject(i).toString();
		}
		for (int i = 0; i < labels.length; i++) {
			for (int j = 0; j < labels.length; j++) {
				String[] destinationNames = new String[labels.length];
				for (int k = 0; k < labels.length; k++)
					destinationNames[k] = labels[j] + LABEL_SEPARATOR
							+ labels[k];
				addState(labels[i] + LABEL_SEPARATOR + labels[j], 0.0, 0.0,
						destinationNames, labels);
			}
		}
	}

	/**
	 * Add states to create a second-order Markov model on labels, adding only
	 * those transitions the occur in the given trainingSet.
	 */
	public void addStatesForBiLabelsConnectedAsIn(InstanceList trainingSet) {
		int numLabels = outputAlphabet.size();
		boolean[][] connections = labelConnectionsIn(trainingSet);
		for (int i = 0; i < numLabels; i++) {
			for (int j = 0; j < numLabels; j++) {
				if (!connections[i][j])
					continue;
				int numDestinations = 0;
				for (int k = 0; k < numLabels; k++)
					if (connections[j][k])
						numDestinations++;
				String[] destinationNames = new String[numDestinations];
				String[] labels = new String[numDestinations];
				int destinationIndex = 0;
				for (int k = 0; k < numLabels; k++)
					if (connections[j][k]) {
						destinationNames[destinationIndex] = (String) outputAlphabet
								.lookupObject(j)
								+ LABEL_SEPARATOR
								+ (String) outputAlphabet.lookupObject(k);
						labels[destinationIndex] = (String) outputAlphabet
								.lookupObject(k);
						destinationIndex++;
					}
				addState((String) outputAlphabet.lookupObject(i)
						+ LABEL_SEPARATOR
						+ (String) outputAlphabet.lookupObject(j), 0.0, 0.0,
						destinationNames, labels);
			}
		}
	}

	public void addFullyConnectedStatesForTriLabels() {
		String[] labels = new String[outputAlphabet.size()];
		// This is assuming the the entries in the outputAlphabet are Strings!
		for (int i = 0; i < outputAlphabet.size(); i++) {
			logger.info("HMM: outputAlphabet.lookup class = "
					+ outputAlphabet.lookupObject(i).getClass().getName());
			labels[i] = outputAlphabet.lookupObject(i).toString();
		}
		for (int i = 0; i < labels.length; i++) {
			for (int j = 0; j < labels.length; j++) {
				for (int k = 0; k < labels.length; k++) {
					String[] destinationNames = new String[labels.length];
					for (int l = 0; l < labels.length; l++)
						destinationNames[l] = labels[j] + LABEL_SEPARATOR
								+ labels[k] + LABEL_SEPARATOR + labels[l];
					addState(labels[i] + LABEL_SEPARATOR + labels[j]
							+ LABEL_SEPARATOR + labels[k], 0.0, 0.0,
							destinationNames, labels);
				}
			}
		}
	}

	public void addSelfTransitioningStateForAllLabels(String name) {
		String[] labels = new String[outputAlphabet.size()];
		String[] destinationNames = new String[outputAlphabet.size()];
		for (int i = 0; i < outputAlphabet.size(); i++) {
			labels[i] = outputAlphabet.lookupObject(i).toString();
			destinationNames[i] = name;
		}
		addState(name, 0.0, 0.0, destinationNames, labels);
	}

	private String concatLabels(String[] labels) {
		String sep = "";
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < labels.length; i++) {
			buf.append(sep).append(labels[i]);
			sep = LABEL_SEPARATOR;
		}
		return buf.toString();
	}

	private String nextKGram(String[] history, int k, String next) {
		String sep = "";
		StringBuffer buf = new StringBuffer();
		int start = history.length + 1 - k;
		for (int i = start; i < history.length; i++) {
			buf.append(sep).append(history[i]);
			sep = LABEL_SEPARATOR;
		}
		buf.append(sep).append(next);
		return buf.toString();
	}

	private boolean allowedTransition(String prev, String curr, Pattern no,
			Pattern yes) {
		String pair = concatLabels(new String[] { prev, curr });
		if (no != null && no.matcher(pair).matches())
			return false;
		if (yes != null && !yes.matcher(pair).matches())
			return false;
		return true;
	}

	private boolean allowedHistory(String[] history, Pattern no, Pattern yes) {
		for (int i = 1; i < history.length; i++)
			if (!allowedTransition(history[i - 1], history[i], no, yes))
				return false;
		return true;
	}

	/**
	 * Assumes that the HMM's output alphabet contains <code>String</code>s.
	 * Creates an order-<em>n</em> HMM with input predicates and output labels
	 * given by <code>trainingSet</code> and order, connectivity, and weights
	 * given by the remaining arguments.
	 * 
	 * @param trainingSet
	 *            the training instances
	 * @param orders
	 *            an array of increasing non-negative numbers giving the orders
	 *            of the features for this HMM. The largest number <em>n</em> is
	 *            the Markov order of the HMM. States are <em>n</em>-tuples of
	 *            output labels. Each of the other numbers <em>k</em> in
	 *            <code>orders</code> represents a weight set shared by all
	 *            destination states whose last (most recent) <em>k</em> labels
	 *            agree. If <code>orders</code> is <code>null</code>, an order-0
	 *            HMM is built.
	 * @param defaults
	 *            If non-null, it must be the same length as <code>orders</code>
	 *            , with <code>true</code> positions indicating that the weight
	 *            set for the corresponding order contains only the weight for a
	 *            default feature; otherwise, the weight set has weights for all
	 *            features built from input predicates.
	 * @param start
	 *            The label that represents the context of the start of a
	 *            sequence. It may be also used for sequence labels.
	 * @param forbidden
	 *            If non-null, specifies what pairs of successive labels are not
	 *            allowed, both for constructing <em>n</em>order states or for
	 *            transitions. A label pair (<em>u</em>,<em>v</em>) is not
	 *            allowed if <em>u</em> + "," + <em>v</em> matches
	 *            <code>forbidden</code>.
	 * @param allowed
	 *            If non-null, specifies what pairs of successive labels are
	 *            allowed, both for constructing <em>n</em>order states or for
	 *            transitions. A label pair (<em>u</em>,<em>v</em>) is allowed
	 *            only if <em>u</em> + "," + <em>v</em> matches
	 *            <code>allowed</code>.
	 * @param fullyConnected
	 *            Whether to include all allowed transitions, even those not
	 *            occurring in <code>trainingSet</code>,
	 * @returns The name of the start state.
	 * 
	 */
	public String addOrderNStates(InstanceList trainingSet, int[] orders,
			boolean[] defaults, String start, Pattern forbidden,
			Pattern allowed, boolean fullyConnected) {
		boolean[][] connections = null;
		if (!fullyConnected)
			connections = labelConnectionsIn(trainingSet);
		int order = -1;
		if (defaults != null && defaults.length != orders.length)
			throw new IllegalArgumentException(
					"Defaults must be null or match orders");
		if (orders == null)
			order = 0;
		else {
			for (int i = 0; i < orders.length; i++) {
				if (orders[i] <= order)
					throw new IllegalArgumentException(
							"Orders must be non-negative and in ascending order");
				order = orders[i];
			}
			if (order < 0)
				order = 0;
		}
		if (order > 0) {
			int[] historyIndexes = new int[order];
			String[] history = new String[order];
			String label0 = (String) outputAlphabet.lookupObject(0);
			for (int i = 0; i < order; i++)
				history[i] = label0;
			int numLabels = outputAlphabet.size();
			while (historyIndexes[0] < numLabels) {
				logger.info("Preparing " + concatLabels(history));
				if (allowedHistory(history, forbidden, allowed)) {
					String stateName = concatLabels(history);
					int nt = 0;
					String[] destNames = new String[numLabels];
					String[] labelNames = new String[numLabels];
					for (int nextIndex = 0; nextIndex < numLabels; nextIndex++) {
						String next = (String) outputAlphabet
								.lookupObject(nextIndex);
						if (allowedTransition(history[order - 1], next,
								forbidden, allowed)
								&& (fullyConnected || connections[historyIndexes[order - 1]][nextIndex])) {
							destNames[nt] = nextKGram(history, order, next);
							labelNames[nt] = next;
							nt++;
						}
					}
					if (nt < numLabels) {
						String[] newDestNames = new String[nt];
						String[] newLabelNames = new String[nt];
						for (int t = 0; t < nt; t++) {
							newDestNames[t] = destNames[t];
							newLabelNames[t] = labelNames[t];
						}
						destNames = newDestNames;
						labelNames = newLabelNames;
					}
					addState(stateName, 0.0, 0.0, destNames, labelNames);
				}
				for (int o = order - 1; o >= 0; o--)
					if (++historyIndexes[o] < numLabels) {
						history[o] = (String) outputAlphabet
								.lookupObject(historyIndexes[o]);
						break;
					} else if (o > 0) {
						historyIndexes[o] = 0;
						history[o] = label0;
					}
			}
			for (int i = 0; i < order; i++)
				history[i] = start;
			return concatLabels(history);
		}
		String[] stateNames = new String[outputAlphabet.size()];
		for (int s = 0; s < outputAlphabet.size(); s++)
			stateNames[s] = (String) outputAlphabet.lookupObject(s);
		for (int s = 0; s < outputAlphabet.size(); s++)
			addState(stateNames[s], 0.0, 0.0, stateNames, stateNames);
		return start;
	}

	public State getState(String name) {
		return (State) name2state.get(name);
	}

	public int numStates() {
		return states.size();
	}

	public Transducer.State getState(int index) {
		return (Transducer.State) states.get(index);
	}

	public Iterator initialStateIterator() {
		return initialStates.iterator();
	}

	public boolean isTrainable() {
		return true;
	}

	private Alphabet getTransitionAlphabet() {
		Alphabet transitionAlphabet = new Alphabet();
		for (int i = 0; i < numStates(); i++)
			transitionAlphabet.lookupIndex(getState(i).getName(), true);
		return transitionAlphabet;
	}

	@Deprecated
	public void reset() {
		emissionEstimator = new Multinomial.LaplaceEstimator[numStates()];
		transitionEstimator = new Multinomial.LaplaceEstimator[numStates()];
		emissionMultinomial = new Multinomial[numStates()];
		transitionMultinomial = new Multinomial[numStates()];
		Alphabet transitionAlphabet = getTransitionAlphabet();
		for (int i = 0; i < numStates(); i++) {
			emissionEstimator[i] = new Multinomial.LaplaceEstimator(
					inputAlphabet);
			transitionEstimator[i] = new Multinomial.LaplaceEstimator(
					transitionAlphabet);
			emissionMultinomial[i] = new Multinomial(
					getUniformArray(inputAlphabet.size()), inputAlphabet);
			transitionMultinomial[i] = new Multinomial(
					getUniformArray(transitionAlphabet.size()),
					transitionAlphabet);
		}
		initialMultinomial = new Multinomial(getUniformArray(transitionAlphabet
				.size()), transitionAlphabet);
		initialEstimator = new Multinomial.LaplaceEstimator(transitionAlphabet);
	}

	/**
	 * Separate initialization of initial/transitions and emissions. All
	 * probabilities are proportional to (1+Uniform[0,1])^noise.
	 * 
	 * @author kedarb
	 * @param random
	 *            Random object (if null use uniform distribution)
	 * @param noise
	 *            Noise exponent to use. If zero, then uniform distribution.
	 */
	public void initTransitions(Random random, double noise) {
		Alphabet transitionAlphabet = getTransitionAlphabet();
		initialMultinomial = new Multinomial(getRandomArray(transitionAlphabet
				.size(), random, noise), transitionAlphabet);
		initialEstimator = new Multinomial.LaplaceEstimator(transitionAlphabet);
		transitionMultinomial = new Multinomial[numStates()];
		transitionEstimator = new Multinomial.LaplaceEstimator[numStates()];
		for (int i = 0; i < numStates(); i++) {
			transitionMultinomial[i] = new Multinomial(getRandomArray(
					transitionAlphabet.size(), random, noise),
					transitionAlphabet);
			transitionEstimator[i] = new Multinomial.LaplaceEstimator(
					transitionAlphabet);
			// set state's initial weight
			State s = (State) getState(i);
			s.setInitialWeight(initialMultinomial.logProbability(s.getName()));
		}
	}

	public void initEmissions(Random random, double noise) {
		emissionMultinomial = new Multinomial[numStates()];
		emissionEstimator = new Multinomial.LaplaceEstimator[numStates()];
		for (int i = 0; i < numStates(); i++) {
			emissionMultinomial[i] = new Multinomial(getRandomArray(
					inputAlphabet.size(), random, noise), inputAlphabet);
			emissionEstimator[i] = new Multinomial.LaplaceEstimator(
					inputAlphabet);
		}
	}

	public void estimate() {
		Alphabet transitionAlphabet = getTransitionAlphabet();
		initialMultinomial = initialEstimator.estimate();
		initialEstimator = new Multinomial.LaplaceEstimator(transitionAlphabet);
		for (int i = 0; i < numStates(); i++) {
			State s = (State) getState(i);
			emissionMultinomial[i] = emissionEstimator[i].estimate();
			transitionMultinomial[i] = transitionEstimator[i].estimate();
			s.setInitialWeight(initialMultinomial.logProbability(s.getName()));
			// reset estimators
			emissionEstimator[i] = new Multinomial.LaplaceEstimator(
					inputAlphabet);
			transitionEstimator[i] = new Multinomial.LaplaceEstimator(
					transitionAlphabet);
		}
	}

	/**
	 * Trains a HMM without validation and evaluation.
	 */
	public boolean train(InstanceList ilist) {
		return train(ilist, (InstanceList) null, (InstanceList) null);
	}

	/**
	 * Trains a HMM with <tt>evaluator</tt> set to null.
	 */
	public boolean train(InstanceList ilist, InstanceList validation,
			InstanceList testing) {
		return train(ilist, validation, testing, (TransducerEvaluator) null);
	}

	public boolean train(InstanceList ilist, InstanceList validation,
			InstanceList testing, TransducerEvaluator eval) {
		assert (ilist.size() > 0);
		if (emissionEstimator == null) {
			emissionEstimator = new Multinomial.LaplaceEstimator[numStates()];
			transitionEstimator = new Multinomial.LaplaceEstimator[numStates()];
			emissionMultinomial = new Multinomial[numStates()];
			transitionMultinomial = new Multinomial[numStates()];
			Alphabet transitionAlphabet = new Alphabet();
			for (int i = 0; i < numStates(); i++)
				transitionAlphabet.lookupIndex(((State) states.get(i))
						.getName(), true);
			for (int i = 0; i < numStates(); i++) {
				emissionEstimator[i] = new Multinomial.LaplaceEstimator(
						inputAlphabet);
				transitionEstimator[i] = new Multinomial.LaplaceEstimator(
						transitionAlphabet);
				emissionMultinomial[i] = new Multinomial(
						getUniformArray(inputAlphabet.size()), inputAlphabet);
				transitionMultinomial[i] = new Multinomial(
						getUniformArray(transitionAlphabet.size()),
						transitionAlphabet);
			}
			initialEstimator = new Multinomial.LaplaceEstimator(
					transitionAlphabet);
		}
		for (Instance instance : ilist) {
			FeatureSequence input = (FeatureSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			new SumLatticeDefault(this, input, output, new Incrementor());
		}
		initialMultinomial = initialEstimator.estimate();
		for (int i = 0; i < numStates(); i++) {
			emissionMultinomial[i] = emissionEstimator[i].estimate();
			transitionMultinomial[i] = transitionEstimator[i].estimate();
			getState(i).setInitialWeight(
					initialMultinomial.logProbability(getState(i).getName()));
		}

		return true;
	}

	public class Incrementor implements Transducer.Incrementor {
		public void incrementFinalState(Transducer.State s, double count) {
		}

		public void incrementInitialState(Transducer.State s, double count) {
			initialEstimator.increment(s.getName(), count);
		}

		public void incrementTransition(Transducer.TransitionIterator ti,
				double count) {
			int inputFtr = (Integer) ti.getInput();
			State src = (HMM.State) ((TransitionIterator) ti).getSourceState();
			State dest = (HMM.State) ((TransitionIterator) ti)
					.getDestinationState();
			int index = ti.getIndex();
			emissionEstimator[index].increment(inputFtr, count);
			transitionEstimator[src.getIndex()]
					.increment(dest.getName(), count);
		}
	}

	public class WeightedIncrementor implements Transducer.Incrementor {
		double weight = 1.0;

		public WeightedIncrementor(double wt) {
			this.weight = wt;
		}

		public void incrementFinalState(Transducer.State s, double count) {
		}

		public void incrementInitialState(Transducer.State s, double count) {
			initialEstimator.increment(s.getName(), weight * count);
		}

		public void incrementTransition(Transducer.TransitionIterator ti,
				double count) {
			int inputFtr = (Integer) ti.getInput();
			State src = (HMM.State) ((TransitionIterator) ti).getSourceState();
			State dest = (HMM.State) ((TransitionIterator) ti)
					.getDestinationState();
			int index = ti.getIndex();
			emissionEstimator[index].increment(inputFtr, weight * count);
			transitionEstimator[src.getIndex()].increment(dest.getName(),
					weight * count);
		}
	}

	public void write(File f) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(f));
			oos.writeObject(this);
			oos.close();
		} catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}

	private double[] getUniformArray(int size) {
		double[] ret = new double[size];
		for (int i = 0; i < size; i++)
			// gsc: removing unnecessary cast from 'size'
			ret[i] = 1.0 / size;
		return ret;
	}

	// kedarb: p[i] = (1+random)^noise/sum
	private double[] getRandomArray(int size, Random random, double noise) {
		double[] ret = new double[size];
		double sum = 0;
		for (int i = 0; i < size; i++) {
			ret[i] = random == null ? 1.0 : Math.pow(1.0 + random.nextDouble(),
					noise);
			sum += ret[i];
		}
		for (int i = 0; i < size; i++)
			ret[i] /= sum;
		return ret;
	}

	// Serialization
	// For HMM class

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	static final int NULL_INTEGER = -1;

	/* Need to check for null pointers. */
	/* Bug fix from Cheng-Ju Kuo cju.kuo@gmail.com */
	private void writeObject(ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(inputPipe);
		out.writeObject(outputPipe);
		out.writeObject(inputAlphabet);
		out.writeObject(outputAlphabet);
		size = states.size();
		out.writeInt(size);
		for (i = 0; i < size; i++)
			out.writeObject(states.get(i));
		size = initialStates.size();
		out.writeInt(size);
		for (i = 0; i < size; i++)
			out.writeObject(initialStates.get(i));
		out.writeObject(name2state);
		if (emissionEstimator != null) {
			size = emissionEstimator.length;
			out.writeInt(size);
			for (i = 0; i < size; i++)
				out.writeObject(emissionEstimator[i]);
		} else
			out.writeInt(NULL_INTEGER);
		if (emissionMultinomial != null) {
			size = emissionMultinomial.length;
			out.writeInt(size);
			for (i = 0; i < size; i++)
				out.writeObject(emissionMultinomial[i]);
		} else
			out.writeInt(NULL_INTEGER);
		if (transitionEstimator != null) {
			size = transitionEstimator.length;
			out.writeInt(size);
			for (i = 0; i < size; i++)
				out.writeObject(transitionEstimator[i]);
		} else
			out.writeInt(NULL_INTEGER);
		if (transitionMultinomial != null) {
			size = transitionMultinomial.length;
			out.writeInt(size);
			for (i = 0; i < size; i++)
				out.writeObject(transitionMultinomial[i]);
		} else
			out.writeInt(NULL_INTEGER);
	}

	/* Bug fix from Cheng-Ju Kuo cju.kuo@gmail.com */
	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		int size, i;
		int version = in.readInt();
		inputPipe = (Pipe) in.readObject();
		outputPipe = (Pipe) in.readObject();
		inputAlphabet = (Alphabet) in.readObject();
		outputAlphabet = (Alphabet) in.readObject();
		size = in.readInt();
		states = new ArrayList();
		for (i = 0; i < size; i++) {
			State s = (HMM.State) in.readObject();
			states.add(s);
		}
		size = in.readInt();
		initialStates = new ArrayList();
		for (i = 0; i < size; i++) {
			State s = (HMM.State) in.readObject();
			initialStates.add(s);
		}
		name2state = (HashMap) in.readObject();
		size = in.readInt();
		if (size == NULL_INTEGER) {
			emissionEstimator = null;
		} else {
			emissionEstimator = new Multinomial.Estimator[size];
			for (i = 0; i < size; i++) {
				emissionEstimator[i] = (Multinomial.Estimator) in.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			emissionMultinomial = null;
		} else {
			emissionMultinomial = new Multinomial[size];
			for (i = 0; i < size; i++) {
				emissionMultinomial[i] = (Multinomial) in.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			transitionEstimator = null;
		} else {
			transitionEstimator = new Multinomial.Estimator[size];
			for (i = 0; i < size; i++) {
				transitionEstimator[i] = (Multinomial.Estimator) in
						.readObject();
			}
		}
		size = in.readInt();
		if (size == NULL_INTEGER) {
			transitionMultinomial = null;
		} else {
			transitionMultinomial = new Multinomial[size];
			for (i = 0; i < size; i++) {
				transitionMultinomial[i] = (Multinomial) in.readObject();
			}
		}
	}

	public static class State extends Transducer.State implements Serializable {
		// Parameters indexed by destination state, feature index
		String name;
		int index;
		double initialWeight, finalWeight;
		String[] destinationNames;
		State[] destinations;
		String[] labels;
		HMM hmm;

		// No arg constructor so serialization works

		protected State() {
			super();
		}

		protected State(String name, int index, double initialWeight,
				double finalWeight, String[] destinationNames,
				String[] labelNames, HMM hmm) {
			super();
			assert (destinationNames.length == labelNames.length);
			this.name = name;
			this.index = index;
			this.initialWeight = initialWeight;
			this.finalWeight = finalWeight;
			this.destinationNames = new String[destinationNames.length];
			this.destinations = new State[labelNames.length];
			this.labels = new String[labelNames.length];
			this.hmm = hmm;
			for (int i = 0; i < labelNames.length; i++) {
				// Make sure this label appears in our output Alphabet
				hmm.outputAlphabet.lookupIndex(labelNames[i]);
				this.destinationNames[i] = destinationNames[i];
				this.labels[i] = labelNames[i];
			}
		}

		public Transducer getTransducer() {
			return hmm;
		}

		public double getFinalWeight() {
			return finalWeight;
		}

		public double getInitialWeight() {
			return initialWeight;
		}

		public void setFinalWeight(double c) {
			finalWeight = c;
		}

		public void setInitialWeight(double c) {
			initialWeight = c;
		}

		public void print() {
			System.out.println("State #" + index + " \"" + name + "\"");
			System.out.println("initialWeight=" + initialWeight
					+ ", finalWeight=" + finalWeight);
			System.out.println("#destinations=" + destinations.length);
			for (int i = 0; i < destinations.length; i++)
				System.out.println("-> " + destinationNames[i]);
		}

		public State getDestinationState(int index) {
			State ret;
			if ((ret = destinations[index]) == null) {
				ret = destinations[index] = (State) hmm.name2state
						.get(destinationNames[index]);
				assert (ret != null) : index;
			}
			return ret;
		}

		public Transducer.TransitionIterator transitionIterator(
				Sequence inputSequence, int inputPosition,
				Sequence outputSequence, int outputPosition) {
			if (inputPosition < 0 || outputPosition < 0)
				throw new UnsupportedOperationException(
						"Epsilon transitions not implemented.");
			if (inputSequence == null)
				throw new UnsupportedOperationException(
						"HMMs are generative models; but this is not yet implemented.");
			if (!(inputSequence instanceof FeatureSequence))
				throw new UnsupportedOperationException(
						"HMMs currently expect Instances to have FeatureSequence data");
			return new TransitionIterator(this,
					(FeatureSequence) inputSequence, inputPosition,
					(outputSequence == null ? null : (String) outputSequence
							.get(outputPosition)), hmm);
		}

		public String getName() {
			return name;
		}

		public int getIndex() {
			return index;
		}

		public void incrementInitialCount(double count) {
		}

		public void incrementFinalCount(double count) {
		}

		// Serialization
		// For class State

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private static final int NULL_INTEGER = -1;

		private void writeObject(ObjectOutputStream out) throws IOException {
			int i, size;
			out.writeInt(CURRENT_SERIAL_VERSION);
			out.writeObject(name);
			out.writeInt(index);
			size = (destinationNames == null) ? NULL_INTEGER
					: destinationNames.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for (i = 0; i < size; i++) {
					out.writeObject(destinationNames[i]);
				}
			}
			size = (destinations == null) ? NULL_INTEGER : destinations.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for (i = 0; i < size; i++) {
					out.writeObject(destinations[i]);
				}
			}
			size = (labels == null) ? NULL_INTEGER : labels.length;
			out.writeInt(size);
			if (size != NULL_INTEGER) {
				for (i = 0; i < size; i++)
					out.writeObject(labels[i]);
			}
			out.writeObject(hmm);
		}

		private void readObject(ObjectInputStream in) throws IOException,
				ClassNotFoundException {
			int size, i;
			int version = in.readInt();
			name = (String) in.readObject();
			index = in.readInt();
			size = in.readInt();
			if (size != NULL_INTEGER) {
				destinationNames = new String[size];
				for (i = 0; i < size; i++) {
					destinationNames[i] = (String) in.readObject();
				}
			} else {
				destinationNames = null;
			}
			size = in.readInt();
			if (size != NULL_INTEGER) {
				destinations = new State[size];
				for (i = 0; i < size; i++) {
					destinations[i] = (State) in.readObject();
				}
			} else {
				destinations = null;
			}
			size = in.readInt();
			if (size != NULL_INTEGER) {
				labels = new String[size];
				for (i = 0; i < size; i++)
					labels[i] = (String) in.readObject();
				// inputAlphabet = (Alphabet) in.readObject();
				// outputAlphabet = (Alphabet) in.readObject();
			} else {
				labels = null;
			}
			hmm = (HMM) in.readObject();
		}

	}

	protected static class TransitionIterator extends
			Transducer.TransitionIterator implements Serializable {
		State source;
		int index, nextIndex, inputPos;
		double[] weights; // -logProb
		// Eventually change this because we will have a more space-efficient
		// FeatureVectorSequence that cannot break out each FeatureVector
		FeatureSequence inputSequence;
		Integer inputFeature;
		HMM hmm;

		public TransitionIterator(State source, FeatureSequence inputSeq,
				int inputPosition, String output, HMM hmm) {
			this.source = source;
			this.hmm = hmm;
			this.inputSequence = inputSeq;
			this.inputFeature = new Integer(inputSequence
					.getIndexAtPosition(inputPosition));
			this.inputPos = inputPosition;
			this.weights = new double[source.destinations.length];
			for (int transIndex = 0; transIndex < source.destinations.length; transIndex++) {
				if (output == null || output.equals(source.labels[transIndex])) {
					weights[transIndex] = 0;
					// xxx should this be emission of the _next_ observation?
					// double logEmissionProb =
					// hmm.emissionMultinomial[source.getIndex()].logProbability
					// (inputSeq.get (inputPosition));
					double logEmissionProb = hmm.emissionMultinomial[transIndex]
							.logProbability(inputSeq.get(inputPosition));
					double logTransitionProb = hmm.transitionMultinomial[source
							.getIndex()]
							.logProbability(source.destinationNames[transIndex]);
					// weight = logProbability
					weights[transIndex] = (logEmissionProb + logTransitionProb);
					assert (!Double.isNaN(weights[transIndex]));
				} else
					weights[transIndex] = IMPOSSIBLE_WEIGHT;
			}
			nextIndex = 0;
			while (nextIndex < source.destinations.length
					&& weights[nextIndex] == IMPOSSIBLE_WEIGHT)
				nextIndex++;
		}

		public boolean hasNext() {
			return nextIndex < source.destinations.length;
		}

		public Transducer.State nextState() {
			assert (nextIndex < source.destinations.length);
			index = nextIndex;
			nextIndex++;
			while (nextIndex < source.destinations.length
					&& weights[nextIndex] == IMPOSSIBLE_WEIGHT)
				nextIndex++;
			return source.getDestinationState(index);
		}

		public int getIndex() {
			return index;
		}

		/*
		 * Returns an Integer object containing the feature index of the symbol
		 * at this position in the input sequence.
		 */
		public Object getInput() {
			return inputFeature;
		}

		// public int getInputPosition () { return inputPos; }
		public Object getOutput() {
			return source.labels[index];
		}

		public double getWeight() {
			return weights[index];
		}

		public Transducer.State getSourceState() {
			return source;
		}

		public Transducer.State getDestinationState() {
			return source.getDestinationState(index);
		}

		// Serialization
		// TransitionIterator

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private static final int NULL_INTEGER = -1;

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.writeInt(CURRENT_SERIAL_VERSION);
			out.writeObject(source);
			out.writeInt(index);
			out.writeInt(nextIndex);
			out.writeInt(inputPos);
			if (weights != null) {
				out.writeInt(weights.length);
				for (int i = 0; i < weights.length; i++) {
					out.writeDouble(weights[i]);
				}
			} else {
				out.writeInt(NULL_INTEGER);
			}
			out.writeObject(inputSequence);
			out.writeObject(inputFeature);
			out.writeObject(hmm);
		}

		private void readObject(ObjectInputStream in) throws IOException,
				ClassNotFoundException {
			int version = in.readInt();
			source = (State) in.readObject();
			index = in.readInt();
			nextIndex = in.readInt();
			inputPos = in.readInt();
			int size = in.readInt();
			if (size == NULL_INTEGER) {
				weights = null;
			} else {
				weights = new double[size];
				for (int i = 0; i < size; i++) {
					weights[i] = in.readDouble();
				}
			}
			inputSequence = (FeatureSequence) in.readObject();
			inputFeature = (Integer) in.readObject();
			hmm = (HMM) in.readObject();
		}

	}
}
