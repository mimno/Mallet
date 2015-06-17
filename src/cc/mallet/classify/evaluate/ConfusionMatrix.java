/* Copyright (C) 2002 Dept. of Computer Science, Univ. of Massachusetts, Amherst

   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet

   This program toolkit free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License as
   published by the Free Software Foundation; either version 2 of the
   License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  For more
   details see the GNU General Public License and the file README-LEGAL.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
   02111-1307, USA. */


/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.classify.evaluate;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.*;
import java.text.*;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Trial;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Labeling;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletLogger;

/**
 * Calculates and prints confusion matrix, accuracy,
 * and precision for a given clasification trial.
 */
public class ConfusionMatrix
{
	private static Logger logger = MalletLogger.getLogger(ConfusionMatrix.class.getName());
	
	int numClasses;
  /**
   * the list of classifications from the trial
   */
	ArrayList classifications;
	/**
	 * 2-d confiusion matrix
	 */
	int[][] values;

	Trial trial;

	/**
	 * Constructs matrix and calculates values
	 * @param t the trial to build matrix from
	 */
	public ConfusionMatrix(Trial t)
	{
		this.trial = t;
		this.classifications = t;
		Labeling tempLabeling =
			((Classification)classifications.get(0)).getLabeling();
		this.numClasses = tempLabeling.getLabelAlphabet().size();
		values = new int[numClasses][numClasses];
		for(int i=0; i < classifications.size(); i++)
		{
			LabelVector lv =
				((Classification)classifications.get(i)).getLabelVector();
			Instance inst = ((Classification)classifications.get(i)).getInstance();
			int bestIndex = lv.getBestIndex();
			int correctIndex = inst.getLabeling().getBestIndex();
			assert(correctIndex != -1);
			//System.out.println("Best index="+bestIndex+". Correct="+correctIndex);
			values[correctIndex][bestIndex]++;
		}			
	}

	/** Return the count at row i (true) , column j (predicted) */
	 double value(int i, int j) 
	 {
	    assert(i >= 0 && j >= 0 && i < numClasses && j < numClasses);
	    return values[i][j];	    
	 }

	static private void appendJustifiedInt (StringBuffer sb, int i, boolean zeroDot) {
		if (i < 100) sb.append (' ');
		if (i < 10) sb.append (' ');
		if (i == 0 && zeroDot)
			sb.append (".");
		else
			sb.append (""+i);
	}

	public String toString () {
		StringBuffer sb = new StringBuffer ();
		int maxLabelNameLength = 0;
		LabelAlphabet labelAlphabet = trial.getClassifier().getLabelAlphabet();
		for (int i = 0; i < numClasses; i++) {
			int len = labelAlphabet.lookupLabel(i).toString().length();
			if (maxLabelNameLength < len)
				maxLabelNameLength = len;
		}

		double[] distribution = new double[values.length];
		for (int i = 0; i < distribution.length; i++)
			distribution[i] = MatrixOps.sum(values[i]);
		double baselineAccuracy = MatrixOps.max(distribution) / MatrixOps.sum(distribution);
		sb.append ("Confusion Matrix, row=true, column=predicted  accuracy="+trial.getAccuracy()+" most-frequent-tag baseline="+baselineAccuracy+"\n");
		for (int i = 0; i < maxLabelNameLength-5+4; i++) sb.append (' ');
		sb.append ("label");
		for (int c2 = 0; c2 < Math.min(10,numClasses); c2++)	sb.append ("   "+c2);
		for (int c2 = 10; c2 < numClasses; c2++)	sb.append ("  "+c2);
		sb.append ("  |total\n");
		for (int c = 0; c < numClasses; c++) {
			appendJustifiedInt (sb, c, false);
			String labelName = labelAlphabet.lookupLabel(c).toString();
			for (int i = 0; i < maxLabelNameLength-labelName.length(); i++) sb.append (' ');
			sb.append (" "+labelName+" ");
			for (int c2 = 0; c2 < numClasses; c2++) {
				appendJustifiedInt (sb, values[c][c2], true);
				sb.append (' ');
			}
			sb.append (" |"+ MatrixOps.sum(values[c]));
			sb.append ('\n');
		}
		return sb.toString();
	}
	
