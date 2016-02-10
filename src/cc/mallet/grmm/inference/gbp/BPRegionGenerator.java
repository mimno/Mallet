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
import cc.mallet.grmm.types.Variable;

/**
 * Created: May 30, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: BPRegionGenerator.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class BPRegionGenerator implements RegionGraphGenerator {

  public RegionGraph constructRegionGraph (FactorGraph mdl)
  {
    RegionGraph rg = new RegionGraph ();
    for (Iterator it = mdl.factorsIterator (); it.hasNext();) {
      Factor ptl = (Factor) it.next ();
      if (ptl.varSet ().size() == 1) continue;  // Single-node potentials handled separately

      Region parent = new Region (ptl);

      // Now add appropriate edges to region graph
      for (Iterator childIt = ptl.varSet().iterator (); childIt.hasNext();) {
        Variable var = (Variable) childIt.next ();
        Factor childPtl = mdl.factorOf (var);
        Region child = rg.findRegion (childPtl, true);

        //add node potential to parent if necessary
        if (childPtl != null) {
          parent.addFactor (childPtl);
          child.addFactor (childPtl);
        }

        rg.add (parent, child);
      }
    }

    rg.computeInferenceCaches ();

    return rg;
  }

}
