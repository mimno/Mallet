/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 A FeatureVector for which you can efficiently get the feature with
	 highest value, and other ranks.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;


import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedList;

public class RankedFeatureVector extends FeatureVector
{
	int[] rankOrder;
	private static final int SORTINIT = -1;
	int sortedTo = SORTINIT; /* Extent of latest sort */

	public RankedFeatureVector (final Alphabet dict,
														 final int[] indices,
														 final double[] values)
	{
		super (dict, indices, values);
	}

	public RankedFeatureVector (final Alphabet dict, final double[] values)
	{
		super (dict, values);
	}

  private static double[] subArray (final double[] a, final int begin, final int length)
  {
    double[] ret = new double[length];
    System.arraycopy(a, begin, ret, 0, length);
    return ret;
  }

  public RankedFeatureVector (final Alphabet dict, final double[] values, final int begin, final int length)
  {
    super (dict, subArray(values, begin, length));
  }


  public RankedFeatureVector (final Alphabet dict, final DenseVector v)
	{
		this (dict, v.values);
	}

	public RankedFeatureVector (final Alphabet dict, final AugmentableFeatureVector v)
	{
		super (dict, v.indices, v.values, v.size, v.size,
					 true, true, true);
	}

	public RankedFeatureVector (final Alphabet dict, final SparseVector v)
	{
		super (dict, v.indices, v.values);
	}

	protected void setRankOrder ()
	{
	  this.rankOrder = new int[values.length];

	  final java.util.List<EntryWithOriginalIndex> rankedEntries = new LinkedList<EntryWithOriginalIndex>();

    for (int i = 0; i < rankOrder.length; i++) {
      assert (!Double.isNaN(values[i]));
      rankedEntries.add(new EntryWithOriginalIndex(values[i], i));
    }

    Collections.sort(rankedEntries);

    int i = 0;
    for (EntryWithOriginalIndex entry : rankedEntries) {
      rankOrder[i++] = entry._originalIndex;
    }
	}

	protected void setRankOrder (final int extent, final boolean reset)
	{
		int sortExtent;
		// Set the number of cells to sort, making sure we don't go past the max.
		// Since we are using insertion sort, sorting n-1 sorts the whole array.
		sortExtent = (extent >= values.length) ? values.length - 1: extent;
		if (sortedTo == SORTINIT || reset) { // reinitialize and sort
			this.rankOrder = new int[values.length];
			for (int i = 0; i < rankOrder.length; i++) {
				rankOrder[i] = i;
				assert (!Double.isNaN(values[i]));
			}
		}
		// Selection sort
		for (int i = sortedTo+1; i <= sortExtent; i++) {
			double max = values[rankOrder[i]];
			int maxIndex = i;
			for(int j = i+1; j < rankOrder.length; j++) {
				if (values[rankOrder[j]] > max) {
					max = values[rankOrder[j]];
					maxIndex = j;
				}
			}
			//swap
			int r = rankOrder[maxIndex];
			rankOrder[maxIndex] = rankOrder[i];
			rankOrder[i] = r;
			sortedTo = i;
		}
	}

	//added by Limin Yao, rank the elements ascendingly, the smaller is in the front
	protected void setReverseRankOrder (final int extent, final boolean reset)
	{
		int sortExtent;
		// Set the number of cells to sort, making sure we don't go past the max.
		// Since we are using insertion sort, sorting n-1 sorts the whole array.
		sortExtent = (extent >= values.length) ? values.length - 1: extent;
		if (sortedTo == SORTINIT || reset) { // reinitialize and sort
			this.rankOrder = new int[values.length];
			for (int i = 0; i < rankOrder.length; i++) {
				rankOrder[i] = i;
				assert (!Double.isNaN(values[i]));
			}
		}
		// Selection sort
		for (int i = sortedTo+1; i <= sortExtent; i++) {
			double min = values[rankOrder[i]];
			int minIndex = i;
			for(int j = i+1; j < rankOrder.length; j++) {
				if (values[rankOrder[j]] < min) {
					min = values[rankOrder[j]];
					minIndex = j;
				}
			}
			//swap
			int r = rankOrder[minIndex];
			rankOrder[minIndex] = rankOrder[i];
			rankOrder[i] = r;
			sortedTo = i;
		}
	}


	protected void setRankOrder (final int extent) {
		setRankOrder(extent, false);
	}

	public int getMaxValuedIndex ()
	{
		if (rankOrder == null) {
      setRankOrder (0);
    }
		return getIndexAtRank(0);  // was return rankOrder[0];
	}

	public Object getMaxValuedObject ()
	{
		return dictionary.lookupObject (getMaxValuedIndex());
	}

