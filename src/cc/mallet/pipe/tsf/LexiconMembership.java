/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Tests membership of the token text in the provided list of words.
	 The lexicon words are provided in a file, one word per line.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.io.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class LexiconMembership extends Pipe implements Serializable
{
	String name;
	gnu.trove.THashSet lexicon;
	boolean ignoreCase;
	
	public LexiconMembership (String name, Reader lexiconReader, boolean ignoreCase)
	{
		this.name = name;
		this.lexicon = new gnu.trove.THashSet ();
		this.ignoreCase = ignoreCase;
		LineNumberReader reader = new LineNumberReader (lexiconReader);
		String line;
		while (true) {
			try {
				line = reader.readLine();
			} catch (IOException e) {
				throw new IllegalStateException ();
			}
			if (line == null) {
				break;
			} else {
			//	System.out.println(name + " : " + (ignoreCase ? line.toLowerCase().intern() : line.intern()) );
				lexicon.add (ignoreCase ? line.toLowerCase().intern() : line.intern());
			}
		}
		if (lexicon.size() == 0)
			throw new IllegalArgumentException ("Empty lexicon");
	}

	public LexiconMembership (String name, File lexiconFile, boolean ignoreCase) throws FileNotFoundException
	{
		this (name, new BufferedReader (new FileReader (lexiconFile)), ignoreCase);
	}

	public LexiconMembership (File lexiconFile, boolean ignoreCase) throws FileNotFoundException
	{
		this (lexiconFile.getName(), lexiconFile, ignoreCase);
	}

	public LexiconMembership (File lexiconFile) throws FileNotFoundException
	{
		this (lexiconFile.getName(), lexiconFile, true);
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.get(i);
			String s = t.getText();
			String conS=s;
			//dealing with ([a-z]+), ([a-z]+, [a-z]+), [a-z]+.
			if(conS.startsWith("("))
				conS = conS.substring(1);
			if(conS.endsWith(")") || conS.endsWith("."))
				conS = conS.substring(0, conS.length()-1);
			if (lexicon.contains (ignoreCase ? s.toLowerCase() : s))
				t.setFeatureValue (name, 1.0);
			if(conS.compareTo(s) != 0) {
				if (lexicon.contains (ignoreCase ? conS.toLowerCase() : conS))
					t.setFeatureValue (name, 1.0);
			}
		}
		return carrier;
	}
	
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (name);
		out.writeObject (lexicon);
		out.writeBoolean (ignoreCase);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		this.name = (String) in.readObject();
		this.lexicon = (gnu.trove.THashSet) in.readObject();
		this.ignoreCase = in.readBoolean();
	}


}
