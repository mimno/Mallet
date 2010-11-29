/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised;

import java.util.ArrayList;

import cc.mallet.fst.CRF;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.semi_supervised.constraints.GEConstraint;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.LogNumber;

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
 * gdruck NOTE: This new version of GE Lattice that computes the gradient
 * for all constraints simultaneously! 
 *
 * @author Gregory Druck
 * @author Gaurav Chandalia
 * @author Gideon Mann
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
  // cache of dot produce between violation and 
  // constraint features
  protected LogNumber[][][] dotCache;
  
  /**
   * @param fvs Input FeatureVectorSequence
   * @param gammas Marginals over single states
   * @param xis Marginals over pairs of states
   * @param transducer Transducer
   * @param reverseTrans Source state indices for each destination state
   * @param reverseTransIndices Transition indices for each destination state
   * @param gradient Gradient to increment
   * @param constraints List of constraints
   * @param check Whether to run the debugging test to verify correctness (will be much slower if true)
   */
  public GELattice( 
      FeatureVectorSequence fvs, double[][] gammas, double[][][] xis,
      Transducer transducer, int[][] reverseTrans, int[][] reverseTransIndices, CRF.Factors gradient,
      ArrayList<GEConstraint> constraints, boolean check) {
    assert(gradient != null);

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
    
    dotCache = new LogNumber[latticeLength][numStates][numStates];
    
    // TODO maybe this should be cached?
    // Separate lists for constraints that look at one vs two states.
    ArrayList<GEConstraint> constraints1 = new ArrayList<GEConstraint>();
    ArrayList<GEConstraint> constraints2 = new ArrayList<GEConstraint>();
    
    for (GEConstraint constraint : constraints) {
      if (constraint.isOneStateConstraint()) {
        constraints1.add(constraint);
      }
      else {
        constraints2.add(constraint);
      }
    }
    
    CRF crf = (CRF)transducer;
    
    double dotEx = this.runForward(crf, constraints1, constraints2, gammas, xis, reverseTrans, fvs);
    this.runBackward(crf, gammas, xis, reverseTrans, reverseTransIndices, fvs, dotEx, gradient);
    //check(constraints,gammas,xis,fvs);
  }
  
  /**
   * Run forward pass of dynamic programming algorithm
   * 
   * @param crf CRF 
   * @param constraints1 Constraints that consider one state.
   * @param constraints2 Constraints that consider two states.
   * @param gammas Marginals over single states
   * @param xis Marginals over pairs of states
   * @param reverseTrans Source state indices for each destination state
   * @param fvs Input FeatureVectorSequence
   * @return
   */
  private double runForward(CRF crf, ArrayList<GEConstraint> constraints1, ArrayList<GEConstraint> constraints2, double[][] gammas,
      double[][][] xis, int[][] reverseTrans, FeatureVectorSequence fvs) {
    double dotEx = 0;
  
    LogNumber[] oneStateValueCache = new LogNumber[numStates];
    LogNumber nuAlpha = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
    LogNumber temp = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
    
    for (int ip = 0; ip < latticeLength-1; ++ip) {
      FeatureVector fv = fvs.get(ip);
      // speed things up by giving the constraints an 
      // opportunity to cache, for example, which 
      // constrained input features appear in this 
      // FeatureVector
      for (GEConstraint constraint : constraints1) {
        constraint.preProcess(fv);
      }
      for (GEConstraint constraint : constraints2) {
        constraint.preProcess(fv);
      }
      
      boolean[] oneStateValComputed = new boolean[numStates];
      for (int prev = 0; prev < numStates; prev++) {
        nuAlpha.set(Transducer.IMPOSSIBLE_WEIGHT,true);
        if (ip != 0) {
          int[] prevPrevs = reverseTrans[prev];
          // calculate only once: \sum_y_{i-1} w_a(y_{i-1},y_i)
          for (int ppi = 0; ppi < prevPrevs.length; ppi++) {
            nuAlpha.plusEquals(lattice[ip-1][prevPrevs[ppi]].alpha[prev]);
          }
        }

        assert (!Double.isNaN(nuAlpha.logVal));

        CRF.State prevState = (CRF.State)crf.getState(prev); 
        LatticeNode node = lattice[ip][prev];
        double[] xi = xis[ip][prev];
        double gamma = gammas[ip][prev];

        for (int ci = 0; ci < prevState.numDestinations(); ci++) {
          int curr = prevState.getDestinationState(ci).getIndex();
          double dot = 0;
          for (GEConstraint constraint : constraints2) {
            dot += constraint.getConstraintFeatureValue(fv, ip, prev, curr);
          }

          // avoid recomputing one-state constraint features #labels times
          if (!oneStateValComputed[curr]) {
            double osVal = 0;
            for (GEConstraint constraint : constraints1) {
              osVal += constraint.getConstraintFeatureValue(fv, ip, prev, curr);
            }
            if (osVal < 0) {
              dotEx += Math.exp(gammas[ip+1][curr]) * osVal;
              oneStateValueCache[curr] = new LogNumber(Math.log(-osVal),false);
            }
            else if (osVal > 0) {
              dotEx += Math.exp(gammas[ip+1][curr]) * osVal;
              oneStateValueCache[curr] = new LogNumber(Math.log(osVal),true);
            }
            else {
              oneStateValueCache[curr] = null;
            }
            oneStateValComputed[curr] = true;
          }
          
          // combine the one and two state constraint feature values
          if (dot == 0 && oneStateValueCache[curr] == null) {
            dotCache[ip][prev][curr] = null;
          }
          else if (dot == 0 && oneStateValueCache[curr] != null) {
            dotCache[ip][prev][curr] = oneStateValueCache[curr];
          }
          else {
            dotEx += Math.exp(xi[curr]) * dot;
            if (dot < 0) {
              dotCache[ip][prev][curr] = new LogNumber(Math.log(-dot),false);
            }
            else {
              dotCache[ip][prev][curr] = new LogNumber(Math.log(dot),true);
            }
            if (oneStateValueCache[curr] != null) {
              dotCache[ip][prev][curr].plusEquals(oneStateValueCache[curr]);
            }
          }
          
          // update the dynamic programming table
          if (dotCache[ip][prev][curr] != null) {
            temp.set(xi[curr],true);
            temp.timesEquals(dotCache[ip][prev][curr]);
            node.alpha[curr].plusEquals(temp);
          }
          if (gamma == Transducer.IMPOSSIBLE_WEIGHT) {
            node.alpha[curr] = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
          } else {
            temp.set(xi[curr] - gamma,true);
            temp.timesEquals(nuAlpha);
            node.alpha[curr].plusEquals(temp);
          }
          assert (!Double.isNaN(node.alpha[curr].logVal)) : "xi: " + xi[curr] + ", gamma: "
              + gamma + ", constraint feature: " + dotCache[ip][prev][curr]
              + ", nuApha: " + nuAlpha + " dot: " + dot;
        }
      }
    }
    return dotEx;
  }

  /**
   * Run backward pass of dynamic programming algorithm
   * 
   * @param crf CRF 
   * @param gammas Marginals over single states
   * @param xis Marginals over pairs of states
   * @param reverseTrans Source state indices for each destination state
   * @param reverseTransIndices Transition indices for each destination state
   * @param fvs Input FeatureVectorSequence
   * @param dotEx Expectation of constraint features dot violation terms
   * @param gradient Gradient to increment
   * @return
   */
  private void runBackward(CRF crf, double[][] gammas, double[][][] xis, int[][] reverseTrans, int[][] reverseTransIndices, 
      FeatureVectorSequence fvs, double dotEx, CRF.Factors gradient) {
    
    LogNumber nuBeta = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
    LogNumber dot = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
    LogNumber temp = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
    LogNumber temp2 = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
    LogNumber nextDot;
    
    for (int ip = latticeLength-2; ip >= 0; --ip) {
      for (int curr = 0; curr < numStates; ++curr) {

        nuBeta.set(Transducer.IMPOSSIBLE_WEIGHT,true);
        dot.set(Transducer.IMPOSSIBLE_WEIGHT,true);
        // calculate only once: \sum_y_{i+1} w_b(y_i,y+i)
        
        
        CRF.State currState = (CRF.State)crf.getState(curr);
        for (int ni = 0; ni < currState.numDestinations(); ni++){
          int next= currState.getDestinationState(ni).getIndex();
          nuBeta.plusEquals(lattice[ip+1][curr].beta[next]);
          assert(!Double.isNaN(nuBeta.logVal));

          nextDot = dotCache[ip+1][curr][next];
          if (nextDot != null) {
            double xi = xis[ip+1][curr][next];
            temp.set(xi,true);
            temp.timesEquals(nextDot);
            dot.plusEquals(temp);
          }
        }

        double gamma = gammas[ip+1][curr];

        int[] prevStates = reverseTrans[curr];
        for (int pi = 0; pi < prevStates.length; pi++) {
          int prev = prevStates[pi];
          
          CRF.State crfState = (CRF.State)crf.getState(prev);

          LatticeNode node = lattice[ip][prev];
          double xi = xis[ip][prev][curr];

          if (gamma == Transducer.IMPOSSIBLE_WEIGHT) {
            node.beta[curr] = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
          } else {
            // constraint feature values cached in Forward pass
            temp.set(dot.logVal,dot.sign);
            temp.plusEquals(nuBeta);
            temp2.set(xi-gamma,true);
            temp.timesEquals(temp2);
            node.beta[curr].plusEquals(temp);
          }
          assert(!Double.isNaN(node.beta[curr].logVal))
          : "xi: " + xi + ", gamma: " + gamma + ", xi: " + xi +
          ", log(indicatorFeat): " + dotCache[ip][curr];

          // compute and update gradient!
          double transProb = Math.exp(xi);
          double covFirstTerm = node.alpha[curr].exp() + node.beta[curr].exp();
          double contribution = (covFirstTerm - (transProb * dotEx));

          int nwi = crfState.getWeightNames(reverseTransIndices[curr][pi]).length;
          int weightsIndex;
          for (int wi = 0; wi < nwi; wi++) {
            weightsIndex = ((CRF)transducer).getWeightsIndex(crfState.getWeightNames(reverseTransIndices[curr][pi])[wi]);
            gradient.weights[weightsIndex].plusEqualsSparse (fvs.get(ip), contribution);
            gradient.defaultWeights[weightsIndex] += contribution;
          }
        }
      }
    }
  }
  
  
  /**
   * Verifies the correctness of the lattice computations.
   */
  public void check(ArrayList<GEConstraint> constraints, double[][] gammas, double[][][] xis, FeatureVectorSequence fvs) {
    // sum of marginal probabilities
    double ex1 = 0.0;
    for (int ip = 0; ip < latticeLength-1; ++ip) {
      for (int si1 = 0; si1 < numStates; si1++) {
        for (int si2 = 0; si2 < numStates; si2++) {
          double dot = 0;
          for (GEConstraint constraint : constraints) {
            dot += constraint.getConstraintFeatureValue(fvs.get(ip), ip, si1, si2);
          }
          double prob = Math.exp(xis[ip][si1][si2]);
          ex1 += prob * dot;
        }
      }
    }

    double ex2 = 0.0;
    for (int ip = 0; ip < latticeLength-1; ++ip) {
      double ex3 = 0.0;
      for (int s1 = 0; s1 < numStates; ++s1) {
        LatticeNode node = lattice[ip][s1];
        for (int s2 = 0; s2 < numStates; ++s2) {
          ex3 += node.alpha[s2].exp() + node.beta[s2].exp();
        }
      }
      // should be equal to marginal prob.
      assert(ex1 - ex3 < 1e-6) :ex1 + " " + ex3;
      ex2 += ex3;
    }
    ex2 = ex2 / (latticeLength - 1);
    // should be equal to marginal prob.
    assert(ex1 - ex2 < 1e-6) : ex1 + " " + ex2;
  }
  
  public LogNumber getAlpha(int ip, int s1, int s2) {
    return lattice[ip][s1].alpha[s2];
  }
  
  public LogNumber getBeta(int ip, int s1, int s2) {
    return lattice[ip][s1].beta[s2];
  }
  
  /**
   * Contains forward-backward vectors correspoding to an input position and a
   * state index.
   */
  protected class LatticeNode {
    // ip -> input position, a vector of doubles since for each node we need to
    // keep track of the alpha, beta values of state@(ip+1)
    protected LogNumber[] alpha;
    protected LogNumber[] beta;

    public LatticeNode() {
      alpha = new LogNumber[numStates];
      beta = new LogNumber[numStates];
      for (int si = 0; si < numStates; ++si) {
        alpha[si] = new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
        beta[si] =  new LogNumber(Transducer.IMPOSSIBLE_WEIGHT,true);
      }
    }
  }
}
