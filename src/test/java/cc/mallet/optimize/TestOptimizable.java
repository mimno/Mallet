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

import junit.framework.*;
import java.util.logging.*;
import java.io.*;
import java.util.Random;

import cc.mallet.classify.*;
import cc.mallet.optimize.LineOptimizer;
import cc.mallet.optimize.Optimizable;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

/**
 *  Contains static methods for testing subclasses of
 *   Maximizable and Maximizable.ByGradient.  Especially
 *   useful are methods that verify the consistency of the value
 *   and gradient functions of an instance of 
 *   Maximizable.ByGradient.
 */
public class TestOptimizable extends TestCase
{
	private static Logger logger =
		MalletLogger.getLogger(TestOptimizable.class.getName());

	public TestOptimizable (String name) {
		super (name);
	}

	static private int numComponents = -1;

	/**
	 *  Sets the number of gradient components that will be checked.
	 *   If negative, all will be checked.
	 */
	public static void setNumComponents (int n) { numComponents = n; }

	/**
	 *  Tests that parameters set by setParameters can be retrieved by 
	 *   getParameters.
	 *  @param maxable Instance of a Maximizable that should be tested.
	 *   Its current parameters will be overwritten.
	 */
	public static boolean testGetSetParameters (Optimizable maxable)
	{
		System.out.println ("TestMaximizable testGetSetParameters");
		// Set all the parameters to unique values using setParameters()
		double[] parameters = new double [maxable.getNumParameters()];
		maxable.getParameters (parameters);
		for (int i = 0; i < parameters.length; i++)
			parameters[i] = (double)i;
		maxable.setParameters (parameters);

		// Test to make sure those parameters are there
		MatrixOps.setAll (parameters, 0.0);
		maxable.getParameters (parameters);
		for (int i = 0; i < parameters.length; i++)
			assertTrue (parameters[i] == (double)i);
		return true;
	}

	public static double
	testValueAndGradientInDirection (Optimizable.ByGradientValue maxable, double[] direction)
	{
		int numParameters = maxable.getNumParameters();
		assert (numParameters == direction.length);
		double[] oldParameters = new double[numParameters];
		double[] parameters = new double[numParameters];
		double[] normalizedDirection = direction.clone();
		System.arraycopy(direction, 0, normalizedDirection, 0, numParameters);
		MatrixOps.absNormalize(normalizedDirection);
		
		double value = maxable.getValue();
		// the gradient from the optimizable function
		double[] analyticGradient = new double[numParameters];
		maxable.getParameters (parameters);
		maxable.getParameters (oldParameters);
		maxable.getValueGradient (analyticGradient);
		// the gradient calculate from the slope of the value
		// This setting of epsilon should make the individual elements of
		// the analytical gradient and the empirical gradient equal.  This
		// simplifies the comparison of the individual dimensions of the
		// gradient and thus makes debugging easier.
		double directionGradient = MatrixOps.dotProduct (analyticGradient, normalizedDirection);
		double epsilon = 0.1 / MatrixOps.absNorm(analyticGradient);
		double tolerance = 0.00001 * directionGradient; // this was "epsilon * 5";
		System.out.println ("epsilon = "+epsilon+" tolerance="+tolerance);
		MatrixOps.plusEquals (parameters, normalizedDirection, epsilon);
		//logger.fine ("Parameters:"); parameters.print();
		maxable.setParameters (parameters);
		double epsValue = maxable.getValue();
		double slope = (epsValue - value) / epsilon;
		System.out.println ("value="+value+" epsilon="+epsilon+" epsValue="+
				epsValue+" slope = "+slope+" gradient="+directionGradient);
		assert (!Double.isNaN (slope));
		double slopeDifference = Math.abs(slope - directionGradient);
		logger.info ("TestMaximizable "+
				": slope tolerance = "+tolerance+
				": gradient slope = "+directionGradient+
				", value+epsilon slope = "+slope+
				": slope difference = "+slopeDifference);
		maxable.setParameters (oldParameters);
		assert (Math.abs(slopeDifference) < tolerance) : "Slope difference "+slopeDifference+" is greater than tolerance "+tolerance;
		return slopeDifference;
	}

