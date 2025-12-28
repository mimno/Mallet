/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
 This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 http://www.cs.umass.edu/~mccallum/mallet
 This software is provided under the terms of the Common Public License,
 version 1.0, as published by http://www.opensource.org.  For further
 information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.logging.Logger;

import cc.mallet.classify.Boostable;
import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.GainRatio;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;


/**
 * A C4.5 Decision Tree classifier.
 *
 * @see C45Trainer
 * @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class C45 extends Classifier implements Boostable, Serializable
{
	private static Logger logger = MalletLogger.getLogger(C45.class.getName());
	Node m_root;
	
	public C45 (Pipe instancePipe, C45.Node root)
	{
		super (instancePipe);
		m_root = root;
	}
	
	public Node getRoot ()
	{
		return m_root;
	}
	
	private Node getLeaf (Node node, FeatureVector fv)
	{
		if (node.getLeftChild() == null && node.getRightChild() == null)
			return node;
		else if (fv.value(node.getGainRatio().getMaxValuedIndex()) <= node.getGainRatio().getMaxValuedThreshold())
			return getLeaf(node.getLeftChild(), fv);
		else
			return getLeaf(node.getRightChild(), fv);
	}
	
	public Classification classify (Instance instance)
	{
		FeatureVector fv = (FeatureVector) instance.getData ();
		assert (instancePipe == null || fv.getAlphabet () == this.instancePipe.getDataAlphabet ());
		
		Node leaf = getLeaf(m_root, fv);
		return new Classification (instance, this, leaf.getGainRatio().getBaseLabelDistribution());
	}
	
	/**
	 * Prune the tree using minimum description length
	 */
	public void prune()
	{
		getRoot().computeCostAndPrune();
	}
	
	/**
	 * @return the total number of nodes in this tree
	 */
	public int getSize()
	{
		Node root = getRoot();        
		if (root == null)
			return 0;
		return 1+root.getNumDescendants();
	}
	
	/**
	 * Prints the tree
	 */
	public void print()
	{
		if (getRoot() != null)
			getRoot().print();
	}
	
	public static class Node implements Serializable
	{
		private static final long serialVersionUID = 1L;
		
		GainRatio m_gainRatio;
		// the entire set of instances given to the root node
		InstanceList m_ilist;
		// indices of instances at this node
		int[] m_instIndices;
		// data vocabulary
		Alphabet m_dataDict;
		// mininum number of instances allowed in this node
		int m_minNumInsts;
		Node m_parent, m_leftChild, m_rightChild;
		
		public Node(InstanceList ilist, Node parent, int minNumInsts)
		{
			this(ilist, parent, minNumInsts, null);
		}
		
		public Node(InstanceList ilist, Node parent, int minNumInsts, int[] instIndices)
		{
			if (instIndices == null) {
				instIndices = new int[ilist.size()];
				for (int ii = 0; ii < instIndices.length; ii++)
					instIndices[ii] = ii;
			}
			m_gainRatio = GainRatio.createGainRatio(ilist, instIndices, minNumInsts);
			m_ilist = ilist;
			m_instIndices = instIndices;
			m_dataDict = m_ilist.getDataAlphabet();
			m_minNumInsts = minNumInsts;
			m_parent = parent;
			m_leftChild = m_rightChild = null;
		}
		
		/** The root has depth zero. */
		public int depth ()
		{
			int depth = 0;
			Node p = m_parent;
			while (p != null) {
				p = p.m_parent;
				depth++;
			}
			return depth;
		}
		
		public int getSize() { return m_instIndices.length; }
		public boolean isLeaf() { return (m_leftChild == null && m_rightChild == null); }
		public boolean isRoot() { return m_parent == null; }
		public Node getParent() { return m_parent; }
		public Node getLeftChild() { return m_leftChild; }
		public Node getRightChild() { return m_rightChild; }
		public GainRatio getGainRatio() { return m_gainRatio; }
		public Object getSplitFeature() { return m_dataDict.lookupObject(m_gainRatio.getMaxValuedIndex()); }
		
		public InstanceList getInstances() 
		{ 
			InstanceList ret = new InstanceList(m_ilist.getPipe());
			for (int ii = 0; ii < m_instIndices.length; ii++)
				ret.add(m_ilist.get(m_instIndices[ii]));
			return ret; 
		}
		
		/** 
		 * Count the number of non-leaf descendant nodes
		 */
		public int getNumDescendants()
		{
			if (isLeaf())
				return 0;
			int count = 0;
			if (! getLeftChild().isLeaf())
				count += 1 + getLeftChild().getNumDescendants();
			if (! getRightChild().isLeaf())
				count += 1 + getRightChild().getNumDescendants();
			return count;
		}
		
		public void split()
		{
			if (m_ilist == null)
				throw new IllegalStateException ("Frozen.  Cannot split.");
			int numLeftChildren = 0;
			boolean[] toLeftChild = new boolean[m_instIndices.length];
			for (int i = 0; i < m_instIndices.length; i++) {
				Instance instance = m_ilist.get(m_instIndices[i]);
				FeatureVector fv = (FeatureVector) instance.getData();
				if (fv.value (m_gainRatio.getMaxValuedIndex()) <= m_gainRatio.getMaxValuedThreshold()) {
					toLeftChild[i] = true;
					numLeftChildren++;
				}
				else
					toLeftChild[i] = false;
			}
			logger.info("leftChild.size=" + numLeftChildren 
					+ " rightChild.size=" + (m_instIndices.length-numLeftChildren));
			int[] leftIndices = new int[numLeftChildren];
			int[] rightIndices = new int[m_instIndices.length - numLeftChildren];
			int li = 0, ri = 0;
			for (int i = 0; i < m_instIndices.length; i++) {
				if (toLeftChild[i])
					leftIndices[li++] = m_instIndices[i];
				else
					rightIndices[ri++] = m_instIndices[i];
			}
			m_leftChild = new Node(m_ilist, this, m_minNumInsts, leftIndices);
			m_rightChild = new Node(m_ilist, this, m_minNumInsts, rightIndices);
		}
		
		public double computeCostAndPrune()
		{
			double costS = getMDL();

			if (isLeaf())
				return costS + 1;

			double minCost1 = getLeftChild().computeCostAndPrune();
			double minCost2 = getRightChild().computeCostAndPrune();
			double costSplit = Math.log(m_gainRatio.getNumSplitPointsForBestFeature()) / GainRatio.log2;
			double minCostN = Math.min(costS+1, costSplit+1+minCost1+minCost2);

			if (Maths.almostEquals(minCostN, costS+1))
				m_leftChild = m_rightChild = null;

			return minCostN;
		}
		
		/**
		 * Calculates the minimum description length of this node, i.e., 
		 * the length of the binary encoding that describes the feature 
		 * and the split value used at this node
		 */
		public double getMDL()
		{
			int numClasses = m_ilist.getTargetAlphabet().size();
			double mdl = getSize() * getGainRatio().getBaseEntropy();
			mdl += ((numClasses-1) * Math.log(getSize() / 2.0)) / (2 * GainRatio.log2);
			double piPow = Math.pow(Math.PI, numClasses/2.0);
			double gammaVal = Maths.gamma(numClasses/2.0);
			mdl += Math.log(piPow/gammaVal) / GainRatio.log2;
			return mdl;
		}
		
		/**
		 * Saves memory by allowing ilist to be garbage collected
		 * (deletes this node's associated instance list)
		 */
		public void stopGrowth ()
		{
			if (m_leftChild != null)
				m_leftChild.stopGrowth();
			if (m_rightChild != null)
				m_rightChild.stopGrowth();	  
			m_ilist = null;
		}
		
		public String getName()
		{
			return getStringBufferName().toString();
		}
		
		public StringBuffer getStringBufferName()
		{
			StringBuffer sb = new StringBuffer();
			if (m_parent == null)
				return sb.append("root");
			else if (m_parent.getParent() == null) {
				sb.append("(\"");
				sb.append(m_dataDict.lookupObject(m_parent.getGainRatio().getMaxValuedIndex()).toString());
				sb.append("\"");
				if (m_parent.getLeftChild() == this)
					sb.append(" <= ");
				else
					sb.append(" > ");
				sb.append(m_parent.getGainRatio().getMaxValuedThreshold());
				return sb.append(")");
			} 
			else {
				sb.append(m_parent.getStringBufferName());
				sb.append(" && (\"");
				sb.append(m_dataDict.lookupObject(m_parent.getGainRatio().getMaxValuedIndex()).toString());
				sb.append("\"");
				if (m_parent.getLeftChild() == this)
					sb.append(" <= ");
				else 
					sb.append(" > ");
				sb.append(m_parent.getGainRatio().getMaxValuedThreshold());
				return sb.append(")");
			}
		}
		
		/**
		 * Prints the tree rooted at this node
		 */
		public void print()
		{
			print("");
		}
		
		public void print(String prefix)
		{	  
			if (isLeaf()) {
				int bestLabelIndex = getGainRatio().getBaseLabelDistribution().getBestIndex();
				int numMajorityLabel = (int) (getGainRatio().getBaseLabelDistribution().value(bestLabelIndex) * getSize());
				System.out.println("root:" + getGainRatio().getBaseLabelDistribution().getBestLabel() + " " + numMajorityLabel + "/" + getSize());
			}
			else {
				String featName = m_dataDict.lookupObject(getGainRatio().getMaxValuedIndex()).toString();
				double threshold = getGainRatio().getMaxValuedThreshold();
				System.out.print(prefix + "\"" + featName + "\" <= " + threshold + ":");
				if (m_leftChild.isLeaf()) {
					int bestLabelIndex = m_leftChild.getGainRatio().getBaseLabelDistribution().getBestIndex();
					int numMajorityLabel = (int) (m_leftChild.getGainRatio().getBaseLabelDistribution().value(bestLabelIndex) * m_leftChild.getSize());
					System.out.println(m_leftChild.getGainRatio().getBaseLabelDistribution().getBestLabel() + " " + numMajorityLabel + "/" + m_leftChild.getSize());
				}
				else {
					System.out.println();
					m_leftChild.print(prefix + "|    ");
				}	      
				System.out.print(prefix + "\"" + featName + "\" > " + threshold + ":");
				if (m_rightChild.isLeaf()) {
					int bestLabelIndex = m_rightChild.getGainRatio().getBaseLabelDistribution().getBestIndex();
					int numMajorityLabel = (int) (m_rightChild.getGainRatio().getBaseLabelDistribution().value(bestLabelIndex) * m_rightChild.getSize());
					System.out.println(m_rightChild.getGainRatio().getBaseLabelDistribution().getBestLabel() + " " + numMajorityLabel + "/" + m_rightChild.getSize());
				}
				else {
					System.out.println();
					m_rightChild.print(prefix + "|    ");
				}
			}
		}
		
	}
	
	// Serialization
	// serialVersionUID is overriden to prevent innocuous changes in this
	// class from making the serialization mechanism think the external
	// format has changed.
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	private void writeObject(ObjectOutputStream out) throws IOException
	{
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(getInstancePipe());
		out.writeObject(m_root);
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt();
		if (version != CURRENT_SERIAL_VERSION)
			throw new ClassNotFoundException("Mismatched C45 versions: wanted " +
					CURRENT_SERIAL_VERSION + ", got " +
					version);
		instancePipe = (Pipe) in.readObject();
		m_root = (Node) in.readObject();
		
	}
	
}
