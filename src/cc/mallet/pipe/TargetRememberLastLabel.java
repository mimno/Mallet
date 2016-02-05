/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept. 
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit). 
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe;

import cc.mallet.types.*;

/**
 * For each position in the target, remember the last non-background
 *  label.  Assumes that the target of piped instances is a LabelSequence.
 *  Replaces the target with a LabelsSequence where row 0 is the original
 *  labels, and row 1 is the last label.
 *
 * @author Charles Sutton
 * @version $Id: TargetRememberLastLabel.java,v 1.1 2007/10/22 21:37:39 mccallum Exp $ 
 */
public class TargetRememberLastLabel extends Pipe {

  private String backgroundLabel;

	private boolean offset;

  public TargetRememberLastLabel () {
    this ("O", true);
  }

	/** offset determines how the memory and base sequences will be
	 * aligned.  If true, they'll be aligned like this:
	 * <pre>
	 *  MEM   O  O  S  S  S  E  L
	 *  BASE  O  S  S  O  E  L  O
	 * </pre>
	 * otherwise, they'll be aligned like this:
	 * <pre>
	 *  MEM   O  S  S  S  E  E  L
	 *  BASE  O  S  S  O  E  L  O
	 * </pre>
	 */
  public TargetRememberLastLabel (String backgroundLabel, boolean offset)
  {
    this.backgroundLabel = backgroundLabel;
		this.offset = offset;
  }

  public Instance pipe(Instance carrier)
  {
    LabelSequence lblseq = (LabelSequence) carrier.getTarget ();
    Labels[] lbls = new Labels [lblseq.size()];
    Label lastLabel = lblseq.getLabelAtPosition(0);

    for (int i = 0; i < lblseq.size(); i++) {
      Label thisLabel = lblseq.getLabelAtPosition (i);
			if (offset)
				lbls [i] = new Labels (new Label[] { thisLabel, lastLabel });
      if (!thisLabel.toString().equals (backgroundLabel))
        lastLabel = thisLabel;
			if (!offset)
				lbls [i] = new Labels (new Label[] { thisLabel, lastLabel });
    }

    carrier.setTarget (new LabelsSequence (lbls));
    return carrier;
  }

}
