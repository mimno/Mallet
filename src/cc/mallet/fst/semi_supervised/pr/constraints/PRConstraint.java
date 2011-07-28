/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.pr.constraints;

import cc.mallet.fst.semi_supervised.StateLabelMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.InstanceList;

import java.util.BitSet;

/**
 * Interface for GE constraint that considers
 * either one or two states.
 * 
 * @author Gregory Druck
 */
public interface PRConstraint {

  int numDimensions();
  
  double getScore(FeatureVector input, int inputPosition, int srcIndex, int destIndex, double[] parameters);
  
  void incrementExpectation(FeatureVector input, int inputPosition, int srcIndex, int destIndex, double prob);

  double getProjectionValue(double[] parameters);
  
  double getCompleteValue(double[] parameters);
  
  void getGradient(double[] parameters, double[] gradient);
  
  /**
   * Sets that map between the state indices and label indices.
   * 
   * @param map StateLabelMap
   */
  void setStateLabelMap(StateLabelMap map);
  
  /**
   * Zero expectation values. Called before re-computing gradient.
   */
  void zeroExpectations();
  
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
  
  /**
   * This is used in multi-threading.  
   * 
   * @return A copy of the GEConstraint.  
   */
  PRConstraint copy();
  
  // multi-threading
  
  void getExpectations(double[] expectations);
  
  void addExpectations(double[] expectations);
}
