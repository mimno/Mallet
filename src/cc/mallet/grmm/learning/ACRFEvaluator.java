/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.learning;



import java.io.File;
import java.util.List;

import cc.mallet.grmm.util.GeneralUtils;
import cc.mallet.types.InstanceList;
import cc.mallet.util.FileUtils;

/**
 *  
 * Created: Sun Jan 25 23:28:45 2004
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: ACRFEvaluator.java,v 1.1 2007/10/22 21:37:43 mccallum Exp $
 */
public abstract class ACRFEvaluator {

	// Evaulation settings
	private int numIterToSkip = 10;
	private int numIterToWait = 1;

	public void setNumIterToSkip (int n) { numIterToSkip = n; }
	public void setNumIterToWait (int n) { numIterToWait = n; }

  /**
   * Evalutes the model in the middle of training.
   * @param acrf Model tha is being trained.
   * @param iter How many iterations have been completed.
   * @param training    Training set.
   * @param validation  Validation set; may be null.
   * @param testing     Testing set; maybe null.
   * @return Whether to continue training.  If return is false, training should be be stopped.
   */
  public abstract boolean evaluate (ACRF acrf, int iter,
												  				 InstanceList training,
													  			 InstanceList validation,
														  		 InstanceList testing);

	public abstract void test (InstanceList gold, List returned,
														 String description);

	public void test (ACRF acrf, InstanceList data, String description)
	{
		List ret = acrf.getBestLabels (data);
		test (data, ret, description);
	}

	private File outputPrefix;

	public void setOutputPrefix (File file) { outputPrefix = file; }
	
	protected File makeOutputFile ()
	{
		try {
			String name = GeneralUtils.classShortName (this);
			return FileUtils.uniqueFile (outputPrefix, name, ".log");
		} catch (java.io.IOException e) {
			throw new RuntimeException (e);
		}
	}

	protected boolean shouldDoEvaluate (int iter)
	{
		if (iter < numIterToWait) {
			return false;
		} else {
			return (numIterToSkip <= 0) || (iter % numIterToSkip == 0);
		}
	}
	
} // ACRFEvaluator
