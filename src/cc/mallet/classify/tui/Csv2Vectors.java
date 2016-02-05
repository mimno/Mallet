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
	public static String defaultLineRegex = "^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$";
	public static String defaultTokenRegex = "\\p{L}[\\p{L}\\p{P}]+\\p{L}";

	static CommandOption.File inputFile = new CommandOption.File(Csv2Vectors.class, "input", "FILE", true, null,
		 "The file containing data to be classified, one instance per line", null);

	static CommandOption.File outputFile = new CommandOption.File(Csv2Vectors.class, "output", "FILE", true, new File("text.vectors"),
		 "Write the instance list to this file; Using - indicates stdout.", null);

	static CommandOption.String lineRegex = new CommandOption.String(Csv2Vectors.class, "line-regex", "REGEX", true, defaultLineRegex,
		 "Regular expression containing regex-groups for label, name and data.", null);
	
	static CommandOption.Integer labelOption = new CommandOption.Integer(Csv2Vectors.class, "label", "INTEGER", true, 2,
		 "The index of the group containing the label string.\n" +
		 "   Use 0 to indicate that the label field is not used.", null);

	static CommandOption.Integer nameOption = new CommandOption.Integer(Csv2Vectors.class, "name", "INTEGER", true, 1,
		 "The index of the group containing the instance name.\n" +
         "   Use 0 to indicate that the name field is not used.", null);

	static CommandOption.Integer dataOption = new CommandOption.Integer(Csv2Vectors.class, "data", "INTEGER", true, 3,
		 "The index of the group containing the data.", null);
	
	static CommandOption.File usePipeFromVectorsFile = new CommandOption.File(Csv2Vectors.class, "use-pipe-from", "FILE", true, new File("text.vectors"),
		 "Use the pipe and alphabets from a previously created vectors file.\n" +
		 "   Allows the creation, for example, of a test set of vectors that are\n" +
		 "   compatible with a previously created set of training vectors", null);

	static CommandOption.Boolean keepSequence = new CommandOption.Boolean(Csv2Vectors.class, "keep-sequence", "[TRUE|FALSE]", false, false,
	     "If true, final data will be a FeatureSequence rather than a FeatureVector.", null);

	static CommandOption.Boolean keepSequenceBigrams = new CommandOption.Boolean(Csv2Vectors.class, "keep-sequence-bigrams", "[TRUE|FALSE]", false, false,
		 "If true, final data will be a FeatureSequenceWithBigrams rather than a FeatureVector.", null);
	
	static CommandOption.Boolean targetAsFeatures = new CommandOption.Boolean(Csv2Vectors.class, "label-as-features", "[TRUE|FALSE]", false, false,
		 "If true, parse the 'label' field as space-delimited features.\n     Use feature=[number] to specify values for non-binary features.", null);
	
	static CommandOption.Boolean removeStopWords = new CommandOption.Boolean(Csv2Vectors.class, "remove-stopwords", "[TRUE|FALSE]", false, false,
		 "If true, remove a default list of common English \"stop words\" from the text.", null);

	static CommandOption.SpacedStrings replacementFiles = new CommandOption.SpacedStrings(Csv2Vectors.class, "replacement-files", "FILE [FILE ...]", true, null,
             "files containing string replacements, one per line:\n    'A B [tab] C' replaces A B with C,\n    'A B' replaces A B with A_B", null);

	static CommandOption.SpacedStrings deletionFiles = new CommandOption.SpacedStrings(Csv2Vectors.class, "deletion-files", "FILE [FILE ...]", true, null,
             "files containing strings to delete after replacements but before tokenization (ie multiword stop terms)", null);

	static CommandOption.File stoplistFile = new CommandOption.File(Csv2Vectors.class, "stoplist-file", "FILE", true, null,
		 "Instead of the default list, read stop words from a file, one per line. Implies --remove-stopwords", null);

	static CommandOption.File extraStopwordsFile = new CommandOption.File(Csv2Vectors.class, "extra-stopwords", "FILE", true, null,
		 "Read whitespace-separated words from this file, and add them to either \n" +
		 "   the default English stoplist or the list specified by --stoplist-file.", null);

	static CommandOption.File stopPatternFile = new CommandOption.File(Csv2Vectors.class, "stop-pattern-file", "FILE", true, null,
		 "Read regular expressions from a file, one per line. Tokens matching these regexps will be removed.", null);

	static CommandOption.Boolean preserveCase = new CommandOption.Boolean(Csv2Vectors.class, "preserve-case", "[TRUE|FALSE]", false, false,
		 "If true, do not force all strings to lowercase.", null);
	
	static CommandOption.String encoding = new CommandOption.String(Csv2Vectors.class, "encoding", "STRING", true, Charset.defaultCharset().displayName(),
		 "Character encoding for input file", null);

	static CommandOption.String tokenRegex = new CommandOption.String(Csv2Vectors.class, "token-regex", "REGEX", true, defaultTokenRegex,
		 "Regular expression used for tokenization.\n" +
		 "   Example: \"[\\p{L}\\p{N}_]+|[\\p{P}]+\" (unicode letters, numbers and underscore OR all punctuation) ", null);

	static CommandOption.Boolean printOutput = new CommandOption.Boolean(Csv2Vectors.class, "print-output", "[TRUE|FALSE]", false, false,
		 "If true, print a representation of the processed data\n" +
		 "   to standard output. This option is intended for debugging.", null);


	public static void main (String[] args) throws FileNotFoundException, IOException {
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
			throw new IllegalArgumentException ("You must include `--input FILE ...' in order to specify a"+
								"file containing the instances, one per line.");
		}
		
		Pipe instancePipe;
		InstanceList previousInstanceList = null;
		
		if (usePipeFromVectorsFile.wasInvoked()) {

			// Ignore all options, use a previously created pipe

			previousInstanceList = InstanceList.load (usePipeFromVectorsFile.value);
			instancePipe = previousInstanceList.getPipe();			
		}
		else {
			
			// Build a new pipe

			ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

			// Convert the "target" object into a numeric index
			//  into a LabelAlphabet.
			if (labelOption.value > 0) {
				if (targetAsFeatures.value) {
					pipeList.add(new TargetStringToFeatures());
				}
				else {
					// If the label field is not used, adding this
					//  pipe will cause "Alphabets don't match" exceptions.
					pipeList.add(new Target2Label());
				}
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
			
			// String replacements
			
			if (! preserveCase.value()) {
				pipeList.add(new CharSequenceLowercase());
			}
			
            if (replacementFiles.value != null || deletionFiles.value != null) {
				NGramPreprocessor preprocessor = new NGramPreprocessor();

				if (replacementFiles.value != null) {
					for (String filename: replacementFiles.value) { preprocessor.loadReplacements(filename); }
				}
				if (deletionFiles.value != null) {
					for (String filename: deletionFiles.value) { preprocessor.loadDeletions(filename); }
				}
			
				pipeList.add(preprocessor);
            }
			
			// Add the tokenizer
			pipeList.add(new CharSequence2TokenSequence(tokenPattern));

			// 
			// Normalize the input as necessary
			// 
			
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
			
			if (stopPatternFile.wasInvoked()) {
				TokenSequenceRemoveStopPatterns stopPatternFilter = 
					new TokenSequenceRemoveStopPatterns(stopPatternFile.value);
				pipeList.add(stopPatternFilter);
			}
                        
			// 
			// Convert tokens to numeric indices into the Alphabet
			//
			
			if (keepSequenceBigrams.value) {
				// Output is feature sequences with bigram features
				pipeList.add(new TokenSequence2FeatureSequenceWithBigrams());
			}
			else if (keepSequence.value) {
				// Output is unigram feature sequences
				pipeList.add(new TokenSequence2FeatureSequence());
			}
			else {
				// Output is feature vectors (no sequence information)
				pipeList.add(new TokenSequence2FeatureSequence());
				pipeList.add(new FeatureSequence2AugmentableFeatureVector());
			}

			if (printOutput.value) {
				pipeList.add(new PrintInputAndTarget());
			}

			instancePipe = new SerialPipes(pipeList);
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

	

