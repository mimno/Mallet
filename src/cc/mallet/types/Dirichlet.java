/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.	For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.types;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIterator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import cc.mallet.types.Multinomial;
import cc.mallet.util.Maths;
import cc.mallet.util.Randoms;

/** 
 *	Various useful functions related to Dirichlet distributions.
 *
 *	@author Andrew McCallum and David Mimno
 */

public class Dirichlet {

	Alphabet dict;
	double magnitude = 1;
	double[] partition;

	Randoms random = null;

	/** Actually the negative Euler-Mascheroni constant */
	public static final double EULER_MASCHERONI = -0.5772156649015328606065121;
	public static final double PI_SQUARED_OVER_SIX = Math.PI * Math.PI / 6;
	public static final double HALF_LOG_TWO_PI = Math.log(2 * Math.PI) / 2;

	public static final double DIGAMMA_COEF_1 = 1/12;
	public static final double DIGAMMA_COEF_2 = 1/120;
	public static final double DIGAMMA_COEF_3 = 1/252;
	public static final double DIGAMMA_COEF_4 = 1/240;
	public static final double DIGAMMA_COEF_5 = 1/132;
	public static final double DIGAMMA_COEF_6 = 691/32760;
	public static final double DIGAMMA_COEF_7 = 1/12;
	public static final double DIGAMMA_COEF_8 = 3617/8160;
	public static final double DIGAMMA_COEF_9 = 43867/14364;
	public static final double DIGAMMA_COEF_10 = 174611/6600;

	public static final double DIGAMMA_LARGE = 9.5;
	public static final double DIGAMMA_SMALL = .000001;



	/** A dirichlet parameterized by a distribution and a magnitude
	 * 
	 * @param m The magnitude of the Dirichlet: sum_i alpha_i
	 * @param p A probability distribution: p_i = alpha_i / m
	 */
	public Dirichlet (double m, double[] p) {
		magnitude = m;
		partition = p;
	}

	/** A symmetric dirichlet: E(X_i) = E(X_j) for all i, j 
	 * 
	 * @param m The magnitude of the Dirichlet: sum_i alpha_i
	 * @param n The number of dimensions
	 */
	/*
	public Dirichlet (double m, int n) {
		magnitude = m;
		partition = new double[n];

		partition[0] = 1.0 / n;
		for (int i=1; i<n; i++) {
			partition[i] = partition[0];
		}
	}
	*/

	/** A dirichlet parameterized with a single vector of positive reals */
	public Dirichlet(double[] p) {
		magnitude = 0;
		partition = new double[p.length];

		// Add up the total
		for (int i=0; i<p.length; i++) {
			magnitude += p[i];
		}

		for (int i=0; i<p.length; i++) {
			partition[i] = p[i] / magnitude;
		}
	}
	
	/** Constructor that takes an alphabet representing the
	 *	meaning of each dimension
	 */
	public Dirichlet (double[] alphas, Alphabet dict)
	{
		this(alphas);
		if (dict != null && alphas.length != dict.size())
			throw new IllegalArgumentException ("alphas and dict sizes do not match.");
		this.dict = dict;
		if (dict != null)
			dict.stopGrowth();
	}

	/**
	 *	A symmetric Dirichlet with alpha_i = 1.0 and the 
	 *	number of dimensions of the given alphabet.
	 */
	public Dirichlet (Alphabet dict)
	{
		this (dict, 1.0);
	}

	/**
	 *	A symmetric Dirichlet with alpha_i = <code>alpha</code> and the 
	 *	number of dimensions of the given alphabet.
	 */
	public Dirichlet (Alphabet dict, double alpha)
	{
		this(dict.size(), alpha);
		this.dict = dict;
		dict.stopGrowth();
	}

	/** A symmetric Dirichlet with alpha_i = 1.0 and <code>size</code> 
	dimensions */
	public Dirichlet (int size)
	{
		this (size, 1.0);
	}

	/** A symmetric dirichlet: E(X_i) = E(X_j) for all i, j 
	 * 
	 * @param n The number of dimensions
	 * @param alpha The parameter for each dimension
	 */
	public Dirichlet (int size, double alpha)
	{
		magnitude = size * alpha;

		partition = new double[size];

		partition[0] = 1.0 / size;
		for (int i=1; i<size; i++) {
		partition[i] = partition[0];
		}
	}
	
	
	

	private void initRandom() {
		if (random == null) {
			random = new Randoms();
		}
	}

	public double[] nextDistribution() {
		double distribution[] = new double[partition.length];
		initRandom();

//		For each dimension, draw a sample from Gamma(mp_i, 1)
		double sum = 0;
		for (int i=0; i<distribution.length; i++) {
			distribution[i] = random.nextGamma(partition[i] * magnitude, 1);
			if (distribution[i] <= 0) {
				distribution[i] = 0.0001;
			}
			sum += distribution[i];
		}

//		Normalize
		for (int i=0; i<distribution.length; i++) {
			distribution[i] /= sum;
		}

		return distribution;
	}

	/** 
	 *	Create a printable list of alpha_i parameters
	 */

	public static String distributionToString(double magnitude, double[] distribution) {
		StringBuffer output = new StringBuffer();
		NumberFormat formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(5);

		output.append(formatter.format(magnitude) + ":\t");

		for (int i=0; i<distribution.length; i++) {
			output.append(formatter.format(distribution[i]) + "\t");
		}

		return output.toString();
	}

	/** Write the parameters alpha_i to the specified file, one
	 *	per line
	 */
	public void toFile(String filename) throws IOException {
		PrintWriter out = 
			new PrintWriter(new BufferedWriter(new FileWriter(filename)));
		for (int i=0; i<partition.length; i++) {
			out.println(magnitude * partition[i]);
		}
		out.flush();
		out.close();
	}

	/** Dirichlet-multinomial: draw a distribution from the 
dirichlet, then draw n samples from that multinomial. */
	public int[] drawObservation(int n) {
		initRandom();

		double[] distribution = nextDistribution();

		return drawObservation(n, distribution);
	}

