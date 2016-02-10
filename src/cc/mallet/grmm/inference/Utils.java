/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;


import java.util.Iterator;

import cc.mallet.grmm.types.*;
import cc.mallet.types.MatrixOps;

import gnu.trove.THashSet;

/**
 * A bunch of static utilities useful for dealing with Inferencers.
 * Created: Jun 1, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Utils.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class Utils {

  /**
   * Returns ths value of -log Z in mdl according to the given inferencer.
   * If inf is exact, the answer will be exact; otherwise the answer will be
   * approximation
   *
   * @param mdl
   * @param inf An inferencer.  <tt>inf.computeMarginals (mdl)</tt> must already have
   *            been called.
   * @return The value of -logZ
   */
  public static double lookupMinusLogZ (FactorGraph mdl, Inferencer inf)
  {
    // heuristically try to find a reasonable assignment (not numerically 0 prob)
    int [] vals = new int[mdl.numVariables()];
    for (int vi = 0; vi < vals.length; vi++) {
      Variable var = mdl.getVariable (vi);
      Factor mrg = inf.lookupMarginal (var);
      vals[vi] = mrg.argmax(); 
    }

    Assignment assn = new Assignment (mdl, vals);
    double prob = inf.lookupLogJoint (assn);
    double energy = mdl.logValue (assn);
    return prob - energy;
  }

  public static double localMagnetization (Inferencer inferencer, Variable var)
  {
    if (var.getNumOutcomes () != 2)
      throw new IllegalArgumentException ();

    Factor marg = inferencer.lookupMarginal (var);
    AssignmentIterator it = marg.assignmentIterator ();
    double v1 = marg.value (it); it.advance ();
    double v2 = marg.value (it);
    return v1 - v2;
  }

  public static double[] allL1MarginalDistance (FactorGraph mdl, Inferencer inf1, Inferencer inf2)
  {
    double[] dist = new double [mdl.numVariables ()];

    int i = 0;
    for (Iterator it = mdl.variablesIterator (); it.hasNext();) {
      Variable var = (Variable) it.next ();
      Factor bel1 = inf1.lookupMarginal (var);
      Factor bel2 = inf2.lookupMarginal (var);
      dist[i++] = Factors.oneDistance (bel1, bel2);
    }

    return dist;
  }

  public static double avgL1MarginalDistance (FactorGraph mdl, Inferencer inf1, Inferencer inf2)
  {
    double[] dist = allL1MarginalDistance (mdl, inf1, inf2);
    return MatrixOps.mean (dist);
  }

  public static double maxL1MarginalDistance (FactorGraph mdl, Inferencer inf1, Inferencer inf2)
  {
    double[] dist = allL1MarginalDistance (mdl, inf1, inf2);
    return MatrixOps.max (dist);
  }

  public static int[] toSizesArray (Variable[] vars)
  {
    int[] szs = new int [vars.length];
    for (int i = 0; i < vars.length; i++) {
      szs[i] = vars[i].getNumOutcomes ();
    }
    return szs;
  }

  public static VarSet defaultIntersection (VarSet v1, VarSet v2)
  {// Grossly inefficient implementation
    THashSet hset = new THashSet (v1);
    hset.retainAll (v2);
    Variable[] ret = new Variable [hset.size ()];

    int vai = 0;
    for (int vi = 0; vi < v1.size(); vi++) {
      Variable var = v1.get (vi);
      if (hset.contains (var)) { ret[vai++] = var; }
    }

    return new HashVarSet (ret);
  }
}
