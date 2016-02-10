/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

/**
 * A subclass of Factor in which all operations throw an UnsupportedOperationException.
 * This is useful for creating special-purpose factor classes that support only a few operations.
 * $Id: SkeletonFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class SkeletonFactor extends AbstractFactor {

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

  public boolean almostEquals (Factor p, double epsilon)
  {
    throw new UnsupportedOperationException ();
  }

  public boolean isNaN () { return false; }


  public Factor normalize ()
  {
    throw new UnsupportedOperationException ();
  }

  public Assignment sample (cc.mallet.util.Randoms r)
  {
    throw new UnsupportedOperationException ();
  }

  public Factor duplicate ()
  {
    throw new UnsupportedOperationException ();
  }

  public String dumpToString ()
  {
    return toString ();
  }

  public Factor slice (Assignment assn)
  {
    throw new UnsupportedOperationException ();
  }
}
