/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
 This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 http://www.cs.umass.edu/~mccallum/mallet
 This software is provided under the terms of the Common Public License,
 version 1.0, as published by http://www.opensource.org.  For further
 information, see the file `LICENSE' included with this distribution. */


package cc.mallet.types;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Logger;

import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;



/**
 * List of features along with their thresholds sorted in descending order of 
 * the ratio of (1) information gained by splitting instances on the 
 * feature at its associated threshold value, to (2) the split information.<p>
 *
 * The calculations performed do not take into consideration the instance weights.<p>
 *
 * To create an instance of GainRatio from an InstanceList, one must do the following:<p><tt>
 *
 * InstanceList ilist = ... 
 *         ...
 * GainRatio gr = GainRatio.createGainRatio(ilist);
 * </tt><p>
 *
 * J. R. Quinlan
 * "Improved Use of Continuous Attributes in C4.5" 
 * ftp://ftp.cs.cmu.edu/project/jair/volume4/quinlan96a.ps
 *
 * @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class GainRatio extends RankedFeatureVector
{
	private static final Logger logger = MalletLogger.getLogger (GainRatio.class.getName ());
	
	private static final long serialVersionUID = 1L;
	
	public static final double log2 = Math.log(2);
	
	double[] m_splitPoints;
	double m_baseEntropy;
	LabelVector m_baseLabelDistribution;
	int m_numSplitPointsForBestFeature;
	int m_minNumInsts;
	
	/**
	 * Calculates gain ratios for all (feature, split point) pairs 
	 * snd returns array of:<pre>
	 *   1.  gain ratios (each element is the max gain ratio of a feature 
	 * for those split points with at least average gain)
	 *   2.  the optimal split point for each feature
	 *   3.  the overall entropy 
	 *   4.  the overall label distribution of the given instances
	 *   5.  the number of split points of the split feature.
	 *   </pre>
	 */
	protected static Object[] calcGainRatios(InstanceList ilist, int[] instIndices, int minNumInsts)
	{
		int numInsts = instIndices.length;
		Alphabet dataDict = ilist.getDataAlphabet();
		LabelAlphabet targetDict = (LabelAlphabet) ilist.getTargetAlphabet();
		double[] targetCounts = new double[targetDict.size()];

		// Accumulate target label counts and make sure
		// the sum of each instance's target label is 1
		for (int ii = 0; ii < numInsts; ii++) {
			Instance inst = ilist.get(instIndices[ii]);
			Labeling labeling = inst.getLabeling();
			double labelWeightSum = 0;
			for (int ll = 0; ll < labeling.numLocations(); ll++) {
				int li = labeling.indexAtLocation(ll);
				double labelWeight = labeling.valueAtLocation(ll);
				labelWeightSum += labelWeight;
				targetCounts[li] += labelWeight;
			}
			assert(Maths.almostEquals(labelWeightSum, 1));
		}

		// Calculate the base entropy Info(D) and the the 
		// label distribution of the given instances
		double[] targetDistribution = new double[targetDict.size()];
		double baseEntropy = 0;
		for (int ci = 0; ci < targetDict.size(); ci++) {
			double p = targetCounts[ci] / numInsts;
			targetDistribution[ci] = p;
			if (p > 0)
				baseEntropy -= p * Math.log(p) / log2;
		}

		LabelVector baseLabelDistribution = new LabelVector(targetDict, targetDistribution);
		double infoGainSum = 0;
		int totalNumSplitPoints = 0;
		double[] passTestTargetCounts = new double[targetDict.size()];
		// Maps feature index -> Hashtable, and each table 
		// maps (split point) -> (info gain, split ratio)
		Hashtable[] featureToInfo = new Hashtable[dataDict.size()]; 
		
		// Go through each feature's split points in ascending order
		for (int fi = 0; fi < dataDict.size(); fi++) {
			
			if ((fi+1) % 1000 == 0)
				logger.info("at feature " + (fi+1) + " / " + dataDict.size());
			
			featureToInfo[fi] = new Hashtable();
			Arrays.fill(passTestTargetCounts, 0);
			// Sort instances on this feature's values
			instIndices = sortInstances(ilist, instIndices, fi);

			// Iterate through the sorted instances
			for (int ii = 0; ii < numInsts-1; ii++) {
				Instance inst = ilist.get(instIndices[ii]);
				Instance instPlusOne = ilist.get(instIndices[ii+1]);
				FeatureVector fv1 = (FeatureVector) inst.getData();
				FeatureVector fv2 = (FeatureVector) instPlusOne.getData();
				double lower = fv1.value(fi);
				double higher = fv2.value(fi);

				// Accumulate the label weights for instances passing the test
				Labeling labeling = inst.getLabeling();
				for (int ll = 0; ll < labeling.numLocations(); ll++) {
					int li = labeling.indexAtLocation(ll);
					double labelWeight = labeling.valueAtLocation(ll);
					passTestTargetCounts[li] += labelWeight;
				}

				if (Maths.almostEquals(lower, higher) 
						|| 
						inst.getLabeling().toString().equals(instPlusOne.getLabeling().toString()))
					continue;

				// For this (feature, spilt point) pair, calculate the 
				// info gain of using this pair to split insts into those 
				// with value of feature <= p versus > p
				totalNumSplitPoints++;
				double splitPoint = (lower + higher) / 2;
				double numPassInsts = ii+1;
				
				// If this split point creates a partition 
				// with too few instances, ignore it
				double numFailInsts = numInsts - numPassInsts;
				if (numPassInsts < minNumInsts || numFailInsts < minNumInsts)
					continue;
				
				// If all instances pass or fail this test, it is useless
				double passProportion = numPassInsts / numInsts;
				if (Maths.almostEquals(passProportion, 0) || Maths.almostEquals(passProportion, 1))
					continue;	  
				
				// Calculate the entropy of instances passing and failing the test
				double passEntropy = 0;
				double failEntropy = 0;
				double p;
				
				for (int ci = 0; ci < targetDict.size(); ci++) {
					if (numPassInsts > 0) {
						p = passTestTargetCounts[ci] / numPassInsts;
						if (p > 0)
							passEntropy -= p * Math.log(p) / log2;
					}
					if (numFailInsts > 0) {
						double failTestTargetCount = targetCounts[ci] - passTestTargetCounts[ci];
						p = failTestTargetCount / numFailInsts;
						if (p  > 0)
							failEntropy -= p * Math.log(p) / log2;
					}
				}
				
				// Calculate Gain(D, T), the information gained 
				// by testing on this (feature, split-point) pair
				double gainDT = baseEntropy 
				- passProportion * passEntropy
				- (1-passProportion) * failEntropy;	      
				infoGainSum += gainDT;
				// Calculate Split(D, T), the split information
				double splitDT = 
					- passProportion * Math.log(passProportion) / log2
					- (1-passProportion) * Math.log(1-passProportion) / log2;
				// Calculate the gain ratio
				double gainRatio = gainDT / splitDT;
				featureToInfo[fi].put(new Double(splitPoint),
						new Point2D.Double(gainDT, gainRatio));
			}  // End loop through sorted instances
		}  // End loop through features

		// For each feature's split point with at least average gain, 
		// get the maximum gain ratio and the associated split point
		// (using the info gain as tie breaker)
		double[] gainRatios = new double[dataDict.size()];
		double[] splitPoints = new double[dataDict.size()];
		int numSplitsForBestFeature = 0;

		// If all feature vectors are identical or no splits are worthy, return all 0s
		if (totalNumSplitPoints == 0 || Maths.almostEquals(infoGainSum, 0))
			return new Object[] {gainRatios, splitPoints, new Double(baseEntropy), 
				baseLabelDistribution, new Integer(numSplitsForBestFeature)};

		double avgInfoGain = infoGainSum / totalNumSplitPoints;
		double maxGainRatio = 0;
		double gainForMaxGainRatio = 0; // tie breaker
		
		int xxx = 0;
		
		
		for (int fi = 0; fi < dataDict.size(); fi++) {
			double featureMaxGainRatio = 0;
			double featureGainForMaxGainRatio = 0;
			double bestSplitPoint = Double.NaN;

			for (Iterator iter = featureToInfo[fi].keySet().iterator(); iter.hasNext(); ) {
				Object key = iter.next();
				Point2D.Double pt = (Point2D.Double) featureToInfo[fi].get(key);
				double splitPoint = ((Double) key).doubleValue();
				double infoGain = pt.getX();
				double gainRatio = pt.getY();

				if (infoGain >= avgInfoGain) {
					if (gainRatio > featureMaxGainRatio
							||
							(gainRatio == featureMaxGainRatio && infoGain > featureGainForMaxGainRatio)) {
						featureMaxGainRatio = gainRatio;
						featureGainForMaxGainRatio = infoGain;
						bestSplitPoint = splitPoint;
					}
				}
				
				
				else 
					xxx++;
				
				
			}
			assert(bestSplitPoint != Double.NaN);
			gainRatios[fi] = featureMaxGainRatio;
			splitPoints[fi] = bestSplitPoint;

			if (featureMaxGainRatio > maxGainRatio
					||
					(featureMaxGainRatio == maxGainRatio && featureGainForMaxGainRatio > gainForMaxGainRatio)) {
				maxGainRatio = featureMaxGainRatio;
				gainForMaxGainRatio = featureGainForMaxGainRatio;
				numSplitsForBestFeature = featureToInfo[fi].size();
			}
		}
		
		logger.info("label distrib:\n" + baseLabelDistribution);
		logger.info("base entropy=" + baseEntropy + ", info gain sum=" + infoGainSum + ", total num split points=" + totalNumSplitPoints + ", avg info gain=" + avgInfoGain + ", num splits with < avg gain=" + xxx);
		
		return new Object[] {gainRatios, splitPoints, new Double(baseEntropy), 
				baseLabelDistribution, new Integer(numSplitsForBestFeature)};
	}
	
	public static int[] sortInstances(InstanceList ilist, int[] instIndices, int featureIndex)
	{
		ArrayList list = new ArrayList();
		for (int ii = 0; ii < instIndices.length; ii++) {
			Instance inst = ilist.get(instIndices[ii]);
			FeatureVector fv = (FeatureVector) inst.getData();
			list.add(new Point2D.Double(instIndices[ii], fv.value(featureIndex)));
		}
		Collections.sort(list, new Comparator() 
				{
			public int compare(Object o1, Object o2)
			{
				Point2D.Double p1 = (Point2D.Double) o1;
				Point2D.Double p2 = (Point2D.Double) o2;
				if (p1.y == p2.y) {
					assert(p1.x != p2.x);
					return p1.x > p2.x ? 1 : -1;
				}
				else 
					return p1.y > p2.y ? 1 : -1;		    
			}
				});
		int[] sorted = new int[instIndices.length];
		for (int i = 0; i < list.size(); i++)
			sorted[i] = (int) ((Point2D.Double) list.get(i)).getX();
		return sorted;
	}
	
	/**
	 * Constructs a GainRatio object.
	 */
	public static GainRatio createGainRatio(InstanceList ilist)
	{
		int[] instIndices = new int[ilist.size()];
		for (int ii = 0; ii < instIndices.length; ii++)
			instIndices[ii] = ii;
		return createGainRatio(ilist, instIndices, 2);
	}
	
	/**
	 * Constructs a GainRatio object
	 */
	public static GainRatio createGainRatio(InstanceList ilist, int[] instIndices, 
			int minNumInsts)
	{
		Object[] objs = calcGainRatios(ilist, instIndices, minNumInsts);
		double[] gainRatios = (double[]) objs[0];
		double[] splitPoints = (double[]) objs[1];
		double baseEntropy = ((Double) objs[2]).doubleValue();
		LabelVector baseLabelDistribution = (LabelVector) objs[3];
		int numSplitPointsForBestFeature = ((Integer) objs[4]).intValue();
		return new GainRatio(ilist.getDataAlphabet(), gainRatios, splitPoints, 
				baseEntropy, baseLabelDistribution, 
				numSplitPointsForBestFeature, minNumInsts);
	}
	
	protected GainRatio(Alphabet dataAlphabet, double[] gainRatios, double[] splitPoints, 
			double baseEntropy, LabelVector baseLabelDistribution,
			int numSplitPointsForBestFeature, int minNumInsts)
	{
		super (dataAlphabet, gainRatios);
		m_splitPoints = splitPoints;
		m_baseEntropy = baseEntropy;
		m_baseLabelDistribution = baseLabelDistribution;
		m_numSplitPointsForBestFeature = numSplitPointsForBestFeature;
		m_minNumInsts = minNumInsts;
	}
	
	/**
	 * @return the threshold of the (feature, threshold) 
	 * pair with with maximum gain ratio
	 */
	public double getMaxValuedThreshold()
	{
		return getThresholdAtRank(0);
	}
	
	/**
	 * @return the threshold of the (feature, threshold)
	 * pair with the given rank
	 */
	public double getThresholdAtRank(int rank)
	{
		int index = getIndexAtRank(rank);
		return m_splitPoints[index];
	}
	
	public double getBaseEntropy ()
	{
		return m_baseEntropy;
	}
	
	public LabelVector getBaseLabelDistribution ()
	{
		return m_baseLabelDistribution;
	}
	
	public int getNumSplitPointsForBestFeature()
	{
		return m_numSplitPointsForBestFeature;
	}
	
}
