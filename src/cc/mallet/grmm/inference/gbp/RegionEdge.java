/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * Created: May 30, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: RegionEdge.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
class RegionEdge {

  Region from;
  Region to;

  // List of factors in the parent region that are not in the child region
  List factorsToSend;

  // E(P)\E(R) in Yedidia notation.  Note that this includes parent node.
  Set cousins;

  // N(from,to) in Yedida 2004 TR notation
  List neighboringParents;

  // D(from,to) in Yedida 2004 TR notation
  List loopingMessages;

  public RegionEdge (Region from, Region to)
  {
    this.from = from;
    this.to = to;
  }

  public boolean equals (Object o)
  {
    if (this == o) return true;
    if (!(o instanceof RegionEdge)) return false;

    final RegionEdge regionEdge = (RegionEdge) o;

    if (from != null ? !from.equals (regionEdge.from) : regionEdge.from != null) return false;
    if (to != null ? !to.equals (regionEdge.to) : regionEdge.to != null) return false;

    return true;
  }

  public int hashCode ()
  {
    int result;
    result = (from != null ? from.hashCode () : 0);
    result = 29 * result + (to != null ? to.hashCode () : 0);
    return result;
  }

  void initializeFactorsToSend ()
  {
    factorsToSend = new ArrayList (from.factors);
    factorsToSend.removeAll (to.factors);
  }

  public String toString ()
  {
    return "EDGE:["+from+"-->"+to+"]";
  }
}