	/** 
	 *	 Draw a count vector from the probability distribution provided.
	 * 
	 *	@param n The <i>expected</i> total number of counts in the returned vector. The actual number is ~ Poisson(<code>n</code>)
	 */
	public int[] drawObservation(int n, double[] distribution) {
		initRandom();

		int[] histogram = new int[partition.length];

		Arrays.fill(histogram, 0);

		int count;

//		I was using a poisson, but the poisson variate generator
//		goes berzerk for lambda above ~500.
		if (n < 100) {
			count = random.nextPoisson();
		}
		else {
			// p(N(100, 10) <= 0) = 7.619853e-24

			count = (int) Math.round(random.nextGaussian(n, n));
		}

		for (int i=0; i<count; i++) {
			histogram[random.nextDiscrete(distribution)]++;
		}

		return histogram;
	}

	/** Create a set of d draws from a dirichlet-multinomial, each
	 *  with an average of n observations. */
	public Object[] drawObservations(int d, int n) {
		Object[] observations = new Object[d];
		for (int i=0; i<d; i++) {
			observations[i] = drawObservation(n);
		}
		return observations;
	}

	/** This calculates a log gamma function exactly. 
	 *	It's extremely inefficient -- use this for comparison only.
	 */
	public static double logGammaDefinition(double z) {
		double result = EULER_MASCHERONI * z - Math.log(z);
		for (int k=1; k < 10000000; k++) {
			result += (z/k) - Math.log(1 + (z/k));
		}
		return result;
	}

	/**	 This directly calculates the difference between two 
	 *	 log gamma functions using a recursive formula.
	 *	 The break-even with the Stirling approximation is about 
	 *	 n=2, so it's not necessarily worth using this.
	 */
	public static double logGammaDifference(double z, int n) {
		double result = 0.0;
		for (int i=0; i < n; i++) {
			result += Math.log(z + i);
		}
		return result;
	}

	/** Currently aliased to <code>logGammaStirling</code> */
	public static double logGamma(double z) {
		return logGammaStirling(z);
	}

	/** Use a fifth order Stirling's approximation.
	 * 
	 *	@param z Note that Stirling's approximation is increasingly unstable as <code>z</code> approaches 0. If <code>z</code> is less than 2, we shift it up, calculate the approximation, and then shift the answer back down.
	 */
	public static double logGammaStirling(double z) {
		int shift = 0;
		while (z < 2) {
			z++;
			shift++;
		}

		double result = HALF_LOG_TWO_PI + (z - 0.5) * Math.log(z) - z +
		1/(12 * z) - 1 / (360 * z * z * z) + 1 / (1260 * z * z * z * z * z);

		while (shift > 0) {
			shift--;
			z--;
			result -= Math.log(z);
		}

		return result;
	}

	/** Gergo Nemes' approximation */
	
	public static double logGammaNemes(double z) {
		double result = HALF_LOG_TWO_PI - (Math.log(z) / 2) +
		z * (Math.log(z + (1/(12 * z - (1/(10*z))))) - 1);
		return result;
	}

	/** Calculate digamma using an asymptotic expansion involving
Bernoulli numbers. */
	public static double digamma(double z) {
//		This is based on matlab code by Tom Minka

//		if (z < 0) { System.out.println(" less than zero"); }

		double psi = 0;

		if (z < DIGAMMA_SMALL) {
			psi = EULER_MASCHERONI - (1 / z); // + (PI_SQUARED_OVER_SIX * z);
			/*for (int n=1; n<100000; n++) {
	psi += z / (n * (n + z));
	}*/
			return psi;
		}

		while (z < DIGAMMA_LARGE) {
			psi -= 1 / z;
			z++;
		}

		double invZ = 1/z;
		double invZSquared = invZ * invZ;

		psi += Math.log(z) - .5 * invZ
		- invZSquared * (DIGAMMA_COEF_1 - invZSquared * 
				(DIGAMMA_COEF_2 - invZSquared * 
						(DIGAMMA_COEF_3 - invZSquared * 
								(DIGAMMA_COEF_4 - invZSquared * 
										(DIGAMMA_COEF_5 - invZSquared * 
												(DIGAMMA_COEF_6 - invZSquared *
														DIGAMMA_COEF_7))))));

		return psi;
	}

	public static double digammaDifference(double x, int n) {
		double sum = 0;
		for (int i=0; i<n; i++) {
			sum += 1 / (x + i);
		}
		return sum;
	}

	public static double trigamma(double z) {
		int shift = 0;
        while (z < 2) {
            z++;
            shift++;
        }
		
		double oneOverZ = 1.0 / z;
		double oneOverZSquared = oneOverZ * oneOverZ;

		double result = 
			oneOverZ +
			0.5 * oneOverZSquared +
			0.1666667 * oneOverZSquared * oneOverZ -
			0.03333333 * oneOverZSquared * oneOverZSquared * oneOverZ +
			0.02380952 * oneOverZSquared * oneOverZSquared * oneOverZSquared * oneOverZ -
			0.03333333 * oneOverZSquared * oneOverZSquared * oneOverZSquared * oneOverZSquared * oneOverZ;
			
		System.out.println(z + " -> " + result);
		
		while (shift > 0) {
			shift--;
			z--;
			result += 1.0 / (z * z);
			System.out.println(z + " -> " + result);
		}

		return result;
	}

	/**
	 * Learn the concentration parameter of a symmetric Dirichlet using frequency histograms.
	 *  Since all parameters are the same, we only need to keep track of 
	 *  the number of observation/dimension pairs with count N
	 *
	 * @param countHistogram An array of frequencies. If the matrix X represents observations such that x<sub>dt</sub> is how many times word t occurs in document d, <code>countHistogram[3]</code> is the total number of cells <i>in any column</i> that equal 3.
	 * @param observationLengths A histogram of sample lengths, for example <code>observationLengths[20]</code> could be the number of documents that are exactly 20 tokens long.	 
	 * @param numDimensions The total number of dimensions.
	 * @param currentValue An initial starting value.
	 */

