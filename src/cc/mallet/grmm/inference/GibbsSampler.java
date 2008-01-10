/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;

//import edu.umass.cs.mallet.users.casutton.util.Timing;


import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import cc.mallet.grmm.types.*;
import cc.mallet.util.Randoms;
import cc.mallet.util.Timing;

/**
 * Created: Mar 28, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: GibbsSampler.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class GibbsSampler implements Sampler {

  private int burnin;

  private Factor[] allCpts;

  private Randoms r = new Randoms (324231);

  public GibbsSampler () {}

  public GibbsSampler (int burnin)
  {
    this.burnin = burnin;
  }

  public GibbsSampler (Randoms r, int burnin)
  {
    this.burnin = burnin;
    this.r = r;
  }

  public void setBurnin (int burnin)
  {
    this.burnin = burnin;
  }

  public void setRandom (Randoms r)
  {
    this.r = r;
  }

  public Assignment sample (FactorGraph mdl, int N)
  {
//    initForGraph (mdl);
    Assignment assn = initialAssignment (mdl);
    if (assn == null)
	throw new IllegalArgumentException ("GibbsSampler: Could not find feasible assignment for model "+mdl);

    Timing timing = new Timing ();
    for (int i = 0; i < burnin; i++) {
      assn = doOnePass (mdl, assn);
    }
    timing.tick ("Burnin");

    Assignment ret = new Assignment ();
    for (int i = 0; i < N; i++) {
      assn = doOnePass (mdl, assn);
      ret.addRow (assn);
    }
    timing.tick ("Sampling");

    return ret;
  }

  private Assignment initialAssignment (FactorGraph mdl)
  {
    Assignment assn = new Assignment ();
    return initialAssignmentRec (mdl, assn, 0);
  }

  // backtracking search for a feasible assignment
  private Assignment initialAssignmentRec (FactorGraph mdl, Assignment assn, int fi)
  {
    if (fi >= mdl.factors ().size ()) return assn;
    Factor f = mdl.getFactor (fi);

    Factor sliced = f.slice (assn);
    if (sliced.varSet().isEmpty()) {
      double val = f.value (assn);
      if (val > 1e-50) {
	  return initialAssignmentRec (mdl, assn, fi+1);
      } else {
	return null;
      }
    }

    for (AssignmentIterator it = sliced.assignmentIterator (); it.hasNext ();) {
      double val = sliced.value (it);
      if (val > 1e-50) {
        Assignment new_assn = Assignment.union (assn, it.assignment());
        Assignment assn_ret = initialAssignmentRec (mdl, new_assn, fi+1);
        if (assn_ret != null) return assn_ret;
      }
      it.advance ();
    }

    return null;
  }

  private Assignment doOnePass (FactorGraph mdl, Assignment initial)
  {
    Assignment ret = (Assignment) initial.duplicate ();
    for (int vidx = 0; vidx < ret.size (); vidx++) {
      Variable var = mdl.get (vidx);
      DiscreteFactor subcpt = constructConditionalCpt (mdl, var, ret);
      int value = subcpt.sampleLocation (r);
      ret.setValue (var, value);
    }

    return ret;
  }

   // Warning: destructively modifies ret's assignment to fullAssn (I could save and restore, but I don't care
  private DiscreteFactor constructConditionalCpt (FactorGraph mdl, Variable var, Assignment fullAssn)
  {
    List ptlList = mdl.allFactorsContaining (var);
    LogTableFactor ptl = new LogTableFactor (var);
    for (AssignmentIterator it = ptl.assignmentIterator (); it.hasNext(); it.advance ()) {
      Assignment varAssn = it.assignment ();
      fullAssn.setValue (var, varAssn.get (var));
      ptl.setRawValue (varAssn, sumValues (ptlList, fullAssn));
    }
    ptl.normalize ();
    return ptl;
  }

  private double sumValues (List ptlList, Assignment assn)
  {
    double sum = 0;
    for (Iterator it = ptlList.iterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      sum += ptl.logValue (assn);
    }
    return sum;
  }

}
