/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.cluster.evaluate;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.evaluate.*;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

import junit.framework.*;

/**
 * Examples drawn from Luo, "On Coreference Resolution Performance
 * Metrics", HLT 2005.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see TestCase
 */
public class TestClusteringEvaluators extends TestCase
{
	public TestClusteringEvaluators (String name)
	{
		super (name);
	}

	private Clustering generateTruth (InstanceList instances) {
		int[] labels = new int[]{0,0,0,0,0,1,1,2,2,2,2,2};
		return new Clustering(instances, 3, labels);
	}
	
	private Clustering[] generatePredicted (InstanceList instances) {
		Clustering[] clusterings = new Clustering[4];
		clusterings[0] = new Clustering(instances, 2, new int[]{0,0,0,0,0,1,1,1,1,1,1,1});
		clusterings[1] = new Clustering(instances, 2, new int[]{0,0,0,0,0,1,1,0,0,0,0,0});
		clusterings[2] = new Clustering(instances, 1, new int[]{0,0,0,0,0,0,0,0,0,0,0,0});
		clusterings[3] = new Clustering(instances, 12, new int[]{0,1,2,3,4,5,6,7,8,9,10,11});
		return clusterings;
	}
	
	public void testEvaluators ()
	{
		InstanceList instances = new InstanceList(new Randoms(1), 100, 2).subList(0,12);
		System.err.println(instances.size() + " instances");
		Clustering truth = generateTruth(instances);
		System.err.println("truth=" + truth);

		Clustering[] predicted = generatePredicted(instances);
		ClusteringEvaluator pweval = new PairF1Evaluator();
		ClusteringEvaluator bceval = new BCubedEvaluator();
		ClusteringEvaluator muceval = new MUCEvaluator();

		for (int i = 0; i < predicted.length; i++) {
			System.err.println("\npred" + i + "=" + predicted[i]);
			System.err.println("pairs: " + pweval.evaluate(truth, predicted[i]));
			System.err.println("bcube: " + bceval.evaluate(truth, predicted[i]));
			System.err.println("  muc: " + muceval.evaluate(truth, predicted[i]));
		}

		System.err.println("totals:");
		System.err.println("pairs: " + pweval.evaluateTotals());
		System.err.println("bcube: " + bceval.evaluateTotals());
		System.err.println("  muc: " + muceval.evaluateTotals());

		assertTrue(pweval.evaluateTotals().matches(".*f1=0\\.5550.*"));
		assertTrue(bceval.evaluateTotals().matches(".*f1=0\\.7404.*"));
		assertTrue(muceval.evaluateTotals().matches(".*f1=0\\.8059.*"));
	}

	public static Test suite ()
	{
		return new TestSuite (TestClusteringEvaluators.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
		
