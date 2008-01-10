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

/**
 * Estimates the confidence in the labeling of a LabeledSpan.
 */
abstract public class ExtractionConfidenceEstimator
{
  public void estimateConfidence (Extraction extraction) {
    for (int i=0; i < extraction.getNumDocuments(); i++) 
      estimateConfidence(extraction.getDocumentExtraction(i));
  }

  abstract public void estimateConfidence (DocumentExtraction documentExtraction);
}