	/**
	 * Tests that the value and gradient function are consistent
	 *  at the current parameters.
	 *  Computes both the analytic gradient (the one given by 
	 *  <tt>maxable.getValueGradient</tt>) and the empirical gradient,
	 *  which is (if x are the current parameters and f the function
	 *  computed by maxable) <tt>f(x + epsilon) - f(x)</tt>.  Verifies
	 *  that the angle between the empirical and analytic gradients 
	 *  are close to 0.
	 * @see #testValueAndGradient testValueAndGradient
	 * @see #testValueAndGradientRandomParameters testValueAndGradientRandomParameters
	 * @throws IllegalStateException If the angle is above the tolerance
	 */
	public static double
	testValueAndGradientCurrentParameters (Optimizable.ByGradientValue maxable)
	{
		double[] parameters = new double [maxable.getNumParameters()];
		double value = maxable.getValue();
		// the gradient from the maximizable function
		double[] analyticGradient = new double[maxable.getNumParameters()];
		double[] empiricalGradient = new double[maxable.getNumParameters()];
		maxable.getParameters (parameters);
		maxable.getValueGradient (analyticGradient);
		// the gradient calculate from the slope of the value
		maxable.getValueGradient (empiricalGradient);
		// This setting of epsilon should make the individual elements of
		// the analytical gradient and the empirical gradient equal.  This
		// simplifies the comparison of the individual dimensions of the
		// gradient and thus makes debugging easier.
		// cas: However, avoid huge epsilon if norm of analytic gradient is
		//  close to 0.
		// Next line used to be: double norm = Math.max (0.1, MatrixOps.twoNorm(analyticGradient));
		// but if all the components of the analyticalGradient are very small, the squaring in the
		// twoNorm causes epsilon to be too large.  -AKM
		double norm = Math.max (0.1, MatrixOps.absNorm(analyticGradient));
		double epsilon = 0.1 / norm;
		double tolerance = epsilon * 5;
		System.out.println ("epsilon = "+epsilon+" tolerance="+tolerance);

		int sampleParameterInterval = -1;
		if (numComponents > 0) {
			sampleParameterInterval = Math.max (1, parameters.length / numComponents);
			logger.info ("Will check every "+sampleParameterInterval+"-th component.");
		}

		// Check each direction, perturb it, measure new value, and make
		// sure it agrees with the gradient from
		// maxable.getValueGradient()
		for (int i = 0; i < parameters.length; i++) {
//			{ int i = 0;   // Uncomment this line to debug one parameter at a time -cas
			if ((parameters.length >= sampleParameterInterval) &&
					(i % sampleParameterInterval != 0))
				continue;

			double param = parameters[i];
			parameters[i] = param + epsilon;
			//logger.fine ("Parameters:"); parameters.print();
			maxable.setParameters (parameters);
			double epsValue = maxable.getValue();
			double slope = (epsValue - value) / epsilon;
			System.out.println ("value="+value+" epsValue="+epsValue+" slope["+i+"] = "+slope+" gradient[]="+analyticGradient[i]);
			assert (!Double.isNaN (slope));
			logger.info ("TestMaximizable checking singleIndex "+i+
					": gradient slope = "+analyticGradient[i]+
					", value+epsilon slope = "+slope+
					": slope difference = "+(slope - analyticGradient[i]));
			// No negative below because the gradient points in the direction
			// of maximizing the function.
			empiricalGradient[i] = slope;
			parameters[i] =  param;
		}
		// Normalize the matrices to have the same L2 length
		System.out.println ("analyticGradient.twoNorm = "+
				MatrixOps.twoNorm(analyticGradient));
		System.out.println ("empiricalGradient.twoNorm = "+
				MatrixOps.twoNorm(empiricalGradient));
		MatrixOps.timesEquals (analyticGradient,
				1.0/MatrixOps.twoNorm(analyticGradient));
		MatrixOps.timesEquals (empiricalGradient,
				1.0/MatrixOps.twoNorm(empiricalGradient));
		/* 
	   System.out.println("N   ANA          EMP");
	   for (int i = 0; i < analyticGradient.length; i++) {
      	System.out.println(i+"   "+analyticGradient[i]+"  "+empiricalGradient[i]);
	   }
		 */

		// Return the angle between the two vectors, in radians
		double dot = MatrixOps.dotProduct (analyticGradient,empiricalGradient);
		if (Maths.almostEquals (dot, 1.0)) {
			logger.info ("TestMaximizable angle is zero.");
			return 0.0;
		} else {
			double angle = Math.acos (dot);

			logger.info ("TestMaximizable angle = "+angle);
			if (Math.abs(angle) > tolerance)
				throw new IllegalStateException ("Gradient/Value mismatch: angle="+
						angle + " tol: " + tolerance);
			if (Double.isNaN (angle))
				throw new IllegalStateException ("Gradient/Value error: angle is NaN!");

			return angle;
		}
	}

