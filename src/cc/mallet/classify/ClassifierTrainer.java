/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify;

import java.io.*;
import java.util.*;

import cc.mallet.classify.Classifier;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Labeler;
import cc.mallet.util.BshInterpreter;
import cc.mallet.util.CommandOption;
/**
 * Abstract parent of all classifier trainers.
 * <p>
 * All classification techniques in MALLET are implement as two classes:
 * a trainer and a classifier.  The trainer ingests the training data
 * and creates a classifier that holds the parameters set during training.
 * The classifier applies those parameters to an Instance to produce
 * a classification of the Instance.
 * <p>
 * A concrete trainer is required only to be able to train from an InstanceList.
 * Trainers that can incrementally train are subclasses of IncrementalTrainingClassifier.
 * <p>
 * The command line interface tools for document classification are:
 * {@link cc.mallet.classify.tui.Csv2Vectors},
 * {@link cc.mallet.classify.tui.Text2Vectors},
 * {@link cc.mallet.classify.tui.Vectors2Classify},
 * {@link cc.mallet.classify.tui.Vectors2Info}, and
 * {@link cc.mallet.classify.tui.Vectors2Vectors}
 *
 * @see Classifier
 * @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

/** Each ClassifierTrainer trains one Classifier based on various interfaces for consuming training data.
 * If you want an object that can train be asked to train on multiple different training sets and 
 * yield different classifiers, you probably want a ClassifierTrainer.Factory. */
public abstract class ClassifierTrainer<C extends Classifier>
{
	protected InstanceList validationSet;
	protected boolean finishedTraining = false;
	
	public boolean isFinishedTraining() { return finishedTraining; } // Careful to set this properly in subclasses!  Consider removing -akm 1/08
	public abstract C getClassifier();
	public abstract C train (InstanceList trainingSet);  
	public void setValidationInstances (InstanceList validationSet) { this.validationSet = validationSet; }
	public InstanceList getValidationInstances () { return this.validationSet; }
	
	/* No, it is fine if these can be set in the constructor only.  
	 * Individual ClassifierTrainer subclasses could provide this interface if desired. 
	public C setInitialClassifier (C initialClassifier) { return null; }
	public C getInitialClassifier () { return null; } 
	*/
	
		
	public interface ByOptimization<C extends Classifier> {
		public C train (InstanceList trainingSet, int numIterations);
		public Optimizer getOptimizer ();	
		public abstract int getIteration();
	}
	
	/** For active learning, in which this trainer will select certain instances and 
	 * request that the Labeler instance label them. 
	 * @param trainingAndUnlabeledSet the instances on which to train; some may be labeled; unlabeled ones may have their label requested from the labeler.
	 * @param labeler  
	 * @param numLabelRequests the number of times to call labeler.label(). */
	public interface ByActiveLearning<C extends Classifier> {
		public C train (InstanceList trainingAndUnlabeledSet, Labeler labeler, int numLabelRequests);
	}

	/** For various kinds of online learning by batches, where training instances are presented,
	 * consumed for learning immediately.  The same instances may be presented more than once to 
	 * this interface.  For example, StochasticGradient, etc conforms to this interface. */
	public interface ByIncrements<C extends Classifier> {
		public C trainIncremental (InstanceList trainingInstancesToAdd);
	}
	
	/** For online learning that can operate on one instance at a time.  For example, Perceptron. */
	public interface ByInstanceIncrements<C extends Classifier> extends ByIncrements<C> {
		public C trainIncremental (Instance instanceToAdd);
	}

	/** Instances of a Factory know how to create new ClassifierTrainers to apply to new Classifiers. */
	public static abstract class Factory<CT extends ClassifierTrainer<? extends Classifier>>
	{
		// This is recommended (but cannot be enforced in Java) that subclasses implement
		// public static Classifier train (InstanceList trainingSet)
		// public static Classifier train (InstanceList trainingSet, InstanceList validationSet)
		// public static Classifier train (InstanceList trainingSet, InstanceList validationSet, Classifier initialClassifier)
		// which call 
		
		public abstract CT newClassifierTrainer (Classifier initialClassifier);
		public CT newClassifierTrainer () { return newClassifierTrainer (null); }
				
		@Override public String toString() {
			return this.getClass().getName();
		}

	}

}
