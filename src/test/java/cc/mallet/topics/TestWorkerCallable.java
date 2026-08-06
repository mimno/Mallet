package cc.mallet.topics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.iterator.StringArrayIterator;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * Regression test for https://github.com/mimno/Mallet/issues/219, correctness finding #5:
 * shouldSaveState is armed by collectAlphaStatistics() for exactly the call() that follows,
 * but nothing ever reset it back to false afterward, so every subsequent call() also
 * accumulated into docLengthCounts/topicDocCounts instead of only every saveSampleInterval-th
 * one. WorkerCallable#call() now resets shouldSaveState after the armed pass uses it.
 */
public class TestWorkerCallable {

    private static final String[] DOCUMENTS = new String[] {
        "cat dog cat bird dog cat fish bird cat dog",
        "soup pasta bread soup cheese pasta bread soup cheese",
        "guitar drum piano guitar violin drum piano guitar",
        "dog cat fish dog bird cat dog fish bird cat",
    };

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
    public void shouldSaveStateResetsAfterOneCall() throws Exception {
        ParallelTopicModel model = trainSmallModel();

        WorkerCallable worker = new WorkerCallable(
            model.numTopics, model.alpha, model.alphaSum, model.beta,
            new Randoms(11), model.data, model.getTypeTopicCounts(),
            model.getTokensPerTopic(), 0, model.data.size());
        worker.makeOnlyThread();
        worker.initializeAlphaStatistics(20);

        // Arms shouldSaveState for exactly the call() that follows -- mirrors
        // ParallelTopicModel calling collectAlphaStatistics() only on optimization
        // iterations.
        worker.collectAlphaStatistics();
        worker.call();

        int totalAfterFirstCall = sum(worker.getDocLengthCounts());
        assertTrue("the armed call() should have recorded at least one document",
            totalAfterFirstCall > 0);

        // Not re-armed: before this fix, shouldSaveState stayed true forever once set, so
        // this second, unarmed call() would double the histogram instead of leaving it
        // unchanged.
        worker.call();

        assertEquals(totalAfterFirstCall, sum(worker.getDocLengthCounts()));
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }
}
