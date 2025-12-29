/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */






package cc.mallet.classify;

import cc.mallet.types.FeatureSelector;
import cc.mallet.types.InstanceList;

/**
 * Adaptor for adding feature selection to a classifier trainer.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class FeatureSelectingClassifierTrainer extends ClassifierTrainer
{
    ClassifierTrainer underlyingTrainer;
    FeatureSelector featureSelector;
    Classifier classifier;
    @Override public Classifier getClassifier () { return classifier; }

    public FeatureSelectingClassifierTrainer (ClassifierTrainer underlyingTrainer, FeatureSelector featureSelector)
    {
        this.underlyingTrainer = underlyingTrainer;
        this.featureSelector = featureSelector;
    }

    @Override public Classifier train (InstanceList trainingSet)
    {
        featureSelector.selectFeaturesFor (trainingSet);
        // TODO What about also selecting features for the validation set?
        this.classifier = underlyingTrainer.train (trainingSet);
        return classifier;
    }

}
