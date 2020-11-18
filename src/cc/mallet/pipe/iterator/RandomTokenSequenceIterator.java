/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.iterator;

import java.net.URI;
import java.util.Iterator;
import java.util.logging.Logger;

import com.google.errorprone.annotations.Var;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Randoms;

public class RandomTokenSequenceIterator implements Iterator<Instance>
{
	private static Logger logger = MalletLogger.getLogger(RandomTokenSequenceIterator.class.getName());

	Randoms r;
	Dirichlet classCentroidDistribution;
	double classCentroidAvergeAlphaMean;
	double classCentroidAvergeAlphaVariance;
	double featureVectorSizePoissonLambda;
	double classInstanceCountPoissonLamba;
	String[] classNames;

	int[] numInstancesPerClass;						// indexed over classes
	Dirichlet[] classCentroid;						// indexed over classes
	int currentClassIndex;
	int currentInstanceIndex;
	
	public RandomTokenSequenceIterator (Randoms r,
																			// the generator of all random-ness used here
																			Dirichlet classCentroidDistribution,
																			// includes a Alphabet
																			double classCentroidAvergeAlphaMean,
																			// Gaussian mean on the sum of alphas
																			double classCentroidAvergeAlphaVariance,
																			// Gaussian variance on the sum of alphas
																			double featureVectorSizePoissonLambda,
																			double classInstanceCountPoissonLamba,
																			String[] classNames)
	{
		this.r = r;
		this.classCentroidDistribution = classCentroidDistribution;
		assert (classCentroidDistribution.getAlphabet() instanceof Alphabet);
		this.classCentroidAvergeAlphaMean = classCentroidAvergeAlphaMean;
		this.classCentroidAvergeAlphaVariance = classCentroidAvergeAlphaVariance;
		this.featureVectorSizePoissonLambda = featureVectorSizePoissonLambda;
		this.classInstanceCountPoissonLamba = classInstanceCountPoissonLamba;
		this.classNames = classNames;
		this.numInstancesPerClass = new int[classNames.length];
		this.classCentroid = new Dirichlet[classNames.length];
		for (int i = 0; i < classNames.length; i++) {
			logger.fine ("classCentroidAvergeAlphaMean = "+classCentroidAvergeAlphaMean);
			double aveAlpha = r.nextGaussian (classCentroidAvergeAlphaMean,
																				classCentroidAvergeAlphaVariance);
			logger.fine ("aveAlpha = "+aveAlpha);
			classCentroid[i] = classCentroidDistribution.randomDirichlet (r, aveAlpha);
			//logger.fine ("Dirichlet for class "+classNames[i]);	classCentroid[i].print();
		}
		reset ();
	}

	public RandomTokenSequenceIterator (Randoms r, Alphabet vocab, String[] classnames)
	{
		this (r, new Dirichlet(vocab, 2.0),
					30, 0,
					10, 20, classnames);
	}

	public Alphabet getAlphabet () { return classCentroidDistribution.getAlphabet(); }

	private static Alphabet dictOfSize (int size)
	{
		Alphabet ret = new Alphabet ();
		for (int i = 0; i < size; i++)
			ret.lookupIndex ("feature"+i);
		return ret;
	}

	private static String[] classNamesOfSize (int size)
	{
		String[] ret = new String[size];
		for (int i = 0; i < size; i++)
			ret[i] = "class"+i;
		return ret;
	}

	public RandomTokenSequenceIterator (Randoms r, int vocabSize, int numClasses)
	{
		this (r, new Dirichlet(dictOfSize(vocabSize), 2.0),
					30, 0,
					10, 20, classNamesOfSize(numClasses));
	}

	public void reset ()
	{
		for (int i = 0; i < classNames.length; i++) {
			this.numInstancesPerClass[i] = r.nextPoisson (classInstanceCountPoissonLamba);
			logger.fine ("Class "+classNames[i]+" will have "
									 +numInstancesPerClass[i]+" instances.");
		}
		this.currentClassIndex = classNames.length - 1;
		this.currentInstanceIndex = numInstancesPerClass[currentClassIndex] - 1;
	}

	public Instance next ()
	{
		if (currentInstanceIndex < 0) {
			if (currentClassIndex <= 0)
				throw new IllegalStateException ("No next TokenSequence.");
			currentClassIndex--;
			currentInstanceIndex = numInstancesPerClass[currentClassIndex] - 1;
		}
		@Var
		URI uri = null;
		try { uri = new URI ("random:" + classNames[currentClassIndex] + "/" + currentInstanceIndex); }
		catch (Exception e) {e.printStackTrace(); throw new IllegalStateException (); }
		//xxx Producing small numbers? int randomSize = r.nextPoisson (featureVectorSizePoissonLambda);
		int randomSize = (int)featureVectorSizePoissonLambda;
		TokenSequence ts = classCentroid[currentClassIndex].randomTokenSequence (r, randomSize);
		//logger.fine ("FeatureVector "+currentClassIndex+" "+currentInstanceIndex); fv.print();
		currentInstanceIndex--;
		return new Instance (ts, classNames[currentClassIndex], uri, null);
	}

	public boolean hasNext ()	{	return ! (currentClassIndex <= 0 && currentInstanceIndex <= 0);	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}
