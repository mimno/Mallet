/* Copyright (C) 2003 University of Pennsylvania
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 */

package cc.mallet.pipe.iterator;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;

public class ArrayDataAndTargetIterator implements Iterator<Instance>
{
	Iterator subIterator;
  Iterator targetIterator;
	int index;
	
	public ArrayDataAndTargetIterator (List data, List targets)
	{
		this.subIterator = data.iterator ();
		this.targetIterator = targets.iterator ();
		this.index = 0;
	}

	public ArrayDataAndTargetIterator (Object[] data, Object target[])
	{
		this (java.util.Arrays.asList (data),	java.util.Arrays.asList (target));
	}

	// The PipeInputIterator interface

	public Instance next ()
	{
		URI uri = null;
		try { uri = new URI ("array:" + index++); }
		catch (Exception e) { e.printStackTrace(); throw new IllegalStateException(); }
		return new Instance (subIterator.next(), targetIterator.next(), uri, null);
	}

	public boolean hasNext ()	{	return subIterator.hasNext();	}

	public void remove () { throw new IllegalStateException ("This iterator does not support remove().");	}
	
}

