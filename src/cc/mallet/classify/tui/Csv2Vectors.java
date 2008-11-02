/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify.tui;

import java.util.logging.*;
import java.util.regex.*;
import java.io.*;

import cc.mallet.classify.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
/**
 * Convert comma-separated-value files into vectors (persistent instance list).
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Csv2Vectors
{
	private static Logger logger = MalletLogger.getLogger(Csv2Vectors.class.getName());

	static CommandOption.File inputFile =	new CommandOption.File
	(Csv2Vectors.class, "input", "FILE", true, null,
	 "The file containing data to be classified, one instance per line", null);

	static CommandOption.File outputFile = new CommandOption.File
	(Csv2Vectors.class, "output", "FILE", true, new File("text.vectors"),
	 "Write the instance list to this file; Using - indicates stdout.", null);

	static CommandOption.String lineRegex = new CommandOption.String
	(Csv2Vectors.class, "line-regex", "REGEX", true, "^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$",
	 "Regular expression containing regex-groups for label, name and data.", null);
	
	static CommandOption.Integer labelOption = new CommandOption.Integer
	(Csv2Vectors.class, "label", "INTEGER", true, 2,
	 "The index of the group containing the label string.", null);

	static CommandOption.Integer nameOption = new CommandOption.Integer
	(Csv2Vectors.class, "name", "INTEGER", true, 1,
	 "The index of the group containing the instance name.", null);

	static CommandOption.Integer dataOption = new CommandOption.Integer
	(Csv2Vectors.class, "data", "INTEGER", true, 3,
	 "The index of the group containing the data.", null);
	
	static CommandOption.File usePipeFromVectorsFile = new CommandOption.File
	(Csv2Vectors.class, "use-pipe-from", "FILE", true, new File("text.vectors"),
	 "Use the pipe and alphabets from a previously created vectors file. " +
	 "Allows the creation, for example, of a test set of vectors that are" +
	 "compatible with a previously created set of training vectors", null);

        static CommandOption.Boolean keepSequence = new CommandOption.Boolean
	    (Csv2Vectors.class, "keep-sequence", "[TRUE|FALSE]", false, false,
	     "If true, final data will be a FeatureSequence rather than a FeatureVector.", null);

         static CommandOption.Boolean keepSequenceBigrams = new CommandOption.Boolean
	    (Csv2Vectors.class, "keep-sequence-bigrams", "[TRUE|FALSE]", false, false,
	 "If true, final data will be a FeatureSequenceWithBigrams rather than a FeatureVector.", null);
    

        static CommandOption.Boolean removeStopWords = new CommandOption.Boolean
	    (Csv2Vectors.class, "remove-stopwords", "[TRUE|FALSE]", false, false,
	     "If true, remove common \"stop words\" from the text.", null);

        static CommandOption.Boolean preserveCase = new CommandOption.Boolean
            (Csv2Vectors.class, "preserve-case", "[TRUE|FALSE]", false, false,
	     "If true, do not force all strings to lowercase.", null);
    
	
	public static void main (String[] args) throws FileNotFoundException, IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Csv2Vectors.class,
	  "A tool for creating instance lists of feature vectors from comma-separated-values");
		CommandOption.process (Csv2Vectors.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(Csv2Vectors.class).printUsage(false);
			System.exit (-1);
		}
		if (inputFile == null) {
			System.err.println ("You must include `--input FILE ...' in order to specify a"+
					    "file containing the instances, one per line.");
			System.exit (-1);
		}
		
		Pipe instancePipe;
		InstanceList previousInstanceList = null;
		
		if (!usePipeFromVectorsFile.wasInvoked()) {
			instancePipe = new SerialPipes (new Pipe[] {
				new Target2Label (),
				(keepSequenceBigrams.value
				 ? (Pipe) new CharSequence2TokenSequence(CharSequenceLexer.LEX_NONWHITESPACE_CLASSES)
				 : (Pipe) new CharSequence2TokenSequence(CharSequenceLexer.LEX_WORD_CLASSES)),
				(preserveCase.value
				 ? (Pipe) new Noop()
				 : (Pipe) new TokenSequenceLowercase()),
				(keepSequenceBigrams.value
				 ? (Pipe) new TokenSequenceRemoveNonAlpha(true)
				 : (Pipe) new Noop()),
				 new PrintInput(),
				(removeStopWords.value
				 ? (Pipe) new TokenSequenceRemoveStopwords(false, keepSequenceBigrams.value)
				 : (Pipe) new Noop()),
				(keepSequenceBigrams.value
				 ? (Pipe) new TokenSequence2FeatureSequenceWithBigrams()
				 : (Pipe) new TokenSequence2FeatureSequence()),
		        (keepSequence.value || keepSequenceBigrams.value
				 ? (Pipe) new Noop()
				 : (Pipe) new FeatureSequence2AugmentableFeatureVector()), 
				// or FeatureSequence2FeatureVector
				//new PrintInputAndTarget ()
			});
		}
		else {
			previousInstanceList = InstanceList.load (usePipeFromVectorsFile.value);
			instancePipe = previousInstanceList.getPipe();			
		}
		InstanceList ilist = new InstanceList (instancePipe);
		Reader fileReader;
		if (inputFile.value.toString().equals ("-"))
		    fileReader = new InputStreamReader (System.in);
		else
		    fileReader = new FileReader (inputFile.value);

		ilist.addThruPipe (new CsvIterator (fileReader, Pattern.compile(lineRegex.value),
					    dataOption.value, labelOption.value, nameOption.value));
		
		ObjectOutputStream oos;
		if (outputFile.value.toString().equals ("-"))
			oos = new ObjectOutputStream(System.out);
		else
			oos = new ObjectOutputStream(new FileOutputStream(outputFile.value));
		oos.writeObject(ilist);
		oos.close();	 	
		// *rewrite* vector file used as source of pipe in case we changed the alphabet(!)
		if (usePipeFromVectorsFile.wasInvoked()){
			System.out.println(" output usepipe ilist pipe instance id =" + previousInstanceList.getPipe().getInstanceId());
			oos = new ObjectOutputStream(new FileOutputStream(usePipeFromVectorsFile.value));
			oos.writeObject(previousInstanceList);
			oos.close();
		}
	}
}

    

