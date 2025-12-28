/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.*;
/**
 * convert the property list on a token into a feature vector
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class Token2FeatureVector extends Pipe implements Serializable
{
	boolean augmentable;									// Create AugmentableFeatureVector's in the sequence
	boolean binary;												// Create binary (Augmentable)FeatureVector's in the sequence
	
	public Token2FeatureVector (Alphabet dataDict,
															boolean binary, boolean augmentable)
	{
		super (dataDict, null);
		this.augmentable = augmentable;
		this.binary = binary;
	}

	public Token2FeatureVector (Alphabet dataDict)
	{
		this (dataDict, false, false);
	}
	
	public Token2FeatureVector (boolean binary, boolean augmentable)
	{
		super (new Alphabet(), null);
		this.augmentable = augmentable;
		this.binary = binary;
	}

	public Token2FeatureVector ()
	{
		this (false, false);
	}
	
	public Instance pipe (Instance carrier)
	{
		if (augmentable)
			carrier.setData(new AugmentableFeatureVector ((Alphabet)getDataAlphabet(),
																										((Token)carrier.getData()).getFeatures(),
																										binary));
		else
			carrier.setData(new FeatureVector ((Alphabet)getDataAlphabet(),
																				 ((Token)carrier.getData()).getFeatures(),
																				 binary));
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeBoolean(augmentable);
		out.writeBoolean(binary);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		augmentable = in.readBoolean();
		binary = in.readBoolean();
	}

}
