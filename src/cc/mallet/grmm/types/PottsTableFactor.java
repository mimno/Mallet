/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import cc.mallet.grmm.util.Matrices;
import cc.mallet.types.Matrix;
import cc.mallet.types.SparseMatrixn;
import cc.mallet.util.Randoms;

/**
 * A factor over a continuous variable alpha and discrete variables <tt>x</tt>
 *  such that <tt>phi(x|alpha)<tt> is Potts.  That is, for fixed alpha, <tt>phi(x)</tt> = 1
 *  if all x are equal, and <tt>exp^{-alpha}</tt> otherwise.
 * $Id: PottsTableFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class PottsTableFactor extends AbstractFactor implements ParameterizedFactor {

  private Variable alpha;
  private VarSet xs;

  public PottsTableFactor (VarSet xs, Variable alpha)
  {
    super (combineVariables (alpha, xs));
    this.alpha = alpha;
    this.xs = xs;
    if (!alpha.isContinuous ()) throw new IllegalArgumentException ("alpha must be continuous");
  }

  public PottsTableFactor (Variable x1, Variable x2, Variable alpha)
  {
    super (new HashVarSet (new Variable[] { x1, x2, alpha }));
    this.alpha = alpha;
    this.xs = new HashVarSet (new Variable[] { x1, x2 });
    if (!alpha.isContinuous ()) throw new IllegalArgumentException ("alpha must be continuous");
  }

  private static VarSet combineVariables (Variable alpha, VarSet xs)
  {
    VarSet ret = new HashVarSet (xs);
    ret.add (alpha);
    return ret;
  }

  protected Factor extractMaxInternal (VarSet varSet)
  {
    throw new UnsupportedOperationException ();
  }

  protected double lookupValueInternal (int i)
  {
    throw new UnsupportedOperationException ();
  }

  protected Factor marginalizeInternal (VarSet varsToKeep)
  {
    throw new UnsupportedOperationException ();
  }

  /* Inefficient, but this will seldom be called. */
  public double value (AssignmentIterator it)
  {
    Assignment assn = it.assignment();
    Factor tbl = sliceForAlpha (assn);
    return tbl.value (assn);
  }

  private Factor sliceForAlpha (Assignment assn)
  {
    double alph = assn.getDouble (alpha);
    int[] sizes = sizesFromVarSet (xs);
    Matrix diag = Matrices.diag (sizes, alph);
    Matrix matrix = Matrices.constant (sizes, -alph);
    matrix.plusEquals (diag);
    return LogTableFactor.makeFromLogMatrix (xs.toVariableArray (), (SparseMatrixn) matrix);
  }

  private int[] sizesFromVarSet (VarSet xs)
  {
    int[] szs = new int [xs.size ()];
    for (int i = 0; i < xs.size (); i++) {
      szs[i] = xs.get (i).getNumOutcomes ();
    }
    return szs;
  }

  public Factor normalize ()
  {
    throw new UnsupportedOperationException ();
  }

  public Assignment sample (Randoms r)
  {
    throw new UnsupportedOperationException ();
  }

  public double logValue (AssignmentIterator it)
  {
    return Math.log (value (it));
  }

  public Factor slice (Assignment assn) 
  {
    Factor alphSlice = sliceForAlpha (assn);
    // recursively slice, in case assn includes some of the xs
    return alphSlice.slice (assn);
  }

  public String dumpToString ()
  {
    StringBuffer buf = new StringBuffer ();
    buf.append ("[Potts: alpha:");
    buf.append (alpha);
    buf.append (" xs:");
    buf.append (xs);
    buf.append ("]");
    return buf.toString ();
  }

  public double sumGradLog (Factor q, Variable param, Assignment theta)
  {
    if (param != alpha) throw new IllegalArgumentException ();
    Factor q_xs = q.marginalize (xs);
    double qDiff = 0.0;

    for (AssignmentIterator it = xs.assignmentIterator (); it.hasNext(); it.advance()) {
      Assignment assn = it.assignment ();
      if (!isAllEqual (assn)) {
        qDiff += -q_xs.value (it);
      }
    }
    
    return qDiff;
  }

  public double secondDerivative (Factor q, Variable param, Assignment theta)
  {
    double e_x = sumGradLog (q, param, theta);

    Factor q_xs = q.marginalize (xs);
    double e_x2 = 0.0;

    for (AssignmentIterator it = xs.assignmentIterator (); it.hasNext(); it.advance()) {
      Assignment assn = it.assignment ();
      if (!isAllEqual (assn)) {
        e_x2 += q_xs.value (it);
      }
    }

    return e_x2 - (e_x * e_x);
  }

  private boolean isAllEqual (Assignment assn)
  {
    Object val1 = assn.getObject (xs.get (0));
    for (int i = 1; i < xs.size (); i++) {
      Object val2 = assn.getObject (xs.get (i));
      if (!val1.equals (val2)) return false;
    }
    return true;
  }

  public Factor duplicate ()
  {
    return new PottsTableFactor (xs, alpha);
  }

  public boolean isNaN ()
  {
    return false;
  }

  public boolean almostEquals (Factor p, double epsilon)
  {
    return equals (p);
  }

  public boolean equals (Object o)
  {
    if (this == o) return true;
    if (o == null || getClass () != o.getClass ()) return false;

    final PottsTableFactor that = (PottsTableFactor) o;

    if (alpha != null ? !alpha.equals (that.alpha) : that.alpha != null) return false;
    if (xs != null ? !xs.equals (that.xs) : that.xs != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = (alpha != null ? alpha.hashCode () : 0);
    result = 29 * result + (xs != null ? xs.hashCode () : 0);
    return result;
  }
}
