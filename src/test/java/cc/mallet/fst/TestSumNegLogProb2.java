/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.fst;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestSumNegLogProb2
{
	private double sumNegLogProb (double a, double b) {
		return - Math.log (Math.exp(-a) + Math.exp(-b));
	}

	public void testSum (double a, double b)
	{
		double al = - Math.log (a);
		double bl = - Math.log (b);
		double abl = sumNegLogProb (al, bl);
		double ab = Math.exp (-abl);
		System.out.println (" " + a +"  +  "+ b +"  =  "+ab);
		System.out.println (">" + al +"  +  "+ bl +"  =  "+abl);
		assertTrue (Math.abs (ab - (a+b)) < 0.001);
	}

	@Test
	public void testTwo ()
	{
		testSum (.5, .5);
		testSum (.9, .1);
		testSum (.99, .01);
		testSum (.99999, .00001);
		testSum (.00001, 0.00001);
		testSum (.00000001, 0.00001);
		testSum (.0000000000001, 0.00001);
	}

}
