/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;


import java.util.*;

import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.grmm.util.Flops;
import cc.mallet.types.*;
import cc.mallet.util.*;

import gnu.trove.TIntArrayList;
import gnu.trove.TDoubleArrayList;

/**
 * A static utility class containing utility methods for dealing with factors,
 *  especially TableFactor objects.
 * 
 * Created: Mar 17, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Factors.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class Factors {

  public static CPT normalizeAsCpt (AbstractTableFactor ptl, Variable var)
  {
    double[] sums = new double [ptl.numLocations ()];
    Arrays.fill (sums, Double.NEGATIVE_INFINITY);

    // Compute normalization factor for each neighbor assignment
    VarSet neighbors = new HashVarSet (ptl.varSet ());
    neighbors.remove (var);

    for (AssignmentIterator it = ptl.assignmentIterator (); it.hasNext (); it.advance ()) {
      Assignment assn = it.assignment ();
      Assignment nbrAssn = (Assignment) assn.marginalizeOut (var);
      int idx = nbrAssn.singleIndex ();
//        sums[idx] += ptl.phi (assn);
      sums[idx] = Maths.sumLogProb (ptl.logValue (assn), sums[idx]);
    }

    // ...and then normalize potential
    for (AssignmentIterator it = ptl.assignmentIterator (); it.hasNext (); it.advance ()) {
      Assignment assn = it.assignment ();
      double oldVal = ptl.logValue (assn);
//        double oldVal = ptl.phi (assn);
      Assignment nbrAssn = (Assignment) assn.marginalizeOut (var);
      double logZ = sums[nbrAssn.singleIndex ()];
//        ptl.setPhi (assn, oldVal / logZ);
      if (Double.isInfinite (oldVal) && Double.isInfinite (logZ)) {
        // 0/0 = 0
        ptl.setLogValue (assn, Double.NEGATIVE_INFINITY);
      } else {
        ptl.setLogValue (assn, oldVal - logZ);
      }
    }

    return new CPT (ptl, var);
  }

  public static Factor average (Factor ptl1, Factor ptl2, double weight)
  {
    // complete hack
    TableFactor mptl1 = (TableFactor) ptl1;
    TableFactor mptl2 = (TableFactor) ptl2;
    return TableFactor.hackyMixture (mptl1, mptl2, weight);
  }

  public static double oneDistance (Factor bel1, Factor bel2)
  {
    Set vs1 = bel1.varSet ();
    Set vs2 = bel2.varSet ();

    if (!vs1.equals (vs2)) {
      throw new IllegalArgumentException ("Attempt to take distancebetween mismatching potentials "+bel1+" and "+bel2);
    }

    double dist = 0;
    for (AssignmentIterator it = bel1.assignmentIterator (); it.hasNext ();) {
      Assignment assn = it.assignment ();
      dist += Math.abs (bel1.value (assn) - bel2.value (assn));
      it.advance ();
    }

    return dist;
  }


  public static TableFactor retainMass (DiscreteFactor ptl, double alpha)
  {
    int[] idxs = new int [ptl.numLocations ()];
    double[] vals = new double [ptl.numLocations ()];
    for (int i = 0; i < idxs.length; i++) {
      idxs[i] = ptl.indexAtLocation (i);
      vals[i] = ptl.logValue (i);
    }

    RankedFeatureVector rfv = new RankedFeatureVector (new Alphabet(), idxs, vals);
    TIntArrayList idxList = new TIntArrayList ();
    TDoubleArrayList valList = new TDoubleArrayList ();

    double mass = Double.NEGATIVE_INFINITY;
    double logAlpha = Math.log (alpha);
    for (int rank = 0; rank < rfv.numLocations (); rank++) {
      int idx = rfv.getIndexAtRank (rank);
      double val = rfv.value (idx);
      mass = Maths.sumLogProb (mass, val);
      idxList.add (idx);
      valList.add (val);
      if (mass > logAlpha) {
        break;
      }
    }

    int[] szs = computeSizes (ptl);
    SparseMatrixn m = new SparseMatrixn (szs, idxList.toNativeArray (), valList.toNativeArray ());

    TableFactor result = new TableFactor (computeVars (ptl));
    result.setValues (m);

    return result;
  }

  public static int[] computeSizes (Factor result)
  {
    int nv = result.varSet ().size();
    int[] szs = new int [nv];
    for (int i = 0; i < nv; i++) {
      Variable var = result.getVariable (i);
      szs[i] = var.getNumOutcomes ();
    }
    return szs;
  }

  public static Variable[] computeVars (Factor result)
  {
    int nv = result.varSet ().size();
    Variable[] vars = new Variable [nv];
    for (int i = 0; i < nv; i++) {
      Variable var = result.getVariable (i);
      vars[i] = var;
    }
    return vars;
  }

  /**
   * Given a joint distribution over two variables, returns their mutual information.
   * @param factor A joint distribution.  Must be normalized, and over exactly two variables.
   * @return The mutual inforamiton
   */
  public static double mutualInformation (Factor factor)
  {
    VarSet vs = factor.varSet ();
    if (vs.size() != 2) throw new IllegalArgumentException ("Factor must have size 2");
    Factor marg1 = factor.marginalize (vs.get (0));
    Factor marg2 = factor.marginalize (vs.get (1));

    double result = 0;
    for (Iterator it = factor.assignmentIterator (); it.hasNext(); ) {
      Assignment assn = (Assignment) it.next ();
      result += (factor.value (assn)) * (factor.logValue (assn) - marg1.logValue (assn) - marg2.logValue (assn));
    }
    return result;
  }

  public static double KL (AbstractTableFactor f1, AbstractTableFactor f2)
  {
    double result = 0;
    // assumes same var set
    for (int loc = 0; loc < f1.numLocations (); loc++) {
      double val1 = f1.valueAtLocation (loc);
      double val2 = f2.value (f1.indexAtLocation (loc));
      if (val1 > 1e-5) {
        result += val1 * Math.log (val1 / val2);
      }
    }
    return result;
  }

  /**
   * Returns a new Factor <tt>F = alpha * f1 + (1 - alpha) * f2</tt>.
   */
   public static Factor mix (AbstractTableFactor f1, AbstractTableFactor f2, double alpha)
  {
    return AbstractTableFactor.hackyMixture (f1, f2, alpha);
  }

  public static double euclideanDistance (AbstractTableFactor f1, AbstractTableFactor f2)
  {
    double result = 0;
    // assumes same var set
    for (int loc = 0; loc < f1.numLocations (); loc++) {
      double val1 = f1.valueAtLocation (loc);
      double val2 = f2.value (f1.indexAtLocation (loc));
      result += (val1 - val2) * (val1 - val2);
    }
    return Math.sqrt (result);
  }

  public static double l1Distance (AbstractTableFactor f1, AbstractTableFactor f2)
  {
    double result = 0;
    // assumes same var set
    for (int loc = 0; loc < f1.numLocations (); loc++) {
      double val1 = f1.valueAtLocation (loc);
      double val2 = f2.value (f1.indexAtLocation (loc));
      result += Math.abs (val1 - val2);
    }
    return result;
  }

  /**
   * Adapter that allows an Inferencer to be treated as if it were a factor.
   * @param inf An inferencer on which computeMarginals() has been called.
   * @return A factor
   */
  public static Factor asFactor (final Inferencer inf)
  {
    return new SkeletonFactor () {
      public double value (Assignment assn)
      {
        Factor factor = inf.lookupMarginal (assn.varSet ());
        return factor.value (assn);
      }

      public Factor marginalize (Variable vars[])
      {
        return inf.lookupMarginal (new HashVarSet (vars));
      }

      public Factor marginalize (Collection vars)
      {
        return inf.lookupMarginal (new HashVarSet (vars));
      }

      public Factor marginalize (Variable var)
      {
        return inf.lookupMarginal (new HashVarSet (new Variable[] { var }));
      }

      public Factor marginalizeOut (Variable var)
      {
        throw new UnsupportedOperationException ();
      }

      public Factor marginalizeOut (VarSet varset)
      {
        throw new UnsupportedOperationException ();
      }
      public VarSet varSet ()
      {
        throw new UnsupportedOperationException ();
      }

    };
  }

  public static Variable[] discreteVarsOf (Factor fg)
  {
    List vars = new ArrayList ();
    VarSet vs = fg.varSet ();
    for (int vi = 0; vi < vs.size (); vi++) {
      Variable var = vs.get (vi);
      if (!var.isContinuous ()) {
        vars.add (var);
      }
    }
    return (Variable[]) vars.toArray (new Variable [vars.size ()]);
  }

  public static Variable[] continuousVarsOf (Factor fg)
  {
    List vars = new ArrayList ();
    VarSet vs = fg.varSet ();
    for (int vi = 0; vi < vs.size (); vi++) {
      Variable var = vs.get (vi);
      if (var.isContinuous ()) {
        vars.add (var);
      }
    }
    return (Variable[]) vars.toArray (new Variable [vars.size ()]);
  }

  public static double corr (Factor factor)
  {
    if (factor.varSet ().size() != 2)
     throw new IllegalArgumentException ("corr() only works on Factors of size 2, tried "+factor);

    Variable v0 = factor.varSet ().get (0);
    Variable v1 = factor.varSet ().get (1);

    double eXY = 0.0;
    for (AssignmentIterator it = factor.assignmentIterator (); it.hasNext();) {
      Assignment assn = (Assignment) it.next ();
      int val0 = assn.get (v0);
      int val1 = assn.get (v1);
      eXY += factor.value (assn) * val0 * val1;
    }

    double eX = mean (factor.marginalize (v0));
    double eY = mean (factor.marginalize (v1));

    return eXY - eX * eY;
  }

  private static double mean (Factor factor)
  {
    if (factor.varSet ().size() != 1)
     throw new IllegalArgumentException ("mean() only works on Factors of size 1, tried "+factor);

    Variable v0 = factor.varSet ().get (0);

    double mean = 0.0;
    for (AssignmentIterator it = factor.assignmentIterator (); it.hasNext();) {
      Assignment assn = (Assignment) it.next ();
      int val0 = assn.get (v0);
      mean += factor.value (assn) * val0;
    }

    return mean;
  }

  public static Factor multiplyAll (Collection factors)
  {
    Factor first = (Factor) factors.iterator ().next ();
    if (factors.size() == 1) {
      return first.duplicate ();
    }

    /* Get all the variables */
    VarSet vs = new HashVarSet ();
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor phi = (Factor) it.next ();
      vs.addAll (phi.varSet ());
    }

    /* define a new potential over the neighbors of NODE */
    Factor result = first.duplicate ();
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor phi = (Factor) it.next ();
      result.multiplyBy (phi);
    }

    return result;
  }


  public static double distLinf (AbstractTableFactor f1, AbstractTableFactor f2)
  {
//    double sum1 = f1.logsum ();
//    double sum2 = f2.logsum ();
    Matrix m1 = f1.getLogValueMatrix ();
    Matrix m2 = f2.getLogValueMatrix ();
    return matrixDistLinf (m1, m2);
  }

  public static double distValueLinf (AbstractTableFactor f1, AbstractTableFactor f2)
  {
//    double sum1 = f1.logsum ();
//    double sum2 = f2.logsum ();
    Matrix m1 = f1.getValueMatrix ();
    Matrix m2 = f2.getValueMatrix ();
    return matrixDistLinf (m1, m2);
  }

  private static double matrixDistLinf (Matrix m1, Matrix m2)
  {
    double max = 0;

    int nl1 = m1.singleSize ();
    int nl2 = m2.singleSize ();

    if (nl1 != nl2) return Double.POSITIVE_INFINITY;

    for (int l = 0; l < nl1; l++) {
      double val1 = m1.singleValue (l);
      double val2 = m2.singleValue (l);
      double diff = (val1 > val2) ? val1 - val2 : val2 - val1;
      max = (diff > max) ? diff : max;
    }

    return max;
  }

  /** Implements the error range measure from Ihler et al. */
  public static double logErrorRange (AbstractTableFactor f1, AbstractTableFactor f2)
  {
    double error_min = Double.MAX_VALUE;
    double error_max = 0;

    Matrix m1 = f1.getLogValueMatrix ();
    Matrix m2 = f2.getLogValueMatrix ();

    int nl1 = m1.singleSize ();
    int nl2 = m2.singleSize ();

    if (nl1 != nl2) return Double.POSITIVE_INFINITY;

    for (int l = 0; l < nl1; l++) {
      double val1 = m1.singleValue (l);
      double val2 = m2.singleValue (l);
      double diff = (val1 > val2) ? val1 - val2 : val2 - val1;
      error_max = (diff > error_max) ? diff : error_max;
      error_min = (diff < error_min) ? diff : error_min;
    }

    return error_max - error_min;
  }

}
