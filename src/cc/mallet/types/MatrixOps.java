/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */

package cc.mallet.types;

import java.util.Random;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/** 
 *  A class of static utility functions for manipulating arrays of
 *   double. 
 */
public final class MatrixOps
{

	/** Sets every element of a double array to a given value.
	 * @param m  The array to modify
	 * @param v  The value
	 */
    public static void setAll (double[] m, double v) {
			java.util.Arrays.fill (m, v);
    }

    public static void set (double[] dest, double[] source) {
			// XXX  Huh? This if-statement won't work. Java is pass-by-value, buddy. -cas
        if (source.length != dest.length)
            dest  = new double [source.length];
        System.arraycopy (source, 0, dest, 0, source.length);
    }

	/**
	 *  Multiplies every element in an array by a scalar.
	 *  @param m The array
	 *  @param factor The scalar
	 */
    public static void timesEquals (double[] m, double factor) {
        for (int i=0; i < m.length; i++)
            m[i] *= factor;
    }
    
    /* Calculates the Schur/Hadamard product */ // JJW
    public static void timesEquals(double[] m1, double[] m2) {

        assert (m1.length == m2.length) : "unequal lengths\n";
        for (int i=0; i < m1.length; i++) {
	    m1[i] *= m2[i];
	}
    }

	/**
	 *  Adds a scalar to every element in an array.
	 *  @param m The array
	 *  @param toadd The scalar
	 */
    public static void plusEquals (double[] m, double toadd) {
        for (int i=0; i < m.length; i++)
            m[i] += toadd;
    }

    public static void plusEquals (double[] m1, double[] m2) {
        assert (m1.length == m2.length) : "unequal lengths\n";
        for (int i=0; i < m1.length; i++) {
            if (Double.isInfinite(m1[i]) && Double.isInfinite(m2[i]) && (m1[i]*m2[i] < 0))
                m1[i] = 0.0;
            else
                m1[i] += m2[i];
        }
    }

    public static void plusEquals (double[] m1, double[] m2,  double factor) {
        assert (m1.length == m2.length) : "unequal lengths\n";
        for (int i=0; i < m1.length; i++) {
            double m1i = m1[i];
            double m2i = m2[i];
            if (Double.isInfinite(m1i) && Double.isInfinite(m2i) && (m1[i]*m2[i] < 0))
                m1[i] = 0.0;
            else  m1[i] += m2[i] * factor;
        }
    }

  public static void plusEquals (double[][] m1, double[][] m2,  double factor)
  {
      assert (m1.length == m2.length) : "unequal lengths\n";
      for (int i=0; i < m1.length; i++) {
        for (int j=0; j < m1[i].length; j++) {
          m1[i][j] += m2[i][j] * factor;
        }
      }
  }

	public static void log (double[] m)
	{
		for (int i = 0; i < m.length; i++)
			m[i] = Math.log(m[i]);
	}


    /** @deprecated  Use dotProduct() */
    public static double dot (double[] m1, double[] m2) {
        assert (m1.length == m2.length) : "m1.length != m2.length\n";
        double ret = 0.0;
        for (int i=0; i < m1.length; i++)
            ret += m1[i] * m2[i];
        return ret;
    }

    public static double dotProduct (double[] m1, double[] m2) {
        assert (m1.length == m2.length) : "m1.length != m2.length\n";
        double ret = 0.0;
        for (int i=0; i < m1.length; i++)
            ret += m1[i] * m2[i];
        return ret;
    }

    public static double absNorm (double[] m) {
        double ret = 0;
        for (int i = 0; i < m.length; i++)
            ret += Math.abs(m[i]);
        return ret;
    }

    public static double twoNorm (double[] m) {
        double ret = 0;
        for (int i = 0; i < m.length; i++)
            ret += m[i] * m[i];
        return Math.sqrt (ret);
    }

    public static double twoNormSquared (double[] m) {
      double ret = 0;
      for (int i = 0; i < m.length; i++)
          ret += m[i] * m[i];
      return ret;
  }

