/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.THashMap;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.ArrayList;

/**
 * A global mapping between variables and indices.
 * All variables belong to exactly one universe.
 * $Id: Universe.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class Universe implements Serializable {

  private BidirectionalIntObjectMap variableAlphabet;

  public static Universe DEFAULT = new Universe ();

  public Universe ()
  {
    variableAlphabet = new BidirectionalIntObjectMap ();
  }

  public static void resetUniverse()
  {
      DEFAULT = new Universe();
      allProjectionCaches = new THashMap();
  }

  public int add (Variable var)
  {
    return variableAlphabet.lookupIndex (var, true);
  }

  public Variable get (int idx)
  {
    return (Variable) variableAlphabet.lookupObject (idx);
  }

  public int getIndex (Variable var)
  {
     return variableAlphabet.lookupIndex (var);
  }

  public int size ()
  {
    return variableAlphabet.size ();
  }

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
    out.writeObject (variableAlphabet.toArray ());
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();

    Object[] vars = (Object[]) in.readObject ();
    variableAlphabet = new BidirectionalIntObjectMap (vars.length);
    for (int vi = 0; vi < vars.length; vi++) {
      add ((Variable) vars[vi]);
    }
  }

  // maintaining global projection caches

  // this can get dangerous if variables are thrown away willy nilly (as in test cases) -cas

  static THashMap allProjectionCaches = new THashMap ();

  public TIntObjectHashMap lookupProjectionCache (VarSet varSet)
  {
    List sizes = new ArrayList (varSet.size ());
    for (int vi = 0; vi < varSet.size (); vi++) {
      sizes.add (varSet.get(vi).getNumOutcomes ());
    }

    TIntObjectHashMap result = (TIntObjectHashMap) allProjectionCaches.get (sizes);
    if (result == null) {
      result = new TIntObjectHashMap ();
      allProjectionCaches.put (sizes, result);
    }

    return result;
  }
}
