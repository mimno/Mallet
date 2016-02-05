/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;

/**
 * Interface for functions that compares extracted values of a field to see
 *  if they match.  These are used by the evaluation metrics (e.g.,
 *  @link{PerDocumentF1Evaluator}) to see if the extraction is correct.
 *
 * Created: Nov 23, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: FieldComparator.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public interface FieldComparator {

  /**
   * Returns true if the given two slot fillers match.
   */
  public boolean matches (String fieldVal1, String fieldVal2);

}
