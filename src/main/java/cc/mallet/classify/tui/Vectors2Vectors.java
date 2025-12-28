/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.tui;

import java.util.logging.*;
import java.util.Iterator;
import java.util.Random;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Collections;
import java.io.*;

import cc.mallet.classify.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
/**
   A command-line tool for manipulating InstanceLists.  For example,
   reducing the feature space by information gain.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
*/

public class Vectors2Vectors {

	private static Logger logger = MalletLogger.getLogger(Vectors2Vectors.class.getName());

	static CommandOption.File inputFile = new CommandOption.File(Vectors2Vectors.class, "input", "FILE", true, new File("-"),
		 "Read the instance list from this file; Using - indicates stdin.", null);

	static CommandOption.File outputFile = new CommandOption.File(Vectors2Vectors.class, "output", "FILE", true, new File("-"),
		 "Write pruned instance list to this file (use --training-file etc. if you are splitting the list). Using - indicates stdout.", null);

	static CommandOption.File trainingFile = new CommandOption.File(Vectors2Vectors.class, "training-file", "FILE", true, new File("training.vectors"),
		 "Write the training set instance list to this file (or use --output if you are only pruning features); Using - indicates stdout.", null);

	static CommandOption.File testFile = new CommandOption.File(Vectors2Vectors.class, "testing-file", "FILE", true, new File("test.vectors"),
		 "Write the test set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.File validationFile = new CommandOption.File(Vectors2Vectors.class, "validation-file", "FILE", true, new File("validation.vectors"),
		 "Write the validation set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.Double trainingProportion = new CommandOption.Double(Vectors2Vectors.class, "training-portion", "DECIMAL", true, 1.0,
		 "The fraction (0.0 - 1.0) of the instances that should be used for training.", null);

	static CommandOption.Double validationProportion = new CommandOption.Double(Vectors2Vectors.class, "validation-portion", "DECIMAL", true, 0.0,
		 "The fraction (0.0 - 1.0) of the instances that should be used for validation.", null);

