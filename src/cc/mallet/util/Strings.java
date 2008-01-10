/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.util;

import java.util.Arrays;

/**
 *  Static utility methods for Strings
 */
final public class Strings {

	public static int commonPrefixIndex (String[] strings)
	{
		int prefixLen = strings[0].length();
		for (int i = 1; i < strings.length; i++) {
			if (strings[i].length() < prefixLen)
				prefixLen = strings[i].length();
			int j = 0;
			if (prefixLen == 0)
				return 0;
			while (j < prefixLen) {
				if (strings[i-1].charAt(j) != strings[i].charAt(j)) {
					prefixLen = j;
					break;
				}
				j++;
			}
		}
		return prefixLen;
	}

	public static String commonPrefix (String[] strings)
	{
		return strings[0].substring (0, commonPrefixIndex(strings));
	}

  public static int count (String string, char ch)
  {
    int idx = -1;
    int count = 0;
    while ((idx = string.indexOf (ch, idx+1)) >= 0) { count++; };
    return count;
  }

	public static double levenshteinDistance (String s, String t) {
		int n = s.length();
		int m = t.length();
		int d[][]; // matrix
		int i; // iterates through s
		int j; // iterates through t
		char s_i; // ith character of s
		char t_j; // jth character of t
		int cost; // cost
		
    if (n == 0) 
      return 1.0;
    if (m == 0) 
      return 1.0;

    d = new int[n+1][m+1];

    for (i = 0; i <= n; i++)
      d[i][0] = i;

    for (j = 0; j <= m; j++) 
      d[0][j] = j;

    for (i = 1; i <= n; i++) {
      s_i = s.charAt (i - 1);

      for (j = 1; j <= m; j++) {
        t_j = t.charAt (j - 1);

				cost = (s_i == t_j) ? 0 : 1;

        d[i][j] = minimum (d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1] + cost);
      }
    }

		int longer = (n > m) ? n : m;
    return (double)d[n][m] / longer; // Normalize to 0-1.
	}

	private static int minimum (int a, int b, int c) {
		int mi = a;		
    if (b < mi) {
      mi = b;
    }
    if (c < mi) {
      mi = c;
    }
    return mi;		
  }
}
