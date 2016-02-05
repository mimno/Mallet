/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.*;
/**
 * Unimplemented.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class InstanceListTrimFeaturesByCount extends Pipe implements Serializable
{
	int minCount;
	
	public InstanceListTrimFeaturesByCount (int minCount)
	{
		super (new Alphabet(), null);
		this.minCount = minCount;
	}

	public Instance pipe (Instance carrier)
	{
		// xxx Not yet implemented!
		throw new UnsupportedOperationException ("Not yet implemented");
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
	}

}
