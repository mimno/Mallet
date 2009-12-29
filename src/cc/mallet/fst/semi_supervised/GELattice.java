package cc.mallet.fst.semi_supervised;

import java.util.BitSet;
import java.util.Iterator;

import cc.mallet.fst.Transducer;
import cc.mallet.fst.semi_supervised.GECriteria.GECriterion;

import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.MatrixOps;


/**
 * Runs the dynamic programming algorithm of [Mann and McCallum 08] for
 * computing the gradient of a Generalized Expectation constraint that
 * considers a single label of a linear chain CRF.
 * 
 * See:
 * "Generalized Expectation Criteria for Semi-Supervised Learning of Conditional Random Fields"
 * Gideon Mann and Andrew McCallum
 * ACL 2008
 *
 * @author Gregory Druck
 * @author Gaurav Chandalia
 */
public class GELattice {
  // input length + 1
  protected int latticeLength;

  // the model
  protected Transducer transducer;
  // number of states in the FST
  protected int numStates;

  // dynamic programming lattice
  protected LatticeNode[][] lattice;
  
  // cache for the gradient on each 
  protected double[][][] transGradientCache;

  public GELattice( 
      FeatureVectorSequence fvs, double[][] gammas, double[][][] xis,
      Transducer transducer, Transducer.Incrementor incrementor,
      GECriteria geCriteria, boolean check) {
    assert(incrementor != null);

    latticeLength = fvs.size() + 1;
    this.transducer = transducer;
    numStates = transducer.numStates();

    // lattice
    lattice = new LatticeNode[latticeLength][numStates];
    for (int ip = 0; ip < latticeLength; ++ip) {
      for (int a = 0; a < numStates; ++a) {
        lattice[ip][a] = new LatticeNode();
      }
    }
    
    transGradientCache = new double[latticeLength][numStates][numStates];

    StateLabelMap stateLabelMap = geCriteria.getStateLabelMap();
    int numLabels = stateLabelMap.getNumLabels();

    BitSet constraintBits = geCriteria.getConstraintBitsForInstance(fvs);
    Iterator<Integer> iter = geCriteria.getFeatureIndexIterator();
    while (iter.hasNext()) {
      int fi = iter.next();
      GECriterion constraint = geCriteria.getConstraint(fi);
      // skip if the instance doesn't have this constraint
      if (!constraintBits.get(fi)) {
        continue;
      }

      // weight of this constraint
      double constraintGamma = constraint.getWeight();

      // model expectations over labels for feature
      double[] expectation = constraint.getExpectation();
      if (!MatrixOps.isNonZero(expectation)) {
        // if label expectations are zero, then this instance won't contribute
        // anything to the weights
        return;
      }
      
      // model expectation over labels for this instance
      double[] labelExpInstance = 
        geCriteria.getExpectationForInstance(fi, fvs, gammas);

      // target distribution over labels for feature
      double[] target = constraint.getTarget();
      
      for (int li = 0; li < numLabels; ++li) {
    	// only compute the lattice 
    	// if the target expectation is greater than 0
        if (expectation[li] > 0.0 && target[li] > 0.0) {
          // create one lattice for this feature-label constraint, 
        	// run dynamic programming
          this.initLattice();
          this.runForward(stateLabelMap, gammas, xis, li, fi, fvs);
          this.runBackward(stateLabelMap, gammas, xis, li, fi, fvs);

          // used to weight the contribution of this feature-label pair to the gradient
          double targetModelExpRatio = constraint.getTargetModelExpRatio(li);
          this.updateGradientCache(fi, constraintGamma, gammas, xis, fvs,
                            targetModelExpRatio, labelExpInstance[li],
                            incrementor);
          if (check) {
            // check if lattice computations are correct
            this.check(gammas, xis, li, fi, fvs);
          }
        }
      }
    }
    // update gradient using cache
    updateGradient(fvs,incrementor); 
  }

  /**
   * Initialize the lattice (for a particular feature-label constraint), lattice
   * should have already been created.
   */
  private final void initLattice() {
    // ip: input position, si: state index
    for (int ip = 0; ip < latticeLength; ++ip) {
      for (int a = 0; a < numStates; ++a) {
        LatticeNode node = lattice[ip][a];
        for (int b = 0; b < numStates; ++b) {
          node.alpha[b] = Transducer.IMPOSSIBLE_WEIGHT;
          node.beta[b] = Transducer.IMPOSSIBLE_WEIGHT;
        }
      }
    }
  }

