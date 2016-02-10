/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>

	 Maximize a function projected along a line.
 */

package cc.mallet.optimize;

/** Optimize, constrained to move parameters along the direction of a specified line.
 * The Optimizable object would be either Optimizable.ByValue or Optimizable.ByGradient. */
public interface LineOptimizer
{
	
	/** Returns the last step size used. */
	public double optimize (double[] line, double initialStep);

	public interface ByGradient	{
		/** Returns the last step size used. */
		public double optimize (double[] line, double initialStep);
	}
	
}
