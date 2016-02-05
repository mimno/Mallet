/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;


import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.HashVarSet;
import cc.mallet.grmm.types.VarSet;
import cc.mallet.grmm.types.Variable;
import cc.mallet.util.MalletLogger;

/**
 * An implementation of Hugin-style propagation for junction trees.
 * This destructively modifies the junction tree so that its clique potentials
 * are the true marginals of the underlying graph.
 * <p/>
 * End users will not usually need to use this class directly.  Use
 * <tt>JunctionTreeInferencer</tt> instead.
 * <p/>
 * This class is not an instance of Inferencer because it destructively
 * modifies the junction tree, which the Inferencer methods do not do to
 * factor graphs.
 * <p/>
 * Created: Feb 1, 2006
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: JunctionTreePropagation.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
class JunctionTreePropagation implements Serializable {

  private static Logger logger = MalletLogger.getLogger (JunctionTreePropagation.class.getName ());

  transient private int totalMessagesSent = 0;

  private MessageStrategy strategy;

  public JunctionTreePropagation (MessageStrategy strategy)
  {
    this.strategy = strategy;
  }

  public static JunctionTreePropagation createSumProductInferencer ()
  {
    return new JunctionTreePropagation (new SumProductMessageStrategy ());
  }

  public static JunctionTreePropagation createMaxProductInferencer ()
  {
    return new JunctionTreePropagation (new MaxProductMessageStrategy ());
  }


  public int getTotalMessagesSent ()
  {
    return totalMessagesSent;
  }

  public void computeMarginals (JunctionTree jt)
  {
    propagate (jt);
    jt.normalizeAll ();      // Necessary if jt originally unnormalized
  }

/* Hugin-style propagation for junction trees */

  // bottom-up pass
  private void collectEvidence (JunctionTree jt, VarSet parent, VarSet child)
  {
    logger.finer ("collectEvidence " + parent + " --> " + child);
    for (Iterator it = jt.getChildren (child).iterator (); it.hasNext ();) {
      VarSet gchild = (VarSet) it.next ();
      collectEvidence (jt, child, gchild);
    }
    if (parent != null) {
      totalMessagesSent++;
      strategy.sendMessage (jt, child, parent);
    }
  }

  // top-down pass
  private void distributeEvidence (JunctionTree jt, VarSet parent)
  {
    for (Iterator it = jt.getChildren (parent).iterator (); it.hasNext ();) {
      VarSet child = (VarSet) it.next ();
      totalMessagesSent++;
      strategy.sendMessage (jt, parent, child);
      distributeEvidence (jt, child);
    }
  }

  private void propagate (JunctionTree jt)
  {
    VarSet root = (VarSet) jt.getRoot ();
    collectEvidence (jt, null, root);
    distributeEvidence (jt, root);
  }


  public Factor lookupMarginal (JunctionTree jt, VarSet varSet)
  {
    if (jt == null) { throw new IllegalStateException ("Call computeMarginals() first."); }

    VarSet parent = jt.findParentCluster (varSet);
    if (parent == null) {
      throw new UnsupportedOperationException
              ("No parent cluster in " + jt + " for clique " + varSet);
    }

    Factor cpf = jt.getCPF (parent);
    if (logger.isLoggable (Level.FINER)) {
      logger.finer ("Lookup jt marginal: clique " + varSet + " cluster " + parent);
      logger.finest ("  cpf " + cpf);
    }

    Factor marginal = strategy.extractBelief (cpf, varSet);
    marginal.normalize ();

    return marginal;
  }

  public Factor lookupMarginal (JunctionTree jt, Variable var)
  {
    if (jt == null) { throw new IllegalStateException ("Call computeMarginals() first."); }

    VarSet parent = jt.findParentCluster (var);
    Factor cpf = jt.getCPF (parent);
    if (logger.isLoggable (Level.FINER)) {
      logger.finer ("Lookup jt marginal: var " + var + " cluster " + parent);
      logger.finest (" cpf " + cpf);
    }

    Factor marginal = strategy.extractBelief (cpf, new HashVarSet (new Variable[] { var }));
    marginal.normalize ();

    return marginal;
  }

  ///////////////////////////////////////////////////////////////////////////
  //   MEESAGE STRATEGIES
  ///////////////////////////////////////////////////////////////////////////


  /**
   * Implements a strategy pattern for message sending.  This allows sum-product
   * and max-product messages, e.g., to be different implementations of this strategy.
   */
  public interface MessageStrategy {

    /**
     * Sends a message from the clique FROM to TO in a junction tree.
     */
    public void sendMessage (JunctionTree jt, VarSet from, VarSet to);

    public Factor extractBelief (Factor cpf, VarSet varSet);

  }

  public static class SumProductMessageStrategy implements MessageStrategy, Serializable {

    /**
     * This sends a sum-product message, normalized to avoid
     * underflow.
     */
    public void sendMessage (JunctionTree jt, VarSet from, VarSet to)
    {
      Collection sepset = jt.getSepset (from, to);
      Factor fromCpf = jt.getCPF (from);
      Factor toCpf = jt.getCPF (to);
      Factor oldSepsetPot = jt.getSepsetPot (from, to);
      Factor lambda = fromCpf.marginalize (sepset);

      lambda.normalize ();

      jt.setSepsetPot (lambda, from, to);
      toCpf = toCpf.multiply (lambda);
      toCpf.divideBy (oldSepsetPot);
      toCpf.normalize ();
      jt.setCPF (to, toCpf);
    }

    public Factor extractBelief (Factor cpf, VarSet varSet)
    {
      return cpf.marginalize (varSet);
    }

    // Serialization
    private static final long serialVersionUID = 1;
    private static final int CUURENT_SERIAL_VERSION = 1;

    private void writeObject (ObjectOutputStream out) throws IOException
    {
      out.defaultWriteObject ();
      out.writeInt (CUURENT_SERIAL_VERSION);
    }

    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
    {
      in.defaultReadObject ();
      in.readInt (); // version
    }
  }


  public static class MaxProductMessageStrategy implements MessageStrategy, Serializable {

    /**
     * This sends a max-product message.
     */
    public void sendMessage (JunctionTree jt, VarSet from, VarSet to)
    {
//      System.err.println ("Send message "+from+" --> "+to);
      Collection sepset = jt.getSepset (from, to);
      Factor fromCpf = jt.getCPF (from);
      Factor toCpf = jt.getCPF (to);
      Factor oldSepsetPot = jt.getSepsetPot (from, to);
      Factor lambda = fromCpf.extractMax (sepset);

      lambda.normalize ();

      jt.setSepsetPot (lambda, from, to);
      toCpf = toCpf.multiply (lambda);
      toCpf.divideBy (oldSepsetPot);
      toCpf.normalize ();
      jt.setCPF (to, toCpf);
    }

    public Factor extractBelief (Factor cpf, VarSet varSet)
    {
      return cpf.extractMax (varSet);
    }

    // Serialization
    private static final long serialVersionUID = 1;
    private static final int CUURENT_SERIAL_VERSION = 1;

    private void writeObject (ObjectOutputStream out) throws IOException
    {
      out.defaultWriteObject ();
      out.writeInt (CUURENT_SERIAL_VERSION);
    }

    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
    {
      in.defaultReadObject ();
      in.readInt (); // version
    }
  }


  // Serialization
  private static final long serialVersionUID = 1;
  private static final int CUURENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CUURENT_SERIAL_VERSION);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    in.readInt (); // version
  }

}
