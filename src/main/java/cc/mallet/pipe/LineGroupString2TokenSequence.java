/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Aron Culotta <a
	 href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a> Takes a
	 (possibly) multi-line String and creates one token for each line,
	 where token.getText holds the contents of the line.
	 e.g.
	 input:
	 Instance.data = "
	 PERSON John NN
	 O kicked V
	 0 the
	 0 ball
	 "
	 output:
	 TokenSequence ts = (TokenSequence)Instance.data:
	 ts.getToken(0).getText = "PERSON John NN";
	 ts.getToken(1).getText = "0 kicked V";
	 ...	 
*/

package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.util.Lexer;

public class LineGroupString2TokenSequence extends Pipe implements Serializable
{
	CharSequenceLexer lexer;
	
	public LineGroupString2TokenSequence ()
	{
	}

	public Instance pipe (Instance carrier)
	{
		
		if (!(carrier.getData() instanceof CharSequence)) 
			throw new IllegalArgumentException ();
		String s = carrier.getData().toString();
		String[] lines = s.split (System.getProperty ("line.separator"));
		carrier.setData (new TokenSequence (lines));
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
