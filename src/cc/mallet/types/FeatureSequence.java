/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.types;

import java.util.Arrays;
import java.io.*;

/**
 *   An implementation of {@link Sequence} that ensures that every
 *   Object in the sequence has the same class.  Feature sequences are
 *   mutable, and will expand as new objects are added.
 *
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class FeatureSequence implements Sequence, Serializable, AlphabetCarrying
{
	Alphabet dictionary;
	int[] features;
	int length;

	/**
	 *  Creates a FeatureSequence given all of the objects in the
	 *  sequence.
	 *
	 *  @param dict A dictionary that maps objects in the sequence
	 *     to numeric indices.
	 *  @param features An array where features[i] gives the index
	 *     in dict of the ith element of the sequence.
	 */
	public FeatureSequence (Alphabet dict, int[] features)
	{
		this(dict, features.length);
		for (int i = 0; i < features.length; i++)
			add(features[i]);
	}

	public FeatureSequence (Alphabet dict, int[] features, int len)
	{
		this(dict, len);
		for (int i = 0; i < len; i++)
			add(features[i]);
	}

	public FeatureSequence (Alphabet dict, int capacity)
	{
		dictionary = dict;
		features = new int[capacity > 2 ? capacity : 2];
		length = 0;
	}

	public FeatureSequence (Alphabet dict)
	{
		this (dict, 2);
	}
	
	public int[] getFeatures() { return features ;}
	
	public Alphabet getAlphabet ()	{	return dictionary; }
	
	public Alphabet[] getAlphabets() {
		return new Alphabet[] {getAlphabet()};
	}
	
	public boolean alphabetsMatch (AlphabetCarrying object)	{
		return getAlphabet().equals (object.getAlphabet());
	}

	public final int getLength () { return length; }

	public final int size () { return length; }

	public final int getIndexAtPosition (int pos)
	{
		return features[pos];
	}

	public Object getObjectAtPosition (int pos)
	{
		return dictionary.lookupObject (features[pos]);
	}

	// xxx This method name seems a bit ambiguous?
	public Object get (int pos)
	{
		return dictionary.lookupObject (features[pos]);
	}

	public String toString ()
	{
		StringBuffer sb = new StringBuffer ();
		for (int fsi = 0; fsi < length; fsi++) {
			Object o = dictionary.lookupObject(features[fsi]);
      sb.append (fsi);
      sb.append (": ");
			sb.append (o.toString());
			sb.append (" (");
			sb.append (features[fsi]);
			sb.append (")\n");
		}
		return sb.toString();
	}

	protected void growIfNecessary ()
	{
		if (length == features.length) {
			int[] newFeatures = new int[features.length * 2];
			System.arraycopy (features, 0, newFeatures, 0, length);
			features = newFeatures;
		}
	}

	public void add (int featureIndex)
	{
		growIfNecessary ();
		assert (featureIndex < dictionary.size());
		features[length++] = featureIndex;
	}

	public void add (Object key)
	{
		int fi = dictionary.lookupIndex (key);
		if (fi >= 0)
			add (fi);
		
		// gdruck@cs.umass.edu
		// With the exception below, it is not possible to pipe data
		// when growth of the alphabet is stopped.  We want to be 
		// able to do this, for example to process new data using 
		// an old Pipe (for example from a fixed, cached classifier
		// that we want to apply to new data.).
		//else
			// xxx Should we raise an exception if the appending doesn't happen?  "yes" -akm, added 1/2008
		//	throw new IllegalStateException ("Object cannot be added to FeatureSequence because its Alphabet is frozen.");
	}

	public void addFeatureWeightsTo (double[] weights)
	{
		for (int i = 0; i < length; i++)
			weights[features[i]]++;
	}

	public void addFeatureWeightsTo (double[] weights, double scale)
	{
		for (int i = 0; i < length; i++)
			weights[features[i]] += scale;
	}

	public int[] toFeatureIndexSequence ()
	{
		int[] feats = new int[length];
		System.arraycopy (features, 0, feats, 0, length);
		return feats;
	}

	public int[] toSortedFeatureIndexSequence ()
	{
		int[] feats = this.toFeatureIndexSequence ();
		java.util.Arrays.sort (feats);
		return feats;
	}
	
	
	/** 
	 *  Remove features from the sequence that occur fewer than 
	 *  <code>cutoff</code> times in the corpus, as indicated by 
	 *  the provided counts. Also swap in the new, reduced alphabet.
	 *  This method alters the instance in place; it is not appropriate
	 *  if the original instance will be needed.
	 */
    public void prune (double[] counts, Alphabet newAlphabet,
                       int cutoff) {
        // The goal is to replace the sequence of features in place, by
        //  creating a new array and then swapping it in.

        // First: figure out how long the new array will have to be

        int newLength = 0;
        for (int i = 0; i < length; i++) {
            if (counts[features[i]] >= cutoff) {
                newLength++;
            }
        }

        // Second: allocate a new features array
        
        int[] newFeatures = new int[newLength];

        // Third: fill the new array

        int newIndex = 0;
        for (int i = 0; i < length; i++) {
            if (counts[features[i]] >= cutoff) {

                Object feature = dictionary.lookupObject(features[i]);
                newFeatures[newIndex] = newAlphabet.lookupIndex(feature);

                newIndex++;
            }
        }

        // Fourth: swap out the arrays

        features = newFeatures;
        length = newLength;
        dictionary = newAlphabet;

    }
    
   	// Serialization
		
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (dictionary);
		out.writeInt (features.length);
		for (int i = 0; i < features.length; i++)
			out.writeInt (features[i]);
		out.writeInt (length);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int featuresLength;
		int version = in.readInt ();
		dictionary = (Alphabet) in.readObject ();
		featuresLength = in.readInt();
		features = new int[featuresLength];
		for (int i = 0; i < featuresLength; i++)
			features[i] = in.readInt ();
		length = in.readInt ();
	}
	
}
