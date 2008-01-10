/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Convert a String containing space-separated feature-name floating-point-value pairs
	 into a FeatureVector.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe;

import java.io.*;
import java.net.URI;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.util.Lexer;
/**
 * Unimplemented.
 */

public class FeatureValueString2FeatureVector extends Pipe implements Serializable
{
	public FeatureValueString2FeatureVector (Alphabet dataDict)
	{
		super (dataDict, null);
	}

	public FeatureValueString2FeatureVector ()
	{
		super(new Alphabet(), null);
	}
	
	public Instance pipe (Instance carrier)
	{
		throw new UnsupportedOperationException ("Not yet implemented");
	}
	
}

