/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.tui;

import java.util.Iterator;
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
 * Command line tool for classifying a sequence of  
 *  instances directly from text input, without
 *  creating an instance list.
 *  <p>
 * 
 *  @author David Mimno
 */

public class Text2Classify {

	private static Logger logger = MalletLogger.getLogger(Text2Classify.class.getName());

	static CommandOption.File inputFile =	new CommandOption.File
		(Text2Classify.class, "input", "FILE", true, null,
		 "The file containing data to be classified, one instance per line", null);

	static CommandOption.File outputFile = new CommandOption.File
		(Text2Classify.class, "output", "FILE", true, new File("text.vectors"),
		 "Write the instance list to this file; Using - indicates stdout.", null);

	static CommandOption.String lineRegex = new CommandOption.String
		(Text2Classify.class, "line-regex", "REGEX", true, "^(\\S*)[\\s,]*(.*)$",
		 "Regular expression containing regex-groups for label, name and data.", null);
	
	static CommandOption.Integer nameOption = new CommandOption.Integer
		(Text2Classify.class, "name", "INTEGER", true, 1,
		 "The index of the group containing the instance name.\n" +
         "   Use 0 to indicate that the name field is not used.", null);

	static CommandOption.Integer dataOption = new CommandOption.Integer
		(Text2Classify.class, "data", "INTEGER", true, 2,
		 "The index of the group containing the data.", null);
	
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
		if (inputFile == null) {
			System.err.println ("You must include `--input FILE ...' in order to specify a"+
								"file containing the instances, one per line.");
			System.exit (-1);
		}
		
		Pipe instancePipe;
		Classifier classifier = null;
		
		try {
			ObjectInputStream ois =
				new ObjectInputStream (new BufferedInputStream(new FileInputStream (classifierFile.value)));
			
			classifier = (Classifier) ois.readObject();
			ois.close();
		} catch (Exception e) {
			System.err.println("Problem loading classifier from file " + classifierFile.value +
							   ": " + e.getMessage());
			System.exit(-1);
		}
		
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

		Iterator<Instance> csvIterator = 
			new CsvIterator (fileReader, Pattern.compile(lineRegex.value),
							 dataOption.value, 0, nameOption.value);
		Iterator<Instance> iterator = 
			classifier.getInstancePipe().newIteratorFrom(csvIterator);
	
		// 
		// Write classifications to the output file
		//
		
		PrintStream out = null;

		if (outputFile.value.toString().equals ("-")) {
			out = System.out;
		}
		else {
			out = new PrintStream(outputFile.value, encoding.value);
		}

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

    

