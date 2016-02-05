/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.io.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

/**
	 Creates a {@link LabelSequence} out of a {@link TokenSequence} that
	 is the target of an {@link Instance}. Labels are constructed out of
	 each Token in the TokenSequence to conform with BIO format (Begin,
	 Inside, Outside of Segment). Prepends a "B-" to Tokens that leave a
	 background state and an "I-" to tags that have the same label as
	 the previous Token. NOTE: This class assumes that subsequent
	 identical tags belong to the same Segment. This means that you
	 cannot have B B I, only B I I.
 */
public class Target2BIOFormat extends Pipe implements Serializable
{
	String backgroundTag;
	
	public Target2BIOFormat ()
	{
		super (null, new LabelAlphabet());
		backgroundTag = "O";
	}

	/**
		 @param background represents Tokens that are not part of a target
		 Segment.
	 */
	public Target2BIOFormat (String background)
	{
		super (null, new LabelAlphabet());
		this.backgroundTag = background;
	}

	public Instance pipe (Instance carrier)
	{
		
		Object target = carrier.getTarget();
		if (target instanceof TokenSequence) {
			Alphabet v = getTargetAlphabet ();
			TokenSequence ts = (TokenSequence) target;
			int indices[] = new int[ts.size()];
			String previousString =  this.backgroundTag;
			for (int i = 0; i < ts.size(); i++) {
				String s = ts.get (i).getText ();
				String tag = s;
				if (!tag.equals (this.backgroundTag)) {
					if (tag.equals (previousString))
						tag = "I-" + tag;
					else tag = "B-" + tag;					
				}
				indices[i] = v.lookupIndex (tag);
				previousString = s;
			}
			LabelSequence ls = new LabelSequence ((LabelAlphabet)getTargetAlphabet(), indices);
			carrier.setTarget(ls);
		} else {
			throw new IllegalArgumentException ("Unrecognized target type.");
		}

		return carrier;
	}


	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (backgroundTag);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		backgroundTag = (String) in.readObject ();
	}
	
}
