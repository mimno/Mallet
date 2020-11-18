package cc.mallet.classify.tui;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.util.*;

import java.util.*;
import java.util.logging.*;
import java.io.*;
import java.util.*;
import java.io.*;

public class MultFileToSequences {

	protected static Logger logger = MalletLogger.getLogger(MultFileToSequences.class.getName());

	static CommandOption.SpacedStrings inputFiles = new CommandOption.SpacedStrings 		(MultFileToSequences.class, "input", "FILE [FILE ...]", true, null,
		 "The file(s) containing data, one instance per line", null);
	
	static CommandOption.File outputFile = new CommandOption.File(MultFileToSequences.class, "output", "FILE", true, new File("mallet.data"),
		 "Write the instance list to this file", null);
	
    static CommandOption.File vocabularyFile = new CommandOption.File (MultFileToSequences.class, "vocabulary", "FILE", true, null,
		 "Read newline-separated words from this file.", null);

	static CommandOption.String lineRegex = new CommandOption.String (MultFileToSequences.class, "line-regex", "REGEX", true, "^([^\\t]*)\\t([^\\t]*)\\t(.*)",
		 "Regular expression containing regex-groups for label, name and data.", null);
	
    static CommandOption.Integer nameGroup = new CommandOption.Integer (MultFileToSequences.class, "name", "INTEGER", true, 1,
		 "The index of the group containing the instance name.\n   Use 0 to indicate that this field is not used.", null);

    static CommandOption.Integer labelGroup = new CommandOption.Integer (MultFileToSequences.class, "label", "INTEGER", true, 2,
		 "The index of the group containing the label string.\n   Use 0 to indicate that this field is not used.", null);

    static CommandOption.Integer dataGroup = new CommandOption.Integer (MultFileToSequences.class, "data", "INTEGER", true, 3,
		 "The index of the group containing the data.", null);


	public static void main(String[] args) throws Exception {
		// Process the command-line options
        CommandOption.setSummary (MultFileToSequences.class,
                                  "Tool for importing text in id:count format as Mallet feature sequences.");
        CommandOption.process (MultFileToSequences.class, args);

		Alphabet alphabet = AlphabetFactory.loadFromFile(vocabularyFile.value);
		alphabet.stopGrowth();
		
		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		Pipe pipe = new CountsToFeatureSequencePipe();
		pipe.setDataAlphabet(alphabet);
		
		pipes.add(pipe);
		//pipes.add(new PrintInput());
		
		InstanceList instances = new InstanceList(new SerialPipes(pipes));

		for (String filename: inputFiles.value) {
			logger.info("Loading " + filename);

			CsvIterator reader = new CsvIterator(new FileReader(filename),
												 lineRegex.value,
												 dataGroup.value,
												 labelGroup.value,
												 nameGroup.value);
			
			instances.addThruPipe(reader);
		}

		instances.save(outputFile.value);
	}
}