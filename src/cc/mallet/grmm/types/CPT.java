/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;


import java.util.Collection;

import cc.mallet.util.Randoms;

/**
 * $Id: CPT.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class CPT implements DiscreteFactor {

  private DiscreteFactor subFactor;
  private VarSet parents;
  private Variable child;

  public CPT (DiscreteFactor subFactor, Variable child)
  {
    this.subFactor = subFactor;
    this.child = child;
    if (!subFactor.containsVar (child)) {
      throw new IllegalArgumentException ("Invalid child var for CPT\n  Child: " + child + "\n  Factor: " + subFactor);
    }
    parents = new HashVarSet (subFactor.varSet ());
    parents.remove (child);
  }

  public VarSet getParents ()
  {
    return parents;
  }

  public Variable getChild ()
  {
    return child;
  }

  public void setSubFactor (DiscreteFactor subFactor)
  {
    this.subFactor = subFactor;
  }

  public String toString ()
  {
    return "CPT: Child ["+child+"]\n  Factor: "+subFactor.toString ();
  }

  public String prettyOutputString() { return toString(); }

  public double value (Assignment assn) {return subFactor.value (assn);}

  public double value (AssignmentIterator it) {return subFactor.value (it);}

  public Factor normalize () { return subFactor.normalize (); }

  public Factor marginalize (Variable[] vars) {return subFactor.marginalize (vars);}

  public Factor marginalize (Collection vars) {return subFactor.marginalize (vars);}

  public Factor marginalize (Variable var) {return subFactor.marginalize (var);}

  public Factor marginalizeOut (Variable var) {return subFactor.marginalizeOut (var);}

  public Factor extractMax (Collection vars) {return subFactor.extractMax (vars);}

  public Factor extractMax (Variable var) {return subFactor.extractMax (var);}

  public Factor extractMax (Variable[] vars) {return subFactor.extractMax (vars);}

  public int argmax () {return subFactor.argmax ();}

  public Assignment sample (Randoms r) {return subFactor.sample (r);}

  public double sum () {return subFactor.sum ();}

  public double entropy () {return subFactor.entropy ();}

  public Factor multiply (Factor dist) {return subFactor.multiply (dist);}

  public void multiplyBy (Factor pot) {subFactor.multiplyBy (pot);}

  public void exponentiate (double power) {subFactor.exponentiate (power);}

  public void divideBy (Factor pot) {subFactor.divideBy (pot);}

  public boolean containsVar (Variable var) {return subFactor.containsVar (var);}

  public VarSet varSet () {return subFactor.varSet ();}

  public AssignmentIterator assignmentIterator () {return subFactor.assignmentIterator ();}

  public boolean almostEquals (Factor p) {return subFactor.almostEquals (p);}

  public boolean almostEquals (Factor p, double epsilon) {return subFactor.almostEquals (p, epsilon);}

  public Factor duplicate () {return subFactor.duplicate ();}

  public boolean isNaN () {return subFactor.isNaN ();}

  public double logValue (AssignmentIterator it) {return subFactor.logValue (it);}

  public double logValue (Assignment assn) {return subFactor.logValue (assn);}

  public double logValue (int loc) {return subFactor.logValue (loc);}

  public Variable getVariable (int i) {return subFactor.getVariable (i);}

  public int sampleLocation (Randoms r) {return subFactor.sampleLocation (r);}

  public double value (int index) {return subFactor.value (index);}

  public int numLocations () {return subFactor.numLocations ();}

  public double valueAtLocation (int loc) {return subFactor.valueAtLocation (loc);}

  public int indexAtLocation (int loc) {return subFactor.indexAtLocation (loc);}

  public double[] toValueArray () {return subFactor.toValueArray ();}

  public int singleIndex (int[] smallDims) {return subFactor.singleIndex (smallDims);}

  public String dumpToString () { return subFactor.dumpToString (); }

  public Factor slice (Assignment assn) { return subFactor.slice (assn); }

  public AbstractTableFactor asTable ()
  {
    return subFactor.asTable ();
  }

  public Factor marginalizeOut (VarSet varset)
  {
    return subFactor.marginalizeOut (varset);
  }

}