	public static double learnSymmetricConcentration(int[] countHistogram,
													 int[] observationLengths,
													 int numDimensions, 
													 double currentValue) {
		double currentDigamma;

		// The histogram arrays are presumably allocated before
		//  we knew what went in them. It is therefore likely that
		//  the largest non-zero value may be much closer to the 
		//  beginning than the end. We don't want to iterate over
		//  a whole bunch of zeros, so keep track of the last value.
		int largestNonZeroCount = 0;
		int[] nonZeroLengthIndex = new int[ observationLengths.length ];
		
		for (int index = 0; index < countHistogram.length; index++) {
			if (countHistogram[index] > 0) { largestNonZeroCount = index; }
		}

		int denseIndex = 0;
		for (int index = 0; index < observationLengths.length; index++) {
			if (observationLengths[index] > 0) {
				nonZeroLengthIndex[denseIndex] = index;
				denseIndex++;
			}
		}

		int denseIndexSize = denseIndex;

		for (int iteration = 1; iteration <= 200; iteration++) {
			
			double currentParameter = currentValue / numDimensions;

			// Calculate the numerator
			
			currentDigamma = 0;
			double numerator = 0;
		
			// Counts of 0 don't matter, so start with 1
			for (int index = 1; index <= largestNonZeroCount; index++) {
				currentDigamma += 1.0 / (currentParameter + index - 1);
				numerator += countHistogram[index] * currentDigamma;
			}
			
			// Now calculate the denominator, a sum over all observation lengths
			
			currentDigamma = 0;
			double denominator = 0;
			int previousLength = 0;
			
			double cachedDigamma = digamma(currentValue);
			
			for (denseIndex = 0; denseIndex < denseIndexSize; denseIndex++) {
				int length = nonZeroLengthIndex[denseIndex];
				
				if (length - previousLength > 20) {
					// If the next length is sufficiently far from the previous,
					//  it's faster to recalculate from scratch.
					currentDigamma = digamma(currentValue + length) - cachedDigamma;
				}
				else {
					// Otherwise iterate up. This looks slightly different
					//  from the previous version (no -1) because we're indexing differently.
					for (int index = previousLength; index < length; index++) {
						currentDigamma += 1.0 / (currentValue + index);
					}
				}
				
				denominator += currentDigamma * observationLengths[length];
			}
			
			currentValue = currentParameter * numerator / denominator;


			///System.out.println(currentValue + " = " + currentParameter + " * " + numerator + " / " + denominator);
		}

		return currentValue;
	}

	public static void testSymmetricConcentration(int numDimensions, int numObservations,
												  int observationMeanLength) {

		double logD = Math.log(numDimensions);

		for (int exponent = -5; exponent < 4; exponent++) {
			double alpha = numDimensions * 1.0;

			Dirichlet prior = new Dirichlet(numDimensions, alpha / numDimensions);

			int[] countHistogram = new int[ 1000000 ];
			int[] observationLengths = new int[ 1000000 ];
			
			Object[] observations = prior.drawObservations(numObservations, observationMeanLength);

			Dirichlet optimizedDirichlet = new Dirichlet(numDimensions, 1.0);
			optimizedDirichlet.learnParametersWithHistogram(observations);

			System.out.println(optimizedDirichlet.magnitude);

			for (int i=0; i < numObservations; i++) {
				int[] observation = (int[]) observations[i];
				
				int total = 0;
				for (int k=0; k < numDimensions; k++) {
					if (observation[k] > 0) {
						total += observation[k];
						countHistogram[ observation[k] ]++;
					}
				}
				
				observationLengths[ total ]++;
			}
			
			double estimatedAlpha = learnSymmetricConcentration(countHistogram, observationLengths,
																numDimensions, 1.0);
			
			System.out.println(alpha + "\t" + estimatedAlpha + "\t" +
							   Math.abs(alpha - estimatedAlpha));
		}
		

	}


	/** 
	 * Learn Dirichlet parameters using frequency histograms
	 * 
	 * @param parameters A reference to the current values of the parameters, which will be updated in place
	 * @param observations An array of count histograms. <code>observations[10][3]</code> could be the number of documents that contain exactly 3 tokens of word type 10.
	 * @param observationLengths A histogram of sample lengths, for example <code>observationLengths[20]</code> could be the number of documents that are exactly 20 tokens long.
	 * @returns The sum of the learned parameters.
	 */ 
	public static double learnParameters(double[] parameters,
										 int[][] observations,
										 int[] observationLengths) {

		return learnParameters(parameters, observations, observationLengths,
							   1.00001, 1.0, 200);
	}

