/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
 * Implementation of Salakhutdinav and Roweis Adaptive Overrelaxed GIS (2003)
   @author Ryan McDonald <a href="mailto:ryantm@cis.upenn.edu">ryantm@cis.upenn.edu</a>
 */

package cc.mallet.optimize;

import java.util.logging.*;

import cc.mallet.optimize.Optimizable;
import cc.mallet.types.MatrixOps;

public class AGIS implements Optimizer
{
	private static Logger logger =
	Logger.getLogger("edu.umass.cs.mallet.base.minimize.AGIS");

	double initialStepSize = 1;
	double alpha;
	double eta = 1.0;
	double tolerance = 0.0001;
	int maxIterations = 200;
	Optimizable.ByGISUpdate maxable;
	boolean converged = false;

	boolean backTrack;
	
	// "eps" is a small number to recitify the special case of converging
	// to exactly zero function value
	final double eps = 1.0e-10;
	
	public AGIS (Optimizable.ByGISUpdate maxable, double alph)
	{
		this(maxable,alph,true);
	}

	public AGIS (Optimizable.ByGISUpdate maxable, double alph, boolean backTrack)
	{
		this.maxable = maxable;
		this.alpha = alph;
		this.backTrack = backTrack;
	}
	
	public Optimizable getOptimizable () { return maxable; }
	public boolean isConverged () { return converged; }

	public boolean optimize () {
		return optimize (maxIterations);
	}
	
	public boolean optimize (int numIterations)
	{
		int iterations;
		double[] params = new double[maxable.getNumParameters()];
		double[] gis = new double[maxable.getNumParameters()];
		double[] old_params = new double[maxable.getNumParameters()];
		double[] updates = new double[maxable.getNumParameters()];
		
		maxable.getParameters(params);
		maxable.getParameters(gis);
		maxable.getParameters(old_params);

		
		for (iterations = 0; iterations < numIterations; iterations++) {

			boolean complete = false;
			double old = maxable.getValue();
			maxable.getGISUpdate(updates);
			MatrixOps.plusEquals(gis,updates);
			MatrixOps.plusEquals(params,updates,eta);
			maxable.setParameters(params);
			double next = maxable.getValue();
			
			// Different from normal AGIS, only fall back to GIS updates
			// If log-likelihood gets worse
			// i.e. if lower log-likelihood, always make AGIS update
			if(next > old) {
				complete = true;
				// don't let eta get too large
				if(eta*alpha < 99999999.0)
					eta = eta*alpha;
			}

			if(backTrack && complete == false) {
				// gone too far
				// unlike Roweis et al., we will back track on eta to find
				// acceptable value, instead of automatically setting it to 1
				while(eta > 1.0 && complete == false) {
					
					eta = eta/2.0;
					
					MatrixOps.set(params,old_params);
					
					MatrixOps.plusEquals(params,updates,eta);		
					maxable.setParameters(params);
					next = maxable.getValue();
					
					if(next > old)
						complete = true;
					
				}
			}
			else if(complete == false) {
				maxable.setParameters(gis);
				eta = 1.0;
				next = maxable.getValue();
			}
			
			logger.info("eta: " + eta);
			
			if (2.0*Math.abs(next-old) <= tolerance*(Math.abs(next)+Math.abs(old)+eps)) {
				converged = true;
				return true;
			}
			
			if(numIterations > 1) {
				maxable.getParameters(params);
				maxable.getParameters(old_params);
				maxable.getParameters(gis);
			}
		}
		converged = false;
		return false;
	}
	
}
