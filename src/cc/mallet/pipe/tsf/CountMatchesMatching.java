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
import java.util.logging.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

public class CountMatchesMatching extends Pipe
{
	private static Logger logger = MalletLogger.getLogger(CountMatchesMatching.class.getName());
	String feature;
	Pattern regex;
	Pattern moreSpecificRegex;
	boolean normalizeByRegexMatches = false;
	
	public CountMatchesMatching (String featureName,
															 Pattern regex, Pattern moreSpecificRegex,
															 boolean normalizeByRegexMatches)
	{
		this.feature = featureName;
		this.regex = regex;
		this.moreSpecificRegex = regex;
		this.normalizeByRegexMatches = normalizeByRegexMatches;
	}

	public CountMatchesMatching (String featureName, Pattern regex, Pattern moreSpecificRegex)
	{
		this (featureName, regex, moreSpecificRegex, false);
	}
	

	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		int count;
		int moreSpecificCount;
		for (int i = 0; i < ts.size(); i++) {
			count = 0;
			moreSpecificCount = 0;
			Token t = ts.getToken(i);
			Matcher matcher = regex.matcher (t.getText());
			while (matcher.find()) {
				count++;
				logger.info ("CountMatchesMatching found >"+matcher.group()+"<");
				Matcher moreSpecificMatcher = moreSpecificRegex.matcher (t.getText().substring(matcher.start()));
				if (moreSpecificMatcher.lookingAt ()) {
					moreSpecificCount++;
					logger.info ("CountMatchesMatching sound >"+moreSpecificMatcher.group()+"<");
				}
			}
			if (moreSpecificCount > 0)
				t.setFeatureValue (feature, (normalizeByRegexMatches
																		 ? ((double)moreSpecificCount)/count
																		 : moreSpecificCount));
		}
		return carrier;
	}


}
