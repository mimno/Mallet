/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.examples;


import java.util.Random;

import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.grmm.inference.JunctionTreeInferencer;
import cc.mallet.grmm.types.*;

/**
 * Created: Aug 13, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: SimpleGraphExample.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class SimpleGraphExample {

  public static void main (String[] args)
  {

    // STEP 1: Create the graph

    Variable[] allVars = {
      new Variable (2),
      new Variable (2),
      new Variable (2),
      new Variable (2)
    };

    FactorGraph mdl = new FactorGraph (allVars);

    // Create a diamond graph, with random potentials
    Random r = new Random (42);
    for (int i = 0; i < allVars.length; i++) {
      double[] ptlarr = new double [4];
      for (int j = 0; j < ptlarr.length; j++)
        ptlarr[j] = Math.abs (r.nextDouble ());

      Variable v1 = allVars[i];
      Variable v2 = allVars[(i + 1) % allVars.length];
      mdl.addFactor (v1, v2, ptlarr);
    }

    // STEP 2: Compute marginals

    Inferencer inf = new JunctionTreeInferencer ();
    inf.computeMarginals (mdl);

    // STEP 3: Collect the results
    //   We'll just print them out

    for (int varnum = 0; varnum < allVars.length; varnum++) {
      Variable var = allVars[varnum];
      Factor ptl = inf.lookupMarginal (var);
      for (AssignmentIterator it = ptl.assignmentIterator (); it.hasNext (); it.advance()) {
        int outcome = it.indexOfCurrentAssn ();
        System.out.println (var+"  "+outcome+"   "+ptl.value (it));
      }
      System.out.println ();
    }

  }

}
