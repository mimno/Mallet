/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.pr;

import java.util.ArrayList;
import java.util.Iterator;

import cc.mallet.fst.CRF;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.semi_supervised.pr.constraints.PRConstraint;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Sequence;

/**
 * Auxiliar model (q) for E-step/I-projection in Posterior Regularization (PR).
 *
 * @author Gregory Druck
 */

public class PRAuxiliaryModel extends Transducer {

  private static final long serialVersionUID = 1L;
  
  private int numParameters;
  private double[][] parameters;
  private ArrayList<PRConstraint> constraints;
  private CRF baseModel;

  public PRAuxiliaryModel(CRF baseModel, ArrayList<PRConstraint> constraints) {
    this.baseModel = baseModel;
    this.constraints = constraints;
    int index = 0;
    this.parameters = new double[constraints.size()][];
    for (PRConstraint constraint : constraints) {
      parameters[index] = new double[constraint.numDimensions()];
      index++;
      numParameters += constraint.numDimensions();
    }
  }
  
  private PRAuxiliaryModel(CRF baseModel, ArrayList<PRConstraint> constraints, double[][] parameters) {
    this.baseModel = baseModel;
    this.constraints = constraints;
    this.parameters = parameters;
    for (PRConstraint constraint : constraints) {
      numParameters += constraint.numDimensions();
    }
  }
  
  public PRAuxiliaryModel copy() {
    ArrayList<PRConstraint> copy = new ArrayList<PRConstraint>();
    for (PRConstraint constraint : constraints) {
      copy.add(constraint.copy());
    }
    
    // parameters should not be changed in copy, 
    // so we can use the same parameters array
    return new PRAuxiliaryModel(baseModel,copy,parameters);
  }
  
  public void preProcess(int index, int position, Sequence input) {
    for (PRConstraint constraint : constraints) {
      constraint.preProcess((FeatureVector)input.get(position));
    }
  }
  
  public double getValue() {
    double value = 0;
    int index = 0;
    for (PRConstraint constraint : constraints) {
      value += constraint.getAuxiliaryValueContribution(parameters[index]);
      index++;
    }
    return value;
  }
  
  public double getCompleteValueContribution() {
    double value = 0;
    int index = 0;
    for (PRConstraint constraint : constraints) {
      value += constraint.getCompleteValueContribution(parameters[index]);
      index++;
    }
    return value;
  }
  
  public void getValueGradient(double[] gradient) {
    int index = 0;
    int start = 0;
    for (PRConstraint constraint: constraints) {
      double[] constraintGradient = new double[constraint.numDimensions()];
      constraint.getGradient(parameters[index], constraintGradient);
      System.arraycopy(constraintGradient, 0, gradient, start, constraintGradient.length);
      start += constraint.numDimensions();
      index++;
    }
  }
  
  public double getWeight(int index, int position, Sequence input, TransitionIterator iter) {
    double weight = 0;
    
    int si1 = iter.getSourceState().getIndex(); 
    int si2 = iter.getDestinationState().getIndex();
    int constrIndex = 0;
    for (PRConstraint constraint : constraints) {
      weight += constraint.getScore((FeatureVector)input.get(position), position, si1, si2, parameters[constrIndex]);
      constrIndex++;
    }
    return weight;
  }
  
  public void incrementTransition(int index, int position, Sequence input, TransitionIterator iter, double prob) {
    int si1 = iter.getSourceState().getIndex(); 
    int si2 = iter.getDestinationState().getIndex();
    for (PRConstraint constraint : constraints) {
      constraint.incrementExpectations((FeatureVector)input.get(position), position, si1, si2, prob);
    }
  }
  
  public void zeroExpectations() {
    for (PRConstraint constraint : constraints) {
      constraint.zeroExpectations();
    }
  }
  
  public int numParameters() { 
    return numParameters;
  }
  
  public void getParameters(double[] params) {
    assert(params.length == numParameters);
    int start = 0;
    for (int i = 0; i < this.parameters.length; i++) {
      System.arraycopy(this.parameters[i], 0, params, start, this.parameters[i].length);
      start += this.parameters[i].length;
    }
  }

  public double getParameter(int index) {
    assert(index > 0);
    int constrIndex = 0;
    for (PRConstraint constraint : constraints) {
      if (index < constraint.numDimensions()) {
        return parameters[constrIndex][index];
      }
      constrIndex++;
      index -= constraint.numDimensions();
    }
    throw new RuntimeException("index not found: " + index);
  }

  public void setParameters(double[] params) {
    assert(params.length == numParameters);
    int start = 0;
    for (int i = 0; i < parameters.length; i++) {
      System.arraycopy(params, start, this.parameters[i], 0, this.parameters[i].length);
      start += parameters[i].length;
    }
  }

  public void setParameter(int index, double value) {
    assert(index > 0);
    int constrIndex = 0;
    for (PRConstraint constraint : constraints) {
      if (index < constraint.numDimensions()) {
        parameters[constrIndex][index] = value;
        return;
      }
      constrIndex++;
      index -= constraint.numDimensions();
    }
    throw new RuntimeException("index not found: " + index);
  }
  
  public int numConstraints() { 
    return constraints.size();
  }
  
  public PRConstraint getConstraint(int index)  {
    return constraints.get(index);
  }
  
  public CRF getBaseModel() {
    return baseModel;
  }

  @Override
  public int numStates() {
    return baseModel.numStates();
  }

  @Override
  public State getState(int index) {
    return baseModel.getState(index);
  }

  @Override
  public Iterator initialStateIterator() {
    return baseModel.initialStateIterator();
  }
}