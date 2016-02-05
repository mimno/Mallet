/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import cc.mallet.types.ArrayListSequence;
import cc.mallet.types.Label;

/**
 * Created: Oct 31, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: LabeledSpans.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class LabeledSpans extends ArrayListSequence {

   private Object document;


  public LabeledSpans (Object document)
  {
    this.document = document;
  }

  public Object getDocument ()
  {
    return document;
  }

  public Label getLabel (int i)
  {
    LabeledSpan span = (LabeledSpan) get (i);
    return span.getLabel ();
  }

  public Span getSpan (int i)
  {
    return (Span) get (i);
  }

  public LabeledSpan getLabeledSpan (int i)
  {
    return (LabeledSpan) get (i);
  }

  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
  }

}
