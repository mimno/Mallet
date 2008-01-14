/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.share.mccallum.ner;

import junit.framework.*;
import java.util.Iterator;
import java.util.Random;
import java.util.regex.*;
import java.io.*;

import cc.mallet.fst.*;
import cc.mallet.optimize.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

public class TUI
{
	static CommandOption.Double gaussianVarianceOption = new CommandOption.Double
	(TUI.class, "gaussian-variance", "DECIMAL", true, 10.0,
	 "The gaussian prior variance used for training.", null);

	static CommandOption.Double hyperbolicSlopeOption = new CommandOption.Double
	(TUI.class, "hyperbolic-slope", "DECIMAL", true, 0.2,
	 "The hyperbolic prior slope used for training.", null);

	static CommandOption.Double hyperbolicSharpnessOption = new CommandOption.Double
	(TUI.class, "hyperbolic-sharpness", "DECIMAL", true, 10.0,
	 "The hyperbolic prior sharpness used for training.", null);

	static CommandOption.File crfInputFileOption = new CommandOption.File
	(TUI.class, "crf-input-file", "FILENAME", true, null,
	 "The name of the file to write the CRF after training.", null);

	static CommandOption.Integer randomSeedOption = new CommandOption.Integer
	(TUI.class, "random-seed", "INTEGER", true, 0,
	 "The random seed for randomly selecting a proportion of the instance list for training", null);

	static CommandOption.Integer labelGramOption = new CommandOption.Integer
	(TUI.class, "label-gram", "INTEGER", true, 1,
	 "Markov order of labels: 1, 2, 3", null);

	static CommandOption.Integer wordWindowFeatureOption = new CommandOption.Integer
	(TUI.class, "word-window-size", "INTEGER", true, 0,
	 "Size of window of words as features: 0=none, 10, 20...", null);

	static CommandOption.Boolean useTestbOption = new CommandOption.Boolean
	(TUI.class, "use-testb", "true|false", true, false,
	 "Use testb, final test set", null);
	
	static CommandOption.Boolean useHyperbolicPriorOption = new CommandOption.Boolean
	(TUI.class, "use-hyperbolic-prior", "true|false", true, false,
	 "Use hyperbolic prior", null);

	static CommandOption.Boolean useFeatureInductionOption = new CommandOption.Boolean
	(TUI.class, "use-feature-induction", "true|false", true, false,
	 "Not use or use feature induction", null);

	static CommandOption.Boolean clusterFeatureInductionOption = new CommandOption.Boolean
	(TUI.class, "cluster-feature-induction", "true|false", true, false,
	 "Cluster in feature induction", null);

	static CommandOption.Boolean useFirstMentionFeatureOption = new CommandOption.Boolean
	(TUI.class, "use-firstmention-feature", "true|false", true, false,
	 "Don't use first-mention feature", null);

	static CommandOption.Boolean useDocHeaderFeatureOption = new CommandOption.Boolean
	(TUI.class, "use-docheader-feature", "true|false", true, false,
	 "", null);

	static CommandOption.Boolean includeConllLexiconsOption = new CommandOption.Boolean
	(TUI.class, "include-conll-lexicons", "true|false", true, false,
	 "", null);

	static CommandOption.Boolean charNGramsOption = new CommandOption.Boolean
	(TUI.class, "char-ngrams", "true|false", true, false,
	 "", null);
	
	static CommandOption.String offsetsOption = new CommandOption.String
	(TUI.class, "offsets", "e.g. [[0,0],[1]]", true, "[[-2],[-1],[1],[2]]", 
	 "Offset conjunctions", null);

	static CommandOption.String capOffsetsOption = new CommandOption.String
	(TUI.class, "cap-offsets", "e.g. [[0,0],[0,1]]", true, "", 
	 "Offset conjunctions applied to features that are [A-Z]*", null);

	static CommandOption.String viterbiFilePrefixOption = new CommandOption.String
	(TUI.class, "viterbi-file", "FILE", true, "TUI", 
	 "Filename in which to store most recent Viterbi output", null);
	

	static final CommandOption.List commandOptions =
	new CommandOption.List (
		"Training, testing and running a Chinese word segmenter.",
		new CommandOption[] {
			gaussianVarianceOption,
			hyperbolicSlopeOption,
			hyperbolicSharpnessOption,
			randomSeedOption,
			labelGramOption,
			wordWindowFeatureOption,
			useHyperbolicPriorOption,
			useFeatureInductionOption,
			clusterFeatureInductionOption,
			useFirstMentionFeatureOption,
			useDocHeaderFeatureOption,
			includeConllLexiconsOption,
			offsetsOption,
			capOffsetsOption,
			viterbiFilePrefixOption,
			useTestbOption,
		});

	
	int numEvaluations = 0;
	static int iterationsBetweenEvals = 16;
	static boolean doingFeatureInduction = true;
	static boolean doingClusteredFeatureInduction = false;

