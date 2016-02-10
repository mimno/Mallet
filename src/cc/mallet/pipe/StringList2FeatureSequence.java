package cc.mallet.pipe;

import java.io.*;
import java.util.ArrayList;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;

/**
 * Convert a list of strings into a feature sequence
 */

public class StringList2FeatureSequence extends Pipe {

    public long totalNanos = 0;

	public StringList2FeatureSequence (Alphabet dataDict) {
		super (dataDict, null);
	}

	public StringList2FeatureSequence () {
		super(new Alphabet(), null);
	}
	
	public Instance pipe (Instance carrier) {

		long start = System.nanoTime();

		try {
			ArrayList<String> tokens = (ArrayList<String>) carrier.getData();
			FeatureSequence featureSequence =
				new FeatureSequence ((Alphabet) getDataAlphabet(), tokens.size());
			for (int i = 0; i < tokens.size(); i++) {
				featureSequence.add (tokens.get(i));
			}
			carrier.setData(featureSequence);
			
			totalNanos += System.nanoTime() - start;
		} catch (ClassCastException cce) {
			System.err.println("Expecting ArrayList<String>, found " + carrier.getData().getClass());
		}

		return carrier;
	}

	static final long serialVersionUID = 1;
}
