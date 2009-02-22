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

import cc.mallet.types.Instance;

public class StringArrayIterator implements Iterator<Instance>
{
	String[] data;
	int index;
	
	public StringArrayIterator (String[] data)
	{
		this.data = data;
		this.index = 0;
	}

	public Instance next ()
	{
		URI uri = null;
		try { uri = new URI ("array:" + index); }
		catch (Exception e) { e.printStackTrace(); throw new IllegalStateException(); }
		return new Instance (data[index++], null, uri, null);
	}

	public boolean hasNext ()	{	return index < data.length;	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}
