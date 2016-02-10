/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.util;

import gnu.trove.TDoubleProcedure;
import gnu.trove.TObjectProcedure;

import java.lang.reflect.Array;

/**
 *  Static utility methods for arrays 
 *   (like java.util.Arrays, but more useful).
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: ArrayUtils.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
final public class ArrayUtils {

	private ArrayUtils() {}

  public static int indexOf (Object[] array, Object obj)
  {
    for (int i = 0; i < array.length; i++) {
      if ((array[i] != null) && array[i].equals (obj)) {
        return i;
      }
    }
    return -1;
  }

  public static int indexOf (int[] array, int obj)
  {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == obj) {
        return i;
      }
    }
    return -1;
  }

	/**
	 *  Returns true if the procedure proc returns true for any
	 *   element of the array v.
	 */
	public static boolean any (TDoubleProcedure proc, double[] v)
	{
		for (int i = 0; i < v.length; i++) {
			if (proc.execute (v[i])) {
				return true;
			}
		}
		return false;
	}


	/**
	 *  Returns true if the procedure proc returns true for any
	 *   element of the array v.
	 */
	public static boolean any (TObjectProcedure proc, Object[][] v)
	{
		for (int i = 0; i < v.length; i++) {
			for (int j = 0; j < v[i].length; j++) {
				if (proc.execute (v[i][j])) {
					return true;
				}
			}
		}
		return false;
	}
	
	public static void forEach (TObjectProcedure proc, Object[] v) 
	{
		for (int i = 0; i < v.length; i++) {
			proc.execute (v[i]);
		}
	}

	public static void forEach (TObjectProcedure proc, Object[][] v) 
	{
		for (int i = 0; i < v.length; i++) {
			for (int j = 0; j < v[i].length; j++) {
				proc.execute (v[i][j]);
			}
		}
	}


	public static void print (double[] v)
	{
		System.out.print ("[");
		for (int i = 0; i < v.length; i++)
			System.out.print (" " + v[i]);
		System.out.println (" ]");
	}
	
	public static void print (int[] v)
	{
		System.out.print ("[");
		for (int i = 0; i < v.length; i++)
			System.out.print (" " + v[i]);
		System.out.println (" ]");
	}

  public static String toString (int[] v)
  {
    StringBuffer buf = new StringBuffer ();
    for (int i = 0; i < v.length; i++) {
      buf.append (v[i]);
      if (i < v.length - 1) buf.append (" ");
    }
    return buf.toString ();
  }

  public static String toString (double[] v)
  {
    StringBuffer buf = new StringBuffer ();
    for (int i = 0; i < v.length; i++) {
      buf.append (v[i]);
      if (i < v.length - 1) buf.append (" ");
    }
    return buf.toString ();
  }

  public static String toString (Object[] v)
  {
    StringBuffer buf = new StringBuffer ();
    for (int i = 0; i < v.length; i++) {
      buf.append (v[i]);
      if (i < v.length - 1) buf.append (" ");
    }
    return buf.toString ();
  }
  
  /**
   * Returns a new array containing all of a, with additional extra space added (zero initialized).
   * @param a
   * @param additional
   * @return
   */
  public static int[] extend (int[] a, int additional)
  {
  	int[] ret = new int[a.length + additional];
  	System.arraycopy(a, 0, ret, 0, a.length);
  	return ret;
  }

  /**
   * Returns a new array containing all of a, with additional extra space added (zero initialized).
   * @param a
   * @param additional
   * @return
   */
  public static double[] extend (double[] a, int additional)
  {
  	double[] ret = new double[a.length + additional];
  	System.arraycopy(a, 0, ret, 0, a.length);
  	return ret;
  }

  /**
	 * Returns a new array that is the concatenation of a1 and a2.
	 * @param a1
	 * @param a2
	 * @return
	 */
	public static int[] append (int[] a1, int[] a2)
  {
		int[] ret = new int[a1.length + a2.length];
		System.arraycopy(a1, 0, ret, 0, a1.length);
		System.arraycopy(a2, 0, ret, a1.length, a2.length);			
    return ret;
  }

  /**
	 * Returns a new array that is the concatenation of a1 and a2.
	 * @param a1
	 * @param a2
	 * @return
	 */
	public static double[] append (double[] a1, double[] a2)
  {
		double[] ret = new double[a1.length + a2.length];
		System.arraycopy(a1, 0, ret, 0, a1.length);
		System.arraycopy(a2, 0, ret, a1.length, a2.length);			
    return ret;
  }

  /**
   * Returns a new array with a single element appended at the end.
   *  Use this sparingly, for it will allocate a new array.  You can
   *  easily turn a linear-time algorithm to quadratic this way.
   * @param v Original array
   * @param elem Element to add to end
   */
  public static int[] append (int[] v, int elem)
  {
    int[] ret = new int [v.length + 1];
    System.arraycopy (v, 0, ret, 0, v.length);
    ret[v.length] = elem;
    return ret;
  }

  /**
   * Returns a new array with a single element appended at the end.
   *  Use this sparingly, for it will allocate a new array.  You can
   *  easily turn a linear-time algorithm to quadratic this way.
   * @param v Original array
   * @param elem Element to add to end
   */
  public static boolean[] append (boolean[] v, boolean elem)
  {
    boolean[] ret = new boolean [v.length + 1];
    System.arraycopy (v, 0, ret, 0, v.length);
    ret[v.length] = elem;
    return ret;
  }

    /**
   * Returns a new array with a single element appended at the end.
   *  Use this sparingly, for it will allocate a new array.  You can
   *  easily turn a linear-time algorithm to quadratic this way.
   * @param v Original array
   * @param elem Element to add to end
   * @return Array with length v+1 that is (v0,v1,...,vn,elem).
     *  Runtime type will be same as he pased-in array.
   */
  public static Object[] append (Object[] v, Object elem)
  {
    Object[] ret = (Object[]) Array.newInstance (v.getClass().getComponentType(), v.length+1);
    System.arraycopy (v, 0, ret, 0, v.length);
    ret[v.length] = elem;
    return ret;
  }
  
/*
	public static Object[] cloneArray (Cloneable[] arr)
	{
		// Do this magic so that it can be cast to original type when done
		Object[] aNew = (Object[]) Array.newInstance (arr.getClass().getComponentType(), arr.length);
		for (int i = 0; i < arr.length; i++) {
			aNew [i] = arr[i].clone ();
		}
		return aNew;
	}
*/

  /** Returns the number of times a value occurs in a given array. */
  public static double count (int[] sampled, int val)
  {
    int count = 0;
    for (int i = 0; i < sampled.length; i++) {
      if (sampled[i] == val) {
        count++;
      }
    }
    return count;
  }

   public static int argmax (double [] elems)
  {
    int bestIdx = -1;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < elems.length; i++) {
      double elem = elems[i];
      if (elem > max) {
        max = elem;
        bestIdx = i;
      }
    }
    return bestIdx;
  }

  public static boolean equals (boolean[][] m1, boolean[][] m2)
  {
     if (m1.length != m2.length) return false;
     for (int i = 0; i < m1.length; i++) {
       if (m1[i].length != m2[i].length) return false;
       for (int j = 0; j < m1[i].length; j++) {
         boolean b1 = m1[i][j];
         boolean b2 = m2[i][j];
         if (b1 != b2) return false;
       }
    }
    return true;
  }
  

} // Arrays
