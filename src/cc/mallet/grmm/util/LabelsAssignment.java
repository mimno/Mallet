/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;


import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import cc.mallet.grmm.types.Assignment;
import cc.mallet.grmm.types.Variable;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Labels;
import cc.mallet.types.LabelsSequence;
import cc.mallet.types.Alphabet;
import cc.mallet.types.AlphabetCarrying;

import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;

/**
 * A special kind of assignment for Variables that
 * can be arranged in a LabelsSequence.  This is an Adaptor
 * to adapt LabelsSequences to Assignments.
 * <p/>
 * $Id: LabelsAssignment.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class LabelsAssignment extends Assignment implements AlphabetCarrying {

  // these are just for printing; race conditions don't matter
  private static int NEXT_ID = 0;
  private int id = NEXT_ID++;

  private Variable[][] idx2var;
  private LabelsSequence lblseq;
  private Map var2label;

  public LabelsAssignment (LabelsSequence lbls)
  {
    super ();
    this.lblseq = lbls;
    setupLabel2Var ();
    addRow (toVariableArray (), toValueArray ());
  }

  private Variable[] toVariableArray ()
  {
    List vars = new ArrayList (maxTime () * numSlices ());
    for (int t = 0; t < idx2var.length; t++) {
      for (int j = 0; j < idx2var[t].length; j++) {
        vars.add (idx2var[t][j]);
      }
    }
    return (Variable[]) vars.toArray (new Variable [vars.size ()]);
  }

  private int[] toValueArray ()
  {
    TIntArrayList vals = new TIntArrayList (maxTime () * numSlices ());
    for (int t = 0; t < lblseq.size (); t++) {
      Labels lbls = lblseq.getLabels (t);
      for (int j = 0; j < lbls.size (); j++) {
        Label lbl = lbls.get (j);
        vals.add (lbl.getIndex ());
      }
    }
    return vals.toNativeArray ();
  }

  private void setupLabel2Var ()
  {
    idx2var = new Variable [lblseq.size ()][];
    var2label = new THashMap ();
    for (int t = 0; t < lblseq.size (); t++) {
      Labels lbls = lblseq.getLabels (t);
      idx2var[t] = new Variable [lbls.size ()];
      for (int j = 0; j < lbls.size (); j++) {
        Label lbl = lbls.get (j);
        Variable var = new Variable (lbl.getLabelAlphabet ());
        var.setLabel ("I"+id+"_VAR[f=" + j + "][tm=" + t + "]");
        idx2var[t][j] = var;
        var2label.put (var, lbl);
      }
    }
  }

  public Variable varOfIndex (int t, int j)
  {
    return idx2var[t][j];
  }

  public Label labelOfVar (Variable var) { return (Label) var2label.get (var); }

  public int maxTime () { return lblseq.size (); }

  // assumes that lblseq not ragged
  public int numSlices () { return idx2var[0].length; }

  public LabelsSequence getLabelsSequence ()
  {
    return lblseq;
  }

  public LabelsSequence toLabelsSequence (Assignment assn)
  {
    int numFactors = numSlices ();
    int maxTime = maxTime ();
    Labels[] lbls = new Labels [maxTime];
    for (int t = 0; t < maxTime; t++) {
      Label[] theseLabels = new Label [numFactors];
      for (int i = 0; i < numFactors; i++) {
        Variable var = varOfIndex (t, i);
        int maxidx;

        if (var != null) {
          maxidx = assn.get (var);
        } else {
          maxidx = 0;
        }

        LabelAlphabet dict = labelOfVar (var).getLabelAlphabet ();
        theseLabels[i] = dict.lookupLabel (maxidx);
      }

      lbls[t] = new Labels (theseLabels);
    }

    return new LabelsSequence (lbls);
  }


  public LabelAlphabet getOutputAlphabet (int lvl)
  {
    return idx2var[0][lvl].getLabelAlphabet ();
  }

	public Alphabet getAlphabet() { return getOutputAlphabet(0); }
	public Alphabet[] getAlphabets() { return new Alphabet[] { getAlphabet() }; } //hack

}
