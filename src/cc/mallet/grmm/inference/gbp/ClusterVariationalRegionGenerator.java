/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;


import java.util.*;
import java.util.logging.Logger;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.grmm.types.UndirectedGrid;
import cc.mallet.grmm.types.Variable;
import cc.mallet.util.CollectionUtils;
import cc.mallet.util.MalletLogger;

/**
 * Created: Jun 1, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: ClusterVariationalRegionGenerator.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class ClusterVariationalRegionGenerator implements RegionGraphGenerator {

  private static final Logger logger = MalletLogger.getLogger (ClusterVariationalRegionGenerator.class.getName());
  private static final boolean debug = false;

  private BaseRegionComputer regionComputer;

  public ClusterVariationalRegionGenerator ()
  {
    this (new ByFactorRegionComputer ());
  }

  public ClusterVariationalRegionGenerator (BaseRegionComputer regionComputer)
  {
    this.regionComputer = regionComputer;
  }

  public RegionGraph constructRegionGraph (FactorGraph mdl)
  {
    RegionGraph rg = new RegionGraph ();

    int depth = 0;

    List baseRegions = regionComputer.computeBaseRegions (mdl);
    List theseRegions = baseRegions;
    while (!theseRegions.isEmpty ()) {

      if (debug)
        System.out.println ("Depth 0 regions:\n"+CollectionUtils.dumpToString (theseRegions, "\n   "));

      List overlaps = computeOverlaps (theseRegions);
      addEdgesForOverlaps (rg, theseRegions, overlaps);
      theseRegions = overlaps;

      depth++;
    }

    rg.computeInferenceCaches ();

    logger.info ("ClusterVariationalRegionGenerator: Number of regions "+rg.size()+" Number of edges:"+rg.numEdges());

    return rg;
  }

  private List computeOverlaps (List regions)
  {
    List overlaps = new ArrayList ();
    for (Iterator it1 = regions.iterator (); it1.hasNext ();) {
      Region r1 = (Region) it1.next ();
      for (Iterator it2 = regions.iterator (); it2.hasNext ();) {
        Region r2 = (Region) it2.next ();
        if (r1 != r2) {
          Collection intersection = CollectionUtils.intersection (r1.vars, r2.vars);
          if (!intersection.isEmpty () && !anySubsumes (overlaps, intersection)) {
            Collection ptlSet = CollectionUtils.intersection (r1.factors, r2.factors);
            Variable[] vars = (Variable[]) intersection.toArray (new Variable[intersection.size ()]);
            Factor[] ptls = (Factor[]) ptlSet.toArray (new Factor [ptlSet.size ()]);
            Region r = new Region (vars, ptls);
            overlaps.add (r);
          }
        }
      }
    }

    // We can still have subsumed regions in the list if the smaller region was added first.
    for (ListIterator it = overlaps.listIterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      List otherRegions = overlaps.subList (it.nextIndex (), overlaps.size ());
      if (anySubsumes (otherRegions, region.vars)) {
        it.remove ();
      }
    }

    return overlaps;
  }


  /** Returns true if any region in regions contains all the variables in vars. */
  private boolean anySubsumes (List regions, Collection vars)
  {
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.vars.containsAll (vars))
        return true;
    }
    return false;
  }


  private void addEdgesForOverlaps (RegionGraph rg, List fromList, List toList)
  {
    for (Iterator fromIt = fromList.iterator (); fromIt.hasNext ();) {
      Region from = (Region) fromIt.next ();
      for (Iterator toIt = toList.iterator (); toIt.hasNext ();) {
        Region to = (Region) toIt.next ();
        if (from.vars.containsAll (to.vars)) {
          rg.add (from, to);
        }
      }
    }
  }

  // computing base regions


  public static void removeSubsumedRegions (List regions)
  {
    for (ListIterator it = regions.listIterator (); it.hasNext ();) {
      Region region =  (Region) it.next ();
      for (Iterator it2 = regions.iterator (); it2.hasNext();) {
        Region r2 = (Region) it2.next ();
        if (r2 != region && r2.vars.size() >= region.vars.size ()) {
          if (r2.vars.containsAll (region.vars)) {
            it.remove ();
            break;
          }
        }
      }
    }
  }

  public static void addAllFactors (FactorGraph mdl, List regions)
  {
    for (Iterator it = regions.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      for (Iterator pIt = mdl.factorsIterator (); pIt.hasNext();) {
        Factor ptl = (Factor) pIt.next ();
        if (region.vars.containsAll (ptl.varSet ())) {
          region.factors.add (ptl);
        }
      }
    }
  }

  public static interface BaseRegionComputer {
    /**
     * Returns a list of top-level regions for use in the cluster variational method.
     * @param mdl An undirected model.
     * @return A list of regions.  No region in the list may subsume another.
     */
    List computeBaseRegions (FactorGraph mdl);
  }

  /**
   * Region computer where each top-level region consists of a single factor node.
   *  If the model is pairwise, this is equivalent to using the Bethe free energy.
   */
  public static class ByFactorRegionComputer implements BaseRegionComputer {

    public List computeBaseRegions (FactorGraph mdl)
    {
      List regions = new ArrayList (mdl.factors ().size ());
      for (Iterator it = mdl.factorsIterator (); it.hasNext ();) {
        Factor ptl =  (Factor) it.next ();
        regions.add (new Region (ptl));
      }
      removeSubsumedRegions (regions);
      addAllFactors (mdl, regions);
      return regions;
    }

  }

  public static class Grid2x2RegionComputer implements BaseRegionComputer {

    public List computeBaseRegions (FactorGraph mdl)
    {
      List regions = new ArrayList ();
      UndirectedGrid grid = (UndirectedGrid) mdl;
      for (int x = 0; x < grid.getWidth() - 1; x++) {
        for (int y = 0; y < grid.getHeight() - 1; y++) {
          Variable[] vars = new Variable[] {
            grid.get (x, y),
            grid.get (x, y+1),
            grid.get (x+1, y+1),
            grid.get (x+1, y),
          };
          regions.add (new Region (vars, new Factor[0]));
        }
      }
      addAllFactors (mdl, regions);
      return regions;
    }
  }

}
