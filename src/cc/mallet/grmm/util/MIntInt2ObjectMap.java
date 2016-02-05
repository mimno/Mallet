/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import gnu.trove.TIntObjectIterator;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Hash map that maps integer pairs to objects.
 * This uses much less space than an 2d array, if the mapping is sparse.
 *
 * Created: Dec 14, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: MIntInt2ObjectMap.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class MIntInt2ObjectMap implements Serializable {

  private TIntObjectHashMap backing = new TIntObjectHashMap ();

  public MIntInt2ObjectMap () { }
  public MIntInt2ObjectMap (int initialCapacity) {
    backing = new TIntObjectHashMap (initialCapacity);
  }

  public Object put (int key1, int key2, Object value)
  {
    TIntObjectHashMap inner;
    if (backing.containsKey (key1)) {
      inner = (TIntObjectHashMap) backing.get (key1);
    } else {
      inner = new TIntObjectHashMap ();
      backing.put (key1, inner);
    }

    return inner.put (key2, value);
  }

  public Object get (int key1, int key2)
  {
    TIntObjectHashMap inner = (TIntObjectHashMap) backing.get (key1);
    if (inner == null) {
      return null;
    } else {
      return inner.get (key2);
    }
  }

  /** Returns an iterator over the set of (key2, value) pairs that match (key1). */
  public TIntObjectIterator curry (int key1)
  {
    final TIntObjectHashMap inner = (TIntObjectHashMap) backing.get (key1);
    if (inner == null) {
      return new TIntObjectIterator (new TIntObjectHashMap ());
    } else {
      return new TIntObjectIterator (inner);
    }
  }

  /** Returns an array of first-level keys. */
  public int[] keys1 () {
    return backing.keys ();
  }

  public int size ()
  {
    final int[] N = new int[]{0};
    backing.forEachValue (new TObjectProcedure() {
      public boolean execute (Object object)
      {
        TIntObjectHashMap inner = (TIntObjectHashMap) object;
        N[0] += inner.size ();
        return true;
      }
    });
    return N[0];
  }

  public int[] keys2 (int key1)
  {
    TIntObjectHashMap inner = (TIntObjectHashMap) backing.get (key1);
    return inner.keys ();
  }

  private static final long serialVersionUID = 1;

  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.writeInt (CURRENT_SERIAL_VERSION);
    int[] keys1 = keys1 ();
    out.writeInt (keys1.length);
    for (int i = 0; i < keys1.length; i++) {
      int k1 = keys1[i];
      out.writeInt (k1);

      int[] keys2 = keys2 (k1);
      out.writeInt (keys2.length);
      for (int j = 0; j < keys2.length; j++) {
        int k2 = keys2[j];
        out.writeInt (k2);
        out.writeObject (get (k1, k2));
      }
    }
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.readInt (); // version =

    int N1 = in.readInt ();
    backing = new TIntObjectHashMap (N1);

    for (int i = 0; i < N1; i++) {
      int k1 = in.readInt ();
      int N2 = in.readInt ();
      for (int j = 0; j < N2; j++) {
        int k2 = in.readInt ();
        Object value = in.readObject ();
        put (k1, k2, value);
      }
    }
  }




}
