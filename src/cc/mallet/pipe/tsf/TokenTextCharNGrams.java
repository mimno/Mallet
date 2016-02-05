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

public class TokenTextCharNGrams extends Pipe implements Serializable
{
	static char startBorderChar = '>';
	static char endBorderChar = '<';

	String prefix;
	int[] gramSizes;
	boolean distinguishBorders = false;

	public TokenTextCharNGrams (String prefix, int[] gramSizes, boolean distinguishBorders)
	{
		this.prefix=prefix;
		this.gramSizes = gramSizes;
		this.distinguishBorders = distinguishBorders;
	}

	public TokenTextCharNGrams (String prefix, int[] gramSizes)
	{
		this.prefix=prefix;
		this.gramSizes = gramSizes;
	}
	
	public TokenTextCharNGrams ()
	{
		this ("CHARBIGRAM=", new int[] {2});
	}

	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.get(i);
			String s = t.getText();
			if (distinguishBorders)
				s = startBorderChar + s + endBorderChar;
			int slen = s.length();
			for (int j = 0; j < gramSizes.length; j++) {
				int size = gramSizes[j];
				for (int k = 0; k < (slen - size)+1; k++)
					t.setFeatureValue ((prefix + s.substring (k, k+size)), 1.0);
			}
		}
		return carrier;
	}
	
	// Serialization

  // Version 0 : Initial (Saved prefix & gram sizes)
  // Version 1 : Save distinguishBorders
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (prefix);
		out.writeInt (gramSizes.length);
		for (int i = 0; i < gramSizes.length; i++)
			out.writeInt (gramSizes[i]);
    out.writeBoolean (distinguishBorders);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		prefix = (String) in.readObject();
		int gsl = in.readInt ();
		if (gsl > 0) {
			gramSizes = new int[gsl];
			for (int i = 0; i < gsl; i++)
				gramSizes[i] = in.readInt();
		}

    if (version >= 1) {
      distinguishBorders = in.readBoolean ();
    }

	}


}
