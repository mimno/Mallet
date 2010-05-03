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

import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import cc.mallet.util.PropertyList;

/** A representation of a piece of text, usually a single word, to
		which we can attach properties. */

public class Token implements Serializable, PropertyHolder {

	private String text;
	PropertyList properties = null;				// for arbitrary properties
	PropertyList features = null;					// numeric, to turn into a FeatureVector
	
	public Token (String s) {
		text = s;
	}

	public String getText () {
		return text;
	}

	public void setText (String t) {
		text = t;
	}

	// xxx This implementation may change in the future!
	// If you really just want the text, you should use Token.getText() instead.
	public String toString () {
		StringBuffer sb = new StringBuffer ();
		sb.append (getText());
		if (features != null) {
			PropertyList.Iterator iter = features.iterator();
			while (iter.hasNext()) {
				iter.next();
				sb.append (" feature(" + iter.getKey() + ")=" +iter.getNumericValue());
			}
		}
		if (properties != null) {
			PropertyList.Iterator iter = properties.iterator();
			while (iter.hasNext()) {
				iter.next();
				if (iter.isNumeric())
					sb.append (" property(" + iter.getKey() + ")=" +iter.getNumericValue());
				else
					sb.append (" property(" + iter.getKey() + ")=" +iter.getObjectValue());
			}
		}
		return sb.toString();
	}
	
	public String toStringWithFeatureNames () {
		StringBuffer sb = new StringBuffer ();
		sb.append (getText());
		if (features != null) {
			PropertyList.Iterator iter = features.iterator();
			while (iter.hasNext()) {
				iter.next();
				sb.append (" " + iter.getKey());
			}
		}
		return sb.toString();
	}

	public FeatureVector toFeatureVector (Alphabet dict, boolean binary) {
		return new FeatureVector (dict, features, binary);
	}

	public void setProperty (String key, Object value) {
		properties = PropertyList.add (key, value, properties);
	}

	public void setNumericProperty (String key, double value) {
		properties = PropertyList.add (key, value, properties);
	}

	public PropertyList getProperties () {
		return properties;
	}

	public void setProperties (PropertyList newProperties) {
		properties = newProperties;
	}

	public Object getProperty (String key) {
		return properties == null ? null : properties.lookupObject (key);
	}

	public double getNumericProperty (String key) {
		return (properties == null ? 0.0 : properties.lookupNumber (key));
	}

	public boolean hasProperty (String key) {
		return (properties != null && properties.hasProperty( key ));
	}

	public void setFeatureValue (String key, double value) {
		features = PropertyList.add (key, value, features);
	}

	public double getFeatureValue (String key) {
		return (features == null ? 0.0 : features.lookupNumber (key));
	}

	public PropertyList getFeatures () {
		return features;
	}

	public void setFeatures (PropertyList pl) {
		features = pl;
	}

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
