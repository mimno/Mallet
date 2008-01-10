/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types; // Generated package name


import java.io.Serializable;
import java.util.Collection;

import cc.mallet.util.Randoms;



/**
 *  Interface for multivariate discrete probability distributions.
 *   All distributions are assumed to be over
 *   0...k.  If you want a distribution over some
 *   other discrete set, use the @see getAlphabet
 *   and @see setAlphabet members.
 *
 *  (I thought about having a single Potential interface,
 *   for both continuous and discrete, but then all the method
 *   parameters were java.lang.Object, and the lack of type
 *   safety was both inefficient and disturbing.)
 *
 * Created: Mon Sep 15 14:04:58 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: Factor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public interface Factor extends Cloneable, Serializable {
    
  /**
   *  Returns the value of the local function for a given assignment.
	 *   All variables in the potential must be included, but it's
	 *   okay if the assignment uses variables not in the potential.
	 */
	public double value (Assignment assn);
	
	/**
	 *  Returns the probability of an assignment to these variables.
	 *   The assignment used is the curret assignment from the given
	 *   AssignmentIterator.
   * <p>
	 *  This can be used to do things like
	 * <pre>
	 DiscretePotential phi = createMyPtl ();
	 for (AssignmentIterator it = phi.assignmentIterator; it.hasNext(); it.advance()) {
	   double val = ptl.phi (it);
		 // do something with val
   }
  </pre>
  * <p>
	*  This is equivalent to creating an assignment object explicitly
	*   using <tt>(Assignment) it.next()</tt>, but can be much faster.
	*/
	public double value (AssignmentIterator it);

	/**
	 *  Multiplies this potential by a constant such that it sums to 1.
   *   Destructive; returns this factor.
	 */
	public Factor normalize ();

	/**
	 * Returns the marginal of this distribution over the given variables.
	 */
	public Factor marginalize (Variable vars[]);

	/**
	 * Returns the marginal of this distribution over the given variables.
	 */
	public Factor marginalize (Collection vars);

	/**
	 * Returns the marginal of this distribution over one variable.
	 */
	public Factor marginalize (Variable var);

	/**
	 * Returns the marginal distribution attained by summing out
	 *  the given variable.
	 */
	public Factor marginalizeOut (Variable var);

  /**
	 * Returns the marginal distribution attained by summing out
	 *  the given set of variables.
	 */
	public Factor marginalizeOut (VarSet varset);

	/**
	 *  Returns a potential phi over the given variables
	 *   obtained by taking 
	 *  phi (x) = max_[all v that contain x] this.prob (x)
	 */
	public Factor extractMax (Collection vars);

	/**
	 *  Returns a potential phi over the given variables
	 *   obtained by taking 
	 *  phi (x) = max_[all v that contain x] this.prob (x)
	 */
	public Factor extractMax (Variable var);

	/**
	 *  Returns a potential phi over the given variables
	 *   obtained by taking 
	 *  phi (x) = max_[all v that contain x] this.prob (x)
	 */
	public Factor extractMax (Variable[] vars);

	/**
	 * Returns the assignment that maximizes this potential.
	 */
	// todo: should return an Assignment
  int argmax ();

  /**
   * Return an assignment sampled from this factor, interpreting
   *  it as an unnormalized probability distribution.
   */
  Assignment sample (Randoms r);

	/**
	 *  Returns the sum of this potential over all cases.
	 */
	public double sum ();

	/**
	 *  Returns the expected log factor value, i.e.,
   *   <tt>sum_x factor.value(x) * Math.log (factor.value (x))</tt>
   *   where the summation is taken over all passible assignments.
	 */
	public double entropy ();

 

	/**
	 * Returns the elementwise product of this factor with
	 *  another.
	 */
	public Factor multiply (Factor dist);

	/**
	 *  Does this *= pot.
	 *  <P>
	 *  If both potentials are currently in log space, then does
	 *   addition instead.
	 *  @throws UnsupportedOperationException If one potential is in
	 *  log space and the other isn't.
	 */
	public void multiplyBy (Factor pot);

  public void exponentiate (double power);

  /**
	 * Computes this /= pot
	 *  <P>
	 *  If both potentials are currently in log space, then does
	 *   subtraction instead.
	 *  @throws UnsupportedOperationException If one potential is in
	 *  log space and the other isn't.
	 */
	public void divideBy (Factor pot);

	/**
	 *  Returns whether the potential is over the given variable.
	 */
	public boolean containsVar (Variable var);

	/** Returns set of variables in this potential. */
	public VarSet varSet ();

	/** Returns an iterator over all Assignmentss to this potential. */
	public AssignmentIterator assignmentIterator ();

	/** Returns whether this is almost equal to another potential. */
	public boolean almostEquals (Factor p);
	public boolean almostEquals (Factor p, double epsilon);

	public Factor duplicate ();

	public boolean isNaN ();

  double logValue (AssignmentIterator it);

  double logValue (Assignment assn);

  double logValue (int loc);

  Variable getVariable (int i);

  String dumpToString ();

  Factor slice (Assignment assn);

  AbstractTableFactor asTable ();
  
}
