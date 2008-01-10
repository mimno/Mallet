/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 The "gain" obtained by adding a feature to a conditional exponential model.
	 Based on the joint exponential model in Della Pietra, Della Pietra & Lafferty, 1997.

	 We smooth using a Gaussian prior.
	 Note that we use Math.log(), not log-base-2, so the units are not "bits".

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.util.logging.*;
import java.io.*;

import cc.mallet.classify.Classification;
import cc.mallet.util.MalletLogger;

public class ExpGain extends RankedFeatureVector
{
	private static Logger logger = MalletLogger.getLogger(ExpGain.class.getName());
	// ExpGain of a feature, f, is defined in terms of MaxEnt-type feature+class "Feature"s, F,
	//  F = f,c
	// ExpGain of a Feature, F, is
	//  G(F) = KL(p~[C]||q[C]) - KL(p~[C]||q_F[C])
	// where p~[] is the empirical distribution, according to the true class label distribution
	// and   q[] is the distribution from the (imperfect) classifier
	// and   q_F[] is the distribution from the (imperfect) classifier with F added
	//       and F's weight adjusted (but none of the other weights adjusted)
	// ExpGain of a feature,f, is
	//  G(f) = sum_c G(f,c)

	// It would be more accurate to return a gain number for each "feature/class" combination,
	// but here we simply return the gain(feature) = \sum_{class} gain(feature,class).

	// xxx Not ever used.  Remove them.
	boolean usingHyperbolicPrior = false;
	double hyperbolicSlope = 0.2;
	double hyperbolicSharpness = 10.0;

	private static double[] calcExpGains (InstanceList ilist, LabelVector[] classifications,
																				double gaussianPriorVariance)
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
		int fli; // feature location index
		double flv;	// feature location value
		logger.info ("Starting klgains, #instances="+numInstances);
		double trueLabelWeightSum = 0;
		double modelLabelWeightSum = 0;

		// Calculate p~[f] and q[f]
		for (int i = 0; i < numInstances; i++) {
			assert (classifications[i].getLabelAlphabet() == ilist.getTargetAlphabet());
			Instance inst = ilist.get(i);
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData ();
			//double instanceWeight = ilist.getInstanceWeight(i);
			// The code below relies on labelWeights summing to 1 over all labels!
			double perInstanceModelLabelWeight = 0;
			for (int li = 0; li < numClasses; li++) {
				double trueLabelWeight = labeling.value (li);
				double modelLabelWeight = classifications[i].value(li);
				trueLabelWeightSum += trueLabelWeight;
				modelLabelWeightSum += modelLabelWeight;
				perInstanceModelLabelWeight += modelLabelWeight;
				//if (i < 500) System.out.println ("i="+i+" li="+li+" true="+trueLabelWeight+" model="+modelLabelWeight);
				if (trueLabelWeight == 0 && modelLabelWeight == 0)
					continue;
				for (int fl = 0; fl < fv.numLocations(); fl++) {
					fli = fv.indexAtLocation(fl);
					assert (fv.valueAtLocation(fl) == 1.0);
					// xxx Note that we are not attenting to instanceWeight here!
					//p[li][fli] += trueLabelWeight * instanceWeight / (numInstances+1);
					//q[li][fli] += modelLabelWeight * instanceWeight / (numInstances+1);
					p[li][fli] += trueLabelWeight;
					q[li][fli] += modelLabelWeight;
				}
			}
			assert (Math.abs (perInstanceModelLabelWeight - 1.0) < 0.001);
		}
		assert (Math.abs (trueLabelWeightSum/numInstances - 1.0) < 0.001)
			: "trueLabelWeightSum should be 1.0, it was "+trueLabelWeightSum;
		assert (Math.abs (modelLabelWeightSum/numInstances - 1.0) < 0.001)
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

		// Determine the alphas
		// We can't do it in closed form as in the Della Pietra paper, because this we have here
		// a conditional MaxEnt model.
		// So we do it by Newton-Raphson...

		// ...initializing by the broken, inappropriate joint-case closed-form solution:
		//for (int i = 0; i < numClasses; i++)
		//for (int j = 0; j < numFeatures; j++)
		//alphas[i][j] = Math.log ( (p[i][j]*(1.0-q[i][j])) / (q[i][j]*(1.0-p[i][j])) );
		
