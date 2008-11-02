/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.classify;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import cc.mallet.classify.Classifier;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Labeling;

/**
 * The result of classifying a single instance.
 * Contains the instance, the classifier used, and the labeling the
 * classifier produced.
 * Also has methods for comparing the correct (true) label contained in the
 * target field of the instance with the one produced by the classifier.
 */
public class Classification
{
	Instance instance;
	Classifier classifier;
	Labeling labeling;

	public Classification (Instance instance, Classifier classifier,
												 Labeling labeling)
	{
		this.instance = instance;
		this.classifier = classifier;
		this.labeling = labeling;
	}

	public Instance getInstance ()
	{
		return instance;
	}

	public Classifier getClassifier ()
	{
		return classifier;
	}

	public Labeling getLabeling ()
	{
		return labeling;
	}

	public LabelVector getLabelVector ()
	{
		return labeling.toLabelVector();
	}

	public boolean bestLabelIsCorrect ()
	{
		Labeling correctLabeling = instance.getLabeling();
		if (correctLabeling == null)
			throw new IllegalStateException ("Instance has no label.");
		return (labeling.getBestLabel().equals (correctLabeling.getBestLabel()));
	}

	public double valueOfCorrectLabel ()
	{
		Labeling correctLabeling = instance.getLabeling();
		int correctLabelIndex = correctLabeling.getBestIndex();
		return labeling.value (correctLabelIndex);
	}

	public void print() {
		//not implemented
	}
	public void print (PrintWriter pw) throws FileNotFoundException
	{
		// xxx Fix this.
		/*System.out.print (classifier.getClass().getName() + "(.");
		System.out.print (") = [");
		for (int i = 0; i < labeling.numLocations(); i++)
			System.out.print (labeling.labelAtLocation(i).toString()+"="+labeling.valueAtLocation(i)+" ");
		System.out.println ("]");*/		
		pw.print(classifier.getClass().getName());
		pw.print(" ");
		pw.print(instance.getSource() + " ");
		for (int i = 0; i < labeling.numLocations(); i++)
			pw.print (labeling.labelAtLocation(i).toString()+"="+labeling.valueAtLocation(i)+" ");
		pw.println ();
	}
	
	public void printRank (PrintWriter pw) throws FileNotFoundException
	{
		// xxx Fix this.
		/*System.out.print (classifier.getClass().getName() + "(.");
		System.out.print (") = [");
		for (int i = 0; i < labeling.numLocations(); i++)
			System.out.print (labeling.labelAtLocation(i).toString()+"="+labeling.valueAtLocation(i)+" ");
		System.out.println ("]");*/		
		pw.print(classifier.getClass().getName());
		pw.print(" ");
		pw.print(instance.getSource() + " ");
		LabelVector lv = labeling.toLabelVector();
		lv.printByRank(pw);
		pw.println ();
	}

	public Instance toInstance() {
		Instance ret;
		FeatureVector fv;
		double[] values = new double[labeling.numLocations()];
		int[] indices = new int[labeling.numLocations()];
		for(int i = 0; i < labeling.numLocations(); i++){
			indices[i] = labeling.indexAtLocation(i);
			values[i] = labeling.valueAtLocation(i);
		}
		fv = new FeatureVector(labeling.getAlphabet(), indices, values);
		ret = new Instance(fv,null,null,instance.getSource());
		return ret;
	}
}
