/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.util;

import java.util.ArrayList;

public class ArrayListUtils
{

	// xxx Why not just use java.util.Arrays.asList (Object[] a)  -cas
	static public ArrayList createArrayList (Object[] a)
	{
		ArrayList al = new ArrayList (a.length);
		for (int i = 0; i < a.length; i++)
			al.add (a[i]);
		return al;
	}

	// Useful until java 1.5  -ghuang
	static public int[] toIntArray (ArrayList list)
	{
		int[] result = new int[list.size()];

		for (int i = 0; i < list.size(); i++) {
			Number n = (Number) list.get(i);
			result[i] = n.intValue();
		}

		return result;
	}

	// Useful until java 1.5  -ghuang
	static public double[] toDoubleArray (ArrayList list)
	{
		double[] result = new double[list.size()];

		for (int i = 0; i < list.size(); i++) {
			Number n = (Number) list.get(i);
			result[i] = n.doubleValue();
		}

		return result;
	}
}
