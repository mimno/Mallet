/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package cc.mallet.classify;


import java.util.logging.*;

import cc.mallet.classify.Classifier;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Labeling;
import cc.mallet.types.Multinomial;
import cc.mallet.util.MalletLogger;
/**
	 A decision tree learner, roughly ID3, but only to a fixed given depth in all branches.

	 Does not yet implement splitting of continuous-valued features, but
	 it should in the future.  Currently a feature is considered
	 "present" if it has positive value.
	 ftp://ftp.cs.cmu.edu/project/jair/volume4/quinlan96a.ps

	 Only set up for conveniently learning decision stubs:  there is no pruning or
	 good stopping rule.  Currently only stop by reaching a maximum depth.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class DecisionTreeTrainer extends ClassifierTrainer<DecisionTree> implements Boostable
{
	private static Logger logger = MalletLogger.getLogger(DecisionTreeTrainer.class.getName());
	
	public static final int DEFAULT_MAX_DEPTH = 5;
	public static final double DEFAULT_MIN_INFO_GAIN_SPLIT = 0.001;
	
	int maxDepth = DEFAULT_MAX_DEPTH;
	double minInfoGainSplit = 0.001;
	boolean finished = false;
	DecisionTree classifier = null;
	
	public DecisionTreeTrainer (int maxDepth)	{ this.maxDepth = maxDepth; }
	public DecisionTreeTrainer () {	this(4); }
	
	public DecisionTreeTrainer setMaxDepth (int maxDepth) { this.maxDepth = maxDepth; return this; }
	public DecisionTreeTrainer setMinInfoGainSplit (double m) { this.minInfoGainSplit = m; return this; }
	
	public boolean isFinishedTraining() { return finished; } 
	public DecisionTree getClassifier() { return classifier; }
	
	public DecisionTree train (InstanceList trainingList) {
		FeatureSelection selectedFeatures = trainingList.getFeatureSelection();
		DecisionTree.Node root = new DecisionTree.Node (trainingList, null, selectedFeatures);
		splitTree (root, selectedFeatures, 0);
		root.stopGrowth();
		finished = true;
		System.out.println ("DecisionTree learned:");
		root.print();
		this.classifier = new DecisionTree (trainingList.getPipe(), root);
		return classifier;
	}


	protected void splitTree (DecisionTree.Node node, FeatureSelection selectedFeatures, int depth)
	{
		if (depth == maxDepth || node.getSplitInfoGain() < minInfoGainSplit)
			return;
		logger.info("Splitting feature \""+node.getSplitFeature()
												+"\" infogain="+node.getSplitInfoGain());
		node.split(selectedFeatures);
		splitTree (node.getFeaturePresentChild(), selectedFeatures, depth+1);
		splitTree (node.getFeatureAbsentChild(), selectedFeatures, depth+1);
	}

	
	public static abstract class Factory extends ClassifierTrainer.Factory<DecisionTreeTrainer>
	{
		protected static int maxDepth = DEFAULT_MAX_DEPTH;
		protected static double minInfoGainSplit = DEFAULT_MIN_INFO_GAIN_SPLIT;
		// This is recommended (but cannot be enforced in Java) that subclasses implement
		// public static Classifier train (InstanceList trainingSet)
		// public static Classifier train (InstanceList trainingSet, InstanceList validationSet)
		// public static Classifier train (InstanceList trainingSet, InstanceList validationSet, Classifier initialClassifier)
		// which call 
		
		public DecisionTreeTrainer newClassifierTrainer (Classifier initialClassifier) {
			DecisionTreeTrainer t = new DecisionTreeTrainer ();
			t.maxDepth = this.maxDepth;
			t.minInfoGainSplit = this.minInfoGainSplit;
			
			return t;
		}
	}	

	/*
	public static void main () {
		DecisionTreeTrainer.Factory dtf = new DecisionTreeTrainer.Factory() {{ maxDepth = 6; }};
		DecisionTreeTrainer.Factory dtf = new DecisionTreeTrainer.Factory().setMaxDepth(6).setMinInfoGainSplit(.2);
	}
	*/
	
}
