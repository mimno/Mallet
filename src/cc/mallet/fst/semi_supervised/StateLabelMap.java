/* Copyright (C) 2008 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;

import cc.mallet.types.Alphabet;


/**
 * Maps states in the lattice to labels. <p>
 *
 * When a custom state machine is constructed while training a CRF, it is
 * possible that several states map to the same label. In this case, there will
 * be a discrepancy between the number of states used in the lattice and the
 * number of output labels (targets). Use this mapping if such an FST is used in
 * training a CRF model. <p>
 *
 * If the number of states in the lattice is expected to be equal to the number
 * of output labels, then set <tt>isOneToOneMap</tt> to <tt>true</tt> in the
 * constructor. <p>
 *
 * This map associates the state with the appropriate label (indexing is zero
 * onwards). <p>
 *
 * <b>Note:</b> Add the states to the map in the same order in which they are
 * added to the CRF while constructing the FST. This is necessary to keep a
 * correct mapping of the state indices in this map to the state indices used
 * within the CRF.
 *
 * @author Gaurav Chandalia
 */
public class StateLabelMap {
  public final static int START_LABEL = -2;
  
  // mapping labels to integers
  private Alphabet stateAlphabet;

  // mapping state names to integers
  private Alphabet labelAlphabet;

  // true if a standard FST is used (using one of the methods provided in CRF
  // class), in this case the state and label alphabets are the same
  private boolean isOneToOneMap;

  // key: index identifying a state
  // value: index identifying a label that the state maps to in the state
  // machine
  private HashMap<Integer, Integer> stateToLabel;

  // key: index identifying a label
  // value: indices of states that are associated with the label
  private HashMap<Integer, LinkedHashSet<Integer>> labelToState;

  
  public StateLabelMap(Alphabet labelAlphabet, boolean isOneToOneMap) {
    this(labelAlphabet,isOneToOneMap,-1);
  }
  
  /**
   * Initializes the state and label maps.
   *
   * <b>Note:</b> If a standard FST is used (using one of the methods
   * provided in CRF class), the state and label alphabets are the same. In this
   * case, there will be a one-to-one mapping between the states and labels.
   * Also, the <tt>addStates</tt> method can no longer be used. This is done
   * when <tt>isOneToOneMap</tt> is <tt>true</tt>.
   *
   * @param labelAlphabet Target alphabet that maps label names to integers.
   * @param isOneToOneMap True if a one to one mapping of states and labels
   *        is to be created (ignoring the start label)
   * @param startStateIndex Index of special START state or -1
   */
  public StateLabelMap(Alphabet labelAlphabet, boolean isOneToOneMap, int startStateIndex) {
    this.labelAlphabet = labelAlphabet;
    this.isOneToOneMap = isOneToOneMap;
    
    stateToLabel = new HashMap<Integer, Integer>();
    labelToState = new HashMap<Integer, LinkedHashSet<Integer>>();

    if (isOneToOneMap) {
      // use the same alphabet for state and label 
      stateAlphabet = labelAlphabet;

      int alphaCount = labelAlphabet.size();
      for (int i = 0; i < alphaCount; i++) {

        String label = (String) labelAlphabet.lookupObject(i);
        int labelIndex = labelAlphabet.lookupIndex(label, false); 

        stateToLabel.put(labelIndex, labelIndex);

        LinkedHashSet<Integer> stateIndices = new LinkedHashSet<Integer>();
        stateIndices.add(labelIndex);
        labelToState.put(labelIndex, stateIndices);
      }
    } else {
      stateAlphabet = new Alphabet();

      int alphaCount = labelAlphabet.size();
      for (int i = 0; i < alphaCount; i++) {
        String label = (String) labelAlphabet.lookupObject(i);
        labelToState.put(labelAlphabet.lookupIndex(label, false),
                          new LinkedHashSet<Integer>());
      }
    }
    
    if (startStateIndex != -1) {
      addStartState(startStateIndex);
    }
  }
  
  /**
   * If there is a special start state in the CRF
   * that is not included in the label set, then
   * we need to add it here.  Constraints can then
   * check if a state maps to the special START_LABEL,
   * and handle this appropriately.  
   * 
   * @param index Index of the special start state in the CRF.
   */
  public void addStartState(int index) {
    this.stateToLabel.put(index, START_LABEL);
  }
  
  /**
   * Returns <tt>true</tt> if there is a one-to-one mapping between the states
   * and labels and <tt>false</tt> otherwise.
   */
  public boolean isOneToOneMapping() {
    return isOneToOneMap;
  }

  /**
   * Returns the number of labels in the map.
   */
  public int getNumLabels() {
    return labelToState.size();
  }

  /**
   * Returns the number of states in the map.
   */
  public int getNumStates() {
    return stateToLabel.size();
  }

  /**
   * Returns the label (target) alphabet.
   */
  public Alphabet getLabelAlphabet() {
    return labelAlphabet;
  }

  /**
   * Returns the state alphabet.
   */
  public Alphabet getStateAlphabet() {
    return stateAlphabet;
  }

  /**
   * Returns the label index mapped to the state index.
   *
   * @param stateIndex State index.
   * @return Index of the label that is mapped to the state. Returns <tt>-1</tt>
   *         if there is no label (index) that maps to the specified state.
   */
  public int getLabelIndex(int stateIndex) {
    // since no null values are allowed in our map, directly use the get method
    Integer labelIndex = stateToLabel.get(stateIndex);
    if (labelIndex == null) {
      return -1;
    }
    return labelIndex;
  }

  /**
   * Returns the state indices that map to the label index.
   *
   * @param labelIndex Label (target) index.
   * @return Indices of the states that map to the label. Returns <tt>null</tt>
   *         if there are no states that map to the label.
   */
  public LinkedHashSet<Integer> getStateIndices(int labelIndex) {
    return labelToState.get(labelIndex);
  }

  /**
   * Adds a state to the map.
   *
   * @param stateName Name of the state.
   * @param labelName Label (target) name with which the state is associated.
   * @return The index associated with the state that was added.
   * @throws IllegalArgumentException If an invalid label name or a duplicate
   *         state name is provided.
   * @throws IllegalStateError If this method is called when there is a
   *         one-to-one mapping between the states and labels.
   */
  public int addState(String stateName, String labelName) {
    if (isOneToOneMap)
      throw new IllegalStateException("Trying to add a state when there is a " +
                                      "one to one mapping between the states " +
                                      "and labels.");

    // get the label index
    int labelIndex = labelAlphabet.lookupIndex(labelName, false);
    if (labelIndex == -1) {
      throw new IllegalArgumentException("Invalid label: " + labelName);
    }

    // add the state and get its index
    int stateIndex = stateAlphabet.lookupIndex(stateName, false);
    if (stateIndex != -1) {
      throw new IllegalArgumentException("Duplicate state: " + stateName);
    }
    stateIndex = stateAlphabet.lookupIndex(stateName, true);

    // add the indices to the label-state and state-label maps
    try {
      labelToState.get(labelIndex).add(stateIndex);
    } catch (NullPointerException npe) {
      // It is possible that a label is never seen in the training data. In that
      // case the true number of labels will not be equal to the size of the
      // label (target) alphabet until the state with the unseen label is added
      // to the label alphabet while constructing the FST, add such a label
      // here.
      LinkedHashSet<Integer> stateIndices = new LinkedHashSet<Integer>();
      stateIndices.add(stateIndex);
      labelToState.put(labelIndex, stateIndices); 
    }
    stateToLabel.put(stateIndex, labelIndex);

    return stateIndex;
  }
}
