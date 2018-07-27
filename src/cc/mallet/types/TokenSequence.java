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

import cc.mallet.util.PropertyList;

/**
 * A representation of a piece of text, usually a single word, to which we can attach properties.
 */

public class TokenSequence extends ArrayList<Token> implements Sequence, Serializable {
    //ArrayList tokens;
    PropertyList properties = null;                // for arbitrary properties

    public TokenSequence (Collection<Token> tokens) {
        super(tokens);
    }

    public TokenSequence () {
        super();
    }

    public TokenSequence (int capacity) {
        super (capacity);
    }

    public TokenSequence (Token[] tokens) {
        this (tokens.length);
        for (int i = 0; i < tokens.length; i++)
            this.add( tokens[i] );
    }

    public TokenSequence (Object[] tokens) {
        this( tokens.length );
        for (int i = 0; i < tokens.length; i++)
            this.add (new Token( tokens[i].toString()));
    }
    
    //public Token get (int i) {return this.get(i);    }

    @Override public String toString () {
        StringBuilder sb = new StringBuilder();
        sb.append( "TokenSequence " + super.toString() + "\n" );
        for (int i = 0; i < this.size(); i++) {
            String tt = get(i).toString();
            sb.append( "Token#" + i + ":" );
            sb.append( tt );
            if (!tt.endsWith( "\n" ))
                sb.append( "\n" );
        }
        return sb.toString();
    }

    public String toStringShort () {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.size(); i++) {
            String tt = get(i).toString().replaceAll("\n"," ");
            if (i > 0){
                sb.append(" ");
            }
            sb.append(tt);
        }
        return sb.toString();
    }

    public void add(String string) {
        add(new Token(string));
    }

    // added by Fuchun Peng, Oct. 24, 2003
    public Object removeLast () {
        if (this.size() > 0) {
            return this.remove (this.size() - 1);
        }
        else {
            return null;
        }
    }


    public void addAll (Object[] objects) {
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] instanceof Token) {
                add( (Token)objects[i] );
            }
            else {
                add( new Token( objects[i].toString() ) );
            }
        }
    }

    public FeatureSequence toFeatureSequence (Alphabet dict) {
        FeatureSequence fs = new FeatureSequence( dict, this.size() );
        for (int i = 0; i < this.size(); i++) {
            fs.add (dict.lookupIndex( (this.get(i)).getText()));
        }
        return fs;
    }

    public FeatureVector toFeatureVector (Alphabet dict) {
        return new FeatureVector( toFeatureSequence( dict ) );
    }

    public void setNumericProperty (String key, double value) {
        properties = PropertyList.add( key, value, properties );
    }

    public void setProperty (String key, Object value) {
        properties = PropertyList.add( key, value, properties );
    }

    public double getNumericProperty (String key) {
        return properties.lookupNumber( key );
    }

    public Object getProperty (String key) {
        return properties.lookupObject( key );
    }

    public boolean hasProperty (String key) {
        return properties.hasProperty( key );
    }

    // added gmann 8/30/2006
    public PropertyList getProperties () {
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
        @SuppressWarnings("unused")
        int version = in.readInt();
        in.defaultReadObject();
    }


}
