/* Copyright (C) 2003 University of Pennsylvania.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 */

package cc.mallet.share.upenn;

import java.util.regex.*;
import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.logging.*;

import cc.mallet.classify.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

/**
 * Simple wrapper for training a MALLET maxent classifier.
 *
 * @author <a href="mailto:pereira@cis.upenn.edu">Fernando Pereira</a>
 * @version 1.0
 */
public class MaxEntShell {
	private static Logger logger =
		MalletLogger.getLogger(MaxEntShell.class.getName());

	private MaxEntShell()
	{
	}

	private static final CommandOption.Double gaussianVarianceOption = new CommandOption.Double
	(MaxEntShell.class, "gaussian-variance", "decimal", true, 1.0,
			"The gaussian prior variance used for training.", null);

	private static final CommandOption.File trainOption = new CommandOption.File
	(MaxEntShell.class, "train", "FILENAME", true, null,
			"Training datafile", null);

	private static final CommandOption.File testOption = new CommandOption.File
	(MaxEntShell.class, "test", "filename", true, null,
			"Test datafile", null);

	private static final CommandOption.File classifyOption = new CommandOption.File
	(MaxEntShell.class, "classify", "filename", true, null,
			"Datafile to classify", null);

	private static final CommandOption.File modelOption = new CommandOption.File
	(MaxEntShell.class, "model", "filename", true, null,
			"Model file", null);

	private static final CommandOption.String encodingOption = new CommandOption.String
	(MaxEntShell.class, "encoding", "character-encoding-name", true,
			null, "Input character encoding", null);

	private static final CommandOption.Boolean internalTestOption = new CommandOption.Boolean
	(MaxEntShell.class, "internal-test", "true|false", true, false,
			"Run internal tests", null);

	private static final CommandOption.List commandOptions =
		new CommandOption.List (
				"Training, testing and running a generic tagger.",
				new CommandOption[] {
						gaussianVarianceOption,
						trainOption,
						testOption,
						modelOption,
						classifyOption,
						encodingOption,
						internalTestOption
				});

	/**
	 * Train a maxent classifier. Each row of <code>features</code>
	 * represents the features of a training instance. The label for
	 * that instance is in the corresponding position of
	 * <code>labels</code>.
	 *
	 * @param features Each row gives the on features of an instance
	 * @param labels Each position gives the label of an instance
	 * @param var Gaussian prior variance for training
	 * @param save if non-null, save the trained model to this file
	 * @return the maxent classifier
	 * @exception IOException if the trained model cannot be saved
	 */
	static public Classifier train(String[][]features, String[] labels, double var, File save) throws IOException 
	{
		return train(new
				PipeExtendedIterator(
						new ArrayDataAndTargetIterator(features, labels),
						new CharSequenceArray2TokenSequence()),
						var, save);
	}

	/**
	 * Train a maxent classifier. The iterator <code>data</code> returns
	 * training instances with a {@link TokenSequence} as data and a
	 * target object. The tokens in the instance data will be converted to
	 * features.
	 *
	 * @param data the iterator over training instances
	 * @param var Gaussian prior variance for training.
	 * @param save if non-null, save the trained model to this file
	 * @return the maxent classifier
	 * @exception IOException if the trained model cannot be saved
	 */
	static public Classifier train(Iterator<Instance> data, double var,
	                               File save)
	throws IOException {
		Alphabet features = new Alphabet();
		LabelAlphabet labels = new LabelAlphabet();
		Pipe instancePipe =
			new SerialPipes (new Pipe[] {
					new Target2Label(labels),
					new TokenSequence2FeatureSequence(features),
					new FeatureSequence2FeatureVector()});
		InstanceList trainingList = new InstanceList(instancePipe);
		trainingList.addThruPipe(data);
		logger.info("# features = " + features.size());
		logger.info("# labels = " + labels.size());
		logger.info("# training instances = " + trainingList.size());
		ClassifierTrainer trainer = new MaxEntTrainer(var);
		Classifier classifier = trainer.train(trainingList);
		logger.info("The training accuracy is "+
				classifier.getAccuracy (trainingList));
		features.stopGrowth();
		if (save != null) {
			ObjectOutputStream s =
				new ObjectOutputStream(new FileOutputStream(save));
			s.writeObject(classifier);
			s.close();
		}
		return classifier;
	}

