/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.Set;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import cc.mallet.grmm.inference.Utils;


/**
 * A clique that uses very little time and memory based on the flyweight
 *  pattern, in the same way as BitVarSet.  This implementation uses an
 *  ArrayList of indices, and is likely to be more memory-efficient when the
 *  Universe is very, very large.
 *
 * @author Charles Sutton
 * @version $Id: ListVarSet.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class ListVarSet extends AbstractSet implements VarSet, Serializable {

  transient private Universe universe;
  transient private TIntArrayList included;

  public ListVarSet (Universe universe, Collection included)
  {
    this.universe = universe;
    this.included = new TIntArrayList (included.size ());

    java.util.Iterator it = included.iterator();
    while (it.hasNext()) {
      this.included.add (universe.getIndex ((Variable) it.next ()));
    }

    this.included.sort ();
  }

  public ListVarSet (VarSet vsOld)
  {
    this (vsOld.get(0).getUniverse (), vsOld);
  }

  public boolean add (Object o)
  {
    int idx = universe.getIndex ((Variable) o);
    if (idx == -1)
      throw new UnsupportedOperationException();
    included.add (idx);
    included.sort ();
    return true;
  }

  public Variable get(int idx)
  {
    int gidx = included.get (idx);
    return universe.get (gidx);
  }

  public Variable[] toVariableArray()
  {
    return (Variable[]) toArray (new Variable[0]);
  }


// FIXME cache not updated on changes to the clique
	private int cachedWeight = -1;

  public int weight()
  {
		if (cachedWeight == -1) {
			int weight = 1;
			ListVarSet.Iterator it = new ListVarSet.Iterator ();
			while (it.hasNext()) {
				Variable var = (Variable) it.next();
				weight *= var.getNumOutcomes();
			}
			cachedWeight = weight;
		}

    return cachedWeight;
  }


  public AssignmentIterator assignmentIterator()
  {
    return new DenseAssignmentIterator (this);
  }


  public int size()
  {
    return included.size ();
  }


  public boolean isEmpty()
  {
    return included.isEmpty();
  }


  public boolean contains(Object o)
  {
    return included.contains (universe.getIndex ((Variable) o));
  }


  private class Iterator implements java.util.Iterator {

    int nextIdx;

    public Iterator () { nextIdx = 0; }

    public boolean hasNext()
    {
      return (nextIdx < included.size ());
    }

    public Object next()
    {
      int thisIdx = nextIdx;
      nextIdx++;
      return universe.get (included.get (thisIdx));
    }

    public void remove()
    {
      throw new UnsupportedOperationException("Removal from BitSetClique not permitted");
    }

  }

  public java.util.Iterator iterator()
  {
    return new ListVarSet.Iterator ();
  }


  public boolean equals (Object o)
  {
    if (this == o) return true;
    if (!(o instanceof VarSet)) return false;

    VarSet vs = (VarSet) o;
    return (vs.size () == size()) && containsAll (vs);
  }

  public int hashCode ()
  {
    int result = 39;
    for (int vi = 0; vi < size(); vi++) {
      result = 59 * result + get(vi).hashCode ();
    }

    return result;
  }

  public VarSet intersection (VarSet c)
  {
    return Utils.defaultIntersection (this, c);
  }

  public void clear()
  {
    included.clear();
  }

  public String toString ()
  {
    String foo = "(C";
    ListVarSet.Iterator it = new ListVarSet.Iterator ();
    while (it.hasNext()) {
      Variable var = (Variable) it.next();
      foo = foo + " " + var;
    }
    foo = foo + ")";
    return foo;
  }

  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
    out.writeObject (universe);
    out.writeObject (included.toNativeArray ());
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
    universe = (Universe) in.readObject ();
    int[] vals = (int[]) in.readObject ();
    included = new TIntArrayList (vals);
  }

}
