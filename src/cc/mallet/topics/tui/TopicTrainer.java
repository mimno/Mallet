/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics.tui;

import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;
import cc.mallet.types.InstanceList;
import cc.mallet.types.FeatureSequence;
import cc.mallet.topics.*;
import cc.mallet.pipe.iterator.DBInstanceIterator;

import java.util.logging.*;
import java.io.*;

/** Create a simple LDA topic model, with some reporting options.
 */

public class TopicTrainer {

	// Input options
	
	static CommandOption.String inputFile = new CommandOption.String
		(TopicTrainer.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

	static CommandOption.String inputModelFilename = new CommandOption.String
		(TopicTrainer.class, "input-model", "FILENAME", true, null,
		 "The filename from which to read the binary topic model. The --input option is ignored. " +
		 "By default this is null, indicating that no file will be read.", null);

	static CommandOption.String inputStateFilename = new CommandOption.String
		(TopicTrainer.class, "input-state", "FILENAME", true, null,
		 "The filename from which to read the gzipped Gibbs sampling state created by --output-state. " +
		 "The original input file must be included, using --input. " + 
		 "By default this is null, indicating that no file will be read.", null);

	// Model output options

	static CommandOption.String outputModelFilename = new CommandOption.String
		(TopicTrainer.class, "output-model", "FILENAME", true, null,
		 "The filename in which to write the binary topic model at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String stateFile = new CommandOption.String
		(TopicTrainer.class, "output-state", "FILENAME", true, null,
		 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Integer outputModelInterval = new CommandOption.Integer
		(TopicTrainer.class, "output-model-interval", "INTEGER", true, 0,
		 "The number of iterations between writing the model (and its Gibbs sampling state) to a binary file.  " +
		 "You must also set the --output-model to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Integer outputStateInterval = new CommandOption.Integer
        (TopicTrainer.class, "output-state-interval", "INTEGER", true, 0,
         "The number of iterations between writing the sampling state to a text file.  " +
         "You must also set the --output-state to use this option, whose argument will be the prefix of the filenames.", null);

	// Tools

    static CommandOption.String inferencerFilename = new CommandOption.String
        (TopicTrainer.class, "inferencer-filename", "FILENAME", true, null,
         "A topic inferencer applies a previously trained topic model to new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String evaluatorFilename = new CommandOption.String
        (TopicTrainer.class, "evaluator-filename", "FILENAME", true, null,
         "A held-out likelihood evaluator for new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

	// Reports

	static CommandOption.String topicKeysFile = new CommandOption.String
		(TopicTrainer.class, "output-topic-keys", "FILENAME", true, null,
         "The filename in which to write the top words for each topic and any Dirichlet parameters.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Integer topWords = new CommandOption.Integer
		(TopicTrainer.class, "num-top-words", "INTEGER", true, 20,
		 "The number of most probable words to print for each topic after model estimation.", null);

	static CommandOption.Integer showTopicsInterval = new CommandOption.Integer
		(TopicTrainer.class, "show-topics-interval", "INTEGER", true, 50,
		 "The number of iterations between printing a brief summary of the topics so far.", null);

	static CommandOption.String topicWordWeightsFile = new CommandOption.String
		(TopicTrainer.class, "topic-word-weights-file", "FILENAME", true, null,
         "The filename in which to write unnormalized weights for every topic and word type.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String wordTopicCountsFile = new CommandOption.String
		(TopicTrainer.class, "word-topic-counts-file", "FILENAME", true, null,
         "The filename in which to write a sparse representation of topic-word assignments.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String diagnosticsFile = new CommandOption.String
		(TopicTrainer.class, "diagnostics-file", "FILENAME", true, null,
         "The filename in which to write measures of topic quality, in XML format.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicReportXMLFile = new CommandOption.String
		(TopicTrainer.class, "xml-topic-report", "FILENAME", true, null,
         "The filename in which to write the top words for each topic and any Dirichlet parameters in XML format.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicPhraseReportXMLFile = new CommandOption.String
		(TopicTrainer.class, "xml-topic-phrase-report", "FILENAME", true, null,
		 "The filename in which to write the top words and phrases for each topic and any Dirichlet parameters in XML format.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String docTopicsFile = new CommandOption.String
		(TopicTrainer.class, "output-doc-topics", "FILENAME", true, null,
		 "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Double docTopicsThreshold = new CommandOption.Double
		(TopicTrainer.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
		 "When writing topic proportions per document with --output-doc-topics, " +
		 "do not print topics with proportions less than this threshold value.", null);

	static CommandOption.Integer docTopicsMax = new CommandOption.Integer
		(TopicTrainer.class, "doc-topics-max", "INTEGER", true, -1,
		 "When writing topic proportions per document with --output-doc-topics, " +
		 "do not print more than INTEGER number of topics.  "+
		 "A negative value indicates that all topics should be printed.", null);

	// Model parameters

	static CommandOption.Integer numTopics = new CommandOption.Integer
		(TopicTrainer.class, "num-topics", "INTEGER", true, 10,
		 "The number of topics to fit.", null);

	static CommandOption.Integer numThreads = new CommandOption.Integer
		(TopicTrainer.class, "num-threads", "INTEGER", true, 1,
		 "The number of threads for parallel training.", null);

	static CommandOption.Integer numIterations = new CommandOption.Integer
		(TopicTrainer.class, "num-iterations", "INTEGER", true, 1000,
		 "The number of iterations of Gibbs sampling.", null);

	static CommandOption.Integer numMaximizationIterations = new CommandOption.Integer
		(TopicTrainer.class, "num-icm-iterations", "INTEGER", true, 0,
		 "The number of iterations of iterated conditional modes (topic maximization).", null);

	static CommandOption.Boolean noInference = new CommandOption.Boolean
		(TopicTrainer.class, "no-inference", "true|false", false, false,
		 "Do not perform inference, just load a saved model and create a report. Equivalent to --num-iterations 0.", null);

	static CommandOption.Integer randomSeed = new CommandOption.Integer
		(TopicTrainer.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	// Hyperparameters and hyperparameter optimization

    static CommandOption.Integer optimizeInterval = new CommandOption.Integer
        (TopicTrainer.class, "optimize-interval", "INTEGER", true, 0,
         "The number of iterations between reestimating dirichlet hyperparameters.", null);

    static CommandOption.Integer optimizeBurnIn = new CommandOption.Integer
        (TopicTrainer.class, "optimize-burn-in", "INTEGER", true, 200,
         "The number of iterations to run before first estimating dirichlet hyperparameters.", null);

	static CommandOption.Boolean useSymmetricAlpha = new CommandOption.Boolean
		(TopicTrainer.class, "use-symmetric-alpha", "true|false", false, false,
		 "Only optimize the concentration parameter of the prior over document-topic distributions. This may reduce the number of very small, poorly estimated topics, but may disperse common words over several topics.", null);

	static CommandOption.Double alpha = new CommandOption.Double
		(TopicTrainer.class, "alpha", "DECIMAL", true, 50.0,
		 "Alpha parameter: smoothing over topic distribution.",null);

	static CommandOption.Double beta = new CommandOption.Double
		(TopicTrainer.class, "beta", "DECIMAL", true, 0.01,
		 "Beta parameter: smoothing over unigram distribution.",null);

	private static Logger logger = MalletLogger.getLogger(TopicTrainer.class.getName());

	public static void main (String[] args) throws java.io.IOException {

		// Process the command-line options
		CommandOption.setSummary (TopicTrainer.class,
								  "A tool for estimating, saving and printing diagnostics for topic models, such as LDA.");
		try {
			CommandOption.process (TopicTrainer.class, args);
		} catch (IllegalArgumentException e) {
			logger.warning("");
			logger.warning(e.getMessage());
			System.exit(0);
		}

		ParallelTopicModel topicModel = null;
		
		if (inputModelFilename.value != null) {
			
			if (inputFile.value != null) { 
				logger.warning("The --input option is not compatible with --input-model.");
			}
			if (inputStateFilename.value != null) {
				logger.warning("The --input-state option is not compatible with --input-model.");
			}
			
			try {
				topicModel = ParallelTopicModel.read(new File(inputModelFilename.value));
			} catch (Exception e) {
				logger.warning("Unable to restore saved topic model " + 
							   inputModelFilename.value + ": " + e);
				System.exit(1);
			}
		} 
		else {
			InstanceList training = null;
			try {
				if (inputFile.value.startsWith("db:")) {
					training = DBInstanceIterator.getInstances(inputFile.value.substring(3));
				}
				else {
					training = InstanceList.load (new File(inputFile.value));
				}
			} catch (Exception e) {
				System.err.println("Unable to restore instance list " +
								   inputFile.value + ": " + e);
				System.exit(1);
			}

			logger.info("Data loaded.");
			
			if (training.size() > 0 &&
				training.get(0) != null) {
				Object data = training.get(0).getData();
				if (! (data instanceof FeatureSequence)) {
					logger.warning("Topic modeling currently only supports feature sequences: use --keep-sequence option when importing data.");
					System.exit(1);
				}
			}

			topicModel = new ParallelTopicModel (numTopics.value, alpha.value, beta.value);
			if (randomSeed.value != 0) {
				topicModel.setRandomSeed(randomSeed.value);
			}

			topicModel.addInstances(training);

			if (inputStateFilename.value != null) {
				logger.info("Initializing from saved state.");
				topicModel.initializeFromState(new File(inputStateFilename.value));
			}
		}

		topicModel.setTopicDisplay(showTopicsInterval.value, topWords.value);

		topicModel.setNumIterations(numIterations.value);
		topicModel.setOptimizeInterval(optimizeInterval.value);
		topicModel.setBurninPeriod(optimizeBurnIn.value);
		topicModel.setSymmetricAlpha(useSymmetricAlpha.value);

		if (outputStateInterval.value != 0) {
			topicModel.setSaveState(outputStateInterval.value, stateFile.value);
		}

		if (outputModelInterval.value != 0) {
			topicModel.setSaveSerializedModel(outputModelInterval.value, outputModelFilename.value);
		}

		topicModel.setNumThreads(numThreads.value);
		
		if (! noInference.value()) {
			topicModel.estimate();
		}

		if (numMaximizationIterations.value > 0) {
			topicModel.maximize(numMaximizationIterations.value);
		}

		if (topicKeysFile.value != null) {
			topicModel.printTopWords(new File(topicKeysFile.value), topWords.value, false);
		}

		if (diagnosticsFile.value != null) {
			PrintWriter out = new PrintWriter(diagnosticsFile.value);
			TopicModelDiagnostics diagnostics = new TopicModelDiagnostics(topicModel, topWords.value);
			out.println(diagnostics.toXML());
			out.close();
		}

		if (topicReportXMLFile.value != null) {
			PrintWriter out = new PrintWriter(topicReportXMLFile.value);
			topicModel.topicXMLReport(out, topWords.value);
			out.close();
		}

		if (topicPhraseReportXMLFile.value != null) {
			PrintWriter out = new PrintWriter(topicPhraseReportXMLFile.value);
			topicModel.topicPhraseXMLReport(out, topWords.value);
			out.close();
		}

		if (stateFile.value != null && outputStateInterval.value == 0) {
			topicModel.printState (new File(stateFile.value));
		}

		if (docTopicsFile.value != null) {
			PrintWriter out = new PrintWriter (new FileWriter ((new File(docTopicsFile.value))));
			if (docTopicsThreshold.value == 0.0) {
				topicModel.printDenseDocumentTopics(out);
			}
			else {
				topicModel.printDocumentTopics(out, docTopicsThreshold.value, docTopicsMax.value);
			}
			out.close();
		}

		if (topicWordWeightsFile.value != null) {
			topicModel.printTopicWordWeights(new File (topicWordWeightsFile.value));
		}

		if (wordTopicCountsFile.value != null) {
			topicModel.printTypeTopicCounts(new File (wordTopicCountsFile.value));
		}

		if (outputModelFilename.value != null) {
			assert (topicModel != null);
			try {

				ObjectOutputStream oos =
					new ObjectOutputStream (new FileOutputStream (outputModelFilename.value));
				oos.writeObject (topicModel);
				oos.close();

			} catch (Exception e) {
				logger.warning("Couldn't write topic model to filename " + outputModelFilename.value);
			}
		}

		if (inferencerFilename.value != null) {
			try {

				ObjectOutputStream oos = 
					new ObjectOutputStream(new FileOutputStream(inferencerFilename.value));
				oos.writeObject(topicModel.getInferencer());
				oos.close();

			} catch (Exception e) {
				logger.warning("Couldn't create inferencer: " + e.getMessage());
			}
					
		}

		if (evaluatorFilename.value != null) {
			try {

				ObjectOutputStream oos = 
					new ObjectOutputStream(new FileOutputStream(evaluatorFilename.value));
				oos.writeObject(topicModel.getProbEstimator());
				oos.close();

			} catch (Exception e) {
				logger.warning("Couldn't create evaluator: " + e.getMessage());
			}
					
		}
	}
}
