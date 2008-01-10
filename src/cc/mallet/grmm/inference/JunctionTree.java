/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.inference;

import java.util.Set;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Arrays;

import cc.mallet.grmm.types.*;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectIterator;


/**
 * Datastructure for a junction tree.
 *
 * Created: Tue Sep 30 10:30:25 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: JunctionTree.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class JunctionTree extends Tree {

  private int numNodes;

  private static class Sepset {

    Sepset(Set s, Factor p)
    {
      set = s;
      ptl = p;
    }


    Set set;
    Factor ptl;
  }

  private TIntObjectHashMap sepsets;
  private Factor[] cpfs;


  public JunctionTree(int size)
  {
    super();

    numNodes = size;
    sepsets = new TIntObjectHashMap();
    cpfs = new Factor[size];
  } // JunctionTree constructor


  public void addNode (Object parent1, Object child1)
  {
    super.addNode(parent1, child1);
    VarSet parent = (VarSet) parent1;
    VarSet child = (VarSet) child1;
    Set sepset = parent.intersection(child);
    int id1 = lookupIndex(parent);
    int id2 = lookupIndex(child);
    putSepset(id1, id2, new Sepset (sepset, newSepsetPtl (sepset)));
  }

  private Factor newSepsetPtl (Set sepset)
  {
    if (sepset.isEmpty ()) {
      // use identity factor
      return ConstantFactor.makeIdentityFactor ();
    } else {
      return new TableFactor (sepset);
    }
  }

  private int hashIdxIdx(int id1, int id2)
  {
    assert (id1 < 65536) && (id2 < 65536);

    int id;
    if (id1 < id2) {
      id = (id1 << 16) | id2;
    } else {
      id = (id2 << 16) | id1;
    }
    return id;
  }


  private void putSepset(int id1, int id2, Sepset sepset)
  {
    int id = hashIdxIdx(id1, id2);
    sepsets.put(id, sepset);
  }


  private Sepset getSepset(int id1, int id2)
  {
    int id = hashIdxIdx(id1, id2);
    return (Sepset) sepsets.get(id);
  }

  //  CPF accessors

  public Factor getCPF(VarSet c)
  {
    return cpfs[lookupIndex(c)];
  }


  public void setCPF(VarSet c, Factor pot)
  {
    cpfs[lookupIndex(c)] = pot;
  }


  void clearCPFs()
  {
    for (int i = 0; i < cpfs.length; i++) {
      cpfs[i] = new TableFactor ((VarSet) lookupVertex (i));
    }

    TIntObjectIterator it = sepsets.iterator();
    while (it.hasNext()) {
      it.advance();
      Sepset sepset = (Sepset) it.value();
      sepset.ptl = newSepsetPtl (sepset.set);
    }

  }


  public Set sepsetPotentials()
  {
    THashSet set = new THashSet();
    TIntObjectIterator it = sepsets.iterator();
    while (it.hasNext()) {
      it.advance();
      Factor ptl = ((Sepset) it.value()).ptl;
      set.add(ptl);
    }

    return set;
  }


  void setSepsetPot(Factor pot, VarSet v1, VarSet v2)
  {
    int id1 = lookupIndex(v1);
    int id2 = lookupIndex(v2);
    getSepset(id1, id2).ptl = pot;
  }


  public Factor getSepsetPot(VarSet v1, VarSet v2)
  {
    int id1 = lookupIndex(v1);
    int id2 = lookupIndex(v2);
    return getSepset(id1, id2).ptl;
  }

  /**
   * Returns a collection of all the potentials of cliques in the junction tree.
   *  (i.e., these are the terms in the numerator of the jounction tre theorem).
   * @see #sepsetPotentials()
   */
  public Collection clusterPotentials ()
  {
    HashSet h = new HashSet();
    for (int i = 0; i < cpfs.length; i++) {
      if (cpfs[i] != null) {
        h.add(cpfs[i]);
      }
    }
    return h;
  }


  public Set getSepset(VarSet v1, VarSet v2)
  {
    int id1 = lookupIndex(v1);
    int id2 = lookupIndex(v2);
    return getSepset(id1, id2).set;
  }


  public Factor lookupMarginal(Variable var)
  {
    VarSet c = findParentCluster(var);
    Factor pot = getCPF(c);
    return pot.marginalize(var);
  }


  public double lookupLogJoint(Assignment assn)
  {
    double accum = 0;
    for (int i = 0; i < cpfs.length; i++) {
      if (cpfs[i] != null) {
        double phi = cpfs[i].logValue (assn);
        accum += phi;
      }
    }

    TIntObjectIterator it = sepsets.iterator();
    while (it.hasNext()) {
      it.advance();
      Factor ptl = ((Sepset) it.value()).ptl;
      double phi = ptl.logValue (assn);
      accum -= phi;
    }

    return accum;
  }


  /** Returns a cluster in the tree that contains var. */
  public VarSet findParentCluster(Variable var)
  {
    int best = Integer.MAX_VALUE;
    VarSet retval = null;
    // xxx Inefficient
    for (Iterator it = getVerticesIterator(); it.hasNext();) {
      VarSet c = (VarSet) it.next();
      if (c.contains(var) && c.weight() < best) {
        retval = c;
        best = c.weight();
      }
    }
    return retval;
  }


  /**
   * Returns a cluster in the tree that contains all the vars in a
   *   collection.
   */
  public VarSet findParentCluster(Collection vars)
  {
    int best = Integer.MAX_VALUE;
    VarSet retval = null;
    // xxx Inefficient
    for (Iterator it = getVerticesIterator(); it.hasNext();) {
      VarSet c = (VarSet) it.next();
      if (c.containsAll(vars) && c.weight() < best) {
        retval = c;
        best = c.weight();
      }
    }
    return retval;
  }


  /** Returns a cluster in the tree that contains exactly the given
   * 	variables, or null if no such cluster exists. */
  public VarSet findCluster(Variable[] vars)
  {
    List l = Arrays.asList(vars);
    for (Iterator it = getVerticesIterator(); it.hasNext();) {
      VarSet c2 = (VarSet) it.next();
      if (c2.containsAll(l) && l.containsAll(c2))
        return c2;
    }
    return null;
  }


  /** Normalizes all potentials in the tree, both node and sepset. */
  public void normalizeAll()
  {
    int n = cpfs.length;
    for (int i = 0; i < n; i++) {
      if (cpfs[i] != null) {
        cpfs[i].normalize();
      }
    }

    TIntObjectIterator it = sepsets.iterator();
    while (it.hasNext()) {
      it.advance();
      Factor ptl = ((Sepset) it.value()).ptl;
      ptl.normalize();
    }
  }


  int getId(VarSet c)
  {
    return lookupIndex(c);
  }

