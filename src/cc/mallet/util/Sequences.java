package cc.mallet.util;

import cc.mallet.types.Sequence;

/** Utility methods for cc.mallet.types.Sequence and similar classes. */
public class Sequences {

	public static double elementwiseAccuracy (Sequence truth, Sequence predicted) {
		int accuracy = 0;
		assert (truth.size() == predicted.size());
		for (int i = 0; i < predicted.size(); i++) {
			//logger.fine("tokenAccuracy: ref: "+referenceOutput.get(i)+" viterbi: "+output.get(i));
			if (truth.get(i).toString().equals (predicted.get(i).toString())) {
				accuracy++;
			}
		}
		return ((double)accuracy)/predicted.size();
	}

	
}
