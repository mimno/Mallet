/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics.tui;

import cc.mallet.util.CommandOption;
import cc.mallet.util.Randoms;
import cc.mallet.types.InstanceList;
import cc.mallet.types.FeatureSequence;
import cc.mallet.topics.*;
import cc.mallet.pipe.iterator.DBInstanceIterator;

import java.io.*;

/** Perform topic analysis in the style of LDA and its variants.
 *  @author <a href="mailto:mccallum@cs.umass.edu">Andrew McCallum</a>
 */

public class Vectors2Topics {

	static CommandOption.String inputFile = new CommandOption.String
		(Vectors2Topics.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

	static CommandOption.SpacedStrings languageInputFiles = new CommandOption.SpacedStrings
		(Vectors2Topics.class, "language-inputs", "FILENAME [FILENAME ...]", true, null,
		 "Filenames for polylingual topic model. Each language should have its own file, " +
		 "with the same number of instances in each file. If a document is missing in " + 
		 "one language, there should be an empty instance.", null);

	static CommandOption.String testingFile = new CommandOption.String
		(Vectors2Topics.class, "testing", "FILENAME", false, null,
		 "The filename from which to read the list of instances for empirical likelihood calculation.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String outputModelFilename = new CommandOption.String
		(Vectors2Topics.class, "output-model", "FILENAME", true, null,
		 "The filename in which to write the binary topic model at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String inputModelFilename = new CommandOption.String
		(Vectors2Topics.class, "input-model", "FILENAME", true, null,
		 "The filename from which to read the binary topic model to which the --input will be appended, " +
		 "allowing incremental training.  " +
		 "By default this is null, indicating that no file will be read.", null);

	static CommandOption.String inferencerFilename = new CommandOption.String
		(Vectors2Topics.class, "inferencer-filename", "FILENAME", true, null,
		 "A topic inferencer applies a previously trained topic model to new documents.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String evaluatorFilename = new CommandOption.String
		(Vectors2Topics.class, "evaluator-filename", "FILENAME", true, null,
		 "A held-out likelihood evaluator for new documents.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String stateFile = new CommandOption.String
		(Vectors2Topics.class, "output-state", "FILENAME", true, null,
		 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicKeysFile = new CommandOption.String
		(Vectors2Topics.class, "output-topic-keys", "FILENAME", true, null,
		 "The filename in which to write the top words for each topic and any Dirichlet parameters.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicWordWeightsFile = new CommandOption.String
		(Vectors2Topics.class, "topic-word-weights-file", "FILENAME", true, null,
		 "The filename in which to write unnormalized weights for every topic and word type.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String wordTopicCountsFile = new CommandOption.String
		(Vectors2Topics.class, "word-topic-counts-file", "FILENAME", true, null,
		 "The filename in which to write a sparse representation of topic-word assignments.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicReportXMLFile = new CommandOption.String
		(Vectors2Topics.class, "xml-topic-report", "FILENAME", true, null,
		 "The filename in which to write the top words for each topic and any Dirichlet parameters in XML format.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String topicPhraseReportXMLFile = new CommandOption.String
	(Vectors2Topics.class, "xml-topic-phrase-report", "FILENAME", true, null,
	   "The filename in which to write the top words and phrases for each topic and any Dirichlet parameters in XML format.  " +
	 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.String docTopicsFile = new CommandOption.String
		(Vectors2Topics.class, "output-doc-topics", "FILENAME", true, null,
		 "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
		 "By default this is null, indicating that no file will be written.", null);

	static CommandOption.Double docTopicsThreshold = new CommandOption.Double
		(Vectors2Topics.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
		 "When writing topic proportions per document with --output-doc-topics, " +
		 "do not print topics with proportions less than this threshold value.", null);

	static CommandOption.Integer docTopicsMax = new CommandOption.Integer
		(Vectors2Topics.class, "doc-topics-max", "INTEGER", true, -1,
		 "When writing topic proportions per document with --output-doc-topics, " +
		 "do not print more than INTEGER number of topics.  "+
		 "A negative value indicates that all topics should be printed.", null);

	static CommandOption.Integer numTopics = new CommandOption.Integer
		(Vectors2Topics.class, "num-topics", "INTEGER", true, 10,
		 "The number of topics to fit.", null);

	static CommandOption.Integer numThreads = new CommandOption.Integer
		(Vectors2Topics.class, "num-threads", "INTEGER", true, 1,
		 "The number of threads for parallel training.", null);

	static CommandOption.Integer numIterations = new CommandOption.Integer
		(Vectors2Topics.class, "num-iterations", "INTEGER", true, 1000,
		 "The number of iterations of Gibbs sampling.", null);

	static CommandOption.Integer randomSeed = new CommandOption.Integer
		(Vectors2Topics.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	static CommandOption.Integer topWords = new CommandOption.Integer
		(Vectors2Topics.class, "num-top-words", "INTEGER", true, 20,
		 "The number of most probable words to print for each topic after model estimation.", null);

	static CommandOption.Integer showTopicsInterval = new CommandOption.Integer
		(Vectors2Topics.class, "show-topics-interval", "INTEGER", true, 50,
		 "The number of iterations between printing a brief summary of the topics so far.", null);

	static CommandOption.Integer outputModelInterval = new CommandOption.Integer
		(Vectors2Topics.class, "output-model-interval", "INTEGER", true, 0,
		 "The number of iterations between writing the model (and its Gibbs sampling state) to a binary file.  " +
		 "You must also set the --output-model to use this option, whose argument will be the prefix of the filenames.", null);

	static CommandOption.Integer outputStateInterval = new CommandOption.Integer
		(Vectors2Topics.class, "output-state-interval", "INTEGER", true, 0,
		 "The number of iterations between writing the sampling state to a text file.  " +
		 "You must also set the --output-state to use this option, whose argument will be the prefix of the filenames.", null);

	static CommandOption.Integer optimizeInterval = new CommandOption.Integer
		(Vectors2Topics.class, "optimize-interval", "INTEGER", true, 0,
		 "The number of iterations between reestimating dirichlet hyperparameters.", null);

	static CommandOption.Integer optimizeBurnIn = new CommandOption.Integer
		(Vectors2Topics.class, "optimize-burn-in", "INTEGER", true, 200,
		 "The number of iterations to run before first estimating dirichlet hyperparameters.", null);

	static CommandOption.Boolean useSymmetricAlpha = new CommandOption.Boolean
		(Vectors2Topics.class, "use-symmetric-alpha", "true|false", false, false,
		 "Only optimize the concentration parameter of the prior over document-topic distributions. This may reduce the number of very small, poorly estimated topics, but may disperse common words over several topics.", null);

	static CommandOption.Boolean useNgrams = new CommandOption.Boolean
		(Vectors2Topics.class, "use-ngrams", "true|false", false, false,
		 "Rather than using LDA, use Topical-N-Grams, which models phrases.", null);

	static CommandOption.Boolean usePAM = new CommandOption.Boolean
		(Vectors2Topics.class, "use-pam", "true|false", false, false,
		 "Rather than using LDA, use Pachinko Allocation Model, which models topical correlations." +
		 "You cannot do this and also --use-ngrams.", null);

	static CommandOption.Double alpha = new CommandOption.Double
		(Vectors2Topics.class, "alpha", "DECIMAL", true, 50.0,
		 "Alpha parameter: smoothing over topic distribution.",null);

	static CommandOption.Double beta = new CommandOption.Double
		(Vectors2Topics.class, "beta", "DECIMAL", true, 0.01,
		 "Beta parameter: smoothing over unigram distribution.",null);

	static CommandOption.Double gamma = new CommandOption.Double
		(Vectors2Topics.class, "gamma", "DECIMAL", true, 0.01,
		 "Gamma parameter: smoothing over bigram distribution",null);

	static CommandOption.Double delta = new CommandOption.Double
		(Vectors2Topics.class, "delta", "DECIMAL", true, 0.03,
		 "Delta parameter: smoothing over choice of unigram/bigram",null);

	static CommandOption.Double delta1 = new CommandOption.Double
		(Vectors2Topics.class, "delta1", "DECIMAL", true, 0.2,
		 "Topic N-gram smoothing parameter",null);

	static CommandOption.Double delta2 = new CommandOption.Double
		(Vectors2Topics.class, "delta2", "DECIMAL", true, 1000.0,
		 "Topic N-gram smoothing parameter",null);
	
	static CommandOption.Integer pamNumSupertopics = new CommandOption.Integer
		(Vectors2Topics.class, "pam-num-supertopics", "INTEGER", true, 10,
		 "When using the Pachinko Allocation Model (PAM) set the number of supertopics.  " +
		 "Typically this is about half the number of subtopics, although more may help.", null);

	static CommandOption.Integer pamNumSubtopics = new CommandOption.Integer
		(Vectors2Topics.class, "pam-num-subtopics", "INTEGER", true, 20,
		 "When using the Pachinko Allocation Model (PAM) set the number of subtopics.", null);

	public static void main (String[] args) throws java.io.IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Vectors2Topics.class,
								  "A tool for estimating, saving and printing diagnostics for topic models, such as LDA.");
		CommandOption.process (Vectors2Topics.class, args);

		if (usePAM.value) {
			InstanceList ilist = InstanceList.load (new File(inputFile.value));
			System.out.println ("Data loaded.");
			if (inputModelFilename.value != null)
				throw new IllegalArgumentException ("--input-model not supported with --use-pam.");
			PAM4L pam = new PAM4L(pamNumSupertopics.value, pamNumSubtopics.value);
			pam.estimate (ilist, numIterations.value, /*optimizeModelInterval*/50,
						  showTopicsInterval.value,
						  outputModelInterval.value, outputModelFilename.value, 
						  randomSeed.value == 0 ? new Randoms() : new Randoms(randomSeed.value));
			pam.printTopWords(topWords.value, true);
			if (stateFile.value != null)
				pam.printState (new File(stateFile.value));
			if (docTopicsFile.value != null) {
				PrintWriter out = new PrintWriter (new FileWriter ((new File(docTopicsFile.value))));
				pam.printDocumentTopics (out, docTopicsThreshold.value, docTopicsMax.value);
				out.close();
			}

			
			if (outputModelFilename.value != null) {
				assert (pam != null);
				try {
					ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream (outputModelFilename.value));
					oos.writeObject (pam);
					oos.close();
				} catch (Exception e) {
					e.printStackTrace();
					throw new IllegalArgumentException ("Couldn't write topic model to filename "+outputModelFilename.value);
				}
			}
			

		}
		
		else if (useNgrams.value) {
			InstanceList ilist = InstanceList.load (new File(inputFile.value));
			System.out.println ("Data loaded.");
			if (inputModelFilename.value != null)
				throw new IllegalArgumentException ("--input-model not supported with --use-ngrams.");
			TopicalNGrams tng = new TopicalNGrams(numTopics.value,
												  alpha.value,
												  beta.value,
												  gamma.value,
												  delta.value,
												  delta1.value,
												  delta2.value);
			tng.estimate (ilist, numIterations.value, showTopicsInterval.value,
						  outputModelInterval.value, outputModelFilename.value, 
						  randomSeed.value == 0 ? new Randoms() : new Randoms(randomSeed.value));
			tng.printTopWords(topWords.value, true);
			if (stateFile.value != null)
				tng.printState (new File(stateFile.value));
			if (docTopicsFile.value != null) {
				PrintWriter out = new PrintWriter (new FileWriter ((new File(docTopicsFile.value))));
				tng.printDocumentTopics (out, docTopicsThreshold.value, docTopicsMax.value);
				out.close();
			}

			if (outputModelFilename.value != null) {
				assert (tng != null);
				try {
					ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream (outputModelFilename.value));
					oos.writeObject (tng);
					oos.close();
				} catch (Exception e) {
					e.printStackTrace();
					throw new IllegalArgumentException ("Couldn't write topic model to filename "+outputModelFilename.value);
				}
			}
			
		}
		else if (languageInputFiles.value != null) {
			// Start a new polylingual topic model
			
			PolylingualTopicModel topicModel = null;

			int numLanguages = languageInputFiles.value.length;

			InstanceList[] training = new InstanceList[ languageInputFiles.value.length ];
			for (int i=0; i < training.length; i++) {
				training[i] = InstanceList.load(new File(languageInputFiles.value[i]));
				if (training[i] != null) { System.out.println(i + " is not null"); }
				else { System.out.println(i + " is null"); }
			}

			System.out.println ("Data loaded.");
			
			// For historical reasons we currently only support FeatureSequence data,
			//  not the FeatureVector, which is the default for the input functions.
			//  Provide a warning to avoid ClassCastExceptions.
			if (training[0].size() > 0 &&
				training[0].get(0) != null) {
				Object data = training[0].get(0).getData();
				if (! (data instanceof FeatureSequence)) {
					System.err.println("Topic modeling currently only supports feature sequences: use --keep-sequence option when importing data.");
					System.exit(1);
				}
			}
			
			topicModel = new PolylingualTopicModel (numTopics.value, alpha.value);
			if (randomSeed.value != 0) {
				topicModel.setRandomSeed(randomSeed.value);
			}
			
			topicModel.addInstances(training);

			topicModel.setTopicDisplay(showTopicsInterval.value, topWords.value);

			topicModel.setNumIterations(numIterations.value);
			topicModel.setOptimizeInterval(optimizeInterval.value);
			topicModel.setBurninPeriod(optimizeBurnIn.value);

			if (outputStateInterval.value != 0) {
				topicModel.setSaveState(outputStateInterval.value, stateFile.value);
			}

			if (outputModelInterval.value != 0) {
				topicModel.setModelOutput(outputModelInterval.value, outputModelFilename.value);
			}

			topicModel.estimate();

			if (topicKeysFile.value != null) {
				topicModel.printTopWords(new File(topicKeysFile.value), topWords.value, false);
			}

			if (stateFile.value != null) {
				topicModel.printState (new File(stateFile.value));
			}

			if (docTopicsFile.value != null) {
				PrintWriter out = new PrintWriter (new FileWriter ((new File(docTopicsFile.value))));
				topicModel.printDocumentTopics(out, docTopicsThreshold.value, docTopicsMax.value);
				out.close();
			}

            if (inferencerFilename.value != null) {
                try {
					for (int language = 0; language < numLanguages; language++) {

						ObjectOutputStream oos =
							new ObjectOutputStream(new FileOutputStream(inferencerFilename.value + "." + language));
						oos.writeObject(topicModel.getInferencer(language));
						oos.close();
					}

                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }

            }

			if (outputModelFilename.value != null) {
				assert (topicModel != null);
				try {

					ObjectOutputStream oos =
						new ObjectOutputStream (new FileOutputStream (outputModelFilename.value));
					oos.writeObject (topicModel);
					oos.close();

				} catch (Exception e) {
					e.printStackTrace();
					throw new IllegalArgumentException ("Couldn't write topic model to filename "+outputModelFilename.value);
				}
			}

		}
		else {

			// Start a new LDA topic model
			
			ParallelTopicModel topicModel = null;

			if (inputModelFilename.value != null) {
				
				try {
					topicModel = ParallelTopicModel.read(new File(inputModelFilename.value));
				} catch (Exception e) {
					System.err.println("Unable to restore saved topic model " + 
									   inputModelFilename.value + ": " + e);
					System.exit(1);
				}
				/*
				// Loading new data is optional if we are restoring a saved state.
				if (inputFile.value != null) {
					InstanceList instances = InstanceList.load (new File(inputFile.value));
					System.out.println ("Data loaded.");
					lda.addInstances(instances);
				}
				*/
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

				System.out.println ("Data loaded.");

				if (training.size() > 0 &&
					training.get(0) != null) {
					Object data = training.get(0).getData();
					if (! (data instanceof FeatureSequence)) {
						System.err.println("Topic modeling currently only supports feature sequences: use --keep-sequence option when importing data.");
						System.exit(1);
					}
				}

				topicModel = new ParallelTopicModel (numTopics.value, alpha.value, beta.value);
				if (randomSeed.value != 0) {
					topicModel.setRandomSeed(randomSeed.value);
				}

				topicModel.addInstances(training);
			}

			topicModel.setTopicDisplay(showTopicsInterval.value, topWords.value);

			/*
			if (testingFile.value != null) {
				topicModel.setTestingInstances( InstanceList.load(new File(testingFile.value)) );
			}
			*/

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

			topicModel.estimate();

			if (topicKeysFile.value != null) {
				topicModel.printTopWords(new File(topicKeysFile.value), topWords.value, false);
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

			if (stateFile.value != null) {
				topicModel.printState (new File(stateFile.value));
			}

			if (docTopicsFile.value != null) {
				PrintWriter out = new PrintWriter (new FileWriter ((new File(docTopicsFile.value))));
				topicModel.printDocumentTopics(out, docTopicsThreshold.value, docTopicsMax.value);
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
					e.printStackTrace();
					throw new IllegalArgumentException ("Couldn't write topic model to filename "+outputModelFilename.value);
				}
			}

			if (inferencerFilename.value != null) {
				try {

					ObjectOutputStream oos = 
						new ObjectOutputStream(new FileOutputStream(inferencerFilename.value));
					oos.writeObject(topicModel.getInferencer());
					oos.close();

				} catch (Exception e) {
					System.err.println(e.getMessage());
				}
					
			}

			if (evaluatorFilename.value != null) {
				try {

					ObjectOutputStream oos = 
						new ObjectOutputStream(new FileOutputStream(evaluatorFilename.value));
					oos.writeObject(topicModel.getProbEstimator());
					oos.close();

				} catch (Exception e) {
					System.err.println(e.getMessage());
				}
					
			}

		}

	}

}