		double[][] dalphas = new double[numClasses][numFeatures];	// first derivative
		double[][] alphaChangeOld = new double[numClasses][numFeatures];	// change in alpha, last iteration
		double[][] alphaMax = new double[numClasses][numFeatures];	// change in alpha, last iteration
		double[][] alphaMin = new double[numClasses][numFeatures];	// change in alpha, last iteration
		double[][] ddalphas = new double[numClasses][numFeatures];// second derivative
		for (int i = 0; i < numClasses; i++)
			for (int j = 0; j < numFeatures; j++) {
				alphaMax[i][j] = Double.POSITIVE_INFINITY;
				alphaMin[i][j] = Double.NEGATIVE_INFINITY;
			}
		double maxAlphachange = 0;
		double maxDalpha = 99;
		int maxNewtonSteps = 50;							// xxx Change to more?
		// alphas[][] are initialized to zero
		for (int newton = 0; maxDalpha > 1.0E-8 && newton < maxNewtonSteps; newton++) {
			//System.out.println ("Newton iteration "+newton);
			if (false /*usingHyperbolicPrior*/) {
				for (int i = 0; i < numClasses; i++)
					for (int j = 0; j < numFeatures; j++) {
						dalphas[i][j] = p[i][j] - (alphas[i][j] / gaussianPriorVariance);
						ddalphas[i][j] = -1 / gaussianPriorVariance;
					}
			} else {
				// Gaussian prior
				for (int i = 0; i < numClasses; i++)
					for (int j = 0; j < numFeatures; j++) {
						dalphas[i][j] = p[i][j] - (alphas[i][j] / gaussianPriorVariance);
						ddalphas[i][j] = -1 / gaussianPriorVariance;
					}
			}
			for (int i = 0; i < ilist.size(); i++) {
				assert (classifications[i].getLabelAlphabet() == ilist.getTargetAlphabet());
				Instance inst = ilist.get(i);
				Labeling labeling = inst.getLabeling ();
				FeatureVector fv = (FeatureVector) inst.getData ();
				// xxx This assumes binary-valued features.  What about "tied" weights?
				for (int fl = 0; fl < fv.numLocations(); fl++) {
					fli = fv.indexAtLocation(fl);
 					for (int li = 0; li < numClasses; li++) {
						double modelLabelWeight = classifications[i].value(li);
						double expalpha = Math.exp (alphas[li][fli]);
						double numerator = modelLabelWeight * expalpha;
						double denominator = numerator + (1.0 - modelLabelWeight);
						dalphas[li][fli] -= numerator / denominator;
						ddalphas[li][fli] += ((numerator*numerator) / (denominator*denominator)
																	- (numerator/denominator));
					}
				}
			}
			// We now now first- and second-derivative for this newton step
			// Run tests on the alphas and their derivatives, and do a newton step
			double alphachange, newalpha, oldalpha;
			maxAlphachange = maxDalpha = 0;
			for (int i = 0; i < numClasses; i++)
				for (int j = 0; j < numFeatures; j++) {
					alphachange = - (dalphas[i][j] / ddalphas[i][j]);
					if (p[i][j] == 0 && q[i][j] == 0)
						continue;
					else if (false && (i*numFeatures+j) % (numClasses*numFeatures/2000) == 0
									 || Double.isNaN(alphas[i][j]) || Double.isNaN(alphachange))
						// Print just a sampling of them...
						logger.info ("alpha["+i+"]["+j+"]="+alphas[i][j]+
												 " p="+p[i][j]+
												 " q="+q[i][j]+
												 " dalpha="+dalphas[i][j]+
												 " ddalpha="+ddalphas[i][j]+
												 " alphachange="+alphachange+
												 " min="+alphaMin[i][j]+
												 " max="+alphaMax[i][j]);
					if (Double.isNaN(alphas[i][j]) || Double.isNaN(dalphas[i][j]) || Double.isNaN(ddalphas[i][j])
							|| Double.isInfinite(alphas[i][j]) || Double.isInfinite(dalphas[i][j]) || Double.isInfinite(ddalphas[i][j])) 
						alphachange = 0;
//					assert (!Double.isNaN(alphas[i][j]));
//					assert (!Double.isNaN(dalphas[i][j]));
//					assert (!Double.isNaN(ddalphas[i][j]));
					oldalpha = alphas[i][j];
					// xxx assert (ddalphas[i][j] <= 0);
					//assert (Math.abs(alphachange) < 100.0) : alphachange; // xxx arbitrary?
					// Trying to prevent a cycle
					if (Math.abs(alphachange + alphaChangeOld[i][j]) / Math.abs(alphachange) < 0.01)
						newalpha = alphas[i][j] + alphachange / 2;
					else
						newalpha = alphas[i][j] + alphachange;
					if (alphachange < 0 && alphaMax[i][j] > alphas[i][j]) {
						//System.out.println ("Updating alphaMax["+i+"]["+j+"] = "+alphas[i][j]);
						alphaMax[i][j] = alphas[i][j];
					}
					if (alphachange > 0 && alphaMin[i][j] < alphas[i][j]) {
						//System.out.println ("Updating alphaMin["+i+"]["+j+"] = "+alphas[i][j]);
						alphaMin[i][j] = alphas[i][j];
					}
					if (newalpha <= alphaMax[i][j] && newalpha >= alphaMin[i][j])
						// Newton wants to jump to a point inside the boundaries; let it
						alphas[i][j] = newalpha;
					else {
						// Newton wants to jump to a point outside the boundaries; bisect instead
						assert (alphaMax[i][j] != Double.POSITIVE_INFINITY);
						assert (alphaMin[i][j] != Double.NEGATIVE_INFINITY);
						alphas[i][j] = alphaMin[i][j] + (alphaMax[i][j] - alphaMin[i][j]) / 2;
						//System.out.println ("Newton tried to exceed bounds; bisecting. dalphas["+i+"]["+j+"]="+dalphas[i][j]+" alphaMin="+alphaMin[i][j]+" alphaMax="+alphaMax[i][j]);
					}
					alphachange = alphas[i][j] - oldalpha;
					if (Math.abs(alphachange) > maxAlphachange)
						maxAlphachange = Math.abs (alphachange);
					if (Math.abs (dalphas[i][j]) > maxDalpha)
						maxDalpha = Math.abs (dalphas[i][j]);
					alphaChangeOld[i][j] = alphachange;
				}
			logger.info ("After "+newton+" Newton iterations, maximum alphachange="+maxAlphachange+
													" dalpha="+maxDalpha);
		}
					
