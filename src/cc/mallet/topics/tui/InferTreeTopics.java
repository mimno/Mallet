package cc.mallet.topics.tui;

import java.io.File;

import cc.mallet.topics.TopicInferencer;
import cc.mallet.topics.tree.VocabGenerator;
import cc.mallet.topics.tree.OntologyWriter;
import cc.mallet.topics.tree.TreeTopicInferencer;
import cc.mallet.topics.tree.TreeTopicSamplerFast;
import cc.mallet.topics.tree.TreeTopicSamplerFastEst;
import cc.mallet.topics.tree.TreeTopicSamplerFastEstSortD;
import cc.mallet.topics.tree.TreeTopicSamplerFastSortD;
import cc.mallet.topics.tree.TreeTopicSamplerInterface;
import cc.mallet.topics.tree.TreeTopicSamplerNaive;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;

public class InferTreeTopics {
	
	// common options in mallet
	
	static CommandOption.String inputFile = new CommandOption.String
	(InferTreeTopics.class, "input", "FILENAME", true, null,
	 "The filename from which to read the list of testing instances.  Use - for stdin.  " +
	 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);	

	static CommandOption.Integer numIterations = new CommandOption.Integer
	(InferTreeTopics.class, "num-iterations", "INTEGER", true, 1000,
	 "The number of iterations of Gibbs sampling.", null);
		
    static CommandOption.String inferencerFilename = new CommandOption.String
    (InferTreeTopics.class, "inferencer", "FILENAME", true, null,
     "A topic inferencer applies a previously trained topic model to new documents." +
     "By default this is null, indicating that no file will be written.", null);
    
    static CommandOption.String docTopicsFile = new CommandOption.String
    (InferTreeTopics.class, "output-doc-topics", "FILENAME", true, null,
     "The filename in which to write the inferred topic\n" +
	 "proportions per document.  " +
     "By default this is null, indicating that no file will be written.", null);
    
    static CommandOption.Integer randomSeed = new CommandOption.Integer
    (InferTreeTopics.class, "random-seed", "INTEGER", true, 0,
     "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);    

	static CommandOption.Integer outputInteval = new CommandOption.Integer
	(InferTreeTopics.class, "output-interval", "INTEGER", true, 20,
	 "For each interval, the result files are output to the outputFolder.", null);    
		
	
	public static void main (String[] args) throws java.io.IOException {
		// Process the command-line options
		CommandOption.setSummary (InferTreeTopics.class,
								  "A tool for estimating, saving and printing diagnostics for topic models, such as LDA.");
		CommandOption.process (InferTreeTopics.class, args);
		
		if (inferencerFilename.value == null) {
			System.err.println("You must specify a serialized topic inferencer. Use --help to list options.");
			System.exit(0);
		}

		if (inputFile.value == null) {
			System.err.println("You must specify a serialized instance list. Use --help to list options.");
			System.exit(0);
		}
			
		try {
			InstanceList testlist = InstanceList.load (new File(inputFile.value));
			System.out.println ("Test data loaded.");
			
			TreeTopicInferencer inferencer = TreeTopicInferencer.read(new File(inferencerFilename.value));
			System.out.println("Inferencer loaded.");
			
			inferencer.setRandomSeed(randomSeed.value);
			
			inferencer.writeInferredDistributions(testlist, new File(docTopicsFile.value), 
					numIterations.value, outputInteval.value);
			
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		
	}

}
