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
 * A first implementation of MessageStrategy that assumes that a BP region graph
 *  is being used.
 *
 * Created: May 29, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: FullMessageStrategy.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class FullMessageStrategy extends AbstractMessageStrategy {

  private static final boolean debug = false;
  private static final boolean debugLite = false;

  public FullMessageStrategy ()
  {
  }

  public void sendMessage (RegionEdge edge)
  {
    if (debugLite) {
      System.err.println ("Sending message "+edge);
    }

    Factor product = msgProduct (edge);
    Region from = edge.from;
    Region to = edge.to;

    if (debug)
      System.err.println ("Message "+from+" --> "+to+" after msgProduct: "+product);

    for (Iterator it = edge.factorsToSend.iterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      product.multiplyBy (ptl);
    }

    TableFactor result = (TableFactor) product.marginalize (to.vars);
    result.normalize ();

    if (debug) {
      System.err.println ("Final message "+edge+":"+result);
    }
    newMessages.setMessage (from, to, result);
  }

  /*
  static void multiplyEdgeFactors (RegionEdge edge, DiscretePotential product)
  {
    for (Iterator it = edge.factorsToSend.iterator (); it.hasNext ();) {
      DiscretePotential ptl = (DiscretePotential) it.next ();

      if (debug)
        System.err.println ("Message "+edge+" multiplying by: "+ptl);

      product.multiplyBy (ptl);
    }
  }
  */

  // debugging function
  private boolean willBeNaN (Factor product, Factor otherMsg)
  {
    Factor p2 = product.duplicate ();
    p2.divideBy (otherMsg);
    return p2.isNaN ();
  }

  // debugging function
  private boolean willBeNaN2 (Factor product, Factor otherMsg)
  {
    Factor p2 = product.duplicate ();
    p2.multiplyBy (otherMsg);
    return p2.isNaN ();
  }

  public MessageArray averageMessages (RegionGraph rg, MessageArray a1, MessageArray a2, double inertiaWeight)
  {
    MessageArray arr = new MessageArray (rg);
    for (Iterator it = rg.edgeIterator (); it.hasNext ();) {
      RegionEdge edge = (RegionEdge) it.next ();
      DiscreteFactor msg1 = a1.getMessage (edge.from, edge.to);
      DiscreteFactor msg2 = a2.getMessage (edge.from, edge.to);
      if (msg1 != null) {
        TableFactor averaged = (TableFactor) Factors.average (msg1, msg2, inertiaWeight);
        arr.setMessage (edge.from, edge.to, averaged);
      }
    }

    return arr;
  }

}
