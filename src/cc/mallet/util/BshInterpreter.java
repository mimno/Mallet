/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


package cc.mallet.util;

import java.io.*;
import java.util.*;
import bsh.Interpreter;

public class BshInterpreter extends bsh.Interpreter
{
	Interpreter interpreter;

	public BshInterpreter (String prefixCommands)
	{
		try {
			eval (
				"import java.util.*;"+
				"import java.util.regex.*;"+
				"import java.io.*;"+
				"import edu.umass.cs.mallet.base.types.*;"+
				"import edu.umass.cs.mallet.base.pipe.*;"+
				"import edu.umass.cs.mallet.base.pipe.iterator.*;"+
				"import edu.umass.cs.mallet.base.pipe.tsf.*;"+
				"import edu.umass.cs.mallet.base.classify.*;"+
				"import edu.umass.cs.mallet.base.extract.*;"+
				"import edu.umass.cs.mallet.base.fst.*;"+
				"import edu.umass.cs.mallet.base.minimize.*;");
			if (prefixCommands != null)
				eval (prefixCommands);
		} catch (bsh.EvalError e) {
			throw new IllegalArgumentException ("bsh Interpreter error: "+e);
		}
	}

	public BshInterpreter ()
	{
		this (null);
	}
	
}
