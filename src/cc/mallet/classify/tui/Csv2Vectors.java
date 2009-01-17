/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.tui;

import java.util.ArrayList;
import java.util.logging.*;
import java.util.regex.*;
import java.io.*;
import java.nio.charset.Charset;

import cc.mallet.classify.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

/**
 * Command line import tool for loading a sequence of 
 *  instances from a single file, with one instance 
 *  per line of the input file. 
 *  <p>
 * Despite the name of the class, input data does not
 *  have to be comma-separated, and instance data can 
 *  remain sequences (rather than unordered vectors).
 * 
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Csv2Vectors {

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
		 "The index of the group containing the label string.\n" +
		 "   Use 0 to indicate that the label field is not used.", null);

	static CommandOption.Integer nameOption = new CommandOption.Integer
		(Csv2Vectors.class, "name", "INTEGER", true, 1,
		 "The index of the group containing the instance name.\n" +
         "   Use 0 to indicate that the name field is not used.", null);

	static CommandOption.Integer dataOption = new CommandOption.Integer
		(Csv2Vectors.class, "data", "INTEGER", true, 3,
		 "The index of the group containing the data.", null);
	
	static CommandOption.File usePipeFromVectorsFile = new CommandOption.File
		(Csv2Vectors.class, "use-pipe-from", "FILE", true, new File("text.vectors"),
		 "Use the pipe and alphabets from a previously created vectors file.\n" +
		 "   Allows the creation, for example, of a test set of vectors that are\n" +
		 "   compatible with a previously created set of training vectors", null);

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
    
	static CommandOption.String encoding = new CommandOption.String
		(Csv2Vectors.class, "encoding", "STRING", true, Charset.defaultCharset().displayName(),
		 "Character encoding for input file", null);

	static CommandOption.String tokenRegex = new CommandOption.String
		(Csv2Vectors.class, "token-regex", "REGEX", true, CharSequenceLexer.LEX_ALPHA.toString(),
		 "Regular expression used for tokenization.\n" +
		 "   Example: \"[\\p{L}\\p{N}_]+|[\\p{P}]+\" (unicode letters, numbers and underscore OR all punctuation) ", null);

	static CommandOption.Boolean printOutput = new CommandOption.Boolean
		(Csv2Vectors.class, "print-output", "[TRUE|FALSE]", false, false,
		 "If true, print a representation of the processed data\n" +
		 "   to standard output. This option is intended for debugging.", null);


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
				 : (Pipe) new CharSequence2TokenSequence(CharSequenceLexer.LEX_NONWHITESPACE_TOGETHER)),
				(preserveCase.value
				 ? (Pipe) new Noop()
				 : (Pipe) new TokenSequenceLowercase()),
				(keepSequenceBigrams.value
				 ? (Pipe) new TokenSequenceRemoveNonAlpha(true)
				 : (Pipe) new Noop()),
				 //new PrintInput(),
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


			// Ignore all options, use a previously created pipe

			previousInstanceList = InstanceList.load (usePipeFromVectorsFile.value);
			instancePipe = previousInstanceList.getPipe();			
		}

		//
		// Create the instance list and open the input file
		// 

		InstanceList instances = new InstanceList (instancePipe);
		Reader fileReader;

		if (inputFile.value.toString().equals ("-")) {
		    fileReader = new InputStreamReader (System.in);
		}
		else {
			fileReader = new InputStreamReader(new FileInputStream(inputFile.value), encoding.value);
		}

		// 
		// Read instances from the file
		//

		instances.addThruPipe (new CsvIterator (fileReader, Pattern.compile(lineRegex.value),
												dataOption.value, labelOption.value, nameOption.value));
		
		// 
		// Save instances to output file
		//

		ObjectOutputStream oos;
		if (outputFile.value.toString().equals ("-")) {
			oos = new ObjectOutputStream(System.out);
		}
		else {
			oos = new ObjectOutputStream(new FileOutputStream(outputFile.value));
		}
		oos.writeObject(instances);
		oos.close();


		// If we are reusing a pipe from an instance list 
		//  created earlier, we may have extended the label
		//  or feature alphabets. To maintain compatibility,
		//  we now save that original instance list back to disk
		//  with the new alphabet.

		if (usePipeFromVectorsFile.wasInvoked()) {

			System.out.println(" Rewriting extended pipe from " + usePipeFromVectorsFile.value);
			System.out.println("  Instance ID = " + previousInstanceList.getPipe().getInstanceId());

			oos = new ObjectOutputStream(new FileOutputStream(usePipeFromVectorsFile.value));
			oos.writeObject(previousInstanceList);
			oos.close();

		}
	}
}

    

