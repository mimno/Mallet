/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.ListIterator;

import cc.mallet.types.Label;

/**
 * Created: Oct 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Field.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class Field {

  private Label name;
  private List values = new ArrayList ();
  private List allSpans = new ArrayList ();


  public Field (LabeledSpan span)
  {
    name = span.getLabel ();
    addFiller (span);
  }


  public Label getName ()
  {
    return name;
  }


  public int numValues () { return values.size (); }

  public String value (int i) { return (String) values.get (i); }
  public LabeledSpan span (int i) { return (LabeledSpan) allSpans.get (i); }

  public void addFiller (LabeledSpan span) {
    if (name != span.getLabel ())
      throw new IllegalArgumentException ("Attempt to fill slot "+name+" with a span of type "+span.getLabel ());

    values.add (span.getText ());
    allSpans.add (span);
  }

  void cleanField (FieldCleaner cleaner) {
    //??? Should I prevent the same cleaner from running twice?
    ListIterator it = values.listIterator ();
    while (it.hasNext()) {
      String rawValue = (String) it.next ();
      it.remove ();
      it.add (cleaner.cleanFieldValue (rawValue));
    }
  }

  /**
   * Returns true if <tt>filler</tt> is an exact match to one of the values
   *  of this field.
   */
  public boolean isValue (String filler)
  {
    return values.contains (filler);
  }

  public boolean isValue (String filler, FieldComparator comper)
  {
    for (Iterator it = values.iterator (); it.hasNext ();) {
      String s = (String) it.next ();
      if (comper.matches (filler, s))
        return true;
    }
    return false;
  }

  public String toString () {
    StringBuffer buf = new StringBuffer ();
    buf.append ("FIELD NAME: ");
    buf.append (name);
    buf.append ("\n");
    for (Iterator it = values.iterator (); it.hasNext ();) {
      String s = (String) it.next ();
      buf.append ("FILLER:");
      buf.append (s);
      buf.append ("\n");
    }
    return buf.toString ();
  }


}
