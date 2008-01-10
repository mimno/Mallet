/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;


import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import cc.mallet.grmm.util.Flops;
import cc.mallet.types.Matrix;
import cc.mallet.types.Matrixn;
import cc.mallet.util.Maths;

/**
 * Created: Jan 4, 2006
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TableFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class TableFactor extends AbstractTableFactor {


  public static DiscreteFactor multiplyAll (Factor[] phis)
  {
    return multiplyAll (Arrays.asList (phis));
  }


  /**
   * Returns the product of a collection of multinomial potentials.
   */
  /// xxx once there are other types of potentials, this will need to
  /// be refactored into a Factors static-utilities class.
  public static AbstractTableFactor multiplyAll (Collection phis)
  {
    if (phis.size() == 1) {
      Factor first = (Factor) phis.iterator ().next ();
      return (AbstractTableFactor) first.duplicate ();
    }

    /* Get all the variables */
    VarSet vs = new HashVarSet ();
    for (Iterator it = phis.iterator (); it.hasNext ();) {
      Factor phi = (Factor) it.next ();
      vs.addAll (phi.varSet ());
    }

    /* define a new potential over the neighbors of NODE */
    TableFactor newCPF = new TableFactor (vs);
    for (Iterator it = phis.iterator (); it.hasNext ();) {
      Factor phi = (Factor) it.next ();
      newCPF.multiplyBy (phi);
    }

    return newCPF;
  }


  public TableFactor (Variable var)
  {
    super (var);
  }

  public TableFactor (Variable var, double[] values)
  {
    super (var, values);
  }

  public TableFactor ()
  {
  }

  public TableFactor (BidirectionalIntObjectMap varMap)
  {
    super (varMap);
  }

  public TableFactor (Variable allVars [])
  {
    super (allVars);
  }

  public TableFactor (Collection allVars)
  {
    super (allVars);
  }

  public TableFactor (Variable[] allVars, double[] probs)
  {
    super (allVars, probs);
  }

  public TableFactor (VarSet allVars, double[] probs)
  {
    super (allVars, probs);
  }

  public TableFactor (Variable[] allVars, Matrix probsIn)
  {
    super (allVars, probsIn);
  }

  public TableFactor (AbstractTableFactor in)
  {
    super (in);
    probs = (Matrix) in.getValueMatrix ().cloneMatrix ();
  }

  public TableFactor (VarSet allVars, Matrix probsIn)
  {
    super (allVars, probsIn);
  }

  public TableFactor (AbstractTableFactor ptl, double[] probs)
  {
    super (ptl, probs);
  }


  /**
   * **********************************************************************
   */

  void setAsIdentity ()
  {
    setAll (1.0);
  }

  public Factor duplicate ()
  {
    return new TableFactor (this);
  }

  protected AbstractTableFactor createBlankSubset (Variable[] vars)
  {
    return new TableFactor (vars);
  }

  /**
   * Multiplies every entry in the potential by a constant
   * such that all the entries sum to 1.
   */
  public Factor normalize ()
  {
    Flops.increment (2 * probs.numLocations ());
    probs.oneNormalize ();
    return this;
  }

  public double sum ()
  {
    Flops.increment (probs.numLocations ());
    return probs.oneNorm ();
  }

  public double logValue (AssignmentIterator it)
  {
    Flops.log ();
    return Math.log (rawValue (it.indexOfCurrentAssn ()));
  }

  public double logValue (Assignment assn)
  {
    Flops.log ();
    return Math.log (rawValue (assn));
  }

  public double logValue (int loc)
  {
    Flops.log ();
    return Math.log (rawValue (loc));
  }

  public double value (Assignment assn)
  {
    return rawValue (assn);
  }

  public double value (int loc)
  {
    return rawValue (loc);
  }

  public double value (AssignmentIterator assn)
  {
    return rawValue (assn.indexOfCurrentAssn ());
  }

  protected Factor marginalizeInternal (AbstractTableFactor result)
  {

    result.setAll (0.0);

    int[] projection = largeIdxToSmall (result);

    /* Add each element of the single array of the large potential
to the correct element in the small potential. */
    int numLocs = probs.numLocations ();
    for (int largeLoc = 0; largeLoc < numLocs; largeLoc++) {

      /* Convert a single-index from this distribution to
 one for the smaller distribution */
      int smallIdx = projection[largeLoc];

      /* Whew! Now, add it in. */
      double oldValue = this.probs.valueAtLocation (largeLoc);
      result.probs.incrementSingleValue (smallIdx, oldValue);
    }

    Flops.increment (numLocs);

    return result;
  }

  // Does destructive multiplication on this, assuming this has all
