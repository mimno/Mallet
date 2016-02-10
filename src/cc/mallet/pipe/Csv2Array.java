/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;


import java.util.logging.*;
import java.lang.reflect.Array;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.Labeling;
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.util.MalletLogger;

/**

   Converts a string of comma separated values to an array. To be used
	 prior to {@link Array2FeatureVector}. Note that this class assumes
	 that each location of the line corresponds to a feature index
	 (i.e. "dense" representation) eg:

	 instance 1: 1,0,0,1,0,0,1  << feature alphabet size = 7
	 instance 2: 0,0,1,0,0,0,1  << feature alphabet size = 7

 	 @author Aron Culotta
 */
public class Csv2Array extends Pipe {

	CharSequenceLexer lexer;
	int numberFeatures = -1;
	private static Logger logger = MalletLogger.getLogger(Csv2Array.class.getName());

	public Csv2Array () {
		this.lexer = new CharSequenceLexer ("([^,]+)");
	}

	public Csv2Array (String regex) {
		this.lexer = new CharSequenceLexer (regex);
	}

	public Csv2Array (CharSequenceLexer l) {
		this.lexer = l;
	}

	/** Convert the data in an <CODE>Instance</CODE> from a CharSequence
	 * of comma-separated-values to an array, where each index is the
	 * feature name.
	 */
	public Instance pipe(  Instance carrier ) {
		
		CharSequence c = (CharSequence)carrier.getData();
		int nf = countNumberFeatures (c);
		if (numberFeatures == -1) // first instance seen
			numberFeatures = nf;
		else if (numberFeatures != nf)
			throw new IllegalArgumentException ("Instances must have same-length feature vectors. length_i: " + numberFeatures + " length_j: " + nf);
		double[] feats = new double[numberFeatures];
		lexer.setCharSequence (c);
		int i=0;
		while (lexer.hasNext()) 
			feats[i++] = Double.parseDouble ((String)lexer.next());
		carrier.setData (feats);
		return carrier;
		
	}
				 
	private int countNumberFeatures (CharSequence c) {
		String s = c.toString();
		int ret = 0;
		int pos = 0;
		while ((pos = s.indexOf (",", pos) + 1) != 0)
			ret++;
		return ret+1;
	}
}
