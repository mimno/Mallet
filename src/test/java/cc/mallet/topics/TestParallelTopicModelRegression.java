/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.iterator.StringArrayIterator;
import cc.mallet.types.InstanceList;

/**
 * Pins down the exact token-topic assignments and log likelihood produced by a
 * fixed-seed, single-threaded LDA run on a small, fully in-memory corpus.
 *
 * The corpus is built from a literal String[] (not scanned from disk) so that
 * filesystem iteration order can never make this test flaky across operating
 * systems. The sampler itself only uses integer/double arithmetic and
 * java.util.Random's spec-guaranteed LCG (no Math.log/exp/pow, which are
 * allowed to differ in their low bits across JVMs and CPU architectures), so
 * the assignments and likelihood below should reproduce bit-for-bit on any
 * conforming JVM. If this test ever fails after an unrelated change, that
 * change altered sampling behavior, not just its performance.
 */
public class TestParallelTopicModelRegression {

	// Repeated, overlapping vocabulary across three loose "topics" so that many
	// word types accumulate counts under more than one topic, exercising the
	// sorted-array reordering logic in WorkerCallable and MergeCallable.
	static String[] DOCUMENTS = new String[] {
		"cat dog cat bird dog cat fish bird cat dog",
		"soup pasta bread soup cheese pasta bread soup cheese",
		"guitar drum piano guitar violin drum piano guitar",
		"dog cat fish dog bird cat dog fish bird cat",
		"pasta cheese bread pasta soup cheese bread pasta soup",
		"piano violin guitar piano drum violin piano guitar drum",
		"cat bird fish cat dog bird cat fish dog cat",
		"cheese soup pasta cheese bread soup pasta cheese bread",
		"drum piano violin drum guitar piano violin drum guitar",
		"bird dog cat bird fish dog cat bird fish dog",
		"bread pasta cheese bread soup pasta cheese bread soup",
		"violin guitar drum violin piano guitar drum violin piano",
		"fish cat dog fish bird cat dog fish bird cat",
		"soup cheese pasta soup bread cheese pasta soup bread",
		"guitar piano violin guitar drum piano violin guitar drum",
		"cat dog bird cat fish dog bird cat fish dog",
		"cheese bread soup cheese pasta bread soup cheese pasta",
		"piano drum guitar piano violin drum guitar piano violin",
		"dog bird fish dog cat bird fish dog cat bird",
		"pasta soup cheese pasta bread soup cheese pasta bread"
	};

	private static InstanceList buildCorpus() {
		ArrayList<Pipe> pipes = new ArrayList<Pipe>();
		pipes.add(new CharSequence2TokenSequence());
		pipes.add(new TokenSequenceLowercase());
		pipes.add(new TokenSequence2FeatureSequence());

		InstanceList instances = new InstanceList(new SerialPipes(pipes));
		instances.addThruPipe(new StringArrayIterator(DOCUMENTS));
		return instances;
	}

	private static ParallelTopicModel trainFixedModel() throws Exception {
		InstanceList instances = buildCorpus();

		ParallelTopicModel model = new ParallelTopicModel(4, 4.0, 0.01);

		// Pin every source of variation this test cares about. The random
		// seed must be set *before* addInstances(), which uses it to draw
		// the initial topic assignments.
		model.setNumThreads(1);
		model.setRandomSeed(42);
		model.setOptimizeInterval(0);

		model.addInstances(instances);
		model.setNumIterations(200);
		model.setTopicDisplay(0, 0);
		model.printLogLikelihood = false;

		model.estimate();
		return model;
	}

	@Test
	public void sameSeedProducesIdenticalAssignments() throws Exception {
		ParallelTopicModel first = trainFixedModel();
		ParallelTopicModel second = trainFixedModel();

		assertEquals(first.modelLogLikelihood(), second.modelLogLikelihood(), 0.0);
		assertTrue(Arrays.deepEquals(first.getTypeTopicCounts(), second.getTypeTopicCounts()));
	}

	@Test
	public void fixedSeedMatchesKnownGoodResult() throws Exception {
		ParallelTopicModel model = trainFixedModel();

		// Regenerate this fingerprint (with `mvn -Dtest=TestParallelTopicModelRegression test`)
		// only when a change to the sampler is intentional. If it changes
		// unexpectedly, sampling behavior -- not just performance -- was altered.
		assertEquals(-437.5468111850073, model.modelLogLikelihood(), 0.0);
		assertEquals(-294991111, Arrays.deepToString(model.getTypeTopicCounts()).hashCode());
	}
}
