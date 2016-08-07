/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;

import gnu.trove.set.hash.THashSet;

import java.util.*;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.VarSet;
import cc.mallet.grmm.types.Variable;


/**
 * Created: May 27, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: RegionGraph.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
class RegionGraph {

  private Set regions = new THashSet ();
  private List edges = new ArrayList ();

  public RegionGraph ()
  {
  }

  void add (Region parent, Region child)
  {
    if (!isConnected (parent, child)) {

      addRegion (parent);
      addRegion (child);

      child.isRoot = false;

      if (parent.children == null)
        parent.children = new ArrayList ();
      parent.children.add (child);

      if (child.parents == null)
        child.parents = new ArrayList ();
      child.parents.add (parent);

      edges.add (new RegionEdge (parent, child));
    }
  }

  private boolean isConnected (Region parent, Region child)
  {
    return (parent.children.contains (child));
  }

  private void addRegion (Region region)
  {
    if (regions.add (region)) {
      if (region.index != -1) {
        throw new IllegalArgumentException ("Region "+region+" has already been added to a different region graph.");
      }

      region.index = regions.size() - 1;
    }
  }

  int size () { return regions.size (); }

  Iterator iterator () { return regions.iterator (); }

  Iterator edgeIterator ()
  {
    return edges.iterator ();
  }

  public void computeInferenceCaches ()
  {
    computeDescendants ();
    includeDescendantFactors ();
    computeFactorsToSend ();
    computeCountingNumbers ();
    computeCousins ();
    computeNeighboringParents ();
    computeLoopingMessages ();

    // todo: Compute D(P,R) as well
  }

  private void includeDescendantFactors ()
  {
    // Slightly inefficient: A recursive soln would be more efficient
    for (Iterator it = iterator (); it.hasNext();) {
      Region region = (Region) it.next ();
      for (Iterator dIt = region.descendants.iterator (); dIt.hasNext ();) {
        Region descendant = (Region) dIt.next ();
        // factors is a set, so it avoids duplicates
        region.factors.addAll (descendant.factors);
      }
    }
  }

  private void computeLoopingMessages ()
  {
    for (Iterator it = edgeIterator (); it.hasNext();) {
      RegionEdge edge = (RegionEdge) it.next ();
      Region to = edge.to;

      List result = new ArrayList ();

      for (Iterator cousinIt = edge.cousins.iterator (); cousinIt.hasNext ();) {
        Region cousin = (Region) cousinIt.next ();
        if (cousin == edge.from) continue;
        for (Iterator edgeIt = cousin.children.iterator (); edgeIt.hasNext();) {
          Region cousinChild = (Region) edgeIt.next ();
          if (cousinChild == to || to.descendants.contains (cousinChild)) {
            result.add (findEdge (cousin, cousinChild));
          }
        }
      }

      edge.loopingMessages = result;
    }
  }

  // computes region graph counting numbers as defined in Yedidia et al.
  private void computeCountingNumbers ()
  {
    LinkedList queue = new LinkedList ();
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.isRoot) queue.add (region);
    }

    while (!queue.isEmpty()) {
      Region region = (Region) queue.removeFirst ();
      int parentCnt = 0;
      for (Iterator it = region.parents.iterator (); it.hasNext ();) {
        Region parent = (Region) it.next ();
        parentCnt += parent.countingNumber;
      }
      region.countingNumber = 1 - parentCnt;
      queue.addAll (region.children);
    }
  }

  private void computeFactorsToSend ()
  {
    for (Iterator it = edges.iterator (); it.hasNext ();) {
      RegionEdge edge = (RegionEdge) it.next ();
      edge.initializeFactorsToSend ();
    }
  }

  private void computeCousins ()
  {
    for (Iterator it = edgeIterator (); it.hasNext();) {
      RegionEdge edge = (RegionEdge) it.next ();
      Set cousins = new THashSet (edge.from.descendants);
      cousins.removeAll (edge.to.descendants);
      cousins.remove (edge.to);
      cousins.add (edge.from);
      edge.cousins = cousins;
    }
  }

  private void computeDescendants ()
  {
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.isRoot) {
        computeDescendantsRec (region);
      }
    }
  }

  private void computeDescendantsRec (Region region)
  {
    Set descendants = new THashSet (region.children.size ());

    // all region graphs are DAGs, so no infinite regress
    for (Iterator it = region.children.iterator (); it.hasNext();) {
      Region child = (Region) it.next ();
      computeDescendantsRec (child);
      descendants.add (child);
      descendants.addAll (child.descendants);
    }

    region.descendants = descendants;
  }

  private void computeNeighboringParents ()
  {
    for (Iterator it = edgeIterator (); it.hasNext();) {
      RegionEdge edge = (RegionEdge) it.next ();
      edge.neighboringParents = new ArrayList ();

      List l = new LinkedList (regions);
      l.removeAll (edge.from.descendants);
      l.remove (edge.from);

      for (Iterator uncleIt = l.iterator (); uncleIt.hasNext ();) {
        Region uncle = (Region) uncleIt.next ();
        for (Iterator childIt = uncle.children.iterator (); childIt.hasNext();) {
          Region cousin = (Region) childIt.next ();
          if (edge.cousins.contains (cousin)) {
            edge.neighboringParents.add (findEdge (uncle, cousin));
          }
        }
      }
    }
  }

  // horrifically inefficient
   private RegionEdge findEdge (Region uncle, Region cousin)
  {
    int idx = edges.indexOf (new RegionEdge (uncle, cousin));
    return (RegionEdge) edges.get (idx);
  }

  public String toString ()
  {
    StringBuffer buf = new StringBuffer ();
    buf.append ("REGION GRAPH\nRegions:\n");
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      buf.append ("\n    ");
      buf.append (region);
    }
    buf.append ("\nEdges:");
    for (Iterator it = edges.iterator (); it.hasNext ();) {
      RegionEdge edge = (RegionEdge) it.next ();
      buf.append ("\n   ");
      buf.append (edge.from);
      buf.append (" --> ");
      buf.append (edge.to);
    }
    buf.append ("\n");
    return buf.toString ();
  }

  public boolean contains (Region region)
  {
    return regions.contains (region);
  }

  /** Returns the region in this graph whose factor list contains only
   *    a given potential.
   * @param ptl
   * @param doCreate If true, an appropriate region will be created and added
   * to graph if none is found.
   * @return A region, or null if no region found and doCreate false.
   */
  public Region findRegion (Factor ptl, boolean doCreate)
  {
    Set allVars = ptl.varSet ();
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.vars.size() == allVars.size() && region.vars.containsAll (allVars))
        return region;
    }

    if (doCreate) {
      Region region = new Region (ptl);
      addRegion (region);
      return region;
    } else {
      return null;
    }
  }

  /** Returns the region in this graph whose variable list contains only
   *    a given variable.
   * @param var
   * @param doCreate If true, an appropriate region will be created and added
   * to graph if none is found.
   * @return A region, or null if no region found and doCreate false.
   */
  public Region findRegion (Variable var, boolean doCreate)
  {
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if ((region.vars.size() == 1) && (region.vars.contains (var))) {
        return region;
      }
    }


    if (doCreate) {
      Region region = new Region (var);
      addRegion (region);
      return region;
    } else {
      return null;
    }
  }

  /** Finds the smallest region containing a given variable.
   *   This might return a region that contains many extraneous variables.
   * @param variable
   * @return
   */
  public Region findContainingRegion (Variable variable)
  {
    Region ret = null;
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.vars.contains (variable)) {
        if (ret == null || region.vars.size() < ret.vars.size ())
          ret = region;
      }
    }
    return ret;
  }

  /** Finds the smallest region containing all the variables in a given set.
   *   This might return a region that contains many extraneous variables.
   * @param varSet
   * @return
   */
  public Region findContainingRegion (VarSet varSet)
  {
    Region ret = null;
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.vars.containsAll (varSet)) {
        if (ret == null || region.vars.size() < ret.vars.size ())
          ret = region;
      }
    }
    return ret;
  }

  public int numEdges ()
  {
    return edges.size ();
  }
}
