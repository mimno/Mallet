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


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import cc.mallet.pipe.Pipe;
import cc.mallet.util.PropertyList;

/**
 * A representation of a piece of text, usually a single word, to which we can attach properties.
 */

public class TokenSequence implements Sequence, Serializable {
	ArrayList tokens;
	PropertyList properties = null;				// for arbitrary properties

	public TokenSequence(Collection tokens) {
		this.tokens = new ArrayList( tokens );
	}

	public TokenSequence() {
		this.tokens = new ArrayList();
	}

	public TokenSequence(int capacity) {
		this.tokens = new ArrayList( capacity );
	}

	public TokenSequence(Token[] tokens) {
		this( tokens.length );
		for (int i = 0; i < tokens.length; i++)
			this.add( tokens[i] );
	}

	public TokenSequence(Object[] tokens) {
		this( tokens.length );
		for (int i = 0; i < tokens.length; i++)
			this.add( new Token( tokens[i].toString() ) );
	}


	public int size() {
		return this.tokens.size();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append( "TokenSequence " + super.toString() + "\n" );
		for (int i = 0; i < tokens.size(); i++) {
			String tt = getToken( i ).toString();
			sb.append( "Token#" + i + ":" );
			sb.append( tt );
			if (!tt.endsWith( "\n" ))
				sb.append( "\n" );
		}
		return sb.toString();
	}

    public String toStringShort(){
	StringBuffer sb = new StringBuffer();
	for (int i = 0; i < tokens.size(); i++) {
	    String tt = getToken( i ).toString();
	    tt.replaceAll("\n","");
	    if (i > 0){
		sb.append(" ");
	    }
	    sb.append(tt);
	}
	return sb.toString();
    }

	public Token getToken(int i) {
		return (Token)tokens.get( i );
	}

	public Object get(int i) {
		return tokens.get( i );
	}

	public void add(Object o) {
		if (o instanceof Token)
			add( (Token)o );
		else if (o instanceof TokenSequence)
			add( (TokenSequence)o );
		else
			add( new Token( o.toString() ) );
	}

	public void add(Token t) {
		tokens.add( t );
	}

	//added by Fuchun Peng, Oct. 24, 2003
	public Object remove(int index) {
		return tokens.remove( index );
	}

	// added by Fuchun Peng, Oct. 24, 2003
	public Object removeLastToken() {
		if (tokens.size() > 0) {
			return tokens.remove( tokens.size() - 1 );
		}
		else
			return null;
	}

	public void addAll(TokenSequence ts) {
		for (int i = 0; i < ts.size(); i++)
			add( ts.getToken( i ) );
	}

	public void addAll(Token[] tokens) {
		for (int i = 0; i < tokens.length; i++)
			add( tokens[i] );
	}

	public void addAll(Object[] tokens) {
		for (int i = 0; i < tokens.length; i++) {
			if (tokens[i] instanceof Token)
				add( (Token)tokens[i] );
			else
				add( new Token( tokens[i].toString() ) );
		}
	}

	public Iterator iterator() {
		return tokens.iterator();
	}

	public FeatureSequence toFeatureSequence(Alphabet dict) {
		FeatureSequence fs = new FeatureSequence( dict, tokens.size() );
		for (int i = 0; i < tokens.size(); i++)
			fs.add( dict.lookupIndex( ((Token)tokens.get( i )).getText() ) );
		return fs;
	}

	public FeatureVector toFeatureVector(Alphabet dict) {
		return new FeatureVector( toFeatureSequence( dict ) );
	}

	public void setNumericProperty(String key, double value) {
		properties = PropertyList.add( key, value, properties );
	}

	public void setProperty(String key, Object value) {
		properties = PropertyList.add( key, value, properties );
	}

	public double getNumericProperty(String key) {
		return properties.lookupNumber( key );
	}

	public Object getProperty(String key) {
		return properties.lookupObject( key );
	}

	public boolean hasProperty(String key) {
		return properties.hasProperty( key );
	}

    // added gmann 8/30/2006
    public PropertyList getProperties(){
	return properties;
    }


	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeInt( CURRENT_SERIAL_VERSION );
		out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt();
		in.defaultReadObject();
	}


}