	/**
	 * Tests that getValue and getValueGradient are consistent.
	 * Tests for consistency at <tt>params = 0</tt> and at
	 *  <tt> params = -0.0001 * grad(f)</tt>
	 *  @see #testValueAndGradientCurrentParameters testValueAndGradientCurrentParameters
	 * @throws IllegalStateException If the test fails.
	 */
	public static boolean	testValueAndGradient (Optimizable.ByGradientValue maxable)
	{
		double[] parameters = new double [maxable.getNumParameters()];
		MatrixOps.setAll (parameters, 0.0);
		maxable.setParameters (parameters);
		testValueAndGradientCurrentParameters (maxable);
		MatrixOps.setAll (parameters, 0.0);
		double[] delta = new double[maxable.getNumParameters()];
		maxable.getValueGradient (delta);
		logger.info ("Gradient two-Norm = "+MatrixOps.twoNorm(delta));
		logger.info ("  max parameter change = "+(MatrixOps.infinityNorm(delta) * -0.001));
		MatrixOps.timesEquals (delta, -0.0001);
		MatrixOps.plusEquals (parameters, delta);
		maxable.setParameters (parameters);
		testValueAndGradientCurrentParameters (maxable);
		return true;
	}

	/**
	 * Tests that getValue and getValueGradient are consistent 
	 *   at a random parameter setting.
	 *  @see #testValueAndGradientCurrentParameters testValueAndGradientCurrentParameters
	 * @throws IllegalStateException If the test fails.
	 */
	public static boolean testValueAndGradientRandomParameters 
	(Optimizable.ByGradientValue maxable, Random r)
	{
		double[] params = new double [maxable.getNumParameters()];
		for (int i = 0; i < params.length; i++) {
			params[i] = r.nextDouble ();
			if (r.nextBoolean ()) 
				params [i] = -params[i];
		}
		maxable.setParameters (params);
		testValueAndGradientCurrentParameters (maxable);
		return true;
	}


	// Maximizable for 3x^2 - 5x + 2
	static class SimplePoly implements Optimizable.ByGradientValue {

		double[] params = new double [1];

		public void getParameters(double[] doubleArray) {
			doubleArray [0] = params [0];
		}

		public int getNumParameters() { return 1; }

		public double getParameter(int n) { return params [0]; };

		public void setParameters(double[] doubleArray) {
			params [0] = doubleArray [0];
		}
		public void setParameter(int n, double d) { params[n] = d; }

		public double getValue () {
			return 3*params[0]*params[0] - 5 * params[0] + 2;
		}

		public void getValueGradient (double[] buffer)
		{
			buffer [0] = 3*params [0] - 5;
		}
	}

	static class WrongSimplePoly extends SimplePoly {
		public void getValueGradient (double[] buffer)
		{
			buffer [0] = 3*params [0]; // WRONG: Missing -5
		}
	}


	public void testTestValueAndGradient ()
	{
		SimplePoly maxable = new SimplePoly ();
		testValueAndGradient (maxable);

		try {
			WrongSimplePoly badMaxable = new WrongSimplePoly ();
			testValueAndGradient (badMaxable);
			fail ("WrongSimplyPoly should fail testMaxmiziable!");
		} catch (Exception e) {}
	}

	public static Test suite ()
	{
		return new TestSuite (TestOptimizable.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}

}
