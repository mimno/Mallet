package cc.mallet.util;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;

import java.util.*;
import java.io.*;

/**
 *  This class reads through a single file, breaking each line
 *   into data and (optional) name and label fields.
 */

public class BulkLoader {

	static CommandOption.File inputFile =   new CommandOption.File
		(BulkLoader.class, "input", "FILE", true, null,
		 "The file containing data, one instance per line", null);

	static CommandOption.File outputFile = new CommandOption.File
		(BulkLoader.class, "output", "FILE", true, new File("mallet.data"),
		 "Write the instance list to this file", null);

    static CommandOption.Boolean preserveCase = new CommandOption.Boolean
		(BulkLoader.class, "preserve-case", "[TRUE|FALSE]", false, false,
		 "If true, do not force all strings to lowercase.", null);

    static CommandOption.Boolean removeStopWords = new CommandOption.Boolean
		(BulkLoader.class, "remove-stopwords", "[TRUE|FALSE]", false, false,
		 "If true, remove common \"stop words\" from the text.\nThis option invokes a minimal English stoplist. ", null);

    static CommandOption.File stoplistFile = new CommandOption.File
		(BulkLoader.class, "stoplist", "FILE", true, null,
		 "Read newline-separated words from this file,\n   and remove them from text. This option overrides\n   the default English stoplist triggered by --remove-stopwords.", null);

	static CommandOption.Boolean keepSequence = new CommandOption.Boolean
		(BulkLoader.class, "keep-sequence", "[TRUE|FALSE]", false, false,
		 "If true, final data will be a FeatureSequence rather than a FeatureVector.", null);

	static CommandOption.String lineRegex = new CommandOption.String
		(BulkLoader.class, "line-regex", "REGEX", true, "^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$",
		 "Regular expression containing regex-groups for label, name and data.", null);

    static CommandOption.Integer nameGroup = new CommandOption.Integer
		(BulkLoader.class, "name", "INTEGER", true, 1,
		 "The index of the group containing the instance name.\n   Use 0 to indicate that this field is not used.", null);

    static CommandOption.Integer labelGroup = new CommandOption.Integer
		(BulkLoader.class, "label", "INTEGER", true, 2,
		 "The index of the group containing the label string.\n   Use 0 to indicate that this field is not used.", null);

    static CommandOption.Integer dataGroup = new CommandOption.Integer
		(BulkLoader.class, "data", "INTEGER", true, 3,
		 "The index of the group containing the data.", null);

    static CommandOption.Integer pruneCount = new CommandOption.Integer
        (BulkLoader.class, "prune-count", "N", false, 0,
         "Reduce features to those that occur more than N times.", null);
	
    /**
     *  Read the data from inputFile, then write all the words
     *   that do not occur <tt>pruneCount.value</tt> times or more to the pruned word file.
	 * 
	 *  @param prunedTokenizer the tokenizer that will be used to write instances
     */

    public static void generateStoplist(SimpleTokenizer prunedTokenizer)
		throws IOException {

		CsvIterator reader = new CsvIterator(new FileReader(inputFile.value),
                                             lineRegex.value,
											 dataGroup.value,
											 labelGroup.value,
											 nameGroup.value);

		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		Alphabet alphabet = new Alphabet();
		
		CharSequenceLowercase csl = new CharSequenceLowercase();
        SimpleTokenizer st = prunedTokenizer.deepClone();
		StringList2FeatureSequence sl2fs = new StringList2FeatureSequence(alphabet);
		FeatureCountPipe featureCounter = new FeatureCountPipe(alphabet, null);

		if (! preserveCase.value) {
			pipes.add(csl);
		}
		pipes.add(st);
		pipes.add(sl2fs);
		pipes.add(featureCounter);

		Pipe serialPipe = new SerialPipes(pipes);

		Iterator<Instance> iterator = serialPipe.newIteratorFrom(reader);

        int count = 0;

        // We aren't really interested in the instance itself,
		//  just the total feature counts.
        while (iterator.hasNext()) {
            count++;
            if (count % 100000 == 0) {
				System.out.println(count);
            }
            iterator.next();
		}

		featureCounter.addPrunedWordsToStoplist(prunedTokenizer, pruneCount.value);
	}


    public static void writeInstanceList(SimpleTokenizer prunedTokenizer)
		throws IOException {

		CsvIterator reader = new CsvIterator(new FileReader(inputFile.value),
                                             lineRegex.value,
											 dataGroup.value,
											 labelGroup.value,
											 nameGroup.value);

		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		Alphabet alphabet = new Alphabet();
		
		CharSequenceLowercase csl = new CharSequenceLowercase();
		StringList2FeatureSequence sl2fs = new StringList2FeatureSequence(alphabet);

		if (! preserveCase.value) {
			pipes.add(csl);
		}
		pipes.add(prunedTokenizer);
		pipes.add(sl2fs);

		Pipe serialPipe = new SerialPipes(pipes);

		InstanceList instances = new InstanceList(serialPipe);
		instances.addThruPipe(reader);
		instances.save(outputFile.value);
	}


	public static void main (String[] args) throws IOException {

		// Process the command-line options
        CommandOption.setSummary (BulkLoader.class,
                                  "Efficient tool for importing large amounts of text into Mallet format");
        CommandOption.process (BulkLoader.class, args);


		
		SimpleTokenizer tokenizer = null;

		if (stoplistFile.value != null) {
			tokenizer = new SimpleTokenizer(stoplistFile.value);
		}
		else if (removeStopWords.value) {
			tokenizer = new SimpleTokenizer(SimpleTokenizer.USE_DEFAULT_ENGLISH_STOPLIST);
		}
		else {
			tokenizer = new SimpleTokenizer(SimpleTokenizer.USE_EMPTY_STOPLIST);
		}

		if (pruneCount.value > 0) {
			generateStoplist(tokenizer);
		}

		writeInstanceList(tokenizer);
	}

}
