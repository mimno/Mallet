package cc.mallet.topics;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Test;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.iterator.StringArrayIterator;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * Regression test for https://github.com/mimno/Mallet/issues/219, correctness finding #6:
 * smoothingOnlyMass was otherwise maintained only by incremental +=/-= updates as tokens are
 * resampled, so floating-point drift in that running total compounds over a long run.
 * WorkerCallable#sampleTopicsForOneDoc now recomputes it exactly at the start of every
 * document instead of relying on the once-per-call() (once per worker per training
 * iteration) initialization, so an arbitrarily wrong value can survive for at most one
 * document.
 */
public class TestWorkerCallable {

    private static final String[] DOCUMENTS = new String[] {
        "cat dog cat bird dog cat fish bird cat dog",
        "soup pasta bread soup cheese pasta bread soup cheese",
        "guitar drum piano guitar violin drum piano guitar",
        "dog cat fish dog bird cat dog fish bird cat",
    };

    // Forces every draw to land deep in whichever bucket dominates the total, so the test
    // doesn't depend on a particular seed happening to produce a small enough value.
    private static final class FixedUniformRandoms extends Randoms {
        FixedUniformRandoms(int seed) { super(seed); }

        @Override
        public synchronized double nextUniform() { return 0.9; }
    }

    private static ParallelTopicModel trainSmallModel() throws Exception {
        ArrayList<Pipe> pipes = new ArrayList<Pipe>();
        pipes.add(new CharSequence2TokenSequence());
        pipes.add(new TokenSequenceLowercase());
        pipes.add(new TokenSequence2FeatureSequence());

        InstanceList instances = new InstanceList(new SerialPipes(pipes));
        instances.addThruPipe(new StringArrayIterator(DOCUMENTS));

        ParallelTopicModel model = new ParallelTopicModel(4, 4.0, 0.01);
        model.setNumThreads(1);
        model.setRandomSeed(42);
        model.setOptimizeInterval(0);
        model.addInstances(instances);
        model.setNumIterations(5);
        model.setTopicDisplay(0, 0);
        model.printLogLikelihood = false;
        model.estimate();
        return model;
    }

    @Test
    public void smoothingOnlyMassRecoversFromInjectedDriftWithinOneDocument() throws Exception {
        ParallelTopicModel model = trainSmallModel();

        WorkerCallable worker = new WorkerCallable(
            model.numTopics, model.alpha, model.alphaSum, model.beta,
            new FixedUniformRandoms(7), model.data, model.getTypeTopicCounts(),
            model.getTokensPerTopic(), 0, model.data.size());
        worker.makeOnlyThread();

        // Establish a realistic baseline (cachedCoefficients for every topic, a
        // legitimately-computed smoothingOnlyMass) the same way ParallelTopicModel does.
        worker.call();

        // Simulate the drift the bug report describes: a smoothingOnlyMass wildly
        // inconsistent with the exact per-topic sum. Before this fix, feeding this into
        // sampleTopicsForOneDoc -- combined with a uniform draw skewed toward the (now huge)
        // smoothing-only bucket -- walked the smoothing-only branch past numTopics and threw
        // ArrayIndexOutOfBoundsException reading alpha[numTopics].
        worker.smoothingOnlyMass = 1_000_000.0;

        FeatureSequence tokens = (FeatureSequence) model.data.get(0).instance.getData();
        FeatureSequence topics = model.data.get(0).topicSequence;

        worker.sampleTopicsForOneDoc(tokens, topics, true);

        double exactSmoothingOnlyMass = 0.0;
        int[] tokensPerTopic = model.getTokensPerTopic();
        for (int topic = 0; topic < model.numTopics; topic++) {
            exactSmoothingOnlyMass +=
                model.alpha[topic] * model.beta / (tokensPerTopic[topic] + model.betaSum);
        }

        assertEquals(exactSmoothingOnlyMass, worker.smoothingOnlyMass, 1e-12);
    }
}
