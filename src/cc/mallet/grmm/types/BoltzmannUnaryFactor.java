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
public class BoltzmannUnaryFactor extends TableFactor {

  private double theta;  // parameter
  private Variable var;  // The binary variable

  public BoltzmannUnaryFactor (Variable var, double theta)
  {
    super (var, theta2values (theta));
    this.theta = theta;
    this.var = var;
    if (var.getNumOutcomes () != 2) {
        throw new IllegalArgumentException ("Discrete variable "+var+" in BoltzmannUnary must be binary.");
    }
  }

  private static double[] theta2values (double theta)
  {
      return new double[] { 1, Math.exp(theta) };
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
      return Double.isNaN (theta);
  }

  public boolean equals (Object o)
  {
    if (this == o) return true;
    if (o == null || getClass () != o.getClass ()) return false;

    final BoltzmannUnaryFactor that = (BoltzmannUnaryFactor) o;

    if (theta != that.theta) return false;
    if (var != null ? !var.equals (that.var) : that.var != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = new Double(theta).hashCode();
    result = 29 * result + (var != null ? var.hashCode () : 0);
    return result;
  }

    public String prettyOutputString ()
    {
	return var.getLabel() + " ~ Unary " + Double.toString(theta);
    }

    public Factor multiply (Factor other) {
	Factor result = new TableFactor (this);
	result.multiplyBy (other);
	return result;
    }

}
