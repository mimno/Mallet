/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;


import java.util.Iterator;

import cc.mallet.grmm.types.*;

/**
 * Created: Jun 1, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: SparseMessageSender.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class SparseMessageSender extends AbstractMessageStrategy {

  private double epsilon;

  public SparseMessageSender (double epsilon)
  {
    this.epsilon = epsilon;
  }

  public void sendMessage (RegionEdge edge)
  {
    Factor product = msgProduct (edge);
    for (Iterator it = edge.factorsToSend.iterator (); it.hasNext ();) {
       Factor ptl = (Factor) it.next ();
       product.multiplyBy (ptl);
     }

    TableFactor result = (TableFactor) product.marginalize (edge.to.vars);
    result.normalize ();

    TableFactor pruned;
    if (shouldPruneMessage (edge, result)) {
//    if (edge.to.vars.size() > 1) {
      pruned = Factors.retainMass (result, epsilon);
      pruned.normalize();
//      System.err.println ("Potential pruning.\nPRE:"+result+"\nPOST:"+pruned);
    } else {
      // Only prune messages to leaves
      pruned = result;
//      System.err.println ("Message for edge "+edge+" not pruned.");
    }


    newMessages.setMessage (edge.from, edge.to, pruned);
  }

  public MessageArray averageMessages (RegionGraph rg, MessageArray a1, MessageArray a2, double inertiaWeight)
  {
    MessageArray arr = new MessageArray (rg);
    for (Iterator it = rg.edgeIterator (); it.hasNext ();) {
      RegionEdge edge = (RegionEdge) it.next ();
      Factor msg1 = a1.getMessage (edge.from, edge.to);
      Factor msg2 = a2.getMessage (edge.from, edge.to);
      if (msg1 != null) {
        TableFactor averaged = (TableFactor) Factors.average (msg1, msg2, inertiaWeight);
        TableFactor pruned;
        if (shouldPruneMessage (edge, averaged)) {
          pruned = Factors.retainMass (averaged, epsilon);
        } else {
          pruned = averaged;
        }

        arr.setMessage (edge.from, edge.to, pruned);
      }
    }

    // compute amount of sparsity
    int locs = 0; int idxs = 0;
    for (Iterator it = rg.edgeIterator (); it.hasNext ();) {
      RegionEdge edge = (RegionEdge) it.next ();
      DiscreteFactor msg = arr.getMessage (edge.from, edge.to);
      locs += msg.numLocations ();
      idxs += new HashVarSet (msg.varSet ()).weight ();
    }
    System.out.println ("Sparsity quotient = "+locs+" of "+idxs);

    return arr;
  }

  private boolean shouldPruneMessage (RegionEdge edge, Factor msg)
  {
    return edge.to.children.isEmpty ();
  }

}