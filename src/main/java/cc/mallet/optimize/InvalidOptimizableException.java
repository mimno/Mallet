/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.optimize;

/**
 * Exception thrown by optimization algorithms, when the problem is usually
 *  due to a problem with the given Maximizable instance.
 * <p>
 * If the optimizer throws this in your code, usually there are two possible
 *  causes: (a) you are computing the gradients approximately, (b) your value
 *  function and gradient do not match (this can be checking using
 *  @link{edu.umass.cs.mallet.base.maximize.tests.TestMaximizable}.
 *
 * Created: Feb 1, 2006
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: InvalidMaximizableException.java,v 1.1 2007/10/22 21:37:39 mccallum Exp $
 */
public class InvalidOptimizableException extends OptimizationException {

  public InvalidOptimizableException ()
  {
  }

  public InvalidOptimizableException (String message)
  {
    super (message);
  }

  public InvalidOptimizableException (String message, Throwable cause)
  {
    super (message, cause);
  }

  public InvalidOptimizableException (Throwable cause)
  {
    super (cause);
  }
}
