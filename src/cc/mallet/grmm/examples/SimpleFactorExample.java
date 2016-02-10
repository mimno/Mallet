/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.examples;

import cc.mallet.grmm.types.*;

/**
 * A simple example to demonstrate the row-major indexing of potential values.
 *
 * Created: Aug 30, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: SimpleFactorExample.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class SimpleFactorExample {

    public static void main (String[] args)
    {
      FactorGraph mdl = new FactorGraph ();
      Variable[] vars = new Variable [] {
              new Variable (2),
              new Variable (2),
              new Variable (3),
              new Variable (2),
              new Variable (2),
      };

      /* Create an edge potential looking like
           VARS[0]   VARS[1]    VALUE
              0         0        0.6
              0         1        1.3
              1         0        0.3
              1         1        2.3
       */
      double[] arr = new double[] { 0.6, 1.3, 0.3, 2.3, };
      mdl.addFactor (vars[0], vars[1], arr);
      System.out.println ("Model with one edge potential:");
      mdl.dump ();

      /* Add a three-clique potential whose values are
      VARS[2]   VARS[3]  VARS[4]    VALUE
         0         0        0         1
         0         0        1         2
         0         1        0         3
         0         1        1         4
         1         0        0        11
         1         0        1        12
         1         1        0        13
         1         1        1        14
         2         0        0        21
         2         0        1        22
         2         1        0        23
         2         1        1        24
         */
      double[] arr2 = { 1, 2, 3, 4, 11, 12, 13, 14, 21, 22, 23, 24 };
      VarSet varSet = new HashVarSet (new Variable[] { vars[2], vars[3], vars[4] });
      Factor ptl = new TableFactor (varSet, arr2);
      mdl.addFactor (ptl);

      System.out.println ("Model with a 3-clique added:");
      mdl.dump ();
    }
}
