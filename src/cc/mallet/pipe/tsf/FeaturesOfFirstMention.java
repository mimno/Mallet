/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Create new features from features (matching a regex within a window +/- the current position.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.io.*;
import java.util.regex.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.PropertyList;

public class FeaturesOfFirstMention extends Pipe implements Serializable
{
	String namePrefix;
	String firstMentionName;				// If not null, add this feature if this token is the first mention
	Pattern featureRegex;									// Matching tokentext are candidates for FIRSTMENTION features
	Pattern filterRegex;									// Matching features from the FIRSTMENTION will be included
	boolean includeFiltered;							// If false, then EXCLUDE feature names that match the pattern

	public FeaturesOfFirstMention (String namePrefix, String firstMentionName, Pattern featureRegex)
	{
		this.namePrefix = namePrefix;
		this.firstMentionName = firstMentionName;
		this.featureRegex = featureRegex;
	}

	public FeaturesOfFirstMention (String namePrefix, Pattern featureRegex,
																 Pattern featureFilterRegex, boolean includeFiltered)
	{
		this (namePrefix, null, featureRegex);
		this.filterRegex = featureFilterRegex;
		this.includeFiltered = includeFiltered;
	}
	
	public FeaturesOfFirstMention (String namePrefix, Pattern featureRegex)
	{
		this (namePrefix, null, featureRegex);
	}
	
	public FeaturesOfFirstMention (String namePrefix)
	{
		this (namePrefix, null);
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		int tsSize = ts.size();
		for (int i = tsSize-1; i >= 0; i--) {
			Token t = ts.getToken (i);
			String text = t.getText();
			if (featureRegex != null && !featureRegex.matcher(text).matches())
				continue;
			for (int j = 0; j < i; j++) {
				if (ts.getToken(j).getText().equals(text)) {
					PropertyList.Iterator iter = ts.getToken(j).getFeatures().iterator();
					while (iter.hasNext()) {
						iter.next();
						String key = iter.getKey();
						if (filterRegex == null || (filterRegex.matcher(key).matches() ^ !includeFiltered))
							t.setFeatureValue (namePrefix+key, iter.getNumericValue());
					}
					break;
				}
				if (firstMentionName != null)
					t.setFeatureValue (firstMentionName, 1.0);
			}
		}
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (namePrefix);
		out.writeObject (featureRegex);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		namePrefix = (String) in.readObject();
		featureRegex = (Pattern) in.readObject();
	}

}
