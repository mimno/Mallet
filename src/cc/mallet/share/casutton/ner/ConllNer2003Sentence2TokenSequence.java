/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

/*
	An error?  CoNLLTrue MalletTrue MalletPred
	O O O
	I-MISC B-MISC B-MISC
	B-MISC B-MISC I-MISC
	I-MISC B-MISC I-MISC
	O O O
	O O O
	O O O
*/

package cc.mallet.share.casutton.ner; // Generated package name


import java.util.regex.*;

import cc.mallet.extract.StringSpan;
import cc.mallet.extract.StringTokenization;
import cc.mallet.pipe.*;
import cc.mallet.types.*;

/**
 * Reads a data file in CoNLL 2003 format, and makes some simple
 *  transformations.
 *
 * Unlike the version in <tt>mccallum.ner</tt>, does not expect fields in
 *  the data file for tags and phrasos if those features are off.  Does
 *  not look for target field if isTargetProcessing() is false.
 */
public class ConllNer2003Sentence2TokenSequence extends Pipe
{
	static final String[] endings = new String[]
	{"ing", "ed", "ogy", "s", "ly", "ion", "tion", "ity", "ies"};
	static Pattern[] endingPatterns = new Pattern[endings.length];
	// Indexed by {forward,backward} {0,1,2 offset} {ending char ngram index}
	static final String[][][] endingNames = new String[2][3][endings.length];

	{
		for (int i = 0; i < endings.length; i++) {
			endingPatterns[i] = Pattern.compile (".*"+endings[i]+"$");
			for (int j = 0; j < 3; j++) {
				for (int k = 0; k < 2; k++)
					endingNames[k][j][i] = "W"+(k==1?"-":"")+j+"=<END"+endings[i]+">";
			}
		}
	}

	boolean saveSource = true;
	boolean doConjunctions = false;
	boolean doTags = true;
	boolean doPhrases = true;
	boolean doSpelling = false;
	boolean doDigitCollapses = true;
	boolean doDowncasing = false;
	
	public ConllNer2003Sentence2TokenSequence ()
	{
		super (null, new LabelAlphabet());
	}

	public ConllNer2003Sentence2TokenSequence (boolean useTags, boolean usePhrases)
	{
		super (null, new LabelAlphabet());
		this.doTags = useTags;
		this.doPhrases = usePhrases;
	}

	/* Lines look like this:
		 -DOCSTART- -X- -X- O

		 EU NNP I-NP I-ORG
		 rejects VBZ I-VP O
		 German JJ I-NP I-MISC
		 call NN I-NP O
		 to TO I-VP O
		 boycott VB I-VP O
		 British JJ I-NP I-MISC
		 lamb NN I-NP O
		 . . O O

		 Peter NNP I-NP I-PER
		 Blackburn NNP I-NP I-PER

		 BRUSSELS NNP I-NP I-LOC
		 1996-08-22 CD I-NP O

		 The DT I-NP O
		 European NNP I-NP I-ORG
		 Commission NNP I-NP I-ORG
		 said VBD I-VP O
		 on IN I-PP O
		 ...
	*/

	public Instance pipe (Instance carrier)
	{
		String sentenceLines = (String) carrier.getData();
		String[] tokens = sentenceLines.split ("\n");
		LabelSequence target = new LabelSequence ((LabelAlphabet)getTargetAlphabet(), tokens.length);
		boolean [][] ending = new boolean[3][endings.length];
		boolean [][] endingp1 = new boolean[3][endings.length];
		boolean [][] endingp2 = new boolean[3][endings.length];
		StringBuffer source = saveSource ? new StringBuffer() : null;
    TokenSequence data = new StringTokenization (source);

		String prevLabel = "NOLABEL";
		Pattern ipattern = Pattern.compile ("I-.*");
		String word, tag = null, phrase = null, label = null;

		for (int i = 0; i < tokens.length; i++) {
      if (tokens[i].length() != 0) {
        try {
				  String[] features = tokens[i].split (" ");
          int fieldIdx = 0;
          word = features[fieldIdx++]; // .toLowerCase();
          if (doTags) tag = features[fieldIdx++];
				  if (doPhrases) phrase = features[fieldIdx++];
				  if (isTargetProcessing ()) label = features[fieldIdx++];
        } catch (ArrayIndexOutOfBoundsException e) {
          throw new IllegalArgumentException ("Invalid line "+tokens[i]+" : expected word "
            + (doTags ? ", tag" : "")
            + (doPhrases ? ", phrase" : "")
            + (isTargetProcessing () ? ", target" : "")
            + ".");
        }
      } else {
				word = "-<S>-";
				tag = "-<S>-";
				phrase = "-<S>-";
				label = "O";
			}

			// Transformations
			if (doDigitCollapses) {
				if (word.matches ("19\\d\\d"))
					word = "<YEAR>";
				else if (word.matches ("19\\d\\ds"))
					word = "<YEARDECADE>";
				else if (word.matches ("19\\d\\d-\\d+"))
					word = "<YEARSPAN>";
				else if (word.matches ("\\d+\\\\/\\d"))
					word = "<FRACTION>";
				else if (word.matches ("\\d[\\d,\\.]*"))
					word = "<DIGITS>";
				else if (word.matches ("19\\d\\d-\\d\\d-\\d--d"))
					word = "<DATELINEDATE>";
				else if (word.matches ("19\\d\\d-\\d\\d-\\d\\d"))
					word = "<DATELINEDATE>";
				else if (word.matches (".*-led"))
					word = "<LED>";
				else if (word.matches (".*-sponsored"))
					word = "<LED>";
			}

			if (doDowncasing)
				word = word.toLowerCase();

      int start = source.length ();

      if (saveSource) {
        if (word.equals ("-<S>-")) source.append ("\n\n");
        source.append (word); source.append (" ");
      }

      Token token = new StringSpan (source, start, source.length () - 1);

			// Word and tag unigram at current time
			if (doSpelling) {
				for (int j = 0; j < endings.length; j++) {
					ending[2][j] = ending[1][j];
					ending[1][j] = ending[0][j];
					ending[0][j] = endingPatterns[j].matcher(word).matches();
					if (ending[0][j]) token.setFeatureValue (endingNames[0][0][j], 1);
				}
			}

			if (doTags) {
				token.setFeatureValue ("T="+tag, 1);
			}

			if (doPhrases) {
				token.setFeatureValue ("P="+phrase, 1);
			}

      data.add (token);

      if (isTargetProcessing ()) {
        // Change so each segment always begins with a "B-",
				// even if previous token did not have this label.
				String oldLabel = label;
				if (ipattern.matcher(label).matches ()
						&& (prevLabel.length() < 3		// prevLabel is "O"
								|| !prevLabel.substring(2).equals (label.substring(2)))) {
					label = "B" + oldLabel.substring(1);
				}
				prevLabel = oldLabel;
			  target.add (label);
      }

    }

    carrier.setData(data);
		if (isTargetProcessing ()) carrier.setTarget(target);
		if (saveSource) carrier.setSource(source);

    return carrier;
	}

  // serialization garbage

  private static final long serialVersionUID = -7326674871670572522L;
}
