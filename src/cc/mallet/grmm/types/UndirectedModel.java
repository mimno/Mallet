/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

import cc.mallet.grmm.util.Graphs;
import gnu.trove.set.hash.THashSet;
import org._3pq.jgrapht.UndirectedGraph;
import org._3pq.jgrapht.alg.ConnectivityInspector;

import java.util.Collections;
import java.util.Set;

/**
 * Class for pairwise undirected graphical models, also known as
 *  pairwise Markov random fields.  This is a thin wrapper over
 *  FactorGraph, with only a few methods added that don't make
 *  sense for non-pairwise graphs.
 *
 * Created: Dec 21, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: UndirectedModel.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class UndirectedModel extends FactorGraph {

  public UndirectedModel ()
  {
  }

  public UndirectedModel (Variable[] vars)
  {
    super (vars);
  }

  public UndirectedModel (int capacity)
  {
    super (capacity);
  }

  private Set edges = new THashSet ();

  public Set getEdgeSet () {
    return Collections.unmodifiableSet (edges);
  }

  public void addFactor (Factor factor)
  {
    super.addFactor (factor);
    if (factor.varSet ().size() == 2) {
      edges.add (factor.varSet ());
    }
  }

  /**
   * Creates an undirected model that corresponds to a Boltzmann machine with
   *  the given weights and biases.
   * @param weights
   * @param biases
   * @return An appropriate UndirectedModel.
   */
  public static UndirectedModel createBoltzmannMachine (double[][] weights, double[] biases)
  {
    if (weights.length != biases.length)
      throw new IllegalArgumentException ("Number of weights "+weights.length
              +" not equal to number of biases "+biases.length);

    int numV = weights.length;
    Variable vars[] = new Variable [numV];
    for (int i = 0; i< numV; i++) vars[i] = new Variable (2);

    UndirectedModel mdl = new UndirectedModel (vars);
    for (int i = 0; i < numV; i++) {
      Factor nodePtl = new TableFactor (vars[i], new double[] { 1, Math.exp (biases[i]) });
      mdl.addFactor (nodePtl);
      for (int j = i+1; j < numV; j++) {
        if (weights[i][j] != 0) {
          double[] ptl = new double[] { 1, 1, 1, Math.exp (weights[i][j]) };
          mdl.addFactor (vars[i], vars[j], ptl);
        }
      }
    }

    return mdl;
  }

  //xxx Insanely inefficient stub
  public boolean isConnected (Variable v1, Variable v2)
  {
    UndirectedGraph g = Graphs.mdlToGraph (this);
    ConnectivityInspector ins = new ConnectivityInspector (g);
    return g.containsVertex (v1) && g.containsVertex (v2) && ins.pathExists (v1, v2);
  }

}