    public static double oneNorm (double[] m) {
        double ret = 0;
        for (int i = 0; i < m.length; i++)
            ret += m[i];
        return ret;
    }
    
    public static double oneNormalize (double[] m) {
    	double sum = oneNorm(m);
    	for (int i = 0; i < m.length; i++)
    		m[i] /= sum;
    	return sum;
    }
    
    public static double normalize (double[] m) {
    	return oneNormalize(m);
    }

    public static double infinityNorm (double[] m) {
        double ret = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < m.length; i++)
            if (Math.abs(m[i]) > ret)
                ret = Math.abs(m[i]);
        return ret;
    }

    public static double absNormalize (double[] m) {
        double norm = absNorm(m);
        if (norm > 0)
            for (int i = 0; i < m.length; i++)
                m[i] /= norm;
        return norm;
    }

    public static double twoNormalize (double[] m) {
        double norm = twoNorm(m);
        if (norm > 0)
            for (int i = 0; i < m.length; i++)
                m[i] /= norm;
        return norm;
    }

    public static void substitute (double[] m, double oldValue, double newValue) {
        for (int i = m.length-1; i >= 0; i--)
            if (m[i] == oldValue)
                m[i] = newValue;
    }

    /** If "ifSelected" is false, it reverses the selection.  If
     "fselection" is null, this implies that all features are
     selected; all values in the row will be changed unless
     "ifSelected" is false. */
    public static final void rowSetAll (double[] m, int nc, int ri, double v, FeatureSelection fselection, boolean ifSelected) {
        if (fselection == null) {
            if (ifSelected == true) {
                for (int ci = 0; ci < nc; ci++)
                    m[ri*nc+ci] = v;
            }
        } else {
            // xxx Temporary check for full selection
            //assert (fselection.nextDeselectedIndex (0) == nc);
            for (int ci = 0; ci < nc; ci++)
                if (fselection.contains(ci) ^ !ifSelected)
                    m[ri*nc+ci] = v;
        }
    }

    public static double rowDotProduct (double[] m, int nc, int ri,
                                        Vector v, int maxCi,
                                        FeatureSelection selection) {
        return rowDotProduct (m, nc, ri, v, 1, maxCi, selection);
    }

    public static double rowDotProduct (double[] m, int nc, int ri,
                                        Vector v, double factor, int maxCi,
                                        FeatureSelection selection) {
        double ret = 0;
        if (selection != null) {
  					int size = v.numLocations();
            for (int cil = 0; cil < size; cil++) {
                int ci = v.indexAtLocation (cil);
                if (selection.contains(ci) && ci < nc && ci <= maxCi)
                    ret += m[ri*nc+ci] * v.valueAtLocation(cil) * factor;
            }
        } else {
					int size = v.numLocations();
					for (int cil = 0; cil < size; cil++) {
						int ci = v.indexAtLocation (cil);
            if (ci <= maxCi)
						  ret += m[ri*nc+ci] * v.valueAtLocation(cil) * factor;
					}
        }
        return ret;
    }

    public static final void rowPlusEquals (double[] m, int nc, int ri,
                                            Vector v, double factor) {
        for (int vli = 0; vli < v.numLocations(); vli++)
            m[ri*nc+v.indexAtLocation(vli)] += v.valueAtLocation(vli) * factor;

    }

    public static boolean isNaN(double[] m) {
        for (int i = 0; i < m.length; i++)
            if (Double.isNaN(m[i]))
                return true;
        return false;
    }

    // gsc: similar to isNan, but checks for inifinite values
    public static boolean isInfinite(double[] m) {
    		for (int i = 0; i < m.length; i++)
    				if (Double.isInfinite(m[i]))
    						return true;
    		return false;
    }
    
    // gsc: returns true if any value in the array is either NaN or infinite
    public static boolean isNaNOrInfinite(double[] m) {
    		for (int i = 0; i < m.length; i++)
    				if (Double.isInfinite(m[i]) || Double.isNaN(m[i]))
    						return true;
    		return false;
    }
    
    // gsc: returns true if any value in the array is greater than 0.0/-0.0
    public static boolean isNonZero(double[] m) {
    		for (int i = 0; i < m.length; i++)
    				if (Math.abs(m[i]) > 0.0)
    						return true;
    		return false;
    }
    
    // gsc: returns true if any value in the array is 0.0/-0.0
    public static boolean isZero(double[] m) {
    		for (int i = 0; i < m.length; i++)
    				if (Math.abs(m[i]) == 0.0)
    						return true;
    		return false;
    }
    
    // TODO: This is the same as oneNorm(), and should be removed
    public static double sum (double[] m) {
        double sum = 0;
        for (int i = 0; i < m.length; i++)
            sum += m[i];
        return sum;
    }

    public static double sum (double[][] m) {
        double sum = 0;
        for (int i = 0; i < m.length; i++)
          for (int j = 0; j < m[i].length; j++)
            sum += m[i][j];
        return sum;
    }

    public static int sum (int[] m) {
        int sum = 0;
        for (int i = 0; i < m.length; i++)
            sum += m[i];
        return sum;
    }

    // CPAL
		// TODO: This should be removed, because FeatureVector already has oneNorm(). -AKM
    public static double sum(Vector v) {
        double sum = 0;
        for (int vli = 0; vli < v.numLocations(); vli++) {
            sum = sum +  v.valueAtLocation(vli);
        }
        return sum;
    }

    public static double mean (double[] m) {
        double sum = 0;
        for (int i = 0; i < m.length; i++)
            sum += m[i];
        return sum / m.length;
    }

    /** Return the standard deviation */
    public static double stddev (double[] m) {
        double mean = mean (m);
        double s = 0;
        for (int i = 0; i < m.length; i++)
            s += (m[i] - mean) * (m[i] - mean);
        return Math.sqrt (s/m.length);
        // Some prefer dividing by (m.length-1), but this is also common
    }

    public static double stderr (double[] m ) {
        return stddev(m) / Math.sqrt (m.length);
    }

    // gsc
    /** Return the variance */
    public static double variance (double[] m) {
        double mean = mean (m);
        double s = 0;
        for (int i = 0; i < m.length; i++)
            s += (m[i] - mean) * (m[i] - mean);
        return s/m.length;
        // Some prefer dividing by (m.length-1), but this is also common
    }

  /**
   * Prints a double array to standard output
   * @param m Array to print.
   */
    public static final void print (double[] m)
    {
      print (new PrintWriter (new OutputStreamWriter (System.out), true), m);
    }

    /**
     * Prints a double array to the given PrintWriter.
     * @param out Writer to print ouput to
     * @param m Array to print.
     */
      public static final void print (PrintWriter out, double[] m)
    {
      for (int i = 0; i < m.length; i++) {
        out.print (" " + m[i]);
      }
      out.println("");
    }

    public static final void print (double[][] arr)
    {
      for (int i = 0; i < arr.length; i++) {
        double[] doubles = arr[i];
        print (doubles);
      }
    }

    /** Print a space-separated array of elements
     * 
     * @param m An array of any type
     */
    public static final String toString( Object m ) {
	StringBuffer sb = new StringBuffer();
	
	int n=java.lang.reflect.Array.getLength(m)-1;
	for (int i = 0; i<n ; i++) {
	    sb.append(java.lang.reflect.Array.get(m,i));
	    sb.append(" ");
	}

	if (n>=0)
	    sb.append(java.lang.reflect.Array.get(m,n));

	return sb.toString();
    }

  public static final void printInRows (double[] arr)
  {
    for (int i = 0; i < arr.length; i++) {
      double v = arr[i];
      System.out.println("["+i+"]  "+arr[i]);
    }
  }

  public static void setAll (double[][][] m, double v)
  {
    for (int i = 0; i < m.length; i++) {
      for (int j = 0; j < m[i].length; j++) {
        for (int k = 0; k < m[i][j].length; k++) {
          m[i][j][k] = v;
        }
      }
    }
  }

  public static void setAll (double[][] m, double v)
  {
    for (int i = 0; i < m.length; i++) {
      for (int j = 0; j < m[i].length; j++) {
        m[i][j] = v;
      }
    }
  }

  public static void print (int[][] arr)
  {
    for (int i = 0; i < arr.length; i++) {
      print (arr [i]);
    }
  }

  public static void print (int[] m)
  {
    for (int i = 0; i < m.length; i++) {
         System.out.print (" " + m[i]);
     }
     System.out.println("");
  }


  public static double[] randomVector (int n, Random r)
  {
    double[] ret = new double [n];
    for (int i = 0; i < n; i++) ret[i] = r.nextDouble ();
    return ret;
  }

  public static void timesEquals (double[][] m, double factor)
  {
    for (int i = 0; i < m.length; i++) {
      for (int j = 0; j < m[i].length; j++) {
        m[i][j] *= factor;
      }
    }
  }

  /**
   * Returns the maximum elementwise absolute difference between two vectors.
   *   This is the same as infinityNorm (v1 - v2) (if that were legal Java).
   * @param v1 Input vector, as double[]
   * @param v2 Input vector, as double[]
   * @return
   */

  public static double maxAbsdiff (double[] v1, double[] v2)
  {
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < v1.length; i++) {
      double val = Math.abs (v1[i] - v2[i]);
      if (val > max) max = val;
    }
    return max;
  }

  public static int max (int[][] m) {
    int maxval = m[0][0];
    for (int i=0; i < m.length; i++) {
      for (int j=0; j < m[i].length; j++) {
        if (m[i][j] > maxval) {
          maxval = m[i][j];
        }
      }
    }
    return maxval;
  } 

  public static int max (int [] elems)
  {
    int max = Integer.MIN_VALUE;
    for (int i = 0; i < elems.length; i++) {
      int elem = elems[i];
      if (elem > max) {
        max = elem;
      }
    }
    return max;
  }

  public static double max (double [] elems)
  {
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < elems.length; i++) {
      double elem = elems[i];
      if (elem > max) {
        max = elem;
      }
    }
    return max;
  }
  
  public static double min (double [] elems)
  {
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < elems.length; i++) {
      double elem = elems[i];
      if (elem < min) {
        min = elem;
      }
    }
    return min;
  }
  
  public static int maxIndex (double [] elems)
  {
    double max = Double.NEGATIVE_INFINITY;
    int maxIndex = -1; 
    for (int i = 0; i < elems.length; i++) {
      double elem = elems[i];
      if (elem > max) {
        max = elem;
        maxIndex = i;
      }
    }
    return maxIndex;
  }
  
  public static int minIndex (double [] elems)
  {
    double min = Double.POSITIVE_INFINITY;
    int minIndex = -1; 
    for (int i = 0; i < elems.length; i++) {
      double elem = elems[i];
      if (elem < min) {
        min = elem;
        minIndex = i;
      }
    }
    return minIndex;
  }
  
  public static double[] append (double[] original, double newValue)
  {
  	double[] ret = new double[original.length + 1];
		System.arraycopy(original, 0, ret, 0, original.length);
		ret[original.length] = newValue;
  	return ret;
  }

	public static double max(double[][] ds) {
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < ds.length; i++) {
			for (int j = 0; j < ds[i].length; j++) {
				if (ds[i][j] > max) {
					max = ds[i][j];
				}
			}
		}
		return max;
	}
	
	public static int[] maxIndex(double[][] ds) {
		int[] maxIndices = new int[] {-1,-1};
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < ds.length; i++) {
			for (int j = 0; j < ds[i].length; j++) {
				if (ds[i][j] > max) {
					max = ds[i][j];
					maxIndices[0] = i;
					maxIndices[1] = j;
				}
			}
		}
		return maxIndices;
	}

  public static void expNormalize(double[] scores) {
    double max = MatrixOps.max (scores);
    double sum = 0;
    for (int i = 0; i < scores.length; i++)
      sum += (scores[i] = Math.exp (scores[i] - max));
    for (int i = 0; i < scores.length; i++) 
      scores[i] /= sum;
  }
}


