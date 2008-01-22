/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify;

import cc.mallet.pipe.*;
import cc.mallet.types.*;
/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class BaggingClassifier extends Classifier
{
	Classifier[] baggedClassifiers;
	double[] weights;											// Not yet implemented!

	public BaggingClassifier (Pipe instancePipe, Classifier[] baggedClassifiers)
	{
		super (instancePipe);
		this.baggedClassifiers = baggedClassifiers;
	}

	public Classification classify (Instance inst)
	{
		int numClasses = getLabelAlphabet().size();
		double[] scores = new double[numClasses];
		int bestIndex;
		double sum = 0;
		for (int i = 0; i < baggedClassifiers.length; i++) {
			Labeling labeling = baggedClassifiers[i].classify(inst).getLabeling();
			labeling.addTo (scores);
		}
		MatrixOps.normalize (scores);
		return new Classification (inst, this, new LabelVector (getLabelAlphabet(), scores));
	}

}
