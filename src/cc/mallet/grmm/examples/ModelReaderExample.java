/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.examples;

import cc.mallet.grmm.types.*;
import cc.mallet.grmm.inference.*;
import cc.mallet.grmm.util.ModelReader;
import cc.mallet.grmm.util.ModelWriter;
import java.io.*;

// A simple example of the (probably too simple) ModelReader class

public class ModelReaderExample {

    public static String mdlExample = 
	"VAR var0 : 2\n" +
	"VAR var1 : 2\n" +
	"VAR var2 : 2\n" +
	"VAR var3 : 2\n\n" +

	"var0 var1 ~ BinaryPair 1\n" +
	"var1 var2 ~ BinaryPair 1.1\n" +
	"var2 var3 ~ BinaryPair -1.3\n" +
	"var3 var0 ~ BinaryPair 0.9\n" +

	"var0 ~ Unary 1.0\n" +
	"var1 ~ Unary -0.5\n" +
	"var2 ~ Unary 0.75\n" +
	"var3 ~ Unary 0.1\n";
 
   public static void main (String[] args) throws Exception {
       BufferedReader r = new BufferedReader (new StringReader (mdlExample));
       FactorGraph fg = new ModelReader().readModel (r);
       fg.dump();

       Inferencer inf = new JunctionTreeInferencer();
       inf.computeMarginals (fg);
       for (int vi = 0; vi < 4; vi++) {
	    Variable var = fg.getVariable (vi);
	    System.out.println (inf.lookupMarginal (var).dumpToString());
       }

       System.out.println("+++++++++");
       OutputStreamWriter osw = new OutputStreamWriter(System.out);
       ModelWriter.writeModel (fg, osw);
       osw.close();
    }
}