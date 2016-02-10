/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;


import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import cc.mallet.grmm.types.Factor;
import cc.mallet.grmm.types.FactorGraph;
import cc.mallet.grmm.types.Variable;

/**
 * A dynamic BP schedule where 
 * The loopy belief propagation algorithm for approximate inference in
 *  general graphical models.
 *
 * Created: Wed Nov  5 19:30:15 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: ResidualBP.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class ResidualBP extends AbstractBeliefPropagation {

  public static final int DEFAULT_MAX_ITER = 1000;

  transient private int iterUsed;

  private int maxIter;

  private Random rand = new Random ();

  public int iterationsUsed () { return iterUsed; }

  public void setUseCaching (boolean useCaching) { this.useCaching = useCaching; }

  // Note this does not have the sophisticated Terminator interface
  // that we've got in TRP.
  public ResidualBP () { this (new SumProductMessageStrategy (), ResidualBP.DEFAULT_MAX_ITER); }

  public ResidualBP (int maxIter) { this (new SumProductMessageStrategy (), maxIter); }

  public ResidualBP (MessageStrategy messager, int maxIter)
  {
    super (messager);
    this.maxIter = maxIter;
  }

  public static Inferencer createForMaxProduct ()
  {
    return new ResidualBP (new MaxProductMessageStrategy (), ResidualBP.DEFAULT_MAX_ITER);
  }

  public ResidualBP setRand (Random rand)
  {
    this.rand = rand;
    return this;
  }

  public void computeMarginals (FactorGraph mdl)
  {
    super.initForGraph (mdl);

    int iter;
    for (iter = 0; iter < maxIter; iter++) {
      logger.finer ("***AsyncLoopyBP iteration "+iter);
      propagate (mdl);
      if (hasConverged ()) break;
      copyOldMessages ();
    }
    iterUsed = iter;
    if (iter >= maxIter) {
      logger.info ("***Loopy BP quitting: not converged after "+maxIter+" iterations.");
    } else {
      iterUsed++;  // there's an off-by-one b/c of location of above break
      logger.info ("***AsyncLoopyBP converged: "+iterUsed+" iterations");
    }

    doneWithGraph (mdl);
  }

  private void propagate (FactorGraph mdl)
  {
    // Send all messages in random order.
    ArrayList factors = new ArrayList (mdl.factors());
    Collections.shuffle (factors, rand);
    for (Iterator it = factors.iterator(); it.hasNext();) {
      Factor factor = (Factor) it.next();
      for (Iterator vit = factor.varSet ().iterator (); vit.hasNext ();) {
        Variable from = (Variable) vit.next ();
        sendMessage (mdl, from, factor);
      }
    }

    for (Iterator it = factors.iterator(); it.hasNext();) {
      Factor factor = (Factor) it.next();
      for (Iterator vit = factor.varSet ().iterator (); vit.hasNext ();) {
        Variable to = (Variable) vit.next ();
        sendMessage (mdl, factor, to);
      }
    }
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
