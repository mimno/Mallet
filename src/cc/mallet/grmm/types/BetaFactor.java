/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;


import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import cc.mallet.util.Maths;
import cc.mallet.util.Randoms;

/**
 * $Id: BetaFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class BetaFactor extends AbstractFactor {

  transient private Variable var;
  transient private double min;
  transient private double max;
  transient private double alpha;
  transient private double beta;
  transient private double beta12;

  public BetaFactor (Variable var, double alpha, double beta)
  {
    this (var, alpha, beta, 0, 1);
  }

  public BetaFactor (Variable var, double alpha, double beta, double min, double max)
  {
    super (new HashVarSet (new Variable[] { var }));
    if (!var.isContinuous ()) throw new IllegalArgumentException ();
    if (min >= max) throw new IllegalArgumentException ();
    this.var = var;
    this.min = min;
    this.max = max;
    this.alpha = alpha;
    this.beta = beta;
    setBeta12 ();
  }

  private void setBeta12 ()
  {
    beta12 = 1 / Maths.beta (alpha, beta);
  }


  protected Factor extractMaxInternal (VarSet varSet)
  {
    throw new UnsupportedOperationException ();
  }

  public double value (Assignment assn)
  {
    double pct = valueToPct (assn.getDouble (var));
    if ((0 < pct) && (pct < 1)) {
      return beta12 * Math.pow (pct, (alpha - 1.0)) * Math.pow ((1-pct), (beta -1.0));
    } else {
      return 0;
    }
  }

  private double valueToPct (double val)
  {
    return (val - min) / (max - min);
  }

  private double pctToValue (double pct)
  {
    return (pct * (max - min)) + min;
  }

  protected double lookupValueInternal (int i)
  {
    throw new UnsupportedOperationException ();
  }

  protected Factor marginalizeInternal (VarSet varsToKeep)
  {
    if (varsToKeep.contains (var)) {
      return duplicate ();
    } else {
      return new ConstantFactor (1.0);
    }
  }

  public Factor normalize ()
  {
    return this;
  }

  public Assignment sample (Randoms r)
  {
    double pct = r.nextBeta (alpha, beta);
    double val = pctToValue (pct);
    return new Assignment (var, val);
  }

  public boolean almostEquals (Factor p, double epsilon)
  {
    return equals (p);
  }

  public Factor duplicate ()
  {
    return new BetaFactor (var, alpha, beta, min, max);
  }

  public boolean isNaN ()
  {
    return Double.isNaN(alpha) || Double.isNaN(beta) || Double.isNaN (min) || Double.isNaN (max)
             || alpha <= 0 || beta <= 0;
  }

  public String dumpToString ()
  {
    return toString ();
  }

  public void multiplyBy (Factor f)
  {
    if (f instanceof ConstantFactor) {
      double val = f.value (new Assignment());
      // NormalFactor must be normalized right now...
      if (Maths.almostEquals (val, 1.0)) {
        return;  // ok, it's an identity factor
      }
    }

    throw new UnsupportedOperationException ("Can't multiply BetaFactor by "+f);
  }

  public void divideBy (Factor f)
  {
    if (f instanceof ConstantFactor) {
      double val = f.value (new Assignment());
      // NormalFactor must be normalized right now...
      if (Maths.almostEquals (val, 1.0)) {
        return;  // ok, it's an identity factor
      }
    }

    throw new UnsupportedOperationException ("Can't divide BetaFactor by "+f);
  }

  public String toString ()
  {
    return "[BetaFactor("+alpha +", "+beta +") "+var+" scale=("+min+" ... " +max+") ]";
  }

  public Factor slice (Assignment assn)
  {
    if (assn.containsVar (var)) {
      return new ConstantFactor (value (assn));
    } else return duplicate ();
  }


  // serialization nonsense

  private static final long serialVersionUID = 1L;
  private static final int SERIAL_VERSION = 1;

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    in.readInt (); // serial version
    var = (Variable) in.readObject ();
    alpha = in.readDouble ();
    beta = in.readDouble ();
    min = in.readDouble ();
    max = in.readDouble ();
  }

  private void writeObject (ObjectOutputStream out) throws IOException, ClassNotFoundException
  {
    out.defaultWriteObject ();
    out.writeInt (SERIAL_VERSION);
    out.writeObject (var);
    out.writeDouble (alpha);
    out.writeDouble (beta);
    out.writeDouble (min);
    out.writeDouble (max);
    setBeta12 ();
  }

}