		// Allow some memory to be freed
		//q = null;
		ddalphas = dalphas = alphaChangeOld = alphaMin = alphaMax = null;
		
		// "q[e^{\alpha g}]", p.4
		//System.out.println ("Calculating qeag...");
		// Note that we are using a gaussian prior, so we don't multiply by (1/numInstances)
		double[][] qeag = new double[numClasses][numFeatures];
		for (int i = 0; i < ilist.size(); i++) {
			assert (classifications[i].getLabelAlphabet() == ilist.getTargetAlphabet());
			Instance inst = ilist.get(i);
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData ();
			int fvMaxLocation = fv.numLocations()-1;
			for (int li = 0; li < numClasses; li++) {
				double modelLabelWeight = classifications[i].value(li);
				// Following line now done before outside of loop over instances
				// for (int fi = 0; fi < numFeatures; fi++) qeag[li][fi] += modelLabelWeight; // * 1.0;
				for (int fl = 0; fl < fv.numLocations(); fl++) {
					fli = fv.indexAtLocation(fl);
					// When the value of this feature "g" is zero, a value of 1.0 should be included
					// in the expectation; we'll actually add all these at the end (pre-"assuming"
					// that all features have value zero).  Here we subtract the "assumed"
					// modelLabelWeight, and put in the true value based on non-zero valued feature "g".
					qeag[li][fli] += Math.log (modelLabelWeight * Math.exp (alphas[li][fli]) + (1-modelLabelWeight));
				}
			}
		}
		
		//System.out.println ("Calculating klgain values...");
		double[] klgains = new double[numFeatures];
		double klgainIncr, alpha;
		for (int i = 0; i < numClasses; i++)
			for (int j = 0; j < numFeatures; j++) {
				assert (!Double.isInfinite(alphas[i][j]));
				alpha = alphas[i][j];
				if (alpha == 0)
					continue;
				klgainIncr = (alpha * p[i][j]) - qeag[i][j] - (alpha*alpha/(2*gaussianPriorVariance));
				if (klgainIncr < 0) {
					if (false)
						logger.info ("WARNING: klgainIncr["+i+"]["+j+"]="+klgainIncr+
												 "  alpha="+alphas[i][j]+
												 "  feature="+ilist.getDataAlphabet().lookupObject(j)+
												 "  class="+ilist.getTargetAlphabet().lookupObject(i));
				} else
					klgains[j] += klgainIncr;
			}

		if (false) {
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

	public ExpGain (InstanceList ilist, LabelVector[] classifications, double gaussianPriorVariance)
	{
		super (ilist.getDataAlphabet(), calcExpGains (ilist, classifications, gaussianPriorVariance));
	}

	private static LabelVector[] getLabelVectorsFromClassifications (Classification[] c)
	{
		LabelVector[] ret = new LabelVector[c.length];
		for (int i = 0; i < c.length; i++)
			ret[i] = c[i].getLabelVector();
		return ret;
	}

	public ExpGain (InstanceList ilist, Classification[] classifications, double gaussianPriorVariance)
	{
		super (ilist.getDataAlphabet(),
					 calcExpGains (ilist, getLabelVectorsFromClassifications(classifications), gaussianPriorVariance));
	}


	public static class Factory implements RankedFeatureVector.Factory
	{
		LabelVector[] classifications;
		double gaussianPriorVariance = 10.0;
		
		public Factory (LabelVector[] classifications)
		{
			this.classifications = classifications;
		}

		public Factory (LabelVector[] classifications,
										double gaussianPriorVariance)
		{
			this.classifications = classifications;
			this.gaussianPriorVariance = gaussianPriorVariance;
		}
		
		public RankedFeatureVector newRankedFeatureVector (InstanceList ilist)
		{
			assert (ilist.getTargetAlphabet() == classifications[0].getAlphabet());
			return new ExpGain (ilist, classifications, gaussianPriorVariance);
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
