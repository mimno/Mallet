/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;


import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import cc.mallet.grmm.types.*;
import cc.mallet.util.Randoms;

/**
 * Computes an exact sample from the distribution of a given factor graph by forming
 *  a junction tree.
 *
 * Created: Nov 9, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: ExactSampler.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class ExactSampler implements Sampler {

  Randoms r;

  public ExactSampler ()
  {
    this (new Randoms ());
  }

  public ExactSampler (Randoms r)
  {
    this.r = r;
  }

  public Assignment sample (FactorGraph mdl, int N)
  {
    JunctionTreeInferencer jti = new JunctionTreeInferencer ();
    jti.computeMarginals (mdl);
    JunctionTree jt = jti.lookupJunctionTree ();

    VarSet vs = mdl.varSet ();
    Assignment assns = new Assignment ();
    for (int i = 0; i < N; i++) {
      Assignment assn = sampleOneAssn (jt);
      assns.addRow (vs.toVariableArray (), reorderCols (assn, vs));
    }

    return assns;
  }

  private Object[] reorderCols (Assignment assn, VarSet vs)
  {
    Object[] vals = new Object [vs.size ()];
    for (int vi = 0; vi < vs.size(); vi++) {
      vals[vi] = assn.getObject (vs.get (vi));
    }
    return vals;
  }

  private Assignment sampleOneAssn (JunctionTree jt)
  {
    VarSet root = (VarSet) jt.getRoot ();
    return sampleAssignmentRec (jt, new Assignment (), root);
  }

  private Assignment sampleAssignmentRec (JunctionTree jt, Assignment assn, VarSet varSet)
  {
    Factor marg = jt.getCPF (varSet);
    Factor slice = marg.slice (assn);
    Assignment sampled = slice.sample (r);
    assn = Assignment.union (assn, sampled);
    for (Iterator it = jt.getChildren (varSet).iterator(); it.hasNext();) {
      VarSet child = (VarSet) it.next ();
      Assignment other = sampleAssignmentRec (jt, assn, child);
      assn = Assignment.union (assn, other);
    }
    return assn;
  }

  public void setRandom (Randoms r)
  {
    this.r = r;
  }
}
