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

import java.io.*;
import java.util.Iterator;

// xxx A not very space-efficient version.  I'll compress it later.

public class FeatureVectorSequence implements Sequence, Serializable, AlphabetCarrying
{
	FeatureVector[] sequence;

	public FeatureVectorSequence (FeatureVector[] featureVectors)
	{
		this.sequence = featureVectors;
	}

	public FeatureVectorSequence (Alphabet dict,
																TokenSequence tokens,
																boolean binary,
																boolean augmentable,
																boolean growAlphabet)
	{
		this.sequence = new FeatureVector[tokens.size()];
		if (augmentable)
			for (int i = 0; i < tokens.size(); i++)
				sequence[i] = new AugmentableFeatureVector (dict, tokens.getToken(i).getFeatures(), binary, growAlphabet);
		else
			for (int i = 0; i < tokens.size(); i++)
				sequence[i] = new FeatureVector (dict, tokens.getToken(i).getFeatures(), binary, growAlphabet);
	}

	public FeatureVectorSequence (Alphabet dict,
																TokenSequence tokens,
																boolean binary,
																boolean augmentable)
	{
		this(dict, tokens, binary, augmentable, true);
	}
	
	public FeatureVectorSequence (Alphabet dict,
																TokenSequence tokens)
	{
		this (dict, tokens, false, false);
	}
	
	public Alphabet getAlphabet() {
		if (sequence.length > 0)
			return sequence[0].getAlphabet();
		else
			return null;
	}
	
	public Alphabet[] getAlphabets()
	{
		return new Alphabet[] {getAlphabet()};
	}
	
	public boolean alphabetsMatch (AlphabetCarrying object)
	{
		return getAlphabet().equals (object.getAlphabet());
	}


	
	public int size ()
	{
		return sequence.length;
	}

	public Object get (int i)
	{
		return sequence[i];
	}

	public FeatureVector getFeatureVector (int i)
	{
		return sequence [i];
	}
	
	public double dotProduct (int sequencePosition,
														Matrix2 weights,
														int weightRowIndex)
	{
		return weights.rowDotProduct (weightRowIndex, sequence[sequencePosition]);
	}

	public double dotProduct (int sequencePosition,
														Vector weights)
	{
		return weights.dotProduct (sequence[sequencePosition]);
	}
	
	/** An iterator over the FeatureVectors in the sequence. */
	public class Iterator implements java.util.Iterator
	{
		int pos;
		public Iterator () {
			pos = 0;
		}
		public Object next() {
			return sequence[pos++];
		}
		public int getIndex () {
			return pos;
		}
		public boolean hasNext() {
			return pos < sequence.length;
		}
		public void remove () {
			throw new UnsupportedOperationException ();
		}
	}
	
	public Iterator iterator ()
	{
		return new Iterator();
	}


	public String toString ()
	{
		StringBuffer sb = new StringBuffer ();
		sb.append (super.toString());
		sb.append ('\n');
		for (int i = 0; i < sequence.length; i++) {
			sb.append (Integer.toString(i)+": ");
			//sb.append (sequence[i].getClass().getName()); sb.append (' ');
			sb.append (sequence[i].toString(true));
			sb.append ('\n');
		}
		return sb.toString();
	}

	// Serialization of Instance
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		if (sequence == null) {
			out.writeInt(NULL_INTEGER);
		}
		else {
			int size = sequence.length;
			out.writeInt(size);
			for(int i=0; i<size; i++) {
				out.writeObject(sequence[i]);
			}
		}
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		int size = in.readInt();
		if (size == NULL_INTEGER) {
			sequence = null;
		}
		else {
      sequence = new FeatureVector[size];
			for (int i = 0; i<size; i++) {
				sequence[i] = (FeatureVector) in.readObject();
			}
		}
	}

}
