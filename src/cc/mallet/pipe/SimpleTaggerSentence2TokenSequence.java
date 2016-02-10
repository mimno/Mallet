/* Copyright (C) 2003 University of Pennsylvania.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */

/**
 @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 Modified by Kuzman Ganchev to covert to TokenSequence rather than to FeatureVectorSequence.
 */

package cc.mallet.pipe;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import cc.mallet.types.*;

/**
 * Converts an external encoding of a sequence of elements with binary
 * features to a {@link TokenSequence}.  If target processing
 * is on (training or labeled test data), it extracts element labels
 * from the external encoding to create a target {@link LabelSequence}.
 * Two external encodings are supported:
 * <ol>
 * <li> A {@link String} containing lines of whitespace-separated tokens.</li>
 * <li> a {@link String}<code>[][]</code>.</li>
 * </ol>
 * <p/>
 * Both represent rows of tokens. When target processing is on, the last token
 * in each row is the label of the sequence element represented by
 * this row. All other tokens in the row, or all tokens in the row if
 * not target processing, are the names of features that are on for
 * the sequence element described by the row.
 */
public class SimpleTaggerSentence2TokenSequence extends Pipe {

  protected boolean setTokensAsFeatures;

  /**
   * Creates a new
   * <code>SimpleTaggerSentence2TokenSequence</code> instance.
   * By default we include tokens as features.
   */
  public SimpleTaggerSentence2TokenSequence ()
  {
    super (null, new LabelAlphabet());
    setTokensAsFeatures = true;
  }

  /**
   * creates a new <code>SimpleTaggerSentence2TokenSequence</code> instance
   * which includes tokens as features iff the supplied argument is true.
   */
  public SimpleTaggerSentence2TokenSequence (boolean inc)
  {
    super (null, new LabelAlphabet());
    setTokensAsFeatures = inc;
  }

  /**
   * Parses a string representing a sequence of rows of tokens into an
   * array of arrays of tokens.
   *
   * @param sentence a <code>String</code>
   * @return the corresponding array of arrays of tokens.
   */
  protected String[][] parseSentence (String sentence)
  {
    String[] lines = sentence.split ("\n");
    String[][] tokens = new String[lines.length][];
    for (int i = 0; i < lines.length; i++)
      tokens[i] = lines[i].split ("\\s");
    return tokens;
  }

  /** returns the first String in the array or "" if the array has length 0. 
   */ 
  protected String makeText(String[] in){
    if  (in.length>0) return in[0];
    else return "";
  }

  /**
   * Takes an instance with data of type String or String[][] and creates
   * an Instance of type TokenSequence.  Each Token in the sequence is
   * gets the test of the line preceding it and once feature of value 1
   * for each "Feature" in the line.  For example, if the String[][] is
   * {{a,b},{c,d,e}} (and target processing is off) then the text would be
   * "a b" for the first token and "c d e" for the second.  Also, the
   * features "a" and "b" would be set for the first token and "c", "d" and
   * "e"  for the second.  The last element in the String[] for the current
   * token is taken as the target (label), so in the previous example "b"
   * would have been the label of the first sequence.
   */
  public Instance pipe (Instance carrier)
  {
    Object inputData = carrier.getData();
    //Alphabet features = getDataAlphabet();
    LabelAlphabet labels;
    LabelSequence target = null;
    String [][] tokens;
    TokenSequence ts = new TokenSequence ();
    if (inputData instanceof String)
      tokens = parseSentence ((String) inputData);
    else if (inputData instanceof String[][])
      tokens = (String[][]) inputData;
    else
      throw new IllegalArgumentException ("Not a String or String[][]; got " + inputData);
    FeatureVector[] fvs = new FeatureVector[tokens.length];
    if (isTargetProcessing ()) {
      labels = (LabelAlphabet) getTargetAlphabet ();
      target = new LabelSequence (labels, tokens.length);
    }
    for (int l = 0; l < tokens.length; l++) {
      int nFeatures;
      if (isTargetProcessing ()) {
        if (tokens[l].length < 1)
          throw new IllegalStateException ("Missing label at line " + l + " instance " + carrier.getName ());
        nFeatures = tokens[l].length - 1;
        target.add(tokens[l][nFeatures]);
      } else nFeatures = tokens[l].length;
      Token tok = new Token(makeText(tokens[l]));
      if (setTokensAsFeatures){
	for (int f = 0; f < nFeatures; f++)
	  tok.setFeatureValue(tokens[l][f], 1.0);
      } else {
	for (int f = 1; f < nFeatures; f++)
	  tok.setFeatureValue(tokens[l][f], 1.0);
      }
      ts.add (tok);
    }
    carrier.setData (ts);
    if (isTargetProcessing ())
      carrier.setTarget (target);
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
