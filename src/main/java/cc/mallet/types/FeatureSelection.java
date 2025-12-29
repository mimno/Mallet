/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 A subset of features.
	 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.util.BitSet;
import java.util.regex.Pattern;
import java.io.*;

import cc.mallet.types.*;

public class FeatureSelection implements AlphabetCarrying, Serializable
{
	Alphabet dictionary;
	BitSet selectedFeatures;
	// boolean defaultValue;  //Implement this by using it to reverse all the exterior interfaces

	public FeatureSelection (Alphabet dictionary,
													 BitSet selectedFeatures)
	{
		this.dictionary = dictionary;
		this.selectedFeatures = selectedFeatures;
	}

	public FeatureSelection (Alphabet dictionary)
	{
		this.dictionary = dictionary;
		this.selectedFeatures = new BitSet();
	}

	public FeatureSelection (RankedFeatureVector rsv, int numFeatures)
	{
		this.dictionary = rsv.getAlphabet();
		this.selectedFeatures = new BitSet (dictionary.size());
		int numSelections = Math.min (numFeatures, dictionary.size());
		for (int i = 0; i < numSelections; i++)
			selectedFeatures.set (rsv.getIndexAtRank(i));
	}

  /** Creates a FeatureSelection that includes only those features whose names match a given regex.
   *   A static factory method.
   * @param dictionary  A dictionary of fetaure names.  Entries must be string.
   * @param regex Features whose names match this pattern will be included.
   * @return A new FeatureSelection.
   * */
  public static FeatureSelection createFromRegex (Alphabet dictionary, Pattern regex)
  {
    BitSet included = new BitSet (dictionary.size());
    for (int i = 0; i < dictionary.size(); i++) {
      String feature = (String) dictionary.lookupObject (i);
      if (regex.matcher (feature).matches()) {
        included.set (i);
      }
    }
    return new FeatureSelection (dictionary, included);
  }

	public Object clone ()
	{
		return new FeatureSelection (dictionary, (BitSet)selectedFeatures.clone());
	}

	public Alphabet getAlphabet ()
	{
		return dictionary;
	}
	
	public Alphabet[] getAlphabets () { 
		return new Alphabet[] { dictionary }; 
	}

	public int cardinality ()
	{
		return selectedFeatures.cardinality();
	}

	public BitSet getBitSet ()
	{
		return selectedFeatures;
	}

	public void add (Object o)
	{
		add (dictionary.lookupIndex(o));
	}

	public void add (int index)
	{
		assert (index >= 0);
		selectedFeatures.set (index);
	}

	public void remove (Object o)
	{
		remove (dictionary.lookupIndex(o));
	}

	public void remove (int index)
	{
		selectedFeatures.set (index, false);
	}
	
	public boolean contains (Object o)
	{
		int index = dictionary.lookupIndex (o, false);
		if (index == -1)
			return false;
		return contains (index);
	}

	public boolean contains (int index)
	{
		return selectedFeatures.get (index);
	}

	public void or (FeatureSelection fs)
	{
		selectedFeatures.or (fs.selectedFeatures);
	}

	public int nextSelectedIndex (int index)
	{
		return selectedFeatures.nextSetBit (index);
	}

	public int nextDeselectedIndex (int index)
	{
		return selectedFeatures.nextClearBit (index);
	}
	// Serialization
	

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	static final int NULL_INTEGER = -1;
	
	/* Need to check for null pointers. */
	private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(dictionary);
		out.writeObject(selectedFeatures);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int size, i;
		int version = in.readInt ();
		dictionary = (Alphabet) in.readObject();
		selectedFeatures = (BitSet) in.readObject();
	}
	

}