	private static String CAPS = "[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜ]";
	private static String LOW = "[a-zàèìòùáéíóúçñïü]";
	private static String CAPSNUM = "[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜ0-9]";
	private static String ALPHA = "[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜa-zàèìòùáéíóúçñïü]";
	private static String ALPHANUM = "[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜa-zàèìòùáéíóúçñïü0-9]";
	private static String PUNT = "[,\\.;:?!()]";
	private static String QUOTE = "[\"`']";
	
	public static void main (String[] args) throws FileNotFoundException, Exception
	{
		commandOptions.process (args);
		String homedir = System.getProperty ("HOME");
		String lexdir = homedir+"/research/data/resources/";

		String offsetsString = offsetsOption.value.replace('[','{').replace(']','}');
		int[][] offsets = (int[][]) CommandOption.getInterpreter().eval ("new int[][] "+offsetsString);

		String capOffsetsString = capOffsetsOption.value.replace('[','{').replace(']','}');
		int[][] capOffsets = null;
		if (capOffsetsString.length() > 0)
			capOffsets = (int[][]) CommandOption.getInterpreter().eval ("new int[][] "+capOffsetsString);

		Pipe conllLexiconsPipe = null;
		if (includeConllLexiconsOption.value)
			conllLexiconsPipe = new SerialPipes (new Pipe[] {
				new TrieLexiconMembership (new File(lexdir + "conll/CONLLTWOPER")),
				new TrieLexiconMembership (new File(lexdir + "conll/CONLLTWOLOC")),
				new TrieLexiconMembership (new File(lexdir + "conll/CONLLTWOORG")),
				new TrieLexiconMembership (new File(lexdir + "conll/CONLLTWOMISC")),
			});
		
		Pipe p = new SerialPipes (new Pipe[] {
			new ConllNer2003Sentence2TokenSequence (),
			new RegexMatches ("INITCAP", Pattern.compile (CAPS+".*")),
			new RegexMatches ("CAPITALIZED", Pattern.compile (CAPS+LOW+"*")),
			new RegexMatches ("ALLCAPS", Pattern.compile (CAPS+"+")),
			new RegexMatches ("MIXEDCAPS", Pattern.compile ("[A-Z][a-z]+[A-Z][A-Za-z]*")),
			new RegexMatches ("CONTAINSDIGITS", Pattern.compile (".*[0-9].*")),
			new RegexMatches ("ALLDIGITS", Pattern.compile ("[0-9]+")),
			new RegexMatches ("NUMERICAL", Pattern.compile ("[-0-9]+[\\.,]+[0-9\\.,]+")),
			//new RegexMatches ("ALPHNUMERIC", Pattern.compile ("[A-Za-z0-9]+")),
			//new RegexMatches ("ROMAN", Pattern.compile ("[ivxdlcm]+|[IVXDLCM]+")),
			new RegexMatches ("MULTIDOTS", Pattern.compile ("\\.\\.+")),
			new RegexMatches ("ENDSINDOT", Pattern.compile ("[^\\.]+.*\\.")),
			new RegexMatches ("CONTAINSDASH", Pattern.compile (ALPHANUM+"+-"+ALPHANUM+"*")),
			new RegexMatches ("ACRO", Pattern.compile ("[A-Z][A-Z\\.]*\\.[A-Z\\.]*")),
			new RegexMatches ("LONELYINITIAL", Pattern.compile (CAPS+"\\.")),
			new RegexMatches ("SINGLECHAR", Pattern.compile (ALPHA)),
			new RegexMatches ("CAPLETTER", Pattern.compile ("[A-Z]")),
			new RegexMatches ("PUNC", Pattern.compile (PUNT)),
			new RegexMatches ("QUOTE", Pattern.compile (QUOTE)),
			//new RegexMatches ("LOWER", Pattern.compile (LOW+"+")),
			//new RegexMatches ("MIXEDCAPS", Pattern.compile ("[A-Z]+[a-z]+[A-Z]+[a-z]*")),

			(includeConllLexiconsOption.value ? conllLexiconsPipe : new Noop ()),

			// Note that the word has not been lowecased!  so INITCAP, etc, is redundant
			//new TokenSequenceLowercase (),
			new TokenText ("W="),
			//new TokenSequenceFirstSentenceAllCaps (),

			new OffsetConjunctions (offsets),
			(capOffsets != null ? (Pipe) new OffsetConjunctions (capOffsets) : (Pipe) new Noop ()),

			//// Don't lowercase the W= if you want to use this.
			(!useFirstMentionFeatureOption.value
			 ? (Pipe) new Noop ()
			 : (Pipe) new FeaturesOfFirstMention ("FIRSTMENTION=", Pattern.compile (CAPS+".*"),
																						// Exclude singleton W=foo features b/c redundant
																						Pattern.compile ("W=[^@&]+"), false)),
			(!useDocHeaderFeatureOption.value	? (Pipe) new Noop () : (Pipe) new TokenSequenceDocHeader ()),
			
			(wordWindowFeatureOption.value > 0
			 ? (Pipe) new FeaturesInWindow ("WINDOW=", -wordWindowFeatureOption.value,
																			wordWindowFeatureOption.value,	Pattern.compile ("WORD=.*"), true)
			 : (Pipe) new Noop()),
			(charNGramsOption.value
			 ? (Pipe) new TokenTextCharNGrams ("CHARNGRAM=", new int[] {2,3,4})
			 : (Pipe) new Noop()),

			new PrintTokenSequenceFeatures(),
			new TokenSequence2FeatureVectorSequence (true, true)
		});


		// Set up training and testing data
		//args = new String[] {homedir+"/research/data/ie/ner2003/eng.testa"};
		if (useTestbOption.value)
			args = new String[] {homedir+"/research/data/ie/ner2003/eng.train",
													 homedir+"/research/data/ie/ner2003/eng.testb"};
		else
			args = new String[] {homedir+"/research/data/ie/ner2003/eng.train",
													 homedir+"/research/data/ie/ner2003/eng.testa"};

		InstanceList trainingData = new InstanceList (p);
		trainingData.addThruPipe (new LineGroupIterator (new FileReader (new File (args[0])),
																						 Pattern.compile("^.DOCSTART. .X. .X. .$"), true));
		System.out.println ("Read "+trainingData.size()+" training instances");
		
		InstanceList testingData = null;
		if (args.length > 1) {
			testingData = new InstanceList (p);
			testingData.addThruPipe (new LineGroupIterator (new FileReader (new File (args[1])),
																							Pattern.compile("^.DOCSTART. .X. .X. .$"), true));
		}

		if (testingData == null) {
			// For now, just train on a small fraction of the data
			Random r = new Random (1);
			// Proportions below is: {training, testing, ignore}
			InstanceList[] trainingLists = trainingData.split (r, new double[] {.2, .1, .7});
			trainingData = trainingLists[0];
			// and test on just 50% of the data
			if (testingData != null) {
				InstanceList[] testingLists = testingData.split (r, new double[] {.5, .5});
				testingData = testingLists[0];
				testingLists = null;
			} else {
				testingData = trainingLists[1];
			}
			trainingLists = null;
			assert (testingData != null);
		}

		// Print out all the target names
		Alphabet targets = p.getTargetAlphabet();
		System.out.print ("State labels:");
		for (int i = 0; i < targets.size(); i++)
			System.out.print (" " + targets.lookupObject(i));
		System.out.println ("");

		// Print out some feature information
		System.out.println ("Number of features = "+p.getDataAlphabet().size());

		CRF crf = new CRF (p, null);
		if (labelGramOption.value == 1)
			crf.addStatesForLabelsConnectedAsIn (trainingData);
		else if (labelGramOption.value == 2)
			crf.addStatesForBiLabelsConnectedAsIn (trainingData);
		//else if (labelGramOption.value == 3)
		//crf.addStatesForTriLabelsConnectedAsIn (trainingData);
		else
			throw new IllegalStateException ("label-gram must be 1, 2, or 3, not "+ labelGramOption.value);
		CRFTrainerByLikelihood crft = new CRFTrainerByLikelihood (crf);		
		
		if (useHyperbolicPriorOption.value) {
			crft.setUseHyperbolicPrior (true);
			crft.setHyperbolicPriorSlope (hyperbolicSlopeOption.value);
			crft.setHyperbolicPriorSharpness (hyperbolicSharpnessOption.value);
		} else {
			crft.setGaussianPriorVariance (gaussianVarianceOption.value);
		}
		for (int i = 0; i < crf.numStates(); i++) {
			Transducer.State s = crf.getState (i);
			if (s.getName().charAt(0) == 'I')
				s.setInitialWeight (Double.POSITIVE_INFINITY);
		}

		System.out.println("Training on "+trainingData.size()+" training instances, "+
											 testingData.size()+" testing instances...");
		MultiSegmentationEvaluator eval =
			new MultiSegmentationEvaluator (new InstanceList[] {trainingData, testingData},
					new String[] {"Training", "Testing"},
					new String[] {"B-PER", "B-LOC", "B-ORG", "B-MISC"},
					new String[] {"I-PER", "I-LOC", "I-ORG", "I-MISC"});
		ViterbiWriter vw = new ViterbiWriter (viterbiFilePrefixOption.value,
				new InstanceList[] {trainingData, testingData}, new String[] {"Training", "Testing"});
			
		if (useFeatureInductionOption.value) {
			if (clusterFeatureInductionOption.value)
				crft.trainWithFeatureInduction (trainingData, null, testingData,
																			 eval, 99999,
																			 10, 99, 200, 0.5, true,
																			 new double[] {.1, .2, .5, .7});
			else
				crft.trainWithFeatureInduction (trainingData, null, testingData,
																			 eval, 99999,
																			 10, 99, 1000, 0.5, false,
																			 new double[] {.1, .2, .5, .7});
		}
		else {
			double[] trainingProportions = new double[] {.1, .2, .5, .7};
			for (int i = 0; i < trainingProportions.length; i++) {
				crft.train(trainingData, 3, new double[] {trainingProportions[i]});
				eval.evaluate(crft);
				vw.evaluate(crft);
			}
			while (crft.train(trainingData, 3)) {
				eval.evaluate(crft);
				vw.evaluate(crft);
			}
			eval.evaluate(crft);
			vw.evaluate(crft);
		}
	}

	
}
