/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;

import cc.mallet.types.*;
/**
 * convert a token sequence in the target field into a label sequence in the target field.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Target2LabelSequence extends Pipe implements Serializable
{

	public Target2LabelSequence ()
	{
		super (null, new LabelAlphabet());
	}
	
	public Instance pipe (Instance carrier)
	{
		
		//Object in = carrier.getData();
		Object target = carrier.getTarget();
    if (target == null)                 // Do nothing
      ;
		else if (target instanceof LabelSequence)
			;																	// Nothing to do
    else if (target instanceof FeatureSequence) {
      LabelAlphabet dict = (LabelAlphabet) getTargetAlphabet ();
      FeatureSequence fs = (FeatureSequence) target;
      Label[] lbls = new Label[fs.size()];
      for (int i = 0; i < fs.size (); i++) {
        lbls[i] = dict.lookupLabel (fs.getObjectAtPosition (i));
      }
      carrier.setTarget (new LabelSequence (lbls));
    }
    else if (target instanceof TokenSequence) {
			Alphabet v = getTargetAlphabet ();
			TokenSequence ts = (TokenSequence) target;
			int indices[] = new int[ts.size()];
			for (int i = 0; i < ts.size(); i++)
				indices[i] = v.lookupIndex (ts.getToken(i).getText());
			LabelSequence ls = new LabelSequence ((LabelAlphabet)getTargetAlphabet(), indices);
			carrier.setTarget(ls);
		} else if (target instanceof LabelsSequence) {
      LabelAlphabet dict = (LabelAlphabet) getTargetAlphabet ();
      LabelsSequence lblseq = (LabelsSequence) target;
      Label[] labelArray = new Label [lblseq.size()];
      for (int i = 0; i < lblseq.size(); i++) {
        Labels lbls = lblseq.getLabels (i);
        if (lbls.size () != 1)
          throw new IllegalArgumentException ("Cannot convert Labels at position "+i+" : "+lbls);
        labelArray[i] = dict.lookupLabel (lbls.get (0).getEntry ());
      }
      LabelSequence ls = new LabelSequence (labelArray);
      carrier.setTarget (ls);
    } else {
      throw new IllegalArgumentException ("Unrecognized target type: "+target);
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
