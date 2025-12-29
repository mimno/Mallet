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

import cc.mallet.types.*;

public interface Tokenization extends Sequence //??
{
  /**
   * Returns the document of which this is a tokenization.
   */
	public Object getDocument ();

  public Span getSpan (int i);


  /** Returns a span formed by concatenating the spans from start to end.
   *  In more detail:
   *  <ul>
   *   <li>The start of the new span will be the start index of <tt>getSpan(start)</tt>.
   *   <li>The end of the new span will be the start index of <tt>getSpan(end)</tt>.
   *   <li>Unless <tt>start == end</tt>, the new span will completely include <tt>getSpan(start)</tt>.
   *   <li>The new span will never intersect <tt>getSpan(end)</tt>
   *   <li>If <tt>start == end</tt>, then the new span contains no text.
   *  </ul>
   *
   * @param start The index of the first token in the new span (inclusive).
   *   This is an index of a token, *not* an index into the document.
   * @param end The index of the first token in the new span (exclusive).
   *   This is an index of a token, *not* an index into the document.
   * @return A span into this tokenization's document
   */
  Span subspan (int start, int end);
}
