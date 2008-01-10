/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.util.regex.*;
import java.io.*;

import cc.mallet.types.Instance;
/**
	 Given a string, repeatedly look for matches of the regex, and
	 replace the entire match with the given replacement string.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class CharSequenceReplace extends Pipe implements Serializable
{
	public static final Pattern SKIP_SGML = Pattern.compile ("<[^>]*>");
	
	Pattern regex;
	String replacement;

	// xxx Yipes, this only works for UNIX-style newlines.
	// Anyone want to generalize it to Windows, etc?
	public static final Pattern SKIP_HEADER = Pattern.compile ("\\n\\n(.*)\\z", Pattern.DOTALL);
	
	public CharSequenceReplace (Pattern regex, String replacement)
	{
		this.regex = regex;
		this.replacement = replacement;
	}

	public Instance pipe (Instance carrier)
	{
		String string = ((CharSequence)carrier.getData()).toString();
		Matcher m = regex.matcher(string);
		carrier.setData(m.replaceAll (replacement));
		return carrier;
	}

	//Serialization
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(regex);
		out.writeObject(replacement);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		regex = (Pattern) in.readObject();
		replacement = (String) in.readObject();
	}


}
