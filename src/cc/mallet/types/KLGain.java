/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 The "gain" obtained by adding a feature to an exponential model.
	 From Della Pietra, Della Pietra & Lafferty, 1997

	 What is the *right* way to smooth p[] and q[] so we don't get zeros,
	 (and therefore zeros in alpha[], and NaN in klgain[]?)
	 I think it would be to put the prior over parameters into G_q(\alpha,g).
	 Right now I'm simply doing a little m-estimate smoothing of p[] and q[].
	 

	 Note that we use Math.log(), not log-base-2, so the units are not "bits".

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.util.logging.*;

import cc.mallet.classify.Classification;
import cc.mallet.util.MalletLogger;

public class KLGain extends RankedFeatureVector
{
	private static Logger logger = MalletLogger.getLogger(KLGain.class.getName());
	// KLGain of a feature, f, is defined in terms of MaxEnt-type feature+class "Feature"s, F,
	//  F = f,c
	// KLGain of a Feature, F, is
	//  G(F) = KL(p~[C]||q[C]) - KL(p~[C]||q_F[C])
	// where p~[] is the empirical distribution, according to the true class label distribution
	// and   q[] is the distribution from the (imperfect) classifier
	// and   q_F[] is the distribution from the (imperfect) classifier with F added
	//       and F's weight adjusted (but none of the other weights adjusted)
	// KLGain of a feature,f, is
	//  G(f) = sum_c G(f,c)

	
	private static double[] calcKLGains (InstanceList ilist, LabelVector[] classifications)
	{
		int numInstances = ilist.size();
		int numClasses = ilist.getTargetAlphabet().size();
		int numFeatures = ilist.getDataAlphabet().size();
		assert (ilist.size() > 0);
		// Notation from  Della Pietra & Lafferty 1997, p.4
		// "p~"
		double[][] p = new double[numClasses][numFeatures];
		// "q"
		double[][] q = new double[numClasses][numFeatures];
		// "alpha", the weight of the new feature
		double[][] alphas = new double[numClasses][numFeatures];
		double flv;	// feature location value
		int fli; // feature location index
		logger.info ("Starting klgains, #instances="+numInstances);
		double trueLabelWeightSum = 0;
		double modelLabelWeightSum = 0;
		// Actually pretty lame smoothing based on ghost-counts
		final boolean doingSmoothing = true;

		double numInExpectation = doingSmoothing ? (numInstances+1.0) : (numInstances);
		// Attempt some add-hoc smoothing; remove the "+1.0" in the line above, if not doing smoothing
		if (doingSmoothing) {
			for (int i = 0; i < numClasses; i++)
				for (int j = 0; j < numFeatures; j++) {
					p[i][j] = q[i][j] = 1.0/(numInExpectation*numFeatures*numClasses);
					trueLabelWeightSum += p[i][j];
					modelLabelWeightSum += q[i][j];
				}
		}

		for (int i = 0; i < numInstances; i++) {
			assert (classifications[i].getLabelAlphabet() == ilist.getTargetAlphabet());
			Instance inst = ilist.get(i);
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData ();
			//double instanceWeight = ilist.getInstanceWeight(i);
			// The code below relies on labelWeights summing to 1 over all labels!
			for (int li = 0; li < numClasses; li++) {
				double trueLabelWeight = labeling.value (li) / numInExpectation;
				double modelLabelWeight = classifications[i].value(li) / numInExpectation;
				trueLabelWeightSum += trueLabelWeight;
				modelLabelWeightSum += modelLabelWeight;
				//if (i < 500) System.out.println ("i="+i+" li="+li+" true="+trueLabelWeight+" model="+modelLabelWeight);
				if (trueLabelWeight == 0 && modelLabelWeight == 0)
					continue;
				for (int fl = 0; fl < fv.numLocations(); fl++) {
					fli = fv.indexAtLocation(fl);
					assert (fv.valueAtLocation(fl) == 1.0);
					//p[li][fli] += trueLabelWeight * instanceWeight / (numInstances+1);
					//q[li][fli] += modelLabelWeight * instanceWeight / (numInstances+1);
					p[li][fli] += trueLabelWeight;
					q[li][fli] += modelLabelWeight;
				}
			}
		}
		assert (Math.abs (trueLabelWeightSum - 1.0) < 0.001)
			: "trueLabelWeightSum should be 1.0, it was "+trueLabelWeightSum;
		assert (Math.abs (modelLabelWeightSum - 1.0) < 0.001)
			: "modelLabelWeightSum should be 1.0, it was "+modelLabelWeightSum;

/*
		double psum = 0;
		double qsum = 0;
		for (int i = 0; i < numClasses; i++)
			for (int j = 0; j < numFeatures; j++) {
				psum += p[i][j];
				qsum += q[i][j];
			}
		assert (Math.abs(psum - 1.0) < 0.0001) : "psum not 1.0!  psum="+psum+" qsum="+qsum;
		assert (Math.abs(qsum - 1.0) < 0.0001) : "qsum not 1.0!  psum="+psum+" qsum="+qsum;
*/
		
		for (int i = 0; i < numClasses; i++)
			for (int j = 0; j < numFeatures; j++)
				alphas[i][j] = Math.log ( (p[i][j]*(1.0-q[i][j])) / (q[i][j]*(1.0-p[i][j])) );
		//q = null;
		
		// "q[e^{\alpha g}]", p.4
		//System.out.println ("Calculating qeag...");
		double[][] qeag = new double[numClasses][numFeatures];
		modelLabelWeightSum = 0;
		for (int i = 0; i < ilist.size(); i++) {
			assert (classifications[i].getLabelAlphabet() == ilist.getTargetAlphabet());
			Instance inst = ilist.get(i);
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData ();
			int fvMaxLocation = fv.numLocations()-1;
			for (int li = 0; li < numClasses; li++) {
				// q(\omega) = (classifications[i].value(li) / numInstances)
				double modelLabelWeight = classifications[i].value(li) / numInstances;
				modelLabelWeightSum += modelLabelWeight;
				// Following line now done before outside of loop over instances
				// for (int fi = 0; fi < numFeatures; fi++) qeag[li][fi] += modelLabelWeight; // * 1.0;
				for (int fl = 0; fl < fv.numLocations(); fl++) {
					fli = fv.indexAtLocation(fl);
					qeag[li][fli] += (modelLabelWeight * Math.exp (alphas[li][fli])) - modelLabelWeight;
				}
			}
		}
		for (int li = 0; li < numClasses; li++)
			for (int fi = 0; fi < numFeatures; fi++)
				// Assume that feature "fi" does not occur in "fv" and thus has value 0.
				// exp(alpha * 0) == 1.0
				// This factoring is possible because all features have value 1.0
				qeag[li][fi] += modelLabelWeightSum; // * 1.0;
		
		//System.out.println ("Calculating klgain values...");
		double[] klgains = new double[numFeatures];
		for (int i = 0; i < numClasses; i++)
			for (int j = 0; j < numFeatures; j++)
				if (alphas[i][j] > 0 && !Double.isInfinite(alphas[i][j]))
					klgains[j] += (alphas[i][j] * p[i][j]) - Math.log (qeag[i][j]);
					//klgains[j] += Math.abs(alphas[i][j] * p[i][j]);
					//klgains[j] += Math.abs(alphas[i][j]);

		if (true) {
			logger.info ("klgains.length="+klgains.length);
			for (int j = 0; j < numFeatures; j++) {
				if (j % (numFeatures/100) == 0) {
					for (int i = 0; i < numClasses; i++) {
						logger.info ("c="+i+" p["+ilist.getDataAlphabet().lookupObject(j)+"] = "+p[i][j]);
						logger.info ("c="+i+" q["+ilist.getDataAlphabet().lookupObject(j)+"] = "+q[i][j]);
						logger.info ("c="+i+" alphas["+ilist.getDataAlphabet().lookupObject(j)+"] = "+alphas[i][j]);
						logger.info ("c="+i+" qeag["+ilist.getDataAlphabet().lookupObject(j)+"] = "+qeag[i][j]);
					}
					logger.info ("klgains["+ilist.getDataAlphabet().lookupObject(j)+"] = "+klgains[j]);
				} 
			}
		}
		
		return klgains;
	}

	public KLGain (InstanceList ilist, LabelVector[] classifications)
	{
		super (ilist.getDataAlphabet(), calcKLGains (ilist, classifications));
	}

	private static LabelVector[] getLabelVectorsFromClassifications (Classification[] c)
	{
		LabelVector[] ret = new LabelVector[c.length];
		for (int i = 0; i < c.length; i++)
			ret[i] = c[i].getLabelVector();
		return ret;
	}

	public KLGain (InstanceList ilist, Classification[] classifications)
	{
		super (ilist.getDataAlphabet(),
					 calcKLGains (ilist, getLabelVectorsFromClassifications(classifications)));
	}
	
}
