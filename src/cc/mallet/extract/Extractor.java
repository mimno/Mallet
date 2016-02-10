/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.extract;


import java.io.Serializable;
import java.util.Iterator;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.PipeInputIterator;
import cc.mallet.types.Alphabet;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Instance;

// Analogous to base.classify.Classifier

/**
 * Generic interface for objects that do information extraction.
 *  Typically, this will mean extraction of database records
 *  (see @link{Record}) from Strings, but this interface is not
 *  specific to this case.
 */
//TODO: Possibly in the future, create Document and Corpus objects.
// (This would allow calling an extractor on multiple documents in a type-safe manner.
public interface Extractor extends Serializable
{
  /**
   * Performs extraction given a raw object.  The object will
   *  be passed through the Extractor's pipe.
   * @param o The document to extract from (often a String).
   * @return Extraction the results of performing extraction
   */
  public Extraction extract (Object o);

  /**
   * Performs extraction from an object that has been
   *  already been tokenized.  This method will pass spans
   *  through the extractor's pipe.
   * @param toks A tokenized document
   * @return Extraction the results of performing extraction
   */
  public Extraction extract (Tokenization toks);

  /**
   * Performs extraction on a a set of raw documents.  The
   *   Instances output from source will be passed through
   *   both the tokentization pipe and the feature extraction
   *   pipe.
   * @param source A source of raw documents
   * @return Extraction the results of performing extraction
   */
  public Extraction extract (Iterator<Instance> source);

  /**
   * Returns the pipe used by this extractor for.  The pipe
   *  takes an Instance and converts it into a form usable
   *  by the particular extraction algorithm.  This pipe expects
   *  the Instance's data field to be a Tokenization.  For example,
   *  pipes often perform feature extraction.  The type of
   *  raw object expected by the pipe depends on the particular
   *  subclass of extractor.
   * @return a pipe
   */
  public Pipe getFeaturePipe ();


  /**
   * Returns the pipe used by this extractor to tokenize the input.
   *  The type of Instance of this pipe expects is specific to the
   *  individual extractor.  This pipe will return an Instance whose
   *  data is a Tokenization.
   * @return a pipe
   */
  public Pipe getTokenizationPipe ();


  /**
   * Sets the pipe used by this extractor for tokenization.  The pipe should
   *  takes a raw object and convert it into a Tokenization.
   * <P>
   * The pipe @link{edu.umass.cs.mallet.base.pipe.CharSequence2TokenSequence} is an
   *  example of a pipe that could be used here.
   */
  public void setTokenizationPipe (Pipe pipe);

  /**
   * Returns an alphabet of the features used by the extractor.
   *   The alphabet maps strings describing the features to indices.
   * @return the input alphabet
   */
  public Alphabet getInputAlphabet ();

  /**
   * Returns an alphabet of the labels used by the extractor.
   *  Labels include entity types (such as PERSON) and slot
   *  names (such as EMPLOYEE-OF).
   * @return the target alphabet
   */
  public LabelAlphabet getTargetAlphabet ();
}
