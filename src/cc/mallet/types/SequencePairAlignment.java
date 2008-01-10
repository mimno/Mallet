/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

public class SequencePairAlignment<I,O> extends SequencePair<I,O>
{
	protected double weight;
	
	public SequencePairAlignment (Sequence<I> input, Sequence<O> output, double weight)
	{
		super (input, output);
		this.weight = weight;
	}

	protected SequencePairAlignment ()
	{
	}
	
	public double getWeight()
	{
		return weight;
	}
			
}
