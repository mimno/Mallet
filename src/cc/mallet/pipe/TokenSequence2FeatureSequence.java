/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
/**
 * Convert the token sequence in the data field each instance to a feature sequence.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class TokenSequence2FeatureSequence extends Pipe
{
	public TokenSequence2FeatureSequence (Alphabet dataDict)
	{
		super (dataDict, null);
	}

	public TokenSequence2FeatureSequence ()
	{
		super(new Alphabet(), null);
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		FeatureSequence ret =
			new FeatureSequence ((Alphabet)getDataAlphabet(), ts.size());
		for (int i = 0; i < ts.size(); i++) {
			ret.add (ts.getToken(i).getText());
		}
		carrier.setData(ret);
		return carrier;
	}

}
