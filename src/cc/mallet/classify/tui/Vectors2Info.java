/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify.tui;

import java.util.logging.*;
import java.io.*;

import cc.mallet.classify.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
/**
 * Diagnostic facilities for a vector file.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Vectors2Info
{
	private static Logger logger = MalletLogger.getLogger(Vectors2Info.class.getName());

	static CommandOption.File inputFile = new CommandOption.File
	(Vectors2Info.class, "input", "FILE", true, new File("-"),
	 "Read the instance list from this file; Using - indicates stdin.", null);

	static CommandOption.Integer printInfogain = new CommandOption.Integer
	(Vectors2Info.class, "print-infogain", "N", false, 0,
	 "Print top N words by information gain, sorted.", null);

	static CommandOption.Boolean printLabels = new CommandOption.Boolean
	(Vectors2Info.class, "print-labels", "[TRUE|FALSE]", false, false,
	 "Print class labels known to instance list, one per line.", null);

	static CommandOption.String printMatrix = new CommandOption.String
	(Vectors2Info.class, "print-matrix", "STRING", false, "sic",
	 "Print word/document matrix in the specified format (a|s)(b|i)(n|w|c|e)", null)
	{
		public void parseArg(java.lang.String arg)
		{

			if (arg == null) arg = this.defaultValue;
			//System.out.println("pa arg=" + arg);

			// sanity check the raw printing options (a la Rainbow)
			char c0 = arg.charAt(0);
			char c1 = arg.charAt(1);
			char c2 = arg.charAt(2);

			if (arg.length() != 3 ||
			        (c0 != 's' && c0 != 'a') ||
			        (c1 != 'b' && c1 != 'i') ||
			        (c2 != 'n' && c2 != 'w' && c2 != 'c' && c2 != 'e')) {
				throw new IllegalArgumentException("Illegal argument = " + arg + " in --print-matrix=" +arg);
			}

			value = arg;
		}
	};


	public static void main (String[] args) throws FileNotFoundException, IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Vectors2Info.class,
		"A tool for printing information about instance lists of feature vectors.");
		CommandOption.process (Vectors2Info.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(Vectors2Info.class).printUsage(false);
			System.exit (-1);
		}
		if (false && !inputFile.wasInvoked()) {
			System.err.println ("You must specify an input instance list, with --input.");
			System.exit (-1);
		}

		// Read the InstanceList
		InstanceList ilist = InstanceList.load (inputFile.value);

		if (printLabels.value) {
			Alphabet la = ilist.getTargetAlphabet ();
			for (int i = 0; i < la.size(); i++)
				System.out.println (la.lookupObject (i));
			System.out.print ("\n");
		}

		if (printInfogain.value > 0) {
			InfoGain ig = new InfoGain (ilist);
			for (int i = 0; i < printInfogain.value; i++)
				System.out.println (""+i+" "+ig.getObjectAtRank(i));
			System.out.print ("\n");
		}

		if (printMatrix.wasInvoked()){
			 printInstanceList(ilist, printMatrix.value);

		}

	}

	/* print an instance list according to the format string */
	private static void printInstanceList(InstanceList ilist, String formatString)
	{
		//private static double[] calcFeatureCounts (InstanceList ilist)  from FeatureCounts
		//System.out.println("PIL formatString " + formatString);
		int numInstances = ilist.size();
		int numClasses = ilist.getTargetAlphabet().size();
		int numFeatures = ilist.getDataAlphabet().size();
		Alphabet dataAlphabet = ilist.getDataAlphabet();
		double[] counts = new double[numFeatures];
		double count;
		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			if (!(inst.getData() instanceof FeatureVector))
				throw new IllegalArgumentException ("Currently only handles FeatureVector data");
			FeatureVector fv = (FeatureVector) inst.getData ();
			//if (ilist.getInstanceWeight(i) == 0)
			//	continue;
			System.out.print(inst.getName() + " " + inst.getTarget());
			if (formatString.charAt(0) == 'a'){  // all features
				for (int fvi=0; fvi<numFeatures; fvi++){
					printFeature(dataAlphabet.lookupObject(fvi), fvi,  fv.value(fvi), formatString);
				}
			} else{  // sparse; only features present in vector
				for (int l = 0; l < fv.numLocations(); l++) {
					int fvi = fv.indexAtLocation(l);
					printFeature(dataAlphabet.lookupObject(fvi), fvi, fv.valueAtLocation(l), formatString);
					//System.out.print(" " + dataAlphabet.lookupObject(j) + " " + ((int) fv.valueAtLocation(j)));
				}
			}

			System.out.println();
		}

		System.out.println();

		return; // counts;
	}

	/* helper for printInstanceList. prints a single feature within an instance */
	private static void printFeature(Object o, int fvi, double featureValue, String formatString) {
		// print object  n,w,c,e
		char c1 = formatString.charAt(2);
		if (c1 == 'w') {    // word
			System.out.print("  " + o);
		} else if (c1 == 'n') {   // index of word
			System.out.print("  " + fvi);
		} else if (c1 == 'c') { //word and index
			System.out.print("  " + o + ":" + fvi);
		} else if (c1 == 'e'){ //no word identity
		}

		char c2 = formatString.charAt(1);
		if (c2 == 'i') {    // integer count
			System.out.print(" " + ((int)(featureValue + .5)));
		} else if (c2 == 'b') {   // boolean present/not present
			System.out.print(" " + ((featureValue>0.5) ? "1" : "0"));
		}

	}


}
