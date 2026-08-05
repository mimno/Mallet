package cc.mallet.pipe;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Test;

import cc.mallet.types.Instance;

/**
 * Regression tests for the code-point iteration bug reported in
 * https://github.com/mimno/Mallet/issues/219 (correctness finding #2:
 * "Tokenizers truncate documents with emoji / astral chars").
 *
 * {@link SimpleTokenizer#pipe} used to bound its loop by
 * {@code Character.codePointCount(...)} while advancing the iteration
 * variable by a fixed 1 and feeding it straight into
 * {@code Character.codePointAt(characters, i)}, which expects a *char*
 * (UTF-16 code unit) index, not a code-point index. For text containing any
 * character outside the Basic Multilingual Plane (most emoji, some CJK
 * extension and mathematical alphanumeric characters), each such character
 * shortens the number of loop iterations by one relative to the true char
 * length, so the tokenizer silently loses characters from the *end* of the
 * document -- regardless of how far the offending character is from the end.
 */
public class SimpleTokenizerTest {

    // U+1F622 CRYING FACE, encoded as a Java surrogate pair.
    private static final String CRYING_FACE = "😢";
    // U+1F600 GRINNING FACE, encoded as a Java surrogate pair.
    private static final String GRINNING_FACE = "😀";

    private ArrayList<String> tokenize(String text) {
        SimpleTokenizer tokenizer = new SimpleTokenizer(SimpleTokenizer.USE_EMPTY_STOPLIST);
        Instance instance = new Instance(text, null, "test", null);
        instance = tokenizer.pipe(instance);
        @SuppressWarnings("unchecked")
        ArrayList<String> tokens = (ArrayList<String>) instance.getData();
        return tokens;
    }

    @Test
    public void plainAsciiIsUnaffected() {
        ArrayList<String> tokens = tokenize("alpha beta gamma delta epsilon");
        assertEquals(java.util.Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon"), tokens);
    }

    @Test
    public void accentedBmpTextIsUnaffected() {
        // Sanity check: ordinary (non-supplementary) non-ASCII letters, which is the
        // overwhelmingly common case for real corpora, must not be affected by the fix.
        ArrayList<String> tokens = tokenize("café naïve");
        assertEquals(java.util.Arrays.asList("café", "naïve"), tokens);
    }

    @Test
    public void emojiInsideAWordNoLongerTruncatesTheRestOfTheWord() {
        // Reproduces the exact scenario from the issue: an emoji inside "epsilon" used to
        // drop the trailing "n", producing "epsilo".
        ArrayList<String> tokens = tokenize("alpha beta gamma delta epsilo" + CRYING_FACE + "n");
        assertEquals(java.util.Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon"), tokens);
    }

    @Test
    public void twoSupplementaryCharactersInOneWordAreHandledCorrectly() {
        // Each supplementary character used to shorten the loop bound by one *more*
        // char, compounding the truncation ("epsil" instead of "epsilon" with two emoji).
        ArrayList<String> tokens = tokenize("alpha beta gamma delta epsi" + CRYING_FACE + CRYING_FACE + "lon");
        assertEquals(java.util.Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon"), tokens);
    }

    @Test
    public void emojiEarlierInTheStringNoLongerTruncatesALaterWord() {
        // The bug is not confined to the word containing the emoji: because the loop bound
        // is a single count for the *whole* string, an emoji anywhere causes the *last*
        // word of the document to lose its final character(s), even though the emoji
        // itself is nowhere near that word. Before the fix this produced ["hello", "worl"].
        ArrayList<String> tokens = tokenize("hello " + GRINNING_FACE + " world");
        assertEquals(java.util.Arrays.asList("hello", "world"), tokens);
    }

    @Test
    public void trailingSupplementaryCharacterDoesNotLoseAnyRealCharacters() {
        // Boundary case: when the supplementary character truly is the last thing in the
        // document, there is nothing after it left to lose either before or after the fix.
        ArrayList<String> tokens = tokenize("trailing emoji at the very end " + CRYING_FACE);
        assertEquals(
            java.util.Arrays.asList("trailing", "emoji", "at", "the", "very", "end"), tokens);
    }
}
