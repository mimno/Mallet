/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.cluster;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.InstanceList;

/**
 * An abstract class for clustering a set of points.
 * @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
 */
public abstract class Clusterer implements Serializable {
	
	Pipe instancePipe;
	
	/**
	 * Creates a new <code>Clusterer</code> instance.
	 *
	 * @param instancePipe Pipe that created the InstanceList to be
	 * clustered.
	 */
	public Clusterer(Pipe instancePipe) {
		this.instancePipe = instancePipe;
	}
	
	/** Return a clustering of an InstanceList */
	public abstract Clustering cluster (InstanceList trainingSet);

	public Pipe getPipe () { return instancePipe; }
	
	// SERIALIZATION

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject ();
    int version = in.readInt ();
  }	
}
