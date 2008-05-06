/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.util;

// Math and statistics functions
public final class Maths {

	// From libbow, dirichlet.c
	// Written by Tom Minka <minka@stat.cmu.edu>
	public static final double logGamma (double x)
	{
		double result, y, xnum, xden;
		int i;
		final double d1 = -5.772156649015328605195174e-1;
		final double p1[] = { 
			4.945235359296727046734888e0, 2.018112620856775083915565e2, 
			2.290838373831346393026739e3, 1.131967205903380828685045e4, 
			2.855724635671635335736389e4, 3.848496228443793359990269e4, 
			2.637748787624195437963534e4, 7.225813979700288197698961e3 
		};
		final double q1[] = {
			6.748212550303777196073036e1, 1.113332393857199323513008e3, 
			7.738757056935398733233834e3, 2.763987074403340708898585e4, 
			5.499310206226157329794414e4, 6.161122180066002127833352e4, 
			3.635127591501940507276287e4, 8.785536302431013170870835e3
		};
		final double d2 = 4.227843350984671393993777e-1;
		final double p2[] = {
			4.974607845568932035012064e0, 5.424138599891070494101986e2, 
			1.550693864978364947665077e4, 1.847932904445632425417223e5, 
			1.088204769468828767498470e6, 3.338152967987029735917223e6, 
			5.106661678927352456275255e6, 3.074109054850539556250927e6
		};
		final double q2[] = {
			1.830328399370592604055942e2, 7.765049321445005871323047e3, 
			1.331903827966074194402448e5, 1.136705821321969608938755e6, 
			5.267964117437946917577538e6, 1.346701454311101692290052e7, 
			1.782736530353274213975932e7, 9.533095591844353613395747e6
		};
		final double d4 = 1.791759469228055000094023e0;
		final double p4[] = {
			1.474502166059939948905062e4, 2.426813369486704502836312e6, 
			1.214755574045093227939592e8, 2.663432449630976949898078e9, 
			2.940378956634553899906876e10, 1.702665737765398868392998e11, 
			4.926125793377430887588120e11, 5.606251856223951465078242e11
		};
		final double q4[] = {
			2.690530175870899333379843e3, 6.393885654300092398984238e5, 
			4.135599930241388052042842e7, 1.120872109616147941376570e9, 
			1.488613728678813811542398e10, 1.016803586272438228077304e11, 
			3.417476345507377132798597e11, 4.463158187419713286462081e11
		};
		final double c[] = {
			-1.910444077728e-03, 8.4171387781295e-04, 
			-5.952379913043012e-04, 7.93650793500350248e-04, 
			-2.777777777777681622553e-03, 8.333333333333333331554247e-02, 
			5.7083835261e-03
		};
		final double a = 0.6796875;

		if((x <= 0.5) || ((x > a) && (x <= 1.5))) {
			if(x <= 0.5) {
				result = -Math.log(x);
				/*  Test whether X < machine epsilon. */
				if(x+1 == 1) {
					return result;
				}
			}
			else {
				result = 0;
				x = (x - 0.5) - 0.5;
			}
			xnum = 0;
			xden = 1;
			for(i=0;i<8;i++) {
				xnum = xnum * x + p1[i];
				xden = xden * x + q1[i];
			}
			result += x*(d1 + x*(xnum/xden));
		}
		else if((x <= a) || ((x > 1.5) && (x <= 4))) {
			if(x <= a) {
				result = -Math.log(x);
				x = (x - 0.5) - 0.5;
			}
			else {
				result = 0;
				x -= 2;
			}
			xnum = 0;
			xden = 1;
			for(i=0;i<8;i++) {
				xnum = xnum * x + p2[i];
				xden = xden * x + q2[i];
			}
			result += x*(d2 + x*(xnum/xden));
		}
		else if(x <= 12) {
			x -= 4;
			xnum = 0;
			xden = -1;
			for(i=0;i<8;i++) {
				xnum = xnum * x + p4[i];
				xden = xden * x + q4[i];
			}
			result = d4 + x*(xnum/xden);
		}
		/*  X > 12  */
		else {
			y = Math.log(x);
			result = x*(y - 1) - y*0.5 + .9189385332046727417803297;
			x = 1/x;
			y = x*x;
			xnum = c[6];
			for(i=0;i<6;i++) {
				xnum = xnum * y + c[i];
			}
			xnum *= x;
			result += xnum;
		}
		return result;
	}
	
