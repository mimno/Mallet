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
	 Given a string, return only the portion of the string inside a regex parenthesized group.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class CharSubsequence extends Pipe implements Serializable
{
	Pattern regex;
	int groupIndex;

	// xxx Yipes, this only works for UNIX-style newlines.
	// Anyone want to generalize it to Windows, etc?
	public static final Pattern SKIP_HEADER = Pattern.compile ("\\n\\n(.*)\\z", Pattern.DOTALL);
	
	public CharSubsequence (Pattern regex, int groupIndex)
	{
		this.regex = regex;
		this.groupIndex = groupIndex;
	}

	public CharSubsequence (Pattern regex)
	{
		this (regex, 1);
	}
		
	public Instance pipe (Instance carrier)
	{
		CharSequence string = (CharSequence) carrier.getData();
		Matcher m = regex.matcher(string);
		if (m.find()) {
			//System.out.println ("CharSubsequence found match");
			carrier.setData(m.group(groupIndex));
			return carrier;
		} else {
			//System.out.println ("CharSubsequence found no match");
			carrier.setData("");
			return carrier;
		}
	}

	//Serialization
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(regex);
		out.writeInt(groupIndex);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		regex = (Pattern) in.readObject();
		groupIndex = in.readInt();
	}


}
