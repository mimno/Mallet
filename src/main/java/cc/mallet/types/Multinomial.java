/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.types;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.google.errorprone.annotations.Var;

import cc.mallet.util.Randoms;

/**
 * A probability distribution over a set of features represented as a {@link cc.mallet.types.FeatureVector}.
 * The values associated with each element in the Multinomial/FeaturVector are probabilities
 * and should sum to 1.
 * Features are indexed using feature indices - the index into the underlying Alphabet -
 * rather than using locations the way FeatureVectors do.
 * <p>
 * {@link cc.mallet.types.Multinomial.Estimator} provides a subhierachy
 * of ways to generate an estimate of the probability distribution from counts associated
 * with the features.
 *
 * @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class Multinomial extends FeatureVector
{
	//	protected Multinomial () { }

	// "size" is the number of entries in "probabilities" that have valid values in them;
	// note that the dictionary (and thus the resulting multinomial) may be bigger than size
	// if the dictionary is shared with multiple estimators, and the dictionary grew
	// due to another estimator.
	private static double[] getValues (double[] probabilities, Alphabet dictionary,
																		 int size, boolean copy, boolean checkSum)
	{
		double[] values;
		assert (dictionary == null || dictionary.size() >= size);
		// No, not necessarily true; see comment above.
		//assert (dictionary == null || dictionary.size() == size);
		//assert (probabilities.length == size);
		// xxx Consider always copying, so that we are assured that we
		// always have a real probability distribution.
		if (copy) {
			values = new double[dictionary==null ? size : dictionary.size()];
			System.arraycopy (probabilities, 0, values, 0, size);
		} else {
			assert (dictionary == null || dictionary.size() == probabilities.length);
			values = probabilities;
		}
		if (checkSum) {
			// Check that we have a true probability distribution
			@Var
			double sum = 0;
			for (int i = 0; i < values.length; i++)
				sum += values[i];
			if (Math.abs (sum - 1.0) > 0.9999){
				throw new IllegalArgumentException ("Probabilities sum to "	+ sum + ", not to one.");
			}
		}
		return values;
	}

	protected Multinomial (double[] probabilities, Alphabet dictionary,
												 int size, boolean copy, boolean checkSum)
	{
		super (dictionary, getValues(probabilities, dictionary, size, copy, checkSum));
	}

	public Multinomial (double[] probabilities, Alphabet dictionary)
	{
		this (probabilities, dictionary, dictionary.size(), true, true);
	}

	public Multinomial (double[] probabilities, int size)
	{
		this (probabilities, null, size, true, true);
	}

	public Multinomial (double[] probabilities)
	{
		this (probabilities, null, probabilities.length, true, true);
	}


	public int size ()
	{
		return values.length;
	}

	public double probability (int featureIndex)
	{
		return values[featureIndex];
	}

	public double probability (Object key)
	{
		if (dictionary == null)
			throw new IllegalStateException ("This Multinomial has no dictionary.");
		return probability (dictionary.lookupIndex (key));
	}

	public double logProbability (int featureIndex)
	{
		return Math.log(values[featureIndex]);
	}

	public double logProbability (Object key)
	{
		if (dictionary == null)
			throw new IllegalStateException ("This Multinomial has no dictionary.");
		return logProbability (dictionary.lookupIndex (key));
	}

	public Alphabet getAlphabet ()
	{
		return dictionary;
	}

	public void addProbabilitiesTo (double[] vector)
	{
		for (int i = 0; i < values.length; i++)
			vector[i] += values[i];
	}


	public int randomIndex (Randoms r)
	{
		double f = r.nextUniform();
		@Var
		double sum = 0;
		@Var
		int i;
		for (i = 0; i < values.length; i++) {
			sum += values[i];
			//System.out.print (" sum="+sum);
			if (sum >= f)
				break;
		}
		//if (sum < f) throw new IllegalStateException
		//System.out.println ("i = "+i+", f = "+f+", sum = "+sum);
		assert (sum >= f);
		return i;
	}

	public Object randomObject (Randoms r)
	{
		if (dictionary == null)
			throw new IllegalStateException ("This Multinomial has no dictionary.");
		return dictionary.lookupObject (randomIndex (r));
	}

	public FeatureSequence randomFeatureSequence (Randoms r, @Var int length)
	{
		if (! (dictionary instanceof Alphabet))
			throw new UnsupportedOperationException
				("Multinomial's dictionary must be a Alphabet");
		FeatureSequence fs = new FeatureSequence ((Alphabet)dictionary, length);
		while (length-- > 0)
			fs.add (randomIndex (r));
		return fs;
	}

	// "size" is the number of 1.0-weight features in the feature vector
	public FeatureVector randomFeatureVector (Randoms r, int size)
	{
		return new FeatureVector (randomFeatureSequence (r, size));
	}

	
	/** A Multinomial in which the values associated with each feature index fi is
     * Math.log(probability[fi]) instead of probability[fi].
     * Logs are used for numerical stability.
     */
	public static class Logged extends Multinomial
	{
		private static final long serialVersionUID = 1L;

		public Logged (double[] probabilities, Alphabet dictionary,
									 int size, boolean areLoggedAlready)
		{
			super (probabilities, dictionary, size, true, !areLoggedAlready);
			assert (dictionary == null || dictionary.size() == size);
			if (!areLoggedAlready)
				for (int i = 0; i < size; i++)
					values[i] = Math.log (values[i]);
		}

		public Logged (double[] probabilities, Alphabet dictionary,
									 boolean areLoggedAlready)
		{
			this (probabilities, dictionary,
						(dictionary == null ? probabilities.length : dictionary.size()),
						areLoggedAlready);
		}

		public Logged (double[] probabilities, Alphabet dictionary, int size)
		{
			this (probabilities, dictionary, size, false);
		}

		public Logged (double[] probabilities, Alphabet dictionary)
		{
			this (probabilities, dictionary, dictionary.size(), false);
		}
		
		public Logged (Multinomial m)
		{
			this (m.values, m.dictionary, false);
		}

		public Logged (double[] probabilities)
		{
			this (probabilities, null, false);
		}

		public double probability (int featureIndex)
		{
			return Math.exp (values[featureIndex]);
		}

		public double logProbability (int featureIndex)
		{
			return values[featureIndex];
		}

		public void addProbabilities (double[] vector)
		{
      assert (vector.length == values.length);
      for (int fi = 0; fi < vector.length; fi++)
        vector[fi] += Math.exp(values[fi]);
    }

		public void addLogProbabilities (double[] vector)
		{
			for (int i = 0; i < values.length; i++)
				vector[i] += values[i];

			// if vector is longer than values, act as if values
			// were extended with values of minus infinity.
			for (int i=values.length; i<vector.length; i++){
				vector[i] = Double.NEGATIVE_INFINITY;
			}
		}
	}

	// Serialization
	private static final long serialVersionUID = 1L;
	
	
	// xxx Make this inherit from something like AugmentableDenseFeatureVector
    /**
     *  A hierarchy of classes used to produce estimates of probabilities, in
     *  the form of a Multinomial, from counts associated with the elements
     *  of an Alphabet.
     *
     *  Estimator itself contains the machinery for associating and manipulating
     *  counts with elements of an Alphabet, including behaving sanely if the
     *  Alphabet changes size between calls.  It does not contain any means
     *  of generating probability estimates; various means of estimating are
     *  provided by subclasses.
     */
	public static abstract class Estimator implements Cloneable, Serializable
	{
		Alphabet dictionary;
		double counts[];
		int size;														// The number of valid entries in counts[]
		static final int minCapacity = 16;

		protected Estimator (double counts[], int size, Alphabet dictionary)
		{
			this.counts = counts;
			this.size = size;
			this.dictionary = dictionary;
		}

		public Estimator (double counts[], Alphabet dictionary)
		{
			this (counts, dictionary.size(), dictionary);
		}

		public Estimator ()
		{
			this (new double[minCapacity], 0, null);
		}

		public Estimator (int size)
		{
			this (new double[size > minCapacity ? size : minCapacity], size, null);
		}

		public Estimator (Alphabet dictionary)
		{
			this(new double[dictionary.size()], dictionary.size(), dictionary);
		}

		public void setAlphabet (Alphabet d)
		{
			this.size = d.size();
			this.counts = new double[size];
			this.dictionary = d;
		}

		public int size ()
		{
			return (dictionary == null ? size : dictionary.size());
		}

		protected void ensureCapacity (int index)
		{
			//assert (dictionary == null);	// Size is fixed if dictionary present?
			if (index > size)
				size = index;
			if (counts.length <= index) {
				@Var
				int newLength = ((counts.length < minCapacity)
												 ? minCapacity
												 : counts.length);
				while (newLength <= index)
					newLength *= 2;
				double[] newCounts = new double[newLength];
				System.arraycopy (counts, 0, newCounts, 0, counts.length);
				this.counts = newCounts;
			}
		}

		// xxx Note that this does not reset the "size"!
		public void reset ()
		{
			for (int i = 0; i < counts.length; i++)
				counts[i] = 0;
		}

		// xxx Remove this method?
		private void setCounts (double counts[])
		{
			assert (dictionary == null || counts.length <= size());
			// xxx Copy instead?
			// xxx Set size() to match counts.length?
			this.counts = counts;
		}

		public void increment (int index, double count)
		{
			ensureCapacity (index);
			counts[index] += count;
			if (size < index + 1)
				size = index + 1;
		}

		public void increment (String key, double count)
		{
			increment (dictionary.lookupIndex (key), count);
		}

		// xxx Add "public void increment (Object key, double count)", or is it too dangerous?

		public void increment (FeatureSequence fs, double scale)
		{
			if (fs.getAlphabet() != dictionary)
				throw new IllegalArgumentException ("Vocabularies don't match.");
			for (int fsi = 0; fsi < fs.size(); fsi++)
				increment (fs.getIndexAtPosition(fsi), scale);
		}

		public void increment (FeatureSequence fs)
		{
			increment (fs, 1.0);
		}

		public void increment (FeatureVector fv, double scale)
		{
			if (fv.getAlphabet() != dictionary)
				throw new IllegalArgumentException ("Vocabularies don't match.");
			for (int fvi = 0; fvi < fv.numLocations(); fvi++)
				// Originally, the value of the feature was not being taken into account here,
				// so words were only counted once per document! - gdruck 
				// increment (fv.indexAtLocation(fvi), scale);
				increment(fv.indexAtLocation(fvi), scale * fv.valueAtLocation(fvi));
		}

		public void increment (FeatureVector fv)
		{
			increment (fv, 1.0);
		}

		public double getCount (int index)
		{
			return counts[index];
		}

		public Object clone ()
		{
			try {
				return super.clone ();
			} catch (CloneNotSupportedException e) {
				return null;
			}
		}

		public void print () {
			//if (counts != null) throw new IllegalStateException ("Foo");
			System.out.println ("Multinomial.Estimator");
			for (int i = 0; i < size; i++)
				System.out.println ("counts["+i+"] = " + counts[i]);
		}

		public abstract Multinomial estimate ();

       // Serialization
       // serialVersionUID is overriden to prevent innocuous changes in this
       // class from making the serialization mechanism think the external
       // format has changed.

       private static final long serialVersionUID = 1;
       private static final int CURRENT_SERIAL_VERSION = 1;

       private void writeObject(ObjectOutputStream out) throws IOException
       {
           out.writeInt(CURRENT_SERIAL_VERSION);
           out.writeObject(dictionary);
           out.writeObject(counts);
           out.writeInt(size);
       }

       private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
       {
           int version = in.readInt();
           if (version != CURRENT_SERIAL_VERSION)
           throw new ClassNotFoundException("Mismatched Multionmial.Estimator versions: wanted " +
                                       CURRENT_SERIAL_VERSION + ", got " +
                                       version);
           dictionary = (Alphabet) in.readObject();
           counts = (double []) in.readObject();
           size =  in.readInt();
       }
	} // class Estimator

    /**
     * An Estimator in which probability estimates in a Multinomial
     * are generated by adding a constant m (specified at construction time)
     * to each count before dividing by the total of the m-biased counts.
     */

	public static class MEstimator extends Estimator
	{
		double m;

		public MEstimator (Alphabet dictionary, double m)
		{
			super (dictionary);
			this.m = m;
		}


		public MEstimator (int size, double m)
		{
			super(size);
			this.m = m;
		}

		public MEstimator (double m)
		{
			super();
			this.m = m;
		}

		public Multinomial estimate ()
		{
			double[] pr = new double[dictionary==null ? size : dictionary.size()];
            if (dictionary != null){
                ensureCapacity(dictionary.size() -1 );   //side effect: updates size member
            }
			@Var
			double sum = 0;
			for (int i = 0; i < pr.length; i++) {
        //if (dictionary != null) System.out.println (dictionary.lookupObject(i).toString()+' '+counts[i]);
        pr[i] = counts[i] + m;
				sum += pr[i];
			}
			for (int i = 0; i < pr.length; i++)
				pr[i] /= sum;
			return new Multinomial (pr, dictionary, size, false, false);			
		}

       private static final long serialVersionUID = 1;
       private static final int CURRENT_SERIAL_VERSION = 1;

       private void writeObject(ObjectOutputStream out) throws IOException
       {
           out.writeInt(CURRENT_SERIAL_VERSION);
           out.writeDouble(m);
       }

       private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
       {
           int version = in.readInt();
           if (version != CURRENT_SERIAL_VERSION)
           throw new ClassNotFoundException("Mismatched Multinomial.MEstimator versions: wanted " +
                                       CURRENT_SERIAL_VERSION + ", got " +
                                       version);
           m = in.readDouble();
       }
	}  // end MEstimator

    /**
     * An MEstimator with m set to 0. The probability estimates in the Multinomial
     * are generated by dividing each count by the sum of all counts.
     */
	public static class MLEstimator extends MEstimator
	{

		public MLEstimator ()
		{
			super (0);
		}

		public MLEstimator (int size)
		{
			super (size, 0);
		}

		public MLEstimator (Alphabet dictionary)
		{
			super (dictionary, 0);
		}

        private static final long serialVersionUID = 1;
        private static final int CURRENT_SERIAL_VERSION = 1;

        private void writeObject(ObjectOutputStream out) throws IOException
        {
            out.writeInt(CURRENT_SERIAL_VERSION);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
        {
            int version = in.readInt();
            if (version != CURRENT_SERIAL_VERSION)
            throw new ClassNotFoundException("Mismatched Multinomial.MLEstimator versions: wanted " +
                                        CURRENT_SERIAL_VERSION + ", got " +
                                        version);
        }


	}   // class MLEstimator

    /**
     * An MEstimator with m set to 1. The probability estimates in the Multinomial
     * are generated by adding 1 to each count and then dividing each
     * 1-biased count by the sum of all 1-biased counts.
     */
	public static class LaplaceEstimator extends MEstimator
	{

		public LaplaceEstimator ()
		{
			super (1);
		}

		public LaplaceEstimator (int size)
		{
			super (size, 1);
		}

		public LaplaceEstimator (Alphabet dictionary)
		{
			super (dictionary, 1);
		}


        private static final long serialVersionUID = 1;
        private static final int CURRENT_SERIAL_VERSION = 1;

        private void writeObject(ObjectOutputStream out) throws IOException
        {
            out.writeInt(CURRENT_SERIAL_VERSION);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
        {
            int version = in.readInt();
            if (version != CURRENT_SERIAL_VERSION)
            throw new ClassNotFoundException("Mismatched Multinomial.LaplaceEstimator versions: wanted " +
                                        CURRENT_SERIAL_VERSION + ", got " +
                                        version);
        }

	}   // class Multinomial.LaplaceEstimator

    //todo: Lazy, lazy lazy.  Make this serializable, too.
    /**
     * Unimplemented, but the MEstimators are.
     */
	public static class MAPEstimator extends Estimator
	{
		Dirichlet prior;

		public MAPEstimator (Dirichlet d)
		{
			super (d.size());
			prior = d;
		}

		public Multinomial estimate ()
		{
			// xxx unfinished.
			return null;
		}
        private static final long serialVersionUID = 1;
        private static final int CURRENT_SERIAL_VERSION = 1;

        private void writeObject(ObjectOutputStream out) throws IOException
        {
            out.writeInt(CURRENT_SERIAL_VERSION);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
        {
            int version = in.readInt();
            if (version != CURRENT_SERIAL_VERSION)
            throw new ClassNotFoundException("Mismatched Multinomial.MAPEstimator versions: wanted " +
                                        CURRENT_SERIAL_VERSION + ", got " +
                                        version);
        }

	}

}
