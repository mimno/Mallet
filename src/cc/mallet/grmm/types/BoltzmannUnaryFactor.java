/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import cc.mallet.util.Randoms;

/**
 * A factor over a continuous variable theta and binary variables <tt>var</tt>.
 *  such that <tt>phi(x|theta)<tt> is Potts.  That is, for fixed theta, <tt>phi(x)</tt> = 1
 *  if all x are equal, and <tt>exp^{-theta}</tt> otherwise.
 * $Id: BoltzmannUnaryFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class BoltzmannUnaryFactor extends AbstractFactor implements ParameterizedFactor {

  private Variable theta;
  private Variable var;  // The binary variable

  public BoltzmannUnaryFactor (Variable var, Variable alpha)
  {
    super (BoltzmannUnaryFactor.combineVariables (alpha, var));
    this.theta = alpha;
    this.var = var;
    if (var.getNumOutcomes () != 2) {
        throw new IllegalArgumentException ("Discrete variable "+var+" in BoltzmannUnary must be binary.");
    }
    if (!alpha.isContinuous ()) {
        throw new IllegalArgumentException ("Parameter "+alpha+" in BoltzmannUnary must be continuous.");
    }
  }

  private static VarSet combineVariables (Variable alpha, Variable var)
  {
    VarSet ret = new HashVarSet ();
    ret.add (alpha);
    ret.add (var);
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
    double alph = assn.getDouble (theta);
    double[] vals = new double[] { 0.0, -alph };
    return LogTableFactor.makeFromLogValues (var, vals);
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
    buf.append ("Potts Alpha=");
    buf.append (theta);
    buf.append (var);
    return buf.toString ();
  }

  public double sumGradLog (Factor q, Variable param, Assignment paramAssn)
  {
    if (param != theta) throw new IllegalArgumentException ();
    Factor q_xs = q.marginalize (var);
    Assignment assn = new Assignment (var, 1);
    return - q_xs.value (assn);
  }

  /*
  public double secondDerivative (Factor q, Variable param, Assignment paramAssn)
  {
    if (param != theta) throw new IllegalArgumentException ();
    Factor q_xs = q.marginalize (var);
    Assignment assn = new Assignment (var, 1);
    double p = - q_xs.value (assn);
    return p * (p - 1);
  }
  */

  public Factor duplicate ()
  {
    return new BoltzmannUnaryFactor (var, theta);
  }

  public boolean almostEquals (Factor p, double epsilon)
  {
    return equals (p);
  }

  public boolean isNaN ()
  {
    return false;
  }

  public boolean equals (Object o)
  {
    if (this == o) return true;
    if (o == null || getClass () != o.getClass ()) return false;

    final BoltzmannUnaryFactor that = (BoltzmannUnaryFactor) o;

    if (theta != null ? !theta.equals (that.theta) : that.theta != null) return false;
    if (var != null ? !var.equals (that.var) : that.var != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = (theta != null ? theta.hashCode () : 0);
    result = 29 * result + (var != null ? var.hashCode () : 0);
    return result;
  }

}
