/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;


import java.io.*;
import java.text.DecimalFormat;
import java.util.List;

import cc.mallet.fst.CRF;
import cc.mallet.fst.MaxLattice;
import cc.mallet.fst.MaxLatticeDefault;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.fst.Transducer;
import cc.mallet.types.*;

/**
 * Created: Oct 31, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: LatticeViewer.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class LatticeViewer {

  private static final int FEATURE_CUTOFF_PCT = 25;
  private static final int LENGTH = 10;

  static void lattice2html (PrintStream out, ExtorInfo info)
  {
    PrintWriter writer = new PrintWriter (new OutputStreamWriter (out), true);
    lattice2html (writer, info);
  }

  // if lattice == null, no alpha, beta values printed
  static void lattice2html (PrintWriter out, ExtorInfo info)
  {
    assert (info.target.size() == info.predicted.size());
    assert (info.input.size() == info.predicted.size());

    int N = info.target.size();
    for (int start = 0; start < N; start += LENGTH - 1) {
      int end = Math.min (N, start + LENGTH);
      if (!allSeqMatches (info.predicted, info.target, start, end)) {
        error2html (out, info, start, end);
      }
    }
  }


  private static void writeHeader (PrintWriter out)
  {
    out.println ("<html><head><title>ERROR OUTPUT</title>\n<link rel=\"stylesheet\" href=\"errors.css\" type=\"text/css\" />\n</head><body>");
  }


  private static void writeFooter (PrintWriter out)
  {
    out.println ("</body></html>");
  }



  // Display HTML for one error
  private static void error2html (PrintWriter out, ExtorInfo info, int start, int end)
  {
    String anchor = info.idx+":"+start+":"+end;
    out.println ("<p><A NAME=\""+anchor+"\">");
    out.println ("<p>Instance "+info.desc+" Position "+start+"..."+end);
    if (info.link != null) {
      out.println ("<a href=\""+info.link+"#"+anchor+"\">[Lattice]</a>");
    }
    out.println ("</p>");
    out.println ("<table>");


    outputIndices (out, start, end);
    outputInputRow (out, info.input,  start, end);
    outputTableRow (out, "target", info.target, info.predicted,  start, end);
    outputTableRow (out, "predicted", info.predicted, info.target, start, end);
    if (info.lattice != null) {
      outputLatticeRows (out, info.lattice, start, end);
      outputTransitionCosts (out, info, start, end);
      outputFeatures (out, info.fvs, info.predicted, info.target, start, end);
    }
      out.println ("</table>");
  }


  public static int numMaxViterbi = 5;

  private static void outputLatticeRows (PrintWriter out, MaxLattice lattice, int start, int end)
  {
    DecimalFormat f = new DecimalFormat ("0.##");
    Transducer ducer = lattice.getTransducer ();
    int max = Math.min (numMaxViterbi, ducer.numStates());
    List<Sequence<Transducer.State>> stateSequences = lattice.bestStateSequences(max);
    for (int k = 0; k < max; k++) {
      out.println ("  <tr class=\"delta\">");
      out.println ("    <td class=\"label\">&delta; rank "+k+"</td>");
      for (int ip = start; ip < end; ip++) {
          Transducer.State state = stateSequences.get(k).get(ip+1);
        if (state.getName().equals (lattice.bestOutputSequence().get(ip))) {
          out.print ("<td class=\"viterbi\">");
        } else {
          out.print ("<td>");
        }
        out.print (state.getName()+"<br />"+f.format (-lattice.getDelta (ip+1, state.getIndex ()))+"</td>");
      }
      out.println ("</tr>");
    }
  }


  private static int numFeaturesToDisplay = 5;


  public static int getNumFeaturesToDisplay ()
  {
    return numFeaturesToDisplay;
  }


  public static void setNumFeaturesToDisplay (int numFeaturesToDisplay)
  {
    LatticeViewer.numFeaturesToDisplay = numFeaturesToDisplay;
  }


  private static void outputTransitionCosts (PrintWriter out, ExtorInfo info, int start, int end)
  {
    Transducer ducer = info.lattice.getTransducer ();

    out.println ("<tr class=\"predtrans\">");
    out.println ("<td class=\"label\">Cost(pred. trans)</td>");
    for (int ip = start; ip < end; ip++) {
      if (ip == 0) {
        out.println ("<td></td>");
        continue;
      }
      Transducer.State from = ((CRF) ducer).getState (info.bestStates.get (ip - 1).toString ());
      Transducer.TransitionIterator iter = from.transitionIterator (info.fvs, ip, info.predicted, ip);
      if (iter.hasNext ()) {
        iter.next ();
        double cost = iter.getWeight();
        String str = iter.describeTransition ((int) (Math.abs(cost) / FEATURE_CUTOFF_PCT));
        out.print ("<td>" + str + "</td>");
      } else {
        out.print ("<td>No matching transition</td>");
      }
    }
    out.println ("</tr>");

    out.println ("<tr class=\"targettrans\">");
    out.println ("<td class=\"label\">Cost(target trans)</td>");
    for (int ip = start; ip < end; ip++) {
      if (ip == 0) {
        out.println ("<td></td>");
        continue;
      }
      if (!seqMatches (info.predicted, info.target, ip) || !seqMatches (info.predicted, info.target, ip - 1)) {
        Transducer.State from = ((CRF) ducer).getState (info.target.get (ip - 1).toString ());
        if (from == null) {
          out.println ("<td colspan='"+(end-start)+"'>Could not find state for "+info.target.get(ip-1)+"</td>");
        } else {
          Transducer.TransitionIterator iter = from.transitionIterator (info.fvs, ip, info.target, ip);
          if (iter.hasNext ()) {
            iter.next ();
            double cost = iter.getWeight();
            String str = iter.describeTransition ((int) (Math.abs(cost) / FEATURE_CUTOFF_PCT));
            out.print ("<td>" + str + "</td>");
          } else {
            out.print ("<td>No matching transition</td>");
          }
        }
      } else {
        out.print ("<td></td>");
      }
    }
    out.println ("</tr>");


    out.println ("<tr class=\"predtargettrans\">");
    out.println ("<td class=\"label\">Cost (pred->target trans)</td>");
    for (int ip = start; ip < end; ip++) {
      if (ip == 0) {
        out.println ("<td></td>");
        continue;
      }
      if (!seqMatches (info.predicted, info.target, ip) || !seqMatches (info.predicted, info.target, ip - 1)) {
        Transducer.State from = ((CRF) ducer).getState (info.bestStates.get (ip - 1).toString ());
        Transducer.TransitionIterator iter = from.transitionIterator (info.fvs, ip, info.target, ip);
        if (iter.hasNext ()) {
          iter.next ();
          double cost = iter.getWeight();
          String str = iter.describeTransition ((int) (Math.abs(cost) / FEATURE_CUTOFF_PCT));
          out.print ("<td>" + str + "</td>");
        } else {
          out.print ("<td>No matching transition</td>");
        }
      } else {
        out.print ("<td></td>");
      }
    }
    out.println ("</tr>");

  }



  private static void outputLatticeRows (PrintWriter out, SumLatticeDefault lattice, int start, int end)
  {
    DecimalFormat f = new DecimalFormat ("0.##");
    Transducer ducer = lattice.getTransducer ();
    for (int k = 0; k < ducer.numStates(); k++) {
      Transducer.State state = ducer.getState (k);
      out.println ("  <tr class=\"alpha\">");
      out.println ("    <td class=\"label\">&alpha;("+state.getName()+")</td>");
      for (int ip = start; ip < end; ip++) {
          out.print ("<td>"+f.format (lattice.getAlpha (ip+1, state))+"</td>");
      }
      out.println ("</tr>");
    }
    for (int k = 0; k < ducer.numStates(); k++) {
      Transducer.State state = ducer.getState (k);
      out.println ("  <tr class=\"beta\">");
      out.println ("    <td class=\"label\">&beta;("+state.getName()+")</td>");
      for (int ip = start; ip < end; ip++) {
          out.print ("<td>"+f.format (lattice.getBeta (ip+1, state))+"</td>");
      }
      out.println ("</tr>");
    }
    for (int k = 0; k < ducer.numStates(); k++) {
      Transducer.State state = ducer.getState (k);
      out.println ("  <tr class=\"gamma\">");
      out.println ("    <td class=\"label\">&gamma;("+state.getName()+")</td>");
      for (int ip = start; ip < end; ip++) {
          out.print ("<td>"+f.format (lattice.getGammaWeight(ip+1, state))+"</td>");
      }
      out.println ("</tr>");
    }
  }


  private static void outputInputRow (PrintWriter out, TokenSequence input, int start, int end)
  {
    out.println ("  <tr class=\"input\">");
    out.println ("    <td class=\"label\"></td>");
    for (int ip = start; ip < end; ip++) {
      out.print ("<td>"+input.get(ip).getText()+"</td>");
    }
    out.println ("  </tr>");
  }


  private static void outputIndices (PrintWriter out, int start, int end)
  {
    out.println ("  <tr class=\"indices\">");
    out.println ("    <td class=\"label\"></td>");
    for (int ip = start; ip < end; ip++) {
      out.print ("<td>"+ip+"</td>");
    }
    out.println ("  </tr>");
  }


  private static void outputTableRow (PrintWriter out, String cssClass, Sequence seq1, Sequence seq2, int start, int end)
  {
    out.println ("  <tr class=\""+cssClass+"\">");
    out.println ("    <td class=\"label\">"+cssClass+"</td>");
    for (int i = start; i < end; i++) {
      if (seqMatches (seq1, seq2, i)) {
        out.print ("<td>");
      } else {
        out.print ("<td class=\"error\">");
      }
      out.print (seq1.get(i));
      out.print ("</td>");
    }
    out.println ("  </tr>");
  }

  private static void outputFeatures (PrintWriter out, FeatureVectorSequence fvs, Sequence in, Sequence output, int start, int end)
  {
    out.println ("  <tr class=\"features\">\n<td class=\"label\">Features</td>");
    for (int i = start; i < end; i++) {
      if (!seqMatches (in, output, i)) {
        out.print ("<td>");
        FeatureVector fv = fvs.getFeatureVector (i);
        for (int k = 0; k < fv.numLocations (); k++) {
          out.print (fv.getAlphabet ().lookupObject (fv.indexAtLocation (k)));
          if (fv.valueAtLocation (k) != 1.0) {
            out.print (" "+fv.valueAtLocation (k));
          }
          out.println ("<br />");

        }
        out.println ("</td>");
      } else {
        out.println ("<td></td>");
      }
    }
    out.println ("  </tr>");
  }

  private static boolean seqMatches (Sequence seq1, Sequence seq2, int i)
  {
    return seq1.get(i).toString().equals (seq2.get(i).toString());
  }

  private static boolean allSeqMatches (Sequence seq1, Sequence seq2, int start, int end)
  {
    for (int i = start; i < end; i++) {
      if (!seqMatches (seq1, seq2, i)) return false;
    }
    return true;
  }

  public static void extraction2html (Extraction extraction, CRFExtractor extor, PrintStream out)
  {
    PrintWriter writer = new PrintWriter (new OutputStreamWriter (out), true);
    extraction2html (extraction, extor, out, false);
  }

  public static void extraction2html (Extraction extraction, CRFExtractor extor, PrintWriter out)
  {
    extraction2html (extraction, extor, out, false);
  }

  public static void extraction2html (Extraction extraction, CRFExtractor extor, PrintStream out, boolean showLattice)
  {
    PrintWriter writer = new PrintWriter (new OutputStreamWriter (out), true);
    extraction2html (extraction, extor, writer, showLattice);
  }

  public static void extraction2html (Extraction extraction, CRFExtractor extor, PrintWriter out, boolean showLattice)
  {
    writeHeader (out);
    for (int i = 0; i < extraction.getNumDocuments (); i++) {
      DocumentExtraction docextr = extraction.getDocumentExtraction (i);
      String desc = docextr.getName();
      String doc = ((CharSequence) docextr.getDocument ()).toString();
      ExtorInfo info = infoForDoc (doc, desc, "N"+i, docextr, extor, showLattice);
      if (!showLattice) info.link = "lattice.html";
      lattice2html (out, info);
    }
    writeFooter (out);
  }


  private static class ExtorInfo {
    TokenSequence input;
    Sequence predicted;
    LabelSequence target;
    FeatureVectorSequence fvs;
    MaxLattice lattice;
    Sequence bestStates;
    String link; // If non-null, name of HTML file to use for cross-links
    String desc;
    String idx;


    public ExtorInfo (TokenSequence input, Sequence predicted, LabelSequence target, String desc, String idx)
    {
      this.input = input;
      this.predicted = predicted;
      this.target = target;
      this.desc = desc;
      this.idx = idx;
    }
  }

  private static ExtorInfo infoForDoc (String doc, String desc, String idx, DocumentExtraction docextr,
                                         CRFExtractor extor, boolean showLattice)
  {
//    Instance c2 = new Instance (doc, null, null, null, extor.getTokenizationPipe ());
//    TokenSequence input = (TokenSequence) c2.getData ();
    TokenSequence input = (TokenSequence) docextr.getInput (); 
    LabelSequence target = docextr.getTarget ();
    Sequence predicted = docextr.getPredictedLabels ();

    ExtorInfo info = new ExtorInfo (input, predicted, target, desc, idx);

    if (showLattice == true) {
      CRF crf = extor.getCrf();
      // xxx perhaps the next two lines could be a transducer method???
      Instance carrier = extor.getFeaturePipe().pipe(new Instance (input, null, null, null));
      info.fvs = (FeatureVectorSequence) carrier.getData ();
      info.lattice = new MaxLatticeDefault (crf, (Sequence) carrier.getData(), null);
      info.bestStates = info.lattice.bestOutputSequence();
    }

    return info;
  }


  // Lattice files get too large if too many instances are written to one file
  private static final int EXTRACTIONS_PER_FILE = 25;

  public static void viewDualResults (File dir, Extraction e1, CRFExtractor extor1, Extraction e2, CRFExtractor extor2) throws IOException
  {
    if (e1.getNumDocuments () != e2.getNumDocuments ())
      throw new IllegalArgumentException ("Extractions don't match: different number of docs.");

    PrintWriter errorStr = new PrintWriter (new FileWriter (new File (dir, "errors.html")));
    writeDualExtractions (errorStr, e1, extor1, e2, extor2, 0, e1.getNumDocuments (), false);
    errorStr.close ();

    int max = e1.getNumDocuments ();
    for (int start = 0; start < max; start += EXTRACTIONS_PER_FILE) {
      int end = Math.min (start + EXTRACTIONS_PER_FILE, max);
      PrintWriter latticeStr = new PrintWriter (new FileWriter (new File (dir, "lattice-"+start+".html")));
      writeDualExtractions (latticeStr, e1, extor1, e2, extor2, start, end, true);
      latticeStr.close ();
    }
  }

  private static String computeLatticeFname (int docIdx)
  {
    int htmlDocNo = docIdx / EXTRACTIONS_PER_FILE; // this will get integer truncated
    int start = EXTRACTIONS_PER_FILE * htmlDocNo;
    return "lattice-"+start+".html";
  }

  private static void writeDualExtractions (PrintWriter out, Extraction e1, CRFExtractor extor1, Extraction e2, CRFExtractor extor2,
                                           int start, int end, boolean showLattice)
  {
    writeHeader (out);

    for (int i = start; i < end; i++) {
      DocumentExtraction doc1 = e1.getDocumentExtraction (i);
      DocumentExtraction doc2 = e2.getDocumentExtraction (i);

      String desc = doc1.getName();
      String doc1Str = ((CharSequence) doc1.getDocument ()).toString();
      String doc2Str = ((CharSequence) doc2.getDocument ()).toString();
      if (!doc1Str.equals (doc2Str)) {
        System.err.println ("Skipping document "+i+": Extractions don't match");
        continue;
      }

      Sequence targ1 = doc1.getPredictedLabels ();
      Sequence targ2 = doc2.getPredictedLabels ();
      if (!predictionsMatch (targ1, targ2)) {
       ExtorInfo info1 = infoForDoc (doc1Str, "CRF1::"+desc, "C1I"+i, doc1, extor1, showLattice);
       ExtorInfo info2 = infoForDoc (doc1Str, "CRF2::"+desc, "C2I"+i, doc2, extor2, showLattice);
        if (!showLattice) { // add links from errors.html --> lattice.html
          info1.link = info2.link = computeLatticeFname (i);
        }
       dualLattice2html (out, desc, info1, info2);
      }
    }

    writeFooter (out);
  }


  // if lattice == null, no alpha, beta values printed
  public static void dualLattice2html (PrintWriter out, String desc, ExtorInfo info1, ExtorInfo info2)
  {
    assert (info1.predicted.size() == info1.target.size());
    assert (info1.input.size() == info1.predicted.size());
    assert (info2.input.size() == info2.predicted.size());
    assert (info2.predicted.size() == info2.target.size());

    int N = info1.target.size();
    for (int start = 0; start < N; start += LENGTH - 1) {
      int end = Math.min (info1.predicted.size(), start + LENGTH);
      if (!allSeqMatches (info1.predicted, info2.predicted, start, end)) {
        error2html (out, info1, start, end);
        error2html (out, info2, start, end);
      }
    }
  }

  private static boolean predictionsMatch (Sequence targ1, Sequence targ2)
  {
    if (targ1.size() != targ2.size()) return false;
    for (int i = 0; i < targ1.size(); i++)
      if (!targ1.get(i).toString().equals (targ2.get(i).toString()))
        return false;
    return true;
  }

}
