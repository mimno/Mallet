/* Copyright (C) 2009 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised;

import cc.mallet.fst.Transducer;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.util.Maths;

/**
 * Runs subsequence constrained forward-backward to compute the entropy of label
 * sequences. <p>
 *
 * Reference:
 * Gideon Mann, Andrew McCallum
 * "Efficient Computation of Entropy Gradient for Semi-Supervised Conditional Random Fields"
 * HLT/NAACL 2007
 *
 * @author Gideon Mann
 * @author Gaurav Chandalia
 * @author Gregory Druck
 */
public class EntropyLattice {
  // input_sequence_size + 1
  protected int latticeLength;
  // input_sequence_size
  protected int inputLength;

  // the model
  protected Transducer transducer;
  // number of states in the lattice (or the model's finite state machine)
  protected int numStates;

  // ip: input position, each node has a forward and backward factor used in the
  // forward-backward algorithm, indexed by ip, state@ip (state index / si)
  protected LatticeNode[][] nodes;

  // subsequence constrained (forward) entropy
  protected double entropy;

  /**
   * Runs constrained forward-backward. <p>
   *
   * If <tt>incrementor</tt> is null then do not update expectations due to
   * these computations. <p>
   *
   * The contribution of entropy to the expectations is multiplies by the
   * scaling factor.
   */
  public EntropyLattice(FeatureVectorSequence fvs, double[][] gammas,
                        double[][][] xis, Transducer transducer,
                        Transducer.Incrementor incrementor,
                        double scalingFactor) {
    inputLength = fvs.size();
    latticeLength = inputLength + 1;
    this.transducer = transducer;
    numStates = transducer.numStates();

    nodes = new LatticeNode[latticeLength][numStates];

    // run forward-backward and compute the entropy
    entropy = this.forwardLattice(gammas, xis);
    double backwardEntropy = this.backwardLattice(gammas, xis);
    assert(Maths.almostEquals(entropy, backwardEntropy)) : entropy + " " + backwardEntropy;
     
    if (incrementor != null) {
      // add the entropy to expectations
      this.updateCounts(fvs, gammas, xis, scalingFactor, incrementor);
    }
  }

  public double getEntropy() {
    return entropy;
  }
  
  /**
   * Computes the forward entropies (H^alpha).
   */
  public double forwardLattice(double[][] gammas, double[][][] xis) {
    // initialize entropy of start states to 0
    for (int a = 0; a < numStates; ++a) {
      this.getLatticeNode(0, a).alpha = 0;
    }
     
    for (int ip = 1; ip < latticeLength; ++ip) {
      for (int a = 0; a < numStates; ++a) {
        // position ip-1 in input sequence, state a
        LatticeNode node = this.getLatticeNode(ip, a);
        double gamma = gammas[ip][a];
        if (gamma > Transducer.IMPOSSIBLE_WEIGHT) {
          for (int b = 0; b < numStates; ++b) {
          	// position ip in input sequence, state a, coming from state b
            double xi = xis[ip-1][b][a];
            if (xi > Transducer.IMPOSSIBLE_WEIGHT) {
              // p(y_{ip-1}=b|y_{ip}=a)
              double condProb = Math.exp(xi) / Math.exp(gamma);
              node.alpha += condProb * ((xi - gamma) +
                 this.getLatticeNode(ip-1, b).alpha);
            }
          }
        }
      }
    }

		double entropy = 0.0;
		for (int a = 0; a < numStates; ++a) {
			double gamma = gammas[inputLength][a];
			double gammaProb = Math.exp(gamma);

      if (gamma > Transducer.IMPOSSIBLE_WEIGHT) {
        entropy += gammaProb * gamma;
        entropy += gammaProb * this.getLatticeNode(inputLength, a).alpha;
      }
		}
		return entropy;
  }

