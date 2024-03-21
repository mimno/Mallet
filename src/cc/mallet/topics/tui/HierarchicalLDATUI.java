package cc.mallet.topics.tui;

import cc.mallet.util.CommandOption;
import cc.mallet.util.Randoms;
import cc.mallet.types.InstanceList;
import cc.mallet.topics.HierarchicalLDA;

import java.io.*;

public class HierarchicalLDATUI {
	
	static CommandOption.String inputFile = new CommandOption.String
		(HierarchicalLDATUI.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String testingFile = new CommandOption.String
		(HierarchicalLDATUI.class, "testing", "FILENAME", true, null,
		 "The filename from which to read the list of instances for held-out likelihood calculation.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String stateFile = new CommandOption.String
		(HierarchicalLDATUI.class, "output-state", "FILENAME", true, null,
		 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);
	
	static CommandOption.Integer randomSeed = new CommandOption.Integer
		(HierarchicalLDATUI.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);
	
	static CommandOption.Integer numIterations = new CommandOption.Integer
	  	(HierarchicalLDATUI.class, "num-iterations", "INTEGER", true, 1000,
		 "The number of iterations of Gibbs sampling.", null);

	static CommandOption.Boolean showProgress = new CommandOption.Boolean
		(HierarchicalLDATUI.class, "show-progress", "BOOLEAN", false, true,
		 "If true, print a character to standard output after every sampling iteration.", null);

	static CommandOption.Integer showTopicsInterval = new CommandOption.Integer
		(HierarchicalLDATUI.class, "show-topics-interval", "INTEGER", true, 50,
		 "The number of iterations between printing a brief summary of the topics so far.", null);

	static CommandOption.Integer topWords = new CommandOption.Integer
		(HierarchicalLDATUI.class, "num-top-words", "INTEGER", true, 20,
		 "The number of most probable words to print for each topic after model estimation.", null);

	static CommandOption.Integer numLevels = new CommandOption.Integer
		(HierarchicalLDATUI.class, "num-levels", "INTEGER", true, 3,
		 "The number of levels in the tree.", null);

	static CommandOption.Double alpha = new CommandOption.Double
		(HierarchicalLDATUI.class, "alpha", "DECIMAL", true, 10.0,
		 "Alpha parameter: smoothing over level distributions.", null);

	static CommandOption.Double gamma = new CommandOption.Double
		(HierarchicalLDATUI.class, "gamma", "DECIMAL", true, 1.0,
		 "Gamma parameter: CRP smoothing parameter; number of imaginary customers at next, as yet unused table", null);

	static CommandOption.Double eta = new CommandOption.Double
		(HierarchicalLDATUI.class, "eta", "DECIMAL", true, 0.1,
		 "Eta parameter: smoothing over topic-word distributions", null);
	
	public static void main (String[] args) throws java.io.IOException {

		// Process the command-line options
		CommandOption.setSummary (HierarchicalLDATUI.class,
								  "Hierarchical LDA with a fixed tree depth.");
		CommandOption.process (HierarchicalLDATUI.class, args);
		
		// Load instance lists

		if (inputFile.value() == null) {
			System.err.println("Input instance list is required, use --input option");
			System.exit(1);
		}

		InstanceList instances = InstanceList.load(new File(inputFile.value()));
		InstanceList testing = null;
		if (testingFile.value() != null) {
			testing = InstanceList.load(new File(testingFile.value()));
		}
	
		HierarchicalLDA hlda = new HierarchicalLDA();
		
		// Set hyperparameters

		hlda.setAlpha(alpha.value());
		hlda.setGamma(gamma.value());
		hlda.setEta(eta.value());
		
		// Display preferences

		hlda.setTopicDisplay(showTopicsInterval.value(), topWords.value());
		hlda.setProgressDisplay(showProgress.value());

		// Initialize random number generator

		Randoms random = null;
		if (randomSeed.value() == 0) {
			random = new Randoms();
		}
		else {
			random = new Randoms(randomSeed.value());
		}

		// Initialize and start the sampler

		hlda.initialize(instances, testing, numLevels.value(), random);
		hlda.estimate(numIterations.value());
		
		// Output results

		if (stateFile.value() != null) {
			hlda.printState(new PrintWriter(new BufferedWriter(new FileWriter(stateFile.value()))));
		}

		if (testing != null) {
			double empiricalLikelihood = hlda.empiricalLikelihood(1000, testing);
			System.out.println("Empirical likelihood: " + empiricalLikelihood);
		}
		
	}
}
