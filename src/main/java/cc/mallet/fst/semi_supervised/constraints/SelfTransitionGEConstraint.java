/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.constraints;

import java.util.ArrayList;
import java.util.BitSet;

import cc.mallet.fst.SumLattice;
import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/** 
 * GE Constraint on the probability of self-transitions in the FST.
 * 
 * @author Gregory Druck
 */
public class SelfTransitionGEConstraint implements GEConstraint {

  private double selfTransProb;
  private double numTokens;
  private double expectation;
  private double weight;
  
  
  /**
   * @param selfTransProb Probability of self-transition
   * @param weight Weight of this constraint in the objective function
   */
  public SelfTransitionGEConstraint(double selfTransProb, double weight) {
    this.selfTransProb = selfTransProb;
    this.weight = weight;
    this.numTokens = 0;
    this.expectation = 0;
  }
  
  private SelfTransitionGEConstraint(double selfTransProb, double weight, double numTokens, double expectation) {
    this.selfTransProb = selfTransProb;
    this.weight = weight;
    this.numTokens = numTokens;
    this.expectation = expectation;
  }
  
  public GEConstraint copy() {
    return new SelfTransitionGEConstraint(selfTransProb, weight, numTokens, expectation);
  }
  
  public boolean isOneStateConstraint() {
    return false;
  }
  
  public void setStateLabelMap(StateLabelMap map) {}
  
  // no pre-processing possible here
  public void preProcess(FeatureVector fv) {}
  
  public BitSet preProcess(InstanceList data) {
    // count number of tokens
    BitSet bitSet = new BitSet(data.size());
    bitSet.set(0, data.size(), true);
    for (Instance instance : data) {
      FeatureVectorSequence fvs = (FeatureVectorSequence)instance.getData();
      this.numTokens += fvs.size();
    } 
    return bitSet;
  }    
  
  public double getCompositeConstraintFeatureValue(FeatureVector fv, int ip, int si1, int si2) {
    if (si1 == si2) {
      return this.weight * (selfTransProb / expectation);
    }
    else {
      return this.weight * ((1-selfTransProb) / (numTokens - expectation));
    }
  }

  public double getValue() {
    double selfTransEx = this.expectation / this.numTokens;
    if (selfTransProb == 1) {
      return weight * Math.log(selfTransEx);
    }
    else if (selfTransProb == 0) {
      return weight * Math.log(1-selfTransEx);
    }
    
    return weight * (selfTransProb * (Math.log(selfTransEx) - Math.log(selfTransProb))
      + ((1-selfTransProb) * (Math.log(1-selfTransEx)-Math.log(1-selfTransProb))));
  }

  public void zeroExpectations() {
    this.expectation = 0;
  }
  
  public void computeExpectations(ArrayList<SumLattice> lattices) {
    double[][][] xis;
    for (int i = 0; i < lattices.size(); i++) {
      SumLattice lattice = lattices.get(i);
      xis = lattice.getXis();
      int numStates = xis[0].length;
      FeatureVectorSequence fvs = (FeatureVectorSequence)lattice.getInput();
      for (int ip = 0; ip < fvs.size(); ++ip) {
        for (int si = 0; si < numStates; si++) {
          this.expectation += Math.exp(xis[ip][si][si]);
        }
      }
    }
    System.err.println("Self transition expectation: " + (this.expectation/this.numTokens));
  }
}
