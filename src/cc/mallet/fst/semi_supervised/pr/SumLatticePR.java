package cc.mallet.fst.semi_supervised.pr;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Logger;

import cc.mallet.fst.CRF;
import cc.mallet.fst.SumLattice;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.fst.SumLatticeFactory;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.Transducer.State;
import cc.mallet.fst.Transducer.TransitionIterator;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

public class SumLatticePR implements SumLattice {
	private static Logger logger = MalletLogger.getLogger(SumLatticePR.class.getName());

	protected double totalWeight;
	protected int latticeLength;
	protected double[][] gammas;
	protected double[][][] xis;
	protected LabelVector labelings[];
	protected Transducer transducer;
  protected LatticeNode[][] nodes;
  private Sequence input;

	public SumLatticePR(Transducer trans, int index, Sequence input, Sequence output, 
	    PRAuxiliaryModel auxModel, double[][][] cachedDots, boolean incrementConstraints, Transducer.Incrementor incrementor,  
	    LabelAlphabet outputAlphabet, boolean saveXis) {
	  
		assert (output == null || input.size() == output.size());

		// Initialize some structures
		this.input = input;
		this.transducer = trans;
		this.latticeLength = input.size() + 1;
		int numStates = transducer.numStates();
		this.nodes = new LatticeNode[latticeLength][numStates];
		this.gammas = new double[latticeLength][numStates];
		if (saveXis)
			xis = new double[latticeLength][numStates][numStates];

		double outputCounts[][] = null;
		if (outputAlphabet != null)
			outputCounts = new double[latticeLength][outputAlphabet.size()];

		for (int i = 0; i < numStates; i++) {
			for (int ip = 0; ip < latticeLength; ip++) {
				gammas[ip][i] = Transducer.IMPOSSIBLE_WEIGHT;
			}
			if (saveXis) {
				for (int j = 0; j < numStates; j++) {
					for (int ip = 0; ip < latticeLength; ip++) {
						xis[ip][i][j] = Transducer.IMPOSSIBLE_WEIGHT;
					}
				}
			}
		}

		// Forward pass
		boolean atLeastOneInitialState = false;
		for (int i = 0; i < numStates; i++) {
			double initialWeight = transducer.getState(i).getInitialWeight();
			if (initialWeight > Transducer.IMPOSSIBLE_WEIGHT) {
				getLatticeNode(0, i).alpha = initialWeight;
				atLeastOneInitialState = true;
			}
		}
		if (atLeastOneInitialState == false)
			logger.warning("There are no starting states!");

		for (int ip = 0; ip < latticeLength - 1; ip++)
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null 
				    || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT) {
					continue;
				}
				
				State s = transducer.getState(i);
        CachedDotTransitionIterator iter = 
          new CachedDotTransitionIterator((CRF.State)s,input,ip,
              null,cachedDots[ip][i]);

