/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.confidence;

import java.util.Vector;
import java.util.Collections;
import java.util.Comparator;

import cc.mallet.fst.*;
import cc.mallet.types.*;

public class ConfidenceEvaluator
{
	static int DEFAULT_NUM_BINS = 20;
	Vector confidences;
	int nBins;
	int numCorrect;
	
	public ConfidenceEvaluator (Vector confidences, int nBins)
	{
		this.confidences = confidences;
		this.nBins = nBins;
		this.numCorrect = getNumCorrectEntities();
		// sort confidences by score
		Collections.sort (confidences, new ConfidenceComparator());
	}

	public ConfidenceEvaluator (Vector confidences)
	{
		this (confidences, DEFAULT_NUM_BINS);
	}

	public ConfidenceEvaluator (Segment[] segments, boolean sorted)
	{
		this.confidences = new Vector ();
		for (int i=0; i < segments.length; i++) {
			confidences.add (new EntityConfidence (segments[i].getConfidence(),
																						 segments[i].correct(), segments[i].getInput(),
																						 segments[i].getStart(), segments[i].getEnd()));
		}
		if (!sorted)
			Collections.sort (confidences, new ConfidenceComparator());
		this.nBins = DEFAULT_NUM_BINS;
		this.numCorrect = getNumCorrectEntities ();
	}

	public ConfidenceEvaluator (InstanceWithConfidence[] instances, boolean sorted) {
		this.confidences = new Vector ();
		for (int i=0; i < instances.length; i++) {
			Sequence input = (Sequence) instances[i].getInstance().getData();
			confidences.add (new EntityConfidence (instances[i].getConfidence(),
																						 instances[i].correct(), input,
																						 0, input.size()-1));
		}
		if (!sorted)
			Collections.sort (confidences, new ConfidenceComparator());
		this.nBins = DEFAULT_NUM_BINS;
		this.numCorrect = getNumCorrectEntities ();		
	}

	public ConfidenceEvaluator (PipedInstanceWithConfidence[] instances, boolean sorted) {
		this.confidences = new Vector ();
		for (int i=0; i < instances.length; i++) {
			confidences.add (new EntityConfidence (instances[i].getConfidence(),
																						 instances[i].correct(), null,
																						 0, 1));
		}
		if (!sorted)
			Collections.sort (confidences, new ConfidenceComparator());
		this.nBins = DEFAULT_NUM_BINS;
		this.numCorrect = getNumCorrectEntities ();		
	}

	/** Correlation when one variable (X) is binary: r = (bar(x1) -
			bar(x0)) * sqrt(p(1-p)) / sx , where bar(x1) = mean of X when Y
			is 1 bar(x0) = mean of X when Y is 0 sx = standard deviation of
			X p = proportion of values where Y=1
	*/
	
 	public double pointBiserialCorrelation ()
	{
		// here, Y = {incorrect = 0,correct = 1}, X = confidence
		double x0bar = getAverageIncorrectConfidence ();
		double x1bar = getAverageCorrectConfidence ();
		double p = (double)this.numCorrect / size();
		double sx = getConfidenceStandardDeviation ();
		return (x1bar - x0bar) * Math.sqrt(p*(1-p)) / sx;
	}

	/**
		 IR Average precision measure. Analogous to ranking _correct_
		 documents by confidence score. 
	 */
	public double getAveragePrecision () {
		int nc = 0;
		int ni = 0;
		double totalPrecision = 0.0;
		for (int i=confidences.size()-1; i >= 0; i--) {
			EntityConfidence c = (EntityConfidence) confidences.get (i);
			if (c.correct()) {
				nc++;
				totalPrecision += (double)nc / (nc + ni);
			}
			else ni++;
		}
		return totalPrecision / nc;
	}

	/**
		 For comparison, rank segments as badly as possible (all
		 "incorrect" before "correct").
	 */
	public double getWorstAveragePrecision () {
		int ni = confidences.size() - this.numCorrect;
		double totalPrecision = 0.0;
		for (int nc=1; nc <= this.numCorrect; nc++) {
			totalPrecision += (double) nc / (nc + ni);
		}
		return totalPrecision / this.numCorrect;
	}
	
