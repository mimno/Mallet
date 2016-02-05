package cc.mallet.util;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;

import java.util.*;
import java.util.logging.*;
import java.io.*;

/**
 *  This class reads through two files (data and metadata), 
 *	tokenizing metadata for use as a label vector.
 */

public class DBBulkLoader {

	protected static Logger logger = MalletLogger.getLogger(DBBulkLoader.class.getName());

	static CommandOption.SpacedStrings inputFiles = new CommandOption.SpacedStrings
		(DBBulkLoader.class, "input", "FILE [FILE ...]", true, null,
		 "The file containing data, one instance per line", null);

	static CommandOption.String outputDatabase = new CommandOption.String
		(DBBulkLoader.class, "output", "STRING", true, "mallet-db",
		 "Write the instance list to this database", null);

	static CommandOption.Boolean preserveCase = new CommandOption.Boolean
		(DBBulkLoader.class, "preserve-case", "[TRUE|FALSE]", false, false,
		 "If true, do not force all strings to lowercase.", null);

	static CommandOption.File vocabularyFile = new CommandOption.File
	   (DBBulkLoader.class, "vocabulary", "FILE", true, null,
		"Read newline-separated words from this file.", null);

	static CommandOption.SpacedStrings replacementFiles = new CommandOption.SpacedStrings
		(DBBulkLoader.class, "replacement-files", "FILE [FILE ...]", true, null,
		 "files containing string replacements, one per line:\n	 'A B [tab] C' replaces A B with C,\n	 'A B' replaces A B with A_B", null);

	static CommandOption.SpacedStrings deletionFiles = new CommandOption.SpacedStrings
		(DBBulkLoader.class, "deletion-files", "FILE [FILE ...]", true, null,
		 "files containing strings to delete after replacements but before tokenization (ie multiword stop terms)", null);

	static CommandOption.File stoplistFile = new CommandOption.File
		(DBBulkLoader.class, "stoplist", "FILE", true, null,
		 "Read newline-separated words from this file and remove them from text.", null);

	static CommandOption.Boolean keepSequence = new CommandOption.Boolean
		(DBBulkLoader.class, "keep-sequence", "[TRUE|FALSE]", false, true,
		 "If true, final data will be a FeatureSequence rather than a FeatureVector.", null);

	static CommandOption.Integer pruneCount = new CommandOption.Integer
		(DBBulkLoader.class, "prune-count", "N", false, 0,
		 "Reduce features to those that occur more than N times.", null);
	
	/**
	 *  Read the data from inputFiles, then write all the words
	 *	that do not occur <tt>pruneCount.value</tt> times or more to the pruned word file.
	 * 
	 *  @param prunedTokenizer the tokenizer that will be used to write instances
	 */

	public static void generateStoplist(SimpleTokenizer prunedTokenizer, NGramPreprocessor preprocessor)
		throws IOException {

		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		Alphabet alphabet = new Alphabet();
		
		SimpleTokenizer st = prunedTokenizer.deepClone();
		StringList2FeatureSequence sl2fs = new StringList2FeatureSequence(alphabet);
		FeatureCountPipe featureCounter = new FeatureCountPipe(alphabet, null);

		pipes.add(preprocessor);
		pipes.add(st);
		pipes.add(sl2fs);
		pipes.add(featureCounter);

		Pipe serialPipe = new SerialPipes(pipes);

		for (String filename: inputFiles.value) {
			logger.info("pruning from " + filename);

			CsvIterator reader = new CsvIterator(new FileReader(filename),
												 "(.*?)\\t(.*?)\\t(.*)", 3, 2, 1);
			
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
		}

		featureCounter.addPrunedWordsToStoplist(prunedTokenizer, pruneCount.value);
	}


	public static void writeInstanceList(ArrayList<Pipe> pipes) throws Exception {

		Pipe serialPipe = new SerialPipes(pipes);
		
		DBInstanceStore saver = new DBInstanceStore(outputDatabase.value);
		for (String filename: inputFiles.value) {
			logger.info("importing from " + filename);
			CsvIterator reader = new CsvIterator(new FileReader(filename),
												 "(.*?)\\t(.*?)\\t(.*)", 3, 2, 1);
			
			saver.saveInstances(serialPipe.newIteratorFrom(reader));
		}
		
		saver.saveAlphabets(serialPipe.getDataAlphabet(), serialPipe.getTargetAlphabet());
		saver.cleanup();
	}


	public static void main (String[] args) throws Exception {

		logger.info("starting");

		// Process the command-line options
		CommandOption.setSummary (DBBulkLoader.class,
								  "Efficient tool for importing large amounts of text and saving to an embedded Java database");
		CommandOption.process (DBBulkLoader.class, args);

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
		
		if (vocabularyFile.value != null) {
			Alphabet alphabet = AlphabetFactory.loadFromFile(vocabularyFile.value);
			alphabet.stopGrowth();

			logger.info("loaded alphabet of size " + alphabet.size());

			ArrayList<Pipe> pipes = new ArrayList<Pipe>();
			pipes.add(preprocessor);
			pipes.add(new FixedVocabTokenizer(alphabet));

			writeInstanceList(pipes);
		}
		else {
			SimpleTokenizer tokenizer = new SimpleTokenizer(stoplistFile.value);
			
			if (pruneCount.value > 0) {
				generateStoplist(tokenizer, preprocessor);
			}
			
			ArrayList<Pipe> pipes = new ArrayList<Pipe>();
			Alphabet alphabet = new Alphabet();
			
			CharSequenceLowercase csl = new CharSequenceLowercase();
			StringList2FeatureSequence sl2fs = new StringList2FeatureSequence(alphabet);
			
			pipes.add(preprocessor);
			pipes.add(tokenizer);
			pipes.add(sl2fs);
			
			writeInstanceList(pipes);
		}
	}

}