	/**
	 * Test a maxent classifier. The data representation is the same as for
	 * training.
	 *
	 * @param classifier the classifier to test
	 * @param features an array of instances represented as arrays of features
	 * @param labels corresponding labels
	 * @return accuracy on the data
	 */
	static public double test(Classifier classifier,
	                          String[][]features, String[] labels) {
		return test(classifier,
				new PipeExtendedIterator(
						new ArrayDataAndTargetIterator(features, labels),
						new CharSequenceArray2TokenSequence()));
	}

	/**
	 * Test a maxent classifier. The data representation is the same as
	 * for training.
	 *
	 * @param classifier the classifier to test
	 * @param data an iterator over labeled instances
	 * @return accuracy on the data
	 */
	static public double test(Classifier classifier, Iterator<Instance> data) {
		InstanceList testList = new InstanceList (classifier.getInstancePipe());
		testList.addThruPipe(data);
		logger.info("# test instances = " + testList.size());
		double accuracy = classifier.getAccuracy(testList);
		return accuracy;
	}

	/**
	 * Compute the maxent classification of an instance.
	 *
	 * @param classifier the classifier
	 * @param features the features that are on for this instance
	 * @return the classification
	 */
	static public Classification classify(Classifier classifier,
	                                      String[] features) {
		return classifier.classify(
				new Instance(new TokenSequence(features), null, null, null));
	}

	/**
	 * Compute the maxent classifications of an array of instances
	 *
	 * @param classifier the classifier
	 * @param features each row represents the on features for an instance
	 * @return the array of classifications for the given instances
	 */
	static public Classification[] classify(Classifier classifier,
	                                        String[][] features) {
		return classify(classifier,
				new PipeExtendedIterator(
						new ArrayIterator(features),
						new CharSequenceArray2TokenSequence()));
	}

	/**
	 * Compute the maxent classifications for unlabeled instances given
	 * by an iterator.
	 *
	 * @param classifier the classifier
	 * @param data the iterator over unlabeled instances
	 * @return the array of classifications for the given instances
	 */
	static public Classification[] classify(Classifier classifier,
			Iterator<Instance> data) {
		InstanceList unlabeledList =
			new InstanceList(classifier.getInstancePipe());
		unlabeledList.addThruPipe(data);
		logger.info("# unlabeled instances = " + unlabeledList.size());
		List classifications = classifier.classify(unlabeledList);
		return (Classification[])classifications.toArray(new Classification[]{});
	}

	/**
	 * Load a classifier from a file.
	 *
	 * @param modelFile the file
	 * @return the classifier serialized in the file
	 * @exception IOException if the file cannot be opened or read
	 * @exception ClassNotFoundException if the file does not deserialize
	 */
	static public Classifier load(File modelFile)
	throws IOException, ClassNotFoundException {
		ObjectInputStream s =
			new ObjectInputStream(new FileInputStream(modelFile));
		Classifier c = (Classifier)s.readObject();
		s.close();      
		return c;
	}

	static private final String[][] internalData = {{"a", "b"}, {"b", "c"}, {"a", "c"}};
	static private final String[] internalTargets = {"yes", "no", "no"};
	static private final String[] internalInstance = {"a", "b", "c"};

	static private void internalTest() throws IOException {
		Classifier classifier = train(internalData, internalTargets, 1.0, null);
		System.out.println("Training accuracy = " +
				test(classifier, internalData, internalTargets));
		Classification cl =
			classify(classifier, internalInstance);
		Labeling lab = cl.getLabeling();
		LabelAlphabet labels = lab.getLabelAlphabet();
		for (int c = 0; c < labels.size(); c++)
			System.out.print(labels.lookupObject(c) + " " +
					lab.value(c) + " ");
		System.out.println();
	}

