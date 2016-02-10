/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.learning;


import java.util.List;
import java.util.Iterator;

import cc.mallet.grmm.learning.ACRF;
import cc.mallet.grmm.learning.ACRFEvaluator;
import cc.mallet.types.InstanceList;

/**
 * Created: Aug 24, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: AcrfSerialEvaluator.java,v 1.1 2007/10/22 21:37:43 mccallum Exp $
 */
public class AcrfSerialEvaluator extends ACRFEvaluator {

  private List evals;

  public AcrfSerialEvaluator (List evals)
  {
    super();
    this.evals = evals;
  }

  public boolean evaluate (ACRF acrf, int iter, InstanceList training, InstanceList validation, InstanceList testing)
  {
    boolean ret = true;
    for (Iterator it = evals.iterator (); it.hasNext ();) {
      ACRFEvaluator evaluator = (ACRFEvaluator) it.next ();
      // Return false (i.e., stop training) if any sub-evaluator does.
      ret = ret && evaluator.evaluate (acrf, iter, training, validation, testing);
    }
    return ret;
  }

  public void test (InstanceList gold, List returned, String description)
  {
    for (Iterator it = evals.iterator (); it.hasNext ();) {
      ACRFEvaluator eval =  (ACRFEvaluator) it.next ();
      eval.test (gold, returned, description);
    }
  }
  
}
