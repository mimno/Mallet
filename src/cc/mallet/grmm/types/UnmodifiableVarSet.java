/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import java.util.*;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Created: Dec 15, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: UnmodifiableVarSet.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class UnmodifiableVarSet implements VarSet, Serializable {

  transient private VarSet subVarSet;

  public UnmodifiableVarSet (VarSet subVarSet)
  {
    this.subVarSet = subVarSet;
  }

  public Variable get (int idx)
  {
    return subVarSet.get (idx);
  }

  public Set vertices ()
  {
    return Collections.unmodifiableSet (subVarSet);
  }

  public Variable[] toVariableArray ()
  {
    return subVarSet.toVariableArray ();
  }

  public int weight ()
  {
    return subVarSet.weight ();
  }

  public AssignmentIterator assignmentIterator ()
  {
    return subVarSet.assignmentIterator ();
  }

  public VarSet intersection (VarSet c)
  {
    return subVarSet.intersection (c);
  }

  public int size ()
  {
    return subVarSet.size ();
  }

  public boolean isEmpty ()
  {
    return subVarSet.isEmpty ();
  }

  public boolean contains (Object o)
  {
    return subVarSet.contains (o);
  }

  public Iterator iterator ()
  {
    return subVarSet.iterator ();
  }

  public Object[] toArray ()
  {
    return subVarSet.toArray ();
  }

  public Object[] toArray (Object[] objects)
  {
    return subVarSet.toArray (objects);
  }

  public boolean add (Object o)
  {
    throw new UnsupportedOperationException ("Attempt to modify unmodifiable clique: "+this);
  }

  public boolean remove (Object o)
  {
    throw new UnsupportedOperationException ("Attempt to modify unmodifiable clique: "+this);
  }

  public boolean containsAll (Collection collection)
  {
    return subVarSet.containsAll (collection);
  }

  public boolean addAll (Collection collection)
  {
    throw new UnsupportedOperationException ("Attempt to modify unmodifiable clique: "+this);
  }

  public boolean retainAll (Collection collection)
  {
    throw new UnsupportedOperationException ("Attempt to modify unmodifiable clique: "+this);
  }

  public boolean removeAll (Collection collection)
  {
    throw new UnsupportedOperationException ("Attempt to modify unmodifiable clique: "+this);
  }

  public void clear ()
  {

    throw new UnsupportedOperationException ("Attempt to modify unmodifiable clique: "+this);
  }

  public boolean equals (Object o)
  {
    return subVarSet.equals (o);
  }

  public int hashCode ()
  {
    return subVarSet.hashCode ();
  }

  public String toString ()
  {
    return subVarSet.toString ();
  }

  private static final long serialVersionUID = 1L;
  private static final int SERIAL_VERSION = 1;

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    in.readInt (); // version
    subVarSet = (VarSet) in.readObject ();
  }

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (SERIAL_VERSION);
    out.writeObject (subVarSet);
  }

}
