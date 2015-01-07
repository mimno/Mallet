/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;


import java.util.List;
import java.util.Iterator;
import java.util.Collection;
import java.util.Set;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.grmm.types.Variable;

import gnu.trove.set.hash.THashSet;

/**
 * A more space-efficient Region class that doesn't maintain a global factor
 *  over all assignments to the region.
 * 
 * Created: Jun 3, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: FactorizedRegion.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class FactorizedRegion extends Region {

  FactorGraph subMdl;

  public FactorizedRegion (List factors)
  {
    super (varsForFactors (factors), factors);
    subMdl = new FactorGraph ((Variable[]) vars.toArray (new Variable[0]));
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor factor = (Factor) it.next ();
      subMdl.addFactor (factor);
    }
  }

  private static Collection varsForFactors (List factors)
  {
    Set vars = new THashSet ();
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      vars.addAll (ptl.varSet ());
    }
    return vars;
  }

}