// the variables in pot.
  protected void multiplyByInternal (DiscreteFactor ptl)
  {
    int[] projection = largeIdxToSmall (ptl);
    int numLocs = probs.numLocations ();
    for (int singleLoc = 0; singleLoc < numLocs; singleLoc++) {
      int smallIdx = projection[singleLoc];
      double prev = this.probs.valueAtLocation (singleLoc);
      double newVal = ptl.value (smallIdx);
      this.probs.setValueAtLocation (singleLoc, prev * newVal);
    }
    Flops.increment (numLocs);
  }

  // Does destructive divison on this, assuming this has all
  // the variables in pot.
  protected void divideByInternal (DiscreteFactor ptl)
  {
    int[] projection = largeIdxToSmall (ptl);
    int numLocs = probs.numLocations ();
    for (int singleLoc = 0; singleLoc < numLocs; singleLoc++) {
      int smallIdx = projection[singleLoc];
      double prev = this.probs.valueAtLocation (singleLoc);
      double newVal = ptl.value (smallIdx);
      double product = prev / newVal;
      /* by convention, let dividing by zero just return 0 */
      if (Maths.almostEquals (newVal, 0)) {
        product = 0;
      }
      this.probs.setValueAtLocation (singleLoc, product);
    }
    Flops.increment (numLocs);
  }

  // Does destructive addition on this, assuming this has all