	/**
	 * Returns the precision of this predicted class
	 */
	public double getPrecision (int predictedClassIndex)
	{
		int total = 0;
		for (int trueClassIndex=0; trueClassIndex < this.numClasses; trueClassIndex++) {
			total += values[trueClassIndex][predictedClassIndex];
		}
		if (total == 0)
			return 0.0;
		else
			return (double) (values[predictedClassIndex][predictedClassIndex]) / total;
	}
	
	/**
	 * Returns percent of time that class2 is true class when 
	 * class1 is predicted class
	 * 
	 */
	public double getConfusionBetween (int class1, int class2)
	{
		int total = 0;
		for (int trueClassIndex=0; trueClassIndex < this.numClasses; trueClassIndex++) {
			total += values[trueClassIndex][class1];
		}
		if (total == 0)
			return 0.0;
		else
			return (double) (values[class2][class1]) / total;	    
	}

	/**
	 * Returns the percentage of instances with
	 * true label = classIndex
	 */
	public double getClassPrior (int classIndex)
	{
		int sum= 0;
		for(int i=0; i < numClasses; i++) 
			sum += values[classIndex][i];
		return (double)sum / classifications.size();
	}



  /**
	 * prints to stdout the confusion matrix,
	 * class frequency, precision, and recall
	 */
	/*
	public void print()
	{
		double totalPrecision = 0;
		double totalRecall = 0;
		double totalF1 = 0;
		HashMap index2class = new HashMap();
		LabelVector lv =
			((Classification)classifications.get(0)).getLabelVector();
		DecimalFormat df = new DecimalFormat("###.##");
		int [] numInstances = new int[this.numClasses];
		for(int i=0; i<this.numClasses; i++){
			int count = 0;
			for(int j=0; j<this.numClasses; j++)
				count += values[i][j];
			numInstances[i] = count;
			String label = lv.labelAtLocation(i).toString();
			System.out.println("index "+i+": "+label+
												 " "+count+" instances "+
												 df.format(100*(double)count/classifications.size())
				+"%");
			index2class.put (new Integer (i), label);
		}
		System.out.println("Confusion Matrix");
		for(int i=0; i<this.numClasses; i++){
			for(int j=0; j<this.numClasses; j++)
				System.out.print(values[j][i]+"\t\t");
			System.out.println("");
		}
		for(int i=0; i<this.numClasses; i++){
			double recall = 100.0*(double)values[j][j]/numInstances[i];
			double precision;
			int rowCount = 0;
			for(int j=0; j<this.numClasses; j++)
				rowCount += values[j][i];
			if (rowCount == 0)
				precision = 0;
			else
				precision = 100.0*(double)values[j][j] / rowCount;
			double f1;
			if (precision + recall == 0.0)
				f1 = 0;
			else
				f1 = 2 * precision * recall / (precision + recall);
			System.out.println("Class " +
												 (String)index2class.get(new Integer (i)));
			System.out.println("F1="+df.format(f1)+"%");
			System.out.println("Recall="+df.format(recall)+"%");
			System.out.println("Precision="+df.format(precision)+"%");
			totalPrecision += precision;
			totalRecall += recall;
			totalF1 += f1;
		}
		
		int numCorrect = 0;
		int totalInstances = 0;
		for(int i=0; i<this.numClasses; i++)
		{
			numCorrect += values[j][j];
			totalInstances+=numInstances[i];
		}
		System.out.println("Overall Accuracy="+
											 df.format(100.0*(double)numCorrect/totalInstances)+"%");
		System.out.println ("Average F1: " + (totalF1 / this.numClasses) +
												"\nAverage Precision: " +
												(totalPrecision / this.numClasses) +
												"\nAverage Recall: " + (totalRecall / this.numClasses));
	}
*/

}















