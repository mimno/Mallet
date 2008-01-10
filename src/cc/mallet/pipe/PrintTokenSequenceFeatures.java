/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.*;
import cc.mallet.util.*;

/**
 * Print properties of the token sequence in the data field and the corresponding value
 * of any token in a token sequence or feature in a featur sequence in the target field.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class PrintTokenSequenceFeatures extends Pipe implements Serializable
{
	String prefix = null;

	public PrintTokenSequenceFeatures (String prefix)
	{
		this.prefix = prefix;
	}

	public PrintTokenSequenceFeatures ()
	{
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		TokenSequence targets = carrier.getTarget() instanceof TokenSequence ? (TokenSequence)carrier.getTarget() : null;
		TokenSequence source = carrier.getSource() instanceof TokenSequence ? (TokenSequence)carrier.getSource() : null;
		StringBuffer sb = new StringBuffer ();
		if (prefix != null)
			sb.append (prefix);
		sb.append ("name: "+carrier.getName()+"\n");
		for (int i = 0; i < ts.size(); i++) {
			if (source != null) {
				sb.append (source.getToken(i).getText());
				sb.append (' ');
			}
			if (carrier.getTarget() instanceof TokenSequence) {
				sb.append (((TokenSequence)carrier.getTarget()).getToken(i).getText());
				sb.append (' ');
			}	if (carrier.getTarget() instanceof FeatureSequence) {
				sb.append (((FeatureSequence)carrier.getTarget()).getObjectAtPosition(i).toString());
				sb.append (' ');
			}
			PropertyList pl = ts.getToken(i).getFeatures();
			if (pl != null) {
				PropertyList.Iterator iter = pl.iterator();
				while (iter.hasNext()) {
					iter.next();
					double v = iter.getNumericValue();
					if (v == 1.0)
						sb.append (iter.getKey());
					else
						sb.append (iter.getKey()+'='+v);
					sb.append (' ');
				}
			}
			sb.append ('\n');
		}
		System.out.print (sb.toString());
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
