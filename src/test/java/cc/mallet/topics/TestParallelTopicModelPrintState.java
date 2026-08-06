package cc.mallet.topics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.iterator.StringArrayIterator;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelSequence;

/**
 * Regression tests for https://github.com/mimno/Mallet/issues/219, performance finding #1
 * (printState). The per-token java.util.Formatter was replaced with a pre-resolved String[]
 * of vocabulary strings and direct StringBuilder appends, and the GZIP output now uses
 * Deflater.BEST_SPEED instead of the JDK's default level. Both are meant to be
 * performance-only changes: the decompressed content must be byte-for-byte the same text the
 * old implementation produced.
 */
public class TestParallelTopicModelPrintState {

    private static final String[] DOCUMENTS = new String[] {
        "cat dog cat bird dog cat fish bird cat dog",
        "soup pasta bread soup cheese pasta bread soup cheese",
        "guitar drum piano guitar violin drum piano guitar",
        "dog cat fish dog bird cat dog fish bird cat",
    };

    private static ParallelTopicModel trainModel(String[] corpus, int numIterations) throws Exception {
        ArrayList<Pipe> pipes = new ArrayList<Pipe>();
        pipes.add(new CharSequence2TokenSequence());
        pipes.add(new TokenSequenceLowercase());
        pipes.add(new TokenSequence2FeatureSequence());

        InstanceList instances = new InstanceList(new SerialPipes(pipes));
        instances.addThruPipe(new StringArrayIterator(corpus));

        ParallelTopicModel model = new ParallelTopicModel(4, 4.0, 0.01);
        model.setNumThreads(1);
        model.setRandomSeed(42);
        model.setOptimizeInterval(0);
        model.addInstances(instances);
        model.setNumIterations(numIterations);
        model.setTopicDisplay(0, 0);
        model.printLogLikelihood = false;
        model.estimate();
        return model;
    }

    // Mirrors printState's own format, independently, from the same public model state --
    // so the test doesn't just check the method against itself.
    private static List<String> expectedLines(ParallelTopicModel model) {
        List<String> lines = new ArrayList<String>();
        lines.add("#doc source pos typeindex type topic");

        StringBuilder alphaLine = new StringBuilder("#alpha : ");
        for (int topic = 0; topic < model.numTopics; topic++) {
            alphaLine.append(model.alpha[topic]).append(' ');
        }
        lines.add(alphaLine.toString());
        lines.add("#beta : " + model.beta);

        for (int doc = 0; doc < model.data.size(); doc++) {
            FeatureSequence tokens = (FeatureSequence) model.data.get(doc).instance.getData();
            LabelSequence topics = model.data.get(doc).topicSequence;

            String source = model.data.get(doc).instance.getSource() != null
                ? model.data.get(doc).instance.getSource().toString() : "NA";

            for (int pi = 0; pi < topics.getLength(); pi++) {
                int type = tokens.getIndexAtPosition(pi);
                int topic = topics.getIndexAtPosition(pi);
                lines.add(doc + " " + source + " " + pi + " " + type + " "
                    + model.alphabet.lookupObject(type) + " " + topic);
            }
        }
        return lines;
    }

    private static List<String> readGzipLines(File f) throws IOException {
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(f))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    @Test
    public void decompressedContentMatchesExpectedFormat() throws Exception {
        ParallelTopicModel model = trainModel(DOCUMENTS, 20);

        File stateFile = File.createTempFile("mallet-print-state-test", ".gz");
        stateFile.deleteOnExit();
        model.printState(stateFile);

        assertEquals(expectedLines(model), readGzipLines(stateFile));
    }

    @Test
    public void usesFastestDeflateLevelInsteadOfDefault() throws Exception {
        // Needs to be big enough for the level 1 vs. level 6 size difference to be
        // unambiguous rather than lost in fixed GZIP header/footer overhead.
        String[] biggerCorpus = new String[400];
        for (int i = 0; i < biggerCorpus.length; i++) {
            biggerCorpus[i] = DOCUMENTS[i % DOCUMENTS.length];
        }
        ParallelTopicModel model = trainModel(biggerCorpus, 5);

        File stateFile = File.createTempFile("mallet-print-state-test", ".gz");
        stateFile.deleteOnExit();
        model.printState(stateFile);

        StringBuilder text = new StringBuilder();
        for (String line : expectedLines(model)) {
            text.append(line).append('\n');
        }
        byte[] rawBytes = text.toString().getBytes("UTF-8");

        ByteArrayOutputStream defaultLevelBytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(defaultLevelBytes)) {
            gzip.write(rawBytes);
        }

        // Level 1 trades size for speed, so compressing the identical text should produce a
        // larger file than the JDK's default level 6 -- that trade is the whole point of this
        // change.
        assertTrue(
            "expected printState's file (" + stateFile.length() +
                " bytes) to be larger than default-level compression (" +
                defaultLevelBytes.size() + " bytes)",
            stateFile.length() > defaultLevelBytes.size());
    }
}
