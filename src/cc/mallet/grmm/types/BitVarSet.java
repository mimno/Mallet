/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit). 
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

import cc.mallet.grmm.inference.Utils;

/**
 * A clique that uses very little time and memory based on the flyweight
 *  pattern.  The owner supplies an Alphabet of vertices and a BitSet,
 *  and the clique contains the vertices in the Alphabet, masked by the BitSet.
 *
 * @author Charles Sutton
 * @version $Id: BitVarSet.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $ 
 */
public class BitVarSet extends AbstractSet implements VarSet {

  private Universe universe;
  private BitSet bitset;

  /**
   * Creates a BitSet clique given an alphabet of Variables,
   *  and a bitset that says which variables in the alphabet
   *  to include in the clique.  Neither is copied, but
   *  neither is modified, either.
   *
   * @param universe
   * @param included Bit mask that indicates which variables to include
   */
  public BitVarSet (Universe universe, BitSet included)
  {
    this.universe = universe;
    this.bitset = included;

// 	System.out.println("vertexMap: " + vertexMap);
// 	System.out.println("bitSet: " + bitset);
	

  }

  public BitVarSet (Universe universe, Collection included)
  {
    this.universe = universe;
    this.bitset = new BitSet (universe.size ());

    java.util.Iterator it = included.iterator();
    while (it.hasNext()) {
      bitset.set (universe.getIndex ((Variable) it.next()));
    }
  }

  public BitVarSet (VarSet vsOld)
  {
    this (vsOld.get(0).getUniverse (), vsOld);
  }

  public boolean add (Object o)
  {
    int idx = universe.getIndex ((Variable) o);
    if (idx == -1)
      throw new UnsupportedOperationException();
    bitset.set (idx);
    return true;
  }

  public Variable get(int idx)
  {
	  int i,mapIdx = 0;
	
    for ( i = 0, mapIdx = bitset.nextSetBit (0)  ; i<idx; i++) 
	{
		mapIdx = bitset.nextSetBit (mapIdx+1);
		
		if (mapIdx == -1)
			throw new IndexOutOfBoundsException("Index "+idx+" in BitSetClique");
    }
	
	//System.out.println("["+idx+"]("+mapIdx+")"+vertexMap.lookupObject (mapIdx));

    return universe.get (mapIdx);
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
			Iterator it = new Iterator();
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
    return bitset.cardinality();
  }


  public boolean isEmpty()
  {
    return bitset.isEmpty();
  }


  public boolean contains(Object o)
  {
    return bitset.get(universe.getIndex ((Variable) o));
  }


  private class Iterator implements java.util.Iterator {

    int nextIdx;

    public Iterator () { nextIdx = bitset.nextSetBit (0); }

    public boolean hasNext()
    {
      return (nextIdx >= 0);
    }

    public Object next()
    {
      int thisIdx = nextIdx;
      nextIdx = bitset.nextSetBit (thisIdx + 1);
      return universe.get (thisIdx);
    }

    public void remove()
    {
      throw new UnsupportedOperationException("Removal from BitSetClique not permitted");
    }

  }

  public java.util.Iterator iterator()
  {
    return new Iterator();
  }

  public int hashCode ()
  {
    return bitset.hashCode ();
  }

  public boolean containsAll(Collection c)
  {
    if (c instanceof BitVarSet) {
      return containsAll ((BitVarSet) c);
    } else {
      return super.containsAll (c);
    }
  }

  /**
   * Efficient version of containsAll() for BitSetCliques.
   */
  public boolean containsAll (BitVarSet bsc)
  {
		assert universe == bsc.universe;
		for(int i=bsc.bitset.nextSetBit(0); i>=0; i=bsc.bitset.nextSetBit(i+1)) {
			if (!bitset.get (i)) {
				return false;
			}
		}
		return true;
  }

  public VarSet intersection (VarSet c) {
    if (c instanceof BitVarSet) {
      // Efficient implementation
      BitVarSet bsc = (BitVarSet) c;
      BitSet newBitSet = (BitSet) bitset.clone();
      newBitSet.and (bsc.bitset);
      return new BitVarSet (universe, newBitSet);
    } else {
      return Utils.defaultIntersection (this, c);
    }
  }

  /**
   * Returns the number of variables in the intersection between this clique and other.
   *  Equivalent to <tt>intersection(bsc).size()</tt>, but more efficient.
   * @param bsc Other clique to intersect with
   */
  public int intersectionSize (BitVarSet bsc)
  {
		assert universe == bsc.universe;
		int size = 0;
		for(int i=bsc.bitset.nextSetBit(0); i>=0; i=bsc.bitset.nextSetBit(i+1)) {
			if (bitset.get (i)) {
				size++;
			}
		}
		return size;
  }

  public void clear()
  {
    bitset.clear();
  }


  public boolean hasLabel()
  {
    return true;
  }


  public String getLabel()
  {
    return toString ();
  }

  public String toString ()
  {
    String foo = "(C";
    Iterator it = new Iterator ();
    while (it.hasNext()) {
      Variable var = (Variable) it.next();
      foo = foo + " " + var;
    }
    foo = foo + ")";
    return foo;
  }

  public void setLabel(String s)
  {
    throw new UnsupportedOperationException();
  }

  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
  }

}