// Debugging functions

  public void dump ()
  {
    int n = cpfs.length;
    // This will cause OpenJGraph to print all our nodes and edges
    System.out.println(this);
    // Now lets print all the cpfs
    System.out.println("Vertex CPFs");
    for (int i = 0; i < n; i++) {
      if (cpfs[i] != null) {
        System.out.println("CPF "+i+" "+cpfs[i].dumpToString ());
      }
    }

    // And the sepset potentials
    System.out.println("sepset CPFs");
    TIntObjectIterator it = sepsets.iterator();
    while (it.hasNext()) {
      it.advance();
      Factor ptl = ((Sepset) it.value()).ptl;
      System.out.println(ptl.dumpToString ());
    }
    System.out.println ("/End JT");
  }

  public double dumpLogJoint (Assignment assn)
  {
    double accum = 0;
    for (int i = 0; i < cpfs.length; i++) {
      if (cpfs[i] != null) {
        double phi = cpfs[i].logValue (assn);
        System.out.println ("CPF "+i+" accum = "+accum);
      }
    }

    TIntObjectIterator it = sepsets.iterator();
    while (it.hasNext()) {
      it.advance();
      Factor ptl = ((Sepset) it.value()).ptl;
      double phi = ptl.logValue (assn);
      System.out.println("Sepset "+ptl.varSet()+" accum "+accum);
    }

    return accum;
  }

  public boolean isNaN()
  {
    int n = cpfs.length;
    for (int i = 0; i < n; i++)
      if (cpfs[i].isNaN()) return true;

    // And the sepset potentials
    TIntObjectIterator it = sepsets.iterator();
    while (it.hasNext()) {
      it.advance();
      Factor ptl = ((Sepset) it.value()).ptl;
      if (ptl.isNaN()) return true;
    }

    return false;
  }

  public double entropy ()
  {
    double entropy = 0;
    for (Iterator it = clusterPotentials ().iterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      entropy += ptl.entropy ();
    }
    for (Iterator it = sepsetPotentials ().iterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      entropy -= ptl.entropy ();
    }
    return entropy;
  }



// Implementation of edu.umass.cs.mallet.users.casutton.graphical.Compactible

  public void decompact()
  {
    cpfs = new Factor[numNodes];
    clearCPFs();
  }


  public void compact()
  {
    cpfs = null;
  }

} // JunctionTree
