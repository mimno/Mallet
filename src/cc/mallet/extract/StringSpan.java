/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.extract;


import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import cc.mallet.types.Token;

/** A sub-section of a linear string. */

public class StringSpan extends Token implements Span
{
  private CharSequence document;  // The larger string of which this is a span.
  private int start, end;

  public StringSpan (CharSequence doc, int start, int end)
  {
    super (constructTokenText (doc, start, end));
    this.document = doc;
    this.start = start;
    this.end = end;
  }

  public Span intersection (Span r)
  {
    StringSpan other = (StringSpan) r;
    int newStart = Math.max (start, other.start);
    int newEnd = Math.min (end, other.end);
    return new StringSpan (document, newStart, newEnd);
  }

  private static String constructTokenText (CharSequence doc, int start, int end)
  {
    CharSequence subseq = doc.subSequence(start,end);
    return subseq.toString();
  }

  public Object getDocument ()
  {
    return document;
  }

  public boolean intersects (Span r)
  {
    if (!(r instanceof StringSpan))
      return false;
    StringSpan sr = (StringSpan)r;
    return (sr.document == this.document && !(sr.end < this.start || sr.start > this.end));
  }


  public boolean isSubspan (Span r)
  {
    return (r.getDocument() == this.document &&
            (this.start <= r.getStartIdx ()) && (r.getEndIdx () <= this.end));
  }

  public int getStartIdx () { return start; }

  public int getEndIdx () { return end; }

  public String toString() {
    return super.toString() + "  span["+start+".."+end+"]";
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