	/** 
	 * Learn Dirichlet parameters using frequency histograms
	 * 
	 * @param parameters A reference to the current values of the parameters, which will be updated in place
	 * @param observations An array of count histograms. <code>observations[10][3]</code> could be the number of documents that contain exactly 3 tokens of word type 10.
	 * @param observationLengths A histogram of sample lengths, for example <code>observationLengths[20]</code> could be the number of documents that are exactly 20 tokens long.
	 * @param shape Gamma prior E(X) = shape * scale, var(X) = shape * scale<sup>2</sup>
	 * @param scale 
	 * @param numIterations 200 to 1000 generally insures convergence, but 1-5 is often enough to step in the right direction
	 * @returns The sum of the learned parameters.
	 */ 
	public static double learnParameters(double[] parameters,
										 int[][] observations,
										 int[] observationLengths, 
										 double shape, double scale,
										 int numIterations) {
		int i, k;

		double parametersSum = 0;

		//	Initialize the parameter sum

		for (k=0; k < parameters.length; k++) {
			parametersSum += parameters[k];
		}

		double oldParametersK;
		double currentDigamma;
		double denominator;

		int nonZeroLimit;
		int[] nonZeroLimits = new int[observations.length];
		Arrays.fill(nonZeroLimits, -1);

		// The histogram arrays go up to the size of the largest document,
		//	but the non-zero values will almost always cluster in the low end.
		//	We avoid looping over empty arrays by saving the index of the largest
		//	non-zero value.

		int[] histogram;

		for (i=0; i<observations.length; i++) {
			histogram = observations[i];

			//StringBuffer out = new StringBuffer();
			for (k = 0; k < histogram.length; k++) {
				if (histogram[k] > 0) {
					nonZeroLimits[i] = k;
					//out.append(k + ":" + histogram[k] + " ");
				}
			}
			//System.out.println(out);
		}

		for (int iteration=0; iteration<numIterations; iteration++) {

			// Calculate the denominator
			denominator = 0;
			currentDigamma = 0;

			// Iterate over the histogram:
			for (i=1; i<observationLengths.length; i++) {
				currentDigamma += 1 / (parametersSum + i - 1);
				denominator += observationLengths[i] * currentDigamma;
			}

			// Bayesian estimation Part I
			denominator -= 1/scale;

			// Calculate the individual parameters

			parametersSum = 0;
			
			for (k=0; k<parameters.length; k++) {

				// What's the largest non-zero element in the histogram?
				nonZeroLimit = nonZeroLimits[k];

				oldParametersK = parameters[k];
				parameters[k] = 0;
				currentDigamma = 0;

				histogram = observations[k];

				for (i=1; i <= nonZeroLimit; i++) {
					currentDigamma += 1 / (oldParametersK + i - 1);
					parameters[k] += histogram[i] * currentDigamma;
				}

				// Bayesian estimation part II
				parameters[k] = (oldParametersK * parameters[k] + shape) / denominator;

				parametersSum += parameters[k];
			}
		}

		return parametersSum;
	}



	/** Use the fixed point iteration described by Tom Minka. */
	public long learnParametersWithHistogram(Object[] observations) {

		int maxLength = 0;
		int[] maxBinCounts = new int[partition.length];
		Arrays.fill(maxBinCounts, 0);

		for (int i=0; i < observations.length; i++) {

			int length = 0;

			int[] observation = (int[]) observations[i];

			for (int bin=0; bin < observation.length; bin++) {
				if (observation[bin] > maxBinCounts[bin]) {
					maxBinCounts[bin] = observation[bin];
				}
				length += observation[bin];
			}

			if (length > maxLength) {
				maxLength = length;
			}
		}

//		Arrays start at zero, so I'm sacrificing one int for greater clarity
//		later on...
		int[][] binCountHistograms = new int[partition.length][];
		for (int bin=0; bin < partition.length; bin++) {
			binCountHistograms[bin] = new int[ maxBinCounts[bin] + 1 ];
			Arrays.fill(binCountHistograms[bin], 0);
		}

//		System.out.println("got mem: " + (System.currentTimeMillis() - start));

		int[] lengthHistogram = new int[maxLength + 1];
		Arrays.fill(lengthHistogram, 0);
//		System.out.println("got lengths: " + (System.currentTimeMillis() - start));

		for (int i=0; i < observations.length; i++) {
			int length = 0;
			int[] observation = (int[]) observations[i];
			for (int bin=0; bin < observation.length; bin++) {
				binCountHistograms[bin][ observation[bin] ]++;
				length += observation[bin];
			}
			lengthHistogram[length]++;
		}

		return learnParametersWithHistogram(binCountHistograms, lengthHistogram);
	}

	public long learnParametersWithHistogram(int[][] binCountHistograms, int[] lengthHistogram) {

		long start = System.currentTimeMillis();

		double[] newParameters = new double[partition.length];

		double alphaK;
		double currentDigamma;
		double denominator;
		double parametersSum = 0.0;

		int i, k;

		for (k = 0; k < partition.length; k++) {
			newParameters[k] = magnitude * partition[k];
			parametersSum += newParameters[k];
		}

		for (int iteration=0; iteration<1000; iteration++) {


			// Calculate the denominator
			denominator = 0;
			currentDigamma = 0;

			for (i=1; i < lengthHistogram.length; i++) {
				currentDigamma += 1 / (parametersSum + i - 1);
				denominator += lengthHistogram[i] * currentDigamma;
			}

			assert(denominator > 0.0);
			assert(! Double.isNaN(denominator));

			parametersSum = 0.0;

			// Calculate the individual parameters

			for (k=0; k<partition.length; k++) {

				alphaK = newParameters[k];
				newParameters[k] = 0.0;
				currentDigamma = 0;

				int[] histogram = binCountHistograms[k];
				if (histogram.length <= 1) {  // Since histogram[0] is for 0...
					newParameters[k] = 0.000001;
				}
				else {
					for (i=1; i<histogram.length; i++) {
						currentDigamma += 1 / (alphaK + i - 1);
						newParameters[k] += histogram[i] * currentDigamma;
					}
				}

				if (! (newParameters[k] > 0.0)) {
					System.out.println("length of empty array: " + (new int[0]).length);

					for (i=0; i<histogram.length; i++) {
						System.out.print(histogram[i] + " ");
					}
					System.out.println();
				}

				assert(newParameters[k] > 0.0);
				assert(! Double.isNaN(newParameters[k]));

				newParameters[k] *= alphaK / denominator;

				parametersSum += newParameters[k];
			}

			/*
	try {
	if (iteration % 25 == 0) {
		//System.out.println(distributionToString(parametersSum, newParameters));
		//toFile("../newsgroups/direct/iteration" + iteration);
		//System.out.println(iteration + ": " + (System.currentTimeMillis() - start));
	}
	} catch (Exception e) {
	System.out.println(e);
	}
			 */
		}

		for (k = 0; k < partition.length; k++) {
			partition[k] = newParameters[k] / parametersSum;
			magnitude = parametersSum;
		}	

//		System.out.println(distributionToString(magnitude, partition));
		return System.currentTimeMillis() - start;
	}

