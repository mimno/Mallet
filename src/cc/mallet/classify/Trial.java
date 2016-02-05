/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package cc.mallet.classify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.Labeling;

/**
 * Stores the results of classifying a collection of Instances,
 * and provides many methods for evaluating the results.
 *
 * If you just need one evaluation result, you may find it easier to one
 * of the corresponding methods in Classifier, which simply call the methods here.
 * 
 * @see InstanceList
 * @see Classifier
 * @see Classification
 *
 * @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class Trial extends ArrayList<Classification>
{
	private static Logger logger = Logger.getLogger(Trial.class.getName());

	Classifier classifier;

	public Trial (Classifier c, InstanceList ilist)
	{
		super (ilist.size());
		this.classifier = c;
		for (Instance instance : ilist)
			this.add (c.classify (instance));
	}
	
	public boolean add (Classification c)
	{
		if (c.getClassifier() != this.classifier)
			throw new IllegalArgumentException ("Trying to add Classification from a different Classifier.");
		return super.add (c);
	}
	
	public void add (int index, Classification c) 
	{
		if (c.getClassifier() != this.classifier)
			throw new IllegalArgumentException ("Trying to add Classification from a different Classifier.");
		super.add (index, c);
	}
	
	public boolean addAll(Collection<? extends Classification> collection) {
		boolean ret = true;
		for (Classification c : collection)
			if (!this.add(c))
				ret = false;
		return ret;
	}
	
	public boolean addAll (int index, Collection<? extends Classification> collection) {
		throw new IllegalStateException ("Not implemented.");
	}

	
	public Classifier getClassifier () 
	{
		return classifier;
	}

	/** Return the fraction of instances that have the correct label as their best predicted label. */
	public double getAccuracy ()
	{
		int numCorrect = 0;
		for (int i = 0; i < this.size(); i++)
			if (this.get(i).bestLabelIsCorrect())
				numCorrect++;
		return (double)numCorrect/this.size();
	}

	
	/** Calculate the precision of the classifier on an instance list for a
	    particular target entry */
	public double getPrecision (Object labelEntry)
	{
		int index;
		if (labelEntry instanceof Labeling)
			index = ((Labeling)labelEntry).getBestIndex();
		else
			index = classifier.getLabelAlphabet().lookupIndex(labelEntry, false);
		if (index == -1) throw new IllegalArgumentException ("Label "+labelEntry.toString()+" is not a valid label.");
		return getPrecision (index);
	}
	
	public double getPrecision (Labeling label)
	{
		return getPrecision (label.getBestIndex());
	}

	/** Calculate the precision for a particular target index from an 
	    array list of classifications */
	public double getPrecision (int index)
	{
		int numCorrect = 0;
		int numInstances = 0;
		int trueLabel, classLabel;
		for (int i = 0; i<this.size(); i++) {
			trueLabel = this.get(i).getInstance().getLabeling().getBestIndex();
			classLabel = this.get(i).getLabeling().getBestIndex();
			if (classLabel == index) {
				numInstances++;
				if (trueLabel == index)
					numCorrect++;
			}
		}
		
		// gdruck@cs.umass.edu
		// When no examples are predicted to have this label, 
		// we define precision to be 1.
		if (numInstances==0) {
			logger.warning("No examples with predicted label " + 
					classifier.getLabelAlphabet().lookupLabel(index) + "!");
			assert(numCorrect == 0);
			return 1;
		}
			
		return ((double)numCorrect/(double)numInstances);
	}

	
	/** Calculate the recall of the classifier on an instance list for a 
	    particular target entry */
	public double getRecall (Object labelEntry)
	{
		int index;
		if (labelEntry instanceof Labeling)
			index = ((Labeling)labelEntry).getBestIndex();
		else
			index = classifier.getLabelAlphabet().lookupIndex(labelEntry, false);
		if (index == -1) throw new IllegalArgumentException ("Label "+labelEntry.toString()+" is not a valid label.");
		return getRecall (index);
	}

	public double getRecall (Labeling label)
	{
		return getRecall (label.getBestIndex());
	}

	/** Calculate the recall for a particular target index from an
	    array list of classifications */
	public double getRecall (int labelIndex)
	{
		int numCorrect = 0;
		int numInstances = 0;
		int trueLabel, classLabel;
		for (int i = 0; i<this.size(); i++) {
			trueLabel = this.get(i).getInstance().getLabeling().getBestIndex();
			classLabel = this.get(i).getLabeling().getBestIndex();
			if ( trueLabel == labelIndex ) {
				numInstances++;
				if ( classLabel == labelIndex)
					numCorrect++;
			}
		}
		
		// gdruck@cs.umass.edu
		// When no examples have this label, 
		// we define recall to be 1.
		if (numInstances==0) {
			logger.warning("No examples with true label " + 
					classifier.getLabelAlphabet().lookupLabel(labelIndex) + "!");
			assert(numCorrect == 0);
			return 1;
		}
		
		return ((double)numCorrect/(double)numInstances);
	}

	/** Calculate the F1-measure of the classifier on an instance list for a
	    particular target entry */
	public double getF1 (Object labelEntry)
	{
		int index;
		if (labelEntry instanceof Labeling)
			index = ((Labeling)labelEntry).getBestIndex();
		else
			index = classifier.getLabelAlphabet().lookupIndex(labelEntry, false);
		if (index == -1) throw new IllegalArgumentException ("Label "+labelEntry.toString()+" is not a valid label.");
		return getF1 (index);
	}
	
	public double getF1 (Labeling label)
	{
		return getF1 (label.getBestIndex());
	}

	/** Calculate the F1-measure for a particular target index from an
	    array list of classifications */
	public double getF1 (int index)
	{
		double precision = getPrecision (index);
		double recall = getRecall (index);
		
		// gdruck@cs.umass.edu
		// When both precision and recall are 0, F1 is 0.
		if (precision==0.0 && recall==0.0) {
			return 0;
		}

		return 2*precision*recall/(precision+recall);
	}

	/** Return the average rank of the correct class label as returned by Labeling.getRank(correctLabel) on the predicted Labeling. */
	public double getAverageRank ()
	{
		double rsum = 0;
		Labeling tmpL;
		Classification tmpC;
		Instance tmpI;
		Label tmpLbl, tmpLbl2;
		int tmpInt;
		for(int i = 0; i < this.size(); i++) {
			tmpC = this.get(i);
			tmpI = tmpC.getInstance();
			tmpL = tmpC.getLabeling();
			tmpLbl = (Label)tmpI.getTarget();
			tmpInt = tmpL.getRank(tmpLbl);
			tmpLbl2 = tmpL.getLabelAtRank(0);
			rsum = rsum + tmpInt;
		}
		return rsum/this.size();
	}
	
}
