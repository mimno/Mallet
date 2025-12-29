/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
/**
 * Print the data and target fields of each instance.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class PrintInputAndTarget extends Pipe implements Serializable {
    String prefix = null;

    public PrintInputAndTarget (String prefix) {
        this.prefix = prefix;
    }

    public PrintInputAndTarget () {}
    
    @Override public Instance pipe (Instance carrier) {
        if (prefix != null) {
            System.out.print (prefix);
        }
        String targetString = "<null>";
        if (carrier.getTarget() != null) {
            targetString = carrier.getTarget().toString();
        }
        System.out.println ("name: " + carrier.getName() + 
                            "\ntarget: " + targetString + 
                            "\ninput: " + carrier.getData()  // Swapping order, since data often has a newline at the end -DM
                            );
        return carrier;
    }
    
    // Serialization 
    
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    
    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt (CURRENT_SERIAL_VERSION);
        out.writeObject(prefix);
    }
    
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt ();
        prefix = (String) in.readObject();
    }
    
}
