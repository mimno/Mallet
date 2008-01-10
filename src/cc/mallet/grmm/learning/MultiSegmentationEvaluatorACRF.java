/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Evaluate segmentation f1 for several different tags (marked in OIB format).
	 For example, tags might be B-PERSON I-PERSON O B-LOCATION I-LOCATION O...

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.grmm.learning; // Generated package name

import java.util.logging.*;
import java.text.DecimalFormat;

import java.util.List;

import cc.mallet.types.*;
import cc.mallet.util.MalletLogger;

public class MultiSegmentationEvaluatorACRF extends ACRFEvaluator
{
	private static Logger logger = MalletLogger.getLogger(MultiSegmentationEvaluatorACRF.class.getName());


	// equals() is called on these objects to determine if this token is the start or continuation of a segment.
	// A tag not equal to any of these is an "other".
	// is not part of the segment).
	Object[] segmentStartTags;
	Object[] segmentContinueTags;
	Object[] segmentStartOrContinueTags;
	
	private int evalIterations = 0;

	private int slice = 0;

  /** This class WILL NOT WORK if segmentStartTags and segmentContinueTags are the same!! */
  public MultiSegmentationEvaluatorACRF (Object[] segmentStartTags, Object[] segmentContinueTags, boolean showViterbi)
	{
		this.segmentStartTags = segmentStartTags;
		this.segmentContinueTags = segmentContinueTags;
		assert (segmentStartTags.length == segmentContinueTags.length);
	}

  /** This class WILL NOT WORK if segmentStartTags and segmentContinueTags are the same!! */
	public MultiSegmentationEvaluatorACRF (Object[] segmentStartTags, Object[] segmentContinueTags)
	{
    this(segmentStartTags, segmentContinueTags, true);
	}

	public MultiSegmentationEvaluatorACRF (Object[] segmentStartTags, Object[] segmentContinueTags, int slice)
	{
    this(segmentStartTags, segmentContinueTags, true);
    this.slice = slice;
	}

	private LabelSequence slice (LabelsSequence lseq, int k)
	{
		Label[] arr = new Label [lseq.size()];
		for (int i = 0; i < lseq.size(); i++) {
			arr [i] = lseq.getLabels (i).get (k);
		}
		return new LabelSequence (arr);
	}

	public boolean evaluate (ACRF acrf, int iter,
												InstanceList training,
												InstanceList validation,
												InstanceList testing)
	{
		// Don't evaluate if it is too early in training to matter
		if (!shouldDoEvaluate (iter)) return true;

		InstanceList[] lists = new InstanceList[] {training, validation, testing};
		String[] listnames = new String[] {"Training", "Validation", "Testing"};

		for (int k = 0; k < lists.length; k++)
      if (lists[k] != null) {
        test (acrf, lists[k], listnames[k]);
      }

    return true;
  }
	
  public void test(InstanceList gold, List returned, String description)
  {
    TestResults results = new TestResults (segmentStartTags, segmentContinueTags);

    for (int i = 0; i < gold.size(); i++) {
      Instance instance = gold.get(i);
      Sequence trueOutput = processTrueOutput ((Sequence) instance.getTarget());
      Sequence predOutput = slice ((LabelsSequence) returned.get (i), slice);
      assert (predOutput.size() == trueOutput.size());
      results.incrementCounts (trueOutput, predOutput);
    }

    results.logResults (description);
  }

  private Sequence processTrueOutput (Sequence sequence)
  {
    if (sequence instanceof LabelsSequence) {
      LabelsSequence lseq = (LabelsSequence) sequence;
      return slice (lseq, slice);
    } else {
      return sequence;
    }
  }

  public static class TestResults
  {

    private Object[] segmentStartTags, segmentContinueTags;

    private int numCorrectTokens, totalTokens;
    private int[] numTrueSegments, numPredictedSegments, numCorrectSegments;
    private int allIndex;

