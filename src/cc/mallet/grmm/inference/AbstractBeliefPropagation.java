package cc.mallet.grmm.inference;


import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.Iterator;
import java.io.*;

import cc.mallet.grmm.types.*;
import cc.mallet.util.MalletLogger;

/**
 * Abstract base class for umplementations of belief propagation for general factor graphs.
 * This class manages arrays of messages, computing beliefs from messages, and convergence
 * thresholds.
 * <p/>
 * How to send individual messages (e.g., sum-product, max-product, etc) are mananged
 * by istances of the interface @link{#MessageStrategy}.  Concrete subclasses decide
 * which order to send messages in.
 *
 * @author Charles Sutton
 * @version $Id: AbstractBeliefPropagation.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public abstract class AbstractBeliefPropagation extends AbstractInferencer {

  protected static Logger logger = MalletLogger.getLogger (AbstractBeliefPropagation.class.getName ());
  private static final boolean diagnoseConvergence = false;

  protected boolean normalizeBeliefs = true;

  static private int totalMessagesSent = 0;
  transient private int myMessagesSent = 0;
  transient private int messagesSentAtStart = 0;
  private double threshold = 0.00001;
  protected boolean useCaching = false;

  private MessageStrategy messager;
  protected transient int iterUsed;

  protected AbstractBeliefPropagation ()
  {
    this (new SumProductMessageStrategy ());
  }

  protected AbstractBeliefPropagation (MessageStrategy messager)
  {
    this.messager = messager;
  }

  public MessageStrategy getMessager ()
  {
    return messager;
  }

  public AbstractBeliefPropagation setMessager (MessageStrategy messager)
  {
    this.messager = messager;
    return this;
  }

  /**
   * Returns the total number of messages all BP inferencers have sent in the current Java image.
   */
  public static int getTotalMessagesSent ()
  { return totalMessagesSent; }

  /** Returns the total number of messages this inferencer has sent since its creation. */
  public int getMessagesSent () { return myMessagesSent; }

  /**
   * Returns the number of messages sent during the last call to
   * computeMarginals.
   */
  public int getMessagesUsedLastTime ()
  {
    return myMessagesSent - messagesSentAtStart;
  }

  protected void resetMessagesSentAtStart ()
  {
    messagesSentAtStart = myMessagesSent;
  }


  /**
   * Array that maps (to, from) to the lambda message sent from node
   * from to node to.
   */
  transient private MessageArray messages;
  transient private MessageArray oldMessages; // messages from variable --> factor
  transient private Factor[] bel;

  protected transient FactorGraph mdlCurrent;

  private void retrieveCachedMessages (FactorGraph m)
  {
    messages = (MessageArray) m.getInferenceCache (getClass ());
  }

  private void cacheMessages (FactorGraph m)
  {
    m.setInferenceCache (getClass (), messages);
  }

  private void clearOldMessages ()
  {
    oldMessages = null;
  }

  final protected void copyOldMessages ()
  {
    clearOldMessages ();
    oldMessages = messages.duplicate ();
  }

  final protected boolean hasConverged ()
  {
    return hasConverged (this.threshold);
  }

  final protected boolean hasConverged (double threshold)
  {
    double maxDiff = Double.NEGATIVE_INFINITY;
    Factor bestOldMsg = null, bestNewMsg = null;

    for (MessageArray.Iterator msgIt = oldMessages.iterator (); msgIt.hasNext ();) {
      Factor oldMsg = (Factor) msgIt.next ();
      Object from = msgIt.from ();
      Object to = msgIt.to ();
      Factor newMsg = messages.get (from, to);

      if (oldMsg != null) {
        assert newMsg != null
                : "Message went from nonnull to null " + from + " --> " + to;
        for (java.util.Iterator it = oldMsg.assignmentIterator (); it.hasNext ();) {
          Assignment assn = (Assignment) it.next ();
          double val1 = oldMsg.value (assn);
          double val2 = newMsg.value (assn);

          double diff = Math.abs (val1 - val2);
          if (diff > threshold) {
            if (diagnoseConvergence) {
              System.err.println ("*** Not converged: Difference of : " + diff + " from " + oldMsg + " --> " + newMsg);
            }
            return false;
          } else if (diff > maxDiff) {
            maxDiff = diff;
            bestOldMsg = oldMsg;
            bestNewMsg = newMsg;
          }
        }
      }
    }

    if (diagnoseConvergence) {
      System.err.println (
              "*** CONVERGED: Max absolute difference : " + maxDiff + " from " + bestOldMsg + " --> " + bestNewMsg);
    }

    return true;
  }

  private void initOldMessages (FactorGraph fg)
  {
    oldMessages = new MessageArray (fg);
    if (useCaching && fg.getInferenceCache (getClass ()) != null) {
      logger.info ("AsyncLoopyBP: Reusing previous marginals");
      retrieveCachedMessages (fg);
      copyOldMessages ();
    } else {
      for (java.util.Iterator it = fg.factorsIterator (); it.hasNext ();) {
        Factor factor = (Factor) it.next ();
        VarSet varset = factor.varSet ();
        for (java.util.Iterator vit = varset.iterator (); vit.hasNext ();) {
          Variable var = (Variable) vit.next ();
          oldMessages.put (var, factor, new TableFactor (var));
          oldMessages.put (factor, var, new TableFactor (var));
        }
      }
    }
  }

  transient protected int assignedVertexPtls[];

  protected void initForGraph (FactorGraph mdl)
  {
    mdlCurrent = mdl;

    int numV = mdl.numVariables ();
    bel = new Factor [numV];

    Object cache = mdl.getInferenceCache (getClass ());
    if (useCaching && (cache != null)) {
      messages = (MessageArray) cache;
    } else {
      messages = new MessageArray (mdl);

      /*
      // setup self-messages for vertex potentials
      for (Iterator it = mdl.getVerticesIterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        Factor ptl = mdl.factorOfVar (var);
        if (ptl != null) {
          if (inLogSpace) {
            logger.finer ("BeliefPropagation: Using log space.");
            setMessage (i, i, new LogTableFactor ((AbstractTableFactor) ptl));
          } else {
            setMessage (i, i, ptl);
          }
        }
      }
    */

    }

    initOldMessages (mdl);
    messager.setMessageArray (messages, oldMessages);
  }

  protected void sendMessage (FactorGraph mdl, Variable from, Factor to)
  {
    totalMessagesSent++; myMessagesSent++;
//    System.err.println (GeneralUtils.classShortName (this)+" send message "+from+" --> "+to);
    messager.sendMessage (mdl, from, to);
  }

  protected void sendMessage (FactorGraph mdl, Factor from, Variable to)
  {
    totalMessagesSent++; myMessagesSent++;
//    System.err.println (GeneralUtils.classShortName (this)+" send message "+from+" --> "+to);
    messager.sendMessage (mdl, from, to);
  }

  protected void doneWithGraph (FactorGraph mdl)
  {
    clearOldMessages (); // free up memory
    if (useCaching) cacheMessages (mdl);
  }

  public int iterationsUsed () { return iterUsed; }


  public interface MessageStrategy {
    void setMessageArray (MessageArray msgs, MessageArray oldMsgs);

    void sendMessage (FactorGraph mdl, Factor from, Variable to);

    void sendMessage (FactorGraph mdl, Variable from, Factor to);

    Factor msgProduct (Factor product, int idx, int excludeMsgFrom);
  }


  public abstract static class AbstractMessageStrategy implements MessageStrategy {

    protected MessageArray messages;
    protected MessageArray oldMessages;

    public void setMessageArray (MessageArray msgs, MessageArray oldMsgs)
    {
      messages = msgs;
      oldMessages = oldMsgs;
    }

    public Factor msgProduct (Factor product, int idx, int excludeMsgFrom)
    {
      if (product == null) {
        product = createEmptyFactorForVar (idx);
      }

      for (MessageArray.ToMsgsIterator it = messages.toMessagesIterator (idx); it.hasNext ();) {
        it.next ();
        int j = it.currentFromIdx ();
        Factor msg = it.currentMessage ();
        if (j != excludeMsgFrom) {
          product.multiplyBy (msg);
//          assert product.varSet ().size () <= 2;
        }
      }
      return product;
    }

    private Factor createEmptyFactorForVar (int idx)
    {
      Factor product;
      if (messages.isInLogSpace ()) {
        product = new LogTableFactor ((Variable) messages.idx2obj (idx));
      } else {
        product = new TableFactor ((Variable) messages.idx2obj (idx));
      }
      return product;
    }

  }


  public static class SumProductMessageStrategy extends AbstractMessageStrategy implements Serializable {

    private double damping = 1.0;

    public SumProductMessageStrategy ()
    {
    }

    public SumProductMessageStrategy (double damping)
    {
      this.damping = damping;
    }

    public void sendMessage (FactorGraph mdl, Factor from, Variable to)
    {
      int fromIdx = messages.getIndex (from);
      int toIdx = messages.getIndex (to);

      Factor product = from.duplicate ();
      msgProduct (product, fromIdx, toIdx);

      Factor msg = product.marginalize (to);
      msg.normalize ();

      if (logger.isLoggable (Level.FINEST)) {
	  logger.info ("MSG "+from+" --> "+to);
	  logger.info ("FACTOR: "+from.dumpToString());
	  logger.info ("MSG: "+msg.dumpToString ());
	  logger.info ("END MSG "+from+" --> "+to);
      }

      assert msg.varSet ().size () == 1;
      assert msg.varSet ().contains (to);

      makeDampedUpdate (fromIdx, toIdx, msg);
    }

    public void sendMessage (FactorGraph mdl, Variable from, Factor to)
    {
//      System.err.println ("...sum-prod message");
      int fromIdx = messages.getIndex (from);
      int toIdx = messages.getIndex (to);

      Factor msg = msgProduct (null, fromIdx, toIdx);
      msg.normalize ();

      assert msg.varSet ().size () == 1;
      assert msg.varSet ().contains (from);

      messages.put (fromIdx, toIdx, msg);
    }

    private void makeDampedUpdate (int fromIdx, int toIdx, Factor msg)
    {
      if (damping < 1.0) {
        // there's damping
        Factor oldMsg = oldMessages.get (fromIdx, toIdx);
//        Factor oldMsg = messages.get (fromIdx, toIdx);
        if (oldMsg != null) {
          AbstractTableFactor oldTbl = (AbstractTableFactor) oldMsg.duplicate ();
          oldTbl.normalize ();
          oldTbl.timesEquals (1 - damping);

          AbstractTableFactor tbl = (AbstractTableFactor) msg;
          tbl.timesEquals (damping);
          tbl.plusEquals (oldTbl);

          msg = tbl;
        }
      }

      messages.put (fromIdx, toIdx, msg);
    }


    // Serialization
    private static final long serialVersionUID = 1;
    private static final int CUURENT_SERIAL_VERSION = 2;

    private void writeObject (ObjectOutputStream out) throws IOException
    {
      out.defaultWriteObject ();
      out.writeInt (CUURENT_SERIAL_VERSION);
      out.writeDouble (damping);
    }

    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
    {
      in.defaultReadObject ();
      int version = in.readInt (); // version
      if (2 <= version) {
        damping = in.readDouble ();
      }
    }
  }

  public static class MaxProductMessageStrategy extends AbstractMessageStrategy implements Serializable {

    public void sendMessage (FactorGraph mdl, Factor from, Variable to)
    {
//      System.err.println ("...max-prod message");
      int fromIdx = messages.getIndex (from);
      int toIdx = messages.getIndex (to);

      Factor product = from.duplicate ();
      msgProduct (product, fromIdx, toIdx);

      Factor msg = product.extractMax (to);
      msg.normalize ();

      assert msg.varSet ().size () == 1;
      assert msg.varSet ().contains (to);

      messages.put (fromIdx, toIdx, msg);
    }

    public void sendMessage (FactorGraph mdl, Variable from, Factor to)
    {
//      System.err.println ("...max-prod message");
      int fromIdx = messages.getIndex (from);
      int toIdx = messages.getIndex (to);

      Factor msg = msgProduct (null, fromIdx, toIdx);
      msg.normalize ();

      assert msg.varSet ().size () == 1;
      assert msg.varSet ().contains (from);

      messages.put (fromIdx, toIdx, msg);
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


  public Factor lookupMarginal (Variable var)
  {
    int idx = mdlCurrent.getIndex (var);
    if ((idx < 0) || (idx > bel.length)) {
      throw new IllegalArgumentException ("Cannot find variable "+var+" in factor graph "+mdlCurrent);
    }
    
    if (bel[idx] == null) {

      Factor marg = messager.msgProduct (null, idx, Integer.MIN_VALUE);

      if (normalizeBeliefs) {
        marg.normalize ();
      }

      assert marg.varSet ().size () == 1
              :"Invalid marginal for var " + var + ": " + marg;
      assert marg.varSet ().contains (var)
              :"Invalid marginal for var " + var + ": " + marg;

      bel[idx] = marg;
    }


    return bel[idx];
  }

  public void dump ()
  {
    messages.dump ();
  }

  public void reportTime ()
  {
    System.err.println ("AbstractBeliefPropagation: Total messages sent = "+totalMessagesSent);
  }

  public void dump (PrintWriter writer)
  {
    messages.dump (writer);
  }

  // }}}

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

  public Factor lookupMarginal (VarSet c)
  {
    if (c.size () == 1) {
      return lookupMarginal (c.get (0));
    } else {
      List factors = mdlCurrent.allFactorsOf (c);
      if (factors.isEmpty ()) {
        throw new UnsupportedOperationException
                ("Cannot compute marginal of " + c + ": Must be either a single variable or a factor in the graph.");
      }

      return lookupMarginal (c, factors);
    }
  }

  private Factor lookupMarginal (VarSet vs, List factors)
  {
    Factor marginal = Factors.multiplyAll (factors);

    for (Iterator fit = factors.iterator (); fit.hasNext ();) {
      Factor factor = (Factor) fit.next ();
      for (java.util.Iterator it = vs.iterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        Factor msg = messages.get (var, factor);
        if (msg != null) {   // if the inferencer was stopped early, there may be no message
          marginal.multiplyBy (msg);
        }
      }
    }

    marginal.normalize ();
    return marginal;
  }

  public double lookupLogJoint (Assignment assn)
  {
    double accum = 0.0;

    // Compute using BP-factorization
    // prod_s (p(x_s))^-(deg(s)-1) * ...
    for (java.util.Iterator it = mdlCurrent.variablesIterator (); it.hasNext ();) {
      Variable var = (Variable) it.next ();
      Factor ptl = lookupMarginal (var);
      int deg = mdlCurrent.getDegree (var);
      if (deg != 1)
      // Note that below works correctly for degree 0!
      {
        accum -= (deg - 1) * ptl.logValue (assn);
      }
    }

    // ... * prod_{c} p(x_C)
    for (java.util.Iterator it = mdlCurrent.varSetIterator (); it.hasNext ();) {
      VarSet varSet = (VarSet) it.next ();
      Factor p12 = lookupMarginal (varSet);
      double logphi = p12.logValue (assn);
      accum += logphi;
    }

    return accum;
  }

}
