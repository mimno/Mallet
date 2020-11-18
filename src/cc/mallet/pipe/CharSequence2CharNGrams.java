/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.google.errorprone.annotations.Var;

import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;

/**
 * Transform a character sequence into a token sequence of character N grams.
 *    @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class CharSequence2CharNGrams extends Pipe implements Serializable
{
    int n;
    boolean distinguishBorders = false;

    static char startBorderChar = '>';
    static char endBorderChar = '<';

    public CharSequence2CharNGrams (int n, boolean distinguishBorders) {
        this.n = n;
        this.distinguishBorders = distinguishBorders;
    }

    protected String[] ngramify (@Var CharSequence s) {
        if (distinguishBorders) {
            s = new StringBuilder().append(startBorderChar).append(s).append(endBorderChar).toString();
        }
        int count = s.length() - n;
        String[] ngrams = new String[count];
        for (int i = 0; i < count; i++) {
            ngrams[i] = s.subSequence (i, i+n).toString();
        }
        return ngrams;
    }

    @Override public Instance pipe (Instance carrier) {
        if (carrier.getData() instanceof CharSequence) {
            carrier.setData(new TokenSequence (ngramify ((CharSequence)carrier.getData())));
        }
        else if (carrier.getData() instanceof TokenSequence) {
            TokenSequence ts = (TokenSequence) carrier.getData();
            TokenSequence ret = new TokenSequence ();
            for (int i = 0; i < ts.size(); i++) {
                ret.add ( String.join("", ngramify (ts.get(i).getText())) );
            }
            carrier.setData(ret);
        } else {
            throw new IllegalArgumentException ("Unhandled type "+carrier.getData().getClass());
        }
        return carrier;
    }

    // Serialization 
    
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    
    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt (CURRENT_SERIAL_VERSION);
        out.writeInt(n);
        out.writeBoolean(distinguishBorders);
    }
    
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt ();
        n = in.readInt();
        distinguishBorders = in.readBoolean();
    }

}