        auxModel.preProcess(index,ip,input);
				while (iter.hasNext()) {
					State destination = iter.next();
					LatticeNode destinationNode = getLatticeNode(ip + 1, destination.getIndex());
					destinationNode.output = iter.getOutput();
					double transitionWeight = iter.getWeight();
					transitionWeight += auxModel.getWeight(index,ip,input,iter);
					destinationNode.alpha = Transducer.sumLogProb(
							destinationNode.alpha, nodes[ip][i].alpha + transitionWeight);
				}
			}

		totalWeight = Transducer.IMPOSSIBLE_WEIGHT;
		for (int i = 0; i < numStates; i++) {
			if (nodes[latticeLength-1][i] != null) {
				totalWeight = Transducer.sumLogProb(totalWeight,
				  (nodes[latticeLength-1][i].alpha + transducer.getState(i).getFinalWeight()));
			}
		}

		if (totalWeight == Transducer.IMPOSSIBLE_WEIGHT) {
			return;
		}

		// Backward pass
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength - 1][i] != null) {
				State s = transducer.getState(i);
				nodes[latticeLength - 1][i].beta = s.getFinalWeight();
				gammas[latticeLength - 1][i] = nodes[latticeLength - 1][i].alpha
						+ nodes[latticeLength - 1][i].beta - totalWeight;
				if (incrementor != null) {
					double p = Math.exp(gammas[latticeLength - 1][i]);
					assert (p >= 0.0 && p <= 1.0 + 1e-6) : "p=" + p
							+ ", gamma=" + gammas[latticeLength - 1][i];
					incrementor.incrementFinalState(s, p);
				}
			}

		for (int ip = latticeLength - 2; ip >= 0; ip--) {
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null
						|| nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
					continue;
				State s = transducer.getState(i);
				CachedDotTransitionIterator iter = 
				  new CachedDotTransitionIterator((CRF.State)s,input,ip,
				      null,cachedDots[ip][i]);
				auxModel.preProcess(index,ip,input);
				while (iter.hasNext()) {
					State destination = iter.next();
					int j = destination.getIndex();
					LatticeNode destinationNode = nodes[ip + 1][j];
					if (destinationNode != null) {
						double transitionWeight = iter.getWeight();
						transitionWeight += auxModel.getWeight(index,ip,input,iter);

						nodes[ip][i].beta = Transducer.sumLogProb(
								nodes[ip][i].beta, destinationNode.beta
										+ transitionWeight);
						double xi = nodes[ip][i].alpha + transitionWeight
								+ nodes[ip + 1][j].beta - totalWeight;
						if (saveXis)
							xis[ip][i][j] = xi;
						if (incrementor != null || auxModel.numParameters() > 0
								|| outputAlphabet != null) {
							double p = Math.exp(xi);
							assert (p >= 0.0 && p <= 1.0 + 1e-6) : "p=" + p
									+ ", xis[" + ip + "][" + i + "][" + j
									+ "]=" + xi;
							if (incrementor != null) {
								incrementor.incrementTransition(iter, p);
							}
							if (incrementConstraints) {
							   // preprocess from above still applies
		             auxModel.incrementTransition(index, ip, input, iter, p);
							}
							if (outputAlphabet != null) {
								int outputIndex = outputAlphabet.lookupIndex(iter.getOutput(), false);
								assert (outputIndex >= 0);
								outputCounts[ip][outputIndex] += p;
							}
						}
					}
				}
				gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta
						- totalWeight;
			}
		}
		if (incrementor != null)
			for (int i = 0; i < numStates; i++) {
				double p = Math.exp(gammas[0][i]);
				assert (p >= 0.0 && p <= 1.0 + 1e-6) : "p=" + p;
				incrementor.incrementInitialState(transducer.getState(i), p);
			}
		if (outputAlphabet != null) {
			labelings = new LabelVector[latticeLength];
			for (int ip = latticeLength - 2; ip >= 0; ip--) {
				assert (Math.abs(1.0 - MatrixOps.sum(outputCounts[ip])) < 0.000001);
				labelings[ip] = new LabelVector(outputAlphabet,
						outputCounts[ip]);
			}
		}
	}
	
	 protected LatticeNode getLatticeNode(int ip, int stateIndex) {
	    if (nodes[ip][stateIndex] == null)
	      nodes[ip][stateIndex] = new LatticeNode(ip, transducer.getState(stateIndex));
	    return nodes[ip][stateIndex];
	  }

	public double[][][] getXis() {
		return xis;
	}

	public double[][] getGammas() {
		return gammas;
	}

	public double getTotalWeight() {
		assert (!Double.isNaN(totalWeight));
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
		return Math.exp(gammas[inputPosition][stateIndex]);
	}

	public double getXiProbability(int ip, State s1, State s2) {
		if (xis == null)
			throw new IllegalStateException("xis were not saved.");
		int i = s1.getIndex();
		int j = s2.getIndex();
		return Math.exp(xis[ip][i][j]);
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
		return node.alpha;
	}

	public double getBeta(int ip, State s) {
		LatticeNode node = getLatticeNode(ip, s.getIndex());
		return node.beta;
	}

	public LabelVector getLabelingAtPosition(int outputPosition) {
		if (labelings != null)
			return labelings[outputPosition];
		return null;
	}

	public Transducer getTransducer() {
		return transducer;
	}

	protected class LatticeNode {
		int inputPosition;
		State state;
		Object output;
		double alpha = Transducer.IMPOSSIBLE_WEIGHT;
		double beta = Transducer.IMPOSSIBLE_WEIGHT;

		LatticeNode(int inputPosition, State state) {
			this.inputPosition = inputPosition;
			this.state = state;
			assert (this.alpha == Transducer.IMPOSSIBLE_WEIGHT);
		}
	}
	
  public Sequence getInput() {
    return input;
  }
}
