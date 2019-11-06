/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.Locale;

import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.MatrixOps;

/**
 * Created: Oct 8, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: PerFieldF1Evaluator.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class PerFieldF1Evaluator implements ExtractionEvaluator {

  private FieldComparator comparator = new ExactMatchComparator ();
  private PrintStream errorOutputStream = null;

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

    LabelAlphabet dict = extraction.getLabelAlphabet();
    int numLabels = dict.size();
    int[] numCorr = new int [numLabels];
    int[] numPred = new int [numLabels];
    int[] numTrue = new int [numLabels];

    for (int docnum = 0; docnum < numDocs; docnum++) {
      Record extracted = extraction.getRecord (docnum);
      Record target = extraction.getTargetRecord (docnum);

      // Calc precision
      Iterator it = extracted.fieldsIterator ();
      while (it.hasNext ()) {
        Field predField = (Field) it.next ();
        Label name = predField.getName ();
        Field trueField = target.getField (name);
        int idx = name.getIndex ();

        for (int j = 0; j < predField.numValues(); j++) {
          numPred [idx]++;
          if (trueField != null && trueField.isValue (predField.value (j), comparator)) {
            numCorr [idx]++;
          } else {
            // We have an error, report if necessary (this should be moved to the per-field rather than per-filler level.)
            if (errorOutputStream != null) {
              //xxx TODO: Display name of supporting document
              errorOutputStream.println ("Error in extraction!");
              errorOutputStream.println ("Predicted "+predField);
              errorOutputStream.println ("True "+trueField);
              errorOutputStream.println ();
            }
          }

        }
      }

      // Calc true
      it = target.fieldsIterator ();
      while (it.hasNext ()) {
        Field trueField = (Field) it.next ();
        Label name = trueField.getName ();
        numTrue [name.getIndex ()] += trueField.numValues ();
      }
    }

    out.println (description+" SEGMENT counts");
    out.println ("Name\tCorrect\tPred\tTarget");
    for (int i = 0; i < numLabels; i++) {
      Label name = dict.lookupLabel (i);
      out.println (name+"\t"+numCorr[i]+"\t"+numPred[i]+"\t"+numTrue[i]);
    }
    out.println ();

    DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
    DecimalFormat f = new DecimalFormat ("0.####", decimalFormatSymbols);

    double totalF1 = 0;
    int totalFields = 0;
    out.println (description+" per-field F1");
    out.println ("Name\tP\tR\tF1");
    for (int i = 0; i < numLabels; i++) {
      double P = (numPred[i] == 0) ? 0 : ((double)numCorr[i]) / numPred [i];
      double R = (numTrue[i] == 0) ? 1 : ((double)numCorr[i]) / numTrue [i];
      double F1 = (P + R == 0) ? 0 : (2 * P * R) / (P + R);
      if ((numPred[i] > 0) || (numTrue[i] > 0)) {
        totalF1 += F1;
        totalFields++;
      }
      Label name = dict.lookupLabel (i);
      out.println (name+"\t"+f.format(P)+"\t"+f.format(R)+"\t"+f.format(F1));
    }

    int totalCorr = MatrixOps.sum (numCorr);
    int totalPred = MatrixOps.sum (numPred);
    int totalTrue = MatrixOps.sum (numTrue);

    double P = ((double)totalCorr) / totalPred;
    double R = ((double)totalCorr) / totalTrue;
    double F1 = (2 * P * R) / (P + R);
    out.println ("OVERALL (micro-averaged) P="+f.format(P)+" R="+f.format(R)+" F1="+f.format(F1));
    out.println ("OVERALL (macro-averaged) F1="+f.format(totalF1/totalFields));
    out.println();
  }

}
