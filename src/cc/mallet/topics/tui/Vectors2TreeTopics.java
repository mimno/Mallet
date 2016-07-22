/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics.tui;

import cc.mallet.util.CommandOption;
import cc.mallet.types.InstanceList;
import cc.mallet.topics.tree.TreeMarginalProbEstimator;
import cc.mallet.topics.tree.TreeTopicInferencer;
import cc.mallet.topics.tree.TreeTopicSamplerInterface;
import cc.mallet.topics.tree.TreeTopicSamplerFast;
import cc.mallet.topics.tree.TreeTopicSamplerFastEst;
import cc.mallet.topics.tree.TreeTopicSamplerFastEstSortD;
import cc.mallet.topics.tree.TreeTopicSamplerFastSortD;
import cc.mallet.topics.tree.TreeTopicSamplerNaive;

import java.io.*;

/** Perform topic analysis in the style of LDA and its variants.
 *  @author <a href="mailto:mccallum@cs.umass.edu">Andrew McCallum</a>
 */

public class Vectors2TreeTopics {

	// common options in mallet
	static CommandOption.SpacedStrings inputFile = new CommandOption.SpacedStrings
		(Vectors2TreeTopics.class, "input", "FILENAME [FILENAME ...]", true, null,
		 "The filename from which to read the list of training instances. " +
		 "Support multiple languages, each language should have its own file. " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

	static CommandOption.Integer numTopics = new CommandOption.Integer
		(Vectors2TreeTopics.class, "num-topics", "INTEGER", true, 10,
		 "The number of topics to fit.", null);

	static CommandOption.Integer numIterations = new CommandOption.Integer
		(Vectors2TreeTopics.class, "num-iterations", "INTEGER", true, 1000,
		 "The number of iterations of Gibbs sampling.", null);

	static CommandOption.Integer randomSeed = new CommandOption.Integer
		(Vectors2TreeTopics.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	static CommandOption.Integer topWords = new CommandOption.Integer
		(Vectors2TreeTopics.class, "num-top-words", "INTEGER", true, 20,
		 "The number of most probable words to print for each topic after model estimation.", null);

	static CommandOption.Double alpha = new CommandOption.Double
		(Vectors2TreeTopics.class, "alpha", "DECIMAL", true, 50.0,
		 "Alpha parameter: smoothing over topic distribution.",null);
	
    static CommandOption.String inferencerFilename = new CommandOption.String
            (Vectors2TreeTopics.class, "inferencer-filename", "FILENAME", true, null,
             "A topic inferencer applies a previously trained topic model to new documents." +
             "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String evaluatorFilename = new CommandOption.String
        (Vectors2TreeTopics.class, "evaluator-filename", "FILENAME", true, null,
         "A held-out likelihood evaluator for new documents.  " +
         "By default this is null, indicating that no file will be written.", null);	

	////////////////////////////////////
	// new options
    
	static CommandOption.Integer outputInteval = new CommandOption.Integer
	(Vectors2TreeTopics.class, "output-interval", "INTEGER", true, 20,
	 "For each interval, the result files are output to the outputFolder.", null);
	
	static CommandOption.String outputDir= new CommandOption.String
	(Vectors2TreeTopics.class, "output-dir", "FOLDERNAME", true, null,
	 "The output folder.", null);
	
	static CommandOption.String vocabFile = new CommandOption.String
	(Vectors2TreeTopics.class, "vocab", "FILENAME", true, null,
	 "The vocabulary file.", null);
	
	static CommandOption.String treeFiles = new CommandOption.String
	(Vectors2TreeTopics.class, "tree", "FILENAME", true, null,
	 "The files for tree structure.", null);
	
	static CommandOption.String hyperFile = new CommandOption.String
	(Vectors2TreeTopics.class, "tree-hyperparameters", "FILENAME", true, null,
	 "The hyperparameters for tree structure.", null);
	
	static CommandOption.Boolean resume = new CommandOption.Boolean
	(Vectors2TreeTopics.class, "resume", "true|false", false, false,
	 "Resume from the previous output states.", null);
	
	static CommandOption.String resumeDir = new CommandOption.String
	(Vectors2TreeTopics.class, "resume-dir", "FOLDERNAME", true, null,
	 "The resume folder.", null);
	
	static CommandOption.String consFile = new CommandOption.String
	(Vectors2TreeTopics.class, "constraint", "FILENAME", true, null,
	"The file constains the constrained words", null);
	
	static CommandOption.String forgetTopics = new CommandOption.String
	(Vectors2TreeTopics.class, "forget-topics", "TYPENAME", true, null,
	 "Three options: term, doc, null." +
	 "Forget the previous sampled topic assignments of constrained words only (term), " +
	 "or the documents containing constrained words (doc)," +
	 "or not forget at all (keep everything)." +
	 "This option is for adding interaction.", null);
	
	static CommandOption.String removedFile = new CommandOption.String
	(Vectors2TreeTopics.class, "remove-words", "FILENAME", true, null,
	 "The file contains the words that you want to be ignored in topic modeling. " +
	 "You need to have removed.all file, which is the removed words before this round of interaction," +
	 "and a removed.new file, which is the removed words that users just defined in this round of interaction" +
	 "This option is for adding interaction.", null);
	
	static CommandOption.String keepFile = new CommandOption.String
	(Vectors2TreeTopics.class, "keep", "FILENAME", true, null,
	"The topic assignments of words on this list will be kept instead of cleared," +
	"even though it is on the list of constrained words." +
	"This option is for adding interaction.", null);
	
	static CommandOption.String modelType = new CommandOption.String
	(Vectors2TreeTopics.class, "tree-model-type", "TYPENAME", true, "fast-est",
	 "Possible types: naive, fast, fast-est, fast-sortD, fast-sortW, fast-sortD-sortW, " +
	 "fast-est-sortD, fast-est-sortW, fast-est-sortD-sortW.", null);
	
	public static void main (String[] args) throws java.io.IOException {
		// Process the command-line options
		CommandOption.setSummary (Vectors2TreeTopics.class,
								  "A tool for estimating, saving and printing diagnostics for topic models, such as LDA.");
		CommandOption.process (Vectors2TreeTopics.class, args);
		
		int numLanguages = inputFile.value.length;
		InstanceList[] instances = new InstanceList[ numLanguages ];
		for (int i=0; i < instances.length; i++) {
			instances[i] = InstanceList.load(new File(inputFile.value[i]));
			System.out.println ("Data " + i + " loaded. Total number of documents: " + instances[i].size());
		}
		
		TreeTopicSamplerInterface topicModel = null;
		
		// notice there are more inference methods available in this pacakge: 
		// naive, fast, fast-est, fast-sortD, fast-sortW, 
		// fast-sortD-sortW, fast-est-sortD, fast-est-sortW, fast-est-sortD-sortW
		// by default, we set it as fast-est-sortD-sortW
		// but you can change the modelType to any of them by exploring the source code
		// also notice the inferencer and evaluator only support fast-est, fast-sortD-sortW, 
		// fast-est-sortD, fast-est-sortW, fast-est-sortD-sortW
		boolean sortW = false;
		String modeltype = "fast-est";
		//System.out.println("model type:" + modeltype);
		modeltype = modelType.value;
		
        if (modeltype.equals("naive")) {
        	topicModel = new TreeTopicSamplerNaive( 
				numTopics.value, alpha.value, randomSeed.value);
		} else if (modeltype.equals("fast")){
			topicModel = new TreeTopicSamplerFast(
					numTopics.value, alpha.value, randomSeed.value, sortW);
		} else if (modeltype.equals("fast-sortD")){
			topicModel = new TreeTopicSamplerFastSortD(
					numTopics.value, alpha.value, randomSeed.value, sortW);
		} else if (modeltype.equals("fast-sortW")){
			sortW = true;
			topicModel = new TreeTopicSamplerFast(
					numTopics.value, alpha.value, randomSeed.value, sortW);
		} else if (modeltype.equals("fast-sortD-sortW")){
			sortW = true;
			topicModel = new TreeTopicSamplerFastSortD(
					numTopics.value, alpha.value, randomSeed.value, sortW);
			
		} else if (modeltype.equals("fast-est")) {
			topicModel = new TreeTopicSamplerFastEst(
					numTopics.value, alpha.value, randomSeed.value, sortW);
		} else if (modeltype.equals("fast-est-sortD")) {
			topicModel = new TreeTopicSamplerFastEstSortD(
					numTopics.value, alpha.value, randomSeed.value, sortW);
		} else if (modeltype.equals("fast-est-sortW")) {
			sortW = true;
			topicModel = new TreeTopicSamplerFastEst(
					numTopics.value, alpha.value, randomSeed.value, sortW);						
		} else if (modeltype.equals("fast-est-sortD-sortW")) {
			sortW = true;
			topicModel = new TreeTopicSamplerFastEstSortD(
					numTopics.value, alpha.value, randomSeed.value, sortW);
		//} else if (modeltype.equals("fast-est-try")) {
		//	topicModel = new TreeTopicSamplerFastEstTry(
		//			numTopics.value, alpha.value, randomSeed.value, sortW);
		} else {
			System.out.println("model type wrong! please use " +
					"'naive', 'fast', 'fast-est', " +
					"'fast-sortD', 'fast-sortW', 'fast-sortD-sortW', " +
					"'fast-est-sortD', 'fast-est-sortW', 'fast-est-sortD-sortW'!");
			System.exit(0);
		}
		
		// load tree and vocab
		topicModel.initialize(treeFiles.value, hyperFile.value, vocabFile.value, removedFile.value);
        topicModel.setNumIterations(numIterations.value);
        System.out.println("Prior tree loaded!");
        
		if (resume.value == true) {
			// resume instances from the saved states
			topicModel.resume(instances, resumeDir.value);
		} else {
			// add instances
			topicModel.addInstances(instances);
		}
		System.out.println("Model initialized!");
		
		// if clearType is not null, clear the topic assignments of the 
		// constraint words
		if (forgetTopics.value != null) {
			if (forgetTopics.value.equals("term") || forgetTopics.value.equals("doc")) {
				topicModel.clearTopicAssignments(forgetTopics.value, consFile.value, keepFile.value);
			} else {
				System.out.println("clear type wrong! please use either 'doc' or 'term'!");
				System.exit(0);
			}
		}
		
		// sampling and save states
		topicModel.estimate(numIterations.value, outputDir.value,
							outputInteval.value, topWords.value);
		
		// topic report
		//System.out.println(topicModel.displayTopWords(topWords.value));
		
		if (inferencerFilename.value != null) {
			try {
				ObjectOutputStream oos = 
					new ObjectOutputStream(new FileOutputStream(inferencerFilename.value));
				TreeTopicInferencer infer = topicModel.getInferencer();
				infer.setModelType(modeltype);
				oos.writeObject(infer);
				oos.close();
			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
				
		}
		
		if (evaluatorFilename.value != null) {
			try {
				ObjectOutputStream oos = 
					new ObjectOutputStream(new FileOutputStream(evaluatorFilename.value));
				TreeMarginalProbEstimator estimator = topicModel.getProbEstimator();
				estimator.setModelType(modeltype);
				oos.writeObject(estimator);
				oos.close();

			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
				
		}
				
	}

}
