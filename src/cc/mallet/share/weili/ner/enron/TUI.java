/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Wei Li <a href="mailto:weili@cs.umass.edu">weili@cs.umass.edu</a>
 */

package cc.mallet.share.weili.ner.enron;

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
import cc.mallet.share.upenn.ner.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

public class TUI
{

    private static String CAPS = "[\\p{Lu}]";
    private static String LOW = "[\\p{Ll}]";
    private static String CAPSNUM = "[\\p{Lu}\\p{Nd}]";
    private static String ALPHA = "[\\p{Lu}\\p{Ll}]";
    private static String ALPHANUM = "[\\p{Lu}\\p{Ll}\\p{Nd}]";
	private static String PUNT = "[,\\.;:?!()]";
	private static String QUOTE = "[\"`']";

	public static void main(String[] args) throws IOException {
		String datadir = "/usr/can/tmp3/weili/NER/Enron/data";
		String conlllexdir = "/usr/col/tmp1/weili/Resource/conllDict/";
		String idflexdir = "/usr/col/tmp1/weili/Resource/idfDict/";
		String placelexdir = "/usr/col/tmp1/weili/Resource/places";

		Pipe conllLexiconsPipe = new SerialPipes (new Pipe[] {
			new TrieLexiconMembership (new File(conlllexdir + "conll/CONLLTWOPER")),
			new TrieLexiconMembership (new File(conlllexdir + "conll/CONLLTWOLOC")),
			new TrieLexiconMembership (new File(conlllexdir + "conll/CONLLTWOORG")),
			new TrieLexiconMembership (new File(conlllexdir + "conll/CONLLTWOMISC")),
		});
		
		Pipe googleLexiconsPipe = new SerialPipes (new Pipe[] {
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGSOCCER")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGGOVT")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGNGO")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGMILITARY")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGCOMPANY")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGBANK")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGTRADE")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGNEWS")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGOPERATINGSYSTEM")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGPOLITICALPARTY")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGTRAVEL")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGBASEBALLTEAMAUGF")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGCARMODEL")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGCARCOMPANY")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGENGLISHCOUNTYAUG")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGUNIVERSITY")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCNATIONALITYAUGF")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCDISEASEAUG")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCTIME")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCAWARDS")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCMOVIESAUGF")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCPOLITICALPARTY")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCRELIGION")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCGOVT")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCWAR")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCCURRENCY")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/LOC")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/PERFL")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/MISCF")),
			new TrieLexiconMembership (new File(conlllexdir + "googlesets/ORGFRAWEDITEDSORTED")),
		});

		Pipe fixedLexiconsPipe = new SerialPipes (new Pipe[] {
			new LexiconMembership ("FIRSTHIGHEST", new File(conlllexdir + "personname/ssdi.prfirsthighest"), true),
			new LexiconMembership ("FIRSTHIGH", new File(conlllexdir + "personname/ssdi.prfirsthigh"), true),
			new LexiconMembership ("FIRSTMED", new File(conlllexdir + "personname/ssdi.prfirstmed"), true),
			new LexiconMembership ("FIRSTLOW", new File(conlllexdir + "personname/ssdi.prfirstlow"), true),
			new LexiconMembership ("LASTHIGHEST", new File(conlllexdir + "personname/ssdi.prlasthighest"), true),
			new LexiconMembership ("LASTHIGH", new File(conlllexdir + "personname/ssdi.prlasthigh"), true),
			new LexiconMembership ("LASTMED", new File(conlllexdir + "personname/ssdi.prlastmed"), true),
			new LexiconMembership ("LASTLOW", new File(conlllexdir + "personname/ssdi.prlastlow"), true),
			new LexiconMembership ("HONORIFIC", new File(conlllexdir + "personname/honorifics"), true),
			new LexiconMembership ("NAMESUFFIX", new File(conlllexdir + "personname/namesuffixes"), true),
			new LexiconMembership ("NAMEPARTICLE", new File(conlllexdir + "personname/name-particles"), true),
			new LexiconMembership ("DAY", new File(conlllexdir + "days"), true),
			new LexiconMembership ("MONTH", new File(conlllexdir + "months"), true),
			new LexiconMembership ("PLACESUFFIX", new File(conlllexdir + "place-suffixes"), true),
			new TrieLexiconMembership ("COUNTRY", new File(conlllexdir + "countries"), true),
			new TrieLexiconMembership ("COUNTRYCAPITAL", new File(conlllexdir + "country-capitals"), true),
			new TrieLexiconMembership ("USSTATE", new File(conlllexdir + "US-states"), true),
			new TrieLexiconMembership ("COMPANYNAME", new File(conlllexdir + "company-names"), true),
			new TrieLexiconMembership ("COMPANYSUFFIX", new File(conlllexdir + "company-suffixes"), true),
			new TrieLexiconMembership ("CONTINENT", new File(conlllexdir + "continents"), true),
			new LexiconMembership ("STOPWORD", new File(conlllexdir + "stopwords"), true),
			new TrieLexiconMembership (new File(conlllexdir + "biz.yahoo/COMPANYNAME.ABBREV")),
			new TrieLexiconMembership (new File(conlllexdir + "utexas/UNIVERSITIES")),
		});

		Pipe idfLexiconsPipe = new SerialPipes (new Pipe[] {
			new TrieLexiconMembership ("IDF_DES", new File(idflexdir + "designator.data"), true),
			new TrieLexiconMembership ("IDF_FIR", new File(idflexdir + "firstnames.data"), true),
			new TrieLexiconMembership ("IDF_LOC", new File(idflexdir + "locations.data"), true),
			new TrieLexiconMembership ("IDF_NAT", new File(idflexdir + "nations.data"), true),
			new TrieLexiconMembership ("IDF_ABB", new File(idflexdir + "non-final-abbrevs.data"), true),
			new TrieLexiconMembership ("IDF_ORG", new File(idflexdir + "organization.data"), true),
			new TrieLexiconMembership ("IDF_PER", new File(idflexdir + "person.data"), true),
		});

		Pipe spellingFeaturesPipe = new SerialPipes (new Pipe[] {
			new RegexMatches ("INITCAP", Pattern.compile (CAPS+".*")),
			new RegexMatches ("CAPITALIZED", Pattern.compile (CAPS+LOW+"*")),
			new RegexMatches ("ALLCAPS", Pattern.compile (CAPS+"+")),
			new RegexMatches ("MIXEDCAPS", Pattern.compile ("[A-Z][a-z]+[A-Z][A-Za-z]*")),
			new RegexMatches ("CONTAINSDIGITS", Pattern.compile (".*[0-9].*")),
			new RegexMatches ("ALLDIGITS", Pattern.compile ("[0-9]+")),
			new RegexMatches ("NUMERICAL", Pattern.compile ("[-0-9]+[\\.,]+[0-9\\.,]+")),
			new RegexMatches ("MULTIDOTS", Pattern.compile ("\\.\\.+")),
			new RegexMatches ("ENDSINDOT", Pattern.compile ("[^\\.]+.*\\.")),
			new RegexMatches ("CONTAINSDASH", Pattern.compile (ALPHANUM+"+-"+ALPHANUM+"*")),
			new RegexMatches ("ACRO", Pattern.compile ("[A-Z][A-Z\\.]*\\.[A-Z\\.]*")),
			new RegexMatches ("LONELYINITIAL", Pattern.compile (CAPS+"\\.")),
			new RegexMatches ("SINGLECHAR", Pattern.compile (ALPHA)),
			new RegexMatches ("CAPLETTER", Pattern.compile ("[A-Z]")),
			new RegexMatches ("PUNC", Pattern.compile (PUNT)),
			new RegexMatches ("QUOTE", Pattern.compile (QUOTE)),
		});

		SerialPipes p = new SerialPipes (new Pipe[] {
			new EnronMessage2TokenSequence (),
			
			//original
			//new TokenText("W="),
			//spellingFeaturesPipe,

      new NEPipes(new File(placelexdir)),
			conllLexiconsPipe,
			googleLexiconsPipe,
			fixedLexiconsPipe,
			idfLexiconsPipe,
			new OffsetConjunctions (new int[][]{{-1},{1}}),
			new PrintTokenSequenceFeatures(),
			new TokenSequence2FeatureVectorSequence (true, true)
		});

		InstanceList ilist = new InstanceList (p);
		ilist.addThruPipe (new FileIterator (datadir, FileIterator.STARTING_DIRECTORIES));
		Random r = new Random (1);
		InstanceList[] ilists = ilist.split (r, new double[] {0.8, 0.2});
		
		Alphabet targets = p.getTargetAlphabet();
		System.out.print ("State labels:");
		for (int i = 0; i < targets.size(); i++)
			System.out.print (" " + targets.lookupObject(i));
		System.out.println ("");
		System.out.println ("Number of features = "+p.getDataAlphabet().size());

		CRF crf = new CRF (p, null);
		crf.addStatesForThreeQuarterLabelsConnectedAsIn (ilists[0]);
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		crft.setGaussianPriorVariance (100.0);
		
		for (int i = 0; i < crf.numStates(); i++)
			crf.getState(i).setInitialWeight (Transducer.IMPOSSIBLE_WEIGHT);
		crf.getState("O").setInitialWeight(0.0);

		System.out.println("Training on "+ilists[0].size()+" training instances.");

		MultiSegmentationEvaluator eval =
			new MultiSegmentationEvaluator (new InstanceList[] {ilists[0], ilists[1]},
					new String[] {"train", "test"},
					new String[] {"B-DATE", "B-TIME", "B-LOCATION", "B-PERSON",
					"B-ORGANIZATION", "B-ACRONYM", "B-PHONE", "B-MONEY", "B-PERCENT"},
					new String[] {"I-DATE", "I-TIME", "I-LOCATION", "I-PERSON",
					"I-ORGANIZATION", "I-ACRONYM", "I-PHONE", "I-MONEY", "I-PERCENT"});
		if (args[0].equals("FeatureInduction"))
			throw new IllegalStateException ("Feature induction not yet supported.");
			/* crf.trainWithFeatureInduction (ilists[0], null, ilists[1],
																		 eval, 99999,
																		 10, 60, 500, 0.5, false,
																		 new double[] {.1, .2, .5, .7}); */
		else if (args[0].equals("NoFeatureInduction")) {
			crft.train (ilists[0], 5, new double[] {.1, .2, .5, .7});
			while (!crft.trainIncremental(ilists[0])) {
				eval.evaluate(crft);
				if (crft.getIteration() % 5 == 0)
					new ViterbiWriter (args[2], ilists[0], "train", ilists[1], "test");
			}
		} else {
			System.err.println("Feature induction or not? Give me a choice.");
			System.exit(1);
		}
		crf.write(new File(args[1]));
	}
}
