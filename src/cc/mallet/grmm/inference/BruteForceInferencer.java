/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.inference;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;

import cc.mallet.grmm.inference.AbstractInferencer;
import cc.mallet.grmm.types.*;


/**
 *  Computes the joint of a GraphicalModel by brute-force
 *   calculation.  This is exponentially slow, so it is  mostly 
 *   useful as a sanity check on more complicated algorithms.
 *
 * Created: Wed Sep 17 13:21:13 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: BruteForceInferencer.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class BruteForceInferencer 
	extends AbstractInferencer
	implements Inferencer 
{

	transient Factor cachedJoint;

	public Factor joint (FactorGraph model)
	{ 
		Factor joint = TableFactor.multiplyAll
															  (model.factors ());
		joint.normalize();
		return joint;
	}

	public Factor joint (JunctionTree jt)
	{
		Factor joint = TableFactor.multiplyAll (jt.clusterPotentials ());
		for (Iterator it = jt.sepsetPotentials().iterator(); it.hasNext();) {
			TableFactor pot = (TableFactor) it.next();
			joint.divideBy (pot);
		}
		joint.normalize();
		return joint;
	}

	public void computeMarginals (FactorGraph mdl)
	{
		cachedJoint = joint (mdl);
	}

	public void computeMarginals (JunctionTree jt)
	{
		cachedJoint = joint (jt);
	}

	public Factor lookupMarginal (Variable var)
	{
		return cachedJoint.marginalize (var);
	}

	public Factor lookupMarginal (VarSet c)
	{
		return cachedJoint.marginalize (c);
	}

	public double lookupJoint (Assignment assn)
	{
		return cachedJoint.value (assn);
	}

	public double lookupLogJoint (Assignment assn)
	{
		return Math.log (cachedJoint.value (assn));
	}

  // Serialization
  private static final long serialVersionUID = 1;

  // If seralization-incompatible changes are made to these classes,
  //  then smarts can be added to these methods for backward compatibility.
  private void writeObject (ObjectOutputStream out) throws IOException {
     out.defaultWriteObject ();
   }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
     in.defaultReadObject ();
  }

}