	public double getConfidenceSum()
	{
		double sum = 0.0;
		for (int i = 0; i < size(); i++)
			sum += ((EntityConfidence)confidences.get(i)).confidence();
		return sum;
	}
	
	public double getConfidenceMean ()
	{
		return getConfidenceSum() / size();
	}
	
	/** Standard deviation of confidence scores
	 */
	public double getConfidenceStandardDeviation ()
	{
		double mean = getConfidenceMean();
		double sumSquaredDifference = 0.0;
		for (int i = 0; i < size(); i++) {
			double conf = ((EntityConfidence)confidences.get(i)).confidence();
			sumSquaredDifference += ((conf - mean) * (conf - mean));
		}
		return Math.sqrt (sumSquaredDifference / (double)size());
	}
	
	/** Calculate pearson's R for the corellation between confidence and
	 * correct, where 1 = correct and -1 = incorrect
	 */
	public double correlation ()
	{
		double xSum = 0;
		double xSumOfSquares = 0;
		double ySum = 0;
		double ySumOfSquares = 0;
		double xySum = 0; // product of x and y
		for (int i = 0; i < size(); i++) {
			double value = ((EntityConfidence)confidences.get(i)).correct() ? 1.0 : -1.0;
			xSum += value;
			xSumOfSquares += (value * value);
			double conf = ((EntityConfidence)confidences.get(i)).confidence();
			ySum += conf;
			ySumOfSquares += (conf * conf);
			xySum += value * conf;
		}
		double xVariance = xSumOfSquares - (xSum * xSum / size());
		double yVariance = ySumOfSquares - (ySum * ySum / size());
		double crossVariance = xySum  - (xSum * ySum / size());
		return crossVariance / Math.sqrt (xVariance * yVariance);
	}
	
	/** get accuracy at coverage for each bin of values
	 */
	public double[] getAccuracyCoverageValues ()
	{
		double [] values = new double [this.nBins];
		int step = 100 / nBins;
		for (int i = 0; i < values.length; i++) {
			values[i] = accuracyAtCoverage (step * (double)(i+1) / 100.0);
		}
		return values;
	}

	public String accuracyCoverageValuesToString () {
		String buf = "";
		double [] vals = getAccuracyCoverageValues ();
		int step = 100 / nBins;
		for (int i=0; i < vals.length; i++) {
			buf += ((step * (double)(i+1))/100.0) + "\t" + vals[i] + "\n";
		}
		return buf;
	}
	
	/** get accuracy at recall for each bin of values
         * @param totalTrue total number of true Segments
         * @return 2-d array where values[i][0] is coverage and
         * values[i][1] is accuracy at position i.
	 */
	public double[][] getAccuracyRecallValues (int totalTrue)
	{
		double [][] values = new double [this.nBins][2];
		int step = 100 / nBins;
		for (int i = 0; i < this.nBins; i++) {
                  values[i] = new double[2];
                  double coverage = step * (double)(i+1) / 100.0;
                  values[i][1] = accuracyAtCoverage(coverage);
                  int numCorrect = numCorrectAtCoverage(coverage);
                  values[i][0] = (double)numCorrect / totalTrue;
		}
		return values;
	}

	public String accuracyRecallValuesToString (int totalTrue) {
		String buf = "";
		double [][] vals = getAccuracyRecallValues (totalTrue);
		for (int i=0; i < this.nBins; i++) 
                  buf += vals[i][0] + "\t" + vals[i][1] + "\n";
		return buf;
	}

	public double accuracyAtCoverage (double cov)
	{
		assert (cov <= 1 && cov > 0);
		int numPoints = (int) (Math.round ((double)size()*cov));
		return ((double)numCorrectAtCoverage(cov) / numPoints);
	}

