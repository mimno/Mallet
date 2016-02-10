/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.pipe;

import java.io.*;
import java.net.URI;

import cc.mallet.types.Instance;
import cc.mallet.util.CharSequenceLexer;

/**
 *  Pipe that can adds special text between lines to explicitly
 *  represent line breaks.
 */
public class StringAddNewLineDelimiter extends Pipe implements Serializable
{
	String delim;
	
	public StringAddNewLineDelimiter (String delim) { this.delim = delim; }

	public Instance pipe (Instance carrier)
	{
		if (!(carrier.getData() instanceof String))
			throw new IllegalArgumentException ("Expecting String, got " + carrier.getData().getClass().getName());		
		String s = (String) carrier.getData();
		String newline = System.getProperty ("line.separator");
		s = s.replaceAll (newline, delim);
		carrier.setData (s);
		return carrier;
	}

  // Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (delim);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		this.delim = (String) in.readObject ();
	}

}
