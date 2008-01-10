/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify.tui;



import java.io.*;
import java.util.*;
import java.util.Random;
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
	private static Logger logger = MalletLogger.getLogger(Vectors2Classify.class.getName());
	private static Logger progressLogger = MalletProgressMessageLogger.getLogger(Vectors2Classify.class.getName() + "-pl");
	private static ArrayList classifierTrainers = new ArrayList();
    private static boolean[][] ReportOptions = new boolean[3][4];
    private static String[][] ReportOptionArgs = new String[3][4];  //arg in dataset:reportOption=arg
	// Essentially an enum mapping string names to enums to ints.
	private static class ReportOption
	{
		static final String[] dataOptions = {"train", "test", "validation"};
		static final String[] reportOptions = {"accuracy", "f1", "confusion", "raw"};
		static final int train=0;
		static final int test =1;
		static final int validation=2;
		static final int accuracy=0;
		static final int f1=1;
		static final int confusion=2;
		static final int raw=3;
	}
	static CommandOption.SpacedStrings report = new CommandOption.SpacedStrings
	(Vectors2Classify.class, "report", "[train|test|validation]:[accuracy|f1:label|confusion|raw]",
	 true, new String[] {"test:accuracy", "test:confusion",  "train:accuracy"},
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

				//find the report option (accuracy, f1, confusion, raw)
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


				if (j == ReportOption.f1){
					// make sure a label was specified for f1
					if (reportOptionArg == null){
						throw new IllegalArgumentException("F1 must have label argument in --report " + this.value[argi]);
					}
					// Pass through the string argument
					ReportOptionArgs[i][j]= reportOptionArg;

				}else if (reportOptionArg != null){
					throw new IllegalArgumentException("No arguments after = allowed in --report " + this.value[argi]);
				}
			}
		}
	};

	static CommandOption.Object trainerConstructor = new CommandOption.Object
	(Vectors2Classify.class, "trainer", "ClassifierTrainer constructor",	true, new NaiveBayesTrainer(),
	 "Java code for the constructor used to create a ClassifierTrainer.  "+
	 "If no '(' appears, then \"new \" will be prepended and \"Trainer()\" will be appended."+
	 "You may use this option mutiple times to compare multiple classifiers.", null)
		{
			public void parseArg (java.lang.String arg) {
				// parse something like Maxent,gaussianPriorVariance=10,numIterations=20
				//System.out.println("Arg = " + arg);

                // first, split the argument at commas.
				java.lang.String fields[] = arg.split(",");

				//Massage constructor name, so that MaxEnt, MaxEntTrainer, new MaxEntTrainer()
				// all call new MaxEntTrainer()
				java.lang.String constructorName = fields[0];
				if (constructorName.indexOf('(') != -1)     // if contains (), pass it though
					super.parseArg(arg);
				else {
					if (constructorName.endsWith("Trainer")){
						super.parseArg("new " + constructorName + "()"); // add parens if they forgot
					}else{
						super.parseArg("new "+constructorName+"Trainer()"); // make trainer name from classifier name
					}
				}

				// find methods associated with the class we just built
				Method methods[] =  this.value.getClass().getMethods();

				// find setters corresponding to parameter names.
				for (int i=1; i<fields.length; i++){
					java.lang.String nameValuePair[] = fields[i].split("=");
					java.lang.String parameterName  = nameValuePair[0];
					java.lang.String parameterValue = nameValuePair[1];  //todo: check for val present!
					java.lang.Object parameterValueObject;
					try {
						parameterValueObject = getInterpreter().eval(parameterValue);
					} catch (bsh.EvalError e) {
						throw new IllegalArgumentException ("Java interpreter eval error on parameter "+
						                                    parameterName + "\n"+e);
					}

					boolean foundSetter = false;
					for (int j=0; j<methods.length; j++){
//						System.out.println("method " + j + " name is " + methods[j].getName());
//						System.out.println("set" + Character.toUpperCase(parameterName.charAt(0)) + parameterName.substring(1));
						if ( ("set" + Character.toUpperCase(parameterName.charAt(0)) + parameterName.substring(1)).equals(methods[j].getName()) &&
							methods[j].getParameterTypes().length == 1){
//							System.out.println("Matched method " + methods[j].getName());
//							Class[] ptypes = methods[j].getParameterTypes();
//							System.out.println("Parameter types:");
//							for (int k=0; k<ptypes.length; k++){
//								System.out.println("class " + k + " = " + ptypes[k].getName());
//							}

							try {
								java.lang.Object[] parameterList = new java.lang.Object[]{parameterValueObject};
//								System.out.println("Argument types:");
//								for (int k=0; k<parameterList.length; k++){
//									System.out.println("class " + k + " = " + parameterList[k].getClass().getName());
//								}
								methods[j].invoke(this.value, parameterList);
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
								System.out.println(Character.toLowerCase(methods[j].getName().charAt(3)) +
								                   methods[j].getName().substring(4));
							}
						}

						throw new IllegalArgumentException ("no setter found for parameter " + parameterName);
					}
				}

			}
			public void postParsing (CommandOption.List list) {
				assert (this.value instanceof ClassifierTrainer);
				//System.out.println("v2c PostParsing " + this.value);
				classifierTrainers.add (this.value);
			}
		};

	static CommandOption.String outputFile = new CommandOption.String
	(Vectors2Classify.class, "output-classifier", "FILENAME", true, "classifier.mallet",
	 "The filename in which to write the classifier after it has been trained.", null);

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

	public static void main (String[] args) throws bsh.EvalError, java.io.IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Vectors2Classify.class,
		"A tool for training, saving and printing diagnostics from a classifier on vectors.");
		CommandOption.process (Vectors2Classify.class, args);

		// handle default trainer here for now; default argument processing doesn't  work
		if (!trainerConstructor.wasInvoked()){
			classifierTrainers.add (new NaiveBayesTrainer());
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

        if (!separateIlists) { // normal case, --input-file specified
            // Read in the InstanceList, from stdin if the input filename is "-".
            ilist = InstanceList.load (new File(inputFile.value));
        }else{  // user specified separate files for testing and training sets.
            trainingFileIlist = InstanceList.load (new File(trainingFile.value));
            logger.info("Training vectors loaded from " + trainingFile.value);

            if (testFile.wasInvoked()){
                testFileIlist = InstanceList.load (new File(testFile.value));
                logger.info("Testing vectors loaded from " + testFile.value);
            }

            if (validationFile.wasInvoked()){
                validationFileIlist = InstanceList.load (new File(validationFile.value));
                logger.info("validation vectors loaded from " + validationFile.value);
            }

        }

		int numTrials = numTrialsOption.value;
		Random r = randomSeedOption.wasInvoked() ? new Random (randomSeedOption.value) : new Random ();

		ClassifierTrainer[] trainers = new ClassifierTrainer[classifierTrainers.size()];
		for (int i = 0; i < classifierTrainers.size(); i++) {
			trainers[i] = (ClassifierTrainer) classifierTrainers.get(i);
			logger.fine ("Trainer specified = "+trainers[i].toString());
		}

		double trainAccuracy[][] = new double[trainers.length][numTrials];
		double testAccuracy[][] = new double[trainers.length][numTrials];
		double validationAccuracy[][] = new double[trainers.length][numTrials];

		String trainConfusionMatrix[][] = new String[trainers.length][numTrials];
		String testConfusionMatrix[][] = new String[trainers.length][numTrials];
		String validationConfusionMatrix[][] = new String[trainers.length][numTrials];

		double t = trainingProportionOption.value;
		double v = validationProportionOption.value;

        if (!separateIlists) {
          logger.info("Training portion = " + t);
          logger.info(" Unlabeled training sub-portion = "+unlabeledProportionOption.value);
          logger.info("Validation portion = " + v);
          logger.info("Testing portion = " + (1 - v - t));
        }

//		for (int i=0; i<3; i++){
//			for (int j=0; j<4; j++){
//				System.out.print(" " + ReportOptions[i][j]);
//			}
//			System.out.println();
//		}

		for (int trialIndex = 0; trialIndex < numTrials; trialIndex++) {
			System.out.println("\n-------------------- Trial " + trialIndex + "  --------------------\n");
      InstanceList[] ilists;
      BitSet unlabeledIndices = null;
      if (!separateIlists){
        ilists = ilist.split (r, new double[] {t, 1-t-v, v});
      } else {
        ilists = new InstanceList[3];
        ilists[0] = trainingFileIlist;
        ilists[1] = testFileIlist;
        ilists[2] = testFileIlist;
      }
      if (unlabeledProportionOption.value > 0)
        unlabeledIndices = new cc.mallet.util.Randoms(r.nextInt())
            .nextBitSet(ilists[0].size(),
                        unlabeledProportionOption.value);


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
			long time[] = new long[trainers.length];
			for (int c = 0; c < trainers.length; c++){
				time[c] = System.currentTimeMillis();
				System.out.println ("Trial " + trialIndex + " Training " + trainers[c].toString() + " with "+ilists[0].size()+" instances");
        if (unlabeledProportionOption.value > 0)
          ilists[0].hideSomeLabels(unlabeledIndices);
        trainers[c].setValidationInstances(ilists[2]);
        Classifier classifier = trainers[c].train (ilists[0]);
        if (unlabeledProportionOption.value > 0)
          ilists[0].unhideAllLabels();

        System.out.println ("Trial " + trialIndex + " Training " + trainers[c].toString() + " finished");
				time[c] = System.currentTimeMillis() - time[c];
				Trial trainTrial = new Trial (classifier, ilists[0]);
				//assert (ilists[1].size() > 0);
				Trial testTrial = new Trial (classifier, ilists[1]);
				Trial validationTrial = new Trial(classifier, ilists[2]);

				if (ilists[0].size()>0) trainConfusionMatrix[c][trialIndex] = new ConfusionMatrix (trainTrial).toString();
				if (ilists[1].size()>0) testConfusionMatrix[c][trialIndex] = new ConfusionMatrix (testTrial).toString();
				if (ilists[2].size()>0) validationConfusionMatrix[c][trialIndex] = new ConfusionMatrix (validationTrial).toString();

				trainAccuracy[c][trialIndex] = trainTrial.getAccuracy();
				testAccuracy[c][trialIndex] = testTrial.getAccuracy();
				validationAccuracy[c][trialIndex] = validationTrial.getAccuracy();

				if (outputFile.wasInvoked()) {
					String filename = outputFile.value;
					if (trainers.length > 1) filename = filename+trainers[c].toString();
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
					System.out.println("Trial " + trialIndex + " Trainer " + trainers[c].toString());
					System.out.println(" Raw Training Data");
					printTrialClassification(trainTrial);
				}

				if (ReportOptions[ReportOption.test][ReportOption.raw]){
					System.out.println("Trial " + trialIndex + " Trainer " + trainers[c].toString());
					System.out.println(" Raw Testing Data");
					printTrialClassification(testTrial);
				}

				if (ReportOptions[ReportOption.validation][ReportOption.raw]){
					System.out.println("Trial " + trialIndex + " Trainer " + trainers[c].toString());
					System.out.println(" Raw Validation Data");
					printTrialClassification(validationTrial);
				}


				//train
				if (ReportOptions[ReportOption.train][ReportOption.confusion]){
					System.out.println("Trial " + trialIndex + " Trainer " + trainers[c].toString() +  " Training Data Confusion Matrix");
					if (ilists[0].size()>0) System.out.println (trainConfusionMatrix[c][trialIndex]);
				}

				if (ReportOptions[ReportOption.train][ReportOption.accuracy]){
					System.out.println ("Trial " + trialIndex + " Trainer " + trainers[c].toString() + " training data accuracy= "+ trainAccuracy[c][trialIndex]);
				}

				if (ReportOptions[ReportOption.train][ReportOption.f1]){
					String label = ReportOptionArgs[ReportOption.train][ReportOption.f1];
					System.out.println ("Trial " + trialIndex + " Trainer " + trainers[c].toString() + " training data F1(" + label + ") = "+ trainTrial.getF1(label));
				}

				//validation
				if (ReportOptions[ReportOption.validation][ReportOption.confusion]){
					System.out.println("Trial " + trialIndex + " Trainer " + trainers[c].toString() +  " Validation Data Confusion Matrix");
					if (ilists[2].size()>0) System.out.println (validationConfusionMatrix[c][trialIndex]);
				}

				if (ReportOptions[ReportOption.validation][ReportOption.accuracy]){
					System.out.println ("Trial " + trialIndex + " Trainer " + trainers[c].toString() + " validation data accuracy= "+ validationAccuracy[c][trialIndex]);
				}

				if (ReportOptions[ReportOption.validation][ReportOption.f1]){
					String label = ReportOptionArgs[ReportOption.validation][ReportOption.f1];
					System.out.println ("Trial " + trialIndex + " Trainer " + trainers[c].toString() + " validation data F1(" + label + ") = "+ validationTrial.getF1(label));
				}

				//test
				if (ReportOptions[ReportOption.test][ReportOption.confusion]){
					System.out.println("Trial " + trialIndex + " Trainer " + trainers[c].toString() + " Test Data Confusion Matrix");
					if (ilists[1].size()>0) System.out.println (testConfusionMatrix[c][trialIndex]);
				}

				if (ReportOptions[ReportOption.test][ReportOption.accuracy]){
					System.out.println ("Trial " + trialIndex + " Trainer " + trainers[c].toString() + " test data accuracy= "+ testAccuracy[c][trialIndex]);
				}

				if (ReportOptions[ReportOption.test][ReportOption.f1]){
					String label = ReportOptionArgs[ReportOption.test][ReportOption.f1];
					System.out.println ("Trial " + trialIndex + " Trainer " + trainers[c].toString() + " test data F1(" + label + ") = "+ testTrial.getF1(label));
				}


			}  // end for each trainer
		}  // end for each trial

        // New reporting
		//"[train|test|validation]:[accuracy|f1|confusion|raw]"
		for (int c=0; c < trainers.length; c++) {
			System.out.println ("\n"+trainers[c].toString());
			if (ReportOptions[ReportOption.train][ReportOption.accuracy])
				System.out.println ("Summary. train accuracy mean = "+ MatrixOps.mean (trainAccuracy[c])+
														" stddev = "+ MatrixOps.stddev (trainAccuracy[c])+
														" stderr = "+ MatrixOps.stderr (trainAccuracy[c]));

			if (ReportOptions[ReportOption.validation][ReportOption.accuracy])
				System.out.println ("Summary. validation accuracy mean = "+ MatrixOps.mean (validationAccuracy[c])+
														" stddev = "+ MatrixOps.stddev (validationAccuracy[c])+
														" stderr = "+ MatrixOps.stderr (validationAccuracy[c]));

			if (ReportOptions[ReportOption.test][ReportOption.accuracy])
				System.out.println ("Summary. test accuracy mean = "+ MatrixOps.mean (testAccuracy[c])+
														" stddev = "+ MatrixOps.stddev (testAccuracy[c])+
														" stderr = "+ MatrixOps.stderr (testAccuracy[c]));

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


}
