package cc.mallet.pipe;

import cc.mallet.types.*;
import gnu.trove.*;
import java.io.*;

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

public class FeatureDocFreqPipe extends Pipe {
		
	FeatureCounter counter;
	int numInstances;

	public FeatureDocFreqPipe() {
		super(new Alphabet(), null);

		counter = new FeatureCounter(this.getDataAlphabet());
		numInstances = 0;
	}
		
	public FeatureDocFreqPipe(Alphabet dataAlphabet, Alphabet targetAlphabet) {
		super(dataAlphabet, targetAlphabet);

		counter = new FeatureCounter(dataAlphabet);
		numInstances = 0;
	}

	public Instance pipe(Instance instance) {
		
		TIntIntHashMap localCounter = new TIntIntHashMap();
	
		if (instance.getData() instanceof FeatureSequence) {
				
			FeatureSequence features = (FeatureSequence) instance.getData();

			for (int position = 0; position < features.size(); position++) {
				localCounter.adjustOrPutValue(features.getIndexAtPosition(position), 1, 1);
			}

		}
		else {
			throw new IllegalArgumentException("Looking for a FeatureSequence, found a " + 
											   instance.getData().getClass());
		}

		for (int feature: localCounter.keys()) {
			counter.increment(feature);
		}

		numInstances++;

		return instance;
	}

	/** 
	 *  Add all pruned words to the internal stoplist of a SimpleTokenizer.
	 * 
	 * @param docFrequencyCutoff Remove words that occur in greater than this proportion of documents. 0.05 corresponds to IDF >= 3.
	 */
	public void addPrunedWordsToStoplist(SimpleTokenizer tokenizer, double docFrequencyCutoff) {
		Alphabet currentAlphabet = getDataAlphabet();

        for (int feature = 0; feature < currentAlphabet.size(); feature++) {
            if ((double) counter.get(feature) / numInstances > docFrequencyCutoff) {
                tokenizer.stop((String) currentAlphabet.lookupObject(feature));
            }
        }
	}

	static final long serialVersionUID = 1;

}
