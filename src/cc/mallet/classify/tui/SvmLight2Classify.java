/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.tui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.*;
import java.util.regex.*;
import java.io.*;
import java.nio.charset.Charset;

import cc.mallet.classify.*;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.SvmLight2FeatureVectorAndLabel;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

/**
 * Command line tool for classifying a sequence of instances directly from text
 * input, without creating an instance list.
 * <p>
 * 
 * @author David Mimno
 * @author Gregory Druck
 * @author Siddhartha Jonnalagadda
 */

public class SvmLight2Classify {

	private static Logger logger = MalletLogger.getLogger(SvmLight2Classify.class.getName());

	static CommandOption.File inputFile = new CommandOption.File(
		SvmLight2Classify.class, "input", "FILE", true, null,
		"The file containing data to be classified, one instance per line", null);

	static CommandOption.File outputFile = new CommandOption.File(
		SvmLight2Classify.class, "output", "FILE", true,
		new File("text.vectors"),
		"Write predictions to this file; Using - indicates stdout.", null);

	static CommandOption.File classifierFile = new CommandOption.File(
		SvmLight2Classify.class, "classifier", "FILE", true, new File("classifier"),
		"Use the pipe and alphabets from a previously created vectors file.\n"
		+ "   Allows the creation, for example, of a test set of vectors that are\n"
		+ "   compatible with a previously created set of training vectors", null);

	static CommandOption.String encoding = new CommandOption.String(
		SvmLight2Classify.class, "encoding", "STRING", true, Charset.defaultCharset().displayName(), 
		"Character encoding for input file", null);

	public static void main(String[] args) throws FileNotFoundException, IOException {

		// Process the command-line options
		CommandOption.setSummary(SvmLight2Classify.class,
				"A tool for classifying a stream of unlabeled instances");
		CommandOption.process(SvmLight2Classify.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(SvmLight2Classify.class).printUsage(false);
			System.exit(-1);
		}
		if (inputFile == null) {
			throw new IllegalArgumentException(
			  "You must include `--input FILE ...' in order to specify a"
				+ "file containing the instances, one per line.");
		}

		// Read classifier from file
		Classifier classifier = null;
		try {
			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
			  new FileInputStream(classifierFile.value)));

			classifier = (Classifier) ois.readObject();
			ois.close();
		} catch (Exception e) {
			throw new IllegalArgumentException(
			  "Problem loading classifier from file " + classifierFile.value + ": "+ e.getMessage());
		}

		// gdruck@cs.umass.edu
		// Stop growth on the alphabets. If this is not done and new
		// features are added, the feature and classifier parameter
		// indices will not match.
		classifier.getInstancePipe().getDataAlphabet().stopGrowth();
		classifier.getInstancePipe().getTargetAlphabet().stopGrowth();
		
		// Build a new pipe
		InstanceList instances = new InstanceList(classifier.getInstancePipe());
		Reader fileReader;
		if (inputFile.value.toString().equals("-")) {
			fileReader = new InputStreamReader(System.in);
		} else {
			fileReader = new InputStreamReader(new FileInputStream(inputFile.value),encoding.value);
		}
		// Read instances from the file
		instances.addThruPipe(new SelectiveFileLineIterator(fileReader, "^\\s*#.+"));

		Iterator<Instance> iterator = instances.iterator();
		// Write classifications to the output file
		PrintStream out = null;

		if (outputFile.value.toString().equals("-")) {
			out = System.out;
		} else {
			out = new PrintStream(outputFile.value, encoding.value);
		}

		while (iterator.hasNext()) {
			Instance instance = iterator.next();

			Labeling labeling = classifier.classify(instance).getLabeling();

			StringBuilder output = new StringBuilder();
			output.append(instance.getName());

			for (int location = 0; location < labeling.numLocations(); location++) {
				output.append("\t" + labeling.labelAtLocation(location));
				output.append("\t" + labeling.valueAtLocation(location));
			}
			out.println(output);
		}

		if (!outputFile.value.toString().equals("-")) {
			out.close();
		}
	}
}
