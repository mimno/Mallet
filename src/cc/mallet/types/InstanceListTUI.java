/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 A command-line interface for creating an InstanceList.

	 I would have put this in InstanceList itself, but it doesn't seem that an inner class
	 can have its own main()???

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.io.*;
import java.util.*;
//import bsh.Interpreter;
import java.util.regex.*;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

public class InstanceListTUI
{
	
	static CommandOption.String prefixCodeOption = new CommandOption.String
	(InstanceList.class, "prefix-code", "Java code", true, null,
	 "Java code you want run before any other interpreted code.  Note that the text "+
	 "is interpretted without modification, so unlike some other options, "+
	 "you need to include any necessary 'new's.", null);

	static CommandOption.SpacedStrings pipeInputOption = new CommandOption.SpacedStrings
	(InstanceList.class, "pipe-input", "STRING...", true, null,
	 "The String or String[] that will be passed into the Pipe, "+
	 "(or the PipeInputIterator, if specified.  If --pipe-input-iterator is specified, "+
	 "this option is not used.", null);

	static final String defaultPipeIterator =
	"FileIterator(pipeInput,FileIterator.STARTING_DIRECTORIES)";
	
	static CommandOption.String pipeInputIteratorOption = new CommandOption.String
	(InstanceList.class, "pipe-input-iterator", "PipeInputIterator constructor", true, defaultPipeIterator,
	 "A constructor for a PipeInputIterator, omitting the 'new', and substiting 'pipeInput' with the "+
	 "String or String[] that comes from the --pipe-input option.",
	 "By default this value is null, indicating that no iterator is to be run, and simply "+
	 "the single --pipe-input argument should be put directly into the pipe.");

	static final String defaultPipe =
	"new Input2CharSequence(),new CharSequence2TokenSequence(),new TokenSequenceLowercase(),"+
	"new TokenSequenceRemoveStopwords(),new TokenSequence2FeatureSequence(),new FeatureSequence2FeatureVector(),"+
	"new Target2Label()";
		
	static CommandOption.String pipeOption = new CommandOption.String
	(InstanceList.class, "pipe", "Pipe constructor", true, defaultPipe,
	 "List of Java constructors for Pipe objects to be run in serial to process the pipe input, "+
	 "separated by semi-colons, with the 'new's omitted.", null);

	static CommandOption.File pipeFileOption = new CommandOption.File
	(InstanceList.class, "pipefile", "FILENAME", true, null,
	 "Same as --pipe, except get the pipe specification from the named file instead of from the command line.  "+
	 "If both are set, the --pipe option takes precedence.", null);
	
	static CommandOption.String outputFilenameOption = new CommandOption.String
	(InstanceList.class, "output-file", "FILENAME", true, "instance-list.mallet",
	 "The filename in which to write the resulting instance list.", null);
		
	// Some pre-packaged, typical configurations for pipe-input, pipe-input-iterator and pipe.

	static CommandOption.SpacedStrings textFileClassificationOption = new CommandOption.SpacedStrings
	(InstanceList.class, "pipe-text-file-directories", "DIR...", false, null,
	 "Use a standard text classification pipeline run on all the files in the following directories, "+
	 "one directory per class name.", null);


	static final CommandOption.List commandOptions =
	new CommandOption.List (
		"Options for creating, manipulating, querying and saving instance lists",
		new CommandOption[] {
			pipeInputOption,
			pipeInputIteratorOption,
			pipeOption,
			outputFilenameOption,
			textFileClassificationOption,
			prefixCodeOption,
		});

	
	public static void main (String[] args) throws bsh.EvalError, java.io.IOException
	{
		// Process the command-line options
		commandOptions.process (args);

		BshInterpreter interpreter = new BshInterpreter(prefixCodeOption.value);

		// Insert "new " before each constructor in the pipe specification
		String pipeSpec = ((pipeOption.value == defaultPipe && pipeFileOption.value != null)
											 ? IoUtils.contentsAsString (pipeFileOption.value)
											 : pipeOption.value);
		//Pattern pat = Pattern.compile (",");
		//Matcher mat = pat.matcher ("new SerialPipes(new Pipe[] { new "+pipeSpec+" })");
		//String pipeWithNew = mat.replaceAll(", new ");
		String pipeWithNew = "new SerialPipes(new Pipe[] { "+pipeSpec+" })";

		// Construct the pipe
		Pipe instancePipe = (Pipe) interpreter.eval (pipeWithNew);
		//Pipe instancePipe = (Pipe) interpreter.eval (pipeOption.value);
		//Pipe instancePipe = (Pipe) interpreter.eval ("new SerialPipes();");
		InstanceList ilist = new InstanceList (instancePipe);

		System.out.println ("Piping...");
		//System.out.println ("pipeInput = "+pipeInputOption.value);
		//System.out.println ("pipeInputIteator = "+pipeInput);
		//System.out.println ("instancePipe = "+instancePipe);
		
		// Run the pipe on the pipe input data
		if (pipeInputIteratorOption.value != null) {
			// Put the pipe-input in the bsh variable "pipeInput"
			if (pipeInputOption.value.length > 1)
				interpreter.set ("pipeInput", pipeInputOption.value);
			else
				interpreter.set ("pipeInput", pipeInputOption.value[0]);
			Iterator<Instance> pii =
				(Iterator<Instance>)interpreter.eval ("new "+pipeInputIteratorOption.value);
			ilist.addThruPipe (pii);
		} else {
			Instance carrier;
			if (pipeInputOption.value.length > 1)
				carrier = instancePipe.instanceFrom(new Instance (pipeInputOption.value, null, null, null));
			else
				carrier = instancePipe.instanceFrom(new Instance (pipeInputOption.value[0], null, null, null));
			if (carrier.getData() instanceof InstanceList)
				ilist = (InstanceList) carrier.getData();
			else
				ilist.add (carrier);
		}

		// Save the instance list to disk
		ObjectOutputStream oos = new ObjectOutputStream
														 (new FileOutputStream (outputFilenameOption.value));
		oos.writeObject (ilist);
		oos.close();
	}
		
}