	public int getMaxValuedIndexIn (final FeatureSelection fs)
	{
		if (fs == null) {
      return getMaxValuedIndex();
    }
		assert (fs.getAlphabet() == dictionary);
		// xxx Make this more efficient!  I'm pretty sure that Java BitSet's can do this more efficiently
		int i = 0;
		while (!fs.contains(rankOrder[i])) {
			setRankOrder (i);
			i++;
		}
		//System.out.println ("RankedFeatureVector.getMaxValuedIndexIn feature="
		//+dictionary.lookupObject(rankOrder[i]));
		return getIndexAtRank(i); // was return rankOrder[i]
	}

	public Object getMaxValuedObjectIn (final FeatureSelection fs)
	{
		return dictionary.lookupObject (getMaxValuedIndexIn(fs));
	}

	public double getMaxValue ()
	{
		if (rankOrder == null) {
      setRankOrder (0);
    }
		return values[rankOrder[0]];
	}

	public double getMaxValueIn (final FeatureSelection fs)
	{
		if (fs == null) {
      return getMaxValue();
    }
		int i = 0;
		while (!fs.contains(i)) {
			setRankOrder (i);
			i++;
		}
		return values[rankOrder[i]];
	}

	public int getIndexAtRank (final int rank)
	{
		setRankOrder (rank);
		return indexAtLocation(rankOrder[rank]); // was return rankOrder[rank]
	}


	public Object getObjectAtRank (final int rank)
	{
		setRankOrder (rank);
		return dictionary.lookupObject (getIndexAtRank(rank)); // was return dictionary.lookupObject (rankOrder[rank]);
	}

	public double getValueAtRank (int rank)
	{
		if (values == null) {
      return 1.0;
    }
		setRankOrder (rank);
		if (rank >= rankOrder.length) {
			rank = rankOrder.length -1;
			System.err.println("rank larger than rankOrder.length. rank = " + rank + "rankOrder.length = " + rankOrder.length);
		}
		if (rankOrder[rank] >= values.length) {
			System.err.println("rankOrder[rank] out of range.");
			return 1.0;
		}
		return values[rankOrder[rank]];
	}


  /**
   * Prints a human-readable version of this vector, with features listed in ranked order.
   * @param out Stream to write to
   */
  public void printByRank (final OutputStream out)
  {
    printByRank(new PrintWriter (new OutputStreamWriter (out), true));
  }

  /**
   * Prints a human-readable version of this vector, with features listed in ranked order.
   * @param out Writer to write to
   */
  public void printByRank (final PrintWriter out)
  {
    for (int rank = 0; rank < numLocations (); rank++) {
      int idx = getIndexAtRank (rank);
      double val = getValueAtRank (rank);
      Object obj = dictionary.lookupObject (idx);
      out.print (obj+":"+val + " ");
    }
  }

  //added by Limin Yao
  public void printTopK (final PrintWriter out, int num)
  {
	  int length = numLocations();
	  if(num>length) {
      num=length;
    }
    for (int rank = 0; rank < num; rank++) {
      int idx = getIndexAtRank (rank);
      double val = getValueAtRank (rank);
      Object obj = dictionary.lookupObject (idx);
      out.print (obj+":"+val + " ");
    }
  }

  public void printLowerK (final PrintWriter out, final int num)
  {
	  int length = numLocations();
	  assert(num < length);
    for (int rank = length-num ; rank < length; rank++) {
      int idx = getIndexAtRank (rank);
      double val = getValueAtRank (rank);
      Object obj = dictionary.lookupObject (idx);
      out.print (obj+":"+val + " ");
    }
  }

	public int getRank (final Object o)
	{
		throw new UnsupportedOperationException ("Not yet implemented");
	}

	public int getRank (final int index)
	{
		throw new UnsupportedOperationException ("Not yet implemented");
	}


	public void set (final int i, final double v)
	{
		throw new UnsupportedOperationException (RankedFeatureVector.class.getName() + " is immutable");
	}


	public interface Factory
	{
		public RankedFeatureVector newRankedFeatureVector (InstanceList ilist);
	}

	public interface PerLabelFactory
	{
		public RankedFeatureVector[] newRankedFeatureVectors (InstanceList ilist);
	}

	private static class EntryWithOriginalIndex implements Comparable<EntryWithOriginalIndex> {
	  private final double _value;
	  private final int _originalIndex;

    public EntryWithOriginalIndex(final double value, final int originalIndex) {
      _value = value;
      _originalIndex = originalIndex;
    }

    /**
     * Sort by value. Greater comes to the left of smaller.
     */
    public int compareTo(final EntryWithOriginalIndex other) {
      return Double.compare(other._value, _value);
    }

	}

}