	/** Use the fixed point iteration described by Tom Minka. */
	public long learnParametersWithDigamma(Object[] observations) {

		int[][] binCounts = new int[partition.length][observations.length];
//		System.out.println("got mem: " + (System.currentTimeMillis() - start));

		int[] observationLengths = new int[observations.length];
//		System.out.println("got lengths: " + (System.currentTimeMillis() - start));

		for (int i=0; i < observations.length; i++) {
			int[] observation = (int[]) observations[i];
			for (int bin=0; bin < partition.length; bin++) {
				binCounts[bin][i] = observation[bin];
				observationLengths[i] += observation[bin];
			}
		}
//		System.out.println("init: " + (System.currentTimeMillis() - start));

		return learnParametersWithDigamma(binCounts, observationLengths);
	}

	public long learnParametersWithDigamma(int[][] binCounts,
			int[] observationLengths) {

		long start = System.currentTimeMillis();

		double[] newParameters = new double[partition.length];

		double alphaK;
		double denominator;

		double newMagnitude;

		int i, k;

		for (int iteration=0; iteration<1000; iteration++) {
			newMagnitude = 0;

			// Calculate the denominator
			denominator = 0;

			for (i=0; i<observationLengths.length; i++) {
				denominator += digamma(magnitude + observationLengths[i]);
			}
			denominator -= observationLengths.length * digamma(magnitude);

			// Calculate the individual parameters

			for (k=0; k<partition.length; k++) {
				newParameters[k] = 0;

				int[] counts = binCounts[k];

				alphaK = magnitude * partition[k];

				double digammaAlphaK = digamma(alphaK);
				for (i=0; i<counts.length; i++) {
					if (counts[i] == 0) {
						newParameters[k] += digammaAlphaK;
					}
					else {
						newParameters[k] += digamma(alphaK + counts[i]);
					}
				}
				newParameters[k] -= counts.length * digammaAlphaK;

				if (newParameters[k] <= 0) {
					newParameters[k] = 0.000001;
				}
				else {
					newParameters[k] *= alphaK / denominator;
				}			

				if (newParameters[k] <= 0) {
					System.out.println(newParameters[k] + "\t" + alphaK + "\t" + denominator);
				}

				assert(newParameters[k] > 0);
				assert(! Double.isNaN(newParameters[k]));

				newMagnitude += newParameters[k];

				// System.out.println("finished dimension " + k);
			}

			magnitude = newMagnitude;
			for (k=0; k<partition.length; k++) {
				partition[k] = newParameters[k] / magnitude;
				/*
	if (k < 20) {
		System.out.println(partition[k]+" = "+newParameters[k]+" / "+magnitude);
	}
				 */
			}		

			/*
	try {
	if (iteration % 25 == 0) {
		toFile("../newsgroups/digamma/iteration" + iteration);
		//System.out.println(iteration + ": " + (System.currentTimeMillis() - start));
	}
	} catch (Exception e) {
	System.out.println(e);
	}
			 */
		}
//		System.out.println(distributionToString(magnitude, partition));

		return System.currentTimeMillis() - start;
	}

	/** Estimate a dirichlet with the moment matching method
	 *	 described by Ronning.
	 */
	public long learnParametersWithMoments(Object[] observations) {
		long start = System.currentTimeMillis();

		int i, bin;

		int[] observationLengths = new int[observations.length];
		double[] variances = new double[partition.length];

		Arrays.fill(partition, 0.0);
		Arrays.fill(observationLengths, 0);
		Arrays.fill(variances, 0.0);

//		Find E[p_k]'s

		for (i=0; i < observations.length; i++) {
			int[] observation = (int[]) observations[i];

			// Find the sum of counts in each bin
			for (bin=0; bin < partition.length; bin++) {
				observationLengths[i] += observation[bin];
			}

			for (bin=0; bin < partition.length; bin++) {
				partition[bin] += (double) observation[bin] / observationLengths[i];
			}
		}

		for (bin=0; bin < partition.length; bin++) {
			partition[bin] /= observations.length;
		}

//		Find var[p_k]'s

		double difference;
		for (i=0; i < observations.length; i++) {
			int[] observation = (int[]) observations[i];

			for (bin=0; bin < partition.length; bin++) {
				difference = ((double) observation[bin] / observationLengths[i]) -
				partition[bin];
				variances[bin] += difference * difference;	// avoiding Math.pow...
			}
		}

		for (bin=0; bin < partition.length; bin++) {
			variances[bin] /= observations.length - 1;
		}

//		Now calculate the magnitude:
//		log \sum_k \alpha_k = 1/(K-1) \sum_k log[ ( E[p_k](1 - E[p_k]) / var[p_k] ) - 1 ]

		double sum = 0.0;

		for (bin=0; bin < partition.length; bin++) {
			if (partition[bin] == 0) { continue; }
			sum += Math.log(( partition[bin] * ( 1 - partition[bin] ) / variances[bin] ) - 1);
		}

		magnitude = Math.exp(sum / (partition.length - 1));

		//System.out.println(distributionToString(magnitude, partition));

		return System.currentTimeMillis() - start;	
	}

	public long learnParametersWithLeaveOneOut(Object[] observations) {

		int[][] binCounts = new int[partition.length][observations.length];
//		System.out.println("got mem: " + (System.currentTimeMillis() - start));

		int[] observationLengths = new int[observations.length];
//		System.out.println("got lengths: " + (System.currentTimeMillis() - start));

		for (int i=0; i < observations.length; i++) {
			int[] observation = (int[]) observations[i];
			for (int bin=0; bin < partition.length; bin++) {
				binCounts[bin][i] = observation[bin];
				observationLengths[i] += observation[bin];
			}
		}
//		System.out.println("init: " + (System.currentTimeMillis() - start));

		return learnParametersWithLeaveOneOut(binCounts, observationLengths);
	}

