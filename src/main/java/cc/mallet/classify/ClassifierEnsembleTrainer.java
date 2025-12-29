package cc.mallet.classify;

import cc.mallet.types.InstanceList;

/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/**
 * @author <a href="mailto:mccallum@cs.umass.edu">Andrew McCallum</a>
 */
public class ClassifierEnsembleTrainer extends ClassifierTrainer<ClassifierEnsemble>
{
  Classifier[] classifiers;
	ClassifierEnsemble classifier;
	public ClassifierEnsemble getClassifier () { return classifier; }

  public ClassifierEnsembleTrainer (Classifier[] classifiers)
  {
    this.classifiers = (Classifier[]) classifiers.clone();
  }

  public ClassifierEnsemble train (InstanceList trainingSet)
  {
    //if (initialClassifier != null) throw new IllegalArgumentException("initialClassifier not yet supported");
    // Make an instance list, with features being the outputs of the ensemble classifiers
    //return null;
  	throw new IllegalStateException ("Not yet implemented.");
  }
}
