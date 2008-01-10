/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import cc.mallet.util.Randoms;

/**
 * $Id: UniformFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class UniformFactor extends AbstractFactor {

  private Variable var;
  private double min;
  private double max;
  private double val;

  public UniformFactor (Variable var, double min, double max)
  {
    this (var, min, max, 1.0 / (max - min));
  }

  public UniformFactor (Variable var, double min, double max, double val)
  {
    super (new HashVarSet (new Variable[] { var }));
    if (!var.isContinuous ()) throw new IllegalArgumentException ();
    if (min >= max) throw new IllegalArgumentException ();
    this.var = var;
    this.min = min;
    this.max = max;
    this.val = val;
  }


  //
  protected Factor extractMaxInternal (VarSet varSet)
  {
    throw new UnsupportedOperationException ();
  }

  public double value (Assignment assn)
  {
    double x = assn.getDouble (var);
    if ((min < x) && (x < max)) {
      return val;
    } else {
      return 0;
    }
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
      return new ConstantFactor (val * (max - min));
    }
  }

  public Factor normalize ()
  {
    val = 1.0 / (max - min);
    return this;
  }

  public Assignment sample (Randoms r)
  {
    double val = r.nextUniform (min, max);
    return new Assignment (var, val);
  }

  public boolean almostEquals (Factor p, double epsilon)
  {
    return equals (p);
  }

  public Factor duplicate ()
  {
    return new UniformFactor (var, min, max);
  }

  public boolean isNaN ()
  {
    return Double.isNaN (min) || Double.isNaN (max);
  }

  public String dumpToString ()
  {
    return toString ();
  }

  public void multiplyBy (Factor other)
  {
    if (other instanceof ConstantFactor) {
      val *= other.value (new Assignment ());
    } else {
      throw new UnsupportedOperationException ("Can't multiply uniform factor by "+other);
    }
  }

  public void divideBy (Factor other)
  {
    if (other instanceof ConstantFactor) {
      val /= other.value (new Assignment ());
    } else {
      throw new UnsupportedOperationException ("Can't divide uniform factor by "+other);
    }
  }

  public String toString ()
  {
    return "[UniformFactor "+var+" "+min+" ... " +max+" ]";
  }

  public Factor slice (Assignment assn)
  {
    if (assn.containsVar (var)) {
      return new ConstantFactor (value (assn));
    } else return duplicate ();
  }

  
}
