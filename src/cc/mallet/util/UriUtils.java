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

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URI;
import java.io.File;

public class UriUtils
{
	// This regex induced from http://www.ietf.org/rfc/rfc2396.txt
	static Pattern schemeRegex = Pattern.compile ("\\p{Alpha}[\\p{Alnum}\\+\\.-]*:");

	private static String defaultFileSchema (String string)
	{
		// If "string" does not have a URI scheme (e.g. "file:" or "http:")
		// then assume a default of "file:" and add it.
		Matcher matcher = schemeRegex.matcher (string);
		if (!matcher.lookingAt())
			string = "file:" + string;
		return string;
	}

	public static URI objectToUri (Object obj)
	{
		try {
			if (obj instanceof String)
				// If the string has no schema, assume that it is the "file:" schema
				return new URI (defaultFileSchema((String)obj));
			if (obj instanceof File)
				return new URI ("file:" + ((File)obj).getAbsolutePath());
			else
				return new URI (obj.toString());
		} catch (Exception e) {
			throw new IllegalArgumentException ("UriUtils.objectToUri: " + e.toString());
		}
	}

	/** Convert a string-representation of a URI into a string
			that could be a filename.

			Do this by substituting '/' for '+', and '++' for '+'.  For
			example, "http://www.cs.umass.edu/faculty" becomes
			"http:++www.cs.umass.edu+faculty". */

	public static String uriStringToFilename (String uri)
	{
		StringBuffer sb = new StringBuffer();
		char c;
		for (int i = 0; i < uri.length(); i++) {
			c = uri.charAt(i);
			if (c == File.pathSeparatorChar)
				sb.append ('+');
			else if (c == '+')
				sb.append ("++");
			else
				sb.append (c);
		}
		return sb.toString();
	}

	public static String filenameToUriString (String filename)
	{
		StringBuffer sb = new StringBuffer();
		char c;
		for (int i = 0; i < filename.length(); i++) {
			c = filename.charAt(i);
			if (c == '+') {
				if (i < filename.length() && filename.charAt(i+1) == '+') {
					sb.append ('+');
					i++;
				} else
					sb.append (File.pathSeparator);
			} else
				sb.append (c);
		}
		return sb.toString();
	}

}
