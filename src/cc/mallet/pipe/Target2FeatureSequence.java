/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.*;
/** Convert a token sequence in the target field into a feature sequence in the target field.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Target2FeatureSequence extends Pipe implements Serializable
{

	public Target2FeatureSequence ()
	{
		super (null, new Alphabet());
	}
	
	public Instance pipe (Instance carrier)
	{
		//Object in = carrier.getData();
		Object target = carrier.getTarget();
		if (target instanceof FeatureSequence)
			;																	// Nothing to do
		else if (target instanceof TokenSequence) {
			TokenSequence ts = (TokenSequence) target;
			FeatureSequence fs = new FeatureSequence (getTargetAlphabet(), ts.size());
			for (int i = 0; i < ts.size(); i++)
				fs.add (ts.getToken(i).getText());
			carrier.setTarget(fs);
		} else {
			throw new IllegalArgumentException ("Unrecognized target type.");
		}
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
