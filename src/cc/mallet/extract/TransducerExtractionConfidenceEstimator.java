/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.extract;


import java.io.*;
import java.util.*;

import cc.mallet.fst.*;
import cc.mallet.fst.confidence.*;
import cc.mallet.pipe.*;
import cc.mallet.types.*;

/**
 * Estimates the confidence in the labeling of a LabeledSpan using a
 * TransducerConfidenceEstimator.
 */
public class TransducerExtractionConfidenceEstimator extends ExtractionConfidenceEstimator implements Serializable
{
  TransducerConfidenceEstimator confidenceEstimator;
  Pipe featurePipe;

  public TransducerExtractionConfidenceEstimator (TransducerConfidenceEstimator confidenceEstimator,
                                                  Object[] startTags,
                                                  Object[] continueTags,
                                                  Pipe featurePipe) {
    super();
    this.confidenceEstimator = confidenceEstimator;
    this.featurePipe = featurePipe;
  }

  public void estimateConfidence (DocumentExtraction documentExtraction) {
    Tokenization input = documentExtraction.getInput();
    // WARNING: input Tokenization will likely already have many
    // features appended from the last time it was passed through a
    // featurePipe. To avoid a redundant calculation of features, the
    // caller may want to set this.featurePipe =
    // TokenSequence2FeatureVectorSequence
    Instance carrier = this.featurePipe.pipe(new Instance(input, null, null, null)); 

    Sequence pipedInput = (Sequence) carrier.getData();
    Sequence prediction = documentExtraction.getPredictedLabels();
    LabeledSpans labeledSpans = documentExtraction.getExtractedSpans();
    SumLatticeDefault lattice = new SumLatticeDefault (this.confidenceEstimator.getTransducer(), pipedInput);
    for (int i=0; i < labeledSpans.size(); i++) {
      LabeledSpan span = labeledSpans.getLabeledSpan(i);
      if (span.isBackground()) 
        continue;
      int[] segmentBoundaries = getSegmentBoundaries(input, span);
      Segment segment = new Segment(pipedInput, prediction, prediction, 
                                    segmentBoundaries[0], segmentBoundaries[1],
                                    null, null);
      span.setConfidence(confidenceEstimator.estimateConfidenceFor(segment, lattice));
    }
  }

  /** Convert the indices of a LabeledSpan into indices for a Tokenization. 
   * @return array of size two, where first index is start Token,
   * second is end Token, inclusive */
  private int[] getSegmentBoundaries (Tokenization tokens, LabeledSpan labeledSpan) {
    int startCharIndex = labeledSpan.getStartIdx();
    int endCharIndex = labeledSpan.getEndIdx()-1;
    int[] ret = new int[]{-1,-1};
    for (int i=0; i < tokens.size(); i++) {
      int charIndex = tokens.getSpan(i).getStartIdx();
      if (charIndex <= endCharIndex && charIndex >= startCharIndex) {
        if (ret[0] == -1) {
          ret[0] = i;
          ret[1] = i;
        }
        else
          ret[1] = i;
      }         
    }
    if (ret[0] == -1 || ret[1] == -1)
      throw new IllegalArgumentException("Unable to find segment boundaries from span " + labeledSpan);
    return ret;
  }
}
