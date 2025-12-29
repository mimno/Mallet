/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.io.Serializable;

import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;
/**
 * Created: Nov 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: DefaultTokenizationFilter.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class DefaultTokenizationFilter implements TokenizationFilter, Serializable {

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
     int i = 0;
     int docidx = 0;
     while (i < tags.size()) {
       Label thisTag = dict.lookupLabel (tags.get(i).toString());
       int startTokenIdx = i;
       while (i < tags.size()) {
         Label nextTag = dict.lookupLabel (tags.get(i).toString ());
         if (thisTag != nextTag) break;
         i++;
       }
       int endTokenIdx = i;
       Span span = input.subspan(startTokenIdx, endTokenIdx);
       addBackgroundIfNecessary (labeled, (StringSpan) span, docidx, backgroundTag);
       docidx = ((StringSpan) span).getEndIdx ();
       labeled.add (new LabeledSpan (span, thisTag, thisTag == backgroundTag));
     }
   }

   private void addBackgroundIfNecessary (LabeledSpans labeled, StringSpan span, int docidx, Label background)
   {
     int nextIdx = span.getStartIdx ();
     if (docidx < nextIdx) {
       Span newSpan = new StringSpan ((CharSequence) span.getDocument (), docidx, nextIdx);
       labeled.add (new LabeledSpan (newSpan, background, true));
     }
   }

}
