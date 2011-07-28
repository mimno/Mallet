package cc.mallet.fst.semi_supervised.pr;

import cc.mallet.fst.SumLattice;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.Transducer.State;
import cc.mallet.fst.Transducer.TransitionIterator;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Sequence;

public class SumLatticeKL implements SumLattice {
	// "ip" == "input position", "op" == "output position", "i" == "state index"
	Transducer t;
	double totalWeight;
	int latticeLength;
	double[][][] xis;
	Sequence input;

	protected SumLatticeKL() {
	}

	// If outputAlphabet is non-null, this will create a LabelVector
	// for each position in the output sequence indicating the
	// probability distribution over possible outputs at that time
	// index
	public SumLatticeKL(Transducer trans, Sequence input,
			double[] initProbs, double[] finalProbs, double[][][] xis,
			double[][][] cachedDots, 
			Transducer.Incrementor incrementor) {
		assert (xis != null) : "Need transition probabilities";
		// Initialize some structures
		this.t = trans;

		this.input = input;
		
		latticeLength = input.size() + 1;
		int numStates = t.numStates();
		this.xis = xis;

		totalWeight = 0;

		// increment initial states
		for (int i = 0; i < numStates; i++) {
			if (t.getState(i).getInitialWeight() == Transducer.IMPOSSIBLE_WEIGHT)
				continue;
			if (initProbs != null) {
				totalWeight += initProbs[i] * t.getState(i).getInitialWeight();
				if (incrementor != null)
					incrementor.incrementInitialState(t.getState(i),
							initProbs[i]);
			}
		}

		for (int ip = 0; ip < latticeLength - 1; ip++)
			for (int i = 0; i < numStates; i++) {
				State s = t.getState(i);
				TransitionIterator iter = s.transitionIterator(input, ip);
				while (iter.hasNext()) {
					State destination = iter.next();
					double weight = iter.getWeight();
					double p = xis[ip][i][destination.getIndex()];
					totalWeight += p * weight;
					if (cachedDots != null) { 
					  cachedDots[ip][i][destination.getIndex()] = weight; 
					}
					if (incrementor != null) {
						incrementor.incrementTransition(iter, p);
					}
				}
			}

		for (int i = 0; i < numStates; i++) {
			if (t.getState(i).getFinalWeight() == Transducer.IMPOSSIBLE_WEIGHT)
				continue;
			if (finalProbs != null) {
				totalWeight += finalProbs[i] * t.getState(i).getFinalWeight();
				if (incrementor != null)
					incrementor.incrementFinalState(t.getState(i),
							finalProbs[i]);
			}
		}

		assert (totalWeight > Transducer.IMPOSSIBLE_WEIGHT) : "Total weight="
				+ totalWeight;
	}

	public double[][][] getXis() {
		return xis;
	}

	public double[][] getGammas() {
		throw new UnsupportedOperationException("Not handled!");
	}

	public double getTotalWeight() {
		assert (!Double.isNaN(totalWeight));
		return totalWeight;
	}

	public double getGammaWeight(int inputPosition, State s) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public double getGammaWeight(int inputPosition, int stateIndex) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public double getGammaProbability(int inputPosition, State s) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public double getGammaProbability(int inputPosition, int stateIndex) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public double getXiProbability(int ip, State s1, State s2) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public double getXiWeight(int ip, State s1, State s2) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public int length() {
		return latticeLength;
	}

	public double getAlpha(int ip, State s) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public double getBeta(int ip, State s) {
		throw new UnsupportedOperationException("Not handled!");
	}

	public LabelVector getLabelingAtPosition(int outputPosition) {
		return null;
	}

	public Transducer getTransducer() {
		return t;
	}

  public Sequence getInput() {
    return input;
  }
}
