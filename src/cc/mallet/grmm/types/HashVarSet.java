/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types;

import cc.mallet.grmm.inference.Utils;
import gnu.trove.set.hash.THashSet;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;



/**
 *  A clique is a collection of nodes in a graph that are all
 *   adjacent.  We implement it cheaply by delegating to a HashSet.
 *
 * Created: Wed Sep 17 12:50:01 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: HashVarSet.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
// xxx Perhaps this should just use an alphabet and not implement Set.
public class HashVarSet implements VarSet, Serializable {

	private THashSet verts = new THashSet();
	private ArrayList vertsList = new ArrayList ();

  /**
	 * Create an empty clique.
	 */
	public HashVarSet ()
  {
		super ();
	} // Clique constructor

	/**
	 * Create a two-clique given an edge in a graph.
	 *
	public HashVarSet (Edge e) {
		super ();
		add (e.getVertexA());
		add (e.getVertexB());
	}
  */

  /**
	 * Create a Clique given a Collection of nodes.
	 */
	public HashVarSet (Collection c)
  {
		super();
		addAll (c);
	}

	public HashVarSet (Variable[] vars)
  {
		super();
		addAll (Arrays.asList (vars));
	}

	public Variable get (int idx)
  {
		return (Variable) vertsList.get (idx);
	}

	public String getLabel () {
		return toString ();
	}

	/**
	 * Returns the intersection of two cliques.
	 */
	public VarSet intersection (VarSet c)
	{
    return Utils.defaultIntersection (this, c);
	}


// Code for delegation of java.util.AbstractSet methods to verts

  /* Can't delegate to THashMap, because in early versions of Trove (that we are frozen at)
   *  the THashMap.hashCode() isn't consistent with equals.  This is workaround, which may
   *  be removed when we upgrade Trove. */
  public int hashCode()
  {
    int ret = 39;
    for (Iterator it = vertsList.iterator (); it.hasNext ();) {
      Object o = it.next ();
      ret = 59 * ret + o.hashCode ();
    }
    return ret;
	}

	public boolean equals(Object object)
  {
		return verts.equals(object);
	}

	public boolean removeAll(Collection collection)
  {
    boolean ret = true;
    for (Iterator it = collection.iterator (); it.hasNext ();) {
      Object o = it.next ();
      ret = remove (o) & ret;
    }
    return ret;
  }
 
	public Variable[] toVariableArray () 
	{
		// Cannot just do (Variable[]) vertsList.toArray() because that
		// would cause a ClassCastException.  I suppose that's why
		// toArray is overloaded...
		return (Variable[]) vertsList.toArray (new Variable[size()]);
	}

// Code for delegation of java.util.AbstractCollection methods to verts

	public String toString() 
	{
		String val = "(C";
		for (Iterator it = iterator(); it.hasNext();) {
			val += " ";
			val += it.next().toString();
		}
		val += ")";
		return val;
	}

	public boolean addAll (Collection collection)
  {
    boolean ret = true;
    for (Iterator it = collection.iterator (); it.hasNext ();) {
      ret = ret & add (it.next (), false);
    }
    Collections.sort (vertsList);
    return ret;
  }

	/** Returns the variables in this clique as an array.  If the
	 * clique is not modified, then the ordering will remain consistent
	 * across calls.
	 */
	public Object[] toArray(Object[] objectArray)
  {
		// Using vertsList here assures that toArray() always returns the
		// same ordering. 
		return vertsList.toArray(objectArray);
	}


	/** Returns the variables in this clique as an array.  If the
	 * clique is not modified, then the ordering will remain consistent
	 * across calls.
	 */
	public Object[] toArray()
  {
		// Using vertsList here assures that toArray() always returns the
		// same ordering.
		return vertsList.toArray();
	}

	public boolean containsAll(Collection collection)
  {
		return verts.containsAll(collection);
	}

	public boolean retainAll(Collection collection)
  {
		return verts.retainAll(collection);
	}
	
// Code for delegation of java.util.HashSet methods to verts

	public Object clone()
  {
       return new THashSet(verts);
	}

	public boolean add(Object object)
  {
    return add (object, true);
  }

  public boolean add(Object object, boolean checkSorted)
  {
    if (!(object instanceof Variable)) throw new IllegalArgumentException (object.toString ());
    if (!verts.contains (object)) {
      vertsList.add (object);
	  	boolean ret = verts.add (object);
      if (checkSorted) Collections.sort (vertsList);
      return ret;
    } else { return false; }
  }

	public boolean contains(Object object)
  {
		return verts.contains(object);
	}

	public int size()
  {
		return verts.size();
	}

	// Returns the total size of a dense discrete variable over this clique.
	public int weight () {
		int tot = 1;
		for (int i = 0; i < vertsList.size(); i++) {
			Variable var = (Variable) vertsList.get (i);
			tot *= var.getNumOutcomes ();
		}
		return tot;
	}

	public Iterator iterator()
  {
		return vertsList.iterator();
	}

	public boolean remove(Object object)
  {
    vertsList.remove (object);
    return verts.remove (object);
	}

	public void clear()
  {
    vertsList.clear ();
    verts.clear();
	}

	public boolean isEmpty() {
		return verts.isEmpty();
	}

	// Iterating over assignments

	public AssignmentIterator assignmentIterator ()
	{
		return new DenseAssignmentIterator (this);
	}

  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
    out.writeInt (size());
    for (int vi = 0; vi < size(); vi++) {
      out.writeObject (get (vi));
    }
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
    int size = in.readInt ();
    for (int vi = 0; vi < size; vi++){
      Variable var = (Variable) in.readObject ();
      add (var);
    }
  }

} // Clique
