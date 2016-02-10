/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;


import java.io.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import cc.mallet.grmm.types.*;

import gnu.trove.THashMap;
import bsh.Interpreter;
import bsh.EvalError;

/**
 * $Id: ModelReader.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class ModelWriter {

    public static void writeModel (FactorGraph fg, Writer w) 
    {
	try {
	    writeVariables (fg, w);
	    w.write ("\n");
	    writeFactors (fg, w);
	} catch (Exception e) {
	    throw new RuntimeException (e);
	}
    }

    private static void writeVariables (FactorGraph fg, Writer w)  throws IOException
    {
	for (int vi = 0; vi < fg.numVariables(); vi++) {
	    Variable var = fg.getVariable (vi);
	    int nOuts = var.getNumOutcomes ();
	    String outStr = nOuts == Variable.CONTINUOUS ? "continuous" : Integer.toString(nOuts);
	    w.write("VAR " + var.getLabel() + " : " + outStr + "\n");
	}
    }

    private static void writeFactors (FactorGraph fg, Writer w) throws IOException
    {
	for (int fi = 0; fi < fg.factors().size(); fi++) {
	    Factor f = fg.getFactor (fi);
	    w.write (f.prettyOutputString ());
	    w.write ("\n");
	}
    }

}
