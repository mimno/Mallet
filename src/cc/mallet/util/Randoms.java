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


import java.util.*;


public class Randoms extends java.util.Random {

	public Randoms (int seed) {
		super(seed);
	}
	
	public Randoms () {
		super();
	}
	
	/** Return random integer from Poission with parameter lambda.  
	 * The mean of this distribution is lambda.  The variance is lambda. */
  public synchronized int nextPoisson(double lambda) {
    int i,j,v=-1;
    double l=Math.exp(-lambda),p;
    p=1.0;
    while (p>=l) {
      p*=nextUniform();
      v++;
    }
    return v;
  }

  /** Return nextPoisson(1). */
  public synchronized int nextPoisson() {
    return nextPoisson(1);
  }

  /** Return a random boolean, equally likely to be true or false. */
  public synchronized boolean nextBoolean() {
    return (next(32) & 1 << 15) != 0;
  }

  /** Return a random boolean, with probability p of being true. */
  public synchronized boolean nextBoolean(double p) {
    double u=nextUniform();
    if(u < p) return true;
    return false;
  }

  /** Return a random BitSet with "size" bits, each having probability p of being true. */
  public synchronized BitSet nextBitSet (int size, double p)
  {
    BitSet bs = new BitSet (size);
    for (int i = 0; i < size; i++)
      if (nextBoolean (p)) {
        bs.set (i);
      }
    return bs;
  }

  /** Return a random double in the range 0 to 1, inclusive, uniformly sampled from that range. 
   * The mean of this distribution is 0.5.  The variance is 1/12. */
  public synchronized double nextUniform() {
    long l = ((long)(next(26)) << 27) + next(27);
    return l / (double)(1L << 53);
  }

  /** Return a random double in the range a to b, inclusive, uniformly sampled from that range.
   * The mean of this distribution is (b-a)/2.  The variance is (b-a)^2/12 */
  public synchronized double nextUniform(double a,double b) {
    return a + (b-a)*nextUniform();
  }

	/** Draw a single sample from multinomial "a". */
	public synchronized int nextDiscrete (double[] a) {
		double b = 0, r = nextUniform();
		for (int i = 0; i < a.length; i++) {
			b += a[i];
      if (b > r) {
        return i;
      }
		}
		return a.length-1;
	}

	/** draw a single sample from (unnormalized) multinomial "a", with normalizing factor "sum". */
	public synchronized int nextDiscrete (double[] a, double sum) {
		double b = 0, r = nextUniform() * sum;
		for (int i = 0; i < a.length; i++) {
			b += a[i];
      if (b > r) {
        return i;
      }
		}
		return a.length-1;
	}

  private double nextGaussian;
  private boolean haveNextGaussian = false;

  /** Return a random double drawn from a Gaussian distribution with mean 0 and variance 1. */
  public synchronized double nextGaussian() {
    if (!haveNextGaussian) {
      double v1=nextUniform(),v2=nextUniform();
      double x1,x2;
      x1=Math.sqrt(-2*Math.log(v1))*Math.cos(2*Math.PI*v2);
      x2=Math.sqrt(-2*Math.log(v1))*Math.sin(2*Math.PI*v2);
      nextGaussian=x2;
      haveNextGaussian=true;
      return x1;
    }
    else {
      haveNextGaussian=false;
      return nextGaussian;
    }
  }

  /** Return a random double drawn from a Gaussian distribution with mean m and variance s2. */
  public synchronized double nextGaussian(double m,double s2) {
    return nextGaussian()*Math.sqrt(s2)+m;
  }

  // generate Gamma(1,1)
  // E(X)=1 ; Var(X)=1
  /** Return a random double drawn from a Gamma distribution with mean 1.0 and variance 1.0. */
  public synchronized double nextGamma() {
    return nextGamma(1,1,0);
  }

  /** Return a random double drawn from a Gamma distribution with mean alpha and variance 1.0. */
	public synchronized double nextGamma(double alpha) {
    return nextGamma(alpha,1,0);
	}

	/* Return a sample from the Gamma distribution, with parameter IA */
	/* From Numerical "Recipes in C", page 292 */
	public synchronized double oldNextGamma (int ia)
	{
		int j;
		double am, e, s, v1, v2, x, y;

		assert (ia >= 1) ;
		if (ia < 6) 
    {
      x = 1.0;
      for (j = 1; j <= ia; j++)
				x *= nextUniform ();
      x = - Math.log (x);
    }
		else
    {
      do
			{
				do
				{
					do
					{
						v1 = 2.0 * nextUniform () - 1.0;
						v2 = 2.0 * nextUniform () - 1.0;
					}
					while (v1 * v1 + v2 * v2 > 1.0);
					y = v2 / v1;
					am = ia - 1;
					s = Math.sqrt (2.0 * am + 1.0);
					x = s * y + am;
				}
				while (x <= 0.0);
				e = (1.0 + y * y) * Math.exp (am * Math.log (x/am) - s * y);
			}
      while (nextUniform () > e);
    }
		return x;
	}

	
	/** Return a random double drawn from a Gamma distribution with mean alpha*beta and variance alpha*beta^2. */
  public synchronized double nextGamma(double alpha, double beta) {
    return nextGamma(alpha,beta,0);
  }

