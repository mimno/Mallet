/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;

import cc.mallet.grmm.types.FactorGraph;

/**
 * Interface for strategies that construct region graphs from arbitrary graphical models.
 *  They choose both which factors should be grouped into a region, and what the connectivity
 *  between regions should be.
 *
 * Created: May 27, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: RegionGraphGenerator.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public interface RegionGraphGenerator {

  /**
   * Construct a region graph from an artbitrary model. 
   * @param mdl Undirected Model to construct region graph from.
   */
  RegionGraph constructRegionGraph (FactorGraph mdl);
}
