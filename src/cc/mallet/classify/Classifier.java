/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.classify;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import java.io.PrintWriter;
import java.io.Serializable;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.AlphabetCarrying;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Labeling;
import cc.mallet.types.FeatureSelection;

/**
 * Abstract parent of all Classifiers.
 * <p>
 * All classification techniques in MALLET are implemented as two classes:
 * a trainer and a classifier.  The trainer injests the training data
 * and creates a classifier that holds the parameters set during training.
 * The classifier applies those parameters to an Instance to produce
 * a classification of the Instance.
 * <p>
 * A concrete classifier is required only to be able to classify an instance.
 * <p>
 * Methods for classifying an InstanceList are here. There are
 * also methods for calculating precison, recall, and f1 from either
 * InstanceLists (which are classified first) or an ArrayList of
 * classifications. Similar functionality is also in
 * {@link cc.mallet.classify.Trial}
 *
 * <p> A classifier holds a reference to the pipe that was used to
 * create the Instances being classified.  Most classifiers use
 * this to make sure the Alphabets of the instances being classified
 * are the same Alphabet objects used during training.
 * <p>
 * Alphabets are allowed to between training and classification.
 * @see ClassifierTrainer
 * @see Instance
 * @see InstanceList
 * @see Classification
 * @see Trial
 */
public abstract class Classifier implements AlphabetCarrying, Serializable
{
	private static Logger logger = Logger.getLogger(Classifier.class.getName());

	protected Pipe instancePipe;

	/** For serialization only. */
	protected Classifier()
	{
	}

	public Classifier (Pipe instancePipe)
	{
		this.instancePipe = instancePipe;
		// All classifiers must have set of labels.
		assert (instancePipe.getTargetAlphabet() != null);
		assert (instancePipe.getTargetAlphabet().getClass().isAssignableFrom(LabelAlphabet.class));
		// Not all classifiers require a feature dictionary, however.
	}

	// TODO Change this method name to getPipe();
	public Pipe getInstancePipe ()
	{
		return instancePipe;
	}

	public Alphabet getAlphabet ()
	{
		return (Alphabet) instancePipe.getDataAlphabet();
	}

	public LabelAlphabet getLabelAlphabet ()
	{
		return (LabelAlphabet) instancePipe.getTargetAlphabet();
	}
	
	public Alphabet[] getAlphabets() 
	{
		return new Alphabet[] {getAlphabet(), getLabelAlphabet()};
	}
	
	public boolean alphabetsMatch (AlphabetCarrying object)
	{
		Alphabet[] otherAlphabets = object.getAlphabets();
		if (otherAlphabets.length == 2 && otherAlphabets[0] == getAlphabet() && otherAlphabets[1] == getLabelAlphabet())
			return true;
		return false;
	}

	

	// TODO Make argument List<Instance>
	public ArrayList<Classification> classify (InstanceList instances)
	{
		ArrayList<Classification> ret = new ArrayList<Classification> (instances.size());
		for (Instance inst : instances)
			ret.add (classify (inst));
		return ret;
	}

	public Classification[] classify (Instance[] instances)
	{
		Classification[] ret = new Classification[instances.length];
		for (int i = 0; i < instances.length; i++)
			ret[i] = classify (instances[i]);
		return ret;
	}

	public abstract Classification classify (Instance instance);

	/** Pipe the object through this classifier's pipe, then classify the resulting instance. */
	public Classification classify (Object obj)
	{
		if (obj instanceof Instance)
			return classify ((Instance)obj);
		return classify (instancePipe.instanceFrom(new Instance (obj, null, null, null)));
	}
	
	
	public FeatureSelection getFeatureSelection () { return null; }
	public FeatureSelection[] getPerClassFeatureSelection () { return null; }

	
	// Various evaluation methods
	
	public double getAccuracy (InstanceList ilist) { return new Trial(this, ilist).getAccuracy(); }
	public double getPrecision (InstanceList ilist, int index) { return new Trial(this, ilist).getPrecision(index); }
	public double getPrecision (InstanceList ilist, Labeling labeling) { return new Trial(this, ilist).getPrecision(labeling); }
	public double getPrecision (InstanceList ilist, Object labelEntry) { return new Trial(this, ilist).getPrecision(labelEntry); }
	public double getRecall (InstanceList ilist, int index) { return new Trial(this, ilist).getRecall(index); }
	public double getRecall (InstanceList ilist, Labeling labeling) { return new Trial(this, ilist).getRecall(labeling); }
	public double getRecall (InstanceList ilist, Object labelEntry) { return new Trial(this, ilist).getRecall(labelEntry); }
	public double getF1 (InstanceList ilist, int index) { return new Trial(this, ilist).getF1(index); }
	public double getF1 (InstanceList ilist, Labeling labeling) { return new Trial(this, ilist).getF1(labeling); }
	public double getF1 (InstanceList ilist, Object labelEntry) { return new Trial(this, ilist).getF1(labelEntry); }
	public double getAverageRank (InstanceList ilist) { return new Trial(this, ilist).getAverageRank(); }
	

	/**
	 * Outputs human-readable description of classifier (e.g., list of weights, decision tree)
	 *  to System.out
	 */
	public void print () {
		System.out.println ("Classifier "+getClass().getName()+"\n  Detailed printout not yet implemented.");
	}

	public void print (PrintWriter out) {
		out.println ("Classifier "+getClass().getName()+"\n  Detailed printout not yet implemented.");
	}

}
