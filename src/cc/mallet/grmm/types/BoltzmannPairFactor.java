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
public class BoltzmannPairFactor extends TableFactor {

  private double sigma;
  private Variable x1;  // The binary variable
  private Variable x2;  // The binary variable

  public BoltzmannPairFactor (Variable x1, Variable x2, double sigma)
  {
    super (new HashVarSet (new Variable[] { x1, x2 }), sigma2vals(sigma));
    this.sigma = sigma;
    this.x1 = x1;
    this.x2 = x2;
    if (x1.getNumOutcomes () != 2) {
        throw new IllegalArgumentException ("Discrete variable "+x1+" in BoltzmannUnary must be binary.");
    }
    if (x2.getNumOutcomes () != 2) {
        throw new IllegalArgumentException ("Discrete variable "+x2+" in BoltzmannUnary must be binary.");
    }
  }

    public static double[] sigma2vals (double sigma) {
	return new double[] { 1, Math.exp(sigma), Math.exp(sigma), 1 };
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
      return Double.isNaN (sigma);
  }

  public boolean equals (Object o)
  {
    if (this == o) return true;
    if (o == null || getClass () != o.getClass ()) return false;

    final BoltzmannPairFactor that = (BoltzmannPairFactor) o;

    if (sigma != that.sigma) return false;
    if (x1 != null ? !x1.equals (that.x1) : that.x1 != null) return false;
    if (x2 != null ? !x2.equals (that.x2) : that.x2 != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = new Double(sigma).hashCode();
    result = 29 * result + (x1 != null ? x1.hashCode () : 0);
    result = 29 * result + (x2 != null ? x2.hashCode () : 0);
    return result;
  }

    public String prettyOutputString ()
    {
	return x1.getLabel() + " " + x2.getLabel() + " ~ BinaryPair " + Double.toString(sigma);
    }

    public Factor multiply (Factor other) {
	Factor result = new TableFactor (this);
	result.multiplyBy (other);
	return result;
    }
}
