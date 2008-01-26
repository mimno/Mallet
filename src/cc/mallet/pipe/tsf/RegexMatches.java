/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
		Add feature with value 1.0 if the entire token text matches the
		provided regular expression.

		@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;


public class RegexMatches extends Pipe implements Serializable
{
	Pattern regex;
	String feature;
	
	public RegexMatches (String featureName, Pattern regex)
	{
		this.feature = featureName;
		this.regex = regex;
	}

	// Too dangerous with both arguments having the same type
	//public RegexMatches (String regex, String feature) {
	//this (Pattern.compile (regex), feature);
  //}
	

	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.get(i);
			if (regex.matcher (t.getText()).matches ())
				t.setFeatureValue (feature, 1.0);
		}
		return carrier;
	}


	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(regex);
		out.writeObject(feature);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		regex = (Pattern) in.readObject();
		feature = (String) in.readObject();
	}


}
