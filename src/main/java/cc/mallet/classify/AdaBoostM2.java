/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


package cc.mallet.classify;

import java.io.Serializable;

import cc.mallet.pipe.*;
import cc.mallet.types.*;


/**
 * AdaBoostM2
 *
 * <p>Yoav Freund and Robert E. Schapire
 * "Experiments with a New Boosting Algorithm"
 * In Journal of Machine Learning: Proceedings of the 13th International Conference, 1996
 * http://www.cs.princeton.edu/~schapire/papers/FreundSc96b.ps.Z
 *
 * @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class AdaBoostM2 extends Classifier implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	Classifier[] weakClassifiers;
    double[] alphas;
    
    public AdaBoostM2 (Pipe instancePipe, Classifier[] weakClassifiers, double[] alphas)
    {
        super (instancePipe);
        this.weakClassifiers = weakClassifiers;
        this.alphas = alphas;
    }

    /**
     * Get the number of weak classifiers in this ensemble classifier
     */
    public int getNumWeakClassifiers()
    {
        return alphas.length;
    }

    /** 
     * Return an AdaBoostM2 classifier that uses only the first
     * <tt>numWeakClassifiersToUse</tt> weak learners.
     * 
     * <p>The returned classifier's Pipe and weak classifiers
     * are backed by the respective objects of this classifier, 
     * so changes to the returned classifier's Pipe and weak
     * classifiers are reflected in this classifier, and vice versa.
     */
    public AdaBoostM2 getTrimmedClassifier(int numWeakClassifiersToUse)
    {
        if (numWeakClassifiersToUse <= 0 || numWeakClassifiersToUse > weakClassifiers.length)
	  throw new IllegalArgumentException("number of weak learners to use out of range:" 
				       + numWeakClassifiersToUse);

        Classifier[] newWeakClassifiers = new Classifier[numWeakClassifiersToUse];
        System.arraycopy(weakClassifiers, 0, newWeakClassifiers, 0, numWeakClassifiersToUse);
        double[] newAlphas = new double[numWeakClassifiersToUse];
        System.arraycopy(alphas, 0, newAlphas, 0, numWeakClassifiersToUse);
        return new AdaBoostM2(instancePipe, newWeakClassifiers, newAlphas);
    }

    @Override public Classification classify (Instance inst)
    {
        return classify(inst, weakClassifiers.length);
    }

    /**
     * Classify the given instance using only the first
     * <tt>numWeakClassifiersToUse</tt> classifiers
     * trained during boosting
     */
    public Classification classify (Instance inst, int numWeakClassifiersToUse)
    {
    	if (numWeakClassifiersToUse <= 0 || numWeakClassifiersToUse > weakClassifiers.length)
    		throw new IllegalArgumentException("number of weak learners to use out of range:" 
    				+ numWeakClassifiersToUse);

    	FeatureVector fv = (FeatureVector) inst.getData();
    	assert (instancePipe == null || fv.getAlphabet () == this.instancePipe.getDataAlphabet ());
    	
    	int numClasses = getLabelAlphabet().size();
    	double[] scores = new double[numClasses];
    	int bestIndex;
    	double sum = 0;
    	// Gather scores of all weakClassifiers
    	for (int round = 0; round < numWeakClassifiersToUse; round++) {
    		bestIndex = weakClassifiers[round].classify(inst).getLabeling().getBestIndex();
    		scores[bestIndex] += alphas[round];
    		sum += scores[bestIndex];
    	}
    	// Normalize the scores
    	for (int i = 0; i < scores.length; i++)
    		scores[i] /= sum;
    	return new Classification (inst, this, new LabelVector (getLabelAlphabet(), scores));
    }
        
}
