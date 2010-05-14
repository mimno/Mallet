package cc.mallet.fst;

import java.io.IOException;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import cc.mallet.fst.Transducer.State;
import cc.mallet.fst.Transducer.TransitionIterator;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

public class SumLatticeScaling implements SumLattice {
	private static Logger logger = MalletLogger
			.getLogger(SumLatticeScaling.class.getName());
	protected static boolean saveXis = false;

	// "ip" == "input position", "op" == "output position", "i" == "state index"
	@SuppressWarnings("unchecked")
	Sequence input, output;
	Transducer t;
	double totalWeight;
	LatticeNode[][] nodes; // indexed by ip,i
	double[] alphaLogScaling, betaLogScaling;
	double zLogScaling;
	int latticeLength;
	double[][] gammas; // indexed by ip,i
	double[][][] xis; // indexed by ip,i,j; saved only if saveXis is true;

	// Ensure that instances cannot easily be created by a zero arg constructor.
	protected SumLatticeScaling() {
	}

	protected LatticeNode getLatticeNode(int ip, int stateIndex) {
		if (nodes[ip][stateIndex] == null)
			nodes[ip][stateIndex] = new LatticeNode(ip, t.getState(stateIndex));
		return nodes[ip][stateIndex];
	}

	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input) {
		this(trans, input, null, (Transducer.Incrementor) null, saveXis, null);
	}

	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input, boolean saveXis) {
		this(trans, input, null, (Transducer.Incrementor) null, saveXis, null);
	}

	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input,
			Transducer.Incrementor incrementor) {
		this(trans, input, null, incrementor, saveXis, null);
	}

	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input, Sequence output) {
		this(trans, input, output, (Transducer.Incrementor) null, saveXis, null);
	}

	// You may pass null for output, meaning that the lattice
	// is not constrained to match the output
	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input, Sequence output,
			Transducer.Incrementor incrementor) {
		this(trans, input, output, incrementor, saveXis, null);
	}

	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input, Sequence output,
			Transducer.Incrementor incrementor, LabelAlphabet outputAlphabet) {
		this(trans, input, output, incrementor, saveXis, outputAlphabet);
	}

	// You may pass null for output, meaning that the lattice
	// is not constrained to match the output
	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input, Sequence output,
			Transducer.Incrementor incrementor, boolean saveXis) {
		this(trans, input, output, incrementor, saveXis, null);
	}

	@SuppressWarnings("unchecked")
	public SumLatticeScaling(Transducer trans, Sequence input, Sequence output,
			Transducer.Incrementor incrementor, boolean saveXis,
			LabelAlphabet outputAlphabet) {
		assert (output == null || input.size() == output.size());

		// Initialize some structures
		this.t = trans;
		this.input = input;
		this.output = output;
		latticeLength = input.size() + 1;
		int numStates = t.numStates();
		nodes = new LatticeNode[latticeLength][numStates];
		alphaLogScaling = new double[latticeLength];
		betaLogScaling = new double[latticeLength];
		gammas = new double[latticeLength][numStates];
		if (saveXis)
			xis = new double[latticeLength][numStates][numStates];

		double outputCounts[][] = null;
		if (outputAlphabet != null)
			outputCounts = new double[latticeLength][outputAlphabet.size()];

		for (int ip = 0; ip < latticeLength; ip++) {
			alphaLogScaling[ip] = 0.0;
			betaLogScaling[ip] = 0.0;
			for (int i = 0; i < numStates; i++) {
				gammas[ip][i] = Transducer.IMPOSSIBLE_WEIGHT;
				if (saveXis)
					for (int j = 0; j < numStates; j++)
						xis[ip][i][j] = Transducer.IMPOSSIBLE_WEIGHT;
			}
		}

		// Forward pass
		logger.fine("Starting Foward pass");
		boolean atLeastOneInitialState = false;
		for (int i = 0; i < numStates; i++) {
			double initialWeight = t.getState(i).getInitialWeight();
			if (initialWeight > Transducer.IMPOSSIBLE_WEIGHT) {
				getLatticeNode(0, i).alpha = Math.exp(initialWeight);
				atLeastOneInitialState = true;
			}
		}
		rescaleAlphas(0);
		if (atLeastOneInitialState == false)
			logger.warning("There are no starting states!");

		for (int ip = 0; ip < latticeLength - 1; ip++) {
			for (int i = 0; i < numStates; i++) {
				if (isInvalidNode(ip, i))
					continue;
				State s = t.getState(i);
				TransitionIterator iter = s.transitionIterator(input, ip,
						output, ip);
				while (iter.hasNext()) {
					State destination = iter.next();
					LatticeNode destinationNode = getLatticeNode(ip + 1,
							destination.getIndex());
					if (Double.isNaN(destinationNode.alpha))
						destinationNode.alpha = 0;
					destinationNode.output = iter.getOutput();
					double transitionWeight = iter.getWeight();
					destinationNode.alpha += nodes[ip][i].alpha
							* Math.exp(transitionWeight);
				}
			}
			// re-scale alphas to so that \sum_i \alpha[ip][i] = 1
			rescaleAlphas(ip + 1);
		}

		// Calculate total weight of Lattice. This is the normalizer
		double Z = Double.NaN;
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength - 1][i] != null) {
				if (Double.isNaN(Z))
					Z = 0;
				Z += nodes[latticeLength - 1][i].alpha
						* Math.exp(t.getState(i).getFinalWeight());
			}
		zLogScaling = alphaLogScaling[latticeLength - 1];

		if (Double.isNaN(Z)) {
			totalWeight = Transducer.IMPOSSIBLE_WEIGHT;
			return;
		} else
			totalWeight = Math.log(Z) + zLogScaling;

		// Backward pass
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength - 1][i] != null) {
				State s = t.getState(i);
				nodes[latticeLength - 1][i].beta = Math.exp(s.getFinalWeight());
				double gamma = nodes[latticeLength - 1][i].alpha
						* nodes[latticeLength - 1][i].beta / Z;
				gammas[latticeLength - 1][i] = Math.log(gamma);
				if (incrementor != null) {
					double p = gamma;
					assert (p >= 0.0 && p <= 1.0 + 1e-6) : "p=" + p
							+ ", gamma=" + gammas[latticeLength - 1][i];
					incrementor.incrementFinalState(s, p);
				}
			}
		rescaleBetas(latticeLength - 1);

		for (int ip = latticeLength - 2; ip >= 0; ip--) {
			for (int i = 0; i < numStates; i++) {
				if (isInvalidNode(ip, i))
					continue;
				State s = t.getState(i);
				TransitionIterator iter = s.transitionIterator(input, ip,
						output, ip);
				double logScaling = alphaLogScaling[ip]
						+ betaLogScaling[ip + 1] - zLogScaling;
				double pscaling = Math.exp(logScaling);
				while (iter.hasNext()) {
					State destination = iter.next();
					int j = destination.getIndex();
					LatticeNode destinationNode = nodes[ip + 1][j];
					if (destinationNode != null) {
						double transitionWeight = iter.getWeight();
						if (Double.isNaN(nodes[ip][i].beta))
							nodes[ip][i].beta = 0;
						double transitionProb = Math.exp(transitionWeight);
						nodes[ip][i].beta += destinationNode.beta
								* transitionProb;
						double xi = nodes[ip][i].alpha * transitionProb
								* nodes[ip + 1][j].beta / Z;
						if (saveXis)
							xis[ip][i][j] = Math.log(xi) + logScaling;
						if (incrementor != null || outputAlphabet != null) {
							double p = xi * pscaling;
							assert (p >= 0.0 && p <= 1.0 + 1e-6) : "p=" + p
									+ ", xis[" + ip + "][" + i + "][" + j
									+ "]=" + xi;
							if (incrementor != null)
								incrementor.incrementTransition(iter, p);
							if (outputAlphabet != null) {
								int outputIndex = outputAlphabet.lookupIndex(
										iter.getOutput(), false);
								assert (outputIndex >= 0);
								outputCounts[ip][outputIndex] += p;
							}
						}
					}
				}
				gammas[ip][i] = Math.log(nodes[ip][i].alpha * nodes[ip][i].beta
						/ Z)
						+ logScaling;
			}
			// re-scale betas so that they are normalized
			rescaleBetas(ip);
		}
		if (incrementor != null)
			for (int i = 0; i < numStates; i++) {
				double p = Math.exp(gammas[0][i]);
				assert (p >= 0.0 && p <= 1.0 + 1e-6) : "p=" + p;
				incrementor.incrementInitialState(t.getState(i), p);
			}
	}

	private boolean isInvalidNode(int ip, int i) {
		return nodes[ip][i] == null || Double.isNaN(nodes[ip][i].alpha);
	}

	private void rescaleAlphas(int ip) {
		double sumAlpha = 0;
		for (int i = 0; i < t.numStates(); i++) {
			if (!isInvalidNode(ip, i))
				sumAlpha += nodes[ip][i].alpha;
		}
		assert sumAlpha > 0 : "Invalid sum over alphas for ip=" + ip;
		alphaLogScaling[ip] = Math.log(sumAlpha)
				+ (ip == 0 ? 0 : alphaLogScaling[ip - 1]);
		for (int i = 0; i < t.numStates(); i++) {
			if (!isInvalidNode(ip, i))
				nodes[ip][i].alpha /= sumAlpha;
		}
	}

	private void rescaleBetas(int ip) {
		double sumBeta = 0;
		for (int i = 0; i < t.numStates(); i++) {
			if (!isInvalidNode(ip, i))
				sumBeta += nodes[ip][i].beta;
		}
		assert sumBeta > 0 : "Invalid sum over betas for ip=" + ip;
		betaLogScaling[ip] = Math.log(sumBeta)
				+ (ip == latticeLength - 1 ? 0 : betaLogScaling[ip + 1]);
		for (int i = 0; i < t.numStates(); i++) {
			if (!isInvalidNode(ip, i))
				nodes[ip][i].beta /= sumBeta;
		}
	}

	public double[][][] getXis() {
		return xis;
	}

	public double[][] getGammas() {
		return gammas;
	}

	public double getTotalWeight() {
		return totalWeight;
	}

	public double getGammaWeight(int inputPosition, State s) {
		return gammas[inputPosition][s.getIndex()];
	}

	public double getGammaWeight(int inputPosition, int stateIndex) {
		return gammas[inputPosition][stateIndex];
	}

	public double getGammaProbability(int inputPosition, State s) {
		return Math.exp(gammas[inputPosition][s.getIndex()]);
	}

	public double getGammaProbability(int inputPosition, int stateIndex) {
		return getGammaProbability(inputPosition, t.getState(stateIndex));
	}

	public double getXiProbability(int ip, State s1, State s2) {
		return Math.exp(getXiWeight(ip, s1, s2));
	}

	public double getXiWeight(int ip, State s1, State s2) {
		if (xis == null)
			throw new IllegalStateException("xis were not saved.");
		int i = s1.getIndex();
		int j = s2.getIndex();
		return xis[ip][i][j];
	}

	public int length() {
		return latticeLength;
	}

	public double getAlpha(int ip, State s) {
		LatticeNode node = getLatticeNode(ip, s.getIndex());
		return node.alpha * Math.exp(alphaLogScaling[ip]);
	}

	public double getBeta(int ip, State s) {
		LatticeNode node = getLatticeNode(ip, s.getIndex());
		return node.beta * Math.exp(betaLogScaling[ip]);
	}

	public LabelVector getLabelingAtPosition(int outputPosition) {
		throw new RuntimeException("Not implemented for SumLatticeScaling!");
	}

	public Transducer getTransducer() {
		return t;
	}

	protected class LatticeNode {
		int inputPosition;
		State state;
		Object output;
		double alpha = Double.NaN;
		double beta = Double.NaN;

		LatticeNode(int inputPosition, State state) {
			this.inputPosition = inputPosition;
			this.state = state;
		}
	}

	public static class Factory extends SumLatticeFactory implements
			Serializable {
		@SuppressWarnings("unchecked")
		public SumLattice newSumLattice(Transducer trans, Sequence input,
				Sequence output, Transducer.Incrementor incrementor,
				boolean saveXis, LabelAlphabet outputAlphabet) {
			return new SumLatticeScaling(trans, input, output, incrementor,
					saveXis, outputAlphabet);
		}

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 1;

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.writeInt(CURRENT_SERIAL_VERSION);
		}

		private void readObject(ObjectInputStream in) throws IOException,
				ClassNotFoundException {
			@SuppressWarnings("unused")
			int version = in.readInt();
		}
	}
}
