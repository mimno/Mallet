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

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class TokenTextNGrams extends Pipe implements Serializable
{
	static char startBorderChar = '>';
	static char endBorderChar = '<';

	String prefix;
	int[] gramSizes;
	boolean distinguishBorders = false;

	public TokenTextNGrams (String prefix, int[] gramSizes)
	{
		this.prefix=prefix;
		this.gramSizes = gramSizes;
	}
	
	public TokenTextNGrams ()
	{
		this ("CHARBIGRAM=", new int[] {2});
	}

	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.getToken(i);
			String s = t.getText();
			if (distinguishBorders)
				s = startBorderChar + s + endBorderChar;
			int slen = s.length();
			for (int j = 0; j < gramSizes.length; j++) {
				int size = gramSizes[j];
				for (int k = 0; k < slen - size; k++)
					t.setFeatureValue (s.substring (k, k+size), 1.0);//original was substring(k, size), changed by Fuchun
			}
		}
		return carrier;
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
