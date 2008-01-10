/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;

/**
 * Interface for functions that are used to clean up field values after
 *  extraction has been performed.
 *
 * Created: Nov 25, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: FieldCleaner.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public interface FieldCleaner {

  /**
   * Returns a post-processed version of a field.
   * @param rawFieldValue
   * @return A processed string
   */
  String cleanFieldValue (String rawFieldValue);

}
