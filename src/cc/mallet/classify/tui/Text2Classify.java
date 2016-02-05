/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.tui;

import java.util.Iterator;
import java.util.logging.*;
import java.io.*;
import java.nio.charset.Charset;

import cc.mallet.classify.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

/**
 * Command line tool for classifying a sequence of  
 *  instances directly from text input, without
 *  creating an instance list.
 *  <p>
 * 
 *  @author Gregory Druck
 *  @author David Mimno
 */

public class Text2Classify {

	private static Logger logger = MalletLogger.getLogger(Text2Classify.class.getName());

	static CommandOption.SpacedStrings classDirs =	new CommandOption.SpacedStrings
	(Text2Classify.class, "input", "DIR...", true, null,
	 "The directories containing text files to be classified, one directory per class", null);

	static CommandOption.File outputFile = new CommandOption.File
		(Text2Classify.class, "output", "FILE", true, new File("output"),
		 "Write predictions to this file; Using - indicates stdout.", null);
	
	static CommandOption.File classifierFile = new CommandOption.File
		(Text2Classify.class, "classifier", "FILE", true, new File("classifier"),
		 "Use the pipe and alphabets from a previously created vectors file.\n" +
		 "   Allows the creation, for example, of a test set of vectors that are\n" +
		 "   compatible with a previously created set of training vectors", null);

	static CommandOption.String encoding = new CommandOption.String
		(Text2Classify.class, "encoding", "STRING", true, Charset.defaultCharset().displayName(),
		 "Character encoding for input file", null);

	public static void main (String[] args) throws FileNotFoundException, IOException {

		// Process the command-line options
		CommandOption.setSummary (Text2Classify.class,
								  "A tool for classifying a stream of unlabeled instances");
		CommandOption.process (Text2Classify.class, args);
		
		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(Text2Classify.class).printUsage(false);
			System.exit (-1);
		}
		if (classDirs.value.length == 0) {
			throw new IllegalArgumentException ("You must include --input DIR1 DIR2 ...' in order to specify a " +
								"list of directories containing the documents.");
		}
		
		// Read classifier from file
		Classifier classifier = null;
		try {
			ObjectInputStream ois =
				new ObjectInputStream (new BufferedInputStream(new FileInputStream (classifierFile.value)));
			
			classifier = (Classifier) ois.readObject();
			ois.close();
		} catch (Exception e) {
			throw new IllegalArgumentException("Problem loading classifier from file " + classifierFile.value +
							   ": " + e.getMessage());
		}
		
		// Read instances from directories
		File[] directories = new File[classDirs.value.length];
		for (int i = 0; i < classDirs.value.length; i++) {
			directories[i] = new File (classDirs.value[i]);
		}
		Iterator<Instance> fileIterator = new UnlabeledFileIterator (directories);
		Iterator<Instance> iterator = 
			classifier.getInstancePipe().newIteratorFrom(fileIterator);
		
		// Write classifications to the output file
		PrintStream out = null;

		if (outputFile.value.toString().equals ("-")) {
			out = System.out;
		}
		else {
			out = new PrintStream(outputFile.value, encoding.value);
		}

		// gdruck@cs.umass.edu
		// Stop growth on the alphabets. If this is not done and new
		// features are added, the feature and classifier parameter
		// indices will not match.  
		classifier.getInstancePipe().getDataAlphabet().stopGrowth();
		classifier.getInstancePipe().getTargetAlphabet().stopGrowth();
		
		while (iterator.hasNext()) {
			Instance instance = iterator.next();
			
			Labeling labeling = 
				classifier.classify(instance).getLabeling();

			StringBuilder output = new StringBuilder();
			output.append(instance.getName());

			for (int location = 0; location < labeling.numLocations(); location++) {
				output.append("\t" + labeling.labelAtLocation(location));
				output.append("\t" + labeling.valueAtLocation(location));
			}

			out.println(output);
		}

		if (! outputFile.value.toString().equals ("-")) {
			out.close();
		}
	}
}