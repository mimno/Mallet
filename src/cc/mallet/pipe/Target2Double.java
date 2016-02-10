/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe;

import cc.mallet.types.Instance;

/** Convert object in the target field into a floating-point numeric type
 *   @author David Mimno
 */

public class Target2Double extends Pipe {

	public Instance pipe (Instance carrier) {
		if (carrier.getTarget() != null) {
			if (! (carrier.getTarget() instanceof String)) {
				throw new IllegalArgumentException ("Target must be a string for conversion to Double");
			}
			carrier.setTarget( new Double((String) carrier.getTarget()) );
		}
		return carrier;
	}

}
