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
import cc.mallet.fst.Segment;

public class SequencePair<I,O>
{
	protected Sequence<I> input;
	protected Sequence<O> output;

	public SequencePair (Sequence<I> input, Sequence<O> output)
	{
		this.input = input;
		this.output = output;
	}

	protected SequencePair ()
	{
	}

	public Sequence<I> input() { return input; }
	public Sequence<O> output() { return output; }
	
	/* This doesn't belong here. -akm 11/2007
	public Sequence[] outputNBest() {return outputNBest;}
	public double[] costNBest(){return costNBest;}
	public double[] confidenceNBest(){return confidenceNBest;}
	*/
}
