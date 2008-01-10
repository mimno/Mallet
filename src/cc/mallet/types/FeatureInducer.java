/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.util.logging.*;
import java.util.BitSet;
import java.io.*;

import cc.mallet.util.MalletLogger;

/* Where will the new features get extracted in the Pipe? */

public class FeatureInducer implements Serializable
{
	private static Logger logger = MalletLogger.getLogger(FeatureInducer.class.getName());
	static boolean addMaskedFeatures = false;
	static int minTrainingListSize = 20;

	// Only one of the following two will be non-null
	RankedFeatureVector.Factory ranker;
	RankedFeatureVector.PerLabelFactory perLabelRanker;
	int beam1 = 300;
	int beam2 = 1000;
	
	FeatureConjunction.List fcl;

	// xxx Could perhaps build a hash value for each feature that measures its distribution
	// over instances, and avoid conjunctions of features that are *exact* duplicates
	// with this hash value.
	
	public FeatureInducer (RankedFeatureVector.Factory ranker,
												 InstanceList ilist,
												 int numNewFeatures, int beam1, int beam2)
	{
		this.fcl = new FeatureConjunction.List ();
		this.beam1 = beam1;
		this.beam2 = beam2;
		if (ilist.size() < minTrainingListSize) {
			logger.info ("FeatureInducer not inducing from less than "+minTrainingListSize+" features.");
			return;
		}
		Alphabet tmpDV = (Alphabet) ilist.getDataAlphabet().clone();
		FeatureSelection featuresSelected = ilist.getFeatureSelection();
		InstanceList tmpilist = new InstanceList (tmpDV, ilist.getTargetAlphabet());
		RankedFeatureVector gg = ranker.newRankedFeatureVector (ilist);
		logger.info ("Rank values before this round of conjunction-building");
		int n = Math.min (200, gg.numLocations());
		for (int i = 0; i < n; i++)
			logger.info ("Rank="+i+' '+Double.toString(gg.getValueAtRank(i)) + ' ' + gg.getObjectAtRank(i).toString());
		//for (int i = gg.numLocations()-200; i < gg.numLocations(); i++)
		//System.out.println ("i="+i+' '+Double.toString(gg.getValueAtRank(i)) + ' ' + gg.getObjectAtRank(i).toString());
		//System.out.println ("");
		FeatureSelection fsMin = new FeatureSelection (tmpDV);
		FeatureSelection fsMax = new FeatureSelection (tmpDV);
		int minBeam = Math.min (beam1, beam2);
		int maxBeam = Math.max (beam1, beam2);
		logger.info ("Using minBeam="+minBeam+" maxBeam="+maxBeam);
		int max = maxBeam < gg.numLocations() ? maxBeam : gg.numLocations();
		for (int b = 0; b < max; b++) {
			if (gg.getValueAtRank(b) == 0)
				break;
			int index = gg.getIndexAtRank(b);
			fsMax.add (index);
			if (b < minBeam)
				fsMin.add (index);
		}
		// Prevent it from searching through all of gg2
		//double minGain = gg.getValueAtRank(maxBeam*2);
		// No, there are so many "duplicate" features, that it ends up only adding a few each round.
		//double minGain = Double.NEGATIVE_INFINITY;
		// Just use a constant; anything less than this must not have enough support in the data.
		//double minGain = 5;
		double minGain = 0;

		//// xxx Temporarily remove all feature conjunction pruning
		//System.out.println ("FeatureInducer: Temporarily not pruning any feature conjunctions from consideration.");
		//fsMin = fsMax = null; minGain = Double.NEGATIVE_INFINITY;
		
		//int[] conjunctions = new int[beam];
		//for (int b = 0; b < beam; b++)
		//conjunctions[b] = gg.getIndexAtRank(b);
		gg = null;													// Allow memory to be freed
		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			FeatureVector fv = (FeatureVector) inst.getData ();
			tmpilist.add (new Instance (new FeatureVector (fv, tmpDV, fsMin, fsMax),
																	inst.getTarget(), inst.getName(), inst.getSource()),
										ilist.getInstanceWeight(i));
		}
		logger.info ("Calculating gradient gain of conjunctions, vocab size = "+tmpDV.size());
		RankedFeatureVector gg2 = ranker.newRankedFeatureVector (tmpilist);
		for (int i = 0; i < 200 && i < gg2.numLocations(); i++)
			logger.info ("Conjunction Rank="+i+' '+Double.toString(gg2.getValueAtRank(i))
									 + ' ' + gg2.getObjectAtRank(i).toString());

