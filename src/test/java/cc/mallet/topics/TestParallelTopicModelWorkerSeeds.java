package cc.mallet.topics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.iterator.StringArrayIterator;
import cc.mallet.types.InstanceList;

/**
 * Regression tests for https://github.com/mimno/Mallet/issues/219, correctness finding #1
 * ("every worker thread gets the same RNG seed"). mimno's specific follow-up question on the
 * issue -- "Is the run still deterministic over multiple workers with this change? That's
 * the part that people will care about." -- is what these tests are meant to answer with
 * evidence, not just argument.
 *
 * Scope note, per the later discussion on #219: the original report's claim that per-worker
 * seeding *improves* model quality did not hold up under a corrected (unpaired) significance
 * test once mimno pushed back on it. This change is kept purely as hygiene -- it removes a
 * real, measured loss of independence between workers -- not as a quality fix; each draw was
 * already a valid sample from its own conditional either way.
 */
public class TestParallelTopicModelWorkerSeeds {

    @Test
    public void derivedSeedsAreDeterministic() {
        int[] first = ParallelTopicModel.deriveWorkerSeeds(42, 8);
        int[] second = ParallelTopicModel.deriveWorkerSeeds(42, 8);
        assertArrayEquals(first, second);
    }

    @Test
    public void derivedSeedsDifferPerThread() {
        int[] seeds = ParallelTopicModel.deriveWorkerSeeds(42, 8);

        Set<Integer> distinct = new HashSet<Integer>();
        for (int seed : seeds) {
            distinct.add(seed);
        }
        assertEquals("all per-worker seeds should be distinct from each other",
            seeds.length, distinct.size());
    }

    private static final String[] DOCUMENTS = new String[] {
        "cat dog cat bird dog cat fish bird cat dog",
        "soup pasta bread soup cheese pasta bread soup cheese",
        "guitar drum piano guitar violin drum piano guitar",
        "dog cat fish dog bird cat dog fish bird cat",
        "pasta cheese bread pasta soup cheese bread pasta soup",
        "piano violin guitar piano drum violin piano guitar drum",
        "cat bird fish cat dog bird cat fish dog cat",
        "cheese soup pasta cheese bread soup pasta cheese bread",
    };

    private static ParallelTopicModel trainMultiThreaded() throws Exception {
        ArrayList<Pipe> pipes = new ArrayList<Pipe>();
        pipes.add(new CharSequence2TokenSequence());
        pipes.add(new TokenSequenceLowercase());
        pipes.add(new TokenSequence2FeatureSequence());

        InstanceList instances = new InstanceList(new SerialPipes(pipes));
        instances.addThruPipe(new StringArrayIterator(DOCUMENTS));

        ParallelTopicModel model = new ParallelTopicModel(4, 4.0, 0.01);
        model.setNumThreads(4);
        model.setRandomSeed(42);
        model.setOptimizeInterval(0);
        model.addInstances(instances);
        model.setNumIterations(50);
        model.setTopicDisplay(0, 0);
        model.printLogLikelihood = false;
        model.estimate();
        return model;
    }

    @Test
    public void fixedSeedAndThreadCountReproducesIdenticalMultiThreadedRuns() throws Exception {
        // The exact property mimno asked about: with a fixed --random-seed and a fixed
        // --num-threads > 1, does re-running give the same answer? Nothing in this codebase
        // tested multi-threaded determinism before this fix -- the existing fixed-seed
        // regression test (TestParallelTopicModelRegression) pins numThreads=1 only.
        ParallelTopicModel first = trainMultiThreaded();
        ParallelTopicModel second = trainMultiThreaded();

        assertEquals(first.modelLogLikelihood(), second.modelLogLikelihood(), 0.0);
        assertTrue(Arrays.deepEquals(first.getTypeTopicCounts(), second.getTypeTopicCounts()));
    }
}
