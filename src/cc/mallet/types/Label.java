/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;

import cc.mallet.types.Alphabet;

public class Label implements Labeling, Serializable, AlphabetCarrying
{
	Object entry;
	LabelAlphabet dictionary;
	int index;
	
	protected Label ()
	{
		throw new IllegalStateException
			("Label objects can only be created by their Alphabet.");
	}

	/** You should never call this directly.  New Label objects are
			created on-demand by calling LabelAlphabet.lookupIndex(obj). */
	Label (Object entry, LabelAlphabet dict, int index)
	{
		this.entry = entry;
		this.dictionary = dict;
		assert (dict.lookupIndex (entry, false) == index);
		this.index = index;
	}

	public LabelAlphabet getLabelAlphabet ()
	{
		return (LabelAlphabet) dictionary;
	}

	public int getIndex () { return index; }

	public Alphabet getAlphabet () { return dictionary; }
	
	public Alphabet[] getAlphabets () { return new Alphabet[] { dictionary }; }
	
	public Object getEntry () { return entry; }

	public String toString () { return entry.toString(); }


	// Comparable interface

	public int compareTo (Object o)
	{
		Label os = (Label)o;
		if (this.index < os.index)
			return -1;
		else if (this.index == os.index)
			return 0;
		else
			return 1;
	}
	

	// Labeling interface

	public Label getBestLabel ()
	{
		return this;
	}

	public int getBestIndex ()
	{
		return index;
	}

	static final double weightOfLabel = 1.0;

	public double getBestValue ()
	{
		return weightOfLabel;
	}

	public double value (Label label)
	{
		assert (label.dictionary.equals(this.dictionary));
		return weightOfLabel;
	}

	public double value (int labelIndex)
	{
		return labelIndex == this.index ? weightOfLabel : 0;
	}

	public int getRank (Label label)
	{
		assert (label.dictionary.equals(this.dictionary));
		return label == this ? 0 : -1;
	}

	public int getRank (int labelIndex)
	{
		return labelIndex == this.index ? 0 : -1;
	}

	public Label getLabelAtRank (int rank)
	{
		assert (rank == 0);
		return this;
	}

	public double getValueAtRank (int rank)
	{
		assert (rank == 0);
		return weightOfLabel;
	}

	public void addTo (double[] weights)
	{
		weights[this.index] += weightOfLabel;
	}
	
	public void addTo (double[] weights, double scale)
	{
		weights[this.index] += weightOfLabel * scale;
	}


	// The number of non-zero-weight Labels in this Labeling, not total
	// number in the Alphabet
	public int numLocations ()
	{
		return 1;
	}

	public Label labelAtLocation (int loc)
	{
		assert (loc == 0);
		return this;
	}

	public double valueAtLocation (int loc)
	{
		assert (loc == 0);
		return weightOfLabel;
	}

	public int indexAtLocation (int loc)
	{
		assert (loc == 0);
		return index;
	}

	public LabelVector toLabelVector ()
	{
		return new LabelVector ((LabelAlphabet)dictionary, new int[] {index}, new double[] {weightOfLabel});
	}

	public boolean equals (Object l) {
		if (l instanceof Label) {
			return ((Label)l).compareTo(this) == 0;
		}
		else throw new IllegalArgumentException ("Cannot compare a Label object with a " + l.getClass().getName() + " object.");
	}

	// Serialization 

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (dictionary);
		out.writeInt (index);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		dictionary = (LabelAlphabet) in.readObject ();
		index = in.readInt ();
		entry = dictionary.lookupObject (index);
	}


}
