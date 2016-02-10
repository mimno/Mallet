/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.util;

/**
 * Static utility methods that didn't seem to belong anywhere else.
 *
 * Created: Tue Mar 30 14:29:57 2004
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: GeneralUtils.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class GeneralUtils {

	private GeneralUtils () {} // No instances
	
	public static String classShortName (Object obj)
	{
		String classname = obj.getClass().getName();
		int dotidx = classname.lastIndexOf ('.');
		String shortname = classname.substring (dotidx+1);
		return shortname;
	}

} // Utils