  private void runForward(StateLabelMap stateLabelMap, double[][] gammas,
      double[][][] xis, int li, int fi, FeatureVectorSequence fvs) {
    double featureValue;
    for (int ip = 1; ip < latticeLength; ++ip) {
      featureValue = logValueOfIndicatorFeature(fvs, fi, ip-1);

      for (int prevState = 0; prevState < numStates; ++prevState) {
        // calculate only once: \sum_y_{i-1} w_a(y_{i-1},y_i)
        double nuAlpha = Transducer.IMPOSSIBLE_WEIGHT;
        for (int prevPrevState = 0; prevPrevState < numStates; ++prevPrevState) {
          nuAlpha = Transducer.sumLogProb(nuAlpha, lattice[ip - 1][prevPrevState].alpha[prevState]);
        }
        assert (!Double.isNaN(nuAlpha));

        LatticeNode node = lattice[ip][prevState];
        double[] xi = xis[ip][prevState];
        double gamma = gammas[ip][prevState];

        for (int currState = 0; currState < numStates; ++currState) {
          node.alpha[currState] = Transducer.IMPOSSIBLE_WEIGHT;
          if (stateLabelMap.getLabelIndex(prevState) == li) {
            node.alpha[currState] = Transducer.sumLogProb(node.alpha[currState], xi[currState] + featureValue);
          }
          if (gamma == Transducer.IMPOSSIBLE_WEIGHT) {
            node.alpha[currState] = Transducer.IMPOSSIBLE_WEIGHT;
          } else {
            node.alpha[currState] = Transducer.sumLogProb(node.alpha[currState], nuAlpha
                + xi[currState] - gamma);
          }
          assert (!Double.isNaN(node.alpha[currState])) : "xi: " + xi[currState] + ", gamma: "
              + gamma + ", log(indicatorFeat): " + featureValue
              + ", nuApha: " + nuAlpha;
        }
      }
    }
  }

  private void runBackward(StateLabelMap stateLabelMap,
                               double[][] gammas, double[][][] xis,
                               int li, int fi, FeatureVectorSequence fvs) {
    double featureValue;
    for (int ip = latticeLength-2; ip >= 0; --ip) {
    	featureValue = logValueOfIndicatorFeature(fvs, fi, ip);

      for (int currState = 0; currState < numStates; ++currState) {
				// calculate only once: \sum_y_{i+1} w_b(y_i,y+i)
				double nuBeta = Transducer.IMPOSSIBLE_WEIGHT;
				for (int nextState = 0; nextState < numStates; ++nextState){
          nuBeta = Transducer.sumLogProb(nuBeta,
                                          lattice[ip+1][currState].beta[nextState]);
				}
        assert(!Double.isNaN(nuBeta));

        double gamma = gammas[ip+1][currState];

        for (int prevState = 0; prevState < numStates; ++prevState) {
          LatticeNode node = lattice[ip][prevState];
          double xi = xis[ip][prevState][currState];

          node.beta[currState] = Transducer.IMPOSSIBLE_WEIGHT;
          if (stateLabelMap.getLabelIndex(currState) == li) {
            node.beta[currState] = Transducer.sumLogProb(node.beta[currState], xi + featureValue);
          }
          if (gamma == Transducer.IMPOSSIBLE_WEIGHT) {
            node.beta[currState] = Transducer.IMPOSSIBLE_WEIGHT;
          } else {
            node.beta[currState] = Transducer.sumLogProb(node.beta[currState], nuBeta + xi - gamma);
          }
          assert(!Double.isNaN(node.beta[currState]))
              : "xi: " + xi + ", gamma: " + gamma + ", xi: " + xi +
                ", log(indicatorFeat): " + featureValue;
        }
      }
    }
  }

