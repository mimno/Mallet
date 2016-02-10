/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;


import cc.mallet.optimize.Optimizable;
import cc.mallet.util.MalletLogger;

import java.util.logging.Logger;

/**
 * Created: Aug 27, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: CachingOptimizable.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class CachingOptimizable {

  private static abstract class Base implements Optimizable {

    static final Logger logger = MalletLogger.getLogger (CachingOptimizable.class.getName ());

    double cachedValue = -123456789;
    double[] cachedGradient;
    protected boolean cachedValueStale = true;
    protected boolean cachedGradientStale = true;

    protected abstract void setParametersInternal (double[] params);

    public void setParameters (double[] params)
    {
      if (params.length != getNumParameters ())
        throw new IllegalArgumentException ("Argument is not of the " +
                " correct dimensions");

      cachedValueStale = cachedGradientStale = true;
      setParametersInternal (params);
    }

    /**
     * Sets one parameter of the maximizable object.  This default implementation
     * inefficiently uses both <tt>getParameters(double[])</tt> and <tt>setParameters(double[])</tt>.
     * Subclasses may override this method for more efficient implemetnations.
     *
     * @param index
     * @param value
     */
    public void setParameter (int index, double value)
    {
      cachedValueStale = cachedGradientStale = true;
      double[] params = new double[getNumParameters ()];
      getParameters (params);
      params[index] = value;
      setParametersInternal (params);
    }

    /**
     * Returns one parameter of the maximizable object.  This default implementation
     * inefficiently calls through to <tt>getParameters(double[])</tt>.
     * Subclasses may override this method for more efficient implemetnations.
     *
     * @param index
     * @return The value of parameter <tt>index<tt>
     */
    public double getParameter (int index)
    {
      double[] params = new double[getNumParameters ()];
      getParameters (params);
      return params[index];
    }

    public void forceStale ()
    {
      cachedValueStale = cachedGradientStale = true;
    }

  }

   /**/
   public static abstract class ByGradient extends Base implements Optimizable.ByGradientValue {

    protected abstract double computeValue ();

    protected abstract void computeValueGradient (double[] buffer);

    public void getValueGradient (double[] buffer)
    {
      if (buffer.length != getNumParameters ())
        throw new IllegalArgumentException ("Argument is not of the " +
                " correct dimensions");

      if (cachedValueStale) {
        cachedValue = computeValue ();
        cachedValueStale = false;
      }
      if (cachedGradientStale) {
        if (cachedGradient == null) {
          cachedGradient = new double[getNumParameters ()];
        }
        computeValueGradient (cachedGradient);
        cachedGradientStale = false;
      }
      System.arraycopy (cachedGradient, 0, buffer, 0, cachedGradient.length);
    }

    public double getValue ()
    {
      if (cachedValueStale) {
        long startTime = System.currentTimeMillis();
        cachedValue = computeValue ();
        long endTime = System.currentTimeMillis();
        logger.info ("Optimizable computeValue time (ms) ="+(endTime-startTime));
        logger.info ("computeValue() = " + cachedValue);
        cachedValueStale = false;
      }
      return cachedValue;
    }

    /**
     * Sets the cached gradient.  This is useful for subclasses that
     * need to compute the value and the gradient at the same time.
     * If they call this method in computeValue(), then
     * their computeValueGradient() will never be called.
     *
     * @param gradient
     */
    protected void setCachedGradient (double[] gradient)
    {
      if (cachedGradient == null) {
        cachedGradient = new double[getNumParameters ()];
      }
      System.arraycopy (gradient, 0, cachedGradient, 0, gradient.length);
      cachedGradientStale = false;
    }

  }

  public static abstract class ByBatchGradient extends Base implements Optimizable.ByBatchGradient {

    private int lastIndex;
    private int[] lastAssns;

    public void getBatchValueGradient (double[] buffer, int batchIndex, int[] batchAssignments)
    {
      if (buffer.length != getNumParameters ())
        throw new IllegalArgumentException ("Argument is not of the " +
                " correct dimensions");

      if ((batchIndex != lastIndex) || (batchAssignments != lastAssns)) {
        forceStale ();
        lastIndex = batchIndex;
        lastAssns = batchAssignments;
      }

      if (cachedValueStale) {
        cachedValue = computeBatchValue (batchIndex, batchAssignments);
        cachedValueStale = false;
      }
      if (cachedGradientStale) {
        if (cachedGradient == null) {
          cachedGradient = new double[getNumParameters ()];
        }
        computeBatchGradient (cachedGradient, batchIndex, batchAssignments);
        cachedGradientStale = false;
      }
      System.arraycopy (cachedGradient, 0, buffer, 0, cachedGradient.length);
    }

    public double getBatchValue (int batchIndex, int[] batchAssignments)
    {
      if ((batchIndex != lastIndex) || (batchAssignments != lastAssns)) {
        forceStale ();
        lastIndex = batchIndex;
        lastAssns = batchAssignments;
      }

      if (cachedValueStale) {
        cachedValue = computeBatchValue (batchIndex, batchAssignments);
        logger.info ("computeValue() = " + cachedValue);
        cachedValueStale = false;
      }
      return cachedValue;
    }

    protected abstract double computeBatchValue (int batchIndex, int[] batchAssignments);

    protected abstract void computeBatchGradient (double[] buffer, int batchIndex, int[] batchAssignments);

  }
}
