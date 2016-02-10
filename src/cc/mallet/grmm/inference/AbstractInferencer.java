/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.inference;

import cc.mallet.grmm.types.*;
import cc.mallet.grmm.util.Models;


/**
 * Abstract base class for inferencers.  This simply throws
 *  an UnsupportedOperationException for all methods, which
 *  is useful for subclasses that want to implement only
 *  specific inference functionality.
 *
 * Created: Mon Oct  6 17:01:21 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: AbstractInferencer.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
abstract public class AbstractInferencer implements Inferencer, Cloneable {

  public abstract void computeMarginals (FactorGraph fg);

  public double lookupJoint (Assignment assn)
  {
    return Math.exp (lookupLogJoint (assn));
  }

  public double lookupLogJoint (Assignment assn)
  {
    throw new UnsupportedOperationException
      (this.getClass().getName()+" doesn't compute joint probabilities.");
  }

  public Factor lookupMarginal (VarSet c)
  {
    switch (c.size()) {
      case 1:
        return lookupMarginal (c.get (0));

      default:
        throw new UnsupportedOperationException
          (this.getClass().getName()+" doesn't compute marginals of arbitrary cliques.");
    }
  }

  // TODO: Make destructive...
  public double query (FactorGraph mdl, Assignment assn)
  {
    // Computes joint of assignment using chain rule
    double marginal = 1.0;
    for (int i = 0; i < assn.size(); i++) {
      Variable var = assn.getVariable (i);
      computeMarginals (mdl);
      Factor ptl = lookupMarginal (var);
      marginal *= ptl.value (assn);
      mdl = Models.addEvidence (mdl, new Assignment (var, assn.get (var)));
    }
    return marginal;
  }

  abstract public Factor lookupMarginal(Variable variable);

  public Inferencer duplicate () {
    try {
      return (Inferencer) clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException (e);
    }
  }

  public void dump ()
  {
    throw new UnsupportedOperationException ();
  }

  public void reportTime ()
  {
    System.err.println ("AbstractInferencer: reportTime(): No report available.");
  }

  // Serialization garbage
  private static final long serialVersionUID = 1;

} // AbstractInferencer