	private static InputStreamReader getReader(File file, String encoding)
	throws IOException {
		return encoding != null ?
				new InputStreamReader(
						new FileInputStream(file), encoding) :
							new FileReader(file);
	}

	/**
	 * Command-line wrapper to train, test, or run a maxent
	 * classifier. Instances are represented as follows:
	 * <dl>
	 * <dt>Labeled:</dt><dd><em>label</em> <em>feature-1</em> ... <em>feature-n</em></dd>
	 *<dt>Unlabeled:</dt><dd><em>feature-1</em> ... <em>feature-n</em></dd>
	 * </dl>
	 * @param args the command line arguments. Options (shell and Java quoting should be added as needed):
	 *<dl>
	 *<dt><code>--help</code> <em>boolean</em></dt>
	 *<dd>Print this command line option usage information.  Give <code>true</code> for longer documentation. Default is <code>false</code>.</dd>
	 *<dt><code>--prefix-code</code> <em>Java-code</em></dt>
	 *<dd>Java code you want run before any other interpreted code.  Note that the text is interpreted without modification, so unlike some other Java code options, you need to include any necessary 'new's. Default is null.</dd>
	 *<dt><code>--gaussian-variance</code> <em>positive-number</em></dt>
	 *<dd>The Gaussian prior variance used for training. Default is 1.0.</dd>
	 *<dt><code>--train</code> <em>filenane</em></dt>
	 *<dd>Train on labeled instances stored in <em>filename</em>. Default is no training.</dd>
	 *<dt><code>--test</code> <em>filename</em></dt>
	 *<dd>Test on the labeled instances stored in <em>filename</em>. Default is no testing.</dd>
	 *<dt><code>--classify</code> <em>filename</em></dt>
	 *<dd>Classify the unlabeled instances stored in <em>filename</em>. Default is no classification.</dd>
	 *<dt><code>--model</code> <em>filename</em></dt>
	 *<dd>The filename for reading (test/classify) or saving (train) the model. Default is no model file.</dd>
	 *</dl>
	 * @exception Exception if an error occurs
	 */

	static public void main (String[] args) throws Exception {
		Classifier classifier = null;
		Pipe preprocess =
			new CharSequence2TokenSequence(
					new CharSequenceLexer(CharSequenceLexer.LEX_NONWHITESPACE_TOGETHER));
		InputStreamReader trainingData = null, testData = null;
		Pattern instanceFormat = Pattern.compile("^\\s*(\\S+)\\s*(.*)\\s*$");
		Pattern unlabeledInstanceFormat = Pattern.compile("^\\s*(.*)\\s*$");

		commandOptions.process(args);
		if (internalTestOption.value)
			internalTest();
		if (trainOption.value != null) {
			trainingData = getReader(trainOption.value, encodingOption.value);
			classifier = train(
					new PipeExtendedIterator(
							new LineIterator (trainingData, instanceFormat, 2, 1, -1),
							preprocess),
							gaussianVarianceOption.value, modelOption.value);
		}
		else if (modelOption.value != null)
			classifier = load(modelOption.value);
		if (classifier != null) {
			if (testOption.value != null) {
				testData = getReader(testOption.value, encodingOption.value);
				System.out.println
				("The testing accuracy is "+ 
						test(classifier,
								new PipeExtendedIterator(
										new LineIterator (testData, instanceFormat, 2, 1, -1),
										preprocess)));
			}
			if (classifyOption.value != null) {
				classifier.getInstancePipe().setTargetProcessing(false);
				InputStreamReader unlabeledData =
					getReader(classifyOption.value, encodingOption.value);
				Classification[] cl = classify(classifier, new PipeExtendedIterator(
						new LineIterator(unlabeledData,
								unlabeledInstanceFormat,
								1, -1, -1),
								preprocess));
				for (int i = 0; i < cl.length; i++) {
					Labeling lab = cl[i].getLabeling();
					LabelAlphabet labels = lab.getLabelAlphabet();
					for (int c = 0; c < labels.size(); c++)
						System.out.print(labels.lookupObject(c) + " " +
								lab.value(c) + " ");
					System.out.println();
				}
			}
		}     
	}
}
