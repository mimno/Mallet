package cc.mallet.util;

/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
 This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 http://www.cs.umass.edu/~mccallum/mallet
 This software is provided under the terms of the Common Public License,
 version 1.0, as published by http://www.opensource.org.  For further
 information, see the file `LICENSE' included with this distribution. */

/** 
 * Class of static methods for calculating  statistics of a SparseVector sample 
 * packaged in an InstanceList.
 *
 *  @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
 */

import java.util.Arrays;
import java.util.Iterator;

import cc.mallet.types.*;
import gnu.trove.TIntHashSet;

public class VectorStats {

	/**
	 * Returns a <CODE>SparseVector</CODE> whose entries (taken from the union of
	 * those in the instances) are the expected values of those in the
	 * <CODE>InstanceList</CODE>. This implies the returned vector will not have
	 * binary values.
	 */
	public static SparseVector mean(InstanceList instances) {

		if (instances == null || instances.size() == 0)
			return null;

		Iterator<Instance> instanceItr = instances.iterator();

		SparseVector v;
		Instance instance;
		int indices[];
		int maxSparseIndex = -1;
		int maxDenseIndex = -1;

		// First, we find the union of all the indices used in the instances
		TIntHashSet hIndices = new TIntHashSet(instances.getDataAlphabet().size());

		while (instanceItr.hasNext()) {
			instance = (Instance) instanceItr.next();
			v = (SparseVector) (instance.getData());
			indices = v.getIndices();

			if (indices != null) {
				hIndices.addAll(indices);

				if (indices[indices.length - 1] > maxSparseIndex)
					maxSparseIndex = indices[indices.length - 1];
			} else // dense
			if (v.numLocations() > maxDenseIndex)
				maxDenseIndex = v.numLocations() - 1;
		}

		if (maxDenseIndex > -1) // dense vectors were present
		{
			if (maxSparseIndex > maxDenseIndex)
			// sparse vectors were present and they had greater indices than
			// the dense vectors
			{
				// therefore, we create sparse vectors and
				// add all the dense indices
				for (int i = 0; i <= maxDenseIndex; i++)
					hIndices.add(i);
			} else
			// sparse indices may have been present, but we don't care
			// since they never had indices that exceeded those of the
			// dense vectors
			{
				return mean(instances, maxDenseIndex + 1);
			}
		}

		// reaching this statement implies we can create a sparse vector
		return mean(instances, hIndices.toArray());

	}

	/**
	 * Returns a <CODE>SparseVector</CODE> whose entries (dense with the given
	 * number of indices) are the expected values of those in the
	 * <CODE>InstanceList</CODE>. This implies the returned vector will not have
	 * binary values.
	 */
	public static SparseVector mean(InstanceList instances, int numIndices) {
		SparseVector mv = new SparseVector(new double[numIndices], false);

		return mean(instances, mv);
	}

	/**
	 * Returns a <CODE>SparseVector</CODE> whose entries (the given indices) are
	 * the expected values of those in the <CODE>InstanceList</CODE>. This implies
	 * the returned vector will not have binary values.
	 */
	public static SparseVector mean(InstanceList instances, int[] indices) {

		// Create the mean vector with the indices having all zeros,
		// nothing copied, sorted, and no checks for duplicates.

		// gdruck@cs.umass.edu
		// it is faster to sort indices first
		Arrays.sort(indices);

		SparseVector mv = new SparseVector(indices, new double[indices.length],
		// gdruck@cs.umass.edu
				// it is faster to sort indices first (above)
				// false, true, false);
				false, false, false);

		return mean(instances, mv);

	}

	private static SparseVector mean(InstanceList instances,
			SparseVector meanVector) {
		if (instances == null || instances.size() == 0)
			return null;

		Instance instance;
		SparseVector v;

		Iterator<Instance> instanceItr = instances.iterator();

		double factor = 1.0 / (double) instances.size();

		while (instanceItr.hasNext()) {
			instance = (Instance) instanceItr.next();
			v = (SparseVector) (instance.getData());

			meanVector.plusEqualsSparse(v, factor);
		}

		return meanVector;
	}

	/**
	 * Returns a <CODE>SparseVector</CODE> whose entries (taken from the union of
	 * those in the instances) are the variance of those in the
	 * <CODE>InstanceList</CODE>. This implies the returned vector will not have
	 * binary values.
	 * 
	 * @param unbiased
	 *          Normalizes by N-1 when true, and by N otherwise.
	 */
	public static SparseVector variance(InstanceList instances, boolean unbiased) {
		return variance(instances, mean(instances), unbiased);
	}

	/**
	 * Returns a <CODE>SparseVector</CODE> whose entries (taken from the mean
	 * argument) are the variance of those in the <CODE>InstanceList</CODE>. This
	 * implies the returned vector will not have binary values.
	 * 
	 * @param unbiased
	 *          Normalizes by N-1 when true, and by N otherwise.
	 */

	public static SparseVector variance(InstanceList instances,
			SparseVector mean, boolean unbiased)

	{

		if (instances == null || instances.size() == 0)
			return null;

		double factor = 1.0 / (double) (instances.size() - (unbiased ? 1.0 : 0.0));

		System.out.println("factor = " + factor);

		SparseVector v;

		// var = (x^2 - n*mu^2)/(n-1)

		SparseVector vv = (SparseVector) mean.cloneMatrix();

		vv.timesEqualsSparse(vv, -(double) instances.size() * factor);

		Iterator<Instance> instanceItr = instances.iterator();
		Instance instance;

		while (instanceItr.hasNext()) {
			instance = (Instance) instanceItr.next();
			v = (SparseVector) ((SparseVector) (instance.getData())).cloneMatrix();
			v.timesEqualsSparse(v);

			vv.plusEqualsSparse(v, factor);
		}

		System.out.println("Var:\n" + vv);
		return vv;
	}

	/** Returns unbiased variance */
	public static SparseVector variance(InstanceList instances) {
		return variance(instances, true);
	}

	/** Returns unbiased variance of instances having the given mean. */
	public static SparseVector variance(InstanceList instances, SparseVector mean) {
		return variance(instances, mean, true);
	}

	/**
	 * Square root of variance.
	 * 
	 * @param mean
	 *          Mean of the given instances.
	 * @param unbiased
	 *          Normalizes variance by N-1 when true, and by N otherwise.
	 * @see variance
	 */
	public static SparseVector stddev(InstanceList instances, SparseVector mean,
			boolean unbiased) {

		if (instances.size() == 0)
			return null;

		SparseVector sv = variance(instances, mean, unbiased);

		int dim = sv.numLocations();

		double val;

		for (int i = 0; i < dim; i++) {
			val = sv.valueAtLocation(i);

			sv.setValueAtLocation(i, Math.sqrt(val));
		}

		return sv;

	}

	/** Square root of unbiased variance. */
	public static SparseVector stddev(InstanceList instances) {
		return stddev(instances, true);
	}

	/**
	 * Square root of variance.
	 * 
	 * @param unbiased
	 *          Normalizes variance by N-1 when true, and by N otherwise.
	 * @see variance
	 */
	public static SparseVector stddev(InstanceList instances, boolean unbiased) {
		return stddev(instances, mean(instances), unbiased);
	}

	/** Square root of unbiased variance of instances having the given mean */
	public static SparseVector stddev(InstanceList instances, SparseVector mean) {
		return stddev(instances, mean, true);
	}

}
