package cc.mallet.pipe;

import cc.mallet.types.*;

import java.io.*;
import java.nio.charset.Charset;

/** 
 *  Pruning low-count features can be a good way to save memory and computation.
 *   However, in order to use Vectors2Vectors, you need to write out the unpruned
 *   instance list, read it back into memory, collect statistics, create new 
 *   instances, and then write everything back out.
 * <p>
 *  This class supports a simpler method that makes two passes over the data:
 *   one to collect statistics and create an augmented "stop list", and a
 *   second to actually create instances.
 */

public class FeatureCountPipe extends Pipe {
        
    FeatureCounter counter;

    public FeatureCountPipe() {
        super(new Alphabet(), null);

        counter = new FeatureCounter(this.getDataAlphabet());
    }
        
    public FeatureCountPipe(Alphabet dataAlphabet, Alphabet targetAlphabet) {
        super(dataAlphabet, targetAlphabet);

        counter = new FeatureCounter(dataAlphabet);
    }

    @Override public Instance pipe(Instance instance) {
            
        if (instance.getData() instanceof FeatureSequence) {
                
            FeatureSequence features = (FeatureSequence) instance.getData();

            for (int position = 0; position < features.size(); position++) {
                counter.increment(features.getIndexAtPosition(position));
            }

        }
        else {
            throw new IllegalArgumentException("Looking for a FeatureSequence, found a " + 
                                               instance.getData().getClass());
        }

        return instance;
    }

    public FeatureCounter getFeatureCounter() {
        return counter;
    }

    /**
     * Returns a new alphabet that contains only features at or above 
     *  the specified limit.
     */
    public Alphabet getPrunedAlphabet(int minimumCount) {
            
        Alphabet currentAlphabet = getDataAlphabet();
        Alphabet prunedAlphabet = new Alphabet();

        for (int feature = 0; feature < currentAlphabet.size(); feature++) {
            if (counter.get(feature) >= minimumCount) {
                prunedAlphabet.lookupIndex(currentAlphabet.lookupObject(feature));
            }
        }

        prunedAlphabet.stopGrowth();
        return prunedAlphabet;
            
    }

    /** 
     *  Writes a list of features that do not occur at or 
     *  above the specified cutoff to the pruned file, one per line.
     *  This file can then be passed to a stopword filter as 
     *  "additional stopwords".
     */
    public void writePrunedWords(File prunedFile, int minimumCount) throws IOException {

        PrintWriter out = new PrintWriter(prunedFile, Charset.defaultCharset().name());

        Alphabet currentAlphabet = getDataAlphabet();

        for (int feature = 0; feature < currentAlphabet.size(); feature++) {
            if (counter.get(feature) < minimumCount) {
                out.println(currentAlphabet.lookupObject(feature));
            }
        }

        out.close();
    }

    /** 
     *  Add all pruned words to the internal stoplist of a SimpleTokenizer.
     */
    public void addPrunedWordsToStoplist(SimpleTokenizer tokenizer, int minimumCount) {
        Alphabet currentAlphabet = getDataAlphabet();

        for (int feature = 0; feature < currentAlphabet.size(); feature++) {
            if (counter.get(feature) < minimumCount) {
                tokenizer.stop((String) currentAlphabet.lookupObject(feature));
            }
        }
    }

    /**
     * List the most common words, for addition to a stop file
     */
    public void writeCommonWords(File commonFile, int totalWords) throws IOException {

        PrintWriter out = new PrintWriter(commonFile, Charset.defaultCharset().name());
        
        Alphabet currentAlphabet = getDataAlphabet();

        IDSorter[] sortedWords = new IDSorter[currentAlphabet.size()];
        for (int type = 0; type < currentAlphabet.size(); type++) {
            sortedWords[type] = new IDSorter(type, counter.get(type));
        }
        
        java.util.Arrays.sort(sortedWords);

        int max = totalWords;
        if (currentAlphabet.size() < max) {
            max = currentAlphabet.size();
        }

        for (int rank = 0; rank < max; rank++) {
            int type = sortedWords[rank].getID();
            out.println (currentAlphabet.lookupObject(type));
        }

        out.close();

    }


    static final long serialVersionUID = 1;

}
