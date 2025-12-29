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

import com.google.errorprone.annotations.Var;

import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;

/**
 * Created: Nov 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: BIOTokenizationFilter.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class BIOTokenizationFilter implements TokenizationFilter, Serializable {

  public LabeledSpans constructLabeledSpans (LabelAlphabet dict, Object document, Label backgroundTag,
                                             Tokenization input, Sequence seq)
  {
    LabeledSpans labeled = new LabeledSpans (document);
    addSpansFromTags (labeled, input, seq, dict, backgroundTag);
    return labeled;
  }


  private void addSpansFromTags (LabeledSpans labeled, Tokenization input, Sequence tags, LabelAlphabet dict,
                                 Label backgroundTag)
  {
    @Var
    int i = 0;
    @Var
    int docidx = 0;
    while (i < tags.size ()) {
      @Var
      Label thisTag = dict.lookupLabel (tags.get (i).toString ());
      int startTokenIdx = i;
      while (++i < tags.size ()) {
        Label nextTag = dict.lookupLabel (tags.get (i).toString ());
        if (isBeginTag (nextTag) || !tagsMatch (thisTag, nextTag)) break;
      }
      int endTokenIdx = i;
      Span span = createSpan (input, startTokenIdx, endTokenIdx);
      addBackgroundIfNecessary (labeled, (StringSpan) span, docidx, backgroundTag);
      docidx = ((StringSpan) span).getEndIdx ();

      if (isBeginTag (thisTag) || isInsideTag (thisTag)) {
        thisTag = trimTag (dict, thisTag);
      }
      labeled.add (new LabeledSpan (span, thisTag, thisTag == backgroundTag));
    }
  }

  protected Span createSpan (Tokenization input, int startTokenIdx, int endTokenIdx)
  {
    return input.subspan (startTokenIdx, endTokenIdx);
  }


  private Label trimTag (LabelAlphabet dict, Label tag)
  {
    String name = (String) tag.getEntry ();
    return dict.lookupLabel (name.substring (2));
  }


  private boolean tagsMatch (Label tag1, Label tag2)
  {
    @Var
    String name1 = (String) tag1.getEntry ();
    @Var
    String name2 = (String) tag2.getEntry ();

    if (isBeginTag (tag1) || isInsideTag (tag1)) {
      name1 = name1.substring (2);
    }
    if (isInsideTag (tag2)) {
      name2 = name2.substring (2);
    }

    return name1.equals (name2);
  }


  private boolean isBeginTag (Label lbl)
  {
    String name = (String) lbl.getEntry ();
    return name.startsWith ("B-");
  }

  private boolean isInsideTag (Label lbl)
  {
    String name = (String) lbl.getEntry ();
    return name.startsWith ("I-");
  }

  private void addBackgroundIfNecessary (LabeledSpans labeled, StringSpan span, int docidx, Label background)
  {
    int nextIdx = span.getStartIdx ();
    if (docidx < nextIdx) {
      Span newSpan = new StringSpan ((CharSequence) span.getDocument (), docidx, nextIdx);
      labeled.add (new LabeledSpan (newSpan, background, true));
    }
  }

  // Serialization garbage

  private static final long serialVersionUID = -8726127297313150023L;
  private static final int CURRENT_SERIAL_VERSION = 1;
  
  private void writeObject (ObjectOutputStream out) throws IOException
  {
	  out.defaultWriteObject ();
	  out.writeInt (CURRENT_SERIAL_VERSION);
  }
  
  
  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
	  in.defaultReadObject ();
	  in.readInt (); // read version
  }

}