	/** Learn parameters using Minka's Leave-One-Out (LOO) likelihood */
	public long learnParametersWithLeaveOneOut(int[][] binCounts,
			int[] observationLengths) {
		long start = System.currentTimeMillis();

		int i, bin;

		double[] newParameters = new double[partition.length];
		double[] binSums = new double[partition.length];
		double observationSum = 0.0;
		double parameterSum = 0.0;
		int[] counts;

//		Uniform initialization
//		Arrays.fill(partition, 1.0 / partition.length);

		for (int iteration = 0; iteration < 1000; iteration++) {

			observationSum = 0.0;

			Arrays.fill(binSums, 0.0);

			for (i=0; i < observationLengths.length; i++) {
				observationSum += (observationLengths[i] /
						(observationLengths[i] - 1 + magnitude));
			}

			for (bin=0; bin < partition.length; bin++) {
				counts = binCounts[bin];
				for (i=0; i<counts.length; i++) {
					if (counts[i] >= 2) {
						binSums[bin] += (counts[i] / 
								(counts[i] - 1 + (magnitude * partition[bin])));
					}
				}
			}

			parameterSum = 0.0;
			for (bin=0; bin < partition.length; bin++) {
				if (binSums[bin] == 0.0) {
					newParameters[bin] = 0.000001;
				}
				else {
					newParameters[bin] = (partition[bin] * magnitude * binSums[bin] / 
							observationSum);
				}
				parameterSum += newParameters[bin];
			}

			for (bin=0; bin < partition.length; bin++) {
				partition[bin] = newParameters[bin] / parameterSum;
			}		
			magnitude = parameterSum;

			/*
	  if (iteration % 50 == 0) {
	System.out.println(iteration + ": " + magnitude);
	}
			 */
		}

//		System.out.println(distributionToString(magnitude, partition));

		return System.currentTimeMillis() - start;
	}

	/** Compute the L1 residual between two dirichlets */
	public double absoluteDifference(Dirichlet other) {
		if (partition.length != other.partition.length) {
			throw new IllegalArgumentException("dirichlets must have the same dimension to be compared");
		}

		double residual = 0.0;

		for (int k=0; k<partition.length; k++) {
			residual += Math.abs((partition[k] * magnitude) -
					(other.partition[k] * other.magnitude));
		}

		return residual;
	}

	/** Compute the L2 residual between two dirichlets */
	public double squaredDifference(Dirichlet other) {
		if (partition.length != other.partition.length) {
			throw new IllegalArgumentException("dirichlets must have the same dimension to be compared");
		}

		double residual = 0.0;

		for (int k=0; k<partition.length; k++) {
			residual += Math.pow((partition[k] * magnitude) -
					(other.partition[k] * other.magnitude), 2);
		}

		return residual;
	}

	public void checkBreakeven(double x) {
		long start, clock1, clock2;

		double digammaX = digamma(x);

		for (int n=1; n < 100; n++) {
			start = System.currentTimeMillis();
			for (int i=0; i<1000000; i++) {
				digamma(x + n);
			}
			clock1 = System.currentTimeMillis() - start;

			start = System.currentTimeMillis();
			for (int i=0; i<1000000; i++) {
				digammaDifference(x, n);
			}
			clock2 = System.currentTimeMillis() - start;

			System.out.println(n + "\tdirect: " + clock1 + "\tindirect: " + clock2 +
					" (" + (clock1 - clock2) + ")");
			System.out.println("  " + (digamma(x + n) - digammaX) + " " + digammaDifference(x, n));

		}

	}

	public static String compare(double sum, int k, int n, int w) {

		Dirichlet uniformDirichlet, dirichlet;

		StringBuffer output = new StringBuffer();
		output.append(sum + "\t" + k + "\t" +
				n + "\t" + w + "\t");

		uniformDirichlet = new Dirichlet(k, sum/k);

		dirichlet = new Dirichlet(sum, uniformDirichlet.nextDistribution());
//		System.out.println("real: " + distributionToString(dirichlet.magnitude, 
//		dirichlet.partition));
		Object[] observations = dirichlet.drawObservations(n, w);

//		System.out.println("Done drawing...");

		long time;

		Dirichlet estimatedDirichlet = new Dirichlet(k, sum/k);

		time = estimatedDirichlet.learnParametersWithDigamma(observations);
		output.append(time + "\t" + 
				dirichlet.absoluteDifference(estimatedDirichlet) + "\t");

		estimatedDirichlet = new Dirichlet(k, sum/k);

		time = estimatedDirichlet.learnParametersWithHistogram(observations);
		output.append(time + "\t" + 
				dirichlet.absoluteDifference(estimatedDirichlet) + "\t");

		estimatedDirichlet = new Dirichlet(k, sum/k);

		time = estimatedDirichlet.learnParametersWithMoments(observations);
		output.append(time + "\t" + 
				dirichlet.absoluteDifference(estimatedDirichlet) + "\t");
//		System.out.println("Moments: " + time + ", " + 
//		dirichlet.absoluteDifference(estimatedDirichlet));

		estimatedDirichlet = new Dirichlet(k, sum/k);

		time = estimatedDirichlet.learnParametersWithLeaveOneOut(observations);
		output.append(time + "\t" + 
				dirichlet.absoluteDifference(estimatedDirichlet) + "\t");
//		System.out.println("Leave One Out: " + time + ", " + 
//		dirichlet.absoluteDifference(estimatedDirichlet));

		return output.toString();
	}

