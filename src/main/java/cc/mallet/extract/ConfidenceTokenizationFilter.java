/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import cc.mallet.fst.confidence.*;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;

/**
 * Created: Oct 26, 2005
 *
 * @author <A HREF="mailto:culotta@cs.umass.edu>culotta@cs.umass.edu</A>
 */
public class ConfidenceTokenizationFilter implements TokenizationFilter, Serializable {

  ExtractionConfidenceEstimator confidenceEstimator;
  TokenizationFilter underlyingFilter;

  public ConfidenceTokenizationFilter (ExtractionConfidenceEstimator confidenceEstimator,
                                       TokenizationFilter underlyingFilter) {
    super();
    this.confidenceEstimator = confidenceEstimator;
    this.underlyingFilter = underlyingFilter;
  }
      
  public LabeledSpans constructLabeledSpans (LabelAlphabet dict, Object document, Label backgroundTag,
                                             Tokenization input, Sequence seq)
  {
    DocumentExtraction extraction = new DocumentExtraction("Extraction",
                                                           dict,
                                                           input,
                                                           seq,
                                                           null,
                                                           backgroundTag.toString());

    confidenceEstimator.estimateConfidence(extraction);
    return extraction.getExtractedSpans();
  }


  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.writeInt (CURRENT_SERIAL_VERSION);
    out.writeObject(confidenceEstimator);
    out.writeObject(underlyingFilter);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.readInt (); // read version
    this.confidenceEstimator = (ExtractionConfidenceEstimator) in.readObject();
    this.underlyingFilter = (TokenizationFilter) in.readObject();
  }
}