  /** Return a random double drawn from a Gamma distribution with mean alpha*beta+lamba and variance alpha*beta^2. */
  public synchronized double nextGamma(double alpha,double beta,double lambda) {
    double gamma=0;
    if (alpha <= 0 || beta <= 0) {
      throw new IllegalArgumentException ("alpha and beta must be strictly positive.");
    }
    if (alpha < 1) {
      double b,p;
      boolean flag=false;
      b=1+alpha*Math.exp(-1);
      while(!flag) {
        p=b*nextUniform();
        if (p>1) {
          gamma=-Math.log((b-p)/alpha);
          if (nextUniform()<=Math.pow(gamma,alpha-1)) flag=true;
        }
        else {
          gamma=Math.pow(p,1/alpha);
          if (nextUniform()<=Math.exp(-gamma)) flag=true;
        }
      }
    }
    else if (alpha == 1) {
      gamma = -Math.log (nextUniform ());
    } else {
      double y = -Math.log (nextUniform ());
      while (nextUniform () > Math.pow (y * Math.exp (1 - y), alpha - 1))
        y = -Math.log (nextUniform ());
      gamma = alpha * y;
    }
    return beta*gamma+lambda;
  }

	/** Return a random double drawn from an Exponential distribution with mean 1 and variance 1. */
  public synchronized double nextExp() {
    return nextGamma(1,1,0);
  }

	/** Return a random double drawn from an Exponential distribution with mean beta and variance beta^2. */
  public synchronized double nextExp(double beta) {
    return nextGamma(1,beta,0);
  }

	/** Return a random double drawn from an Exponential distribution with mean beta+lambda and variance beta^2. */
  public synchronized double nextExp(double beta,double lambda) {
    return nextGamma(1,beta,lambda);
  }

	/** Return a random double drawn from an Chi-squarted distribution with mean 1 and variance 2. 
	 * Equivalent to nextChiSq(1) */
  public synchronized double nextChiSq() {
    return nextGamma(0.5,2,0);
  }

  /** Return a random double drawn from an Chi-squared distribution with mean df and variance 2*df.  */
  public synchronized double nextChiSq(int df) {
    return nextGamma(0.5*(double)df,2,0);
  }

  /** Return a random double drawn from an Chi-squared distribution with mean df+lambda and variance 2*df.  */
  public synchronized double nextChiSq(int df,double lambda) {
    return nextGamma(0.5*(double)df,2,lambda);
  }

  /** Return a random double drawn from a Beta distribution with mean a/(a+b) and variance ab/((a+b+1)(a+b)^2).  */
  public synchronized double nextBeta(double alpha,double beta) {
    if (alpha <= 0 || beta <= 0) {
      throw new IllegalArgumentException ("alpha and beta must be strictly positive.");
    }
    if (alpha == 1 && beta == 1) {
      return nextUniform ();
    } else if (alpha >= 1 && beta >= 1) {
      double A = alpha - 1,
              B = beta - 1,
              C = A + B,
              L = C * Math.log (C),
              mu = A / C,
              sigma = 0.5 / Math.sqrt (C);
      double y = nextGaussian (), x = sigma * y + mu;
      while (x < 0 || x > 1) {
        y = nextGaussian ();
        x = sigma * y + mu;
      }
      double u = nextUniform ();
      while (Math.log (u) >= A * Math.log (x / A) + B * Math.log ((1 - x) / B) + L + 0.5 * y * y) {
        y = nextGaussian ();
        x = sigma * y + mu;
        while (x < 0 || x > 1) {
          y = nextGaussian ();
          x = sigma * y + mu;
        }
        u = nextUniform ();
      }
      return x;
    } else {
      double v1 = Math.pow (nextUniform (), 1 / alpha),
              v2 = Math.pow (nextUniform (), 1 / beta);
      while (v1 + v2 > 1) {
        v1 = Math.pow (nextUniform (), 1 / alpha);
        v2 = Math.pow (nextUniform (), 1 / beta);
      }
      return v1 / (v1 + v2);
    }
  }

  /** Wrap this instance as a java random, so it can be passed to legacy methods.
   *   All methods of the returned java.util.Random object will affect the state of
   *   this object, as well. */
  @Deprecated // This should no longer be necessary, since Randoms is now a subclass of java.util.random anyway
  public java.util.Random asJavaRandom ()
  {
    return new java.util.Random () {
      protected int next (int bits)
      {
        return cc.mallet.util.Randoms.this.next (bits);
      }
    };
  }

  public static void main (String[] args)
	{
		// Prints the nextGamma() and oldNextGamma() distributions to
		// System.out for testing/comparison.
		Randoms r = new Randoms();
		final int resolution = 60;
		int[] histogram1 = new int[resolution];
		int[] histogram2 = new int[resolution];
		int scale = 10;

		for (int i = 0; i < 10000; i++) {
			double x = 4;
			int index1 = ((int)(r.nextGamma(x)/scale * resolution)) % resolution;
			int index2 = ((int)(r.oldNextGamma((int)x)/scale * resolution)) % resolution;
			histogram1[index1]++;
			histogram2[index2]++;
		}

		for (int i = 0; i < resolution; i++) {
			for (int y = 0; y < histogram1[i]/scale; y++)
				System.out.print("*");
			System.out.print("\n");
			for (int y = 0; y < histogram2[i]/scale; y++)
				System.out.print("-");
			System.out.print("\n");
		}
	}
	
}

