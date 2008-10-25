/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;

/**
 * Replace the data string with a lowercased version. 
 *  This can improve performance over TokenSequenceLowercase.
 */

public class CharSequenceLowercase extends Pipe implements Serializable {
	
	public Instance pipe (Instance carrier) {

		if (carrier.getData() instanceof String) {
			String data = (String) carrier.getData();
			carrier.setData(data.toLowerCase());
		}
		else {
			throw new IllegalArgumentException("CharSequenceLowercase expects a String, found a " + carrier.getData().getClass());
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
