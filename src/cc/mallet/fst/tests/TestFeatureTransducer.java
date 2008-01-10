/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.fst.tests;

import junit.framework.*;
import java.net.URI;
import java.util.Iterator;

import cc.mallet.fst.*;
import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class TestFeatureTransducer extends TestCase
{
	public TestFeatureTransducer (String name)
	{
		super (name);
	}

	FeatureTransducer transducer;
	ArrayListSequence seq;
	double seqWeight;

	public void setUp ()
	{
		System.out.println ("Setup");
		transducer = new FeatureTransducer ();
		FeatureTransducer t = transducer;
		t.addState ("0", 0, Transducer.IMPOSSIBLE_WEIGHT,
								new String[] {"a", "b"},
								new String[] {"x", "y"},
								new double[] {44, 66},
								new String[] {"0", "1"});
		t.addState ("1", Transducer.IMPOSSIBLE_WEIGHT, Transducer.IMPOSSIBLE_WEIGHT,
								new String[] {"c", "d", "d"},
								new String[] {"x", "y", "z"},
								new double[] {44, 11, 66},
								new String[] {"1", "1", "2"});
		t.addState ("2", Transducer.IMPOSSIBLE_WEIGHT, 8,
								new String[] {"e"},
								new String[] {"z"},
								new double[] {11},
								new String[] {"2"});

		seq = new ArrayListSequence ();
		Alphabet dict = transducer.getInputAlphabet ();
		seq.add ("a");
		seq.add ("a");
		seq.add ("b");
		seq.add ("c");
		seq.add ("d");
		seq.add ("e");

		seqWeight = 0 + 44 + 44 + 66 + 44 + 66 + 11 + 8;
	}

	public void testInitialState ()
	{
		Iterator iter = transducer.initialStateIterator ();
		int count = 0;
		FeatureTransducer.State state;
		while (iter.hasNext ()) {
			count++;
			state = (FeatureTransducer.State) iter.next();
			assertTrue (state.getName().equals ("0"));
		}
		assertTrue (count == 1);
	}

	public void testForwardBackward ()
	{
		SumLatticeDefault lattice = new SumLatticeDefault (transducer, seq);
		System.out.println ("weight= "+lattice.getTotalWeight());
		assertTrue (lattice.getTotalWeight() == seqWeight);
	}

	public void testViterbi ()
	{
		double weight = new MaxLatticeDefault (transducer, seq).bestWeight();
		System.out.println ("weight = "+weight);
		assertTrue (weight == seqWeight);
	}

	public void testEstimate ()
	{
		transducer.setTrainable (true);
		SumLatticeDefault lattice = new SumLatticeDefault (transducer, seq); // used to have third argument: true
		double oldWeight = lattice.getTotalWeight ();
		transducer.estimate ();
		lattice = new SumLatticeDefault (transducer, seq); // used to have third argument: false
		double newWeight = lattice.getTotalWeight ();
		System.out.println ("oldWeight="+oldWeight+" newWeight="+newWeight);
		assertTrue (newWeight < oldWeight);
	}

	public void testIncrement ()
	{
		transducer.setTrainable (true);
		SumLatticeDefault lattice = new SumLatticeDefault (transducer, seq); // used to have third argument: true
		double oldWeight = lattice.getTotalWeight ();
		System.out.println ("State 0 transition estimator");
		Multinomial.Estimator est
			= ((FeatureTransducer.State)transducer.getState(0)).getTransitionEstimator();
		est.print();
		assertTrue (est.getCount(0) == 2.0);
		assertTrue (est.getCount(1) == 1.0);
	}
	
	public static Test suite ()
	{
		return new TestSuite (TestFeatureTransducer.class);
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
