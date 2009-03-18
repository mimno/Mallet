package cc.mallet.topics.tui;

import cc.mallet.util.*;
import cc.mallet.types.*;
import cc.mallet.topics.*;

import java.io.*;

public class InferTopics {

    static CommandOption.String inferencerFilename = new CommandOption.String
        (InferTopics.class, "inferencer", "FILENAME", true, null,
		 "A serialized topic inferencer from a trained topic model.\n" + 
         "By default this is null, indicating that no file will be read.", null);

	static CommandOption.String inputFile = new CommandOption.String
		(InferTopics.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of instances\n" +
		 "for which topics should be inferred.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
    static CommandOption.String docTopicsFile = new CommandOption.String
        (InferTopics.class, "output-doc-topics", "FILENAME", true, null,
         "The filename in which to write the inferred topic\n" +
		 "proportions per document.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.Double docTopicsThreshold = new CommandOption.Double
		(InferTopics.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
         "When writing topic proportions per document with --output-doc-topics, " +
         "do not print topics with proportions less than this threshold value.", null);

    static CommandOption.Integer docTopicsMax = new CommandOption.Integer
        (InferTopics.class, "doc-topics-max", "INTEGER", true, -1,
         "When writing topic proportions per document with --output-doc-topics, " +
         "do not print more than INTEGER number of topics.  "+
         "A negative value indicates that all topics should be printed.", null);

	static CommandOption.Integer numIterations = new CommandOption.Integer
        (InferTopics.class, "num-iterations", "INTEGER", true, 100,
         "The number of iterations of Gibbs sampling.", null);

    static CommandOption.Integer sampleInterval = new CommandOption.Integer
        (InferTopics.class, "sample-interval", "INTEGER", true, 10,
         "The number of iterations between saved samples.", null);

    static CommandOption.Integer burnInIterations = new CommandOption.Integer
        (InferTopics.class, "burn-in", "INTEGER", true, 10,
         "The number of iterations before the first sample is saved.", null);

    static CommandOption.Integer randomSeed = new CommandOption.Integer
        (InferTopics.class, "random-seed", "INTEGER", true, 0,
         "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	public static void main (String[] args) {

        // Process the command-line options                                                                           
		CommandOption.setSummary (InferTopics.class,
                                  "Use an existing topic model to infer topic distributions for new documents");
        CommandOption.process (InferTopics.class, args);
		
		if (inferencerFilename.value == null) {
			System.err.println("You must specify a serialized topic inferencer. Use --help to list options.");
			System.exit(0);
		}

		if (inputFile.value == null) {
			System.err.println("You must specify a serialized instance list. Use --help to list options.");
			System.exit(0);
		}

		try {
			
			TopicInferencer inferencer = 
				TopicInferencer.read(new File(inferencerFilename.value));

			InstanceList instances = InstanceList.load (new File(inputFile.value));

			inferencer.writeInferredDistributions(instances, new File(docTopicsFile.value),
												  numIterations.value, sampleInterval.value,
												  burnInIterations.value,
												  docTopicsThreshold.value, docTopicsMax.value);
			

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
	}
}
