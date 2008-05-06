/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Add the token text as a feature with value 1.0.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.io.*;
import java.util.regex.Pattern;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class TokenText extends Pipe implements Serializable
{
	String prefix;
	Pattern matchingRegex;

	public TokenText (String prefix, Pattern matchingRegex) 
	{
		this.prefix = prefix;
		this.matchingRegex = matchingRegex;
	}

	public TokenText (String prefix)
	{
		this.prefix=prefix;
	}
	
	public TokenText ()
	{
	}

	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.get(i);
			if (matchingRegex != null && matchingRegex.matcher(t.getText()).matches())
				t.setFeatureValue (prefix == null ? t.getText().intern() : (prefix+t.getText()).intern(), 1.0);
		}
		return carrier;
	}
	
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (prefix);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		prefix = (String) in.readObject ();
	}


}
