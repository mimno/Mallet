/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 The difference between constraint and expectation for each feature on the correct class.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.io.*;

import cc.mallet.classify.Classification;

public class GradientGain extends RankedFeatureVector
{
	// GradientGain of a feature, f, is defined in terms of MaxEnt-type feature+class "Feature"s, F,
	//  F = f,c
	// GraidentGain of a Feature, F, is
	//  G(F) = G(f,c) = abs(E~[F] - E.[F]
	// where E~[] is the empirical distribution, according to the true class label distribution
	// and   E.[] is the distribution from the (imperfect) classifier
	// GradientGain of a feature,f, is
	//  G(f) = sum_c G(f,c)
	
	private static double[] calcGradientGains (InstanceList ilist, LabelVector[] classifications)
	{
		int numInstances = ilist.size();
		int numClasses = ilist.getTargetAlphabet().size();
		int numFeatures = ilist.getDataAlphabet().size();
		double[] gradientgains = new double[numFeatures];
		double flv;	// feature location value
		int fli; // feature location index
		// Populate targetFeatureCount, et al
		for (int i = 0; i < ilist.size(); i++) {
			assert (classifications[i].getLabelAlphabet() == ilist.getTargetAlphabet());
			Instance inst = ilist.get(i);
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData ();
			double instanceWeight = ilist.getInstanceWeight(i);
			// The code below relies on labelWeights summing to 1 over all labels!
			double labelWeightSum = 0;
			for (int ll = 0; ll < labeling.numLocations(); ll++) {
				int li = labeling.indexAtLocation (ll);
				double labelWeight = labeling.value (li);
				labelWeightSum += labelWeight;
				double labelWeightDiff = Math.abs(labelWeight - classifications[i].value(li));
				for (int fl = 0; fl < fv.numLocations(); fl++) {
					fli = fv.indexAtLocation(fl);
					gradientgains[fli] += fv.valueAtLocation(fl) * labelWeightDiff * instanceWeight;
				}
			}
			assert (Math.abs (labelWeightSum - 1.0) < 0.0001);
		}
		return gradientgains;
	}

	public GradientGain (InstanceList ilist, LabelVector[] classifications)
	{
		super (ilist.getDataAlphabet(), calcGradientGains (ilist, classifications));
	}

	private static LabelVector[] getLabelVectorsFromClassifications (Classification[] c)
	{
		LabelVector[] ret = new LabelVector[c.length];
		for (int i = 0; i < c.length; i++)
			ret[i] = c[i].getLabelVector();
		return ret;
	}

	public GradientGain (InstanceList ilist, Classification[] classifications)
	{
		super (ilist.getDataAlphabet(),
					 calcGradientGains (ilist, getLabelVectorsFromClassifications(classifications)));
	}


	public static class Factory implements RankedFeatureVector.Factory
	{
		LabelVector[] classifications;
		
		public Factory (LabelVector[] classifications)
		{
			this.classifications = classifications;
		}

		public RankedFeatureVector newRankedFeatureVector (InstanceList ilist)
		{
			return new GradientGain (ilist, classifications);
		}

		// Serialization
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeInt(classifications.length);
			for (int i = 0; i < classifications.length; i++)
				out.writeObject(classifications[i]);
		}
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			int n = in.readInt();
			this.classifications = new LabelVector[n];
			for (int i = 0; i < n; i++)
				this.classifications[i] = (LabelVector)in.readObject();
		}
		

	}
	
}
