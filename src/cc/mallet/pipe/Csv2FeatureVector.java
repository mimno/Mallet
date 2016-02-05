/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe;


import java.util.logging.*;
import java.util.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.Labeling;
import cc.mallet.util.MalletLogger;


/**
 * Converts a string of the form
 * <tt>feature_1:val_1 feature_2:val_2 ... feature_k:val_k</tt>
 * into a (sparse) FeatureVector.
 *
 * Features with no ":" character are assumed to have value 1.0.
 * 
 * @author Gary Huang
 */
public class Csv2FeatureVector extends Pipe {

    private static Logger logger = MalletLogger.getLogger(Csv2FeatureVector.class.getName());

    public Csv2FeatureVector(int capacity) {
        this.dataAlphabet = new Alphabet(capacity);
    }
    
    public Csv2FeatureVector() {
        this(1000);
    }
    
    /**
     * Convert the data in the given <tt>Instance</tt> from a <tt>CharSequence</tt> 
     * of sparse feature-value pairs to a <tt>FeatureVector</tt>
     */
    public Instance pipe(Instance carrier) {

        CharSequence c = (CharSequence) carrier.getData();
        String[] pairs = c.toString().trim().split("\\s+");
        int[] keys = new int[pairs.length];
        double[] values = new double[pairs.length];

        for (int i = 0; i < pairs.length; i++) {
			int delimIndex = pairs[i].lastIndexOf(":");
			if (delimIndex <= 0 || delimIndex == (pairs[i].length()-1)) {
				keys[i] = dataAlphabet.lookupIndex(pairs[i], true);
				values[i] = 1.0;
			}
			else {
				keys[i] = dataAlphabet.lookupIndex(pairs[i].substring(0, delimIndex), true);
				values[i] = Double.parseDouble(pairs[i].substring(delimIndex+1));
			}
        }

		// [removed code that sorted indices but NOT values -DM]

        FeatureVector fv = new FeatureVector(dataAlphabet, keys, values);
        carrier.setData( fv );
        return carrier;
    }
    
}
