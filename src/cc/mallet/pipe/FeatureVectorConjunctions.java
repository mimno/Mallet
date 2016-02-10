/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Include in the FeatureVector conjunctions of all its features.
	 Only works with binary FeatureVectors.
	 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;

// This class does not insist on getting its own Alphabet because it can rely on getting
// it from the FeatureSequence input.
/**
	 Include in the FeatureVector conjunctions of all its features.
	 Only works with binary FeatureVectors.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class FeatureVectorConjunctions extends Pipe implements Serializable
{
	public FeatureVectorConjunctions ()
	{
		super();
	}
	
	
	public Instance pipe (Instance carrier)
	{
		FeatureVector fv = (FeatureVector) carrier.getData();
		carrier.setData(new FeatureVector (fv, fv.getAlphabet(), null, null));
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
	}
}
