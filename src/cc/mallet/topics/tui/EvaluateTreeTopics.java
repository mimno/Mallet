package cc.mallet.topics.tui;

import java.io.File;
import java.io.PrintStream;

import cc.mallet.topics.MarginalProbEstimator;
import cc.mallet.topics.tree.TreeMarginalProbEstimator;
import cc.mallet.topics.tree.TreeTopicInferencer;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;

public class EvaluateTreeTopics {
	// common options in mallet
	
	static CommandOption.String inputFile = new CommandOption.String
	(EvaluateTreeTopics.class, "input", "FILENAME", true, null,
	 "The filename from which to read the list of testing instances.  Use - for stdin.  " +
	 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);	
    
    static CommandOption.Integer randomSeed = new CommandOption.Integer
    (EvaluateTreeTopics.class, "random-seed", "INTEGER", true, 0,
     "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);    
	
    static CommandOption.String evaluatorFilename = new CommandOption.String
	(EvaluateTreeTopics.class, "evaluator", "FILENAME", true, null,
	 "A serialized topic evaluator from a trained topic model.\n" + 
     "By default this is null, indicating that no file will be read.", null);
    	
    static CommandOption.String docProbabilityFile = new CommandOption.String
    (EvaluateTreeTopics.class, "output-doc-probs", "FILENAME", true, null,
     "The filename in which to write the inferred log probabilities\n" +
	 "per document.  " +
     "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String probabilityFile = new CommandOption.String
    (EvaluateTreeTopics.class, "output-prob", "FILENAME", true, "-",
     "The filename in which to write the inferred log probability of the testing set\n" +
     "Use - for stdout, which is the default.", null);

	static CommandOption.Integer numParticles = new CommandOption.Integer
    (EvaluateTreeTopics.class, "num-particles", "INTEGER", true, 10,
     "The number of particles to use in left-to-right evaluation.", null);

	static CommandOption.Boolean usingResampling = new CommandOption.Boolean
    (EvaluateTreeTopics.class, "use-resampling", "TRUE|FALSE", false, false,
     "Whether to resample topics in left-to-right evaluation. Resampling is more accurate, but leads to quadratic scaling in the lenght of documents.", null);

	
	public static void main (String[] args) throws java.io.IOException {
        // Process the command-line options
		CommandOption.setSummary (EvaluateTreeTopics.class,
                                  "Estimate the marginal probability of new documents.");
        CommandOption.process (EvaluateTreeTopics.class, args);

		if (evaluatorFilename.value == null) {
			System.err.println("You must specify a serialized topic evaluator. Use --help to list options.");
			System.exit(0);
		}

		if (inputFile.value == null) {
			System.err.println("You must specify a serialized instance list. Use --help to list options.");
			System.exit(0);
		}

		try {
			
			PrintStream docProbabilityStream = null;
			if (docProbabilityFile.value != null) {
				docProbabilityStream = new PrintStream(docProbabilityFile.value);
			}
			
			PrintStream outputStream = System.out;
			if (probabilityFile.value != null &&
				! probabilityFile.value.equals("-")) {
				outputStream = new PrintStream(probabilityFile.value);
			}
			
			TreeMarginalProbEstimator evaluator = 
					TreeMarginalProbEstimator.read(new File(evaluatorFilename.value));

			InstanceList instances = InstanceList.load (new File(inputFile.value));
			
			evaluator.setRandomSeed(randomSeed.value);

			outputStream.println(evaluator.evaluateLeftToRight(instances, numParticles.value, 
															   usingResampling.value,
															   docProbabilityStream));
			

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		
	}
}
