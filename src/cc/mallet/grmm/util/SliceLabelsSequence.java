/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.util;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;


/**
 *  *
 * Created: Fri Jan 02 23:27:04 2004
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version 1.0
 */
public class SliceLabelsSequence extends Pipe {

	int slice;
	
	public SliceLabelsSequence(int k) {
		super (null, new LabelAlphabet ());
		slice = k;
	} // SliceLabelsSequence constructor

	public Instance pipe (Instance carrier) {
		LabelsSequence lbls = (LabelsSequence) carrier.getTarget ();
    LabelAlphabet dict = (LabelAlphabet) getTargetAlphabet ();
    if (dict == null) {
      throw new IllegalArgumentException ("dict is null");
    }

    LabelSequence ls = sliceLabelsSequence (lbls, dict, slice);
    carrier.setTarget (ls);
		return carrier;
	}

  public static LabelSequence sliceLabelsSequence (LabelsSequence lbls, int slice)
  {
    return sliceLabelsSequence (lbls, lbls.getLabels (0).get (0).getLabelAlphabet (), slice);
  }

  public static LabelSequence sliceLabelsSequence (LabelsSequence lbls, LabelAlphabet dict, int slice)
  {
    Label[] labels = new Label [lbls.size()];

    for (int t = 0; t < lbls.size(); t++) {
      Label l = lbls.getLabels (t).get (slice);
      labels [t] = dict.lookupLabel (l.getEntry ());
    }
    LabelSequence ls = new LabelSequence (labels);
    return ls;
  }
} // SliceLabelsSequence

