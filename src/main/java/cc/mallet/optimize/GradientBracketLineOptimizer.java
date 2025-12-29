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

import cc.mallet.optimize.Optimizable;
import cc.mallet.types.Matrix;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletLogger;

// Brents method using derivative information
// p405, "Numeric Recipes in C"

public class GradientBracketLineOptimizer implements LineOptimizer {
	private static Logger logger = MalletLogger.getLogger(GradientBracketLineOptimizer.class.getName());
	
	int maxIterations = 50;
	Optimizable.ByGradientValue optimizable;
	
	public GradientBracketLineOptimizer (Optimizable.ByGradientValue function) {
		this.optimizable = function;
	}

	/*
	public double maximize (Optimizable function, Matrix line, double initialStep) {
		return maximize ((Optimizable.ByGradient)function, line, initialStep);
	}*/

	// TODO
	// This seems to work but is slower than BackTrackLineSearch.  Why?
	
	// Return the last step size used.
	// "line" should point in the direction we want to move the parameters to get
	// higher value.
	public double optimize (double[] line, double initialStep)
	{
		
		assert (initialStep > 0);		
		double[] parameters = new double[optimizable.getNumParameters()];
		double[] gradient = new double[optimizable.getNumParameters()];
		optimizable.getParameters(parameters);		
		optimizable.getValueGradient(gradient);
		
		// a=left, b=center, c=right, t=test
		double ax, bx, cx, tx;							// steps (domain), these are deltas from initial params!
		double ay, by, cy, ty;							// costs (range)
		double ag, bg, cg, tg;							// projected gradients
		double ox;													// the x step of the last function call
		double origY;
		
		tx = ax = bx = cx = ox = 0;
		ty = ay = by = cy = origY = optimizable.getValue();
		
		tg = ag = bg = MatrixOps.dotProduct(gradient,line);
		// Make sure search-line points upward
		//logger.info ("Initial gradient = "+tg);
		if (ag <= 0) {
			throw new InvalidOptimizableException
				("The search direction \"line\" does not point down uphill.  "
				 + "gradient.dotProduct(line)="+ag+", but should be positive");
		}

		// Find an cx value where the gradient points the other way.  Then
		// we will know that the (local) zero-gradient minimum falls
		// in between ax and cx.
		int iterations = 0;
		do {
			if (iterations++ > maxIterations)
				throw new IllegalStateException ("Exceeded maximum number allowed iterations searching for gradient cross-over.");
			// If we are still looking to cross the minimum, move ax towards it
			ax = bx; ay = by; ag = bg;
			// Save this (possibly) middle point; it might make an acceptable bx
			bx = tx; by = ty; bg = tg;
			if (tx == 0) {
				if (initialStep < 1.0) {
					tx  = initialStep;
				}
				else {
					tx = 1.0;
				}
				// Sometimes the "suggested" initialStep is 
				// very large and causes values to go to 
				// infinity.
				//tx = initialStep;
				//tx = 1.0;
			}
			else {
				tx *= 3.0;
			}
			//logger.info ("Gradient cross-over search, incrementing by "+(tx-ox));
			MatrixOps.plusEquals(parameters,line,tx-ox);
			optimizable.setParameters (parameters);
			ty = optimizable.getValue();
			optimizable.getValueGradient(gradient);			
			tg = MatrixOps.dotProduct(gradient,line);
			//logger.info ("Next gradient = "+tg);
			ox = tx;
		} while (tg > 0);
		
		//System.err.println(iterations + " total iterations in A.");
		
		cx = tx; cy = ty; cg = tg;
		//logger.info ("After gradient cross-over ax="+ax+" bx="+bx+" cx="+cx);
		//logger.info ("After gradient cross-over ay="+ay+" by="+by+" cy="+cy);
		//logger.info ("After gradient cross-over ag="+ag+" bg="+bg+" cg="+cg);

		// We need to find a "by" that is less than both "ay" and "cy"
		assert (!Double.isNaN(by));
		while (by <= ay || by <= cy || bx == ax) {
			// Last condition would happen if we did first while-loop only once
			if (iterations++ > maxIterations)
				throw new IllegalStateException ("Exceeded maximum number allowed iterations searching for bracketed minimum, iteratation count = "+iterations);
			// xxx What should this tolerance be?
			// xxx I'm nervous that this is masking some assert()s below that were previously failing.
			// If they were failing due to round-off error, that's OK, but if not...
			if ((Math.abs(bg) < 100 || Math.abs(ay-by) < 10 || Math.abs(by-cy) < 10) && bx != ax)
			//if ((Math.abs(bg) < 10 || Math.abs(ay-by) < 1 || Math.abs(by-cy) < 1) && bx != ax)
			
				// Magically, we are done
				break;

			// Instead make a version that finds the interpolating point by
			// fitting a parabola, and then jumps to that minimum.  If the
			// actual y value is within "tolerance" of the parabola fit's
			// guess, then we are done, otherwise, use the parabola's x to
			// split the region, and try again.

			// There might be some cases where this will perform worse than
			// simply bisecting, as we do now, when the function is not at
			// all parabola shaped.
			
			// If the gradients ag and bg point in the same direction, then
			// the value by must be less than ay.  And vice-versa for bg and cg.
			//assert (ax==bx || ((ag*bg)>=0 && by>ay) || (((bg*cg)>=0 && by>cy)));
			assert (!Double.isNaN(bg));
			if (bg > 0) {
				// the minimum is at higher x values than bx; drop ax
				assert (by >= ay);
				ax = bx; ay = by; ag = bg;
			} else {
				// the minimum is at lower x values than bx; drop cx
				assert (by >= cy);
				cx = bx; cy = by; cg = bg;
			}
			
			// Find a new mid-point
			bx = (ax + cx) / 2;
			//logger.info ("Minimum bx search, incrementing by "+(bx-ox));
			MatrixOps.plusEquals(parameters,line,bx - ox);
			optimizable.setParameters (parameters);
			by = optimizable.getValue();
			assert (!Double.isNaN(by));
			optimizable.getValueGradient(gradient);
			bg = MatrixOps.dotProduct(gradient,line);
			ox = bx;
			
			//logger.info ("  During min bx search ("+iterations+") ax="+ax+" bx="+bx+" cx="+cx);
			//logger.info ("  During min bx search ("+iterations+") ay="+ay+" by="+by+" cy="+cy);
			//logger.info ("  During min bx search ("+iterations+") ag="+ag+" bg="+bg+" cg="+cg);
		}
		
		// We now have two points (ax, cx) that straddle the minimum, and a mid-point
		// bx with a value lower than either ay or cy.
		tx = ax
				 + (((bx-ax)*(bx-ax)*(cy-ay)
						 -(cx-ax)*(cx-ax)*(by-ay))
						/
						(2.0 * ((bx-ax)*(cy-ay)-(cx-ax)*(by-ay))));
		
		//logger.info ("Ending ax="+ax+" bx="+bx+" cx="+cx+" tx="+tx);
		//logger.info ("Ending ay="+ay+" by="+by+" cy="+cy);
		
		MatrixOps.plusEquals(parameters,line,tx - ox);		
		optimizable.setParameters (parameters);
		//assert (function.getValue() >= origY);
		logger.info ("Ending cost = "+optimizable.getValue());

		// As a suggestion for the next initalStep, return the distance
		// from our initialStep to the minimum we found.
		//System.err.println(iterations + " total iterations in B.");
		//System.exit(0);
		
		return Math.max(1,tx - initialStep);
	}

}
