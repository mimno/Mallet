/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
		Count the number of times the provided regular expression matches
		the token text, and add a feature with the provided name having
		value equal to the count.

		@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class CountMatchesAlignedWithOffsets extends Pipe
{
	Pattern regex;
	String feature;
	int[] offsets;
	boolean normalizeByMatchCount = false;
	
	public CountMatchesAlignedWithOffsets (String featureName,
																				 Pattern regex, int[] offsets,
																				 boolean normalizeByMatchCount)
	{
		this.feature = featureName;
		this.regex = regex;
		this.offsets = offsets;
		this.normalizeByMatchCount = normalizeByMatchCount;
	}

	public CountMatchesAlignedWithOffsets (String featureName,
																				 Pattern regex, int[] offsets)
	{
		this (featureName, regex, offsets, false);
	}
	

	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		int countMatches;
		int countAlignedMatches;
		for (int i = 0; i < ts.size(); i++) {
			countMatches = 0;
			countAlignedMatches = 0;
			Token t = ts.getToken(i);
			Matcher matcher = regex.matcher (t.getText());
			while (matcher.find ()) {
				countMatches++;
				int position = matcher.start();
				for (int j = 0; j < offsets.length; j++) {
					int offset = i + offsets[j];
					if (offset >= 0 && offset < ts.size()) {
						String offsetText = ts.getToken(offset).getText();
						if (offsetText.length() > position) {
							Matcher offsetMatcher =
								regex.matcher (offsetText.substring(position));
							if (offsetMatcher.lookingAt())
								countAlignedMatches++;
						}
					}
				}
			}
			if (countAlignedMatches > 0)
				t.setFeatureValue (feature, (normalizeByMatchCount
																		 ? ((double)countAlignedMatches)/countMatches
																		 : (double)countAlignedMatches));
		}
		return carrier;
	}


}
