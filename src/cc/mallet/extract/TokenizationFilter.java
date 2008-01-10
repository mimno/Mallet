/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;

import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;

/**
 * Created: Nov 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TokenizationFilter.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public interface TokenizationFilter {

  /**
   * Converts a the sequence of labels into a set of labeled spans.  Essentially, this converts the
   *  output of sequence labeling into an extraction output.
   * @param dict
   * @param document
   * @param backgroundTag
   * @param input
   * @param seq
   * @return
   */
  LabeledSpans constructLabeledSpans (LabelAlphabet dict, Object document, Label backgroundTag,
                                             Tokenization input, Sequence seq);
}
