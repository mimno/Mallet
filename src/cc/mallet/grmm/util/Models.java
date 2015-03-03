/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;


import java.util.*;

import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.grmm.inference.JunctionTree;
import cc.mallet.grmm.inference.JunctionTreeInferencer;
import cc.mallet.grmm.types.*;

import gnu.trove.set.hash.THashSet;

/**
 * Static utilities that do useful things with factor graphs.
 *
 * Created: Sep 22, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Models.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class Models {

  /**
   * Returns a new factor graph, the same as a given one, except that all the nodes in
   *  the given Assignment are clamped as evidence.
   * @param mdl Old model.  Will not be modified.
   * @param assn Evidence to add
   * @return A new factor graph.
   */
  public static FactorGraph addEvidence (FactorGraph mdl, Assignment assn)
  {
    return addEvidence (mdl, assn, null);
  }

  public static FactorGraph addEvidence (FactorGraph mdl, Assignment assn, Map toSlicedMap)
  {
    FactorGraph newMdl = new FactorGraph (mdl.numVariables ());
    addSlicedPotentials (mdl, newMdl, assn, toSlicedMap);
    return newMdl;
  }

  public static UndirectedModel addEvidence (UndirectedModel mdl, Assignment assn)
  {
    UndirectedModel newMdl = new UndirectedModel (mdl.numVariables ());
    addSlicedPotentials (mdl, newMdl, assn, null);
    return newMdl;
  }

  private static void addSlicedPotentials (FactorGraph fromMdl, FactorGraph toMdl, Assignment assn, Map toSlicedMap)
  {
    Set inputVars = new THashSet (Arrays.asList (assn.getVars ()));
    Set remainingVars = new THashSet (fromMdl.variablesSet ());
    remainingVars.removeAll (inputVars);
    for (Iterator it = fromMdl.factorsIterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      Set theseVars = new THashSet (ptl.varSet ());
      theseVars.retainAll (remainingVars);
      Factor slicedPtl = ptl.slice (assn);
      toMdl.addFactor (slicedPtl);
      if (toSlicedMap != null) {
        toSlicedMap.put (ptl, slicedPtl);
      }
    }
  }

  /**
   * Returns the highest-score Assignment in a model according to a given inferencer.
   * @param mdl Factor graph to use
   * @param inf Inferencer to use.  No need to call <tt>computeMarginals</tt> first.
   * @return An Assignment
   */
  public static Assignment bestAssignment (FactorGraph mdl, Inferencer inf)
  {
    inf.computeMarginals (mdl);
    int[] outcomes = new int [mdl.numVariables ()];
    for (int i = 0; i < outcomes.length; i++) {
      Variable var = mdl.get (i);
      int best = inf.lookupMarginal (var).argmax ();
      outcomes[i] = best;
    }
    return new Assignment (mdl, outcomes);
  }

  /**
   * Computes the exact entropy of a factor graph distribution using the junction tree algorithm.
   *  If the model is intractable, then this method won't return a number anytime soon.
   */
  public static double entropy (FactorGraph mdl)
  {
    JunctionTreeInferencer inf = new JunctionTreeInferencer ();
    inf.computeMarginals (mdl);
    JunctionTree jt = inf.lookupJunctionTree ();
    return jt.entropy ();
  }

  /**
   * Computes the KL divergence <tt>KL(mdl1||mdl2)</tt>.  Junction tree is used to compute the entropy.
   * <p>
   * TODO: This probably won't handle when the jnuction tree for MDL2 contains a clique that's not present in the
   *  junction tree for mdl1.  If so, this is a bug.
   *
   * @param mdl1
   * @param mdl2
   * @return KL(mdl1||mdl2)
   */
  public static double KL (FactorGraph mdl1, FactorGraph mdl2)
  {
    JunctionTreeInferencer inf1 = new JunctionTreeInferencer ();
    inf1.computeMarginals (mdl1);
    JunctionTree jt1 = inf1.lookupJunctionTree ();

    JunctionTreeInferencer inf2 = new JunctionTreeInferencer ();
    inf2.computeMarginals (mdl2);
    JunctionTree jt2 = inf2.lookupJunctionTree ();

    double entropy = jt1.entropy ();
    double energy = 0;

    for (Iterator it = jt2.clusterPotentials ().iterator(); it.hasNext();) {
      Factor marg2 = (Factor) it.next ();
      Factor marg1 = inf1.lookupMarginal (marg2.varSet ());
      for (AssignmentIterator assnIt = marg2.assignmentIterator (); assnIt.hasNext();) {
        energy += marg1.value (assnIt) * marg2.logValue (assnIt);
        assnIt.advance();
      }
    }
    for (Iterator it = jt2.sepsetPotentials ().iterator(); it.hasNext();) {
      Factor marg2 = (Factor) it.next ();
      Factor marg1 = inf1.lookupMarginal (marg2.varSet ());
      for (AssignmentIterator assnIt = marg2.assignmentIterator (); assnIt.hasNext();) {
        energy -= marg1.value (assnIt) * marg2.logValue (assnIt);
        assnIt.advance();
      }
    }

    return -entropy - energy;
  }

  public static void removeConstantFactors (FactorGraph sliced)
  {
    List factors = new ArrayList (sliced.factors ());
    for (Iterator it = factors.iterator (); it.hasNext();) {
      Factor factor = (Factor) it.next ();
      if (factor instanceof ConstantFactor) {
        sliced.divideBy (factor);
      }
    }
  }
}
