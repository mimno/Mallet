/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;


import java.util.Iterator;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.grmm.types.UndirectedGrid;
import cc.mallet.grmm.types.Variable;
import cc.mallet.util.ArrayUtils;

/**
 * Created: May 31, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Kikuchi4SquareRegionGenerator.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class Kikuchi4SquareRegionGenerator implements RegionGraphGenerator {

  public RegionGraph constructRegionGraph (FactorGraph mdl)
  {
    if (mdl instanceof UndirectedGrid) {
      RegionGraph rg = new RegionGraph ();

      UndirectedGrid grid = (UndirectedGrid) mdl;

      // First set up regions for all
      for (int x = 0; x < grid.getWidth () - 1; x++) {
        for (int y = 0; y < grid.getHeight () - 1; y++) {
          Variable[] vars = new Variable[] {
           grid.get (x, y),
           grid.get (x+1, y),
           grid.get (x+1, y+1),
           grid.get (x, y+1), };

          Factor[] edges = new Factor[] {
            mdl.factorOf (vars[0], vars[1]),
           mdl.factorOf (vars[1], vars[2]),
           mdl.factorOf (vars[2], vars[3]),
           mdl.factorOf (vars[0], vars[3]), };

          // Create region for 4-clique
          Region fourSquare = new Region (vars, edges);

          // Create 1-clique region
          for (int i = 0; i < 4; i++) {
            Variable var = vars[i];
            Factor ptl = mdl.factorOf (var);
            if (ptl != null) {
              fourSquare.factors.add (ptl);
            }
          }

          // Finally create edge regions, and connect to everyone else
          for (int i = 0; i < 4; i++) {
            Factor edgePtl = edges[i];
            Region edgeRgn = rg.findRegion (edgePtl, true);
            rg.add (fourSquare, edgeRgn);

            Variable v1 = (Variable) edgeRgn.vars.get (0);
            Region nodeRgn = createVarRegion (rg, mdl, v1);
            edgeRgn.factors.addAll (nodeRgn.factors);
            rg.add (edgeRgn, nodeRgn);

            Variable v2 = (Variable) edgeRgn.vars.get (1);
            nodeRgn = createVarRegion (rg, mdl, v2);
            edgeRgn.factors.addAll (nodeRgn.factors);

            rg.add (edgeRgn, nodeRgn);
          }
        }
      }

      rg.computeInferenceCaches ();

      return rg;

    } else {
      throw new UnsupportedOperationException ("Kikuchi4SquareRegionGenerator requires that you use UndirectedGrid.");
    }
  }

  private Region createVarRegion (RegionGraph rg, FactorGraph mdl, Variable v1)
  {
    Factor ptl = mdl.factorOf (v1);
    if (ptl == null) {
      return rg.findRegion (v1, true);
    } else {
      return rg.findRegion (ptl, true);
    }
  }

  private void checkAllSingles (RegionGraph rg, Region[] nodeRegions)
  {
    for (Iterator it = rg.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.vars.size() == 1) {
        if (ArrayUtils.indexOf (nodeRegions, region) < 0) {
          throw new IllegalStateException ("huh?");
        }
      }
    }
  }

  private void checkTooManyDoubles (RegionGraph rg, FactorGraph mdl)
  {
    int nv = mdl.factors ().size ();
    int doubles = 0;
    for (Iterator it = rg.iterator (); it.hasNext ();) {
      Region region = (Region) it.next ();
      if (region.vars.size() == 2)
        doubles++;
    }

    if (doubles > nv) {
      throw new IllegalStateException ("huh? ");
    }
  }

}
