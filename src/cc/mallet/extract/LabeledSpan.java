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
import java.io.Serializable;

import cc.mallet.types.Label;


/**
 * Created: Oct 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: LabeledSpan.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
//xxx Maybe this is the same thing as a field??
public class LabeledSpan implements Span, Serializable {

  private Span span;
  private Label label;
  private boolean isBackground;
  private double confidence;


  public LabeledSpan (Span span, Label label, boolean isBackground) {
    this (span, label, isBackground, 1.0);
  }

  public LabeledSpan (Span span, Label label, boolean isBackground, double confidence) {
    this.span = span;
    this.label = label;
    this.isBackground = isBackground;
    this.confidence = confidence;
  }


  public Span getSpan () { return span; }

  public Label getLabel () { return label; }


  public String getText ()
  {
    return span.getText ();
  }

  public Object getDocument ()
  {
    return span.getDocument ();
  }

  public double getConfidence ()
  {
    return confidence;
  }

  void setConfidence (double c)
  {
    this.confidence = c;
  }

  public boolean intersects (Span r)
  {
    return span.intersects (r);
  }


  public boolean isSubspan (Span r)
  {
    return span.isSubspan (r);
  }

  public Span intersection (Span r)
  {
    LabeledSpan other = (LabeledSpan) r;
    Span newSpan = getSpan ().intersection (other.getSpan ());
    return new LabeledSpan (newSpan, label, isBackground, confidence);
  }

  public int getEndIdx ()
  {
    return span.getEndIdx ();
  }


  public int getStartIdx ()
  {
    return span.getStartIdx ();
  }


  public boolean isBackground ()
  {
    return isBackground;
  }

  public String toString ()
  {
    return label.toString ()+" [span "+getStartIdx ()+".."+getEndIdx ()+" confidence="+confidence+"]";
  }
  
	// Serialization garbage

	private static final long serialVersionUID = 1L;

	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		out.writeInt(CURRENT_SERIAL_VERSION);
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		in.defaultReadObject();
		in.readInt(); // read version
	}
}
