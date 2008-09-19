/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package cc.mallet.pipe;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.*;
import java.io.*;

import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.MalletLogger;

/**

	 Convert the string in each field <code>Token.text</code> to a list
	 of Strings (space delimited). Add each string as a feature to the
	 token. If <code>realValued</code> is true, then treat the position
	 in the list as the feature name and the value as a
	 double. Otherwise, the feature name is the string itself and the
	 value is 1.0.
     <p>
	 Modified to allow feature names and values to be specified.eg:
	 featureName1=featureValue1 featureName2=featureValue2 ...
	 The name/value separator (here '=') can be specified.
     <p>
	 *  If your data consists of feature/value pairs (eg <code>height=10.7 width=3.6 length=1.7</code>),
	 *   use <code>new TokenSequenceParseFeatureString(true, true, "=")</code>. This
	 *   format is typically used for sparse data, in which most features are equal to 0 in
	 *   any given instance.
	 *  <p>
	 *  If your data consists only of values, and the position determines which feature
	 *   the value is for (eg <code>10.7  3.6  1.7</code>),
	 *   use <code>new TokenSequenceParseFeatureString(true)</code>. 
	 *   This format is typically used for data that has a small number of features
	 *   that all have non-zero values most of the time.
	 *  <p>
	 *  If your data is in the form of named binary indicator variables
	 *   (eg <code>yellow quacks has_webbed_feet</code>), use the constructor
	 *   <code>new TokenSequenceParseFeatureString(false)</code>. Each token will be 
	 *   interpreted as the name of a feature, whose value is 1.0.

   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
*/

public class TokenSequenceParseFeatureString extends Pipe implements Serializable
{
	boolean realValued; // are these real-valued features?
	boolean specifyFeatureNames; // are the feature names given as well?
	String nameValueSeparator; // what separates the name from the value? (CAN'T BE WHITESPACE!)
	
	/**
	 *  @param _realValued interpret each data token as a double, and associate it with a 
	 *     feature called "Feature#K" where K is the order of the token, starting with 0. 
	 *     Note that this option is currently ignored if <code>_specifyFeatureNames</code> is true.
	 *  @param _specifyFeatureNames interpret each data token as a feature name/value pair,
	 *     separated by some delimiter, which is the equals sign ("=") unless otherwise specified.
	 *  @param _nameValueSeparator use a string other than = to separate name/value pairs. Colon (":") is
	 *     a common choice. Note that this string cannot consist of any whitespace, as the tokens stream
	 *     will already have been split.
	 */
	public TokenSequenceParseFeatureString (boolean _realValued, boolean _specifyFeatureNames, String _nameValueSeparator) {
		this.realValued = _realValued;
		if (_nameValueSeparator.trim().length()==0) {
			throw new IllegalArgumentException ("nameValueSeparator can't be whitespace");
		}
		nameValueSeparator = _nameValueSeparator;
		this.specifyFeatureNames = _specifyFeatureNames;
	}

	public TokenSequenceParseFeatureString (boolean _realValued, boolean _specifyFeatureNames) {
		this (_realValued, _specifyFeatureNames, "=");
	}
	
	public TokenSequenceParseFeatureString (boolean _realValued) {
		this (_realValued, false, "=");
	}

	
	public Instance pipe (Instance carrier) {
		TokenSequence ts = (TokenSequence) carrier.getData ();
		for (int i=0; i < ts.size(); i++) {
			Token t = ts.get (i);
			String[] values = t.getText().split("\\s+");
			for (int j=0; j < values.length; j++) {
				if (specifyFeatureNames) {
					String[] nameAndValue = values[j].split(nameValueSeparator);						
					if (nameAndValue.length != 2) { // no feature name. use token as feature.
						t.setFeatureValue ("Token="+values[j], 1.0);
					}
					else {
						t.setFeatureValue (nameAndValue[0], Double.parseDouble (nameAndValue[1]));						
					}
				}
				else if (realValued) {
					t.setFeatureValue ("Feature#" + j, Double.parseDouble (values[j]));
				}
				else
					t.setFeatureValue (values[j], 1.0);					
			}
		}
		carrier.setData (ts);
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeBoolean (realValued);
		out.writeBoolean (specifyFeatureNames);
		out.writeObject (nameValueSeparator);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		realValued = in.readBoolean ();
		if (version >= CURRENT_SERIAL_VERSION) {
			specifyFeatureNames = in.readBoolean();
			nameValueSeparator = (String)in.readObject();
		}
	}
}
