/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.optimize;

import java.util.logging.*;

import cc.mallet.optimize.LineOptimizer;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.tests.TestOptimizable;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletLogger;

// Conjugate Gradient, Polak and Ribiere version
// from "Numeric Recipes in C", Section 10.6.

public class ConjugateGradient implements Optimizer
{
	private static Logger logger = MalletLogger.getLogger(ConjugateGradient.class.getName());
	
	boolean converged = false;
	Optimizable.ByGradientValue optimizable;
	LineOptimizer.ByGradient lineMaximizer;

	// xxx If this is too big, we can get inconsistent value and gradient in MaxEntTrainer
	// Investigate!!!
	double initialStepSize = 0.01;
	double tolerance = 0.0001;
	int maxIterations = 1000;

	// "eps" is a small number to recitify the special case of converging
	// to exactly zero function value
	final double eps = 1.0e-10;
  private OptimizerEvaluator.ByGradient eval;

  public ConjugateGradient (Optimizable.ByGradientValue function, double initialStepSize)
  {
    this.initialStepSize = initialStepSize;
    this.optimizable = function;
    this.lineMaximizer = new BackTrackLineSearch (function);
    // Alternative: = new GradientBracketLineMaximizer (function);
  }

	public ConjugateGradient (Optimizable.ByGradientValue function)
	{
		this (function, 0.01);
	}

	public Optimizable getOptimizable () { return this.optimizable; }
	public boolean isConverged () { return converged; }

  public void setEvaluator (OptimizerEvaluator.ByGradient eval)
  {
    this.eval = eval;
  }

  public void setLineMaximizer (LineOptimizer.ByGradient lineMaximizer)
  {
    this.lineMaximizer = lineMaximizer;
  }

  public void setInitialStepSize (double initialStepSize) { this.initialStepSize = initialStepSize; }
	public double getInitialStepSize () { return this.initialStepSize; }
  public double getStepSize () { return step; }

  // The state of a conjugate gradient search
	double fp, gg, gam, dgg, step, fret;
	double[] xi, g, h;
	int j, iterations;

	public boolean optimize ()
	{
		return optimize (maxIterations);
	}

	public void setTolerance(double t) {
		tolerance = t;
	}

	public boolean optimize (int numIterations)
	{
		if (converged)
			return true;
    int n = optimizable.getNumParameters();
    double prevStepSize = initialStepSize;
    boolean searchingGradient = true;
    if (xi == null) {
			fp = optimizable.getValue ();
			xi = new double[n];
			g = new double[n];
			h = new double[n];
			optimizable.getValueGradient (xi);
			System.arraycopy (xi, 0, g, 0, n);
			System.arraycopy (xi, 0, h, 0, n);
			step = initialStepSize;
      iterations = 0;
		}

		for (int iterationCount = 0; iterationCount < numIterations; iterationCount++) {
			logger.info ("ConjugateGradient: At iteration "+iterations+", cost = "+fp);
			try {
        prevStepSize = step;
        step = lineMaximizer.optimize (xi, step);
			} catch (IllegalArgumentException e) {
				System.out.println ("ConjugateGradient caught "+e.toString());
        TestOptimizable.testValueAndGradientCurrentParameters(optimizable);
        TestOptimizable.testValueAndGradientInDirection(optimizable, xi);
				//System.out.println ("Trying ConjugateGradient restart.");
				//return this.maximize (maxable, numIterations);
			}
      if (step == 0) {
        if (searchingGradient) {
          System.err.println ("ConjugateGradient converged: Line maximizer got step 0 in gradient direction.  "
                              +"Gradient absNorm="+MatrixOps.absNorm(xi));
          converged = true;
          return true;
        } else
          System.err.println ("Line maximizer got step 0.  Probably pointing up hill.  Resetting to gradient.  "
                              +"Gradient absNorm="+MatrixOps.absNorm(xi));
        // Copied from above (how to code this better?  I want GoTo)
        fp = optimizable.getValue();
        optimizable.getValueGradient (xi);
        searchingGradient = true;
        System.arraycopy (xi, 0, g, 0, n);
        System.arraycopy (xi, 0, h, 0, n);
        step = prevStepSize;
        continue;
      }
      fret = optimizable.getValue();
			// This termination provided by "Numeric Recipes in C".
			if (2.0*Math.abs(fret-fp) <= tolerance*(Math.abs(fret)+Math.abs(fp)+eps)) {
        System.out.println ("ConjugateGradient converged: old value= "+fp+" new value= "+fret+" tolerance="+tolerance);
        converged = true;
        return true;
      }
      fp = fret;
			optimizable.getValueGradient(xi);
			
			logger.info ("Gradient infinityNorm = "+MatrixOps.infinityNorm(xi));
			// This termination provided by McCallum
			if (MatrixOps.infinityNorm(xi) < tolerance) {
        System.err.println ("ConjugateGradient converged: maximum gradient component "+MatrixOps.infinityNorm(xi)
                            +", less than "+tolerance);
        converged = true;
        return true;
      }

      dgg = gg = 0.0;
			double gj, xj;
			for (j = 0; j < xi.length; j++) {
				gj = g[j];
				gg += gj * gj;
				xj = -xi[j];
				dgg = (xj + gj) * xj;
			}
			if (gg == 0.0) {
        System.err.println ("ConjugateGradient converged: gradient is exactly zero.");
        converged = true;
        return true; // In unlikely case that gradient is exactly zero, then we are done
      }
      gam = dgg/gg;

			double hj;
			for (j = 0; j < xi.length; j++) {
				xj = xi[j];
				g[j] = xj;
				hj = h[j];
				hj = xj + gam * hj;
				h[j] = hj;
			}
			assert (!MatrixOps.isNaN(h));
			MatrixOps.set (xi, h);
      searchingGradient = false;

      iterations++;
			if (iterations > maxIterations) {
				System.err.println("Too many iterations in ConjugateGradient.java");
				converged = true;
				return true;
				//throw new IllegalStateException ("Too many iterations.");
			}

      if (eval != null)
        eval.evaluate (optimizable, iterations);
    }
		return false;
	}

  public void reset () { xi = null; }
}
