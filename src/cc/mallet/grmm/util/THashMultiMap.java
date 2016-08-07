/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;

import gnu.trove.map.hash.THashMap;

import java.util.*;

/**
 * Version of THashMap where every key is mapped to a list of objects.
 * <p>
 * The put method adds a value to the list associated with a key, without
 * removing any previous values.
 * The get method returns the list of all objects associated with key.
 *  No effort is made to remove duplicates.
 *
 * Created: Dec 13, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: THashMultiMap.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class THashMultiMap extends AbstractMap {

  private THashMap backing;

  public THashMultiMap ()
  {
    backing = new THashMap ();
  }

  public THashMultiMap (int initialCapacity)
  {
    backing = new THashMap (initialCapacity);
  }


  public Set entrySet ()
  {
    return backing.entrySet (); // potentially inefficient
  }

  /** Adds <tt>key</tt> as a key with an empty list as a value. */
  public void add (Object key) { backing.put (key, new ArrayList ()); }

  public Object get (Object o)
  {
    return (List) backing.get (o);
  }

  /** Adds <tt>value</tt> to the list of things mapped to by key.
   * @return The current list of values associated with key.
   *       (N.B. This deviates from Map contract slightly!  (Hopefully harmlessly))
   */
  public Object put (Object key, Object value)
  {
    List lst;
    if (!backing.keySet ().contains (key)) {
      lst = new ArrayList ();
      backing.put (key, lst);
    } else {
      lst = (List) backing.get (key);
    }

    lst.add (value);

    return lst;
  }

  // Serialization not yet supported
}
