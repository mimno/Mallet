/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types; // Generated package name

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.Map;

import org._3pq.jgrapht.DirectedGraph;
import org._3pq.jgrapht.graph.DefaultDirectedGraph;
import org._3pq.jgrapht.alg.ConnectivityInspector;
import gnu.trove.THashMap;


/**
 *  Class for directed graphical models. This is just a
 *   souped-up Graph.
 *
 * Created: Mon Sep 15 14:50:19 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: DirectedModel.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */

public class DirectedModel extends FactorGraph {

  private Map allCpts = new THashMap ();

  // Graph object used to prevent directed cycles
  private DirectedGraph graph = new DefaultDirectedGraph ();

  public DirectedModel ()
  {
  }

  public DirectedModel (Variable[] vars)
  {
    super (vars);
  }

  public DirectedModel (int capacity)
  {
    super (capacity);
  }

  protected void beforeFactorAdd (Factor factor)
  {
    super.beforeFactorAdd (factor);
    if (!(factor instanceof CPT)) {
      throw new IllegalArgumentException ("Factors of a directed model must be an instance of CPT, was "+factor);
    }

    CPT cpt = (CPT) factor;
    Variable child = cpt.getChild ();
    VarSet parents = cpt.getParents ();
    if (graph.containsVertex (child)) {
      checkForNoCycle (parents, child, cpt);
    }
  }

  private void checkForNoCycle (VarSet parents, Variable child, CPT cpt) {
    ConnectivityInspector inspector = new ConnectivityInspector (graph);
    for (Iterator it = parents.iterator (); it.hasNext ();) {
      Variable rent = (Variable) it.next ();
      if (inspector.pathExists (child, rent)) {
        throw new IllegalArgumentException ("Error adding CPT: Would create directed cycle"+
                        "From: "+rent+" To:"+child+"\nCPT: "+cpt);
      }
    }
  }

  protected void afterFactorAdd (Factor factor)
  {
    super.afterFactorAdd (factor);
    CPT cpt = (CPT) factor;
    Variable child = cpt.getChild ();
    VarSet parents = cpt.getParents ();
    allCpts.put (child, cpt);

    graph.addVertex (child);
    graph.addAllVertices (parents);
    for (Iterator it = parents.iterator (); it.hasNext ();) {
      Variable rent = (Variable) it.next ();
      graph.addEdge (rent, child);
    }
  }

  /**
   *  Returns the conditional distribution <tt>P ( node | Parents (node) )</tt>
   */
  public CPT getCptofVar (Variable node)
  {
    return (CPT) allCpts.get (node);
  }

  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
  }

}// DirectedModel