    public TestResults (Object[] segmentStartTags, Object[] segmentContinueTags)
    {
      this.segmentStartTags = segmentStartTags;
      this.segmentContinueTags = segmentContinueTags;
      allIndex = segmentStartTags.length;

      numTrueSegments = new int[allIndex+1];
      numPredictedSegments = new int[allIndex+1];
      numCorrectSegments = new int[allIndex+1];
      TokenSequence sourceTokenSequence = null;

      totalTokens = numCorrectTokens = 0;
      for (int n = 0; n < numTrueSegments.length; n++)
        numTrueSegments[n] = numPredictedSegments[n] = numCorrectSegments[n] = 0;
    }

    public void logResults (String description)
    {
      DecimalFormat f = new DecimalFormat ("0.####");
      logger.info (description +" tokenaccuracy="+f.format(((double)numCorrectTokens)/totalTokens));
      for (int n = 0; n < numCorrectSegments.length; n++) {
        logger.info ((n < allIndex ? segmentStartTags[n].toString() : "OVERALL") +' ');
        double precision = numPredictedSegments[n] == 0 ? 1 : ((double)numCorrectSegments[n]) / numPredictedSegments[n];
        double recall = numTrueSegments[n] == 0 ? 1 : ((double)numCorrectSegments[n]) / numTrueSegments[n];
        double f1 = recall+precision == 0.0 ? 0.0 : (2.0 * recall * precision) / (recall + precision);
        logger.info (" segments true="+numTrueSegments[n]+" pred="+numPredictedSegments[n]+" correct="+numCorrectSegments[n]+
                     " misses="+(numTrueSegments[n]-numCorrectSegments[n])+" alarms="+(numPredictedSegments[n]-numCorrectSegments[n]));
        logger.info (" precision="+f.format(precision)+" recall="+f.format(recall)+" f1="+f.format(f1));
      }
    }

    public void incrementCounts (Sequence trueOutput, Sequence predOutput)
    {
    int trueStart, predStart;				// -1 for non-start, otherwise index into segmentStartTag
    for (int j = 0; j < trueOutput.size(); j++) {
      totalTokens++;
      String trueToken = trueOutput.get(j).toString ();
      String predToken = predOutput.get(j).toString ();
      if (trueToken.equals (predToken)) {
        numCorrectTokens++;
      }
      trueStart = predStart = -1;
      // Count true segment starts
      for (int n = 0; n < segmentStartTags.length; n++) {
        if (segmentStartTags[n].equals(trueToken)) {
          numTrueSegments[n]++;
          numTrueSegments[allIndex]++;
          trueStart = n;
          break;
        }
      }
      // Count predicted segment starts
      for (int n = 0; n < segmentStartTags.length; n++) {
        if (segmentStartTags[n].equals(predOutput.get(j))) {
          numPredictedSegments[n]++;
          numPredictedSegments[allIndex]++;
          predStart = n;
        }
      }
      if (trueStart != -1 && trueStart == predStart) {
        // Truth and Prediction both agree that the same segment tag-type is starting now
        int m;
        boolean trueContinue = false;
        boolean predContinue = false;
        for (m = j+1; m < trueOutput.size(); m++) {
          String trueTokenCtd = trueOutput.get (m).toString ();
          String predTokenCtd = predOutput.get (m).toString ();
          trueContinue = segmentContinueTags[predStart].equals (trueTokenCtd);
          predContinue = segmentContinueTags[predStart].equals (predTokenCtd);
          if (!trueContinue || !predContinue) {
            if (trueContinue == predContinue) {
              // They agree about a segment is ending somehow
              numCorrectSegments[predStart]++;
              numCorrectSegments[allIndex]++;
            }
            break;
          }
        }
        // for the case of the end of the sequence
        if (m == trueOutput.size()) {
          if (trueContinue == predContinue) {
            numCorrectSegments[predStart]++;
            numCorrectSegments[allIndex]++;
          }
        }
      }
    }
    }

  }

}
