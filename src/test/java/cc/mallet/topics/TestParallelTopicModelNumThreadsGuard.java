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
import cc.mallet.types.LabelSequence;

/**
 * Regression test for https://github.com/mimno/Mallet/issues/219, robustness finding
 * "numThreads > numDocs silently serialises the run": ParallelTopicModel#estimate computed
 * docsPerThread = data.size() / numThreads, which is 0 whenever numThreads exceeds the
 * corpus size, so every thread but the last got zero documents while the last took the
 * entire corpus -- correct, but with all parallelism lost and numThreads full copies of
 * typeTopicCounts still allocated regardless. estimate() now clamps numThreads to the
 * corpus size (with a log warning) before allocating any of that.
 */
public class TestParallelTopicModelNumThreadsGuard {

    private static final String[] DOCUMENTS = new String[] {
        "cat dog cat bird dog",
        "soup pasta bread soup cheese",
        "guitar drum piano guitar violin",
    };

    @Test
    public void numThreadsIsClampedToCorpusSize() throws Exception {
        ArrayList<Pipe> pipes = new ArrayList<Pipe>();
        pipes.add(new CharSequence2TokenSequence());
        pipes.add(new TokenSequenceLowercase());
        pipes.add(new TokenSequence2FeatureSequence());

        InstanceList instances = new InstanceList(new SerialPipes(pipes));
        instances.addThruPipe(new StringArrayIterator(DOCUMENTS));

        ParallelTopicModel model = new ParallelTopicModel(4, 4.0, 0.01);
        model.setNumThreads(16); // far more threads than the 3-document corpus below
        model.setRandomSeed(42);
        model.setOptimizeInterval(0);
        model.addInstances(instances);
        model.setNumIterations(5);
        model.setTopicDisplay(0, 0);
        model.printLogLikelihood = false;

        model.estimate();

        assertEquals(DOCUMENTS.length, model.numThreads);

        // Not just "didn't crash": every token should still have a valid topic assignment,
        // confirming the clamped run actually produced a coherent model.
        for (int doc = 0; doc < model.data.size(); doc++) {
            LabelSequence topics = model.data.get(doc).topicSequence;
            for (int pi = 0; pi < topics.getLength(); pi++) {
                int topic = topics.getIndexAtPosition(pi);
                assertTrue(topic >= 0 && topic < model.numTopics);
            }
        }
    }
}
