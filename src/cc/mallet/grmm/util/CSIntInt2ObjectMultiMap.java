/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;

import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TObjectProcedure;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * A map that maps (int, int) --> object, where each (int,int) key
 *  is allowed to map to multiple objects.
 *
 * Created: Dec 14, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: CSIntInt2ObjectMultiMap.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class CSIntInt2ObjectMultiMap {

  private TIntObjectHashMap backing = new TIntObjectHashMap ();

  public void add (int key1, int key2, Object value)
  {
    TIntObjectHashMap inner = (TIntObjectHashMap) backing.get (key1);
    if (inner == null) {
      inner = new TIntObjectHashMap ();
      backing.put (key1, inner);
    }

    List lst = (List) inner.get (key2);
    if (lst == null) {
      lst = new ArrayList ();
      inner. put (key2, lst);
    }

    lst.add (value);
  }

  public List get (int key1, int key2)
  {
    TIntObjectHashMap inner = (TIntObjectHashMap) backing.get (key1);
    if (inner == null) {
      return null;
    } else {
      return (List) inner.get (key2);
    }
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

  public void clear () { backing.clear (); }
  
  // not yet serializable
}
