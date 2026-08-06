package cc.mallet.topics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.util.Randoms;

/**
 * Regression test for https://github.com/mimno/Mallet/issues/219, correctness finding #6:
 * the smoothing-only branch of the sampler walks topics with no bound, so if the
 * (independently-computed) smoothingOnlyMass used to draw a sample is ever inconsistent with
 * the per-topic values used in the walk -- for example residual floating-point drift -- the
 * walk could run past the last topic and throw ArrayIndexOutOfBoundsException reading
 * alpha[numTopics]. TopicInferencer#getSampledDistribution now clamps the walk instead.
 */
public class TestTopicInferencer {

    private static final int NUM_TOPICS = 3;

    // Forces every draw to land deep in the (here, deliberately inflated) smoothing-only
    // bucket, so the test doesn't depend on a particular seed happening to produce a small
    // enough value.
    private static final class FixedUniformRandoms extends Randoms {
        FixedUniformRandoms(int seed) { super(seed); }

        @Override
        public synchronized double nextUniform() { return 0.9; }
    }

    @Test
    public void smoothingOnlyWalkIsClampedInsteadOfReadingPastTheLastTopic() {
        double[] alpha = { 1.0, 1.0, 1.0 };
        double beta = 0.01;
        double betaSum = 0.01;

        // A single word type, with counts spread across all three topics, encoded as
        // (count << topicBits) + topic, sorted descending by count and terminated by 0.
        int[][] typeTopicCounts = new int[][] {
            { (5 << 2) + 0, (3 << 2) + 1, (1 << 2) + 2, 0 }
        };
        int[] tokensPerTopic = { 5, 3, 1 };

        Alphabet alphabet = new Alphabet();
        alphabet.lookupIndex("w", true);
        alphabet.stopGrowth();

        FeatureSequence tokens = new FeatureSequence(alphabet, new int[] { 0, 0, 0 });
        Instance instance = new Instance(tokens, null, "test", null);

        TopicInferencer inferencer =
            new TopicInferencer(typeTopicCounts, tokensPerTopic, alphabet, alpha, beta, betaSum);
        inferencer.random = new FixedUniformRandoms(3);

        // Simulate the kind of inconsistency the bug report describes: a smoothingOnlyMass
        // wildly larger than the exact per-topic sum it is supposed to represent. Before this
        // fix, this -- combined with a uniform draw skewed toward the (now huge) smoothing-only
        // bucket -- read alpha[numTopics] out of bounds.
        inferencer.smoothingOnlyMass = 1_000_000.0;

        double[] distribution = inferencer.getSampledDistribution(instance, 5, 1, 0);

        assertNotNull(distribution);
        assertEquals(NUM_TOPICS, distribution.length);

        double sum = 0.0;
        for (double p : distribution) {
            assertFalse("distribution entry should be finite", Double.isNaN(p) || Double.isInfinite(p));
            assertTrue("distribution entry should be non-negative", p >= 0.0);
            sum += p;
        }
        assertEquals(1.0, sum, 1e-9);
    }
}
