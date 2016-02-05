/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;


import java.util.Collection;
import java.util.Iterator;

import cc.mallet.grmm.util.Flops;
import cc.mallet.types.Matrix;
import cc.mallet.types.Matrixn;
import cc.mallet.types.SparseMatrixn;
import cc.mallet.util.Maths;

/**
 * Created: Jan 4, 2006
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: LogTableFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class LogTableFactor extends AbstractTableFactor {

  public LogTableFactor (AbstractTableFactor in)
  {
    super (in);
    probs = (Matrix) in.getLogValueMatrix ().cloneMatrix ();
  }

  public LogTableFactor (Variable var)
  {
    super (var);
  }

  public LogTableFactor (Variable[] allVars)
  {
    super (allVars);
  }

  public LogTableFactor (Collection allVars)
  {
    super (allVars);
  }

  // Create from
  //  Used by makeFromLogFactorValues
  private LogTableFactor (Variable[] vars, double[] logValues)
  {
    super (vars, logValues);
  }

  private LogTableFactor (Variable[] allVars, Matrix probsIn)
  {
    super (allVars, probsIn);
  }

  //**************************************************************************/

  public static LogTableFactor makeFromValues (Variable[] vars, double[] vals)
  {
    double[] vals2 = new double [vals.length];
    for (int i = 0; i < vals.length; i++) {
      vals2[i] = Math.log (vals[i]);
    }
    return makeFromLogValues (vars, vals2);
  }

  public static LogTableFactor makeFromLogValues (Variable[] vars, double[] vals)
  {
    return new LogTableFactor (vars, vals);
  }

  //**************************************************************************/

  void setAsIdentity ()
  {
    setAll (0.0);
  }

  public Factor duplicate ()
  {
    return new LogTableFactor (this);
  }

  protected AbstractTableFactor createBlankSubset (Variable[] vars)
  {
    return new LogTableFactor (vars);
  }

  public Factor normalize ()
  {
    double sum = logspaceOneNorm ();
    if (sum < -500)
      System.err.println ("Attempt to normalize all-0 factor "+this.dumpToString ());
    
    for (int i = 0; i < probs.numLocations (); i++) {
      double val = probs.valueAtLocation (i);
      probs.setValueAtLocation (i, val - sum);
    }
    return this;
  }

  private double logspaceOneNorm ()
  {
    double sum = Double.NEGATIVE_INFINITY; // That's 0 in log space
    for (int i = 0; i < probs.numLocations (); i++) {
      sum = Maths.sumLogProb (sum, probs.valueAtLocation (i));
    }
    Flops.sumLogProb (probs.numLocations ());
    return sum;
  }

  public double sum ()
  {
    Flops.exp ();  // logspaceOneNorm counts rest
    return Math.exp (logspaceOneNorm ());
  }

  public double logsum ()
  {
    return logspaceOneNorm ();
  }

  /**
   * Does the conceptual equivalent of this *= pot.
   * Assumes that pot's variables are a subset of
   * this potential's.
   */
  protected void multiplyByInternal (DiscreteFactor ptl)
  {
    int[] projection = largeIdxToSmall (ptl);
    int numLocs = probs.numLocations ();
    for (int singleLoc = 0; singleLoc < numLocs; singleLoc++) {
      int smallIdx = projection[singleLoc];
      double prev = this.probs.valueAtLocation (singleLoc);
      double newVal = ptl.logValue (smallIdx);
      double product = prev + newVal;
      this.probs.setValueAtLocation (singleLoc, product);
    }
    Flops.increment (numLocs);  // handle the pluses
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
      double newVal = ptl.logValue (smallIdx);
      double product = prev - newVal;
      /* by convention, let -Inf + Inf (corresponds to 0/0) be -Inf */
      if (Double.isInfinite (newVal)) {
        product = Double.NEGATIVE_INFINITY;
      }
      this.probs.setValueAtLocation (singleLoc, product);
    }
    Flops.increment (numLocs);  // handle the pluses
  }

  /**
   * Does the conceptual equivalent of this += pot.
   * Assumes that pot's variables are a subset of
   * this potential's.
   */
  protected void plusEqualsInternal (DiscreteFactor ptl)
  {
    int[] projection = largeIdxToSmall (ptl);
    int numLocs = probs.numLocations ();
    for (int singleLoc = 0; singleLoc < numLocs; singleLoc++) {
      int smallIdx = projection[singleLoc];
      double prev = this.probs.valueAtLocation (singleLoc);
      double newVal = ptl.logValue (smallIdx);
      double product = Maths.sumLogProb (prev, newVal);
      this.probs.setValueAtLocation (singleLoc, product);
    }
    Flops.sumLogProb (numLocs);
  }


  public double value (Assignment assn)
  {
    Flops.exp ();
    if (getNumVars () == 0) return 1.0;
    return Math.exp (rawValue (assn));
  }


  public double value (AssignmentIterator it)
  {
    Flops.exp ();
    return Math.exp (rawValue (it.indexOfCurrentAssn ()));
  }


  public double value (int idx)
  {
    Flops.exp ();
    return Math.exp (rawValue (idx));
  }

  public double logValue (AssignmentIterator it)
  {
    return rawValue (it.indexOfCurrentAssn ());
  }

  public double logValue (int idx)
  {
    return rawValue (idx);
  }

  public double logValue (Assignment assn)
  {
    return rawValue (assn);
  }

  protected Factor marginalizeInternal (AbstractTableFactor result)
  {

    result.setAll (Double.NEGATIVE_INFINITY);
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
      double currentValue = result.probs.singleValue (smallIdx);
      result.probs.setValueAtLocation (smallIdx,
              Maths.sumLogProb (oldValue, currentValue));

    }
    Flops.sumLogProb (numLocs);

    return result;
  }

  protected double rawValue (Assignment assn)
  {
    int numVars = getNumVars ();
    int[] indices = new int[numVars];
    for (int i = 0; i < numVars; i++) {
      Variable var = getVariable (i);
      indices[i] = assn.get (var);
    }

    return rawValue (indices);
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
      return Double.NEGATIVE_INFINITY;
    } else {
      return probs.valueAtLocation (loc);
    }
  }

  public void exponentiate (double power)
  {
    Flops.increment (probs.numLocations ());
    probs.timesEquals (power);
  }

  /*
  protected AbstractTableFactor ensureOperandCompatible (AbstractTableFactor ptl)
  {
    if (!(ptl instanceof LogTableFactor)) {
      return new LogTableFactor(ptl);
    } else {
      return ptl;
    }
  }
  */

  public void setLogValue (Assignment assn, double logValue)
  {
    setRawValue (assn, logValue);
  }

  public void setLogValue (AssignmentIterator assnIt, double logValue)
  {
    setRawValue (assnIt, logValue);
  }

  public void setValue (AssignmentIterator assnIt, double value)
  {
    Flops.log ();
    setRawValue (assnIt, Math.log (value));
  }

  public void setLogValues (double[] vals)
  {
    for (int i = 0; i < vals.length; i++) {
      setRawValue (i, vals[i]);
    }
  }

  public void setValues (double[] vals)
  {
    Flops.log (vals.length);
    for (int i = 0; i < vals.length; i++) {
      setRawValue (i, Math.log (vals[i]));
    }
  }

  // v is *not* in log space
  public void timesEquals (double v)
  {
    timesEqualsLog (Math.log (v));
  }

  private void timesEqualsLog (double logV)
  {
    Flops.increment (probs.numLocations ());
    Matrix other = (Matrix) probs.cloneMatrix ();
    other.setAll (logV);
    probs.plusEquals (other);
  }

  protected void plusEqualsAtLocation (int loc, double v)
  {
    Flops.log (); Flops.sumLogProb (1);
    double oldVal = logValue (loc);
    setRawValue (loc, Maths.sumLogProb (oldVal, Math.log (v)));
  }

  public static LogTableFactor makeFromValues (Variable var, double[] vals2)
  {
    return makeFromValues (new Variable[]{var}, vals2);
  }

  public static LogTableFactor makeFromMatrix (Variable[] vars, SparseMatrixn values)
  {
    SparseMatrixn logValues = (SparseMatrixn) values.cloneMatrix ();
    for (int i = 0; i < logValues.numLocations (); i++) {
      logValues.setValueAtLocation (i, Math.log (logValues.valueAtLocation (i)));
    }
    Flops.log (logValues.numLocations ());
    return new LogTableFactor (vars, logValues);
  }

  public static LogTableFactor makeFromLogMatrix (Variable[] vars, Matrix values)
  {
    Matrix logValues = (Matrix) values.cloneMatrix ();
    return new LogTableFactor (vars, logValues);
  }

  public static LogTableFactor makeFromLogValues (Variable v, double[] vals)
  {
    return makeFromLogValues (new Variable[]{v}, vals);
  }

  public Matrix getValueMatrix ()
  {
    Matrix logProbs = (Matrix) probs.cloneMatrix ();
    for (int loc = 0; loc < probs.numLocations (); loc++) {
      logProbs.setValueAtLocation (loc, Math.exp (logProbs.valueAtLocation (loc)));
    }
    Flops.exp (probs.numLocations ());
    return logProbs;
  }

  public Matrix getLogValueMatrix ()
  {
    return probs;
  }

  public double valueAtLocation (int idx)
  {
    Flops.exp ();
    return Math.exp (probs.valueAtLocation (idx));
  }

  protected Factor slice_onevar (Variable var, Assignment observed)
  {
    Assignment assn = (Assignment) observed.duplicate ();
    double[] vals = new double [var.getNumOutcomes ()];
    for (int i = 0; i < var.getNumOutcomes (); i++) {
      assn.setValue (var, i);
      vals[i] = logValue (assn);
    }

    return LogTableFactor.makeFromLogValues (var, vals);
  }

  protected Factor slice_twovar (Variable v1, Variable v2, Assignment observed)
  {
    Assignment assn = (Assignment) observed.duplicate ();

    int N1 = v1.getNumOutcomes ();
    int N2 = v2.getNumOutcomes ();
    int[] szs = new int[]{N1, N2};

    double[] vals = new double [N1 * N2];
    for (int i = 0; i < N1; i++) {
      assn.setValue (v1, i);
      for (int j = 0; j < N2; j++) {
        assn.setValue (v2, j);
        int idx = Matrixn.singleIndex (szs, new int[]{i, j}); // Inefficient, but much less error prone
        vals[idx] = logValue (assn);
      }
    }

    return LogTableFactor.makeFromLogValues (new Variable[]{v1, v2}, vals);
  }

  protected Factor slice_general (Variable[] vars, Assignment observed)
  {
    VarSet toKeep = new HashVarSet (vars);
    toKeep.removeAll (observed.varSet ());
    double[] vals = new double [toKeep.weight ()];

    AssignmentIterator it = toKeep.assignmentIterator ();
    while (it.hasNext ()) {
      Assignment union = Assignment.union (observed, it.assignment ());
      vals[it.indexOfCurrentAssn ()] = logValue (union);
      it.advance ();
    }

    return LogTableFactor.makeFromLogValues (toKeep.toVariableArray (), vals);
  }

  public static LogTableFactor multiplyAll (Collection phis)
  {
    /* Get all the variables */
    VarSet vs = new HashVarSet ();
    for (Iterator it = phis.iterator (); it.hasNext ();) {
      Factor phi = (Factor) it.next ();
      vs.addAll (phi.varSet ());
    }

    /* define a new potential over the neighbors of NODE */
    LogTableFactor newCPF = new LogTableFactor (vs);
    for (Iterator it = phis.iterator (); it.hasNext ();) {
      Factor phi = (Factor) it.next ();
      newCPF.multiplyBy (phi);
    }

    return newCPF;
  }

  public AbstractTableFactor recenter ()
  {
//    return (AbstractTableFactor) normalize ();
    int loc = argmax ();
    double lval = probs.valueAtLocation(loc);  // should be refactored
    timesEqualsLog (-lval);
    return this;
  }
}
