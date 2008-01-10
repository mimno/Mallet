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


import java.net.URI;
import java.util.Iterator;
import java.util.List;

import cc.mallet.types.Instance;
import cc.mallet.util.ArrayListUtils;

public class ArrayIterator implements Iterator<Instance>
{
	Iterator subIterator;
  Object target;
	int index;
	
	public ArrayIterator (List data, Object target)
	{
		this.subIterator = data.iterator ();
		this.target = target;
		this.index = 0;
	}

	public ArrayIterator (List data)
	{
		this (data, null);
	}
	
	public ArrayIterator (Object[] data, Object target)
	{
		this (ArrayListUtils.createArrayList (data), target);
	}

	public ArrayIterator (Object[] data)
	{
		this (data, null);
	}
	

	public Instance next ()
	{
		URI uri = null;
		try { uri = new URI ("array:" + index++); }
		catch (Exception e) { e.printStackTrace(); throw new IllegalStateException(); }
		return new Instance (subIterator.next(), target, uri, null);
	}

	public boolean hasNext ()	{	return subIterator.hasNext();	}

	public void remove() { subIterator.remove(); }
	
}

