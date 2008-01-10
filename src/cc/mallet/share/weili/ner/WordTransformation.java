/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
   @author Wei Li <a href="mailto:weili@cs.umass.edu">weili@cs.umass.edu</a>
 */

package cc.mallet.share.weili.ner;

import java.util.regex.*;
import java.io.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class WordTransformation
{
	static final String[] endings = new String[]
	{"ing", "ed", "ogy", "s", "ly", "ion", "tion", "ity", "ies"};
	static Pattern[] endingPatterns = new Pattern[endings.length];
	static final String[][][] endingNames = new String[2][3][endings.length];
	{
		for (int i = 0; i < endings.length; i++) {
			endingPatterns[i] = Pattern.compile (".*"+endings[i]+"$");
			for (int j = 0; j < 3; j++) {
				for (int k = 0; k < 2; k++)
					endingNames[k][j][i] = "W"+(k==1?"-":"")+j+"=<END"+endings[i]+">";
			}
		}
	}

	boolean doSpelling;
	boolean doDigitCollapses;
	boolean doDowncasing;

	public WordTransformation ()
	{
		this (false, true, false);
	}
	
	public WordTransformation (boolean doSpelling, boolean doDigitCollapses, boolean doDowncasing)
	{
		this.doSpelling = doSpelling;
		this.doDigitCollapses = doDigitCollapses;
		this.doDowncasing = doDowncasing;
	}
	
	public Token transformedToken (String original)
	{
		boolean [][] ending = new boolean[3][endings.length];
		boolean [][] endingp1 = new boolean[3][endings.length];
		boolean [][] endingp2 = new boolean[3][endings.length];
		String word = original;
		
		if (doDigitCollapses) {
			if (word.matches ("19\\d\\d"))
				word = "<YEAR>";
			else if (word.matches ("19\\d\\ds"))
				word = "<YEARDECADE>";
			else if (word.matches ("19\\d\\d-\\d+"))
				word = "<YEARSPAN>";
			else if (word.matches ("\\d+\\\\/\\d"))
				word = "<FRACTION>";
			else if (word.matches ("\\d[\\d,\\.]*"))
				word = "<DIGITS>";
			else if (word.matches ("19\\d\\d-\\d\\d-\\d--d"))
				word = "<DATELINEDATE>";
			else if (word.matches ("19\\d\\d-\\d\\d-\\d\\d"))
				word = "<DATELINEDATE>";
			else if (word.matches (".*-led"))
				word = "<LED>";
			else if (word.matches (".*-sponsored"))
				word = "<LED>";
		}

		if (doDowncasing) word = word.toLowerCase();

		Token token = new Token (word);
			
		if (doSpelling) {
			for (int j = 0; j < endings.length; j++) {
				ending[2][j] = ending[1][j];
				ending[1][j] = ending[0][j];
				ending[0][j] = endingPatterns[j].matcher(word).matches();
				if (ending[0][j]) token.setFeatureValue (endingNames[0][0][j], 1);
			}
		}

		return token;
	}
}
