/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.types;
import java.util.LinkedHashMap;

import cc.mallet.util.*;

/**
	 Computes a similarity metric between two strings, based on counts
	 of common subsequences of characters. See Lodhi et al "String
	 kernels for text classification." Optionally caches previous kernel
	 computations.
 */
public class StringKernel extends LinkedHashMap
{
	// all words to lowercase 
	static final boolean DEFAULT_NORMALIZE_CASE = true; 
	// gap penalty 
	static final double DEFAULT_LAMBDA = 0.5; 
	// max length of subsequences to compare
	static final int DEFAULT_LENGTH = 3; 
  // true if we should cache previous kernel
  // computations. Recommended!
	static final boolean DEFAULT_CACHE = true; 
	
	boolean normalizeCase; 
	double lambda;
	int n;
	boolean cache; 
	
	/**
		 @param norm true if we lowercase all strings
		 @param lam 0-1 penalty for gaps between matches.
		 @param length max length of subsequences to compare
		 @param cache true if we should cache previous kernel computations. recommended!
	 */
	public StringKernel (boolean norm, double lam, int length, boolean cache) {
		this.normalizeCase = norm;
		this.lambda = lam;
		this.n = length;
		this.cache = cache;
	}

	public StringKernel () {
		this(DEFAULT_NORMALIZE_CASE, DEFAULT_LAMBDA, DEFAULT_LENGTH, DEFAULT_CACHE);
	}

	public StringKernel (boolean norm, double lam, int length) {
		this (norm, lam, length, DEFAULT_CACHE);
	}

	/**
		 Computes the normalized string kernel between two strings.
		 @param s string 1
		 @param t string 2
		 @return 0-1 value, where 1 is exact match.
	 */
	public double K (String s, String t) {
		// compute self kernels if not in hashmap
		double ss,tt;
		Double sstmp = (Double)get (s);
		Double tttmp = (Double)get (t);
		if (sstmp == null) {
			ss = sK (s,s,n);
			if (cache)
				put (s, new Double (ss));
		}
		else
			ss = sstmp.doubleValue();
		if (tttmp == null) {
			tt = sK (t,t,n);
			if (cache)
				put (t, new Double (tt));
		}
		else
			tt = tttmp.doubleValue();

		double st = sK (s,t,n);
		// normalize
		return st / Math.sqrt (ss*tt);				
	}
	
	private double sK(String s, String t, int n)
	{
		double sum, r = 0.0;
		int i, j, k;
		int slen = s.length();
		int tlen = t.length();
		
		double [][]K = new double[n+1][(slen+1)*(tlen+1)];
		
		for (j = 0; j < (slen+1); j++)
			for (k = 0; k < (tlen+1); k++)
				K[0][k*(slen+1) + j] = 1;
		
		for (i = 0; i < n; i++)
    {
      for (j = 0; j < slen; j++)
			{
				sum = 0.0;
				for (k = 0; k < tlen; k++)
				{
					if (t.charAt(k) == s.charAt(j))
					{
						sum += K[i][k*(slen+1)+j];
					}
					K[i+1][(k+1)*(slen+1)+j+1] = K[i+1][(k+1)*(slen+1)+j] + sum;
				}
			}
      r = r + K[i+1][tlen*(slen+1)+slen];
    }
		return r;
	}

	static CommandOption.String string1Option = new CommandOption.String
	(StringKernel.class, "string1", "FILE", true, null, "String one", null);
	static CommandOption.String string2Option = new CommandOption.String
	(StringKernel.class, "string2", "FILE", true, null, "String two", null);
	static final CommandOption.List commandOptions =
	new CommandOption.List (
		"String Kernel.",
		new CommandOption[] {
			string1Option,
			string2Option,
		});

	/** Return string kernel between two strings*/
	public static void main (String[] args) throws Exception {
		commandOptions.process (args);
		StringKernel sk = new StringKernel ();
		System.err.println ("String Kernel for " + string1Option.value + " and " + string2Option.value + " is " + sk.K (string1Option.value, string2Option.value)); 			
	}
}

