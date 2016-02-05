/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.constraints.ge;

import java.util.BitSet;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.InstanceList;

/**
 * Interface for expectation constraints for use with Generalized Expectation (GE).
 * 
 * @author Gregory Druck
 */

public interface MaxEntGEConstraint {

  /**
   * Computes the composite constraint feature value
   * (over all constraint features) for FeatureVector fv
   * and label label.
   * 
   * @param input input FeatureVector
   * @param label output label index
   * @return Constraint feature value
   */
  double getCompositeConstraintFeatureValue(FeatureVector input, int label);

  /**
   * Returns the total constraint value.
   * 
   * @return Constraint value
   */
  double getValue();

  /**
   * Compute expectations using provided distribution over labels.
   * 
   * @param fv FeatureVector
   * @param dist Distribution over labels
   * @param data Unlabeled data
   */
  void computeExpectations(FeatureVector fv, double[] dist, double weight);

  /**
   * Zero expectation values. Called before re-computing gradient.
   */
  void zeroExpectations();    

  /**
   * @param data Unlabeled data
   * @return Returns a bitset of the size of the data, with the bit set if 
   * a constraint feature fires in that instance.
   */
  BitSet preProcess(InstanceList data);

  /**
   * Gives the constraint the option to do some caching
   * using only the FeatureVector. For example, the
   * constrained input features could be cached.
   * 
   * @param input FeatureVector input
   */
  void preProcess(FeatureVector input);
}