	// This is from "Numeric Recipes in C"
  public static double oldLogGamma (double x) {
    int j;
    double y, tmp, ser;
    double [] cof = {76.18009172947146, -86.50532032941677 ,
                     24.01409824083091, -1.231739572450155 ,
                      0.1208650973866179e-2, -0.5395239384953e-5};
    y = x;
    tmp = x + 5.5 - (x + 0.5) * Math.log (x + 5.5);
    ser = 1.000000000190015;
    for (j = 0; j <= 5; j++)
      ser += cof[j] / ++y;
    return Math.log (2.5066282746310005 * ser / x) - tmp;
  }

  public static double logBeta (double a, double b) {
    return logGamma(a)+logGamma(b)-logGamma(a+b);
  }

  public static double beta (double a, double b) {
    return Math.exp (logBeta(a,b));
  }

  public static double gamma (double x) {
    return Math.exp (logGamma(x));
  }

  public static double factorial (int n) {
    return Math.exp (logGamma(n+1));
  }

  public static double logFactorial (int n) {
    return logGamma(n+1);
  }

  /**
   * Computes p(x;n,p) where x~B(n,p)
   */
  // Copied as the "classic" method from Catherine Loader.
  //  Fast and Accurate Computation of Binomial Probabilities.
  //   2001.  (This is not the fast and accurate version.)
  public static double logBinom (int x, int n, double p)
  {
    return logFactorial (n) - logFactorial (x) - logFactorial (n - x)
            + (x*Math.log (p)) + ((n-x)*Math.log (1-p));
  }

  /**
   * Vastly inefficient O(x) method to compute cdf of B(n,p)
   */
  public static double pbinom (int x, int n, double p) {
    double sum = Double.NEGATIVE_INFINITY;
    for (int i = 0; i <= x; i++) {
      sum = sumLogProb (sum, logBinom (i, n, p));
    }
    return Math.exp (sum);
  }

  public static double sigmod(double beta){
	return (double)1.0/(1.0+Math.exp(-beta));
  }

  public static double sigmod_rev(double sig){
	return (double)Math.log(sig/(1-sig));
  }

  public static double logit (double p)
  {
    return Math.log (p / (1 - p));
  }

  // Combination?
  public static double numCombinations (int n, int r) {
    return Math.exp (logFactorial(n)-logFactorial(r)-logFactorial(n-r));
  }

  // Permutation?
  public static double numPermutations (int n, int r) {
    return Math.exp (logFactorial(n)-logFactorial(r));
  }


	public static double cosh (double a)
	{
		if (a < 0)
			return 0.5 * (Math.exp(-a) + Math.exp(a));
		else
			return 0.5 * (Math.exp(a) + Math.exp(-a));
		}

	public static double tanh (double a)
	{
		return (Math.exp(a) - Math.exp(-a)) / (Math.exp(a) + Math.exp(-a));
	}

	/**
	 * Numbers that are closer than this are considered equal
	 * by almostEquals.
	 */
	public static double EPSILON = 0.000001;

	public static boolean almostEquals (double d1, double d2)
  {
		return almostEquals (d1, d2, EPSILON);
	}
  
   public static boolean almostEquals (double d1, double d2, double epsilon)
   {
		return Math.abs (d1 - d2) < epsilon;
	}

  public static boolean almostEquals (double[] d1, double[] d2, double eps)
 {
   for (int i = 0; i < d1.length; i++) {
     double v1 = d1[i];
     double v2 = d2[i];
     if (!almostEquals (v1, v2, eps)) return false;
   }
   return true;
 }

  // gsc
  /**
   * Checks if <tt>min &lt;= value &lt;= max</tt>.
   */
  public static boolean checkWithinRange(double value, double min, double max) {
    return (value > min || almostEquals(value, min, EPSILON)) &&
           (value < max || almostEquals(value, max, EPSILON));
  }

  public static final double log2 = Math.log(2);

