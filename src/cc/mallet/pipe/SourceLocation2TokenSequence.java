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
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.util.Lexer;
/**
 * Read from File or BufferedRead in the data field and produce a TokenSequence.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class SourceLocation2TokenSequence extends Pipe implements Serializable
{
	CharSequenceLexer lexer;
	
	public SourceLocation2TokenSequence (CharSequenceLexer lexer)
	{
		this.lexer = lexer;
	}

	public Instance pipe (Instance carrier)
	{
		try {
			if (carrier.getData() instanceof File)
				carrier.setData(pipe ((File)carrier.getData()));
			else if (carrier.getData() instanceof BufferedReader)
				carrier.setData(pipe ((BufferedReader)carrier.getData()));
			else
				throw new IllegalArgumentException ("Doesn't handle class "+carrier.getClass());
		} catch (IOException e) {
			throw new IllegalArgumentException ("IOException");
		}
		return carrier;
	}

	public TokenSequence pipe (File file)
		throws java.io.FileNotFoundException, java.io.IOException
	{
		return pipe (new BufferedReader (new FileReader (file)));
	}

	public TokenSequence pipe (BufferedReader br)
		throws java.io.IOException
	{
		final int BUFSIZE = 2048;
		char[] buf = new char[BUFSIZE];
		int count;
		StringBuffer sb = new StringBuffer (BUFSIZE);
		do {
			count = br.read (buf, 0, BUFSIZE);
			sb.append (buf);
		} while (count == BUFSIZE);
		lexer.setCharSequence ((CharSequence)sb);
		TokenSequence ts = new TokenSequence ();
		while (lexer.hasNext())
			ts.add (new Token ((String) lexer.next()));
		return ts;
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
