/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
 This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 http://www.cs.umass.edu/~mccallum/mallet
 This software is provided under the terms of the Common Public License,
 version 1.0, as published by http://www.opensource.org.  For further
 information, see the file `LICENSE' included with this distribution. */




package cc.mallet.classify;

import java.util.logging.Logger;

import cc.mallet.classify.Boostable;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;


/**
 * A C4.5 decision tree learner, approximtely.  Currently treats all 
 * features as continuous-valued, and has no notion of missing values.<p>
 *
 * This implementation uses MDL for pruning.<p>
 *
 * J. R. Quinlan<br>
 * "Improved Use of Continuous Attributes in C4.5" <br>
 * ftp://ftp.cs.cmu.edu/project/jair/volume4/quinlan96a.ps<p>
 *
 * J. R. Quinlan and R. L. Rivest<br>
 * "Inferring Decision Trees Using Minimum Description Length Principle"
 *
 * @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class C45Trainer extends ClassifierTrainer<C45> implements Boostable
{
	private static Logger logger = MalletLogger.getLogger(C45Trainer.class.getName());
	boolean m_depthLimited = false;
	int m_maxDepth = 4;
	int m_minNumInsts = 2;  // minimum number of instances in each node
	boolean m_doPruning = true;
	C45 classifier;
	@Override public C45 getClassifier () { return classifier; }

	/**
	 * Uses default values: not depth limited tree with 
	 * a minimum of 2 instances in each leaf node
	 */
	public C45Trainer() {}
	
	/**
	 * Construct a depth-limited tree with the given depth limit
	 */	
	public C45Trainer(int maxDepth)
	{
		m_maxDepth = maxDepth;
		m_depthLimited = true;
	}
	
	public C45Trainer(boolean doPruning)
	{
		m_doPruning = doPruning;
	}
	
	public C45Trainer(int maxDepth, boolean doPruning)
	{
		m_depthLimited = true;
		m_maxDepth = maxDepth;
		m_doPruning = doPruning;
	}
	
	public void setDoPruning(boolean doPruning)
	{
		m_doPruning = doPruning;
	}
	
	public boolean getDoPruning()
	{
		return m_doPruning;
	}
	
	public void setDepthLimited(boolean depthLimited)
	{
		m_depthLimited = depthLimited;
	}
	
	public boolean getDepthLimited()
	{
		return m_depthLimited;
	}
	
	public void setMaxDepth(int maxDepth)
	{
		m_maxDepth = maxDepth;
	}
	
	public int getMaxDepth()
	{
		return m_maxDepth;
	}
	
	public void setMinNumInsts(int minNumInsts)
	{
		m_minNumInsts = minNumInsts;
	}
	
	public int getMinNumInsts()
	{
		return m_minNumInsts;
	}
	
	protected void splitTree(C45.Node node, int depth)
	{
		// Stop growing the tree when any of the following is true:
		//   1.  We care about tree depth and maximum depth is reached
		//   2.  The entropy of the node is too small (i.e., all 
		//       instances belong to the same class)
		//   3.  The gain ratio of the best split available is too small
		if (m_depthLimited && depth == m_maxDepth) {
			logger.info("Splitting stopped: maximum depth reached (" + m_maxDepth + ")");
			return;
		}       
		else if (Maths.almostEquals(node.getGainRatio().getBaseEntropy(), 0)) {
			logger.info("Splitting stopped: entropy of node too small (" + node.getGainRatio().getBaseEntropy() + ")");
			return;
		}
		else if (Maths.almostEquals(node.getGainRatio().getMaxValue(), 0)) {
			logger.info("Splitting stopped: node has insignificant gain ratio (" + node.getGainRatio().getMaxValue() + ")");
			return;
		}
		logger.info("Splitting feature \""+node.getSplitFeature()
				+"\" at threshold=" + node.getGainRatio().getMaxValuedThreshold() 
				+ " gain ratio="+node.getGainRatio().getMaxValue());
		node.split();
		splitTree(node.getLeftChild(), depth+1);
		splitTree(node.getRightChild(), depth+1);
	}
	
	@Override public C45 train (InstanceList trainingList)
	{
		FeatureSelection selectedFeatures = trainingList.getFeatureSelection();
		if (selectedFeatures != null)
			// xxx Attend to FeatureSelection!!!
			throw new UnsupportedOperationException ("FeatureSelection not yet implemented.");
		C45.Node root = new C45.Node(trainingList, null, m_minNumInsts);
		splitTree(root, 0);
		C45 tree = new C45 (trainingList.getPipe(), root);
		logger.info("C45 learned: (size=" + tree.getSize() + ")\n");
		tree.print();
		if (m_doPruning) {
			tree.prune();
			logger.info("\nPruned C45: (size=" + tree.getSize() + ")\n");
			root.print();
		}
		root.stopGrowth();
		this.classifier = tree;
		return classifier;
	}
	
}
