package cc.mallet.topics.tui;

import cc.mallet.util.*;
import cc.mallet.types.*;
import cc.mallet.topics.*;

import java.io.*;

public class EvaluateTopics {

    static CommandOption.String evaluatorFilename = new CommandOption.String
        (EvaluateTopics.class, "evaluator", "FILENAME", true, null,
		 "A serialized topic evaluator from a trained topic model.\n" + 
         "By default this is null, indicating that no file will be read.", null);

	static CommandOption.String inputFile = new CommandOption.String
		(EvaluateTopics.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of instances\n" +
		 "for which topics should be inferred.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
    static CommandOption.String docTopicsFile = new CommandOption.String
        (EvaluateTopics.class, "output-doc-topics", "FILENAME", true, null,
         "The filename in which to write the inferred topic\n" +
		 "proportions per document.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.Double docTopicsThreshold = new CommandOption.Double
		(EvaluateTopics.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
         "When writing topic proportions per document with --output-doc-topics, " +
         "do not print topics with proportions less than this threshold value.", null);

    static CommandOption.Integer docTopicsMax = new CommandOption.Integer
        (EvaluateTopics.class, "doc-topics-max", "INTEGER", true, -1,
         "When writing topic proportions per document with --output-doc-topics, " +
         "do not print more than INTEGER number of topics.  "+
         "A negative value indicates that all topics should be printed.", null);

	static CommandOption.Integer numParticles = new CommandOption.Integer
        (EvaluateTopics.class, "num-particles", "INTEGER", true, 10,
         "The number of particles to use in left-to-right evaluation.", null);

	static CommandOption.Integer numIterations = new CommandOption.Integer
        (EvaluateTopics.class, "num-iterations", "INTEGER", true, 100,
         "The number of iterations of Gibbs sampling.", null);

    static CommandOption.Integer sampleInterval = new CommandOption.Integer
        (EvaluateTopics.class, "sample-interval", "INTEGER", true, 10,
         "The number of iterations between saved samples.", null);

    static CommandOption.Integer burnInIterations = new CommandOption.Integer
        (EvaluateTopics.class, "burn-in", "INTEGER", true, 10,
         "The number of iterations before the first sample is saved.", null);

    static CommandOption.Integer randomSeed = new CommandOption.Integer
        (EvaluateTopics.class, "random-seed", "INTEGER", true, 0,
         "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	public static void main (String[] args) {

        // Process the command-line options
		CommandOption.setSummary (EvaluateTopics.class,
                                  "Estimate the marginal probability of new documents under ");
        CommandOption.process (EvaluateTopics.class, args);
		
		if (evaluatorFilename.value == null) {
			System.err.println("You must specify a serialized topic evaluator. Use --help to list options.");
			System.exit(0);
		}

		if (inputFile.value == null) {
			System.err.println("You must specify a serialized instance list. Use --help to list options.");
			System.exit(0);
		}

		try {
			
			MarginalProbEstimator evaluator = 
				MarginalProbEstimator.read(new File(evaluatorFilename.value));

			InstanceList instances = InstanceList.load (new File(inputFile.value));

			System.out.println(evaluator.evaluateLeftToRight(instances, numParticles.value));
			

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
	}
}
