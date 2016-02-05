/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;

/**
 *  Convert the token sequence in the data field to a token sequence of ngrams.
		@author Don Metzler <a href="mailto:metzler@cs.umass.edu">metzler@cs.umass.edu</a>
*/

public class TokenSequenceNGrams extends Pipe implements Serializable
{
	int [] gramSizes = null;
    
	public TokenSequenceNGrams (int [] sizes)
	{
		this.gramSizes = sizes;
	}
	
	public Instance pipe (Instance carrier)
	{
		String newTerm = null;
		TokenSequence tmpTS = new TokenSequence();
		TokenSequence ts = (TokenSequence) carrier.getData();

		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.get(i);
			for(int j = 0; j < gramSizes.length; j++) {
				int len = gramSizes[j];
				if (len <= 0 || len > (i+1)) continue;
				if (len == 1) { tmpTS.add(t); continue; }
				newTerm = new String(t.getText());
				for(int k = 1; k < len; k++)
					newTerm = ts.get(i-k).getText() + "_" + newTerm;
				tmpTS.add(newTerm);
			}
		}

		carrier.setData(tmpTS);

		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeInt (gramSizes.length);
		for (int i = 0; i < gramSizes.length; i++)
			out.writeInt (gramSizes[i]);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		int size = in.readInt();
		gramSizes = new int[size];
		for (int i = 0; i < size; i++)
			gramSizes[i] = in.readInt();
	}

}
