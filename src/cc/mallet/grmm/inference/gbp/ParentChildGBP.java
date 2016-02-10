/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;


import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.*;

import cc.mallet.grmm.inference.AbstractInferencer;
import cc.mallet.grmm.types.*;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Timing;

/**
 * Created: May 27, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: ParentChildGBP.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class ParentChildGBP extends AbstractInferencer {

  private static final Logger logger = MalletLogger.getLogger (ParentChildGBP.class.getName());
  private static final boolean debug = false;

  private RegionGraphGenerator regioner;
  private MessageStrategy sender;

  private boolean useInertia = true;
  private double inertiaWeight = 0.5;

  // convergence criteria

  private static final double THRESHOLD = 1e-3;
  private static final int MAX_ITER = 500;

  // current inferencing state

  private MessageArray oldMessages;
  private MessageArray newMessages;
  private RegionGraph rg;
  private FactorGraph mdl;

  private ParentChildGBP ()
  {
  }

  public ParentChildGBP (RegionGraphGenerator regioner)
  {
    this (regioner, new FullMessageStrategy ());
  }

  public ParentChildGBP (RegionGraphGenerator regioner, MessageStrategy sender)
  {
    this.regioner = regioner;
    this.sender = sender;
  }

  public static ParentChildGBP makeBPInferencer ()
  {
    ParentChildGBP inferencer = new ParentChildGBP ();
    inferencer.regioner = new BPRegionGenerator ();
    inferencer.sender = new FullMessageStrategy ();
    return inferencer;
  }

  public static ParentChildGBP makeKikuchiInferencer ()
  {
    ParentChildGBP inferencer = new ParentChildGBP ();
    inferencer.regioner = new Kikuchi4SquareRegionGenerator ();
    inferencer.sender = new FullMessageStrategy ();
    return inferencer;
  }

  // accessors

  public boolean getUseInertia ()
  {
    return useInertia;
  }

  public void setUseInertia (boolean useInertia)
  {
    this.useInertia = useInertia;
  }

  public double getInertiaWeight ()
  {
    return inertiaWeight;
  }

  public void setInertiaWeight (double inertiaWeight)
  {
    this.inertiaWeight = inertiaWeight;
  }
  // inferencer interface

  public Factor lookupMarginal (Variable variable)
  {
    Region region = rg.findContainingRegion (variable);
    if (region == null)
      throw new IllegalArgumentException ("Could not find region containing variable "+variable+" in region graph "+rg);

    Factor belief = computeBelief (region);
    Factor varBelief = belief.marginalize (variable);
    return varBelief;
  }


  public Factor lookupMarginal (VarSet varSet)
  {
    Region region = rg.findContainingRegion (varSet);
    if (region == null)
      throw new IllegalArgumentException ("Could not find region containing clique "+varSet +" in region graph "+rg);

    Factor belief = computeBelief (region);
    Factor cliqueBelief = belief.marginalize (varSet);
    return cliqueBelief;
  }


  private Factor computeBelief (Region region)
  {
    return computeBelief (region, newMessages);
  }

  static Factor computeBelief (Region region, MessageArray messages)
  {
    DiscreteFactor result = new LogTableFactor(region.vars);

    for (Iterator it = region.factors.iterator(); it.hasNext();) {
      Factor factor = (Factor) it.next();
      result.multiplyBy(factor);
    }

    for (Iterator it = region.parents.iterator(); it.hasNext();) {
      Region parent = (Region) it.next();
      Factor msg = messages.getMessage(parent, region);
      result.multiplyBy(msg);
    }

    for (Iterator it = region.descendants.iterator(); it.hasNext();) {
      Region child = (Region) it.next();
      for (Iterator it2 = child.parents.iterator(); it2.hasNext();) {
        Region uncle = (Region) it2.next();
        if (uncle != region && !region.descendants.contains(uncle)) {
          result.multiplyBy(messages.getMessage(uncle, child));
        }
      }
    }

    result.normalize();

    return result;
  }

  public double lookupLogJoint (Assignment assn)
  {
    double factorProduct = mdl.logValue (assn);
//    value += computeFreeEnergy (rg);
    double F = computeFreeEnergy (rg);

    double value = factorProduct + F;

    if (debug)
      System.err.println ("GBP factor product:"+factorProduct+" + free energy: "+F+" = value:"+value);

    return value;
  }

  private double computeFreeEnergy (RegionGraph rg)
  {
    double avgEnergy = 0;
    double entropy = 0;
    for (Iterator it = rg.iterator (); it.hasNext();) {
      Region region = (Region) it.next();
      Factor belief = computeBelief(region);
      double thisEntropy = belief.entropy();

      if (debug)
        System.err.println("Region " + region + " c:" + region.countingNumber + "  entropy:" + thisEntropy);

      entropy += region.countingNumber * thisEntropy;

      DiscreteFactor product = new LogTableFactor(belief.varSet());
      for (Iterator ptlIt = region.factors.iterator(); ptlIt.hasNext();) {
        Factor ptl = (Factor) ptlIt.next();
        product.multiplyBy(ptl);
      }

      double thisAvgEnergy = 0;
      for (AssignmentIterator assnIt = belief.assignmentIterator(); assnIt.hasNext();) {
        Assignment assn = assnIt.assignment();

        // Note: Do not use assnIt here before fixing variable ordering issues.
        double thisEnergy = -product.logValue(assn);
//        double thisEnergy = product.phi (assnIt);
        double thisBel = belief.value(assn);
        thisAvgEnergy += thisBel * thisEnergy;
        assnIt.advance();
      }

      if (debug) {
        System.err.println("Region " + region + " c:" + region.countingNumber + " avgEnergy: " + thisAvgEnergy);
/*        DiscretePotential b2 = belief.duplicate ();
        b2.delogify ();
        System.err.println ("BELIEF:"+b2);
        System.err.println ("ENERGY:"+product);
        */
      }
      avgEnergy += region.countingNumber * thisAvgEnergy;

    }

    if (debug)
      System.err.println ("GBP computeFreeEnergy: avgEnergy:"+avgEnergy+"  entropy:"+entropy+"  free energy:"+(avgEnergy-entropy));

