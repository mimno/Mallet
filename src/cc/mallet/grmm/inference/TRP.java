/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.inference;

import gnu.trove.THashSet;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.*;
import java.io.*;

import org._3pq.jgrapht.UndirectedGraph;
import org._3pq.jgrapht.Graph;
import org._3pq.jgrapht.Edge;
import org._3pq.jgrapht.traverse.BreadthFirstIterator;
import org._3pq.jgrapht.graph.SimpleGraph;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import cc.mallet.grmm.types.*;
import cc.mallet.util.MalletLogger;

/**
 * Implementation of Wainwright's TRP schedule for loopy BP
 * in general graphical models.
 *
 * @author Charles Sutton
 * @version $Id: TRP.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class TRP extends AbstractBeliefPropagation {

  private static Logger logger = MalletLogger.getLogger (TRP.class.getName ());

  private static final boolean reportSpanningTrees = false;

  private TreeFactory factory;

  private TerminationCondition terminator;

  private Random random = new Random ();

  /* Make sure that we've included all edges before we terminate. */
  transient private TIntObjectHashMap factorTouched;

  transient private boolean hasConverged;

  transient private File verboseOutputDirectory = null;

  public TRP ()
  {
    this (null, null);
  }

  public TRP (TreeFactory f)
  {
    this (f, null);
  }

  public TRP (TerminationCondition cond)
  {
    this (null, cond);
  }

  public TRP (TreeFactory f, TerminationCondition cond)
  {
    factory = f;
    terminator = cond;
  }

  public static TRP createForMaxProduct ()
  {
    TRP trp = new TRP ();
    trp.setMessager (new MaxProductMessageStrategy ());
    return trp;
  }

  // Accessors

  public TRP setTerminator (TerminationCondition cond)
  {
    terminator = cond;
    return this;
  }

  public TRP setFactory (TreeFactory factory)
  {
    this.factory = factory;
    return this;
  }

  // xxx should this be static?
  public void setRandomSeed (long seed) { random = new Random (seed); }

  public void setVerboseOutputDirectory (File verboseOutputDirectory)
  {
    this.verboseOutputDirectory = verboseOutputDirectory;
  }

  public boolean isConverged () { return hasConverged; }

  protected void initForGraph (FactorGraph m)
  {
    super.initForGraph (m);

    int numNodes = m.numVariables ();
    factorTouched = new TIntObjectHashMap (numNodes);
    hasConverged = false;

    if (factory == null) {
      factory = new AlmostRandomTreeFactory ();
    }

    if (terminator == null) {
      terminator = new DefaultConvergenceTerminator ();
    } else {
      terminator.reset ();
    }
  }

  private static cc.mallet.grmm.types.Tree graphToTree (Graph g) throws Exception
  {
    // Perhaps handle gracefully?? -cas
    if (g.vertexSet ().size () <= 0) {
      throw new RuntimeException ("Empty graph.");
    }
    Tree tree = new cc.mallet.grmm.types.Tree ();
    Object root = g.vertexSet ().iterator ().next ();
    tree.add (root);

    for (Iterator it1 = new BreadthFirstIterator (g, root); it1.hasNext();) {
      Object v1 = it1.next ();
      for (Iterator it2 = g.edgesOf (v1).iterator (); it2.hasNext ();) {
        Edge edge = (Edge) it2.next ();
        Object v2 = edge.oppositeVertex (v1);
          if (tree.getParent (v1) != v2) {
            tree.addNode (v1, v2);
            assert tree.getParent (v2) == v1;
          }
        }
      }

    return tree;
  }

  /**
   * Interface for tree-generation strategies for TRP.
   * <p/>
   * TRP works by repeatedly doing exact inference over spanning tree
   * of the original graph.  But the trees can be chosen arbitrarily.
   * In fact, they don't need to be spanning trees; any acyclic
   * substructure will do.  Users of TRP can tell it which strategy
   * to use by passing in an implementation of TreeFactory.
   */
  public interface TreeFactory extends Serializable {
    public cc.mallet.grmm.types.Tree nextTree (FactorGraph mdl);
  }

  // This works around what appears to be a bug in OpenJGraph
  // connected sets.
  private static class SimpleUnionFind {

    private Map obj2set = new THashMap ();

    private Set findSet (Object obj)
    {
      Set container = (Set) obj2set.get (obj);
      if (container != null) {
        return container;
      } else {
        Set newSet = new THashSet ();
        newSet.add (obj);
        obj2set.put (obj, newSet);
        return newSet;
      }
    }

    private void union (Object obj1, Object obj2)
    {
      Set set1 = findSet (obj1);
      Set set2 = findSet (obj2);
      set1.addAll (set2);
      for (Iterator it = set2.iterator (); it.hasNext ();) {
        Object obj = it.next ();
        obj2set.put (obj, set1);
      }
    }

    public boolean noPairConnected (VarSet varSet)
    {
      for (int i = 0; i < varSet.size (); i++) {
        for (int j = i + 1; j < varSet.size (); j++) {
          Variable v1 = varSet.get (i);
          Variable v2 = varSet.get (j);
          if (findSet (v1) == findSet (v2)) {
            return false;
          }
        }

      }
      return true;
    }

    public void unionAll (Factor factor)
    {
      VarSet varSet = factor.varSet ();
      for (int i = 0; i < varSet.size (); i++) {
        Variable var = varSet.get (i);
        union (var, factor);
      }
    }

  }


  /**
   * Always adds edges that have not been touched, after that
   * adds random edges.
   */
  public class AlmostRandomTreeFactory implements TreeFactory {

    public Tree nextTree (FactorGraph fullGraph)
    {
      SimpleUnionFind unionFind = new SimpleUnionFind ();
      ArrayList edges = new ArrayList (fullGraph.factors ());
      ArrayList goodEdges = new ArrayList (fullGraph.numVariables ());
      Collections.shuffle (edges, random);

      // First add all edges that haven't been used so far
      try {
        for (Iterator it = edges.iterator (); it.hasNext ();) {
          Factor factor = (Factor) it.next ();
          VarSet varSet = factor.varSet ();
          if (!isFactorTouched (factor) && unionFind.noPairConnected (varSet)) {
            goodEdges.add (factor);
            unionFind.unionAll (factor);
            it.remove ();
          }
        }

        // Now add as many other edges as possible
        for (Iterator it = edges.iterator (); it.hasNext ();) {
          Factor factor = (Factor) it.next ();
          VarSet varSet = factor.varSet ();
          if (unionFind.noPairConnected (varSet)) {
            goodEdges.add (factor);
            unionFind.unionAll (factor);
          }
        }

        for (Iterator it = goodEdges.iterator (); it.hasNext ();) {
          Factor factor = (Factor) it.next ();
          touchFactor (factor);
        }

        UndirectedGraph g = new SimpleGraph ();
        for (Iterator it = fullGraph.variablesIterator (); it.hasNext ();) {
          Variable var = (Variable) it.next ();
          g.addVertex (var);
        }

        for (Iterator it = goodEdges.iterator (); it.hasNext ();) {
          Factor factor = (Factor) it.next ();
          g.addVertex (factor);
          for (Iterator vit = factor.varSet ().iterator (); vit.hasNext ();) {
            Variable var = (Variable) vit.next ();
            g.addEdge (factor, var);
          }
        }

        Tree tree = graphToTree (g);
        if (reportSpanningTrees) {
          System.out.println ("********* SPANNING TREE *************");
          System.out.println (tree.dumpToString ());
          System.out.println ("********* END TREE *************");
        }

        return tree;
      } catch (Exception e) {
        e.printStackTrace ();
        throw new RuntimeException (e);
      }
    }

    private static final long serialVersionUID = -7461763414516915264L;
  }

  /**
   * Generates spanning trees cyclically from a predefined collection.
   */
  static public class TreeListFactory implements TreeFactory {

    private List lst;
    private Iterator it;

    public TreeListFactory (List l)
    {
      lst = l;
      it = lst.iterator ();
    }

    public TreeListFactory (cc.mallet.grmm.types.Tree[] arr)
    {
      lst = new ArrayList (java.util.Arrays.asList (arr));
      it = lst.iterator ();
    }

    public static TreeListFactory makeFromReaders (FactorGraph fg, List readerList)
    {
      List treeList = new ArrayList ();
      for (Iterator it = readerList.iterator (); it.hasNext ();) {
        try {
          Reader reader = (Reader) it.next ();
          Document doc = new SAXBuilder ().build (reader);
          Element treeElt = doc.getRootElement ();
          Element rootElt = (Element) treeElt.getChildren ().get (0);
          Tree tree = readTreeRec (fg, rootElt);
          System.out.println (tree.dumpToString ());
          treeList.add (tree);
        } catch (JDOMException e) {
          throw new RuntimeException (e);
        } catch (IOException e) {
          throw new RuntimeException (e);
        }
      }
      return new TreeListFactory (treeList);
    }

    /** @param fileList List of File objects.  Each file should be an XML document describing a tree. */
    public static TreeListFactory readFromFiles (FactorGraph fg, List fileList)
    {
      List treeList = new ArrayList ();
      for (Iterator it = fileList.iterator (); it.hasNext ();) {
        try {
          File treeFile = (File) it.next ();
          Document doc = new SAXBuilder ().build (treeFile);
          Element treeElt = doc.getRootElement ();
          Element rootElt = (Element) treeElt.getChildren ().get (0);
          treeList. add (readTreeRec (fg, rootElt));
        } catch (JDOMException e) {
          throw new RuntimeException (e);
        } catch (IOException e) {
          throw new RuntimeException (e);
        }
      }
      return new TreeListFactory (treeList);
    }

    private static Tree readTreeRec (FactorGraph fg, Element elt)
    {
      List subtrees = new ArrayList ();
      for (Iterator it = elt.getChildren ().iterator (); it.hasNext ();) {
        Element child = (Element) it.next ();
        Tree subtree = readTreeRec (fg, child);
        subtrees.add (subtree);
      }

      Object parent = objFromElt (fg, elt);
      return Tree.makeFromSubtree (parent, subtrees);
    }

    private static Object objFromElt (FactorGraph fg, Element elt)
    {
      String type = elt.getName ();

      if (type.equals ("VAR")) {
        String vname = elt.getAttributeValue ("NAME");
        return fg.findVariable (vname);
      } else if (type.equals("FACTOR")) {
        String varSetStr = elt.getAttributeValue ("VARS");
        String[] vnames = varSetStr.split ("\\s+");
        Variable[] vars = new Variable [vnames.length];
        for (int i = 0; i < vnames.length; i++) {
          vars[i] = fg.findVariable (vnames[i]);
        }
        return fg.factorOf (new HashVarSet (vars));
      } else {
        throw new RuntimeException ("Can't figure out element "+elt);
      }
    }

    public cc.mallet.grmm.types.Tree nextTree (FactorGraph mdl)
    {
      // If no more trees, rewind.
      if (!it.hasNext ()) {
        it = lst.iterator ();
      }
      return (cc.mallet.grmm.types.Tree) it.next ();
    }

  }

  // Termination conditions

  // will this need to be subclassed from outside?  Will such
  // subclasses need access to the private state of TRP?
  static public interface TerminationCondition extends Cloneable, Serializable {
    // This takes the instances of trp as a parameter so that if a
    //  TRP instance is cloned, and the terminator copied over, it
    //  will still work.
    public boolean shouldContinue (TRP trp);

    public void reset ();

    // boy do I hate Java cloning
    public Object clone () throws CloneNotSupportedException;
  }

  static public class IterationTerminator implements TerminationCondition {
    int current;
    int max;

    public void reset () { current = 0; }

    public IterationTerminator (int m)
    {
      max = m;
      reset ();
    }

    public boolean shouldContinue (TRP trp)
    {
      current++;
      if (current >= max) {
        logger.finest ("***TRP quitting: Iteration " + current + " >= " + max);
      }
      return current <= max;
    }

    public Object clone () throws CloneNotSupportedException
    {
      return super.clone ();
    }
  }

  //xxx Delta is currently ignored.
  public static class ConvergenceTerminator implements TerminationCondition {
    double delta = 0.01;

    public ConvergenceTerminator () {}

    public ConvergenceTerminator (double delta) { this.delta = delta; }

    public void reset ()
    {
    }

    public boolean shouldContinue (TRP trp)
    {
/*
			if (oldMessages != null) 
				retval = !checkForConvergence (trp);
			copyMessages(trp);
			
			return retval;
			*/
      boolean retval = !trp.hasConverged (delta);
      trp.copyOldMessages ();
      return retval;
    }

    public Object clone () throws CloneNotSupportedException
    {
      return super.clone ();
    }

  }

  // Runs until convergence, but doesn't stop until all edges have
  // been used at least once, and always stops after 1000 iterations.
  public static class DefaultConvergenceTerminator implements TerminationCondition {
    ConvergenceTerminator cterminator;
    IterationTerminator iterminator;

    String msg;

    public DefaultConvergenceTerminator () { this (0.001, 1000); }

    public DefaultConvergenceTerminator (double delta, int maxIter)
    {
      cterminator = new ConvergenceTerminator (delta);
      iterminator = new IterationTerminator (maxIter);
      msg = "***TRP quitting: over " + maxIter + " iterations";
    }

    public void reset ()
    {
      iterminator.reset ();
      cterminator.reset ();
    }

    // Terminate if converged or at insanely high # of iterations
    public boolean shouldContinue (TRP trp)
    {
      boolean notAllTouched = !trp.allEdgesTouched ();

      if (!iterminator.shouldContinue (trp)) {
        logger.warning (msg);
        if (notAllTouched) {
          logger.warning ("***TRP warning: Not all edges used!");
        }
        return false;
      }

      if (notAllTouched) {
        return true;
      } else {
        return cterminator.shouldContinue (trp);
      }
    }

    public Object clone () throws CloneNotSupportedException
    {
      DefaultConvergenceTerminator dup = (DefaultConvergenceTerminator)
              super.clone ();
      dup.iterminator = (IterationTerminator) iterminator.clone ();
      dup.cterminator = (ConvergenceTerminator) cterminator.clone ();
      return dup;
    }

  }

  // And now, the heart of TRP:

  public void computeMarginals (FactorGraph m)
  {
    resetMessagesSentAtStart ();
    initForGraph (m);

    int iter = 0;
    while (terminator.shouldContinue (this)) {
      logger.finer ("TRP iteration " + (iter++));
      cc.mallet.grmm.types.Tree tree = factory.nextTree (m);
      propagate (tree);
      dumpForIter (iter, tree);
    }
    iterUsed = iter;
    logger.info ("TRP used " + iter + " iterations.");

    doneWithGraph (m);
  }

  private void dumpForIter (int iter, Tree tree)
  {
    if (verboseOutputDirectory != null) {
      try {
        // output messages
        FileWriter writer = new FileWriter (new File (verboseOutputDirectory, "iter" + iter + ".txt"));
        dump (new PrintWriter (writer, true));
        writer.close ();
        
        FileWriter bfWriter = new FileWriter (new File (verboseOutputDirectory, "beliefs" + iter + ".txt"));
        dumpBeliefs (new PrintWriter (bfWriter, true));
        bfWriter.close ();

        // output spanning tree
        FileWriter treeWriter = new FileWriter (new File (verboseOutputDirectory, "tree" + iter + ".txt"));
        treeWriter.write (tree.toString ());
        treeWriter.write ("\n");
        treeWriter.close ();

      } catch (IOException e) {
        e.printStackTrace ();
      }
    }
  }

  private void dumpBeliefs (PrintWriter writer)
  {
    for (int vi = 0; vi < mdlCurrent.numVariables (); vi++) {
      Variable var = mdlCurrent.get (vi);
      Factor mrg = lookupMarginal (var);
      writer.println (mrg.dumpToString ());
      writer.println ();
    }
  }


  private void propagate (cc.mallet.grmm.types.Tree tree)
  {
    Object root = tree.getRoot ();
    lambdaPropagation (tree, root);
    piPropagation (tree, root);
  }

  /** Sends BP messages starting from children to parents.  This version uses constant stack space. */
  private void lambdaPropagation (cc.mallet.grmm.types.Tree tree, Object root)
  {
    LinkedList openList = new LinkedList ();
    LinkedList closedList = new LinkedList ();
    openList.addAll (tree.getChildren (root));
    while (!openList.isEmpty ()) {
      Object var = openList.removeFirst ();
      openList.addAll (tree.getChildren (var));
      closedList.addFirst (var);
    }

    // Now open list contains all of the nodes (except the root) in reverse topological order.  Send the messages.
    for (Iterator it = closedList.iterator (); it.hasNext ();) {
      Object child = it.next ();
      Object parent = tree.getParent (child);
      sendMessage (mdlCurrent, child, parent);
    }
  }


  /** Sends BP messages starting from parents to children.  This version uses constant stack space. */
  private void piPropagation (cc.mallet.grmm.types.Tree tree, Object root)
  {
    LinkedList openList = new LinkedList ();
    openList.add (root);

    while (!openList.isEmpty ()) {
      Object current = openList.removeFirst ();
      List children = tree.getChildren (current);
      for (Iterator it = children.iterator (); it.hasNext ();) {
        Object child = it.next ();
        sendMessage (mdlCurrent, current, child);
        openList.add (child);
      }
    }
  }

  private void sendMessage (FactorGraph fg, Object parent, Object child)
  {
    if (logger.isLoggable (Level.FINER)) logger.finer ("Sending message: "+parent+" --> "+child);
    if (parent instanceof Factor) {
      sendMessage (fg, (Factor) parent, (Variable) child);
    } else if (parent instanceof Variable) {
      sendMessage (fg, (Variable) parent, (Factor) child);
    }
  }

  private boolean allEdgesTouched ()
  {
    Iterator it = mdlCurrent.factorsIterator ();
    while (it.hasNext ()) {
      Factor factor = (Factor) it.next ();
      int idx = mdlCurrent.getIndex (factor);
      int numTouches = getNumTouches (idx);
      if (numTouches == 0) {
        logger.finest ("***TRP continuing: factor " + idx
                + " not touched.");
        return false;
      }
    }
    return true;
  }

  private void touchFactor (Factor factor)
  {
    int idx = mdlCurrent.getIndex (factor);
    incrementTouches (idx);
  }

  private boolean isFactorTouched (Factor factor)
  {
    int idx1 = mdlCurrent.getIndex (factor);
    return (getNumTouches (idx1) > 0);
  }

  private int getNumTouches (int idx1)
  {
    Integer integer = (Integer) factorTouched.get (idx1);
    return (integer == null) ? 0 : integer.intValue ();
  }

  private void incrementTouches (int idx1)
  {
    int nt = getNumTouches (idx1);
    factorTouched.put (idx1, new Integer (nt + 1));
  }

  public Factor query (DirectedModel m, Variable var)
  {
    throw new UnsupportedOperationException
            ("GRMM doesn't yet do directed models.");
  }

  //xxx could get moved up to AbstractInferencer, if mdlCurrent did.
  public Assignment bestAssignment ()
  {
    int[] outcomes = new int [mdlCurrent.numVariables ()];
    for (int i = 0; i < outcomes.length; i++) {
      Variable var = mdlCurrent.get (i);
      TableFactor ptl = (TableFactor) lookupMarginal (var);
      outcomes[i] = ptl.argmax ();
    }

    return new Assignment (mdlCurrent, outcomes);
  }

  // Deep copy termination condition
  public Object clone ()
  {
    try {
      TRP dup = (TRP) super.clone ();
      if (terminator != null) {
        dup.terminator = (TerminationCondition) terminator.clone ();
      }
      return dup;
    } catch (CloneNotSupportedException e) {
      // should never happen
      throw new RuntimeException (e);
    }
  }

  // Serialization
  private static final long serialVersionUID = 1;

  // If seralization-incompatible changes are made to these classes,
  //  then smarts can be added to these methods for backward compatibility.
  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
  }

}

