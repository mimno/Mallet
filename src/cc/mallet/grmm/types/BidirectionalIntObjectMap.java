/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * A mapping between integers and objects where the mapping in each
 * direction is efficient.  Integers are assigned consecutively, starting
 * at zero, as objects are added to map.  Objects can not be
 * deleted from the map and thus the integers are never reused.
 * <p/>
 * This class is exactly like Alphabet in MALLET, except that it does
 * not do the Serialization magic to ensure that two alphabets that
 * are desrialized from the same file are ==.  This avoids memory leaks,
 * because MALLET Alphabets are retained in memory forever.
 */
public class BidirectionalIntObjectMap implements Serializable {

  gnu.trove.TObjectIntHashMap map;
  ArrayList entries;
  boolean growthStopped = false;

  public BidirectionalIntObjectMap (int capacity)
  {
    this.map = new gnu.trove.TObjectIntHashMap (capacity);
    this.entries = new ArrayList (capacity);
  }

  public BidirectionalIntObjectMap ()
  {
    this (8);
  }

  public BidirectionalIntObjectMap (BidirectionalIntObjectMap other)
  {
    map = (gnu.trove.TObjectIntHashMap) other.map.clone ();
    entries = (ArrayList) other.entries.clone ();
    growthStopped = other.growthStopped;
  }

  /**
   * Return -1 if entry isn't present.
   */
  public int lookupIndex (Object entry, boolean addIfNotPresent)
  {
    if (entry == null) {
      throw new IllegalArgumentException ("Can't lookup \"null\" in an Alphabet.");
    }

    int retIndex = -1;
    if (map.containsKey (entry)) {
      retIndex = map.get (entry);
    } else if (!growthStopped && addIfNotPresent) {
      retIndex = entries.size ();
      map.put (entry, retIndex);
      entries.add (entry);
    }
    return retIndex;
  }

  public int lookupIndex (Object entry)
  {
    return lookupIndex (entry, true);
  }

  public Object lookupObject (int index)
  {
    return entries.get (index);
  }

  public Object[] toArray ()
  {
    return entries.toArray ();
  }

  /**
   * Returns an array containing all the entries in the Alphabet.
   * The runtime type of the returned array is the runtime type of in.
   * If in is large enough to hold everything in the alphabet, then it
   * it used.  The returned array is such that for all entries <tt>obj</tt>,
   * <tt>ret[lookupIndex(obj)] = obj</tt> .
   */
  public Object[] toArray (Object[] in)
  {
    return entries.toArray (in);
  }

  // xxx This should disable the iterator's remove method...
  public Iterator iterator ()
  {
    return entries.iterator ();
  }

  public Object[] lookupObjects (int[] indices)
  {
    Object[] ret = new Object[indices.length];
    for (int i = 0; i < indices.length; i++)
      ret[i] = entries.get (indices[i]);
    return ret;
  }

  /**
   * Returns an array of the objects corresponding to
   *
   * @param indices An array of indices to look up
   * @param buf     An array to store the returned objects in.
   * @return An array of values from this Alphabet.  The runtime type of the array is the same as buf
   */
  public Object[] lookupObjects (int[] indices, Object[] buf)
  {
    for (int i = 0; i < indices.length; i++)
      buf[i] = entries.get (indices[i]);
    return buf;
  }

  public int[] lookupIndices (Object[] objects, boolean addIfNotPresent)
  {
    int[] ret = new int[objects.length];
    for (int i = 0; i < objects.length; i++)
      ret[i] = lookupIndex (objects[i], addIfNotPresent);
    return ret;
  }

  public boolean contains (Object entry)
  {
    return map.contains (entry);
  }

  public int size ()
  {
    return entries.size ();
  }

  public void stopGrowth ()
  {
    growthStopped = true;
  }

  public void startGrowth ()
  {
    growthStopped = false;
  }

  public boolean growthStopped ()
  {
    return growthStopped;
  }

  /**
   * Return String representation of all Alphabet entries, each
   * separated by a newline.
   */
  public String toString ()
  {
    StringBuffer sb = new StringBuffer ();
    for (int i = 0; i < entries.size (); i++) {
      sb.append (entries.get (i).toString ());
      sb.append ('\n');
    }
    return sb.toString ();
  }

  public void dump () { dump (System.out); }

  public void dump (PrintStream out)
  {
    dump (new PrintWriter (new OutputStreamWriter (out), true));
  }

  public void dump (PrintWriter out)
  {
    for (int i = 0; i < entries.size (); i++) {
      out.println (i + " => " + entries.get (i));
    }
  }

  // Serialization

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.writeInt (BidirectionalIntObjectMap.CURRENT_SERIAL_VERSION);
    out.writeInt (entries.size ());
    for (int i = 0; i < entries.size (); i++)
      out.writeObject (entries.get (i));
    out.writeBoolean (growthStopped);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.readInt ();  // int version
    int size = in.readInt ();
    entries = new ArrayList (size);
    map = new gnu.trove.TObjectIntHashMap (size);
    for (int i = 0; i < size; i++) {
      Object o = in.readObject ();
      map.put (o, i);
      entries. add (o);
    }
    growthStopped = in.readBoolean ();
  }

}
