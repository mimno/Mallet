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

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

/**
 * Convert document files into vectors (a persistent instance list).
 * @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Text2Vectors {

	private static Logger logger = MalletLogger.getLogger(Text2Vectors.class.getName());

	static CommandOption.SpacedStrings classDirs =	new CommandOption.SpacedStrings
		(Text2Vectors.class, "input", "DIR...", true, null,
		 "The directories containing text files to be classified, one directory per class", null);
	
	static CommandOption.File outputFile = new CommandOption.File
		(Text2Vectors.class, "output", "FILE", true, new File("text.vectors"),
		 "Write the instance list to this file; Using - indicates stdout.", null);
	
	static CommandOption.File usePipeFromVectorsFile = new CommandOption.File
		(Text2Vectors.class, "use-pipe-from", "FILE", true, new File("text.vectors"),
		 "Use the pipe and alphabets from a previously created vectors file. " +
		 "Allows the creation, for example, of a test set of vectors that are " +
		 "compatible with a previously created set of training vectors", null);

	static CommandOption.Boolean preserveCase = new CommandOption.Boolean
		(Text2Vectors.class, "preserve-case", "[TRUE|FALSE]", false, false,
		 "If true, do not force all strings to lowercase.", null);
	
	static CommandOption.Boolean removeStopWords = new CommandOption.Boolean
		(Text2Vectors.class, "remove-stopwords", "[TRUE|FALSE]", false, false,
		 "If true, remove a default list of common English \"stop words\" from the text.", null);

	static CommandOption.File stoplistFile = new CommandOption.File
		(Text2Vectors.class, "stoplist-file", "FILE", true, null,
		 "Read \"stop words\" from a file, one per line. Implies --remove-stopwords", null);

	static CommandOption.File extraStopwordsFile = new CommandOption.File
		(Text2Vectors.class, "extra-stopwords", "FILE", true, null,
		 "Read whitespace-separated words from this file, and add them to either " + 
		 "  the default English stoplist or the list specified by --stoplist-file.", null);

	static CommandOption.Boolean skipHeader = new CommandOption.Boolean
		(Text2Vectors.class, "skip-header", "[TRUE|FALSE]", false, false,
		 "If true, in each document, remove text occurring before a blank line."+
		 "  This is useful for removing email or UseNet headers", null);
	
	static CommandOption.Boolean skipHtml = new CommandOption.Boolean
		(Text2Vectors.class, "skip-html", "[TRUE|FALSE]", false, false,
		 "If true, remove text occurring inside <...>, as in HTML or SGML.", null);
	
	static CommandOption.Boolean binaryFeatures = new CommandOption.Boolean
		(Text2Vectors.class, "binary-features", "[TRUE|FALSE]", false, false,
		 "If true, features will be binary.", null);
	
	static CommandOption.IntegerArray gramSizes = new CommandOption.IntegerArray
		(Text2Vectors.class, "gram-sizes", "INTEGER,[INTEGER,...]", true, new int[] {1},
		 "Include among the features all n-grams of sizes specified.  "+
		 "For example, to get all unigrams and bigrams, use --gram-sizes 1,2.  "+
		 "This option occurs after the removal of stop words, if removed.", null);
	
	static CommandOption.Boolean keepSequence = new CommandOption.Boolean
		(Text2Vectors.class, "keep-sequence", "[TRUE|FALSE]", false, false,
		 "If true, final data will be a FeatureSequence rather than a FeatureVector.", null);

	static CommandOption.Boolean keepSequenceBigrams = new CommandOption.Boolean
		(Text2Vectors.class, "keep-sequence-bigrams", "[TRUE|FALSE]", false, false,
		 "If true, final data will be a FeatureSequenceWithBigrams rather than a FeatureVector.", null);
	
	static CommandOption.Boolean saveTextInSource = new CommandOption.Boolean
		(Text2Vectors.class, "save-text-in-source", "[TRUE|FALSE]", false, false,
		 "If true, save original text of document in source.", null);
	
	static CommandOption.ObjectFromBean stringPipe = new CommandOption.ObjectFromBean
		(Text2Vectors.class, "string-pipe", "Pipe constructor",	true, null,
		 "Java code for the constructor of a Pipe to be run as soon as input becomes a CharSequence", null);
	
	static CommandOption.ObjectFromBean tokenPipe = new CommandOption.ObjectFromBean
		(Text2Vectors.class, "token-pipe", "Pipe constructor",	true, null,
		 "Java code for the constructor of a Pipe to be run as soon as input becomes a TokenSequence", null);
	
	static CommandOption.ObjectFromBean featureVectorPipe = new CommandOption.ObjectFromBean
		(Text2Vectors.class, "fv-pipe", "Pipe constructor",	true, null,
		 "Java code for the constructor of a Pipe to be run as soon as input becomes a FeatureVector", null);
	
	static CommandOption.String encoding = new CommandOption.String
		(Text2Vectors.class, "encoding", "STRING", true, Charset.defaultCharset().displayName(),
		 "Character encoding for input file", null);
	
	static CommandOption.String tokenRegex = new CommandOption.String
		(Text2Vectors.class, "token-regex", "REGEX", true, CharSequenceLexer.LEX_ALPHA.toString(),
		 "Regular expression used for tokenization.\n" +
		 "   Example: \"[\\p{L}\\p{N}_]+|[\\p{P}]+\" (unicode letters, numbers and underscore OR all punctuation) ", null);
	
	static CommandOption.Boolean printOutput = new CommandOption.Boolean
		(Text2Vectors.class, "print-output", "[TRUE|FALSE]", false, false,
		 "If true, print a representation of the processed data\n" +
		 "   to standard output. This option is intended for debugging.", null);
	
	public static void main (String[] args) throws FileNotFoundException, IOException 	{
		// Process the command-line options
		CommandOption.setSummary (Text2Vectors.class,
								  "A tool for creating instance lists of FeatureVectors or FeatureSequences from text documents.\n");
		CommandOption.process (Text2Vectors.class, args);
		//String[] classDirs = CommandOption.process (Text2Vectors.class, args);
	
		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(Text2Vectors.class).printUsage(false);
			System.exit (-1);
		}
		if (classDirs.value.length == 0) {
			throw new IllegalArgumentException ("You must include --input DIR1 DIR2 ...' in order to specify a " +
								"list of directories containing the documents for each class.");
		}
	
		// Remove common prefix from all the input class directories
		int commonPrefixIndex = Strings.commonPrefixIndex (classDirs.value);
	
		logger.info ("Labels = ");
		File[] directories = new File[classDirs.value.length];
		for (int i = 0; i < classDirs.value.length; i++) {
			directories[i] = new File (classDirs.value[i]);
			if (commonPrefixIndex < classDirs.value.length) {
				logger.info ("   "+classDirs.value[i].substring(commonPrefixIndex));
			}
			else {
				logger.info ("   "+classDirs.value[i]);
			}
		}

		Pipe instancePipe;
		InstanceList previousInstanceList = null;

		if (usePipeFromVectorsFile.wasInvoked()) {
			previousInstanceList = InstanceList.load (usePipeFromVectorsFile.value);
			instancePipe = previousInstanceList.getPipe();
		}
		else {
			
			// Build a new pipe

			// Create a list of pipes that will be added to a SerialPipes object later
			ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

			// Convert the "target" object into a numeric index
			//  into a LabelAlphabet.
			pipeList.add(new Target2Label());
			
			// The "data" field is currently a filename. Save it as "source".
			pipeList.add( new SaveDataInSource() );

			// Set "data" to the file's contents. "data" is now a String.
			pipeList.add( new Input2CharSequence(encoding.value) );

			// Optionally save the text to "source" -- not recommended if memory is scarce.
			if (saveTextInSource.wasInvoked()) {
				pipeList.add( new SaveDataInSource() );
			}

			// Allow the user to specify an arbitrary Pipe object
			//  that operates on Strings
			if (stringPipe.wasInvoked()) {
				pipeList.add( (Pipe) stringPipe.value );
			}

			// Remove all content before the first empty line. 
			//  Useful for email and usenet news posts.
			if (skipHeader.value) {
				pipeList.add( new CharSubsequence(CharSubsequence.SKIP_HEADER) );
			}
			
			// Remove HTML tags. Suitable for SGML and XML.
			if (skipHtml.value) {
				pipeList.add( new CharSequenceRemoveHTML() );
			}


			//
			// Tokenize the input: first compile the tokenization pattern
			// 

			Pattern tokenPattern = null;

			if (keepSequenceBigrams.value) {
				// We do not want to record bigrams across punctuation,
				//  so we need to keep non-word tokens.
				tokenPattern = CharSequenceLexer.LEX_NONWHITESPACE_CLASSES;
			}
			else {
				// Otherwise, try to compile the regular expression pattern.
                                
				try {
					tokenPattern = Pattern.compile(tokenRegex.value);
				} catch (PatternSyntaxException pse) {
					throw new IllegalArgumentException("The token regular expression (" + tokenRegex.value + 
									   ") was invalid: " + pse.getMessage());
				}
			}
                        
			// Add the tokenizer
			pipeList.add(new CharSequence2TokenSequence(tokenPattern));

			// Allow user to specify an arbitrary Pipe object
			//  that operates on TokenSequence objects.
			if (tokenPipe.wasInvoked()) {
				pipeList.add( (Pipe) tokenPipe.value );
			}

			if (! preserveCase.value()) {
				pipeList.add(new TokenSequenceLowercase());
			}
                        
			if (keepSequenceBigrams.value) {
				// Remove non-word tokens, but record the fact that they
				//  were there.
				pipeList.add(new TokenSequenceRemoveNonAlpha(true));
			}

			// Stopword removal.

			if (stoplistFile.wasInvoked()) {

				// The user specified a new list
				
				TokenSequenceRemoveStopwords stopwordFilter =
					new TokenSequenceRemoveStopwords(stoplistFile.value,
													 encoding.value,
													 false, // don't include default list
													 false,
													 keepSequenceBigrams.value);

				if (extraStopwordsFile.wasInvoked()) {
					stopwordFilter.addStopWords(extraStopwordsFile.value);
				}

				pipeList.add(stopwordFilter);
			}
			else if (removeStopWords.value) {

				// The user did not specify a new list, so use the default
				//  built-in English list, possibly adding extra words.

				TokenSequenceRemoveStopwords stopwordFilter =
					new TokenSequenceRemoveStopwords(false, keepSequenceBigrams.value);

				if (extraStopwordsFile.wasInvoked()) {
					stopwordFilter.addStopWords(extraStopwordsFile.value);
				}

				pipeList.add(stopwordFilter);

			}
			
			// gramSizes is an integer array, with default value [1].
			//  Check if we have a non-default value.
			if (! (gramSizes.value.length == 1 && gramSizes.value[0] == 1)) {
				pipeList.add( new TokenSequenceNGrams(gramSizes.value) );
			}

			// So far we have a sequence of Token objects that contain 
			//  String values. Look these up in an alphabet and store integer IDs
			//  ("features") instead of Strings.
			if (keepSequenceBigrams.value) {
				pipeList.add( new TokenSequence2FeatureSequenceWithBigrams() );
			}
			else {
				pipeList.add( new TokenSequence2FeatureSequence() );
			}

			// For many applications, we do not need to preserve the sequence of features,
			//  only the number of times times a feature occurs.
			if (! (keepSequence.value || keepSequenceBigrams.value)) {
				pipeList.add( new FeatureSequence2AugmentableFeatureVector(binaryFeatures.value) );
			}

			// Allow users to specify an arbitrary Pipe object that operates on 
			//  feature vectors.
			if (featureVectorPipe.wasInvoked()) {
				pipeList.add( (Pipe) featureVectorPipe.value );
			}

			if (printOutput.value) {
				pipeList.add(new PrintInputAndTarget());
			}

			instancePipe = new SerialPipes(pipeList);

		}
	
		InstanceList instances = new InstanceList (instancePipe);

		boolean removeCommonPrefix = true;
		instances.addThruPipe (new FileIterator (directories, FileIterator.STARTING_DIRECTORIES, removeCommonPrefix));

		// write vector file
		ObjectOutputStream oos;
		if (outputFile.value.toString().equals ("-")) {
			oos = new ObjectOutputStream(System.out);
		}
		else {
			oos = new ObjectOutputStream(new FileOutputStream(outputFile.value));
		}
		oos.writeObject(instances);
		oos.close();
	
		// *rewrite* vector file used as source of pipe in case we changed the alphabet(!)
		if (usePipeFromVectorsFile.wasInvoked()) {
			logger.info(" rewriting previous instance list, with ID = " + previousInstanceList.getPipe().getInstanceId());
			oos = new ObjectOutputStream(new FileOutputStream(usePipeFromVectorsFile.value));
			oos.writeObject(previousInstanceList);
			oos.close();
		}
	
	}

}