  /**
   * Computes the backward entropies (H^beta).
   */
  public double backwardLattice(double[][] gammas, double[][][] xis) {
    // initialize entropy of end states to 0
    for (int a = 0; a < numStates; ++a) {
      this.getLatticeNode(inputLength, a).beta = 0;
    }
     
    for (int ip = inputLength; ip >= 0; --ip) {
      for (int a = 0; a < numStates; ++a) {
      	// position ip-1 in input sequence, state a
        LatticeNode node = this.getLatticeNode(ip, a);
        double gamma = gammas[ip][a];
        if (gamma > Transducer.IMPOSSIBLE_WEIGHT) {
          for (int b = 0; b < numStates; ++b) {
          	// position ip in input sequence, state a
            double xi = xis[ip][a][b];
            if (xi > Transducer.IMPOSSIBLE_WEIGHT) {
              // p(y_{ip}=b|y_{ip-1}=a)
              double condProb = Math.exp(xi) / Math.exp(gamma);
              node.beta += condProb * ((xi - gamma) +
                this.getLatticeNode(ip+1, b).beta);
            }
          }
        }
      }
    }

		double entropy = 0.0;
		for (int a = 0; a < numStates; ++a) {
			double gamma = gammas[0][a];
			double gammaProb = Math.exp(gamma);

      if (gamma > Transducer.IMPOSSIBLE_WEIGHT) {
        entropy += gammaProb * gamma;
        entropy += gammaProb * this.getLatticeNode(0, a).beta;
      }
		}
		return entropy;
  }

  /**
   * Updates the expectations due to the entropy. <p>
   */
  private void updateCounts(FeatureVectorSequence fvs, double[][] gammas,
    double[][][] xis, double scalingFactor, Transducer.Incrementor incrementor) {
    for (int ip = 0; ip < inputLength; ++ip) {
      for (int a = 0 ; a < numStates; ++a) {
        if (nodes[ip][a] == null) {
          continue;
        }

        Transducer.State sourceState = transducer.getState(a);
        Transducer.TransitionIterator iter = sourceState.transitionIterator(fvs, ip, null, ip);
        while (iter.hasNext()) {
          int b = iter.next().getIndex();
          double xi = xis[ip][a][b];
          if (xi == Transducer.IMPOSSIBLE_WEIGHT) {
            continue;
          }
          double xiProb = Math.exp(xi);

          // This is obtained after substituting and re-arranging the equation
          // at the end of the third page of the paper into the equation of
          // d/d_theta -H(Y|x) at the end of the second page.
					// \sum_(y_i,y_{i+1})
          //      f_k(y_i,y_{i+1},x)  p(y_i, y_{i+1}) *
          //      (log p(y_i,y_{i+1}) + H^a(Y_{1..(i-1)},y_i) +
          //       H^b(Y_{(i+2)..T}|y_{i+1}))
          double constrEntropy = xiProb * (xi + nodes[ip][a].alpha +  nodes[ip+1][b].beta);
          assert(constrEntropy <= 0) : "Negative entropy should be negative! " + constrEntropy;
          // full covariance, (note: it could be positive *or* negative)
          double covContribution = constrEntropy - xiProb * entropy;
          
          assert(!Double.isNaN(covContribution))
              : "xi: " + xi + ", nodes[" + ip + "][" + a + "].alpha: " +
                nodes[ip][a].alpha + ", nodes[" + (ip+1) + "][" + b +
                "].beta: " + nodes[ip+1][b].beta;

          incrementor.incrementTransition(iter, covContribution * scalingFactor);
        }
      }
    }
  }

  public LatticeNode getLatticeNode(int ip, int si) {
		if (nodes[ip][si] == null) {
      nodes[ip][si] = new LatticeNode(ip, transducer.getState(si));
    }
		return nodes[ip][si];
	}
  
  /**
   * Contains alpha, beta values at a particular input position and state pair.
   */
  public class LatticeNode {
    public int ip;
    public Transducer.State state;
    public double alpha;
    public double beta;

    LatticeNode(int ip, Transducer.State state) {
      this.ip = ip;
      this.state = state;
      this.alpha = 0.0;
      this.beta = 0.0;
    }
  }
}