        public int numCorrectAtCoverage (double cov) {
		assert (cov <= 1 && cov > 0);
		// num accuracies to sum for this value of cov
		int numPoints = (int) (Math.round ((double)size()*cov));
		int numCorrect = 0;
		for (int i = 0; i < numPoints; i++) {
			if (((EntityConfidence)confidences.get(size() - i - 1)).correct())
				numCorrect++;
		}
		return numCorrect;          
        }

	public double getAverageAccuracy ()
	{
		int numCorrect = 0;
		double totalArea= 0.0;
		for(int i=confidences.size()-1; i>=0; i--){
			if ( ((EntityConfidence)confidences.get(i)).correct()) 
				numCorrect++;
			totalArea += (double)numCorrect / (confidences.size() - i);
		}
		return totalArea / confidences.size();				
	}

	public int numCorrect()
	{
		return this.numCorrect;
	}
	/**
		 number of entities correctly extracted 
	 */
	private int getNumCorrectEntities ()
	{
		int sum = 0;
		for (int i = 0; i < confidences.size(); i++) {
			EntityConfidence ec = (EntityConfidence) confidences.get(i);
			if (ec.correct()) {
				sum++;
			}				
		}
		return sum;
	}

  /** Average confidence score for the incorrect entities
	 */
	public double getAverageIncorrectConfidence ()
	{
		double sum = 0.0;
		for (int i = 0; i < confidences.size(); i++) {
			EntityConfidence ec = (EntityConfidence) confidences.get(i);
			if (!ec.correct()) {
				sum += ec.confidence();				
			}				
		}
		return sum / ((double)size() - (double) this.numCorrect); 		
	}
	/** Average confidence score for the incorrect entities		 
	 */
	public double getAverageCorrectConfidence ()
	{
		double sum = 0.0;
		for (int i = 0; i < confidences.size(); i++) {
			EntityConfidence ec = (EntityConfidence) confidences.get(i);
			if (ec.correct()) {
				sum += ec.confidence();				
			}				
		}
		return sum / (double) this.numCorrect; 		
	}

	public int size()
	{
		return confidences.size();
	}

	public String toString()
	{
		StringBuffer toReturn = new StringBuffer();
		for (int i = 0; i < size(); i++) {
			toReturn.append (((EntityConfidence)confidences.get(i)).toString() + " ");
		}
		return toReturn.toString();
	}

  /** a simple class to store a confidence score and whether or not this
   * labeling is correct
   */
  public static class EntityConfidence
  {
    double confidence;
    boolean correct;
    String entity;
    
    public EntityConfidence (double conf, boolean corr, String text){
      this.confidence = conf;
      this.correct = corr;
      this.entity = text;
    }


    public EntityConfidence (double conf, boolean corr, Sequence input, int start, int end){
      this.confidence = conf;
      this.correct = corr;
      StringBuffer buff = new StringBuffer();
      if (input != null) {
        for (int j = start; j <= end; j++){
          FeatureVector fv = (FeatureVector) input.get(j);
          for (int k = 0; k < fv.numLocations(); k++) {
            String featureName = fv.getAlphabet().lookupObject (fv.indexAtLocation (k)).toString();
            if (featureName.startsWith ("W=") && featureName.indexOf("@") == -1){
              buff.append(featureName.substring (featureName.indexOf ('=')+1) + " ");
            }
          }
        }
      }
      this.entity = buff.toString();
    }
    public double confidence () {return confidence;}
    public boolean correct () {return correct;}
    public String toString ()
    {
      StringBuffer toReturn = new StringBuffer();
      toReturn.append(this.entity + " / " + this.confidence + " / "+ (this.correct ? "correct" : "incorrect") + "\n");
      return toReturn.toString();
    }	
  }

  private class ConfidenceComparator implements Comparator
  {
    public final int compare (Object a, Object b)
    {
      double x = ((EntityConfidence) a).confidence();
      double y = ((EntityConfidence) b).confidence();
      double difference = x - y;
      int toReturn = 0;
      if(difference > 0)
        toReturn = 1;
      else if (difference < 0)
        toReturn = -1;
      return(toReturn);		
    }    
  }
}