  // gsc
  /**
   * Returns the KL divergence, K(p1 || p2).
   *
   * The log is w.r.t. base 2. <p>
   *
   * *Note*: If any value in <tt>p2</tt> is <tt>0.0</tt> then the KL-divergence
   * is <tt>infinite</tt>.
   */
  public static double klDivergence(double[] p1, double[] p2) {
    assert(p1.length == p2.length);
    double klDiv = 0.0;
    for (int i = 0; i < p1.length; ++i) {
      klDiv += p1[i] * Math.log(p1[i]/p2[i])/log2;
    }
    return klDiv;
  }

  // gsc
  /**
   * Returns the Jensen-Shannon divergence.
   */
  public static double jensenShannonDivergence(double[] p1, double[] p2) {
    assert(p1.length == p2.length);
    double[] average = new double[p1.length];
    for (int i = 0; i < p1.length; ++i) {
      average[i] += (p1[i] + p2[i])/2;
    }
    return (klDivergence(p1, average) + klDivergence(p2, average))/2;
  }

  /**
	 *  Returns the sum of two doubles expressed in log space,
	 *   that is,
	 * <pre>
	 *    sumLogProb = log (e^a + e^b)
   *               = log e^a(1 + e^(b-a))
   *               = a + log (1 + e^(b-a))
	 * </pre>
	 *
	 * By exponentiating <tt>b-a</tt>, we obtain better numerical precision than
	 *  we would if we calculated <tt>e^a</tt> or <tt>e^b</tt> directly.
	 * <P>
	 * Note: This function is just like 
   *  {@link cc.mallet.fst.Transducer#sumNegLogProb sumNegLogProb}
	 *   in <TT>Transducer</TT>,
   *   except that the logs aren't negated.
	 */
	public static double sumLogProb (double a, double b)
	{
		if (a == Double.NEGATIVE_INFINITY) 
			return b;
		else if (b == Double.NEGATIVE_INFINITY)
			return a;
		else if (b < a)
			return a + Math.log (1 + Math.exp(b-a));
		else
			return b + Math.log (1 + Math.exp(a-b));
	}

  // Below from Stanford NLP package, SloppyMath.java

  private static final double LOGTOLERANCE = 30.0;

  /**
   * Sums an array of numbers log(x1)...log(xn).  This saves some of
   *  the unnecessary calls to Math.log in the two-argument version.
   * <p>
   * Cursory testing makes me wonder if this is actually much faster than
   *  repeated use of the 2-argument version, however -cas.
   * @param vals An array log(x1), log(x2), ..., log(xn)
   * @return log(x1+x2+...+xn)
   */
  public static double sumLogProb (double[] vals)
  {
    double max = Double.NEGATIVE_INFINITY;
    int len = vals.length;
    int maxidx = 0;

    for (int i = 0; i < len; i++) {
      if (vals[i] > max) {
        max = vals[i];
        maxidx = i;
      }
    }

    boolean anyAdded = false;
    double intermediate = 0.0;
    double cutoff = max - LOGTOLERANCE;

    for (int i = 0; i < maxidx; i++) {
      if (vals[i] >= cutoff) {
        anyAdded = true;
        intermediate += Math.exp(vals[i] - max);
      }
    }
    for (int i = maxidx + 1; i < len; i++) {
      if (vals[i] >= cutoff) {
        anyAdded = true;
        intermediate += Math.exp(vals[i] - max);
      }
    }

    if (anyAdded) {
      return max + Math.log(1.0 + intermediate);
    } else {
      return max;
    }

  }

  /**
   *  Returns the difference of two doubles expressed in log space,
   *   that is,
   * <pre>
   *    sumLogProb = log (e^a - e^b)
   *               = log e^a(1 - e^(b-a))
   *               = a + log (1 - e^(b-a))
   * </pre>
   *
   * By exponentiating <tt>b-a</tt>, we obtain better numerical precision than
   *  we would if we calculated <tt>e^a</tt> or <tt>e^b</tt> directly.
   * <p>
   * Returns <tt>NaN</tt> if b > a (so that log(e^a - e^b) is undefined).
   */
  public static double subtractLogProb (double a, double b)
  {
    if (b == Double.NEGATIVE_INFINITY)
      return a;
    else
      return a + Math.log (1 - Math.exp(b-a));
  }

}
