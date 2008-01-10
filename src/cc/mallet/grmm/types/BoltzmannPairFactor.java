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
 * $Id: BoltzmannPairFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class BoltzmannPairFactor extends AbstractFactor implements ParameterizedFactor {

  private Variable sigma;
  private Variable x1;  // The binary variable
  private Variable x2;  // The binary variable
  private VarSet xs;

  public BoltzmannPairFactor (Variable x1, Variable x2, Variable sigma)
  {
    super (new HashVarSet (new Variable[] { sigma, x1, x2 }));
    this.sigma = sigma;
    this.x1 = x1;
    this.x2 = x2;
    xs = new HashVarSet (new Variable[] { x1, x2 });
    if (x1.getNumOutcomes () != 2) {
        throw new IllegalArgumentException ("Discrete variable "+x1+" in BoltzmannUnary must be binary.");
    }
    if (x2.getNumOutcomes () != 2) {
        throw new IllegalArgumentException ("Discrete variable "+x2+" in BoltzmannUnary must be binary.");
    }
    if (!sigma.isContinuous ()) {
        throw new IllegalArgumentException ("Parameter "+sigma +" in BoltzmannUnary must be continuous.");
    }
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
    Factor tbl = sliceForSigma (assn);
    return tbl.value (assn);
  }

  private Factor sliceForSigma (Assignment assn)
  {
    double sig = assn.getDouble (sigma);
    double[] vals = new double[] { Math.exp (-sig), 1, 1, 1 };
    return new TableFactor (new Variable[] { x1, x2 }, vals);
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
    Factor sigSlice = sliceForSigma (assn);
    // recursively slice, in case assn includes some of the xs
    return sigSlice.slice (assn);
  }

  public String dumpToString ()
  {
    StringBuffer buf = new StringBuffer ();
    buf.append ("[Pair BM Factor: ");
    buf.append (x1);
    buf.append (" ");
    buf.append (x2);
    buf.append (" sigma=");
    buf.append (sigma);
    buf.append (" ]");
    return buf.toString ();
  }

  public double sumGradLog (Factor q, Variable param, Assignment paramAssn)
  {
    if (param != sigma) throw new IllegalArgumentException ();
    Factor q_xs = q.marginalize (new Variable[] { x1, x2 });
    Assignment assn = new Assignment (xs.toVariableArray (), new int[] { 0, 0 });
    return - q_xs.value (assn);
  }

  public Factor duplicate ()
  {
    return new BoltzmannPairFactor (x1, x2, sigma);
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

    final BoltzmannPairFactor that = (BoltzmannPairFactor) o;

    if (sigma != null ? !sigma.equals (that.sigma) : that.sigma != null) return false;
    if (x1 != null ? !x1.equals (that.x1) : that.x1 != null) return false;
    if (x2 != null ? !x2.equals (that.x2) : that.x2 != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = (sigma != null ? sigma.hashCode () : 0);
    result = 29 * result + (x1 != null ? x1.hashCode () : 0);
    result = 29 * result + (x2 != null ? x2.hashCode () : 0);
    return result;
  }

}
