/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe;


import java.io.*;
import java.net.URI;
import java.util.regex.Pattern;

import cc.mallet.extract.StringSpan;
import cc.mallet.extract.StringTokenization;
import cc.mallet.types.Instance;
import cc.mallet.types.SingleInstanceIterator;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.util.Lexer;

/**
 *  Pipe that tokenizes a character sequence.  Expects a CharSequence
 *   in the Instance data, and converts the sequence into a token
 *   sequence using the given regex or CharSequenceLexer.  
 *   (The regex / lexer should specify what counts as a token.)
 */
public class CharSequence2TokenSequence extends Pipe implements Serializable
{
	Pattern regex;

	public CharSequence2TokenSequence (String regex)
	{
		this.regex = Pattern.compile(regex);
	}

	public CharSequence2TokenSequence (Pattern regex)
	{
		this.regex = regex;
	}

	public CharSequence2TokenSequence ()
	{
	}

	public Instance pipe (Instance carrier)
	{

		CharSequenceLexer lexer = new CharSequenceLexer(regex);

		CharSequence string = (CharSequence) carrier.getData();
		lexer.setCharSequence (string);
		TokenSequence ts = new StringTokenization (string);
		while (lexer.hasNext()) {
			lexer.next();
			ts.add (new StringSpan (string, lexer.getStartOffset (), lexer.getEndOffset ()));
		}
		carrier.setData(ts);
		return carrier;
	}

	public static void main (String[] args)
	{
		try {
			for (int i = 0; i < args.length; i++) {
				Instance carrier = new Instance (new File(args[i]), null, null, null);
				SerialPipes p = new SerialPipes (new Pipe[] {
					new Input2CharSequence (),
					new CharSequence2TokenSequence(Pattern.compile("[\\p{L}\\p{N}_]+"))});
				carrier = p.newIteratorFrom (new SingleInstanceIterator(carrier)).next();
				TokenSequence ts = (TokenSequence) carrier.getData();
				System.out.println ("===");
				System.out.println (args[i]);
				System.out.println (ts.toString());
			}
		} catch (Exception e) {
			System.out.println (e);
			e.printStackTrace();
		}
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(regex);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		regex = (Pattern) in.readObject();
	}


	
}