// the variables in pot.
  protected void plusEqualsInternal (DiscreteFactor ptl)
  {
    int[] projection = largeIdxToSmall (ptl);
    int numLocs = probs.numLocations ();
    for (int singleLoc = 0; singleLoc < numLocs; singleLoc++) {
      int smallIdx = projection[singleLoc];
      double prev = this.probs.valueAtLocation (singleLoc);
      double newVal = ptl.value (smallIdx);
      this.probs.setValueAtLocation (singleLoc, prev + newVal);
    }
    Flops.increment (numLocs);
  }

  protected double rawValue (Assignment assn)
  {
    int numVars = getNumVars ();
    int[] indices = new int[numVars];
    for (int i = 0; i < numVars; i++) {
      Variable var = getVariable (i);
      indices[i] = assn.get (var);
    }

    double value = rawValue (indices);
    return value;
  }

  private double rawValue (int[] indices)
  {
    // handle non-occuring indices specially, for default value is -Inf in log space.
    int singleIdx = probs.singleIndex (indices);
    return rawValue (singleIdx);
  }

  protected double rawValue (int singleIdx)
  {
    int loc = probs.location (singleIdx);
    if (loc < 0) {
      return 0;
    } else {
      return probs.valueAtLocation (loc);
    }
  }

  public void exponentiate (double power)
  {
    for (int loc = 0; loc < probs.numLocations (); loc++) {
      double oldVal = probs.valueAtLocation (loc);
      double newVal = Math.pow (oldVal, power);
      probs.setValueAtLocation (loc, newVal);
    }
    Flops.pow (probs.numLocations ());
  }

  /*
  protected AbstractTableFactor ensureOperandCompatible (AbstractTableFactor ptl)
  {
    if (!(ptl instanceof TableFactor)) {
      return new TableFactor (ptl);
    } else {
      return ptl;
    }
  }
  */

  public void setLogValue (Assignment assn, double logValue)
  {
    Flops.exp ();
    setRawValue (assn, Math.exp (logValue));
  }

  public void setLogValue (AssignmentIterator assnIt, double logValue)
  {
    Flops.exp ();
    setRawValue (assnIt, Math.exp (logValue));
  }

  public void setValue (AssignmentIterator assnIt, double value)
  {
    setRawValue (assnIt, value);
  }

  public void setLogValues (double[] vals)
  {
    Flops.exp (vals.length);
    for (int i = 0; i < vals.length; i++) {
      setRawValue (i, Math.exp (vals[i]));
    }
  }

  public void setValues (double[] vals)
  {
    for (int i = 0; i < vals.length; i++) {
      setRawValue (i, vals[i]);
    }
  }

  public void timesEquals (double v)
  {
    Flops.increment (probs.numLocations ());
    probs.timesEquals (v);
  }

  protected void plusEqualsAtLocation (int loc, double v)
  {
    Flops.increment (1);
    double oldVal = valueAtLocation (loc);
    setRawValue (loc, oldVal + v);
  }

  public Matrix getValueMatrix ()
  {
    return probs;
  }

  public Matrix getLogValueMatrix ()
  {
    Flops.log (probs.numLocations ());
    Matrix logProbs = (Matrix) probs.cloneMatrix ();
    for (int loc = 0; loc < probs.numLocations (); loc++) {
      logProbs.setValueAtLocation (loc, Math.log (logProbs.valueAtLocation (loc)));
    }
    return logProbs;
  }

  public double valueAtLocation (int idx)
  {
    return probs.valueAtLocation (idx);
  }


  /**
   * Creates a new potential from another by restricting it to a given assignment.
   *
   * @param var      Variable the new potential will be over
   * @param observed Evidence to restrict to.  Must give values for all variables in ptl.varSet() except for var.
   * @return A DiscretePotential over var
   */
  protected Factor slice_onevar (Variable var, Assignment observed)
  {
    double[] vals = new double [var.getNumOutcomes ()];
    for (int i = 0; i < var.getNumOutcomes (); i++) {
      Assignment toAssn = new Assignment (var, i);
      Assignment union = Assignment.union (toAssn, observed);
      vals[i] = value (union);
    }

    return new TableFactor (var, vals);
  }

  protected Factor slice_twovar (Variable v1, Variable v2, Assignment observed)
  {
    int N1 = v1.getNumOutcomes ();
    int N2 = v2.getNumOutcomes ();
    int[] szs = new int[]{N1, N2};

    Variable[] varr = new Variable[] { v1, v2 };
    int[] outcomes = new int[2];
    double[] vals = new double [N1 * N2];

    for (int i = 0; i < N1; i++) {
      outcomes[0] = i;
      for (int j = 0; j < N2; j++) {
        outcomes[1] = j;
        Assignment toVars = new Assignment (varr, outcomes);
        Assignment assn = Assignment.union (toVars, observed);
        int idx = Matrixn.singleIndex (szs, new int[]{i, j}); // Inefficient, but much less error prone
        vals[idx] = value (assn);
      }
    }

    return new TableFactor (new Variable[]{v1, v2}, vals);
  }

  protected Factor slice_general (Variable[] vars, Assignment observed)
  {
    VarSet toKeep = new HashVarSet (vars);
    toKeep.removeAll (observed.varSet ());
    double[] vals = new double [toKeep.weight ()];

    AssignmentIterator it = toKeep.assignmentIterator ();
    while (it.hasNext ()) {
      Assignment union = Assignment.union (observed, it.assignment ());
      vals[it.indexOfCurrentAssn ()] = value (union);
      it.advance ();
    }

    return new TableFactor (toKeep, vals);
  }

  public static TableFactor makeFromLogValues (VarSet domain, double[] vals)
  {
    double[] vals2 = new double [vals.length];
    for (int i = 0; i < vals.length; i++) {
      vals2[i] = Math.exp (vals[i]);
    }
    return new TableFactor (domain, vals2);
  }

  public AbstractTableFactor recenter ()
  {
    int loc = argmax ();
    double val = valueAtLocation (loc);
    timesEquals (1.0 / val);
    return this;
  }
}
