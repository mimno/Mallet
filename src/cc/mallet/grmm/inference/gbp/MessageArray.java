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
 * Created: May 29, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: MessageArray.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
class MessageArray  {

  private DiscreteFactor[][] messages;

  private MessageArray () {}

  public MessageArray (RegionGraph rg)
  {
    int size = rg.size ();
    messages = new TableFactor[size][size];
    for (Iterator it = rg.iterator (); it.hasNext ();) {
      Region from = (Region) it.next ();
      for (Iterator it2 = from.children.iterator(); it2.hasNext ();) {
        Region to = (Region) it2.next ();
        DiscreteFactor ptl = new LogTableFactor (to.vars);
//        ptl.normalize ();
        messages[from.index][to.index] = ptl;
      }
    }
  }

  public MessageArray (TableFactor[][] messages)
  {
    this.messages = messages;
  }

  DiscreteFactor getMessage (Region from, Region to) {
    return messages[from.index][to.index];
  }

  public void setMessage (Region from, Region to, TableFactor result)
  {
    messages[from.index][to.index] = result;
  }

  /** deep copy of messages */
  public MessageArray duplicate ()
  {
    MessageArray arr = new MessageArray ();
    arr.messages = new TableFactor[messages.length][messages.length];
    for (int i = 0; i < messages.length; i++) {
      for (int j = 0; j < messages[i].length; j++) {
        if (messages[i][j] != null) {
          arr.messages[i][j] = (TableFactor) messages[i][j].duplicate ();
        }
      }
    }
    return arr;
  }

  public int size () { return messages.length; }

  public Factor getMessage (int i, int j)
  {
    return messages[i][j];
  }
}