//    return avgEnergy + entropy;
    return avgEnergy - entropy;
  }

  public void computeMarginals (FactorGraph mdl)
  {
    Timing timing = new Timing ();

    this.mdl = mdl;
    rg = regioner.constructRegionGraph (mdl);
    RegionEdge[] pairs = chooseMessageSendingOrder ();

    newMessages = new MessageArray (rg);

    timing.tick ("GBP Region Graph construction");
    
    int iter = 0;
    do {

      oldMessages = newMessages;
      newMessages = oldMessages.duplicate ();
      sender.setMessageArray (oldMessages, newMessages);

      for (int i = 0; i < pairs.length; i++) {
        RegionEdge edge = pairs[i];
        sender.sendMessage (edge);
      }

      if (logger.isLoggable (Level.FINER)) {
        timing.tick ("GBP iteration "+iter);
      }

      iter++;

      if (useInertia)
        newMessages = sender.averageMessages (rg, oldMessages, newMessages, inertiaWeight);

    } while (!hasConverged () && (iter < MAX_ITER));

    logger.info ("GBP: Used "+iter+" iterations.");
    if (iter >= MAX_ITER) {
      logger.warning ("***WARNING: GBP not converged!");
    }
  }

  private RegionEdge[] chooseMessageSendingOrder ()
  {
    List l = new ArrayList ();
    for (Iterator it = rg.edgeIterator (); it.hasNext();) {
      RegionEdge edge = (RegionEdge) it.next ();
      l.add (edge);
    }

    Collections.sort (l, new Comparator () {
      public int compare (Object o1, Object o2)
      {
        RegionEdge e1 = (RegionEdge) o1;
        RegionEdge e2 = (RegionEdge) o2;
        int l1 = e1.to.vars.size();
        int l2 = e2.to.vars.size();
        return Double.compare (l1, l2);
      };
    });

    return (RegionEdge[]) l.toArray (new RegionEdge [l.size()]);
  }

  private boolean hasConverged ()
  {
    for (Iterator it = rg.edgeIterator (); it.hasNext();) {
      RegionEdge edge = (RegionEdge) it.next ();
      Factor oldMsg = oldMessages.getMessage (edge.from, edge.to);
      Factor newMsg = newMessages.getMessage (edge.from, edge.to);
      if (oldMsg == null) {
        assert newMsg == null;
      } else {
        if (!oldMsg.almostEquals (newMsg, THRESHOLD)) {
          /*
         //xxx debug
          if (sender instanceof SparseMessageSender)
            System.out.println ("NOT CONVERGED:\n"+newMsg+"\n.......");
          */
          return false;
        }
      }
    }

    return true;
  }

  public void dump ()
  {
    for (Iterator it = rg.edgeIterator (); it.hasNext();) {
      RegionEdge edge = (RegionEdge) it.next ();
      Factor newMsg = newMessages.getMessage (edge.from, edge.to);
      System.out.println ("Message: "+edge.from+" --> "+edge.to+" "+newMsg);
    }
  }

}
