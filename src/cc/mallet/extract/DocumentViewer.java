/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.ColorUtils;

/**
 * Diagnosis class that outputs HTML pages that allows you to view errors on a more
 *  global per-instance basis.
 *
 * Created: Mar 30, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: DocumentViewer.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class DocumentViewer {

  private static final String DOC_ERRS_CSS_FNAME = "docerrs.css";
  private static final String DOC_ERRS_PRED_CSS_FNAME = "docerrs-by-pred.css";
  private static final String DOC_ERRS_TRUE_CSS_FNAME = "docerrs-by-true.css";
  private static final double SATURATION = 0.4;

  private static class DualLabeledSpans {

    DualLabeledSpans (LabeledSpans ls1, LabeledSpans ls2) {
      ls = new LabeledSpans[] { ls1, ls2 };
    }

    private LabeledSpans[] ls;

    int size () { return ls[0].size(); }

    LabeledSpan get (int t, int i) { return ls[i].getLabeledSpan (t); }
  }

  /**
   * Writes several HTML files describing a given extraction.  Each HTML file shows an entire
   *  document, with the extracted fields color-coded.
   * @param directory Directory to write files to
   * @param extraction Extraction to describe
   * @throws IOException
   */

  public static void writeExtraction (File directory, Extraction extraction) throws IOException
  {
    outputIndex (directory, extraction);
    outputStylesheets (directory, extraction);
    outputDocuments (directory, extraction);
  }

  private static void outputStylesheets (File directory, Extraction extraction) throws IOException
  {
    // ERRS css
    PrintWriter out = new PrintWriter (new FileWriter (new File (directory, DOC_ERRS_CSS_FNAME)));
    out.println (".tf_legend { border-style: dashed; border-width: 2px; padding: 10px; padding-top: 0ex; float: right; margin:2em; }");
    out.println (".class_legend { visibility: hidden; }");
    out.println (".correct { background-color:#33FF33; }");
    out.println (".wrong { background-color:pink }");
    out.println (".true { background-color:#99CCFF; }");
    out.println (".pred { background-color:#FFFF66 }");
    out.close ();


    //PRED css
    LabelAlphabet dict = extraction.getLabelAlphabet ();
    String[] fields = determineFieldNames (dict);
    String[] colors = ColorUtils.rainbow (fields.length, (float) SATURATION, 1);
    out = new PrintWriter (new FileWriter (new File (directory, DOC_ERRS_PRED_CSS_FNAME)));
    out.println (".class_legend { border-style: dashed; border-width: 2px; padding: 10px; padding-top: 0ex; float: right; margin:2em; }");
    out.println (".tf_legend { visibility: hidden; }");
    for (int i = 0; i < fields.length; i++) {
      out.println (".pred_"+fields[i]+" { background-color:"+colors[i]+"; }");
    }
    out.close ();

    //TRUE css
    out = new PrintWriter (new FileWriter (new File (directory, DOC_ERRS_TRUE_CSS_FNAME)));
    out.println (".class_legend { border-style: dashed; border-width: 2px; padding: 10px; padding-top: 0ex; float: right; margin:2em; }");
    out.println (".tf_legend { visibility: hidden; }");
    for (int i = 0; i < fields.length; i++) {
      out.println (".true_"+fields[i]+" { background-color:"+colors[i]+"; }");
    }
    out.close ();
  }

  private static void outputDocuments (File directory, Extraction extraction) throws IOException
  {
    for (int i = 0; i < extraction.getNumDocuments (); i++) {
      PrintWriter out = new PrintWriter (new FileWriter (new File (directory, "extraction"+i+".html")));
      outputOneDocument (out, extraction.getDocumentExtraction (i));
      out.close ();
    }
  }

  private static void outputOneDocument (PrintWriter out, DocumentExtraction docExtr)
  {
    String name = docExtr.getName ();
    out.println ("<HTML><HEAD><TITLE>"+name+": Extraction from Document</TITLE>");
    out.println ("<LINK REL=\"stylesheet\" TYPE=\"text/css\" HREF=\""+DOC_ERRS_CSS_FNAME+"\" title=\"Agreement\" />");
    out.println ("<LINK REL=\"stylesheet\" TYPE=\"text/css\" HREF=\""+DOC_ERRS_PRED_CSS_FNAME+"\" title=\"Pred\" />");
    out.println ("<LINK REL=\"stylesheet\" TYPE=\"text/css\" HREF=\""+DOC_ERRS_TRUE_CSS_FNAME+"\" title=\"True\" />");
    out.println ("</HEAD><BODY>");

    outputClassLegend (out, docExtr.getExtractedSpans ().getLabeledSpan (0).getLabel ().getLabelAlphabet ());
    outputRightWrongLegend (out);

    DualLabeledSpans spans = intersectSpans (docExtr);
    for (int i = 0; i < spans.size(); i++) {
      LabeledSpan predSpan = spans.get (i, 0);
      LabeledSpan trueSpan = spans.get (i, 1);

      Label predLabel = predSpan.getLabel ();
      Label trueLabel = trueSpan.getLabel ();

      boolean predNonBgrnd = !predSpan.isBackground ();
      boolean trueNonBgrnd = !trueSpan.isBackground ();
      boolean isBackground = !predNonBgrnd && !trueNonBgrnd;
      
      String spanClass = null;
      if (predNonBgrnd && trueNonBgrnd) {
        if (predLabel == trueLabel) {
          spanClass = "correct";
        } else {
          spanClass = "wrong";
        }
      } else if (predNonBgrnd) {
        spanClass = "pred";
      } else if (trueNonBgrnd) {
        spanClass = "true";
      }

      if (!isBackground) out.print ("<SPAN CLASS=\"pred_"+predLabel+"\">");
      if (!isBackground) out.print ("<SPAN CLASS=\"true_"+trueLabel+"\">");
      if (spanClass != null) { out.print ("<SPAN CLASS=\""+spanClass+"\">"); }

      String text = predSpan.getSpan ().getText ();
      text = text.replaceAll ("<", "&lt;");
      text = text.replaceAll ("\n", "\n<P>");
      out.print (text);

      if (spanClass != null) { out.print ("</SPAN>"); }
      if (!isBackground) out.print ("</SPAN></SPAN>");
      out.println ();
    }

    out.println ("</BODY></HTML>");
  }

  private static void outputRightWrongLegend (PrintWriter out)
  {
    out.println ("<DIV CLASS=\"tf_legend\"><B>LEGEND</B><BR>");
    out.println ("<SPAN CLASS='correct'>Correct</SPAN><BR />");
    out.println ("<SPAN CLASS='wrong'>Wrong</SPAN><BR />");
    out.println ("<SPAN CLASS='true'>False Negative</SPAN> (True field but predicted background)<BR />");
    out.println ("<SPAN CLASS='pred'>False Positive</SPAN> (True background but predicted field)<BR />");
    out.println ("</DIV>");
  }
  private static void outputClassLegend (PrintWriter out, LabelAlphabet dict)
  {
    out.println ("<DIV CLASS=\"class_legend\">");
    out.println ("<H4>LEGEND</H4>");
    String[] fields = determineFieldNames (dict);
    String[] colors = ColorUtils.rainbow (fields.length, (float) SATURATION, 1);
    for (int i = 0; i < fields.length; i++) {
      out.println ("<SPAN STYLE=\"background-color:"+colors[i]+"\">"+fields[i]+"</SPAN><BR />");
    }
    out.println ("</DIV>");
  }

  private static String[] determineFieldNames (LabelAlphabet dict)
  {
    List l = new ArrayList ();
    for (int i = 0; i < dict.size (); i++) {
      String lname = dict.lookupLabel (i).toString ();
      if (!lname.startsWith ("B-") && !lname.startsWith ("I-")) {
        l.add (lname);
      }
    }
    return (String[]) l.toArray (new String [l.size ()]);
  }

  private static DualLabeledSpans intersectSpans (DocumentExtraction docExtr)
  {
    int predIdx = 0;
    int trueIdx = 0;
    LabeledSpans trueSpans = docExtr.getTargetSpans ();
    LabeledSpans predSpans = docExtr.getExtractedSpans ();

    LabeledSpans retPredSpans = new LabeledSpans (predSpans.getDocument ());
    LabeledSpans retTrueSpans = new LabeledSpans (predSpans.getDocument ());

    while ((predIdx < predSpans.size()) && (trueIdx < trueSpans.size ())) {
      LabeledSpan predSpan = predSpans.getLabeledSpan (predIdx);
      LabeledSpan trueSpan = trueSpans.getLabeledSpan (trueIdx);

      LabeledSpan newPredSpan = (LabeledSpan) predSpan.intersection (trueSpan);
      LabeledSpan newTrueSpan = (LabeledSpan) trueSpan.intersection (predSpan);
      retPredSpans.add (newPredSpan);
      retTrueSpans.add (newTrueSpan);

      if (predSpan.getEndIdx () <= trueSpan.getEndIdx ()) {
        predIdx++;
      }
      if (trueSpan.getEndIdx () <= predSpan.getEndIdx ()) {
        trueIdx++;
      }
    }

    assert (retPredSpans.size() == retTrueSpans.size());

    return new DualLabeledSpans (retPredSpans, retTrueSpans);
  }

  private static void outputIndex (File directory, Extraction extraction) throws IOException
  {
    PrintWriter out = new PrintWriter (new FileWriter (new File (directory, "index.html")));
    out.println ("<HTML><HEAD><TITLE>Extraction Results</TITLE></HEAD><BODY><OL>");
    for (int i = 0; i < extraction.getNumDocuments(); i++) {
      String name = extraction.getDocumentExtraction (i).getName ();
      out.println ("  <LI><A HREF=\"extraction"+i+".html\">"+name+"</A></LI>");
    }
    out.println ("</OL></BODY></HTML>");
    out.close ();
  }
}
