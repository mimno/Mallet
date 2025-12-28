/* Copyright (C) 2003 University of Pennsylvania.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */

/**
	 @author Aron Culotta
 
 */

package cc.mallet.pipe;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import cc.mallet.extract.StringSpan;
import cc.mallet.extract.StringTokenization;
import cc.mallet.types.*;

/**
 * This extends {@link SimpleTaggerSentence2TokenSequence} to use
 * {Slink StringTokenizations} for use with the extract package.
 */
public class SimpleTaggerSentence2StringTokenization
	extends SimpleTaggerSentence2TokenSequence{

  /**
   * Creates a new
   * <code>SimpleTaggerSentence2StringTokenization</code> instance.
   * By default we include tokens as features.
   */
  public SimpleTaggerSentence2StringTokenization ()
  {
    super ();
  }

  /**
   * creates a new <code>SimpleTaggerSentence2StringTokenization</code> instance
   * which includes tokens as features iff the supplied argument is true.
   */
  public SimpleTaggerSentence2StringTokenization (boolean inc)
  {
    super (inc);
  }


  /**
   * Takes an instance with data of type String or String[][] and creates
   * an Instance of type StringTokenization.  Each Token in the sequence is
   * gets the test of the line preceding it and once feature of value 1
   * for each "Feature" in the line.  For example, if the String[][] is
   * {{a,b},{c,d,e}} (and target processing is off) then the text would be
   * "a b" for the first token and "c d e" for the second.  Also, the
   * features "a" and "b" would be set for the first token and "c", "d" and
   * "e"  for the second.  The last element in the String[] for the current
   * token is taken as the target (label), so in the previous example "b"
   * would have been the label of the first sequence.
   */
  public Instance pipe(Instance carrier) {
		Object inputData = carrier.getData();
		LabelAlphabet labels;
		LabelSequence target = null;
		String[][] tokens;
		StringBuffer source = new StringBuffer();
		StringTokenization ts = new StringTokenization(source);
		if (inputData instanceof String)
			tokens = parseSentence((String) inputData);
		else if (inputData instanceof String[][])
			tokens = (String[][]) inputData;
		else
			throw new IllegalArgumentException("Not a String; got " + inputData);
		if (isTargetProcessing()) {
			labels = (LabelAlphabet) getTargetAlphabet();
			target = new LabelSequence(labels, tokens.length);
		}
		for (int l = 0; l < tokens.length; l++) {
			int nFeatures;
			if (isTargetProcessing()) {
				if (tokens[l].length < 1)
					throw new IllegalStateException("Missing label at line "
							+ l + " instance " + carrier.getName());
				nFeatures = tokens[l].length - 1;
				target.add(tokens[l][nFeatures]);
			} else
				nFeatures = tokens[l].length;
			int start = source.length();
			String word = makeText(tokens[l]);
			source.append(word + " ");
			Token tok = new StringSpan(source, start, source.length() - 1);
			if (setTokensAsFeatures) {
				for (int f = 0; f < nFeatures; f++)
					tok.setFeatureValue(tokens[l][f], 1.0);
			} else {
				for (int f = 1; f < nFeatures; f++)
					tok.setFeatureValue(tokens[l][f], 1.0);
			}
			ts.add(tok);
		}
		carrier.setData(ts);
		if (isTargetProcessing())
			carrier.setTarget(target);
		return carrier;
	}
	
  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
  }

}
