/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.constraints;

import cc.mallet.fst.SumLattice;
import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.InstanceList;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * Interface for GE constraint that considers
 * either one or two states.
 * 
 * @author Gregory Druck
 */
public interface GEConstraint {
  
  /**
   * Computes the constraint feature value
   * (over all constraint features) for FeatureVector fv
   * and a transition from state li1 to li2.
   * 
   * @param input FeatureVector on transition
   * @param inputPosition Position of input in sequence
   * @param srcIndex Source state index for transition
   * @param destIndex Destination state index for transition
   * @return Constraint feature value
   */
  double getCompositeConstraintFeatureValue(FeatureVector input, int inputPosition, int srcIndex, int destIndex);
  
  /**
   * Returns the total constraint value.
   * 
   * @return Constraint value
   */
  double getValue();
  
  /**
   * Compute expectations using cached lattices.
   * 
   * @param lattices Cached SumLattices
   * @param data Unlabeled data
   */
  void computeExpectations(ArrayList<SumLattice> lattices);
  
  
  /**
   * Zero expectation values. Called before re-computing gradient.
   */
  void zeroExpectations();
  
  
  /**
   * Sets that map between the state indices and label indices.
   * 
   * @param map StateLabelMap
   */
  void setStateLabelMap(StateLabelMap map);
  
  
  /**
   * This is used in multi-threading.  
   * 
   * @return A copy of the GEConstraint.  
   */
  GEConstraint copy();
  
  /**
   * @return true if constraint feature only considers one state
   */
  boolean isOneStateConstraint();
  
  
  /**
   * @param data Unlabeled data
   * @return Returns a bitset of the size of the data, with the bit set if a constraint feature fires in that instance.
   */
  BitSet preProcess(InstanceList data);
  
  /**
   * Gives the constraint the option to do some caching
   * using only the FeatureVector.  For example, the
   * constrained input features could be cached.
   * 
   * @param input FeatureVector input
   */
  void preProcess(FeatureVector input);

}
