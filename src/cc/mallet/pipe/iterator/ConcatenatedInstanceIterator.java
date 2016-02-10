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

import cc.mallet.pipe.*;
import cc.mallet.types.Instance;

public class ConcatenatedInstanceIterator implements Iterator<Instance>
{
	Iterator<Instance>[] iterators;
	Instance next;
	int iteratorIndex;
	
	public ConcatenatedInstanceIterator (Iterator<Instance>[] iterators)
	{
		this.iterators = iterators;
		this.iteratorIndex = 0;
		setNext();
	}

	private void setNext () {
		next = null;
		for (; iteratorIndex < iterators.length &&
					 !iterators[iteratorIndex].hasNext(); iteratorIndex++);			
		if (iteratorIndex < iterators.length)
			next = (Instance)iterators[iteratorIndex].next();		
	}
	
	public boolean hasNext ()
	{
		return next != null;
	}

	public Instance next ()
	{
		Instance ret = (Instance)next;
		setNext();		
		return ret;
	}
	
	public void remove () { throw new IllegalStateException ("This Iterator<Instance> does not support remove()."); }
}



