/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.io.PrintStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Vector;

import cc.mallet.fst.confidence.ConfidenceEvaluator;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.MatrixOps;

/**
 * Constructs Accuracy-coverage graph using confidence values to sort Fields.
 *
 * Created: Nov 8, 2005
 *
 * @author <A HREF="mailto:culotta@cs.umass.edu>culotta@cs.umass.edu</A>
 */
public class AccuracyCoverageEvaluator implements ExtractionEvaluator {

  private int numberBins;
  private FieldComparator comparator = new ExactMatchComparator ();
  private PrintStream errorOutputStream = null;

  public AccuracyCoverageEvaluator (int numberBins) {
    this.numberBins = 20;
  }

  public FieldComparator getComparator ()
  {
    return comparator;
  }

  public void setComparator (FieldComparator comparator)
  {
    this.comparator = comparator;
  }

  public PrintStream getErrorOutputStream ()
  {
    return errorOutputStream;
  }

  public void setErrorOutputStream (OutputStream errorOutputStream)
  {
    this.errorOutputStream = new PrintStream (errorOutputStream);
  }

  public void evaluate (Extraction extraction)
  {
    evaluate ("", extraction, System.out);
  }

  // Assumes that there are as many records as documents, indexed by docs.
  // Assumes that extractor returns at most one value
  public void evaluate (String description, Extraction extraction, PrintStream out)
  {
    int numDocs = extraction.getNumDocuments ();
    assert numDocs == extraction.getNumRecords ();

    Vector entityConfidences = new Vector();
    int numTrueValues = 0;
    int numPredValues = 0;
    int numCorrValues = 0;
    for (int docnum = 0; docnum < numDocs; docnum++) {
      Record extracted = extraction.getRecord (docnum);
      Record target = extraction.getTargetRecord (docnum);

      Iterator it = extracted.fieldsIterator ();
      while (it.hasNext ()) {
        Field predField = (Field) it.next ();
        Field trueField = target.getField (predField.getName());
        if (predField != null) numPredValues += predField.numValues();
        for (int j = 0; j < predField.numValues(); j++) {
          LabeledSpan span = predField.span(j);
          boolean correct = (trueField != null && trueField.isValue (predField.value (j), comparator));
          entityConfidences.add(new ConfidenceEvaluator.EntityConfidence
                                (span.getConfidence(), correct, span.getText()));          
          if (correct)
            numCorrValues++;
        }
      }

      it = target.fieldsIterator ();
      while (it.hasNext ()) {
        Field trueField = (Field) it.next ();
        numTrueValues += trueField.numValues ();
      }


      
    }
    
    ConfidenceEvaluator evaluator = new ConfidenceEvaluator(entityConfidences, this.numberBins);
    out.println("correlation: " + evaluator.correlation());
    out.println("avg precision: " + evaluator.getAveragePrecision());
    out.println("coverage\taccuracy:\n" + evaluator.accuracyCoverageValuesToString());    
    double[] ac = evaluator.getAccuracyCoverageValues();
    for (int i=0; i < ac.length; i++) {
      int marks = (int)(ac[i]*25.0);
      for (int j=0; j < marks; j++)
        out.print("*");
      out.println();
    }

    out.println("nTrue:" + numTrueValues + " nCorr:" + numCorrValues + " nPred:" + numPredValues + "\n");
    out.println("recall\taccuracy:\n" + evaluator.accuracyRecallValuesToString(numTrueValues));
  }
}
