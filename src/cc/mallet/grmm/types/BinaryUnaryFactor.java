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
 * $Id: BinaryUnaryFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class BinaryUnaryFactor extends AbstractFactor implements ParameterizedFactor {

  private Variable theta1;
  private Variable theta2;
  private Variable var;  // The binary variable

  public BinaryUnaryFactor (Variable var, Variable theta1, Variable theta2)
  {
    super (BinaryUnaryFactor.combineVariables (theta1, theta2, var));
    this.theta1 = theta1;
    this.theta2 = theta2;
    this.var = var;
    if (var.getNumOutcomes () != 2) {
        throw new IllegalArgumentException ("Discrete variable "+var+" in BoltzmannUnary must be binary.");
    }
    if (!theta1.isContinuous ()) {
        throw new IllegalArgumentException ("Parameter "+theta1+" in BinaryUnary must be continuous.");
    }
    if (!theta2.isContinuous ()) {
        throw new IllegalArgumentException ("Parameter "+theta2+" in BinaryUnary must be continuous.");
    }
  }

  private static VarSet combineVariables (Variable theta1, Variable theta2, Variable var)
  {
    VarSet ret = new HashVarSet ();
    ret.add (theta1);
    ret.add (theta2);
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
    double th1 = assn.getDouble (theta1);
    double th2 = assn.getDouble (theta2);
    double[] vals = new double[] { th1, th2 };
    return new TableFactor (var, vals);
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
    buf.append ("[BinaryUnary : var=");
    buf.append (var);
    buf.append (" theta1=");
    buf.append (theta1);
    buf.append (" theta2=");
    buf.append (theta2);
    buf.append (" ]");
    return buf.toString ();
  }

  public double sumGradLog (Factor q, Variable param, Assignment paramAssn)
  {
    Factor q_xs = q.marginalize (var);
    Assignment assn;

    if (param == theta1) {
      assn = new Assignment (var, 0);
    } else if (param == theta2) {
      assn = new Assignment (var, 1);
    } else {
      throw new IllegalArgumentException ("Attempt to take gradient of "+this+" wrt "+param+
           "but factor does not depend on that variable.");
    }

    return q_xs.value (assn);
  }

  public Factor duplicate ()
  {
    return new BinaryUnaryFactor (var, theta1, theta2);
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

    final BinaryUnaryFactor that = (BinaryUnaryFactor) o;

    if (theta1 != null ? !theta1.equals (that.theta1) : that.theta1 != null) return false;
    if (theta2 != null ? !theta2.equals (that.theta2) : that.theta2 != null) return false;
    if (var != null ? !var.equals (that.var) : that.var != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = (theta1 != null ? theta1.hashCode () : 0);
    result = 29 * result + (theta2 != null ? theta2.hashCode () : 0);
    result = 29 * result + (var != null ? var.hashCode () : 0);
    return result;
  }
}
