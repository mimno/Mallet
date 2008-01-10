/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types;

import cc.mallet.util.Randoms;

/**
 * $Id: DiscreteFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public interface DiscreteFactor extends Factor {
  
  int sampleLocation (Randoms r);

  double value (int index);

  int numLocations ();

  double valueAtLocation (int loc);

  int indexAtLocation (int loc);

  double[] toValueArray ();

  int singleIndex (int[] smallDims);
}
