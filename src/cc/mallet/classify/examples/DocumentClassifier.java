/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Takes a list of directory names as arguments, (each directory
	 should contain all the text files for each class), performs a random train/test split,
	 trains a classifier, and outputs accuracy on the testing and training sets.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.classify.examples;

import java.io.*;

import cc.mallet.classify.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;

public class DocumentClassifier
{
	static public void main (String[] args)
	{
		// Create Java File objects for each of the arguments
		File[] directories = new File[args.length];
		for (int i = 0; i < args.length; i++)
			directories[i] = new File (args[i]);

		// Create the pipeline that will take as input {data = File, target = String for classname}
		// and turn them into {data = FeatureVector, target = Label}
		Pipe instancePipe = new SerialPipes (new Pipe[] {
			new Target2Label (),							  // Target String -> class label
			new Input2CharSequence (),				  // Data File -> String containing contents
			new CharSubsequence (CharSubsequence.SKIP_HEADER), // Remove UseNet or email header
			new CharSequence2TokenSequence (),  // Data String -> TokenSequence
			new TokenSequenceLowercase (),		  // TokenSequence words lowercased
			new TokenSequenceRemoveStopwords (),// Remove stopwords from sequence
			new TokenSequence2FeatureSequence(),// Replace each Token with a feature index
			new FeatureSequence2FeatureVector(),// Collapse word order into a "feature vector"
			new PrintInputAndTarget(),
		});

		// Create an empty list of the training instances
		InstanceList ilist = new InstanceList (instancePipe);

		// Add all the files in the directories to the list of instances.
		// The Instance that goes into the beginning of the instancePipe
		// will have a File in the "data" slot, and a string from args[] in the "target" slot.
		ilist.add (new FileIterator (directories, FileIterator.STARTING_DIRECTORIES));

		// Make a test/train split; ilists[0] will be for training; ilists[1] will be for testing
		InstanceList[] ilists = ilist.split (new double[] {.5, .5});

		// Create a classifier trainer, and use it to create a classifier
		ClassifierTrainer naiveBayesTrainer = new NaiveBayesTrainer ();
		Classifier classifier = naiveBayesTrainer.train (ilists[0]);

		System.out.println ("The training accuracy is "+ classifier.getAccuracy (ilists[0]));
		System.out.println ("The testing accuracy is "+ classifier.getAccuracy (ilists[1]));
	}
	
}
