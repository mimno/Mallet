/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;


import java.util.List;

import cc.mallet.grmm.types.Assignment;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.util.Randoms;

/**
 * Interface for methods from sampling the distribution given by a graphical
 *  model.
 *
 * Created: Mar 28, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Sampler.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public interface Sampler {

  /**
   * Samples from the distribution of a given undirected model.
   * @param mdl Model to sample from
   * @param N Number of samples to generate
   * @return A list of assignments to the model.
   */
  public Assignment sample (FactorGraph mdl, int N);

  /**
   * Sets the random seed used by this sampler.
   * @param r Random object to be used by this sampler.
   */
  public void setRandom (Randoms r);
  
}
