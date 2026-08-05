package cc.mallet.pipe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;

/**
 * Regression tests for the same code-point iteration bug covered by
 * {@link TestSimpleTokenizer}, applied to {@link FixedVocabTokenizer}, which has an
 * identical (copy-pasted) tokenization loop -- see
 * https://github.com/mimno/Mallet/issues/219, correctness finding #2.
 */
public class TestFixedVocabTokenizer {

    // U+1F622 CRYING FACE, encoded as a Java surrogate pair.
    private static final String CRYING_FACE = "😢";

    private String[] tokenize(String text, String... vocabulary) {
        Alphabet alphabet = new Alphabet();
        for (String word : vocabulary) {
            alphabet.lookupIndex(word, true);
        }
        alphabet.stopGrowth();

        FixedVocabTokenizer tokenizer = new FixedVocabTokenizer(alphabet);
        Instance instance = new Instance(text, null, "test", null);
        instance = tokenizer.pipe(instance);

        FeatureSequence sequence = (FeatureSequence) instance.getData();
        String[] tokens = new String[sequence.getLength()];
        for (int i = 0; i < sequence.getLength(); i++) {
            tokens[i] = (String) sequence.getObjectAtPosition(i);
        }
        return tokens;
    }

    @Test
    public void testPlainAsciiIsUnaffected() {
        String[] tokens = tokenize("alpha beta gamma delta epsilon",
            "alpha", "beta", "gamma", "delta", "epsilon");
        assertEquals(java.util.Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon"),
            java.util.Arrays.asList(tokens));
    }

    @Test
    public void testEmojiInsideAWordNoLongerTruncatesTheRestOfTheWord() {
        // Only "epsilon" is in the fixed vocabulary, so this also verifies that after the
        // fix the fully-reassembled 7-letter token is what actually gets looked up --
        // the truncated 6-letter "epsilo" would silently fail the alphabet.contains() check
        // and be dropped instead of counted.
        String[] tokens = tokenize("alpha beta gamma delta epsilo" + CRYING_FACE + "n",
            "alpha", "beta", "gamma", "delta", "epsilon");
        assertEquals(java.util.Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon"),
            java.util.Arrays.asList(tokens));
    }
}
