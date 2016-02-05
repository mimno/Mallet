/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelsSequence;

/**
 * $Id: LabelsSequence2Assignment.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class LabelsSequence2Assignment extends Pipe {

  public Instance pipe (Instance carrier)
  {
    LabelsSequence lbls = (LabelsSequence) carrier.getTarget ();
    carrier.setTarget (new LabelsAssignment (lbls));
    return carrier;
  }
}
