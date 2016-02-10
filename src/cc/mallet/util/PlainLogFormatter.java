/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */



/** 
		This formmater extends and replaces the SimpleFormatter provided by java.
		It just writes out the message with no adornments.

		@author David Pinto <a href="mailto:pinto@cs.umass.edu">pinto@cs.umass.edu</a>
 */

package cc.mallet.util;

import java.util.logging.*;

public class PlainLogFormatter extends SimpleFormatter {

	public PlainLogFormatter() {
		super();
	}

	public String format (LogRecord record) {
		return record.getMessage()+ "\n";
	}
}
