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
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.PrintStream;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.DenseVector;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.RankedFeatureVector;

/**
 * Maximum Entropy (AKA Multivariate Logistic Regression) classifier.
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class MaxEnt extends Classifier implements Serializable
{
	static final double DEFAULT_TEMPERATURE = 1.0;
	protected double [] parameters;										// indexed by <labelIndex,featureIndex>
	protected int defaultFeatureIndex;
	protected FeatureSelection featureSelection;
	protected FeatureSelection[] perClassFeatureSelection;

	// The default feature is always the feature with highest index
	public MaxEnt (Pipe dataPipe,
			double[] parameters,
			FeatureSelection featureSelection,
			FeatureSelection[] perClassFeatureSelection)
	{
		super (dataPipe);
		assert (featureSelection == null || perClassFeatureSelection == null);
		if (parameters != null)
			this.parameters = parameters;
		else
			this.parameters = new double[getNumParameters(dataPipe)];
		this.featureSelection = featureSelection;
		this.perClassFeatureSelection = perClassFeatureSelection;
		this.defaultFeatureIndex = dataPipe.getDataAlphabet().size();
//		assert (parameters.getNumCols() == defaultFeatureIndex+1);
	}

	public MaxEnt (Pipe dataPipe,	double[] parameters,
			FeatureSelection featureSelection)
	{
		this (dataPipe, parameters, featureSelection, null);
	}

	public MaxEnt (Pipe dataPipe, double[] parameters,
			FeatureSelection[] perClassFeatureSelection)
	{
		this (dataPipe, parameters, null, perClassFeatureSelection);
	}

	public MaxEnt (Pipe dataPipe,	double[] parameters)
	{
		this (dataPipe, parameters, null, null);
	}

	public double[] getParameters () {
		return parameters;
	}
	
	public int getNumParameters () {
		assert (this.instancePipe.getDataAlphabet() != null);
		assert (this.instancePipe.getTargetAlphabet() != null);
		return MaxEnt.getNumParameters(this.instancePipe);
	}
	
	public static int getNumParameters (Pipe instancePipe) {
		return (instancePipe.getDataAlphabet().size() + 1) * instancePipe.getTargetAlphabet().size();
	}

	public void setParameters(double[] parameters){
		this.parameters = parameters;
	}

	public void setParameter (int classIndex, int featureIndex, double value)
	{
		parameters[classIndex*(getAlphabet().size()+1) + featureIndex] = value;
	}

	public FeatureSelection getFeatureSelection() {
		return featureSelection;
	}
	
	public MaxEnt setFeatureSelection (FeatureSelection fs) {
		featureSelection = fs;
		return this;
	}


	public FeatureSelection[] getPerClassFeatureSelection(){
		return perClassFeatureSelection;
	}
	
	public MaxEnt setPerClassFeatureSelection (FeatureSelection[] fss){
		this.perClassFeatureSelection = fss;
		return this;
	}


	public int getDefaultFeatureIndex(){
		return defaultFeatureIndex;
	}

	public void setDefaultFeatureIndex(int defaultFeatureIndex){
		this.defaultFeatureIndex = defaultFeatureIndex;
	}


	public void getUnnormalizedClassificationScores (Instance instance, double[] scores)
	{
		//  arrayOutOfBounds if pipe has grown since training 
		//        int numFeatures = getAlphabet().size() + 1;
		int numFeatures = this.defaultFeatureIndex + 1;

		int numLabels = getLabelAlphabet().size();
		assert (scores.length == numLabels);
		FeatureVector fv = (FeatureVector) instance.getData ();
		// Make sure the feature vector's feature dictionary matches
		// what we are expecting from our data pipe (and thus our notion
		// of feature probabilities.
		assert (fv.getAlphabet ()
				== this.instancePipe.getDataAlphabet ());

		// Include the feature weights according to each label
		for (int li = 0; li < numLabels; li++) {
			scores[li] = parameters[li*numFeatures + defaultFeatureIndex]
			                        + MatrixOps.rowDotProduct (parameters, numFeatures,
			                        		li, fv,
			                        		defaultFeatureIndex,
			                        		(perClassFeatureSelection == null
			                        				? featureSelection
			                        						: perClassFeatureSelection[li]));
		}
	}

	public void getClassificationScores (Instance instance, double[] scores)
	{
		getUnnormalizedClassificationScores(instance, scores);
		// Move scores to a range where exp() is accurate, and normalize
		int numLabels = getLabelAlphabet().size();
		double max = MatrixOps.max (scores);
		double sum = 0;
		for (int li = 0; li < numLabels; li++)
			sum += (scores[li] = Math.exp (scores[li] - max));
		for (int li = 0; li < numLabels; li++) {
			scores[li] /= sum;
			// xxxNaN assert (!Double.isNaN(scores[li]));
		}
	}

	//modified by Limin Yao, to deal with decreasing the peak of some labels
	public void getClassificationScoresWithTemperature (Instance instance, double temperature, double[] scores)
	{
		getUnnormalizedClassificationScores(instance, scores);

		//scores should be divided by temperature, scores are sum of weighted features
		MatrixOps.timesEquals(scores, 1/temperature);

		// Move scores to a range where exp() is accurate, and normalize
		int numLabels = getLabelAlphabet().size();
		double max = MatrixOps.max (scores);
		double sum = 0;
		for (int li = 0; li < numLabels; li++)
			sum += (scores[li] = Math.exp (scores[li] - max));
		for (int li = 0; li < numLabels; li++) {
			scores[li] /= sum;
			// xxxNaN assert (!Double.isNaN(scores[li]));
		}
	}

	//modified by Limin Yao, using temperature classification score
	public Classification classify (Instance instance)
	{
		int numClasses = getLabelAlphabet().size();
		double[] scores = new double[numClasses];
		//getClassificationScores (instance, scores);
		getClassificationScoresWithTemperature (instance, DEFAULT_TEMPERATURE, scores);
		// Create and return a Classification object
		return new Classification (instance, this,
				new LabelVector (getLabelAlphabet(),
						scores));
	}

	public void print () {
		print(System.out);
	}

	public void print (PrintStream out) 
	{		
		final Alphabet dict = getAlphabet();
		final LabelAlphabet labelDict = getLabelAlphabet();

		int numFeatures = dict.size() + 1;
		int numLabels = labelDict.size();

		// Include the feature weights according to each label
		for (int li = 0; li < numLabels; li++) {
			out.println ("FEATURES FOR CLASS "+labelDict.lookupObject (li));
			out.println (" <default> "+parameters [li*numFeatures + defaultFeatureIndex]);
			for (int i = 0; i < defaultFeatureIndex; i++) {
				Object name = dict.lookupObject (i);
				double weight = parameters [li*numFeatures + i];
				out.println (" "+name+" "+weight);
			}
		}
	}
	
	//printRank, added by Limin Yao
	public void printRank (PrintWriter out) 
	{		
		final Alphabet dict = getAlphabet();
		final LabelAlphabet labelDict = getLabelAlphabet();

		int numFeatures = dict.size() + 1;
		int numLabels = labelDict.size();

		// Include the feature weights according to each label
		RankedFeatureVector rfv;
		double[] weights = new double[numFeatures-1]; // do not deal with the default feature
		for (int li = 0; li < numLabels; li++) {
			out.print ("FEATURES FOR CLASS "+labelDict.lookupObject (li) + " ");
			for (int i = 0; i < defaultFeatureIndex; i++) {
				Object name = dict.lookupObject (i);
				double weight = parameters [li*numFeatures + i];
				weights[i] = weight;
			}
			rfv = new RankedFeatureVector(dict,weights);
			rfv.printByRank(out);
			out.println (" <default> "+parameters [li*numFeatures + defaultFeatureIndex] + " ");
		}
	}
	
	public void printExtremeFeatures (PrintWriter out,int num) 
	{		
		final Alphabet dict = getAlphabet();
		final LabelAlphabet labelDict = getLabelAlphabet();

		int numFeatures = dict.size() + 1;
		int numLabels = labelDict.size();

		// Include the feature weights according to each label
		RankedFeatureVector rfv;
		double[] weights = new double[numFeatures-1]; // do not deal with the default feature
		for (int li = 0; li < numLabels; li++) {
			out.print ("FEATURES FOR CLASS "+labelDict.lookupObject (li) + " ");
			for (int i = 0; i < defaultFeatureIndex; i++) {
				Object name = dict.lookupObject (i);
				double weight = parameters [li*numFeatures + i];
				weights[i] = weight;
			}
			rfv = new RankedFeatureVector(dict,weights);
			rfv.printTopK(out,num);
			out.print (" <default> "+parameters [li*numFeatures + defaultFeatureIndex] + " ");
		//	rfv.printLowerK(out, num);
			out.println();
		}
	}
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	static final int NULL_INTEGER = -1;

	private void writeObject(ObjectOutputStream out) throws IOException
	{
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(getInstancePipe());
		int np = parameters.length;
		out.writeInt(np);
		for (int p = 0; p < np; p++)
			out.writeDouble(parameters[p]);
		out.writeInt(defaultFeatureIndex);
		if (featureSelection == null)
			out.writeInt(NULL_INTEGER);
		else
		{
			out.writeInt(1);
			out.writeObject(featureSelection);
		}
		if (perClassFeatureSelection == null)
			out.writeInt(NULL_INTEGER);
		else
		{
			out.writeInt(perClassFeatureSelection.length);
			for (int i = 0; i < perClassFeatureSelection.length; i++)
				if (perClassFeatureSelection[i] == null)
					out.writeInt(NULL_INTEGER);
				else
				{
					out.writeInt(1);
					out.writeObject(perClassFeatureSelection[i]);
				}
		}
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt();
		if (version != CURRENT_SERIAL_VERSION)
			throw new ClassNotFoundException("Mismatched MaxEnt versions: wanted " +
					CURRENT_SERIAL_VERSION + ", got " +
					version);
		instancePipe = (Pipe) in.readObject();
		int np = in.readInt();
		parameters = new double[np];
		for (int p = 0; p < np; p++)
			parameters[p] = in.readDouble();
		defaultFeatureIndex = in.readInt();
		int opt = in.readInt();
		if (opt == 1)
			featureSelection = (FeatureSelection)in.readObject();
		int nfs = in.readInt();
		if (nfs >= 0)
		{
			perClassFeatureSelection = new FeatureSelection[nfs];
			for (int i = 0; i < nfs; i++)
			{
				opt = in.readInt();
				if (opt == 1)
					perClassFeatureSelection[i] = (FeatureSelection)in.readObject();
			}
		}
	}
}

