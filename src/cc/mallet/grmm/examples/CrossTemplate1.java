/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.examples;

import cc.mallet.grmm.learning.ACRF;
import cc.mallet.grmm.types.Variable;
import cc.mallet.grmm.util.LabelsAssignment;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;

/**
 * $Id: CrossTemplate1.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class CrossTemplate1 extends ACRF.SequenceTemplate {

  private int lvl1 = 0;
  private int lvl2 = 1;

  public CrossTemplate1(int lvl1, int lvl2) {
    this.lvl1 = lvl1;
    this.lvl2 = lvl2;
  }

  protected void addInstantiatedCliques(ACRF.UnrolledGraph graph, FeatureVectorSequence fvs, LabelsAssignment lblseq) {
    for (int t = 0; t < lblseq.maxTime() - 1; t++) {
      try {
        Variable var1 = lblseq.varOfIndex(t, lvl1);
        Variable var2 = lblseq.varOfIndex(t + 1, lvl2);
        assert var2 != null : "Couldn't get label factor " + lvl2 + " time " + (t + 1);
        assert var1 != null : "Couldn't get label factor " + lvl1 + " time " + t;

        Variable[] vars = new Variable[]{var1, var2};
        FeatureVector fv = fvs.getFeatureVector(t);
        ACRF.UnrolledVarSet vs = new ACRF.UnrolledVarSet(graph, this, vars, fv);
        graph.addClique(vs);
      } catch (ArrayIndexOutOfBoundsException e) {
        throw e;
      }
    }
  }

}
