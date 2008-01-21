/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.tui;

import java.util.logging.*;
import java.io.*;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
/**
 * Convert document files into vectors (a persistent instance list).
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Text2Vectors
{
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
	 "Allows the creation, for example, of a test set of vectors that are" +
	 "compatible with a previously created set of training vectors", null);

    static CommandOption.Boolean preserveCase = new CommandOption.Boolean
	(Text2Vectors.class, "preserve-case", "[TRUE|FALSE]", false, false,
	 "If true, do not force all strings to lowercase.", null);
    
    static CommandOption.Boolean removeStopWords = new CommandOption.Boolean
	(Text2Vectors.class, "remove-stopwords", "[TRUE|FALSE]", false, false,
	 "If true, remove common \"stop words\" from the text.", null);

    static CommandOption.File extraStopwordsFile = new CommandOption.File
  (Text2Vectors.class, "extra-stopwords", "FILE", true, null,
   "Read whitespace-separated words from this file, and add them to the list of words to ignore.", null);

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
    
    static CommandOption.ObjectFromBean stringPipe = new CommandOption.ObjectFromBean
	(Text2Vectors.class, "string-pipe", "Pipe constructor",	true, null,
	 "Java code for the constructor of a Pipe to be run as soon as input becomes a CharSequence", null);
    
    static CommandOption.ObjectFromBean tokenPipe = new CommandOption.ObjectFromBean
	(Text2Vectors.class, "token-pipe", "Pipe constructor",	true, null,
	 "Java code for the constructor of a Pipe to be run as soon as input becomes a TokenSequence", null);
    
    static CommandOption.ObjectFromBean fvPipe = new CommandOption.ObjectFromBean
	(Text2Vectors.class, "fv-pipe", "Pipe constructor",	true, null,
	 "Java code for the constructor of a Pipe to be run as soon as input becomes a FeatureVector", null);
    
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
	    System.err.println ("You must include --input DIR1 DIR2 ...' in order to specify a"+
				"list of directories containing the documents for each class.");
	    System.exit (-1);
	}
	
	// Remove common prefix from all the input class directories
	int commonPrefixIndex = Strings.commonPrefixIndex (classDirs.value);
	
	logger.info ("Labels = ");
	File[] directories = new File[classDirs.value.length];
	for (int i = 0; i < classDirs.value.length; i++) {
	    directories[i] = new File (classDirs.value[i]);
	    if (commonPrefixIndex < classDirs.value.length)
		logger.info ("   "+classDirs.value[i].substring(commonPrefixIndex));
	    else
		logger.info ("   "+classDirs.value[i]);
	}

      Pipe instancePipe;
      InstanceList previousInstanceList = null;

      if (!usePipeFromVectorsFile.wasInvoked()){
        instancePipe = new SerialPipes (new Pipe[] {
            new Target2Label(),
            new SaveDataInSource(),
            new Input2CharSequence(),
            (stringPipe.wasInvoked() ? (Pipe) stringPipe.value : (Pipe) new Noop()),
            (skipHeader.value
             ? (Pipe) new CharSubsequence(CharSubsequence.SKIP_HEADER)
             : (Pipe) new Noop()),
            (skipHtml.value
             ? (Pipe) new CharSequenceRemoveHTML()
             : (Pipe) new Noop()),
            //new PrintInputAndTarget (), // xxx
            (keepSequenceBigrams.value
             ? (Pipe) new CharSequence2TokenSequence(CharSequenceLexer.LEX_NONWHITESPACE_CLASSES)
             : (Pipe) new CharSequence2TokenSequence()),
            //new PrintInputAndTarget (), // xxx
            (tokenPipe.wasInvoked() ? (Pipe) tokenPipe.value : (Pipe) new Noop()),
            (preserveCase.value
             ? (Pipe) new Noop()
             : (Pipe) new TokenSequenceLowercase()),
            //new PrintInputAndTarget (), // xxx
            (keepSequenceBigrams.value
             ? (Pipe) new TokenSequenceRemoveNonAlpha(true)
             : (Pipe) new Noop()),
            (removeStopWords.value
             ? (Pipe) new TokenSequenceRemoveStopwords(false, keepSequenceBigrams.value).addStopWords(extraStopwordsFile.value)
             : (Pipe) new Noop()),
            (!(gramSizes.value.length == 1 && gramSizes.value[0] == 1)
             ? (Pipe) new TokenSequenceNGrams(gramSizes.value)
             : (Pipe) new Noop()),
            (keepSequenceBigrams.value
             ? (Pipe) new TokenSequence2FeatureSequenceWithBigrams()
             : (Pipe) new TokenSequence2FeatureSequence()),
            //new PrintInputAndTarget (),
            (keepSequence.value || keepSequenceBigrams.value
             ? (Pipe) new Noop()
             : (Pipe) new FeatureSequence2AugmentableFeatureVector(binaryFeatures.value)),
            // or FeatureSequence2FeatureVector
            (fvPipe.wasInvoked()
             ? (Pipe) fvPipe.value
             : (Pipe) new Noop()),
            //new PrintInputAndTarget ()
        });
      }
      else{
        previousInstanceList = InstanceList.load (usePipeFromVectorsFile.value);
        instancePipe = previousInstanceList.getPipe();
        //System.out.println(" input usepipe ilist pipe instance id =" + previousInstanceList.getPipe().getInstanceId());
      }
	
	InstanceList ilist = new InstanceList (instancePipe);
	boolean removeCommonPrefix=true;
	ilist.addThruPipe (new FileIterator (directories, FileIterator.STARTING_DIRECTORIES, removeCommonPrefix));
        //System.out.println(" output ilist pipe instance id =" + ilist.getPipe().getInstanceId());
        // write vector file
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
