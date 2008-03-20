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

package cc.mallet.fst;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.logging.Logger;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.MalletLogger;

public class MultiSegmentationEvaluator extends TransducerEvaluator
{
	private static Logger logger = MalletLogger.getLogger(SegmentationEvaluator.class.getName());


	// equals() is called on these objects to determine if this token is the start or continuation of a segment.
	// A tag not equal to any of these is an "other".
	// is not part of the segment).
	Object[] segmentStartTags;
	Object[] segmentContinueTags;
	Object[] segmentStartOrContinueTags;
	
	public MultiSegmentationEvaluator (InstanceList[] instanceLists, String[] instanceListDescriptions,
			Object[] segmentStartTags, Object[] segmentContinueTags)
	{
		super (instanceLists, instanceListDescriptions);
		this.segmentStartTags = segmentStartTags;
		this.segmentContinueTags = segmentContinueTags;
		assert (segmentStartTags.length == segmentContinueTags.length);
	}
	
	public MultiSegmentationEvaluator (InstanceList instanceList1, String description1,
			Object[] segmentStartTags, Object[] segmentContinueTags)
	{
		this (new InstanceList[] {instanceList1}, new String[] {description1},
				segmentStartTags, segmentContinueTags);
	}
	
	public MultiSegmentationEvaluator (InstanceList instanceList1, String description1,
			InstanceList instanceList2, String description2,
			Object[] segmentStartTags, Object[] segmentContinueTags)
	{
		this (new InstanceList[] {instanceList1, instanceList2}, new String[] {description1, description2},
				segmentStartTags, segmentContinueTags);
	}

	public MultiSegmentationEvaluator (InstanceList instanceList1, String description1,
			InstanceList instanceList2, String description2,
			InstanceList instanceList3, String description3,
			Object[] segmentStartTags, Object[] segmentContinueTags)
	{
		this (new InstanceList[] {instanceList1, instanceList2, instanceList3}, new String[] {description1, description2, description3},
				segmentStartTags, segmentContinueTags);
	}

