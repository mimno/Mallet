/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;

import org._3pq.jgrapht.UndirectedGraph;
import org._3pq.jgrapht.graph.SimpleGraph;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.grmm.types.VarSet;
import cc.mallet.grmm.types.Variable;

import java.util.Iterator;

/**
 * Created: Dec 21, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Graphs.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class Graphs {

  /**
   * Converts a FactorGraph into a plain graph where each Variable is a vertex,
   * and two Variables are connected by an edge if they are arguments to the same factor. 
   *  (Essentially converts an fg into an MRF structure, minus the factors.)
   * @param fg
   * @return a Graph
   */
  public static UndirectedGraph mdlToGraph (FactorGraph fg)
  {
    UndirectedGraph g = new SimpleGraph ();

    for (Iterator it = fg.variablesIterator (); it.hasNext ();) {
      Variable var = (Variable) it.next ();
      g.addVertex (var);
    }

    for (Iterator it = fg.factorsIterator (); it.hasNext ();) {
      Factor factor = (Factor) it.next ();
      VarSet varSet = factor.varSet ();
      int nv = varSet.size ();
      for (int i = 0; i < nv; i++) {
        for (int j = i + 1; j < nv; j++) {
          g.addEdge (varSet.get (i), varSet.get (j));
        }
      }
    }

    return g;
  }

}