	/** What is the probability that these two observations were drawn from
	 *	the same multinomial with symmetric Dirichlet prior alpha, relative 
	 *	to the probability that they were drawn from different multinomials
	 *	both drawn from this Dirichlet?
	 */
	public static double dirichletMultinomialLikelihoodRatio(TIntIntHashMap countsX,
			TIntIntHashMap countsY,
			double alpha, double alphaSum) {
//		The likelihood for one DCM is 
//		Gamma( alpha_sum )	 prod Gamma( alpha + N_i )
//		prod Gamma ( alpha )   Gamma ( alpha_sum + N )

//		When we divide this by the product of two other DCMs with the same
//		alpha parameter, the first term in the numerator cancels with the 
//		first term in the denominator. Then moving the remaining alpha-only
//		term to the numerator, we get
//		prod Gamma(alpha)	  prod Gamma( alpha + X_i + Y_i )
//		Gamma (alpha_sum)	 Gamma( alpha_sum + X_sum + Y_sum )
//		----------------------------------------------------------
//		prod Gamma(alpha + X_i)		  prod Gamma(alpha + Y_i)
//		Gamma( alpha_sum + X_sum )	  Gamma( alpha_sum + Y_sum )


		double logLikelihood = 0.0;
		double logGammaAlpha = logGamma(alpha);

		int totalX = 0;
		int totalY = 0;

		int key, x, y;

		TIntHashSet distinctKeys = new TIntHashSet();
		distinctKeys.addAll(countsX.keys());
		distinctKeys.addAll(countsY.keys());

		TIntIterator iterator = distinctKeys.iterator();
		while (iterator.hasNext()) {
			key = iterator.next();

			x = 0;
			if (countsX.containsKey(key)) {
				x = countsX.get(key);
			}

			y = 0;
			if (countsY.containsKey(key)) {
				y = countsY.get(key);
			}

			totalX += x;
			totalY += y;

			logLikelihood += logGamma(alpha) + logGamma(alpha + x + y)
			- logGamma(alpha + x) - logGamma(alpha + y);
		}

		logLikelihood += logGamma(alphaSum + totalX) + logGamma(alphaSum + totalY) 
		- logGamma(alphaSum) - logGamma(alphaSum + totalX + totalY);

		return logLikelihood;
	}

	/** What is the probability that these two observations were drawn from
	 *	the same multinomial with symmetric Dirichlet prior alpha, relative 
	 *	to the probability that they were drawn from different multinomials
	 *	both drawn from this Dirichlet?
	 */
	public static double dirichletMultinomialLikelihoodRatio(int[] countsX,
			int[] countsY,
			double alpha, double alphaSum) {
//		This is exactly the same as the method that takes
//		Trove hashmaps, but with fixed size arrays.

		if (countsX.length != countsY.length) {
			throw new IllegalArgumentException("both arrays must contain the same number of dimensions");
		}

		double logLikelihood = 0.0;
		double logGammaAlpha = logGamma(alpha);

		int totalX = 0;
		int totalY = 0;

		int x, y;

		for (int key=0; key < countsX.length; key++) {
			x = countsX[key];
			y = countsY[key];

			totalX += x;
			totalY += y;

			logLikelihood += logGammaAlpha + logGamma(alpha + x + y)
			- logGamma(alpha + x) - logGamma(alpha + y);
		}

		logLikelihood += logGamma(alphaSum + totalX) + logGamma(alphaSum + totalY) 
		- logGamma(alphaSum) - logGamma(alphaSum + totalX + totalY);

		return logLikelihood;
	}

	/** This version uses a non-symmetric Dirichlet prior */
	public double dirichletMultinomialLikelihoodRatio(int[] countsX,
			int[] countsY) {

		if (countsX.length != countsY.length || countsX.length != partition.length) {
			throw new IllegalArgumentException("both arrays and the Dirichlet prior must contain the same number of dimensions");
		}

		double logLikelihood = 0.0;
		double alpha;

		int totalX = 0;
		int totalY = 0;

		int x, y;

		for (int key=0; key < countsX.length; key++) {
			x = countsX[key];
			y = countsY[key];

			totalX += x;
			totalY += y;

			alpha = partition[key] * magnitude;
			logLikelihood += logGamma(alpha) + logGamma(alpha + x + y)
			- logGamma(alpha + x) - logGamma(alpha + y);
		}

		logLikelihood += logGamma(magnitude + totalX) + logGamma(magnitude + totalY) 
		- logGamma(magnitude) - logGamma(magnitude + totalX + totalY);

		return logLikelihood;
	}

	/** Similar to the Dirichlet-multinomial test,s this is a likelihood ratio based
	 *	on the Ewens Sampling Formula, which can be considered the distribution of 
	 *	partitions of integers generated by the Chinese restaurant process.
	 */
	public static double ewensLikelihoodRatio(int[] countsX, int[] countsY, double lambda) {

		if (countsX.length != countsY.length) {
			throw new IllegalArgumentException("both arrays must contain the same number of dimensions");
		}

		double logLikelihood = 0.0;
		double alpha;

		int totalX = 0;
		int totalY = 0;
		int total = 0;

		int x, y;

//		First count up the totals
		for (int key=0; key < countsX.length; key++) {
			x = countsX[key];
			y = countsY[key];

			totalX += x;
			totalY += y;
			total += x + y;
		}

//		Now allocate some arrays for the sufficient statisitics 
//		(the number of classes that contain x elements)

		int[] countHistogramX = new int[total + 1];
		int[] countHistogramY = new int[total + 1];
		int[] countHistogramBoth = new int[total + 1];

		for (int key=0; key < countsX.length; key++) {
			x = countsX[key];
			y = countsY[key];

			countHistogramX[ x ]++;
			countHistogramX[ y ]++;
			countHistogramBoth[ x + y ]++;
		}

		for (int j=1; j <= total; j++) {
			if (countHistogramX[ j ] == 0 &&
					countHistogramY[ j ] == 0 &&
					countHistogramBoth[ j ] == 0) {

				continue;
			}

			logLikelihood += (countHistogramBoth[ j ] - countHistogramX[ j ] - countHistogramY[ j ]) *
			Math.log( lambda / j );

			logLikelihood += logGamma(countHistogramX[ j ] + 1) + logGamma(countHistogramY[ j ] + 1)
			- logGamma(countHistogramBoth[ j ] + 1);

		}

		logLikelihood += logGamma(total + 1)
		- logGamma(totalX + 1) - logGamma(totalY + 1);

		logLikelihood += logGamma(lambda + totalX) + logGamma(lambda + totalY) 
		- logGamma(lambda) - logGamma(lambda + totalX + totalY);

		return logLikelihood;
	}

