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


public interface Optimizer
{
	
  // Returns true if it has converged
	// TODO change this to "optimize"
	public boolean optimize ();
	public boolean optimize (int numIterations);
	public boolean isConverged();
	public Optimizable getOptimizable();
	
	@Deprecated // Figure out the right interface for this.  It is odd that 'sampleAssignments' reaches into InstanceList indices
  public interface ByBatches {
  	public boolean optimize (int numSamples, int[] sampleAssigments);
  	public boolean optimize (int numIterations, int numSamples, int[] sampleAssignments);
  }
	
  
  
  // Rest below is deprecated

  /*
  public interface ByValue {
	// Returns true if it has converged
	  public boolean maximize (Optimizable.ByValue maxable);
	  public boolean maximize (Optimizable.ByValue maxable, int numIterations);
  }

	public interface ByGradient {
		// Returns true if it has converged
		public boolean maximize (Optimizable.ByValue maxable);
		public boolean maximize (Optimizable.ByValue maxable, int numIterations);
	}
	
	public interface ByValueGradient {
		// Returns true if it has converged
		public boolean maximize (Optimizable.ByGradientValue maxable);
		public boolean maximize (Optimizable.ByGradientValue maxable, int numIterations);
	}

	public interface ByHessian {
		// Returns true if it has converged
		public boolean maximize (Optimizable.ByHessian minable);
		public boolean maximize (Optimizable.ByHessian minable, int numIterations);
	}

	public interface ByGISUpdate {
		// Returns true if it has converged
		public boolean maximize (Optimizable.ByGISUpdate maxable);
		public boolean maximize (Optimizable.ByGISUpdate maxable, int numIterations);
	}
	
    public interface ByBatchGradient {
        // Returns true if it has converged
		public boolean maximize (Optimizable.ByBatchGradient maxable, int numSamples, int[] sampleAssigments);
		public boolean maximize (Optimizable.ByBatchGradient maxable, int numIterations, int numSamples, int[] sampleAssignments);
    }
    */
}
