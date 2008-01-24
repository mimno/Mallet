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
				"import cc.mallet.types.*;"+
				"import cc.mallet.pipe.*;"+
				"import cc.mallet.pipe.iterator.*;"+
				"import cc.mallet.pipe.tsf.*;"+
				"import cc.mallet.classify.*;"+
				"import cc.mallet.extract.*;"+
				"import cc.mallet.fst.*;"+
				"import cc.mallet.optimize.*;");
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
