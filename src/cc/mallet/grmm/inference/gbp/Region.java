/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;


import java.util.*;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.Variable;

import gnu.trove.THashSet;

/**
 * Created: May 27, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Region.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
class Region {

  // Collection of discrete potentials giving the factors of this region.
  Set factors;
  List parents;  // List of parent regions
  List children; // List of child regions
  List vars; // All variables in region
  int index;
  boolean isRoot;

  int countingNumber;
  
  // cache for computing message passing strategy
  Set descendants;

  private Region ()
  {
    children = new ArrayList (0);
    parents = new ArrayList (0);
    isRoot = true;
    index = -1;
  }
  Region (Variable var) {
    this ();
    factors = new THashSet ();
    vars = new ArrayList (1);
    vars.add (var);
  }

  Region (Factor ptl) {
    this ();
    factors = new THashSet ();
    factors.add (ptl);
    vars = new ArrayList (ptl.varSet ());
  }

  Region (Variable[] vars, Factor[] factors)
  {
    this();
    this.factors = new THashSet (Arrays.asList (factors));
    this.vars = new ArrayList (Arrays.asList (vars));
  }

  Region (Collection vars, Collection factors)
  {
    this();
    this.factors = new THashSet (factors);
    this.vars = new ArrayList (vars);
  }

  Region (Collection vars)
  {
    this();
    this.vars = new ArrayList (vars);
    factors = new THashSet ();
  }

  void addFactor (Factor ptl)
  {
    if (!factors.contains (ptl)) {
      factors.add (ptl);
    }
  }

  /* I think these were mistakes.

  public boolean equals (Object o)
  {
    if (this == o) return true;
    if (!(o instanceof Region)) return false;

    final Region region = (Region) o;

    if (factors != null ? !factors.equals (region.factors) : region.factors != null) return false;
    if (vars != null ? !vars.equals (region.vars) : region.vars != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = (factors != null ? factors.hashCode () : 0);
    result = 29 * result + (vars != null ? vars.hashCode () : 0);
    return result;
  }
  */

  public String toString ()
  {
    // No display of factors
    StringBuffer buf = new StringBuffer ();
    buf.append ("REGION[");
    for (Iterator it = vars.iterator (); it.hasNext ();) {
      Variable var = (Variable) it.next ();
      buf.append (var);
      if (it.hasNext ())
        buf.append (" ");
    }
    buf.append ("] nf:");
    buf.append (factors.size());
    return buf.toString ();
  }

}
