/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify.tui;

import java.util.logging.*;
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

public class Vectors2Vectors
{
	private static Logger logger = MalletLogger.getLogger(Vectors2Vectors.class.getName());

	static CommandOption.File inputFile = new CommandOption.File
	(Vectors2Vectors.class, "input", "FILE", true, new File("-"),
	 "Read the instance list from this file; Using - indicates stdin.", null);

	static CommandOption.File trainingFile = new CommandOption.File
	(Vectors2Vectors.class, "training-file", "FILE", true, new File("training.vectors"),
	 "Write the training set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.File testFile = new CommandOption.File
	(Vectors2Vectors.class, "testing-file", "FILE", true, new File("test.vectors"),
	 "Write the test set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.File validationFile = new CommandOption.File
	(Vectors2Vectors.class, "validation-file", "FILE", true, new File("validation.vectors"),
	 "Write the validation set instance list to this file; Using - indicates stdout.", null);

	static CommandOption.Double trainingProportion = new CommandOption.Double
	(Vectors2Vectors.class, "training-portion", "DECIMAL", true, 1.0,
	 "The fraction of the instances that should be used for training.", null);

	static CommandOption.Double validationProportion = new CommandOption.Double
	(Vectors2Vectors.class, "validation-portion", "DECIMAL", true, 0.0,
	 "The fraction of the instances that should be used for validation.", null);

	static CommandOption.Integer randomSeed = new CommandOption.Integer
	(Vectors2Vectors.class, "random-seed", "INTEGER", true, 0,
	 "The random seed for randomly selecting a proportion of the instance list for training", null);

	static CommandOption.Integer pruneInfogain = new CommandOption.Integer
  (Vectors2Vectors.class, "prune-infogain", "N", false, 0,
	 "Reduce features to the top N by information gain.", null);

  static CommandOption.Integer pruneCount = new CommandOption.Integer
  (Vectors2Vectors.class, "prune-count", "N", false, 0,
   "Reduce features to those that occur more than N times.", null);

  static CommandOption.Boolean vectorToSequence = new CommandOption.Boolean
  (Vectors2Info.class, "vector-to-sequence", "[TRUE|FALSE]", false, false,
   "Convert FeatureVector's to FeatureSequence's.", null);


  public static void main (String[] args) throws FileNotFoundException, IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Vectors2Vectors.class,
		"A tool for manipulating instance lists of feature vectors.");
		CommandOption.process (Vectors2Vectors.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(Vectors2Vectors.class).printUsage(false);
			System.exit (-1);
		}
		if (false && !inputFile.wasInvoked()) {
			System.err.println ("You must specify an input instance list, with --input.");
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

    // Read the InstanceList
		InstanceList ilist = InstanceList.load (inputFile.value);

		if (pruneInfogain.wasInvoked() || pruneCount.wasInvoked())
    {
			InstanceList[] ilists = ilist.split (r, new double[] {t, 1-t-v, v});

      if (pruneInfogain.value > 0 || pruneCount.value > 0) {
        if (ilists[1].size() > 0 || ilists[2].size() > 0)
          throw new UnsupportedOperationException("Infogain/count processing of test or validation lists not yet supported.");

        if (pruneCount.value > 0) {
          Alphabet alpha2 = new Alphabet ();
          Noop pipe2 = new Noop (alpha2, ilists[0].getTargetAlphabet());
          InstanceList ilist2 = new InstanceList (pipe2);
          int numFeatures = ilists[0].getDataAlphabet().size();
          double[] counts = new double[numFeatures];
          for (int ii = 0; ii < ilists[0].size(); ii++) {
            Instance instance = ilists[0].get(ii);
            FeatureVector fv = (FeatureVector) instance.getData();
            fv.addTo(counts);
          }
          BitSet bs = new BitSet(numFeatures);
          for (int fi = 0; fi < numFeatures; fi++)
            if (counts[fi] > pruneCount.value) bs.set(fi);
          logger.info ("Pruning "+(numFeatures-bs.cardinality())+" features out of "+numFeatures
                       +" leaving "+(bs.cardinality())+" features.");
          FeatureSelection fs = new FeatureSelection (ilists[0].getDataAlphabet(), bs);
          for (int ii = 0; ii < ilists[0].size(); ii++) {
            Instance instance = ilists[0].get(ii);
            FeatureVector fv = (FeatureVector) instance.getData();
            FeatureVector fv2 = FeatureVector.newFeatureVector (fv, alpha2, fs);
            ilist2.add(new Instance(fv2, instance.getTarget(), instance.getName(), instance.getSource()),
                       ilists[0].getInstanceWeight(ii));
            instance.unLock();
            instance.setData(null); // So it can be freed by the garbage collector
          }
          ilists[0] = ilist2;
        }

        if (pruneInfogain.value > 0) {
          Alphabet alpha2 = new Alphabet ();
          Noop pipe2 = new Noop (alpha2, ilists[0].getTargetAlphabet());
          InstanceList ilist2 = new InstanceList (pipe2);
          InfoGain ig = new InfoGain (ilists[0]);
          FeatureSelection fs = new FeatureSelection (ig, pruneInfogain.value);
          for (int ii = 0; ii < ilists[0].size(); ii++) {
            Instance instance = ilists[0].get(ii);
            FeatureVector fv = (FeatureVector) instance.getData();
            FeatureVector fv2 = FeatureVector.newFeatureVector (fv, alpha2, fs);
            instance.unLock();
            instance.setData(null); // So it can be freed by the garbage collector
            ilist2.add(pipe2.instanceFrom(new Instance(fv2, instance.getTarget(), instance.getName(), instance.getSource())),
                       ilists[0].getInstanceWeight(ii));
          }
          ilists[0] = ilist2;
        }

        if (vectorToSequence.value) {
          // Convert FeatureVector's to FeatureSequence's by simply randomizing the order
          // of all the word occurrences, including repetitions due to values larger than 1.
          Alphabet alpha = ilists[0].getDataAlphabet();
          Noop pipe2 = new Noop (alpha, ilists[0].getTargetAlphabet());
          InstanceList ilist2 = new InstanceList (pipe2);
          for (int ii = 0; ii < ilists[0].size(); ii++) {
            Instance instance = ilists[0].get(ii);
            FeatureVector fv = (FeatureVector) instance.getData();
            ArrayList seq = new ArrayList();
            for (int loc = 0; loc < fv.numLocations(); loc++)
              for (int count = 0; count < fv.valueAtLocation(loc); count++)
                seq.add (new Integer(fv.indexAtLocation(loc)));
            Collections.shuffle(seq);
            int[] indices = new int[seq.size()];
            for (int i = 0; i < indices.length; i++)
              indices[i] = ((Integer)seq.get(i)).intValue();
            FeatureSequence fs = new FeatureSequence (alpha, indices);
            instance.unLock();
            instance.setData(null); // So it can be freed by the garbage collector
            ilist2.add(pipe2.instanceFrom(new Instance(fs, instance.getTarget(), instance.getName(), instance.getSource())),
                       ilists[0].getInstanceWeight(ii));
          }
          ilists[0] = ilist2;
        }

        writeInstanceList (ilists[0], trainingFile.value());
      }
      else if (trainingProportion.wasInvoked() || validationProportion.wasInvoked())
      {
        // And write them out
        if (ilists[0].size() > 0)
          writeInstanceList(ilists[0], trainingFile.value());
        if (ilists[1].size() > 0)
          writeInstanceList(ilists[1], testFile.value());
        if (ilists[2].size() > 0)
          writeInstanceList(ilists[2], validationFile.value());
      } else
      System.err.println ("Use either --training-proportion or --feature-infogain.  Now exiting doing nothing.");
    }

	}

	private static void writeInstanceList(InstanceList ilist, File file)
	throws FileNotFoundException, IOException
	{
    logger.info ("Writing instance list to "+file);
    ObjectOutputStream oos;
		oos = new ObjectOutputStream(new FileOutputStream(file));
		oos.writeObject(ilist);
		oos.close();
	}
}
