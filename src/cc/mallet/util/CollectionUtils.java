/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.util;

import gnu.trove.THashSet;
import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleProcedure;

import java.util.*;

/**
 *  *
 * Created: Sun Jan 25 01:04:29 2004
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: CollectionUtils.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class CollectionUtils {

	private CollectionUtils() {} // No instances
	
	public static String dumpToString (Collection c, String separator)
	{
		String retval = "";
		for (Iterator it = c.iterator(); it.hasNext();) {
			retval += String.valueOf(it.next());
			retval += separator;
		}
		return retval;
	}

	public static String dumpToString (Collection c)
	{
		return dumpToString (c, " ");
	}

	public static void print (Collection c) 
	{
		System.out.println (dumpToString (c));
	}

	public static void print (Collection c, String separator) 
	{
		System.out.println (dumpToString (c, separator));
	}

	public static Collection subset (Collection c, int size, java.util.Random rand)
	{
		ArrayList list = new ArrayList (c);
		int realSize = (size < c.size()) ? size : c.size();
		java.util.Collections.shuffle (list, rand);
		return list.subList (0, realSize);
	}

  public static List sortedUnion (List args1, List args2)
  {
    SortedSet set = new TreeSet ();
    set.addAll (args1);
    set.addAll (args2);

    List lst = new ArrayList (set.size ());
    for (Iterator it = set.iterator (); it.hasNext ();) {
      Object o = it.next ();
      lst.add (o);
    }

    return lst;
  }

  /** Computes a nondestructive intersection of two collections. */
  public static Collection intersection (Collection c1, Collection c2)
  {
    Set set = new THashSet (c1);
    set.retainAll (c2);
    return set;
  }

  public static Collection union (Collection c1, Collection c2)
  {
    Set set = new THashSet (c1);
    set.addAll (c2);
    return set;
  }

  /** Returns the key in map that has the greatest score */
  public static Object argmax (TObjectDoubleHashMap map)
  {
    // A local class! Yes, Virginia, this is legal Java.
    class Accumulator implements TObjectDoubleProcedure {
      double bestVal = Double.NEGATIVE_INFINITY;
      Object bestObj = null;
      public boolean execute (Object a, double b)
      {
        if (b > bestVal) {
          bestVal = b;
          bestObj = a;
        }
        return true;
      }
    }

    Accumulator procedure = new Accumulator ();
    map.forEachEntry (procedure);
    return procedure.bestObj;
  }

  public static interface Fn { Object f (Object input); }

  /** Returns a new collection whose elements consist of the function fn.f applied to all of the
   *   elements of the given collection.  The returned collection will have the same class as the input
   *   collection.
   */
  public static Collection map (Collection c, Fn fn)
  {
    Class collectionClass = c.getClass ();
    Collection copy;

    try {
      copy = (Collection) collectionClass.newInstance ();
    } catch (InstantiationException e) {
      throw new RuntimeException (e); // should never happen; collections always have public default ctor
    } catch (IllegalAccessException e) {
      throw new RuntimeException (e); // should never happen; collections always have public default ctor
    }

    Iterator it = c.iterator ();
    while (it.hasNext ()) {
      copy.add (fn.f (it.next ()));
    }

    return copy;
  }

} // Collections
