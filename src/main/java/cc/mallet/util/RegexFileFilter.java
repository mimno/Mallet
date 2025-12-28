/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.util;

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Label;

public class RegexFileFilter implements FileFilter
{
	Pattern absolutePathRegex;
	Pattern nameRegex;
		
	public RegexFileFilter (Pattern absolutePathRegex,
													Pattern filenameRegex)
	{
		this.absolutePathRegex = absolutePathRegex;
		this.nameRegex = filenameRegex;
	}
		
	public RegexFileFilter (String absolutePathRegex,
													String filenameRegex)
	{
		this (absolutePathRegex != null ? Pattern.compile (absolutePathRegex) : null,
					filenameRegex != null ? Pattern.compile (filenameRegex) : null);
	}
		
	public RegexFileFilter (Pattern nameRegex)
	{
		this (null, nameRegex);
	}

	public RegexFileFilter (String filenameRegex)
	{
		this (filenameRegex != null ? Pattern.compile (filenameRegex) : null);
	}

	public boolean accept (File f)
	{
		boolean ret = ((absolutePathRegex == null
										|| absolutePathRegex.matcher(f.getAbsolutePath()).matches())
									 &&
									 (nameRegex == null
										|| nameRegex.matcher(f.getName()).matches()));
		//System.out.println ("RegexFileFilter accept "+f+" nameRegex="+nameRegex.pattern()+" ret="+ret);
		return ret;
	}

}

