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
import cc.mallet.util.MalletLogger;

/** Converts a Java array of numerical types to a FeatureVector, where the
    Alphabet is the data array index wrapped in an Integer object.

    @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
 */
public class Array2FeatureVector extends Pipe {

	private static Logger logger = MalletLogger.getLogger(Array2FeatureVector.class.getName());

	public Array2FeatureVector(int capacity) {

		this.dataAlphabet = new Alphabet(capacity);

	}

	public Array2FeatureVector() {
		this(1000);
	}

	/** Construct a pipe based on the dimensions of the data and target. */
	public Array2FeatureVector( Alphabet dataAlphabet, Alphabet targetAlphabet ) {

		this.dataAlphabet = dataAlphabet;
		this.targetAlphabet = targetAlphabet;

	}

	/** Convert the data in an <CODE>Instance</CODE> from an array to a 
	<CODE>FeatureVector</CODE> leaving other fields unchanged.

	<CODE>Instance.getData()</CODE> must return a numeric array, and it is
	 cast to <CODE>double[]</CODE>

	@throws IllegalStateException If <CODE>Instance.getTarget()</CODE> is
	not a Labeling
	 */
	public Instance pipe(  Instance carrier )
	throws IllegalStateException
	{

		int dataLength = Array.getLength( carrier.getData() );

		if ( dataLength > dataAlphabet.size() )
			for (int k=dataAlphabet.size() ; k<dataLength ; k++ )
				dataAlphabet.lookupIndex( new Integer(k) , true ); // 'add'

		FeatureVector fv = new FeatureVector( dataAlphabet, 
				(double[])carrier.getData() );


		// Check if we've set the target alphabet member
		if (targetAlphabet == null) {
			if (carrier.getTarget() instanceof Labeling)
				targetAlphabet = ((Labeling)carrier.getTarget()).getLabelAlphabet();
			else
				throw new IllegalStateException ("Instance target is not a " +
						"Labeling; it is a " + 
						carrier.getTarget().getClass().getName());


		}

		carrier.setData( fv );

		return carrier;

		/*return new Instance( fv, carrier.getTarget(), carrier.getName(), 
	  carrier.getSource(), this );*/
	}


	/** Current size of the Vocabulary */
	public int size() 
	{
		return dataAlphabet.size();
	}
}