  /**
   * Caches expectations with respect to a single instance and constraint. 
   *
   * @param priorExpRatio labelPrior / labelExpectations.
   * @param labelExpInstance Label expectation value due to this instance.
   */
  private void updateGradientCache(double fi, double featureGamma, double[][] gammas,
    double[][][] xis, FeatureVectorSequence fvs, double priorExpRatio, double labelExpInstance,
    Transducer.Incrementor incrementor) {
    for (int ip = 0; ip < latticeLength-1; ++ip) {
      for (int prevState = 0; prevState < numStates; ++prevState) {
        LatticeNode node = lattice[ip][prevState];
        double[] xi = xis[ip][prevState];
        for (int currState = 0; currState < numStates; ++currState) {
          double covFirstTerm = Math.exp(node.alpha[currState]) + Math.exp(node.beta[currState]);
          double transProb = Math.exp(xi[currState]);
          double contribution = - priorExpRatio * (covFirstTerm - (transProb * labelExpInstance));
          transGradientCache[ip][prevState][currState] += featureGamma * contribution;
        }
      }
    }
  }
  
  /**
   * Updates the expectations due to a single instance and all constraints.
   * This saves re-computing the dot product multiple times in 
   * TransitionIterator.
   *
   * @param fvs FeatureVectorSequence
   * @param labelExpInstance Label expectation value due to this instance.
   */
  private void updateGradient(FeatureVectorSequence fvs, Transducer.Incrementor incrementor) {
    for (int ip = 0; ip < latticeLength-1; ++ip) {
      for (int currState = 0; currState < numStates; ++currState) {
        Transducer.State state = transducer.getState(currState);
        Transducer.TransitionIterator iter = state.transitionIterator(fvs, ip, null, ip);
        while (iter.hasNext()) {
          int nextState = iter.next().getIndex();
          incrementor.incrementTransition(iter, transGradientCache[ip][currState][nextState]);
        }
      }
    }
  }

  /**
   * Returns indicator value of feature at specified position in logspace. <p>
   *
   * Returns: <tt>0.0</tt> for <tt>log(1)</tt>,
   *          <tt>Transducer.IMPOSSIBLE_WEIGHT</tt> for <tt>log(0)</tt>.
   */
  public final static double logValueOfIndicatorFeature( 
      FeatureVectorSequence fvs, int fi, int ip) {
    if ((ip < 0) || (ip >= fvs.size())) {
      return Transducer.IMPOSSIBLE_WEIGHT;
    } else if (fvs.getFeatureVector(ip).value(fi) > 0.0) {
      // log(1)
      return 0.0;
    } else {
      // log(0)
      return Transducer.IMPOSSIBLE_WEIGHT;
    }
  }

  /**
   * Verifies the correctness of the lattice computations.
   */
  public void check(double[][] gammas, double[][][] xis, int li, int fi, FeatureVectorSequence fvs) {
    // sum of marginal probabilities
    double marginalProb = 0.0;
    for (int ip = 0; ip < latticeLength-1; ++ip) {
      double prob = Math.exp(gammas[ip+1][li] + logValueOfIndicatorFeature(fvs, fi, ip));
      marginalProb += prob;
    }

    double altMarginalProb = 0.0;
    for (int ip = 0; ip < latticeLength-1; ++ip) {
      double joint = 0.0;
      for (int s1 = 0; s1 < numStates; ++s1) {
        LatticeNode node = lattice[ip][s1];
        for (int s2 = 0; s2 < numStates; ++s2) {
          joint += Math.exp(node.alpha[s2]) + Math.exp(node.beta[s2]);
        }
      }
      // should be equal to marginal prob.
      assert(marginalProb - joint < 1e-6);
      altMarginalProb += joint;
    }
    altMarginalProb = altMarginalProb / (latticeLength - 1);
    // should be equal to marginal prob.
    assert(marginalProb - altMarginalProb < 1e-6);
  }

  /**
   * Contains forward-backward vectors correspoding to an input position and a
   * state index.
   */
  protected class LatticeNode {
    // ip -> input position, a vector of doubles since for each node we need to
    // keep track of the alpha, beta values of state@(ip+1)
    protected double[] alpha;
    protected double[] beta;

    public LatticeNode() {
      alpha = new double[numStates];
      beta = new double[numStates];
      for (int si = 0; si < numStates; ++si) {
        alpha[si] = Transducer.IMPOSSIBLE_WEIGHT;
        beta[si] = Transducer.IMPOSSIBLE_WEIGHT;
      }
    }
  }
}
