package cc.mallet.topics.tui;

import java.io.File;

import cc.mallet.topics.tree.VocabGenerator;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;

public class GenerateVocab {
	
	// common options in mallet
	static CommandOption.SpacedStrings inputFile = new CommandOption.SpacedStrings
	(GenerateVocab.class, "input", "FILENAME [FILENAME ...]", true, null,
	 "The filename from which to read the list of training instances. " +
	 "Support multiple languages, each language should have its own file. " +
     "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String vocabFile = new CommandOption.String
	(GenerateVocab.class, "vocab", "FILENAME", true, null,
	 "The vocabulary file.", null);
	
	static CommandOption.Boolean tfidfRank = new CommandOption.Boolean
	(GenerateVocab.class, "tfidf-rank", "true|false", false, true,
	"Rank vocab by the averaged tfidf of words, or by frequency.", null);
	
	static CommandOption.Double tfidfThresh = new CommandOption.Double
	(GenerateVocab.class, "tfidf-thresh", "DECIMAL", true, 1.0,
	 "The thresh for tfidf to filter out words.",null);
	
	static CommandOption.Double freqThresh = new CommandOption.Double
	(GenerateVocab.class, "freq-thresh", "DECIMAL", true, 1.0,
	 "The thresh for frequency to filter out words.",null);
	
	static CommandOption.Double wordLength = new CommandOption.Double
	(GenerateVocab.class, "word-length", "DECIMAL", true, 3.0,
	 "Keep words with length equal or large than the thresh.",null);

	
	public static void main (String[] args) throws java.io.IOException {
		// Process the command-line options
		CommandOption.setSummary (GenerateVocab.class,
								  "Filtering words by tfidf, frequency, word-length, and generate the vocab.");
		CommandOption.process (GenerateVocab.class, args);
		
		int numLanguages = inputFile.value.length;
		InstanceList[] instances = new InstanceList[ numLanguages ];
		for (int i=0; i < instances.length; i++) {
			instances[i] = InstanceList.load(new File(inputFile.value[i]));
			System.out.println ("Data " + i + " loaded. Total number of documents: " + instances[i].size());
		}
		
		
		VocabGenerator.genVocab(instances, vocabFile.value, tfidfRank.value, tfidfThresh.value, 
			freqThresh.value, wordLength.value);			
		
	}

}
