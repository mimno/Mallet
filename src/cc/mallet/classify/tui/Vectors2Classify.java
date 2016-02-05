/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify.tui;



import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.*;

import cc.mallet.classify.*;
import cc.mallet.classify.evaluate.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
/**
 * Classify documents, run trials, print statistics from a vector file.
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public abstract class Vectors2Classify
{
	static BshInterpreter interpreter = new BshInterpreter();

	private static Logger logger = MalletLogger.getLogger(Vectors2Classify.class.getName());
	private static Logger progressLogger = MalletProgressMessageLogger.getLogger(Vectors2Classify.class.getName() + "-pl");
	private static ArrayList<String> classifierTrainerStrings = new ArrayList<String>();
	private static int dataOptionsSize = 3;
	private static int reportOptionsSize = 6;

	private static boolean[][] ReportOptions = new boolean[dataOptionsSize][reportOptionsSize];

	// Essentially an enum mapping string names to enums to ints.
	private static class ReportOption
	{
		static final String[] dataOptions = {"train", "test", "validation"};
		static final String[] reportOptions = {"accuracy", "f1", "confusion", "raw", "precision", "recall"};
		static final int train=0;
		static final int test =1;
		static final int validation=2;
		static final int accuracy=0;
		static final int f1=1;
		static final int confusion=2;
		static final int raw=3;
		static final int precision=4;
		static final int recall=5;
	}

	static CommandOption.SpacedStrings report = new CommandOption.SpacedStrings
			(Vectors2Classify.class, "report", "[train|test|validation]:[accuracy|f1:label|precision:label|recall:label|confusion|raw]",
					true, new String[] {"train:accuracy",  "test:accuracy", "test:confusion",  "test:precision",  "test:recall", "test:f1"},
					"", null) 
	{
		public void postParsing (CommandOption.List list)
		{
			java.lang.String defaultRawFormatting = "siw";

			for (int argi=0; argi<this.value.length; argi++){
				// convert options like --report train:accuracy --report test:f1=labelA to
				// boolean array of options.

				// first, split the argument at semicolon.
				//System.out.println(argi + " " + this.value[argi]);
				java.lang.String arg = this.value[argi];
				java.lang.String fields[] = arg.split("[:=]");
				java.lang.String dataSet = fields[0];
				java.lang.String reportOption = fields[1];
				java.lang.String reportOptionArg = null;

				if (fields.length >=3){
					reportOptionArg = fields[2];
				}
				//System.out.println("Report option arg " + reportOptionArg);

				//find the datasource (test,train,validation)
				boolean foundDataSource = false;
				int i=0;
				for (; i<ReportOption.dataOptions.length; i++){
					if (dataSet.equals(ReportOption.dataOptions[i])){
						foundDataSource = true;
						break;
					}
				}
				if (!foundDataSource){
					throw new IllegalArgumentException("Unknown argument = " + dataSet + " in --report " + this.value[argi]);
				}

				//find the report option (accuracy, f1, confusion, raw, precision, recall)
				boolean foundReportOption = false;
				int j=0;
				for (; j<ReportOption.reportOptions.length; j++){
					if (reportOption.equals(ReportOption.reportOptions[j])){
						foundReportOption = true;
						break;
					}
				}
				if (!foundReportOption){
					throw new IllegalArgumentException("Unknown argument = " + reportOption + " in --report " + this.value[argi]);
				}

				//Mark the (dataSet,reportionOption) pair as selected
				ReportOptions[i][j] = true;

			}
		}
	};


	static CommandOption.String trainerConstructor = new CommandOption.String
			(Vectors2Classify.class, "trainer", "ClassifierTrainer constructor",  true, "new NaiveBayesTrainer()",
					"Java code for the constructor used to create a ClassifierTrainer.  "+
							"If no '(' appears, then \"new \" will be prepended and \"Trainer()\" will be appended."+
							"You may use this option mutiple times to compare multiple classifiers.", null) {
		public void postParsing (CommandOption.List list) {
			classifierTrainerStrings.add (this.value);
		}};

		static CommandOption.String outputFile = new CommandOption.String
				(Vectors2Classify.class, "output-classifier", "FILENAME", true, "classifier.mallet",
						"The filename in which to write the classifier after it has been trained.", null);

		/*	static CommandOption.String pipeFile = new CommandOption.String
		(Vectors2Classify.class, "output-pipe", "FILENAME", true, "classifier_pipe.mallet",
		"The filename in which to write the classifier's instancePipe after it has been trained.", null);*/

		static CommandOption.String inputFile = new CommandOption.String
				(Vectors2Classify.class, "input", "FILENAME", true, "text.vectors",
						"The filename from which to read the list of training instances.  Use - for stdin.", null);

		static CommandOption.String trainingFile = new CommandOption.String
				(Vectors2Classify.class, "training-file", "FILENAME", true, "text.vectors",
						"Read the training set instance list from this file. " +
								"If this is specified, the input file parameter is ignored", null);

		static CommandOption.String testFile = new CommandOption.String
				(Vectors2Classify.class, "testing-file", "FILENAME", true, "text.vectors",
						"Read the test set instance list to this file. " +
								"If this option is specified, the training-file parameter must be specified and " +
								" the input-file parameter is ignored", null);

		static CommandOption.String validationFile = new CommandOption.String
				(Vectors2Classify.class, "validation-file", "FILENAME", true, "text.vectors",
						"Read the validation set instance list to this file." +
								"If this option is specified, the training-file parameter must be specified and " +
								"the input-file parameter is ignored", null);

		static CommandOption.Double trainingProportionOption = new CommandOption.Double
				(Vectors2Classify.class, "training-portion", "DECIMAL", true, 1.0,
						"The fraction of the instances that should be used for training.", null);

		static CommandOption.Double validationProportionOption = new CommandOption.Double
				(Vectors2Classify.class, "validation-portion", "DECIMAL", true, 0.0,
						"The fraction of the instances that should be used for validation.", null);

		static CommandOption.Double unlabeledProportionOption = new CommandOption.Double
				(Vectors2Classify.class, "unlabeled-portion", "DECIMAL", true, 0.0,
						"The fraction of the training instances that should have their labels hidden.  "
								+"Note that these are taken out of the training-portion, not allocated separately.", null);

		static CommandOption.Integer randomSeedOption = new CommandOption.Integer
				(Vectors2Classify.class, "random-seed", "INTEGER", true, 0,
						"The random seed for randomly selecting a proportion of the instance list for training", null);

		static CommandOption.Integer numTrialsOption = new CommandOption.Integer
				(Vectors2Classify.class, "num-trials", "INTEGER", true, 1,
						"The number of random train/test splits to perform", null);

		static CommandOption.Object classifierEvaluatorOption = new CommandOption.Object
				(Vectors2Classify.class, "classifier-evaluator", "CONSTRUCTOR", true, null,
						"Java code for constructing a ClassifierEvaluating object", null);

		//	static CommandOption.Boolean printTrainAccuracyOption = new CommandOption.Boolean
		//	(Vectors2Classify.class, "print-train-accuracy", "true|false", true, true,
		//	 "After training, run the resulting classifier on the instances included in training, "
		//	 +"and print the accuracy", null);
		//
		//	static CommandOption.Boolean printTestAccuracyOption = new CommandOption.Boolean
		//	(Vectors2Classify.class, "print-test-accuracy", "true|false", true, true,
		//	 "After training, run the resulting classifier on the instances not included in training, "
		//	 +"and print the accuracy", null);

		static CommandOption.Integer verbosityOption = new CommandOption.Integer
				(Vectors2Classify.class, "verbosity", "INTEGER", true, -1,
						"The level of messages to print: 0 is silent, 8 is most verbose. " +
								"Levels 0-8 correspond to the java.logger predefined levels "+
								"off, severe, warning, info, config, fine, finer, finest, all. " +
								"The default value is taken from the mallet logging.properties file," +
								" which currently defaults to INFO level (3)", null);

		static CommandOption.Boolean noOverwriteProgressMessagesOption = new CommandOption.Boolean
				(Vectors2Classify.class, "noOverwriteProgressMessages", "true|false", false, false,
						"Suppress writing-in-place on terminal for progess messages - repetitive messages "
								+"of which only the latest is generally of interest", null);

		static CommandOption.Integer crossValidation = new CommandOption.Integer
				(Vectors2Classify.class, "cross-validation", "INT", true, 0,
						"The number of folds for cross-validation (DEFAULT=0).", null);

		public static void main (String[] args) throws bsh.EvalError, java.io.IOException
		{
			// Process the command-line options
			CommandOption.setSummary (Vectors2Classify.class,
					"A tool for training, saving and printing diagnostics from a classifier on vectors.");
			CommandOption.process (Vectors2Classify.class, args);

			// handle default trainer here for now; default argument processing doesn't  work
			if (!trainerConstructor.wasInvoked()){
				classifierTrainerStrings.add ("new NaiveBayesTrainer()");
			}

			if (!report.wasInvoked()){
				report.postParsing(null);  // force postprocessing of default value
			}

			int verbosity = verbosityOption.value;

			Logger rootLogger = ((MalletLogger)progressLogger).getRootLogger();

			if (verbosityOption.wasInvoked()){
				rootLogger.setLevel( MalletLogger.LoggingLevels[verbosity]);
			}

			if (noOverwriteProgressMessagesOption.value == false){
				// install special formatting for progress messages
				// find console handler on root logger; change formatter to one
				// that knows about progress messages
				Handler[] handlers = rootLogger.getHandlers();
				for (int i = 0; i < handlers.length; i++) {
					if (handlers[i] instanceof ConsoleHandler) {
						handlers[i].setFormatter(new ProgressMessageLogFormatter());
					}
				}
			}

			boolean separateIlists = testFile.wasInvoked() || trainingFile.wasInvoked() ||
					validationFile.wasInvoked();
			InstanceList ilist=null;
			InstanceList testFileIlist=null;
			InstanceList trainingFileIlist=null;
			InstanceList validationFileIlist=null;

			String labels[] ; 

			if (!separateIlists) { // normal case, --input-file specified
				// Read in the InstanceList, from stdin if the input filename is "-".
				ilist = InstanceList.load (new File(inputFile.value));

				labels = new String [ilist.getAlphabets()[1].size()] ;
				for (int k =0 ; k < ilist.getAlphabets()[1].size(); k++) {
					labels[k] = (String) (ilist.getAlphabets()[1].toArray())[k];
					//System.out.println("Debug: labels.length "+ labels.length + " labels["+k+"] "+labels[k] );
				}
			}
			else{  // user specified separate files for testing and training sets.
				trainingFileIlist = InstanceList.load (new File(trainingFile.value));
				logger.info("Training vectors loaded from " + trainingFile.value);
				if (testFile.wasInvoked()){
					testFileIlist = InstanceList.load (new File(testFile.value));
					logger.info("Testing vectors loaded from " + testFile.value);

					if (!testFileIlist.getPipe().alphabetsMatch(trainingFileIlist.getPipe())) {
						throw new RuntimeException( 
								trainingFileIlist.getPipe().getDataAlphabet() + "\n" 
										+ testFileIlist.getPipe().getDataAlphabet() + "\n" 
										+ trainingFileIlist.getPipe().getTargetAlphabet() + "\n" 
										+ testFileIlist.getPipe().getTargetAlphabet() + "\n"
										+ "Training and testing alphabets don't match!\n");
					}
				}

				if (validationFile.wasInvoked()){
					validationFileIlist = InstanceList.load (new File(validationFile.value));
					logger.info("validation vectors loaded from " + validationFile.value);
					if (!validationFileIlist.getPipe().alphabetsMatch(trainingFileIlist.getPipe())) {
						throw new RuntimeException( 
								trainingFileIlist.getPipe().getDataAlphabet() + "\n" 
										+ validationFileIlist.getPipe().getDataAlphabet() + "\n" 
										+ trainingFileIlist.getPipe().getTargetAlphabet() + "\n" 
										+ validationFileIlist.getPipe().getTargetAlphabet() + "\n"
										+ "Training and validation alphabets don't match!\n");
					}
				}
				else {
					validationFileIlist = new InstanceList(new cc.mallet.pipe.Noop());
				}

				labels = new String [trainingFileIlist.getAlphabets()[1].size()] ;
				for (int k =0 ; k < trainingFileIlist.getAlphabets()[1].size(); k++) {
					labels[k] = (String) (trainingFileIlist.getAlphabets()[1].toArray())[k];
					//System.out.println("Debug: labels.length "+ labels.length + " labels["+k+"] "+labels[k] );
				}

			}

			if (crossValidation.wasInvoked() && trainingProportionOption.wasInvoked()) {
				logger.warning("Both --cross-validation and --training-portion were invoked.  Using cross validation with " + 
						crossValidation.value + " folds.");  
			}
			if (crossValidation.wasInvoked() && validationProportionOption.wasInvoked()) {
				logger.warning("Both --cross-validation and --validation-portion were invoked.  Using cross validation with " + 
						crossValidation.value + " folds.");  
			}
			if (crossValidation.wasInvoked() &&  numTrialsOption.wasInvoked()) {
				logger.warning("Both --cross-validation and --num-trials were invoked.  Using cross validation with " + 
						crossValidation.value + " folds.");  
			}

			int numTrials;
			if (crossValidation.wasInvoked()) {
				numTrials = crossValidation.value;
			}
			else {
				numTrials = numTrialsOption.value;
			}

			Random r = randomSeedOption.wasInvoked() ? new Random (randomSeedOption.value) : new Random ();

			int numTrainers = classifierTrainerStrings.size();
			int numLabels = labels.length;

			double trainAccuracy[][] = new double[numTrainers][numTrials];
			double testAccuracy[][] = new double[numTrainers][numTrials];
			double validationAccuracy[][] = new double[numTrainers][numTrials];

			String trainConfusionMatrix[][] = new String[numTrainers][numTrials];
			String testConfusionMatrix[][] = new String[numTrainers][numTrials];
			String validationConfusionMatrix[][] = new String[numTrainers][numTrials];

			double trainPrecision[][][] = new double[numTrainers][numLabels][numTrials];
			double testPrecision[][][] = new double[numTrainers][numLabels][numTrials];
			double validationPrecision[][][] = new double[numTrainers][numLabels][numTrials];

			double trainRecall[][][] = new double[numTrainers][numLabels][numTrials];
			double testRecall[][][] = new double[numTrainers][numLabels][numTrials];
			double validationRecall[][][] = new double[numTrainers][numLabels][numTrials];

			double trainF1[][][] = new double[numTrainers][numLabels][numTrials];
			double testF1[][][] = new double[numTrainers][numLabels][numTrials];
			double validationF1[][][] = new double[numTrainers][numLabels][numTrials];
			
			double t = trainingProportionOption.value;
			double v = validationProportionOption.value;

			if (!separateIlists) {
				if (crossValidation.wasInvoked()) {
					logger.info("Cross-validation folds = " + crossValidation.value);
				}
				else {
					logger.info("Training portion = " + t);
					logger.info("Unlabeled training sub-portion = "+unlabeledProportionOption.value);
					logger.info("Validation portion = " + v);
					logger.info("Testing portion = " + (1 - v - t));
				}
			}

			//		for (int i=0; i<dataOptionsSize; i++){
			//			for (int j=0; j<reportOptionsSize; j++){
			//				System.out.print(" " + ReportOptions[i][j]);
			//			}
			//			System.out.println();
			//		}

			CrossValidationIterator cvIter;
			if (crossValidation.wasInvoked()) {
				if (crossValidation.value < 2) {
					throw new RuntimeException("At least two folds (set with --cross-validation) are required for cross validation");
				}     
				cvIter = new CrossValidationIterator(ilist, crossValidation.value, r);
			}
			else {
				cvIter = null;
			}

			String[] trainerNames = new String[numTrainers];
			for (int trialIndex = 0; trialIndex < numTrials; trialIndex++) {
				System.out.println("\n-------------------- Trial " + trialIndex + "  --------------------\n");
				InstanceList[] ilists;
				BitSet unlabeledIndices = null;
				if (!separateIlists){
					if (crossValidation.wasInvoked()) {
						InstanceList[] cvSplit = cvIter.next();
						ilists = new InstanceList[3];
						ilists[0] = cvSplit[0];
						ilists[1] = cvSplit[1];
						ilists[2] = cvSplit[0].cloneEmpty();
					}
					else {
						ilists = ilist.split (r, new double[] {t, 1-t-v, v});
					}
				} 
				else {
					ilists = new InstanceList[3];
					ilists[0] = trainingFileIlist;
					ilists[1] = testFileIlist;
					ilists[2] = validationFileIlist;
				}

				if (unlabeledProportionOption.value > 0)
					unlabeledIndices = new cc.mallet.util.Randoms(r.nextInt())
				.nextBitSet(ilists[0].size(), unlabeledProportionOption.value);

				//InfoGain ig = new InfoGain (ilists[0]);
				//int igl = Math.min (10, ig.numLocations());
				//for (int i = 0; i < igl; i++)
				//System.out.println ("InfoGain["+ig.getObjectAtRank(i)+"]="+ig.getValueAtRank(i));
				//ig.print();

				//FeatureSelection selectedFeatures = new FeatureSelection (ig, 8000);
				//ilists[0].setFeatureSelection (selectedFeatures);
				//OddsRatioFeatureInducer orfi = new OddsRatioFeatureInducer (ilists[0]);
				//orfi.induceFeatures (ilists[0], false, true);

				//System.out.println ("Training with "+ilists[0].size()+" instances");
				long time[] = new long[numTrainers];
				for (int c = 0; c < numTrainers; c++){
					time[c] = System.currentTimeMillis();
					ClassifierTrainer trainer = getTrainer(classifierTrainerStrings.get(c));
					trainer.setValidationInstances(ilists[2]);
					System.out.println ("Trial " + trialIndex + " Training " + trainer + " with "+ilists[0].size()+" instances");
					if (unlabeledProportionOption.value > 0)
						ilists[0].hideSomeLabels(unlabeledIndices);
					Classifier classifier = trainer.train (ilists[0]);
					if (unlabeledProportionOption.value > 0)
						ilists[0].unhideAllLabels();

					System.out.println ("Trial " + trialIndex + " Training " + trainer.toString() + " finished");
					time[c] = System.currentTimeMillis() - time[c];
					Trial trainTrial = new Trial (classifier, ilists[0]);
					//assert (ilists[1].size() > 0);
					Trial testTrial = new Trial (classifier, ilists[1]);
					Trial validationTrial = new Trial(classifier, ilists[2]);

					// gdruck - only perform evaluation if requested in report options
					if (ReportOptions[ReportOption.train][ReportOption.confusion] && ilists[0].size()>0) 
						trainConfusionMatrix[c][trialIndex] = new ConfusionMatrix (trainTrial).toString();
					if (ReportOptions[ReportOption.test][ReportOption.confusion] && ilists[1].size()>0) 
						testConfusionMatrix[c][trialIndex] = new ConfusionMatrix (testTrial).toString();
					if (ReportOptions[ReportOption.validation][ReportOption.confusion] && ilists[2].size()>0) 
						validationConfusionMatrix[c][trialIndex] = new ConfusionMatrix (validationTrial).toString();

					// gdruck - only perform evaluation if requested in report options
					if (ReportOptions[ReportOption.train][ReportOption.accuracy]) 
						trainAccuracy[c][trialIndex] = trainTrial.getAccuracy();
					if (ReportOptions[ReportOption.test][ReportOption.accuracy]) 
						testAccuracy[c][trialIndex] = testTrial.getAccuracy();
					if (ReportOptions[ReportOption.validation][ReportOption.accuracy]) 
						validationAccuracy[c][trialIndex] = validationTrial.getAccuracy();

					// gdruck - only perform evaluation if requested in report options
					if (ReportOptions[ReportOption.train][ReportOption.precision]) {
						for (int k =0 ; k < labels.length; k++) 
							trainPrecision[c][k][trialIndex] = trainTrial.getPrecision(labels[k]);
					}
					if (ReportOptions[ReportOption.test][ReportOption.precision]) {
						for (int k =0 ; k < labels.length; k++) 
							testPrecision[c][k][trialIndex] = testTrial.getPrecision(labels[k]);
					}
					if (ReportOptions[ReportOption.validation][ReportOption.precision]) {
						for (int k =0 ; k < labels.length; k++) 
							validationPrecision[c][k][trialIndex] = validationTrial.getPrecision(labels[k]);
					}

					// gdruck - only perform evaluation if requested in report options
					if (ReportOptions[ReportOption.train][ReportOption.recall]) {
						for (int k =0 ; k < labels.length; k++) 
							trainRecall[c][k][trialIndex] = trainTrial.getRecall(labels[k]);
					}	
					if (ReportOptions[ReportOption.test][ReportOption.recall]) {
						for (int k =0 ; k < labels.length; k++) 
							testRecall[c][k][trialIndex] = testTrial.getRecall(labels[k]);
					}					
					if (ReportOptions[ReportOption.validation][ReportOption.recall]) {
						for (int k =0 ; k < labels.length; k++) 
							validationRecall[c][k][trialIndex] = validationTrial.getRecall(labels[k]);
					}	

					// gdruck - only perform evaluation if requested in report options
					if (ReportOptions[ReportOption.train][ReportOption.f1]) {
						for (int k =0 ; k < labels.length; k++) 
							trainF1[c][k][trialIndex] = trainTrial.getF1(labels[k]);
					}					
					if (ReportOptions[ReportOption.test][ReportOption.f1]) {
						for (int k =0 ; k < labels.length; k++) 
							testF1[c][k][trialIndex] = testTrial.getF1(labels[k]);
					}						
					if (ReportOptions[ReportOption.validation][ReportOption.f1]) {
						for (int k =0 ; k < labels.length; k++) 
							validationF1[c][k][trialIndex] = validationTrial.getF1(labels[k]);
					}	

					if (outputFile.wasInvoked()) {
						String filename = outputFile.value;
						if (numTrainers > 1) filename = filename+trainer.toString();
						if (numTrials > 1) filename = filename+".trial"+trialIndex;
						try {
							ObjectOutputStream oos = new ObjectOutputStream
									(new FileOutputStream (filename));
							oos.writeObject (classifier);
							oos.close();
						} catch (Exception e) {
							e.printStackTrace();
							throw new IllegalArgumentException ("Couldn't write classifier to filename "+
									filename);
						}
					}

					// New Reporting

					// raw output
					if (ReportOptions[ReportOption.train][ReportOption.raw]){
						System.out.println("Trial " + trialIndex + " Trainer " + trainer.toString());
						System.out.println(" Raw Training Data");
						printTrialClassification(trainTrial);
					}

					if (ReportOptions[ReportOption.test][ReportOption.raw]){
						System.out.println("Trial " + trialIndex + " Trainer " + trainer.toString());
						System.out.println(" Raw Testing Data");
						printTrialClassification(testTrial);
					}

					if (ReportOptions[ReportOption.validation][ReportOption.raw]){
						System.out.println("Trial " + trialIndex + " Trainer " + trainer.toString());
						System.out.println(" Raw Validation Data");
						printTrialClassification(validationTrial);
					}


					//train
					if (ReportOptions[ReportOption.train][ReportOption.confusion]){
						System.out.println("Trial " + trialIndex + " Trainer " + trainer.toString() +  " Training Data Confusion Matrix");
						if (ilists[0].size()>0) System.out.println (trainConfusionMatrix[c][trialIndex]);
					}

					if (ReportOptions[ReportOption.train][ReportOption.precision]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " training data Precision(" + labels[k] + ") = "+ trainTrial.getPrecision(labels[k]));
					}

					if (ReportOptions[ReportOption.train][ReportOption.recall]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " training data Recall(" + labels[k] + ") = "+ trainTrial.getRecall(labels[k]));
					}

					if (ReportOptions[ReportOption.train][ReportOption.f1]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " training data F1(" + labels[k] + ") = "+ trainTrial.getF1(labels[k]));
					}

					if (ReportOptions[ReportOption.train][ReportOption.accuracy]){
						System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " training data accuracy = "+ trainAccuracy[c][trialIndex]);
					}

					//validation
					if (ReportOptions[ReportOption.validation][ReportOption.confusion]){
						System.out.println("Trial " + trialIndex + " Trainer " + trainer.toString() +  " Validation Data Confusion Matrix");
						if (ilists[2].size()>0) System.out.println (validationConfusionMatrix[c][trialIndex]);
					}

					if (ReportOptions[ReportOption.validation][ReportOption.precision]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " validation data precision(" + labels[k] + ") = "+ validationTrial.getPrecision(labels[k]));
					}

					if (ReportOptions[ReportOption.validation][ReportOption.recall]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " validation data recall(" + labels[k] + ") = "+ validationTrial.getRecall(labels[k]));
					}

					if (ReportOptions[ReportOption.validation][ReportOption.f1]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " validation data F1(" + labels[k] + ") = "+ validationTrial.getF1(labels[k]));
					}
					if (ReportOptions[ReportOption.validation][ReportOption.accuracy]){
						System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " validation data accuracy = "+ validationAccuracy[c][trialIndex]);
					}

					//test
					if (ReportOptions[ReportOption.test][ReportOption.confusion]){
						System.out.println("Trial " + trialIndex + " Trainer " + trainer.toString() + " Test Data Confusion Matrix");
						if (ilists[1].size()>0) System.out.println (testConfusionMatrix[c][trialIndex]);
					}

					if (ReportOptions[ReportOption.test][ReportOption.precision]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " test data precision(" + labels[k] + ") = "+ testTrial.getPrecision(labels[k]));
					}

					if (ReportOptions[ReportOption.test][ReportOption.recall]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " test data recall(" + labels[k] + ") = "+ testTrial.getRecall(labels[k]));
					}

					if (ReportOptions[ReportOption.test][ReportOption.f1]){
						for (int k =0 ; k < labels.length; k++) 
							System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " test data F1(" + labels[k] + ") = "+ testTrial.getF1(labels[k]));
					}

					if (ReportOptions[ReportOption.test][ReportOption.accuracy]){
						System.out.println ("Trial " + trialIndex + " Trainer " + trainer.toString() + " test data accuracy = "+ testAccuracy[c][trialIndex]);
					}

					if (trialIndex == 0) trainerNames[c] = trainer.toString();

				}  // end for each trainer
			}  // end for each trial

			// New reporting
			//"[train|test|validation]:[accuracy|f1|confusion|raw|precision|recall]"
			for (int c=0; c < numTrainers; c++) {
				System.out.println ("\n"+trainerNames[c].toString());

				if (ReportOptions[ReportOption.train][ReportOption.accuracy])
					System.out.println ("Summary. train accuracy mean = "+ MatrixOps.mean (trainAccuracy[c])+
							" stddev = "+ MatrixOps.stddev (trainAccuracy[c])+
							" stderr = "+ MatrixOps.stderr (trainAccuracy[c]));
				if (ReportOptions[ReportOption.train][ReportOption.precision]) {
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. train precision("+labels[k]+") mean = "+ MatrixOps.mean (trainPrecision[c][k])+
								" stddev = "+ MatrixOps.stddev (trainPrecision[c][k])+
								" stderr = "+ MatrixOps.stderr (trainPrecision[c][k]));
				}
				if (ReportOptions[ReportOption.train][ReportOption.recall]) {
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. train recall("+labels[k]+") mean = "+ MatrixOps.mean (trainRecall[c][k])+
								" stddev = "+ MatrixOps.stddev (trainRecall[c][k])+
								" stderr = "+ MatrixOps.stderr (trainRecall[c][k]));
				}
				if (ReportOptions[ReportOption.train][ReportOption.f1]){
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. train f1("+labels[k]+") mean = "+ MatrixOps.mean (trainF1[c][k])+
								" stddev = "+ MatrixOps.stddev (trainF1[c][k])+
								" stderr = "+ MatrixOps.stderr (trainF1[c][k]));

				}
				if (ReportOptions[ReportOption.validation][ReportOption.accuracy])
					System.out.println ("Summary. validation accuracy mean = "+ MatrixOps.mean (validationAccuracy[c])+
							" stddev = "+ MatrixOps.stddev (validationAccuracy[c])+
							" stderr = "+ MatrixOps.stderr (validationAccuracy[c]));
				if (ReportOptions[ReportOption.validation][ReportOption.precision]){
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. validation precision("+labels[k]+") mean = "+ MatrixOps.mean (validationPrecision[c][k])+
								" stddev = "+ MatrixOps.stddev (validationPrecision[c][k])+
								" stderr = "+ MatrixOps.stderr (validationPrecision[c][k]));
				}
				if (ReportOptions[ReportOption.validation][ReportOption.recall]){
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. validation recall("+labels[k]+") mean = "+ MatrixOps.mean (validationRecall[c][k])+
								" stddev = "+ MatrixOps.stddev (validationRecall[c][k])+
								" stderr = "+ MatrixOps.stderr (validationRecall[c][k]));
				}
				if (ReportOptions[ReportOption.validation][ReportOption.f1]){
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. validation f1("+labels[k]+") mean = "+ MatrixOps.mean (validationF1[c][k])+
								" stddev = "+ MatrixOps.stddev (validationF1[c][k])+
								" stderr = "+ MatrixOps.stderr (validationF1[c][k]));
				}

				if (ReportOptions[ReportOption.test][ReportOption.accuracy])
					System.out.println ("Summary. test accuracy mean = "+ MatrixOps.mean (testAccuracy[c])+
							" stddev = "+ MatrixOps.stddev (testAccuracy[c])+
							" stderr = "+ MatrixOps.stderr (testAccuracy[c]));
				if (ReportOptions[ReportOption.test][ReportOption.precision]){
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. test precision("+labels[k]+") mean = "+ MatrixOps.mean (testPrecision[c][k])+
								" stddev = "+ MatrixOps.stddev (testPrecision[c][k])+
								" stderr = "+ MatrixOps.stderr (testPrecision[c][k]));
				}
				if (ReportOptions[ReportOption.test][ReportOption.recall]){
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. test recall("+labels[k]+") mean = "+ MatrixOps.mean (testRecall[c][k])+
								" stddev = "+ MatrixOps.stddev (testRecall[c][k])+
								" stderr = "+ MatrixOps.stderr (testRecall[c][k]));
				}
				if (ReportOptions[ReportOption.test][ReportOption.f1]){
					for (int k =0 ; k < labels.length; k++) 
						System.out.println ("Summary. test f1("+labels[k]+") mean = "+ MatrixOps.mean (testF1[c][k])+
								" stddev = "+ MatrixOps.stddev (testF1[c][k])+
								" stderr = "+ MatrixOps.stderr (testF1[c][k]));
				}
			}   // end for each trainer
		}

		private static void printTrialClassification(Trial trial)
		{
			for (Classification c : trial) {
				Instance instance = c.getInstance();
				System.out.print(instance.getName() + " " + instance.getTarget() + " ");
				Labeling labeling = c.getLabeling();
				for (int j = 0; j < labeling.numLocations(); j++){
					System.out.print(labeling.getLabelAtRank(j).toString() + ":" + labeling.getValueAtRank(j) + " ");
				}
				System.out.println();
			}
		}

		private static Object createTrainer(String arg) {
			try {
				return interpreter.eval (arg);
			} catch (bsh.EvalError e) {
				throw new IllegalArgumentException ("Java interpreter eval error\n"+e);
			}
		}

		private static ClassifierTrainer getTrainer(String arg) {
			// parse something like Maxent,gaussianPriorVariance=10,numIterations=20

			// first, split the argument at commas.
			java.lang.String fields[] = arg.split(",");

			//Massage constructor name, so that MaxEnt, MaxEntTrainer, new MaxEntTrainer()
			// all call new MaxEntTrainer()
			java.lang.String constructorName = fields[0];
			Object trainer;
			if (constructorName.indexOf('(') != -1) // if contains (), pass it though
				trainer = createTrainer(arg);
			else {
				if (constructorName.endsWith("Trainer")){
					trainer = createTrainer("new " + constructorName + "()"); // add parens if they forgot
				}else{
					trainer = createTrainer("new "+constructorName+"Trainer()"); // make trainer name from classifier name
				}
			}

			// find methods associated with the class we just built
			Method methods[] =  trainer.getClass().getMethods();

			// find setters corresponding to parameter names.
			for (int i=1; i<fields.length; i++){
				java.lang.String nameValuePair[] = fields[i].split("=");
				java.lang.String parameterName  = nameValuePair[0];
				java.lang.String parameterValue = nameValuePair[1];  //todo: check for val present!
				java.lang.Object parameterValueObject;
				try {
					parameterValueObject = interpreter.eval(parameterValue);
				} catch (bsh.EvalError e) {
					throw new IllegalArgumentException ("Java interpreter eval error on parameter "+
							parameterName + "\n"+e);
				}

				boolean foundSetter = false;
				for (int j=0; j<methods.length; j++){
					// System.out.println("method " + j + " name is " + methods[j].getName());
					// System.out.println("set" + Character.toUpperCase(parameterName.charAt(0)) + parameterName.substring(1));
					if ( ("set" + Character.toUpperCase(parameterName.charAt(0)) + parameterName.substring(1)).equals(methods[j].getName()) &&
							methods[j].getParameterTypes().length == 1){
						// System.out.println("Matched method " + methods[j].getName());
						// Class[] ptypes = methods[j].getParameterTypes();
						// System.out.println("Parameter types:");
						// for (int k=0; k<ptypes.length; k++){
						// System.out.println("class " + k + " = " + ptypes[k].getName());
						// }

						try {
							java.lang.Object[] parameterList = new java.lang.Object[]{parameterValueObject};
							// System.out.println("Argument types:");
							// for (int k=0; k<parameterList.length; k++){
							// System.out.println("class " + k + " = " + parameterList[k].getClass().getName());
							// }
							methods[j].invoke(trainer, parameterList);
						} catch ( IllegalAccessException e) {
							System.out.println("IllegalAccessException " + e);
							throw new IllegalArgumentException ("Java access error calling setter\n"+e);
						}  catch ( InvocationTargetException e) {
							System.out.println("IllegalTargetException " + e);
							throw new IllegalArgumentException ("Java target error calling setter\n"+e);
						}
						foundSetter = true;
						break;
					}
				}
				if (!foundSetter){
					System.out.println("Parameter " + parameterName + " not found on trainer " + constructorName);
					System.out.println("Available parameters for " + constructorName);
					for (int j=0; j<methods.length; j++){
						if ( methods[j].getName().startsWith("set") && methods[j].getParameterTypes().length == 1){
							System.out.println(Character.toLowerCase(methods[j].getName().charAt(dataOptionsSize)) +
									methods[j].getName().substring(reportOptionsSize));
						}
					}

					throw new IllegalArgumentException ("no setter found for parameter " + parameterName);
				}
			}
			assert (trainer instanceof ClassifierTrainer);
			return ((ClassifierTrainer)trainer);
		}
}
