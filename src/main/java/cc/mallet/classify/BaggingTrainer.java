/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify;

import cc.mallet.types.*;
/**
	 Bagging Trainer.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class BaggingTrainer extends ClassifierTrainer<BaggingClassifier>
{
	ClassifierTrainer.Factory underlyingTrainer;
	int numBags;
	BaggingClassifier classifier;
	public BaggingClassifier getClassifier () { return classifier; }

	public BaggingTrainer (ClassifierTrainer.Factory underlyingTrainerFactory, int numBags)
	{
		this.underlyingTrainer = underlyingTrainerFactory;
		this.numBags = numBags;
	}

	public BaggingTrainer (ClassifierTrainer.Factory underlyingTrainerFactory)
	{
		this (underlyingTrainerFactory, 10);
	}

	public BaggingClassifier train (InstanceList trainingList)
	{
		Classifier[] classifiers = new Classifier[numBags];
		java.util.Random r = new java.util.Random ();
		for (int round = 0; round < numBags; round++) {
			InstanceList bag = trainingList.sampleWithReplacement (r, trainingList.size());
			classifiers[round] = underlyingTrainer.newClassifierTrainer().train (bag);
		}
		this.classifier = new BaggingClassifier (trainingList.getPipe(), classifiers);
		return classifier;
	}

}
