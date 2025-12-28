/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.iterator;

import java.util.Iterator;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;

/**
 *  Interface for classes that generate instances.
 *
 *  Typically, these instances will be unprocessed (e.g., they
 *  may come from a corpus data file), and are passed through a pipe
 *  as they are added to an InstanceList.
 *
 *  @see Pipe
 *  @see cc.mallet.types.InstanceList
 *
 */
@Deprecated // You should just use Iterator<Instance> directly.  This class will be removed in the future.
public abstract class PipeInputIterator implements Iterator<Instance>
{
	public abstract Instance next ();
	public abstract boolean hasNext ();
	public void remove () {	throw new UnsupportedOperationException ();	}
	
	// Sometimes (as in an InstanceList used for AdaBoost) Instances may be weighted.
	// Weights may also come from other raw input sources for instances in Pipes.
	public double getWeight () { return 1.0; }

}