	public static void runComparison() {
		double precision;
		int dimensions;
		int documents;
		int meanSize;

		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("comparison")));

			dimensions = 10;
			for (int j=0; j<5; j++) {
				documents = 100;
				for (int k=0; k<5; k++) {
					meanSize = 100;
					for (int l=0; l<5; l++) {
						System.out.println(dimensions + "\t" +
								dimensions + "\t" +
								documents + "\t" +
								meanSize);

						// Finally, run this ten times.
						for (int m=0; m<10; m++) {
							// always use Dir(1, 1, 1, ... 1) for now...
							out.println(compare(dimensions, dimensions, documents, meanSize));
						}
						out.flush();
						meanSize *= 2;
					}
					documents *= 2;
				}
				dimensions *= 2;
			}

			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

	}

	public static void main (String[] args) {

		testSymmetricConcentration(1000, 100, 1000);

		/*

		Dirichlet prior = new Dirichlet(100, 1.0);
		double[] distribution;
		int[] x, y;

		for (int i=0; i<50; i++) {

			Dirichlet nonSymmetric = new Dirichlet(100, prior.nextDistribution());

			// Two observations from same multinomial
			distribution = nonSymmetric.nextDistribution();
			x = nonSymmetric.drawObservation(100, distribution);
			y = nonSymmetric.drawObservation(100, distribution);

			System.out.print(nonSymmetric.dirichletMultinomialLikelihoodRatio(x, y) + "\t");
			System.out.print(ewensLikelihoodRatio(x, y, 1) + "\t");

			// Two observations from different multinomials

			x = nonSymmetric.drawObservation(100);
			y = nonSymmetric.drawObservation(100);

			System.out.print(ewensLikelihoodRatio(x, y, 0.1) + "\t");
			System.out.println(nonSymmetric.dirichletMultinomialLikelihoodRatio(x, y));		
		}

		*/
	}




	
	
	
	
	
	
	




	public Alphabet getAlphabet ()
	{
		return dict;
	}

	public int size ()
	{
		return partition.length;
	}

	public double alpha (int featureIndex)
	{
		return magnitude * partition[featureIndex];
	}

	public void print () {
		System.out.println ("Dirichlet:");
		for (int j = 0; j < partition.length; j++)
			System.out.println (dict!= null ? dict.lookupObject(j).toString() : j + "=" + magnitude * partition[j]);
	}

	protected double[] randomRawMultinomial (Randoms r)
	{
		double sum = 0;
		double[] pr = new double[this.partition.length];
		for (int i = 0; i < this.partition.length; i++) {
//			if (alphas[i] < 0)
//			for (int j = 0; j < alphas.length; j++)
//			System.out.println (dict.lookupSymbol(j).toString() + "=" + alphas[j]);
			pr[i] = r.nextGamma(magnitude * partition[i]);
			sum += pr[i];
		}
		for (int i = 0; i < this.partition.length; i++)
			pr[i] /= sum;
		return pr;
	}

	public Multinomial randomMultinomial (Randoms r)
	{
		return new Multinomial (randomRawMultinomial(r), dict, partition.length, false, false);
	}

	public Dirichlet randomDirichlet (Randoms r, double averageAlpha)
	{
		double[] pr = randomRawMultinomial (r);
		double alphaSum = pr.length*averageAlpha;
		//System.out.println ("randomDirichlet alphaSum = "+alphaSum);
		for (int i = 0; i < pr.length; i++)
			pr[i] *= alphaSum;
		return new Dirichlet (pr, dict);
	}

	public FeatureSequence randomFeatureSequence (Randoms r, int length)
	{
		Multinomial m = randomMultinomial (r);
		return m.randomFeatureSequence (r, length);
	}

	public FeatureVector randomFeatureVector (Randoms r, int size)
	{
		return new FeatureVector (this.randomFeatureSequence (r, size));
	}

	public TokenSequence randomTokenSequence (Randoms r, int length)
	{
		FeatureSequence fs = randomFeatureSequence (r, length);
		TokenSequence ts = new TokenSequence (length);
		for (int i = 0; i < length; i++)
			ts.add (fs.getObjectAtPosition(i));
		return ts;
	}

	public double[] randomVector (Randoms r)
	{
		return randomRawMultinomial (r);
	}


	public static abstract class Estimator
	{
		ArrayList<Multinomial> multinomials;

		public Estimator ()
		{
			this.multinomials = new ArrayList<Multinomial>();
		}

		public Estimator (Collection<Multinomial> multinomialsTraining)
		{
			this.multinomials = new ArrayList<Multinomial>(multinomialsTraining);
			for (int i = 1; i < multinomials.size(); i++)
				if (((Multinomial)multinomials.get(i-1)).size()
						!= ((Multinomial)multinomials.get(i)).size()
						|| ((Multinomial)multinomials.get(i-1)).getAlphabet()
						!= ((Multinomial)multinomials.get(i)).getAlphabet())
					throw new IllegalArgumentException
					("All multinomials must have same size and Alphabet.");
		}

		public void addMultinomial (Multinomial m)
		{
			// xxx Assert that it is the right class and size
			multinomials.add (m);
		}

		public abstract Dirichlet estimate ();

	}

	public static class MethodOfMomentsEstimator extends Estimator
	{
		public Dirichlet estimate ()
		{
			int dims = multinomials.get(0).size();
			double[] alphas = new double[dims];
			for (int i = 1; i < multinomials.size(); i++)
				multinomials.get(i).addProbabilitiesTo(alphas);
			double alphaSum = 0;
			for (int i = 0; i < alphas.length; i++)
				alphaSum += alphas[i];
			for (int i = 0; i < alphas.length; i++)
				alphas[i] /= alphaSum;	// xxx Fix this to set sum by variance matching
			throw new UnsupportedOperationException ("Not yet implemented.");
			//return new Dirichlet(alphas);
		}

	}


}
