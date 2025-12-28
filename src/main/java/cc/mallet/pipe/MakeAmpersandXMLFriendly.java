/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;


import java.io.*;

import cc.mallet.types.*;
// convert & to &amp
/** convert & to &amp;amp in tokens of a token sequence
   @author Aron Culotta <a
   href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

public class MakeAmpersandXMLFriendly extends Pipe implements Serializable
{

    public MakeAmpersandXMLFriendly ()
    {
    }

    @Override public Instance pipe (Instance carrier)
    {
        TokenSequence ts = (TokenSequence) carrier.getData();
        for (int i = 0; i < ts.size(); i++) {
            Token t = ts.get(i);
            String s = t.getText();
            if (s.indexOf("&") != -1) {
                if (s.indexOf("&amp;") != -1) // already friendly
                    return carrier;
                else {
                    t.setText (s.replaceAll ("&", "&amp;"));
                }
            }
        }
        return carrier;
    }
    // Serialization 
    
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    
    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt (CURRENT_SERIAL_VERSION);
    }
    
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt ();
    }

}
