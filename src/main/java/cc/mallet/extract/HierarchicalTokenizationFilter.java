/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.util.regex.Pattern;
import java.util.*;

import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;

/**
 * Tokenization filter that will create nested spans based on a hierarchical labeling of the data.
 *   The labels should be of the form <tt>LBL1[|LBLk]*</tt>.  For example,
 * <pre>
 *   A   A|B   A|B|C   A|B|C  A|B  A   A
 *   w1  w2    w3      w4     w5   w6  w7
 * </pre>
 * will result in LabeledSpans like
 * <tt>&lt;A>w1 &lt;B>w2 &lt;C>w3 w4&lt;/C> w5&lt;/B> w6 w7&lt;/A></tt>
 *
 * Also, labels of the form <tt>&lt;B-field></tt> will force a new instance of the field to begin,
 *  even if it is already active.  And prefixes of <tt>I-</tt> are ignored so you can use BIO labeling.
 *
 * Created: Nov 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: HierarchicalTokenizationFilter.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class HierarchicalTokenizationFilter implements TokenizationFilter {

  Pattern ignorePattern = null;

  public HierarchicalTokenizationFilter ()
  {
  }


  public HierarchicalTokenizationFilter (Pattern ignorePattern)
  {
    this.ignorePattern = ignorePattern;
  }


  public LabeledSpans constructLabeledSpans (LabelAlphabet dict, Object document, Label backgroundTag,
                                                    Tokenization input, Sequence seq)
  {
    LabeledSpans labeled = new LabeledSpans (document);
     addSpansFromTags (labeled, input, seq, dict, backgroundTag);
     return labeled;
   }


  private static class TagStart {
    int start;
    Label label;


    public TagStart (int start, Label label)
    {
      this.start = start;
      this.label = label;
    }
  }
   private void addSpansFromTags (LabeledSpans labeled, Tokenization input, Sequence tags, LabelAlphabet dict,
                                  Label backgroundTag)
    {
      int i = 0;
      LinkedList openTags = new LinkedList();
      String[] lastTagSplit = new String [0];
      while (i < tags.size()) {
        Label thisTag = dict.lookupLabel (tags.get(i).toString());
        String[] thisTagSplit = splitTag (thisTag);
        int numToClose = compareSplitTags (thisTagSplit, lastTagSplit);

        // close all that need to be closed
        while (numToClose > 0) {
          TagStart tagStart = (TagStart) openTags.removeLast ();
          addLabeledSpan (labeled, input, tagStart, i, backgroundTag);
          numToClose--;
        }

        // open all that need to be opened
        for (int tidx = openTags.size (); tidx < thisTagSplit.length; tidx++) {
          openTags.add (new TagStart (i, dict.lookupLabel (thisTagSplit [tidx])));
        }

        lastTagSplit = thisTagSplit;
        i++;
      }

      // Close all remaining tags
      while (!openTags.isEmpty ()) {
        TagStart tagStart = (TagStart) openTags.removeLast ();
        addLabeledSpan (labeled, input, tagStart, i, backgroundTag);
      }
    }


  private void addLabeledSpan (LabeledSpans labeled, Tokenization input,
                               TagStart tagStart, int end, Label backgroundTag)
  {
    Span span = input.subspan (tagStart.start, end);
    Label splitTag = tagStart.label;
    labeled.add (new LabeledSpan (span, splitTag, splitTag == backgroundTag));
  }


  private int compareSplitTags (String[] thisTagSplit, String[] lastTagSplit)
  {
    int idx = lastTagSplit.length - 1;
    for (; idx >= 0; idx--) {
      if (idx >= thisTagSplit.length) continue;
      String thisTag = thisTagSplit [idx];
      if (isBeginName (thisTag)) continue;
      if (matches (lastTagSplit [idx], thisTag)) break;
    }

    int numToClose = lastTagSplit.length - idx - 1;

    // sanity check
    while (idx >= 0) {
      if (!matches (thisTagSplit[idx], lastTagSplit [idx])) {
        throw new IllegalArgumentException ("Tags don't match.");
      }
      idx--;
    }

    return numToClose;
  }


  private boolean matches (String str1, String str2)
  {
    return trim (str1).equals (trim (str2));
  }


  private String trim (String name)
  {
    if (isBeginName (name) || isInsideName (name))
      return (name.substring (2));
    else return name;
  }

  private String[] splitTag (Label tag) {
    String name = tag.toString ();
    List split1 = new ArrayList (Arrays.asList (name.split ("\\|")));
    Iterator it = split1.iterator ();
    while (it.hasNext()) {
      String str = (String) it.next();
      if (ignorePattern != null && ignorePattern.matcher (str).matches ())
        it.remove ();
    }
    return (String[]) split1.toArray (new String[0]);
  }


  private boolean isBeginName (String name) {
    return name.startsWith ("B-");
  }

  private boolean isInsideName (String name) {
    return name.startsWith ("I-");
  }

}
