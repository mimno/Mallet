/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.inference;

import org._3pq.jgrapht.GraphHelper;
import org._3pq.jgrapht.UndirectedGraph;
import org._3pq.jgrapht.alg.ConnectivityInspector;
import org._3pq.jgrapht.graph.SimpleGraph;
import org._3pq.jgrapht.graph.ListenableUndirectedGraph;
import org._3pq.jgrapht.traverse.BreadthFirstIterator;

import cc.mallet.grmm.types.*;
import cc.mallet.grmm.util.Graphs;
import cc.mallet.types.Alphabet;
import cc.mallet.util.MalletLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Does inference in general graphical models using
 *  the Hugin junction tree algorithm.
 *
 * Created: Mon Nov 10 23:58:44 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: JunctionTreeInferencer.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class JunctionTreeInferencer extends AbstractInferencer {

  private static Logger logger = MalletLogger.getLogger(JunctionTreeInferencer.class.getName());
  private boolean inLogSpace;
  private JunctionTreePropagation propagator;

  public JunctionTreeInferencer()
  {
    this (JunctionTreePropagation.createSumProductInferencer ());
  } // JunctionTreeInferencer constructor

  public JunctionTreeInferencer (JunctionTreePropagation propagator)
  {
    this.propagator = propagator;
  }

  public static JunctionTreeInferencer createForMaxProduct ()
  {
    return new JunctionTreeInferencer (JunctionTreePropagation.createMaxProductInferencer ());
  }


  private boolean isAdjacent (UndirectedGraph g, Variable v1, Variable v2)
  {
    return g.getEdge (v1, v2) != null;
  }


  transient protected JunctionTree jtCurrent;
  transient private ArrayList cliques;


  /**
   * Returns the number of edges that would be added to a graph if a
   *  given vertex would be removed in the triangulation procedure.
   *  The return value is the number of edges in the elimination
   *  clique of V that are not already present.
   */
  private int newEdgesRequired(UndirectedGraph mdl, Variable v)
  {
    int rating = 0;

    for (Iterator it1 = neighborsIterator (mdl,v); it1.hasNext();) {
      Variable neighbor1 = (Variable) it1.next();
      Iterator it2 = neighborsIterator (mdl,v);
      while (it2.hasNext()) {
        Variable neighbor2 = (Variable) it2.next();
        if (neighbor1 != neighbor2) {
          if (!isAdjacent (mdl, neighbor1, neighbor2)) {
            rating++;
          }
        }
      }
    }

//		System.out.println(v+" = "+rating);

    return rating;
  }


  /**
   * Returns the weight of the clique that would be added to a graph if a
   *  given vertex would be removed in the triangulation procedure.
   *  The return value is the number of edges in the elimination
   *  clique of V that are not already present.
   */
  private int weightRequired (UndirectedGraph mdl, Variable v)
  {
    int rating = 1;

    for (Iterator it1 = neighborsIterator (mdl,v); it1.hasNext();) {
      Variable neighbor = (Variable) it1.next();
      rating *= neighbor.getNumOutcomes();
    }

//		System.out.println(v+" = "+rating);

    return rating;
  }


  private void connectNeighbors(UndirectedGraph mdl, Variable v)
  {
    for (Iterator it1 = neighborsIterator(mdl,v); it1.hasNext();) {
      Variable neighbor1 = (Variable) it1.next();
      Iterator it2 = neighborsIterator(mdl,v);
      while (it2.hasNext()) {
        Variable neighbor2 = (Variable) it2.next();
        if (neighbor1 != neighbor2) {
          if (!isAdjacent (mdl, neighbor1, neighbor2)) {
            try {
              mdl.addEdge(neighbor1, neighbor2);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
    }
  }


  // xx should refactor into Collections.any (Coll, TObjectProc)
  /* Return true iff a clique in L strictly contains c. */
  private boolean findSuperClique(List l, VarSet c)
  {
    for (Iterator it = l.iterator(); it.hasNext();) {
      VarSet c2 = (VarSet) it.next();
      if (c2.containsAll(c)) {
        return true;
      }
    }
    return false;
  }


  // works like the obscure <=> operator in Perl.
  private static int cmp(int i1, int i2)
  {
    if (i1 < i2) {
      return -1;
    } else if (i1 > i2) {
      return 1;
    } else {
      return 0;
    }
  }

  public Variable pickVertexToRemove (UndirectedGraph mdl, ArrayList lst)
  {
    Iterator it = lst.iterator();
    Variable best = (Variable) it.next();
    int bestVal1 = newEdgesRequired (mdl, best);
    int bestVal2 = weightRequired (mdl, best);

    while (it.hasNext()) {
      Variable v = (Variable) it.next();
      int val = newEdgesRequired (mdl, v);
      if (val < bestVal1) {
        best = v;
        bestVal1 = val;
        bestVal2 = weightRequired (mdl, v);
      } else if (val == bestVal1) {
        int val2 = weightRequired (mdl, v);
        if (val2 < bestVal2) {
          best = v;
          bestVal1 = val;
          bestVal2 = val2;
        }
      }
    }

    return best;
  }


  /**
   * Adds edges to graph until it is triangulated.
   */
  private void triangulate(final UndirectedGraph mdl)
  {
    UndirectedGraph mdl2 = dupGraph (mdl);
    ArrayList vars = new ArrayList(mdl.vertexSet());
    Alphabet varMap = makeVertexMap(vars);
    cliques = new ArrayList();

    // debug
    if (logger.isLoggable (Level.FINER)) {
      logger.finer ("Triangulating model: "+mdl);
      String ret = "";
      for (int i = 0; i < vars.size(); i++) {
        Variable next = (Variable) vars.get(i);
        ret += next.toString() + "\n"; // " (" + mdl.getIndex(next) + ")\n  ";
      }
      logger.finer(ret);
    }

    while (!vars.isEmpty()) {
      Variable v = (Variable) pickVertexToRemove (mdl2, vars);
      logger.finer("Triangulating vertex " + v);

      VarSet varSet = new BitVarSet (v.getUniverse (), GraphHelper.neighborListOf (mdl2, v));
      varSet.add(v);
      if (!findSuperClique(cliques, varSet)) {
        cliques.add(varSet);
        if (logger.isLoggable (Level.FINER)) {
          logger.finer ("  Elim clique " + varSet + " size " + varSet.size () + " weight " + varSet.weight ());
        }
      }

      // must remove V from graph first, because adding the edges
//  will change the rating of other vertices

      connectNeighbors (mdl2, v);
      vars.remove(v);
      mdl2.removeVertex (v);
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Triangulation done. Cliques are: ");
      int totSize = 0, totWeight = 0, maxSize = 0, maxWeight = 0;
      for (Iterator it = cliques.iterator(); it.hasNext();) {
        VarSet c = (VarSet) it.next();
        logger.finer(c.toString());
        totSize += c.size();
        maxSize = Math.max(c.size(), maxSize);
        totWeight += c.weight();
        maxWeight = Math.max(c.weight(), maxWeight);
      }
      double sz = cliques.size();
      logger.fine("Jt created " + sz + " cliques. Size: avg " + (totSize / sz)
                  + " max " + (maxSize) + " Weight: avg " + (totWeight / sz)
                  + " max " + (maxWeight));
    }
  }


  private Alphabet makeVertexMap(ArrayList vars)
  {
    Alphabet map = new Alphabet (vars.size (), Variable.class);
    map.lookupIndices(vars.toArray(), true);
    return map;
  }


  private static int sepsetSize(BitVarSet[] pair)
  {
    assert pair.length == 2;
    return pair[0].intersectionSize(pair[1]);
  }


  private static int sepsetCost(VarSet[] pair)
  {
    assert pair.length == 2;
    return pair[0].weight() + pair[1].weight();
  }


  // Given two pairs of cliques, returns -1 if the pair o1 should be
  // added to the tree first.  We add pairs that have the largest
  // mass (number of vertices in common) to ensure that the clique
  // tree satifies the running intersection property.
  private static Comparator sepsetChooser = new Comparator() {
    public int compare(Object o1, Object o2)
    {
      if (o1 == o2) return 0;
      BitVarSet[] pair1 = (BitVarSet[]) o1;
      BitVarSet[] pair2 = (BitVarSet[]) o2;
      int size1 = sepsetSize(pair1);
      int size2 = sepsetSize(pair2);
      int retval = -cmp(size1, size2);
      if (retval == 0) {
        // Break ties by adding the sepset with the
        //  smallest cost (sum of weights of connected clusters)
        int cost1 = sepsetCost(pair1);
        int cost2 = sepsetCost(pair2);
        retval = cmp(cost1, cost2);

        // Still a tie? Break arbitrarily but consistently.
        if (retval == 0) {
          retval = cmp (o1.hashCode (), o2.hashCode ());
        }
      }
      return retval;
    }
  };


  private JunctionTree graphToJt (UndirectedGraph g)
  {
    JunctionTree jt = new JunctionTree (g.vertexSet ().size ());
    Object root = g.vertexSet ().iterator ().next ();
    jt.add (root);

    for (Iterator it1 = new BreadthFirstIterator (g, root); it1.hasNext ();) {
      Object v1 = it1.next ();
      for (Iterator it2 = GraphHelper.neighborListOf (g, v1).iterator (); it2.hasNext ();) {
        Object v2 = it2.next ();
        if (jt.getParent (v1) != v2) {
          jt.addNode (v1, v2);
        }
      }
    }
    return jt;
  }


  private JunctionTree buildJtStructure()
  {
    TreeSet pq = new TreeSet(sepsetChooser);

    // Initialize pq with all possible edges...
    for (Iterator it = cliques.iterator(); it.hasNext();) {
      BitVarSet c1 = (BitVarSet) it.next();
      for (Iterator it2 = cliques.iterator(); it2.hasNext();) {
        BitVarSet c2 = (BitVarSet) it2.next();
        if (c1 == c2) break;
        pq.add(new BitVarSet[]{c1, c2});
      }
    }

    // ...and add the edges to jt that come to the top of the queue
    //  and don't cause a cycle.
    // xxx OK, this sucks.  openjgraph doesn't allow adding
    //  disconnected edges to a tree, so what we'll do is create a
    //  Graph frist, then convert it to a Tree.
    ListenableUndirectedGraph g = new ListenableUndirectedGraph (new SimpleGraph ());

    // first add every clique to the graph
    for (Iterator it = cliques.iterator(); it.hasNext();) {
      VarSet c = (VarSet) it.next();
      g.addVertex (c);
    }

    ConnectivityInspector inspector = new ConnectivityInspector (g);
    g.addGraphListener (inspector);
    
    // then add n - 1 edges
    int numCliques = cliques.size();
    int edgesAdded = 0;
    while (edgesAdded < numCliques - 1) {
      VarSet[] pair = (VarSet[]) pq.first();
      pq.remove(pair);

      if (!inspector.pathExists(pair[0], pair[1])) {
          g.addEdge(pair[0], pair[1]);
          edgesAdded++;
      }
    }

    JunctionTree jt = graphToJt(g);
    if (logger.isLoggable (Level.FINER)) {
      logger.finer ("  jt structure was " + jt);
    }
    return jt;
  }


  private void initJtCpts(FactorGraph mdl, JunctionTree jt)
  {
    for (Iterator it = jt.getVerticesIterator(); it.hasNext();) {
      VarSet c = (VarSet) it.next();
//      DiscreteFactor ptl = createBlankFactor (c);
//      jt.setCPF(c, ptl);
      jt.setCPF (c, new ConstantFactor (1.0));
    }

    for (Iterator it = mdl.factors ().iterator(); it.hasNext();) {
      Factor ptl = (Factor) it.next();
      VarSet parent = jt.findParentCluster(ptl.varSet());
      assert parent != null
              : "Unable to find parent cluster for ptl " + ptl + "in jt " + jt;

      Factor cpf = jt.getCPF(parent);
      Factor newCpf = cpf.multiply(ptl);
      jt.setCPF (parent, newCpf);

      /* debug
         if (jt.isNaN()) {
           throw new RuntimeException ("Got a NaN");
         }
         */
    }
  }

  private AbstractTableFactor createBlankFactor (VarSet c)
  {
    if (inLogSpace) {
      return new LogTableFactor (c);
    } else {
      return new TableFactor (c);
    }
  }


  public void computeMarginals (FactorGraph mdl)
  {
    inLogSpace = mdl.getFactor (0) instanceof LogTableFactor;
    buildJunctionTree(mdl);
    propagator.computeMarginals(jtCurrent);
    totalMessagesSent += propagator.getTotalMessagesSent();
  }

  public void computeMarginals (JunctionTree jt)
  {
    inLogSpace = false; //??
    jtCurrent = jt;
    propagator.computeMarginals(jtCurrent);
    totalMessagesSent += propagator.getTotalMessagesSent();
  }



  /**
   * Constructs a junction tree from a given factor graph.  Does not perform BP in the resulting
   *  graph.  So this gives you the structure of a jnuction tree, but the factors don't correspond
   *  to the true marginals unless you call BP yourself.
   * @param mdl Factor graph to compute JT for.
   */
  public JunctionTree buildJunctionTree(FactorGraph mdl)
  {
    jtCurrent = (JunctionTree) mdl.getInferenceCache(JunctionTreeInferencer.class);
    if (jtCurrent != null) {
      jtCurrent.clearCPFs();
    } else {
      /* The graph g is the topology of the MRF that corresponds to the factor graph mdl.
       * Essentially, this means that we triangulate factor graphs by converting to an MRF first.
       * I could have chosen to trianglualte the FactorGraph directly, but I didn't for historical reasons
       *  (I already had a version of triangulate() for MRFs, not bipartite factor graphs.)
       * Note that the call to mdlToGraph() is perfectly valid for FactorGraphs that are also DirectedModels,
       *  and has the effect of moralizing in that case.  */
      UndirectedGraph g = Graphs.mdlToGraph (mdl);
      triangulate (g);
      jtCurrent = buildJtStructure();
      mdl.setInferenceCache(JunctionTreeInferencer.class, jtCurrent);
    }

    initJtCpts(mdl, jtCurrent);
    return jtCurrent;
  }

  private UndirectedGraph dupGraph (UndirectedGraph original)
  {
    UndirectedGraph copy = new SimpleGraph ();
    GraphHelper.addGraph (copy, original);
    return copy;
  }


  public Factor lookupMarginal(Variable var)
  {
    return propagator.lookupMarginal (jtCurrent, var);
  }


  public Factor lookupMarginal(VarSet varSet)
  {
    return propagator.lookupMarginal (jtCurrent, varSet);
  }


  public double lookupLogJoint(Assignment assn)
  {
    return jtCurrent.lookupLogJoint(assn);
  }


  public double dumpLogJoint(Assignment assn)
  {
    return jtCurrent.dumpLogJoint(assn);
  }

  /**
   * Returns the JunctionTree computed from the last call to
   *  {@link #computeMarginals}.  Caller must not modify return value.
   */
  public JunctionTree lookupJunctionTree ()
  {
    return jtCurrent;
  }

  private Iterator neighborsIterator (UndirectedGraph g, Variable v)
  {
    return GraphHelper.neighborListOf (g, v).iterator ();
  }

  public void dump ()
  {
    if (jtCurrent != null) {
      System.out.println("Current junction tree");
      jtCurrent.dump();
    } else {
      System.out.println("NO current junction tree");
    }
  }


  transient private int totalMessagesSent = 0;

  /**
   * Returns the total number of messages this inferencer has sent.
   */
  public int getTotalMessagesSent () { return totalMessagesSent; }


  // Serialization
  private static final long serialVersionUID = 1;

  // If seralization-incompatible changes are made to these classes,
  //  then smarts can be added to these methods for backward compatibility.
  private void writeObject (ObjectOutputStream out) throws IOException {
     out.defaultWriteObject ();
   }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
     in.defaultReadObject ();
  }

} // JunctionTreeInferencer