		int numFeaturesAdded = 0;
		Alphabet origV = ilist.getDataAlphabet();
		int origVSize = origV.size();
		nextfeatures:
		for (int i = 0; i < gg2.numLocations(); i++) {
			double gain = gg2.getValueAtRank (i);
			if (gain < minGain) {
				// There are no more new features we could add, because they all have no more gain
				// than the features we started with
				logger.info ("Stopping feature induction: gain["+i+"]="+gain+", minGain="+minGain);
				break;
			}
			if (gg2.getIndexAtRank(i) >= origVSize) {
				// First disjunct above so that we also add singleton features that are currently masked out
				// xxx If addMaskedFeatures == true, we should still check the mask, so we don't
				// "add" and print features that are already unmasked
				String s = (String) gg2.getObjectAtRank(i);
				int[] featureIndices = FeatureConjunction.getFeatureIndices(origV, s);
				// Make sure that the new conjunction doesn't contain duplicate features
				if (FeatureConjunction.isValidConjunction (featureIndices)
						// Don't add features with exactly the same gain value: they are probably an
						// "exactly overlapping duplicate"
						// xxx Note that this might actually increase over-fitting!
						&& (i == 0 || gg2.getValueAtRank(i-1) != gg2.getValueAtRank(i))
					) {
					double newFeatureValue = gg2.getValueAtRank(i);
					// Don't add new conjunctions that have no more gain than any of their constituents
					for (int j = 0; j < featureIndices.length; j++)
						if (gg2.value (featureIndices[j]) >= newFeatureValue) {
							//System.out.println ("Skipping feature that adds no gain "+newFeatureValue+' '+s);
							continue nextfeatures;
						}
					fcl.add (new FeatureConjunction (origV, featureIndices));
					int index = origV.size()-1;
					// If we have a feature mask, be sure to include this new feature
					logger.info ("Added feature c "+numFeaturesAdded+" "+newFeatureValue+ ' ' + s);
					// xxx Also print the gradient here, if the feature already exists.
					numFeaturesAdded++;
				}
			} else if (featuresSelected != null) {
				int index = gg2.getIndexAtRank (i);
				//System.out.println ("Atomic feature rank "+i+" at index "+index);
				if (!featuresSelected.contains (index)
						// A new atomic feature added to the FeatureSelection
						// Don't add features with exactly the same gain value: they are probably an
						// "exactly overlapping duplicate"
						// xxx Note that this might actually increase over-fitting!
						&& (i == 0 || gg2.getValueAtRank(i-1) != gg2.getValueAtRank(i))) {
					fcl.add (new FeatureConjunction (origV, new int[] {index}));
					logger.info ("Added feature a "+numFeaturesAdded+" "+gg2.getValueAtRank(i)+ ' ' + gg2.getObjectAtRank(i));
					numFeaturesAdded++;
				}
			}
			if (numFeaturesAdded >= numNewFeatures) {
				logger.info ("Stopping feature induction: numFeaturesAdded="+numFeaturesAdded);
				break;
			}
		}
		logger.info ("Finished adding features");
	}

	public FeatureInducer (RankedFeatureVector.Factory ranker,
												 InstanceList ilist,
												 int numNewFeatures)
	{
		//this (ilist, classifications, numNewFeatures, 200, numNewFeatures);
		//this (ilist, classifications, numNewFeatures, 200, 500);
		this (ranker, ilist, numNewFeatures, numNewFeatures, numNewFeatures);
	}

	// This must be run on test instance lists before they can be transduced, because we have to add the right
	// feature combinations!
	public void induceFeaturesFor (InstanceList ilist,
																 boolean withFeatureShrinkage, boolean addPerClassFeatures)
	{
		assert (addPerClassFeatures == false);
		assert (withFeatureShrinkage == false);
		FeatureSelection fs = ilist.getFeatureSelection ();
		assert (ilist.getPerLabelFeatureSelection() == null);
		if (fcl.size() == 0)
			return;
		for (int i = 0; i < ilist.size(); i++) {
			//System.out.println ("Induced features for instance #"+i);
			Instance inst = ilist.get(i);
			Object data = inst.getData ();
			if (data instanceof AugmentableFeatureVector) {
				AugmentableFeatureVector afv = (AugmentableFeatureVector) data;
				fcl.addTo (afv, 1.0, fs);
			} else if (data instanceof FeatureVectorSequence) {
				FeatureVectorSequence fvs = (FeatureVectorSequence) data;
				for (int j = 0; j < fvs.size(); j++)
					fcl.addTo ((AugmentableFeatureVector) fvs.get(j), 1.0, fs);
			} else {
				throw new IllegalArgumentException ("Unsupported instance data type "+data.getClass().getName());
			}
		}		
	}

	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;


	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeInt(beam1);
		out.writeInt(beam2);
		out.writeObject(fcl);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		beam1 = in.readInt();
		beam2 = in.readInt();
		fcl = (FeatureConjunction.List)in.readObject();
	}

}
