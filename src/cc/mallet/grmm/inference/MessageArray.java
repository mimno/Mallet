/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;

import gnu.trove.iterator.TIntObjectIterator;

import java.io.PrintWriter;
import java.io.OutputStreamWriter;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.grmm.types.LogTableFactor;
import cc.mallet.grmm.types.Variable;
import cc.mallet.grmm.util.MIntInt2ObjectMap;

/**
 * Efficiently manages a array of messages in a factor graph from
 *  variables to factors and vice versa.
 *
 * Created: Feb 1, 2006
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: MessageArray.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class MessageArray {

  private FactorGraph fg;

  private MIntInt2ObjectMap messages;    // messages from factor --> variable
  private int numV;
  private int numF;

  private boolean inLogSpace;

  public MessageArray (FactorGraph fg)
  {
    this.fg = fg;
    numV = fg.numVariables ();
    numF = fg.factors ().size();
    messages = new MIntInt2ObjectMap (numV + numV);
    inLogSpace = (fg.getFactor (0) instanceof LogTableFactor);
  }

  public boolean isInLogSpace ()
  {
    return inLogSpace;
  }

  public Factor get (Object from, Object to)
  {
    if (from instanceof Factor && to instanceof Variable) {
      return get ((Factor) from, (Variable) to);
    } else if (from instanceof Variable && to instanceof Factor) {
      return get ((Variable) from, (Factor) to);
    } else {
      throw new IllegalArgumentException ();
    }
  }

  public Factor get (Variable from, Factor to)
  {
    int fromIdx = getIndex (from);
    int toIdx = getIndex (to);
    return get (toIdx, fromIdx);
  }

  public Factor get (Factor from, Variable to)
  {
    int fromIdx = getIndex (from);
    int toIdx = getIndex (to);
    return get (toIdx, fromIdx);
  }

  Factor get (int toIdx, int fromIdx) {
    return (Factor) messages.get (toIdx, fromIdx);
  }


  public void put (Factor from, Variable to, Factor msg)
  {
    int fromIdx = getIndex (from);
    int toIdx = getIndex (to);
    messages.put (toIdx, fromIdx, msg);
  }

  public void put (Variable from, Factor to, Factor msg)
  {
    int fromIdx = getIndex (from);
    int toIdx = getIndex (to);
    messages.put (toIdx, fromIdx, msg);
  }

  // more dangerous, but for efficiency
  public void put (int fromIdx, int toIdx, Factor msg)
  {
    messages.put (toIdx, fromIdx, msg);
  }

  public Iterator iterator ()
  {
    return new Iterator ();
  }

  public ToMsgsIterator toMessagesIterator (int toIdx)
  {
    return new ToMsgsIterator (messages, toIdx);
  }

  public MessageArray duplicate ()
  {
    MessageArray dup = new MessageArray (fg);
    dup.messages = deepCopy (messages);
    return dup;
  }


  public MIntInt2ObjectMap deepCopy (MIntInt2ObjectMap msgs)
  {
    MIntInt2ObjectMap copy = new MIntInt2ObjectMap (numV + numF);
    int[] keys1 = msgs.keys1 ();
    for (int i = 0; i < keys1.length; i++) {
      int k1 = keys1[i];
      ToMsgsIterator msgIt = new ToMsgsIterator (msgs, k1);
      while (msgIt.hasNext ()) {
        Factor msg = msgIt.next ();
        int from = msgIt.currentFromIdx ();
        copy.put (k1, from, msg.duplicate ());
      }
    }
    return copy;
  }

  public int getIndex (Factor from)
  {
    return -(fg.getIndex (from) + 1);
  }

  public int getIndex (Variable to)
  {
    return fg.getIndex (to);
  }

  public Object idx2obj (int idx)
  {
    if (idx >= 0) {
      return fg.get (idx);
    } else {
      return fg.getFactor (-idx - 1);
    }

  }

  public void dump ()
  {
    dump (new PrintWriter (new OutputStreamWriter (System.out), true));
  }

  public void dump (PrintWriter out)
  {
    for (MessageArray.Iterator it = iterator (); it.hasNext ();) {
      Factor msg = (Factor) it.next ();
      Object from = it.from ();
      Object to = it.to ();
      out.println ("Message from " + from + " to " + to);
      out.println (msg.dumpToString ());
    }
  }

  public final class Iterator implements java.util.Iterator
  {
    int idx1 = 0;
    int idx2 = -1;

    int[] keys1;
    int[] keys2;

    public Iterator ()
    {
      keys1 = messages.keys1 ();
      if (keys1.length > 0) {
        keys2 = messages.keys2 (keys1[idx1]);
      } else {
        keys2 = new int [0];
      }
    }

    private void increment () {
      idx2++;
      if (idx2 >= keys2.length) {
        idx2 = 0;
        idx1++;
        keys2 = messages.keys2 (keys1[idx1]);
      }
    }

    public boolean hasNext ()
    {
      return (idx1+1 < keys1.length) || (idx2+1 < keys2.length);
    }

    public Object next ()
    {
      increment ();
      return messages.get (keys1[idx1], keys2[idx2]);
    }

    public void remove ()
    {
      throw new UnsupportedOperationException ();
    }

    public Object from ()
    {
      return idx2obj (keys2[idx2]);
    }

    public Object to ()
    {
      return idx2obj (keys1[idx1]);
    }
  }

  final public static class ToMsgsIterator
  {
    private TIntObjectIterator subIt;
    private int toIdx = -1;

    private ToMsgsIterator (MIntInt2ObjectMap msgs, int toIdx)
    {
      this.toIdx = toIdx;
      subIt = msgs.curry (toIdx);
    }

    public boolean hasNext () { return subIt.hasNext (); }
    public Factor next () { subIt.advance (); return currentMessage (); }

    int currentFromIdx () { return subIt.key (); }
    public Factor currentMessage () { return (Factor) subIt.value (); }

    public int currentToIdx ()
    {
      return toIdx;
    }
  }

}
