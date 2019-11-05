package cc.mallet.util;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

import com.google.errorprone.annotations.Var;

import cc.mallet.pipe.NGramPreprocessor;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.types.Instance;

/**
 * This class replaces ngrams as specified in the configuration files.
 * Input: tab-delimited text, one instance per line
 * Output: tab-delimited text, but with deletions and text replacements.
 */

public class Replacer {

	protected static Logger logger = MalletLogger.getLogger(Replacer.class.getName());

	static CommandOption.SpacedStrings inputFiles = new CommandOption.SpacedStrings
		(Replacer.class, "input", "FILE [FILE ...]", true, null,
		 "The file(s) containing data, one instance per line", null);

	static CommandOption.File outputFile = new CommandOption.File
		(Replacer.class, "output", "FILE", true, new File("mallet.data"),
		 "Write the strings with replacements applied to this file", null);

	static CommandOption.SpacedStrings replacementFiles = new CommandOption.SpacedStrings
		(Replacer.class, "replacement-files", "FILE [FILE ...]", true, null,
		 "files containing string replacements, one per line:\n    'A B [tab] C' replaces A B with C,\n    'A B' replaces A B with A_B", null);

	static CommandOption.SpacedStrings deletionFiles = new CommandOption.SpacedStrings
		(Replacer.class, "deletion-files", "FILE [FILE ...]", true, null,
		 "files containing strings to delete after replacements but before tokenization (ie multiword stop terms)", null);

	static CommandOption.String lineRegex = new CommandOption.String
		(Replacer.class, "line-regex", "REGEX", true, "^([^\\t]*)\\t([^\\t]*)\\t(.*)",
		 "Regular expression containing regex-groups for label, name and data.", null);

    static CommandOption.Integer nameGroup = new CommandOption.Integer
		(Replacer.class, "name", "INTEGER", true, 1,
		 "The index of the group containing the instance name.\n   Use 0 to indicate that this field is not used.", null);

    static CommandOption.Integer labelGroup = new CommandOption.Integer
		(Replacer.class, "label", "INTEGER", true, 2,
		 "The index of the group containing the label string.\n   Use 0 to indicate that this field is not used.", null);

    static CommandOption.Integer dataGroup = new CommandOption.Integer
		(Replacer.class, "data", "INTEGER", true, 3,
		 "The index of the group containing the data.", null);

	public static void main (String[] args) throws Exception {

		// Process the command-line options
        CommandOption.setSummary (Replacer.class,
                                  "Tool for modifying text with n-gram preprocessing");
        CommandOption.process (Replacer.class, args);

		NGramPreprocessor preprocessor = new NGramPreprocessor();

		if (replacementFiles.value != null) {
			for (String filename: replacementFiles.value) {
				System.out.println("including replacements from " + filename);
				preprocessor.loadReplacements(filename);
			}
		}

		if (deletionFiles.value != null) {
			for (String filename: deletionFiles.value) {
				System.out.println("including deletions from " + filename);
				preprocessor.loadDeletions(filename);
			}
		}
		
		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		
		PrintWriter out = new PrintWriter(outputFile.value);

		for (String filename: inputFiles.value) {
			logger.info("Loading " + filename);
			
			CsvIterator reader = new CsvIterator(new FileReader(filename),
												 lineRegex.value,
												 dataGroup.value,
												 labelGroup.value,
												 nameGroup.value);
			
			Iterator<Instance> iterator = preprocessor.newIteratorFrom(reader);

			@Var
			int count = 0;
			
			// We're not saving the instance list, just writing to the out file
			while (iterator.hasNext()) {
				Instance instance = iterator.next();

				out.println(instance.getName() + "\t" + instance.getTarget() + "\t" + instance.getData());
				
				count++;
				if (count % 10000 == 0) {
					logger.info("instance " + count);
				}
				iterator.next();
			}
			
		}
		out.close();
	}
}
