/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
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
public class ModelReader {

  private static THashMap allClasses;
  static {
    allClasses = new THashMap ();
    // add new classes here
    allClasses.put ("potts", PottsTableFactor.class);
    allClasses.put ("unary", BoltzmannUnaryFactor.class);
    allClasses.put ("binaryunary", BinaryUnaryFactor.class);
    allClasses.put ("binarypair", BoltzmannPairFactor.class);
    allClasses.put ("uniform", UniformFactor.class);
    allClasses.put ("normal", UniNormalFactor.class);
    allClasses.put ("beta", BetaFactor.class);
  }

  private THashMap name2var = new THashMap ();

  public static Assignment readFromMatrix (VarSet vars, Reader in) throws IOException
  {
    Variable[] varr = vars.toVariableArray ();
    Interpreter interpreter = new Interpreter ();
    BufferedReader bIn = new BufferedReader (in);
    Assignment assn = new Assignment ();
    String line;

    while ((line = bIn.readLine ()) != null) {
      String[] fields = line.split ("\\s+");
      Object[] vals = new Object [fields.length];
      for (int i = 0; i < fields.length; i++) {
        try {
          vals[i] = interpreter.eval (fields[i]);
        } catch (EvalError e) {
          throw new RuntimeException ("Error reading line: "+line, e);
        }
      }
      assn.addRow (varr, vals);
    }

    return assn;
  }


  public FactorGraph readModel (BufferedReader in) throws IOException
  {
    List factors = new ArrayList ();

    String line;
    while ((line = in.readLine ()) != null) {
      try {
	if (Pattern.matches ("^\\s*$", line)) { continue; }
        String[] fields = line.split ("\\s+");
        if (fields[0].equalsIgnoreCase ("VAR")) {
          // a variable declaration
          handleVariableDecl (fields);
        } else {
          // a factor line
          Factor factor = factorFromLine (fields);
          factors.add (factor);
        }
      } catch (Exception e) {
        throw new RuntimeException ("Error reading line:\n"+line, e);
      }
    }

    FactorGraph fg = new FactorGraph ();
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor factor = (Factor) it.next ();
      fg.multiplyBy (factor);
    }

    return fg;
  }

  private void handleVariableDecl (String[] fields)
  {
    int colonIdx = findColon (fields);

    if (fields.length != colonIdx + 2) throw new IllegalArgumentException ("Invalid syntax");

    String numOutsString = fields[colonIdx+1];
    int numOutcomes;
    if (numOutsString.equalsIgnoreCase ("continuous")) {
      numOutcomes = Variable.CONTINUOUS;
    } else {
      numOutcomes = Integer.parseInt (numOutsString);
    }

    for (int i = 0; i < colonIdx; i++) {
      String name = fields[i];
      Variable var = new Variable (numOutcomes);
      var.setLabel (name);
      name2var.put (name, var);
    }
  }

  private int findColon (String[] fields)
  {
    for (int i = 0; i < fields.length; i++) {
      if (fields[i].equals (":")) {
        return i;
      }
    }
    throw new IllegalArgumentException ("Invalid syntax.");
  }

  private Factor factorFromLine (String[] fields)
  {
    int idx = findTwiddle (fields);
    return constructFactor (fields, idx);
  }

  private int findTwiddle (String[] fields)
  {
    for (int i = 0; i < fields.length; i++) {
      if (fields[i].equals ("~")) {
        return i;
      }
    }
    return -1;
  }

  private Factor constructFactor (String[] fields, int idx)
  {
    Class factorClass = determineFactorClass (fields, idx);
    Object[] args = determineFactorArgs (fields, idx);
    Constructor factorCtor = findCtor (factorClass, args);
    Factor factor;
    try {
      factor = (Factor) factorCtor.newInstance (args);
    } catch (InstantiationException e) {
      throw new RuntimeException (e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException (e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException (e);
    }
    return factor;
  }

  private Constructor findCtor (Class factorClass, Object[] args)
  {
    Class[] argClass = new Class[args.length];
    for (int i = 0; i < args.length; i++) {
      argClass[i] = args[i].getClass ();
      // special case
      if (argClass[i] == Double.class) { argClass[i] = double.class; }
    }
    try {
      return factorClass.getDeclaredConstructor (argClass);
    } catch (NoSuchMethodException e) {
	StringBuffer buf = new StringBuffer("Invalid argments for factor "+factorClass+"\n");
	buf.append ("Args were:\n");
	for (int i = 0; i < args.length; i++) { 
	    buf.append(args[i]);
	    buf.append(" ");
	}
	buf.append("\n");
	for (int i = 0; i < args.length; i++) { 
	    buf.append(args[i].getClass());
	    buf.append(" ");
	}
	buf.append("\n");
	throw new RuntimeException (buf.toString());
    }
  }

  private Class determineFactorClass (String[] fields, int twiddleIdx)
  {
    String factorName = fields [twiddleIdx + 1].toLowerCase ();
    Class theClass = (Class) allClasses.get (factorName);
    if (theClass != null) {
      return theClass;
    } else {
      throw new RuntimeException ("Could not determine factor class from "+factorName);
    }
  }

  private Object[] determineFactorArgs (String[] fields, int twiddleIdx)
  {
    List args = new ArrayList (fields.length);
    for (int i = 0; i < twiddleIdx; i++) {
      args.add (varFromName (fields[i], true));
    }

    for (int i = twiddleIdx+2; i < fields.length; i++) {
      args.add (varFromName (fields[i], false));
    }

    return args.toArray ();
  }

<<<<<<< /disk/scratch/umass/clone/mallet/src/cc/mallet/grmm/util/ModelReader.java
    private static Pattern nbrRegex = Pattern.compile ("[+-]?\\d+(?:\\.\\d+)?(E[+-]\\d+)?");
=======
    private static Pattern nbrRegex = Pattern.compile ("[+-]?\\d+(?:\\.\\d+)?");
>>>>>>> /tmp/ModelReader.java~other.Tl9Fy2

  private Object varFromName (String name, boolean preTwiddle)
  {
    if (nbrRegex.matcher(name).matches ()) {
      return new Double (Double.parseDouble (name));
    } else if (name2var.contains (name)) {
      return name2var.get (name);
    } else {
      Variable var = (preTwiddle) ? new Variable (2) : new Variable (Variable.CONTINUOUS);
      var.setLabel (name);
      name2var.put (name, var);
      return var;
    }
  }


}
