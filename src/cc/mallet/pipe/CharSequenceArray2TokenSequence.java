/* Copyright (C) 2003 Univiversity of Pennsylvania.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


package cc.mallet.pipe;

import java.io.*;
import java.net.URI;

import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;

/**
 * Transform an array of character Sequences into a token sequence.
   @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 */
public class CharSequenceArray2TokenSequence extends Pipe
  implements Serializable
{
    public CharSequenceArray2TokenSequence ()
    {
    }
  
    @Override public Instance pipe (Instance carrier)
    {
    carrier.setData(new TokenSequence((CharSequence[]) carrier.getData()));
        return carrier;
    }

    // Serialization 
    
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    
    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt(CURRENT_SERIAL_VERSION);
    }
    
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt ();
    }
}
