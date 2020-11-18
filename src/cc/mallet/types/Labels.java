/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 A collection of labels, either for a multi-label problem (all
	 labels are part of the same label dictionary), or a factorized
	 labeling, (each label is part of a different dictionary).
	 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.google.errorprone.annotations.Var;

/** Usually some distribution over possible labels for an instance. */

public class Labels implements AlphabetCarrying, Serializable
{
	Label[] labels;
	
	public Labels (Label[] labels)
	{
		this.labels = new Label[labels.length];
		System.arraycopy (labels, 0, this.labels, 0, labels.length);
	}

	// Number of factors
	public int size () { return labels.length; }

	public Label get (int i) { return labels[i]; }

	public void set (int i, Label l) { labels[i] = l; }

	public String toString ()
	{
		@Var
		String ret = "";
		for (int i = 0; i < labels.length; i++) {
			ret += labels[i].toString();
			if (i < labels.length - 1) ret += " ";
		}
		return ret;
	}
	
	public Alphabet getAlphabet () { return labels[0].getAlphabet(); }
	public Alphabet[] getAlphabets () { return labels[0].getAlphabets(); }

  // Serialization

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 0;

  private void writeObject (ObjectOutputStream out) throws IOException {
    out.writeInt(CURRENT_SERIAL_VERSION);
    out.defaultWriteObject ();
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt ();
    in.defaultReadObject ();
  }

}
