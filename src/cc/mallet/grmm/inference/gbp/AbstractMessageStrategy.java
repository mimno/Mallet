/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference.gbp;


import java.util.Iterator;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.LogTableFactor;
import cc.mallet.grmm.types.TableFactor;

/**
 * Created: May 29, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: AbstractMessageStrategy.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public abstract class AbstractMessageStrategy implements MessageStrategy {

  protected MessageArray oldMessages;
  protected MessageArray newMessages;

  public void setMessageArray (MessageArray oldMessages, MessageArray newMessages)
  {
    this.oldMessages = oldMessages;
    this.newMessages = newMessages;
  }

  public MessageArray getOldMessages ()
  {
    return oldMessages;
  }

  public MessageArray getNewMessages ()
  {
    return newMessages;
  }

  Factor msgProduct (RegionEdge edge)
  {
    Factor product = new LogTableFactor (edge.from.vars);

    for (Iterator it = edge.neighboringParents.iterator (); it.hasNext ();) {
      RegionEdge otherEdge = (RegionEdge) it.next ();
      Factor otherMsg = oldMessages.getMessage (otherEdge.from, otherEdge.to);

      product.multiplyBy (otherMsg);
    }

    for (Iterator it = edge.loopingMessages.iterator (); it.hasNext ();) {
      RegionEdge otherEdge = (RegionEdge) it.next ();
      Factor otherMsg = newMessages.getMessage (otherEdge.from, otherEdge.to);
      product.divideBy (otherMsg);
    }

    return product;
  }
}