	static CommandOption.Integer randomSeed = new CommandOption.Integer(Vectors2Vectors.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for randomly selecting a proportion of the instance list for training", null);

	static CommandOption.Integer pruneInfogain = new CommandOption.Integer(Vectors2Vectors.class, "prune-infogain", "N", false, 0,
		 "Reduce features to the top N by information gain.", null);

	static CommandOption.Integer pruneCount = new CommandOption.Integer(Vectors2Vectors.class, "prune-count", "N", false, 0,
		 "Reduce features to those that occur more than N times.", null);

	static CommandOption.Integer pruneDocFreq = new CommandOption.Integer(Vectors2Vectors.class, "prune-document-freq", "N", false, 0,
		 "Reduce features to those that occur in more than N contexts.", null);

	static CommandOption.Double minIDF = new CommandOption.Double(Vectors2Vectors.class, "min-idf", "NUMBER", false, 0,
		 "Remove frequent features with inverse document frequency less than this value.", null);

	static CommandOption.Double maxIDF = new CommandOption.Double(Vectors2Vectors.class, "max-idf", "NUMBER", false, Double.POSITIVE_INFINITY,
		 "Remove rare features with inverse document frequency greater than this value.", null);

	static CommandOption.Boolean vectorToSequence = new CommandOption.Boolean(Vectors2Vectors.class, "vector-to-sequence", "[TRUE|FALSE]", false, false,
		 "Convert FeatureVector's to FeatureSequence's.", null);
	
	static CommandOption.Boolean hideTargets = new CommandOption.Boolean(Vectors2Vectors.class, "hide-targets", "[TRUE|FALSE]", false, false,
		 "Hide targets.", null);
	 
	static CommandOption.Boolean revealTargets = new CommandOption.Boolean(Vectors2Vectors.class, "reveal-targets", "[TRUE|FALSE]", false, false,
		 "Reveal targets.", null);

	public static void main (String[] args) throws FileNotFoundException, IOException {

		// Process the command-line options
		CommandOption.setSummary (Vectors2Vectors.class,
								  "A tool for manipulating instance lists of feature vectors.");
		CommandOption.process (Vectors2Vectors.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(Vectors2Vectors.class).printUsage(false);
			System.exit (-1);
		}

		Random r = randomSeed.wasInvoked() ? new Random (randomSeed.value) : new Random ();
		double t = trainingProportion.value;
		double v = validationProportion.value;
		logger.info ("Training portion = "+t);
		logger.info ("Validation portion = "+v);
		logger.info ("Testing portion = "+(1-v-t));
		logger.info ("Prune info gain = "+pruneInfogain.value);
		logger.info ("Prune count = "+pruneCount.value);
		logger.info ("Prune df = "+pruneDocFreq.value);
		logger.info ("idf range = "+minIDF.value + "-" + maxIDF.value);

		// Read the InstanceList
		InstanceList instances = InstanceList.load (inputFile.value);

		if (t == 1.0 && !vectorToSequence.value && ! (pruneInfogain.wasInvoked() || pruneCount.wasInvoked() || pruneDocFreq.wasInvoked() || minIDF.wasInvoked() || maxIDF.wasInvoked())
			&& ! (hideTargets.wasInvoked() || revealTargets.wasInvoked())) {
			logger.warning("Vectors2Vectors was invoked, but did not change anything");
			instances.save(trainingFile.value());
			System.exit(0);
		}

		if (pruneInfogain.wasInvoked() || pruneDocFreq.wasInvoked() || pruneCount.wasInvoked() || minIDF.wasInvoked() || maxIDF.wasInvoked()) {
			
			// Are we also splitting the instances?
			//  Current code doesn't want to do this, so I'm 
			//  not changing it, but I don't know a reason. -DM
			if (t != 1.0) {
				throw new UnsupportedOperationException("Infogain/count processing of test or validation lists not yet supported.");
			}
			
			if (pruneCount.wasInvoked() || pruneDocFreq.wasInvoked() || minIDF.wasInvoked() || maxIDF.wasInvoked()) {

				FeatureCountTool counter = new FeatureCountTool(instances);
				counter.count();
				
				int minDocs = 0;
				int maxDocs = Integer.MAX_VALUE;
				int minCount = 0;
				int maxCount = Integer.MAX_VALUE;
				
				if (pruneCount.wasInvoked()) {
					minCount = pruneCount.value;
				}
				if (pruneDocFreq.wasInvoked()) {
					minDocs = pruneDocFreq.value;
					System.out.println("min docs: " + minDocs);
				}
				if (maxIDF.wasInvoked()) {
					minDocs = (int) Math.floor( instances.size() * Math.exp(-maxIDF.value) );
				}
				if (minIDF.wasInvoked()) {
					maxDocs = (int) Math.ceil( instances.size() * Math.exp(-minIDF.value) );
				}
				
				Alphabet oldAlphabet = instances.getDataAlphabet();
				Alphabet newAlphabet = counter.getPrunedAlphabet(minDocs, maxDocs, minCount, maxCount);

				// Check which type of data element the instances contain
				Instance firstInstance = instances.get(0);
				if (firstInstance.getData() instanceof FeatureSequence) {
					// Version for feature sequences
								
					// It's necessary to create a new instance list in
					//  order to make sure that the data alphabet is correct.
					Noop newPipe = new Noop (newAlphabet, instances.getTargetAlphabet());
					InstanceList newInstanceList = new InstanceList (newPipe);
					
					// Iterate over the instances in the old list, adding
					//  up occurrences of features.
					int numFeatures = oldAlphabet.size();
					double[] counts = new double[numFeatures];
					for (int ii = 0; ii < instances.size(); ii++) {
						Instance instance = instances.get(ii);
						FeatureSequence fs = (FeatureSequence) instance.getData();
						
						fs.addFeatureWeightsTo(counts);
					}
					
					Instance instance, newInstance;

					// Next, iterate over the same list again, adding 
					//  each instance to the new list after pruning.
					while (instances.size() > 0) {
						instance = instances.get(0);
						FeatureSequence fs = (FeatureSequence) instance.getData();

						fs.prune(newAlphabet);

						newInstanceList.add(newPipe.instanceFrom(new Instance(fs, instance.getTarget(),
																			  instance.getName(),
																			  instance.getSource())));
						instances.remove(0);
					}
					
					logger.info("features: " + oldAlphabet.size() + 
								" -> " + newAlphabet.size());
					
					// Make the new list the official list.
					instances = newInstanceList;


				}
				else if (firstInstance.getData() instanceof FeatureVector) {
					// Version for FeatureVector

					Alphabet alpha2 = new Alphabet ();
					Noop pipe2 = new Noop (alpha2, instances.getTargetAlphabet());
					InstanceList instances2 = new InstanceList (pipe2);
					int numFeatures = oldAlphabet.size();
					double[] counts = new double[numFeatures];
										
					BitSet bs = new BitSet(numFeatures);
					
					for (int feature = 0; feature < numFeatures; feature++) {
						if (newAlphabet.contains(oldAlphabet.lookupObject(feature))) {
							bs.set(feature);
						}
					}
					
					logger.info ("Pruning "+(numFeatures-bs.cardinality())+" features out of " + numFeatures
								 +"; leaving "+(bs.cardinality())+" features.");
					
					FeatureSelection fs = new FeatureSelection (oldAlphabet, bs);
					
					for (int ii = 0; ii < instances.size(); ii++) {
						
						Instance instance = instances.get(ii);
						FeatureVector fv = (FeatureVector) instance.getData();
						FeatureVector fv2 = FeatureVector.newFeatureVector (fv, alpha2, fs);
						
						instances2.add(new Instance(fv2, instance.getTarget(), instance.getName(), instance.getSource()),
									   instances.getInstanceWeight(ii));
						instance.unLock();
						instance.setData(null); // So it can be freed by the garbage collector
					}
					instances = instances2;
				}
				else {
					throw new UnsupportedOperationException("Pruning features from " +
															firstInstance.getClass().getName() +
															" is not currently supported");
				}
				
			}
			
			if (pruneInfogain.value > 0) {
				Alphabet alpha2 = new Alphabet ();
				Noop pipe2 = new Noop (alpha2, instances.getTargetAlphabet());
				InstanceList instances2 = new InstanceList (pipe2);
				InfoGain ig = new InfoGain (instances);
				FeatureSelection fs = new FeatureSelection (ig, pruneInfogain.value);
				for (int ii = 0; ii < instances.size(); ii++) {
					Instance instance = instances.get(ii);
					FeatureVector fv = (FeatureVector) instance.getData();
					FeatureVector fv2 = FeatureVector.newFeatureVector (fv, alpha2, fs);
					instance.unLock();
					instance.setData(null); // So it can be freed by the garbage collector
					instances2.add(pipe2.instanceFrom(new Instance(fv2, instance.getTarget(), instance.getName(), instance.getSource())),
								   instances.getInstanceWeight(ii));
				}
				instances = instances2;
			}
			
			if (vectorToSequence.value) {
				// Convert FeatureVector's to FeatureSequence's by simply randomizing the order
				// of all the word occurrences, including repetitions due to values larger than 1.
				Alphabet alpha = instances.getDataAlphabet();
				Noop pipe2 = new Noop (alpha, instances.getTargetAlphabet());
				InstanceList instances2 = new InstanceList (pipe2);
				for (int ii = 0; ii < instances.size(); ii++) {
					Instance instance = instances.get(ii);
					FeatureVector fv = (FeatureVector) instance.getData();
					ArrayList seq = new ArrayList();
					for (int loc = 0; loc < fv.numLocations(); loc++)
						for (int count = 0; count < fv.valueAtLocation(loc); count++)
							seq.add (Integer.valueOf(fv.indexAtLocation(loc)));
					Collections.shuffle(seq);
					int[] indices = new int[seq.size()];
					for (int i = 0; i < indices.length; i++)
						indices[i] = ((Integer)seq.get(i)).intValue();
					FeatureSequence fs = new FeatureSequence (alpha, indices);
					instance.unLock();
					instance.setData(null); // So it can be freed by the garbage collector
					instances2.add(pipe2.instanceFrom(new Instance(fs, instance.getTarget(), instance.getName(), instance.getSource())),
								   instances.getInstanceWeight(ii));
				}
				instances = instances2;
			}
			
			if (outputFile.wasInvoked()) {
				writeInstanceList (instances, outputFile.value());
			}
			else if (trainingFile.wasInvoked()) {
				writeInstanceList (instances, trainingFile.value());
			}
			else {
				throw new IllegalArgumentException("You must specify a file to write to, using --output [filename]");
			}
		}
		else if (vectorToSequence.value) {
			// Convert FeatureVector's to FeatureSequence's by simply randomizing the order
			// of all the word occurrences, including repetitions due to values larger than 1.
			Alphabet alpha = instances.getDataAlphabet();
			Noop pipe2 = new Noop (alpha, instances.getTargetAlphabet());
			InstanceList instances2 = new InstanceList (pipe2);
			for (int ii = 0; ii < instances.size(); ii++) {
				Instance instance = instances.get(ii);
				FeatureVector fv = (FeatureVector) instance.getData();
				ArrayList seq = new ArrayList();
				for (int loc = 0; loc < fv.numLocations(); loc++)
					for (int count = 0; count < fv.valueAtLocation(loc); count++)
						seq.add (Integer.valueOf(fv.indexAtLocation(loc)));
				Collections.shuffle(seq);
				int[] indices = new int[seq.size()];
				for (int i = 0; i < indices.length; i++)
					indices[i] = ((Integer)seq.get(i)).intValue();
				FeatureSequence fs = new FeatureSequence (alpha, indices);
				instance.unLock();
				instance.setData(null); // So it can be freed by the garbage collector
				instances2.add(pipe2.instanceFrom(new Instance(fs, instance.getTarget(), instance.getName(), instance.getSource())),
							   instances.getInstanceWeight(ii));
			}
			instances = instances2;
			if (outputFile.wasInvoked()) {
				writeInstanceList (instances, outputFile.value());
			}
		}
		else if (trainingProportion.wasInvoked() || validationProportion.wasInvoked()) {
			
			// Split into three lists...
			InstanceList[] instanceLists = instances.split (r, new double[] {t, 1-t-v, v});

			// And write them out
			if (instanceLists[0].size() > 0)
				writeInstanceList(instanceLists[0], trainingFile.value());
			if (instanceLists[1].size() > 0)
				writeInstanceList(instanceLists[1], testFile.value());
			if (instanceLists[2].size() > 0)
				writeInstanceList(instanceLists[2], validationFile.value());
		}
		else if (hideTargets.wasInvoked()) {
			Iterator<Instance> iter = instances.iterator();
			while (iter.hasNext()) {
				Instance instance = iter.next();
				instance.unLock();
				instance.setProperty("target", instance.getTarget());
				instance.setTarget(null);
				instance.lock();
			}
			if (outputFile.wasInvoked()) {
				writeInstanceList (instances, outputFile.value());
			}
		}
		else if (revealTargets.wasInvoked()) {
			Iterator<Instance> iter = instances.iterator();
			while (iter.hasNext()) {
				Instance instance = iter.next();
				instance.unLock();
				instance.setTarget(instance.getProperty("target"));
				instance.lock();
			}
			if (outputFile.wasInvoked()) {
				writeInstanceList (instances, outputFile.value());
			}	
		}
	}

	private static void writeInstanceList(InstanceList instances, File file)
		throws FileNotFoundException, IOException {

		logger.info ("Writing instance list to "+file);
		instances.save(file);
	}
}
