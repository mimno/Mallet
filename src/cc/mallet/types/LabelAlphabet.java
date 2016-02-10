/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;

import cc.mallet.types.Alphabet;
/**
		A mapping from arbitrary objects (usually String's) to integers
		(and corresponding Label objects) and back.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class LabelAlphabet extends Alphabet implements Serializable
{
	ArrayList labels;
		
	public LabelAlphabet ()
	{
		super();
		this.labels = new ArrayList ();
	}

	public int lookupIndex (Object entry, boolean addIfNotPresent)
	{
		int index = super.lookupIndex (entry, addIfNotPresent);
		if (index >= labels.size() && addIfNotPresent)
			labels.add (new Label (entry, this, index));
		return index;
	}

	public Label lookupLabel (Object entry, boolean addIfNotPresent)
	{
		int index = lookupIndex (entry, addIfNotPresent);
		if (index >= 0)
			return (Label) labels.get(index);
		else
			return null;
	}
		
	public Label lookupLabel (Object entry)
	{
		return this.lookupLabel (entry, true);
	}

	public Label lookupLabel (int labelIndex)
	{
		return (Label) labels.get(labelIndex);
	}
		
}
