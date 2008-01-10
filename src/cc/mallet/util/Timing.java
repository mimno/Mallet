/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.util;

/**
 * A class for timing things.
 * Originally inspired by the Timing class in the Stanford NLP cade,
 * but completely rewritten.
 * <p/>
 * Created: Dec 30, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: Timing.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class Timing {

  private long objCreationTime;
  private long startTime;

  public Timing ()
  {
    startTime = System.currentTimeMillis ();
    objCreationTime = startTime;
  }

  /**
   * Print to System.out how much time has passed, resetting this Timing's start time to
   * the current time.  Time is measured from the most recent
   * <code>tick</code> call, or when this object was created.
   *
   * @param msg Prefix of string printed with time
   * @return Number of elapsed milliseconds from tick (or start)
   */
  public long tick (String msg)
  {
    long elapsed = report (msg);
    startTime = System.currentTimeMillis ();
    return elapsed;
  }

  /**
   * Returns how much time as passed since Object creation, or the most recent call to tick().
   * @return Number of elapsed milliseconds
   */
  public long elapsedTime ()
  {
    return System.currentTimeMillis () - startTime;
  }

  /**
   * Returns the number of milliseconds since this object was created.
   * Ignores previous calls to <tt>tick</tt>, unlike
   * <tt>elapsedTime</tt> and <tt>tick</tt>.
   */
  public long totalElapsedTime ()
  {
    return System.currentTimeMillis () - objCreationTime;
  }

  /** Like tick(), but doesn't reset the counter. */
  public long report (String msg)
  {
    long currentTime = System.currentTimeMillis ();
    long elapsed = currentTime - startTime;
    System.out.println (msg + " time (ms) =  " + (elapsed));
    return elapsed;
  }
}
