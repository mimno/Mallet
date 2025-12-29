/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.classify;

import org.junit.Test;
import static org.junit.Assert.*;

import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.TestOptimizable;
import cc.mallet.types.*;
import cc.mallet.util.*;

public class TestMaxEntTrainer {

	private static Alphabet dictOfSize(int size) {
		Alphabet ret = new Alphabet();
		for (int i = 0; i < size; i++)
			ret.lookupIndex("feature" + i);
		return ret;
	}

	@Test
	public void testSetGetParameters() {
		MaxEntTrainer trainer = new MaxEntTrainer();
		Alphabet fd = dictOfSize(6);
		String[] classNames = new String[]{"class0", "class1", "class2"};
		InstanceList ilist = new InstanceList(new Randoms(1), fd, classNames, 20);
		Optimizable.ByGradientValue maxable = trainer.getOptimizable(ilist);
		TestOptimizable.testGetSetParameters(maxable);
	}

	@Test
	public void testRandomMaximizable() {
		MaxEntTrainer trainer = new MaxEntTrainer();
		Alphabet fd = dictOfSize(6);
		String[] classNames = new String[]{"class0", "class1"};
		InstanceList ilist = new InstanceList(new Randoms(1), fd, classNames, 20);
		Optimizable.ByGradientValue maxable = trainer.getOptimizable(ilist);
		TestOptimizable.testValueAndGradient(maxable);
	}

	// TODO This doesn't pass, but it didn't in the old MALLET either.  Why?? -akm 1/08
	@Test
	public void testTrainedMaximizable() {
		MaxEntTrainer trainer = new MaxEntTrainer();
		Alphabet fd = dictOfSize(6);
		String[] classNames = new String[]{"class0", "class1"};
		InstanceList ilist = new InstanceList(new Randoms(1), fd, classNames, 20);
		MaxEnt me = (MaxEnt) trainer.train(ilist);
		Optimizable.ByGradientValue maxable = trainer.getOptimizable(ilist, me);
		TestOptimizable.testValueAndGradientCurrentParameters(maxable);
	}

}