	public void evaluateInstanceList (TransducerTrainer tt, InstanceList data, String description)
  {
  	Transducer model = tt.getTransducer();
    int numCorrectTokens, totalTokens;
    int[] numTrueSegments, numPredictedSegments, numCorrectSegments;
    int allIndex = segmentStartTags.length;
    numTrueSegments = new int[allIndex+1];
    numPredictedSegments = new int[allIndex+1];
    numCorrectSegments = new int[allIndex+1];

    totalTokens = numCorrectTokens = 0;
    for (int n = 0; n < numTrueSegments.length; n++)
      numTrueSegments[n] = numPredictedSegments[n] = numCorrectSegments[n] = 0;
    for (int i = 0; i < data.size(); i++) {
      Instance instance = data.get(i);
      Sequence input = (Sequence) instance.getData();
      //String tokens = null;
      //if (instance.getSource() != null)
      //tokens = (String) instance.getSource().toString();
      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = model.transduce (input);
      assert (predOutput.size() == trueOutput.size());
      int trueStart, predStart;				// -1 for non-start, otherwise index into segmentStartTag
      for (int j = 0; j < trueOutput.size(); j++) {
        totalTokens++;
        if (trueOutput.get(j).equals(predOutput.get(j)))
          numCorrectTokens++;
        trueStart = predStart = -1;
        // Count true segment starts
        for (int n = 0; n < segmentStartTags.length; n++) {
          if (segmentStartTags[n].equals(trueOutput.get(j))) {
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
            trueContinue = segmentContinueTags[predStart].equals (trueOutput.get(m));
            predContinue = segmentContinueTags[predStart].equals (predOutput.get(m));
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
    DecimalFormat f = new DecimalFormat ("0.####");
    logger.info (description +" tokenaccuracy="+f.format(((double)numCorrectTokens)/totalTokens));
    for (int n = 0; n < numCorrectSegments.length; n++) {
      logger.info ((n < allIndex ? segmentStartTags[n].toString() : "OVERALL") +' ');
      double precision = numPredictedSegments[n] == 0 ? 1 : ((double)numCorrectSegments[n]) / numPredictedSegments[n];
      double recall = numTrueSegments[n] == 0 ? 1 : ((double)numCorrectSegments[n]) / numTrueSegments[n];
      double f1 = recall+precision == 0.0 ? 0.0 : (2.0 * recall * precision) / (recall + precision);
      logger.info (" "+description+" segments true="+numTrueSegments[n]+" pred="+numPredictedSegments[n]+" correct="+numCorrectSegments[n]+
                   " misses="+(numTrueSegments[n]-numCorrectSegments[n])+" alarms="+(numPredictedSegments[n]-numCorrectSegments[n]));
      logger.info (" "+description+" precision="+f.format(precision)+" recall="+f.format(recall)+" f1="+f.format(f1));
    }

  }


	/**
		 returns the number of incorrect segments in <code>predOutput</code>
		 @param trueOutput truth
		 @param predOutput predicted
		 @return number of incorrect segments
	 */
	public int numIncorrectSegments (Sequence trueOutput, Sequence predOutput) {
    int numCorrectTokens, totalTokens;
    int[] numTrueSegments, numPredictedSegments, numCorrectSegments;
    int allIndex = segmentStartTags.length;
    numTrueSegments = new int[allIndex+1];
    numPredictedSegments = new int[allIndex+1];
    numCorrectSegments = new int[allIndex+1];
    totalTokens = numCorrectTokens = 0;
    for (int n = 0; n < numTrueSegments.length; n++)
      numTrueSegments[n] = numPredictedSegments[n] = numCorrectSegments[n] = 0;
		assert (predOutput.size() == trueOutput.size());
		// -1 for non-start, otherwise index into segmentStartTag
		int trueStart, predStart;				
		for (int j = 0; j < trueOutput.size(); j++) {
			totalTokens++;
			if (trueOutput.get(j).equals(predOutput.get(j)))
				numCorrectTokens++;
			trueStart = predStart = -1;
			// Count true segment starts
			for (int n = 0; n < segmentStartTags.length; n++) {
				if (segmentStartTags[n].equals(trueOutput.get(j))) {
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
					trueContinue = segmentContinueTags[predStart].equals (trueOutput.get(m));
					predContinue = segmentContinueTags[predStart].equals (predOutput.get(m));
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
		int wrong = 0;
		for (int n=0; n < numCorrectSegments.length; n++) {
			// incorrect segment is either false pos or false neg.
			wrong += numTrueSegments[n] - numCorrectSegments[n]; 
		}
		return wrong;
	}

	/**
		 Tests segmentation using an ArrayList of predicted Sequences
		 instead of a {@link Transducer}. If predictedSequence is null,
		 don't include in stats (useful for error analysis).
		 @param data list of instances to be segmented
		 @param predictedSequences predictions
		 @param description description of trial
		 @param viterbiOutputStream where to print the Viterbi paths
	 */
  public void batchTest(InstanceList data, List<Sequence> predictedSequences,
												String description, PrintStream viterbiOutputStream)
  {
    int numCorrectTokens, totalTokens;
    int[] numTrueSegments, numPredictedSegments, numCorrectSegments;
    int allIndex = segmentStartTags.length;
    numTrueSegments = new int[allIndex+1];
    numPredictedSegments = new int[allIndex+1];
    numCorrectSegments = new int[allIndex+1];
    TokenSequence sourceTokenSequence = null;

    totalTokens = numCorrectTokens = 0;
    for (int n = 0; n < numTrueSegments.length; n++)
      numTrueSegments[n] = numPredictedSegments[n] = numCorrectSegments[n] = 0;
    for (int i = 0; i < data.size(); i++) {
      if (viterbiOutputStream != null)
        viterbiOutputStream.println ("Viterbi path for "+description+" instance #"+i);
      Instance instance = data.get(i);
      Sequence input = (Sequence) instance.getData();			
      //String tokens = null;
      //if (instance.getSource() != null)
      //tokens = (String) instance.getSource().toString();
      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = (Sequence) predictedSequences.get (i);
			if (predOutput == null) // skip this instance
				continue;
      assert (predOutput.size() == trueOutput.size());
      int trueStart, predStart;				// -1 for non-start, otherwise index into segmentStartTag
      for (int j = 0; j < trueOutput.size(); j++) {
        totalTokens++;
        if (trueOutput.get(j).equals(predOutput.get(j)))
          numCorrectTokens++;
        trueStart = predStart = -1;
        // Count true segment starts
        for (int n = 0; n < segmentStartTags.length; n++) {
          if (segmentStartTags[n].equals(trueOutput.get(j))) {
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
            trueContinue = segmentContinueTags[predStart].equals (trueOutput.get(m));
            predContinue = segmentContinueTags[predStart].equals (predOutput.get(m));
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

        if (viterbiOutputStream != null) {
          FeatureVector fv = (FeatureVector) input.get(j);
          //viterbiOutputStream.println (tokens.charAt(j)+" "+trueOutput.get(j).toString()+
          //'/'+predOutput.get(j).toString()+"  "+ fv.toString(true));
          if (sourceTokenSequence != null)
            viterbiOutputStream.print (sourceTokenSequence.get(j).getText()+": ");
          viterbiOutputStream.println (trueOutput.get(j).toString()+
                                       '/'+predOutput.get(j).toString()+"  "+ fv.toString(true));
						
        }
      }
    }
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
}
