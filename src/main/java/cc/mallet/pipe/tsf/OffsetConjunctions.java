/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Create new features from all possible conjunctions with other
	 (possibly position-offset) features.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.regex.Pattern;

import com.google.errorprone.annotations.Var;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.PropertyList;

public class OffsetConjunctions extends Pipe implements Serializable
{
	int[][] conjunctions;
	boolean includeOriginalSingletons;
	//	boolean includeBeginEndBoundaries;
	Pattern featureRegex;

	static final int maxWindowSize = 50;
	static final PropertyList[] startfs = new PropertyList[maxWindowSize];
	static final PropertyList[] endfs = new PropertyList[maxWindowSize];

	static {
		initStartEndFs ();
	}

	private static void initStartEndFs ()
	{
		for (int i = 0; i < maxWindowSize; i++) {
			startfs[i] = PropertyList.add ("<START"+i+">", 1.0, null);
			endfs[i] = PropertyList.add ("<END"+i+">", 1.0, null);
		}
	}
	
	// To include all the old previous singleton features, pass {{0}}
	// For a conjunction at the current time step, pass {{0,0}}
	// For a conjunction of current and previous, pass {{0,-1}}
	// For a conjunction of the current and next two, pass {{0,1,2}}
	public OffsetConjunctions (boolean includeOriginalSingletons, Pattern featureRegex, int[][] conjunctions)
	{
		this.conjunctions = conjunctions;
		this.featureRegex = featureRegex;
		this.includeOriginalSingletons = includeOriginalSingletons;
	}

	public OffsetConjunctions (boolean includeOriginalSingletons, int[][] conjunctions)
	{
		this (includeOriginalSingletons, null, conjunctions);
	}
	
	public OffsetConjunctions (int[][] conjunctions)
	{
		this (true, conjunctions);
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		int tsSize = ts.size();
		@Var
		PropertyList[] oldfs = null;
		@Var
		PropertyList[] newfs = null;
		try {
			oldfs = new PropertyList[ts.size()];
		}
		catch (Exception e) {
			System.err.println("Exception allocating oldfs: " + e);
		}
		try {
			newfs = new PropertyList[ts.size()];
		}
		catch (Exception e) {
			System.err.println("Exception allocating newfs: " + e);
		}
		
		for (int i = 0; i < tsSize; i++)
			oldfs[i] = ts.get(i).getFeatures ();
		if (includeOriginalSingletons)
			for (int i = 0; i < tsSize; i++)
				newfs[i] = ts.get(i).getFeatures ();

		for (int i = 0; i < tsSize; i++) {
			for (int j = 0; j < conjunctions.length; j++) {				
				// allow conjunction offsets of length n - awc
				PropertyList.Iterator[] iters = getOffsetIters (conjunctions, j, tsSize, i, oldfs);
				if (iters == null)
					continue;
				int[] iterIndices = new int[iters.length];
				for (int ii=0; ii < iterIndices.length; ii++)
					iterIndices[ii] = -1;
				newfs[i] = makeConjunctions (iters, 0, conjunctions, j, tsSize, newfs[i], i, oldfs, iterIndices);
			}
		}
		// Put the new PropertyLists in place
		for (int i = 0; i < ts.size(); i++)
			ts.get(i).setFeatures (newfs[i]);
		return carrier;
	}		

	/** Recursively makes conjunctions by iterating through features at each offset
	 *	@param iters iterate over the PropertyLists at each offset
	 *	@param currIndex which offset we're currently on, e..g 1 in the list [0,1,2]
	 *	@param conjunctions list of conjunctions
	 *	@param j which offset list we're currently on, e.g. [0,1,2] in the list [[0,1],[0,1,2]]
	 *	@param tsSize size of token sequence
	 *	@param newfs new features
	 *	@param tsi token sequence index
	 *	@param oldfs old features
	 *	@param iterIndices counter to keep track how far in each iterator in "iters"
	 *	@return new features
	 */
	private PropertyList makeConjunctions (PropertyList.Iterator[] iters, int currIndex, int[][] conjunctions,
																				 int j, int tsSize, @Var PropertyList newfs, int tsi, PropertyList[] oldfs,
																				 int[] iterIndices) {
		if (iters.length == currIndex) { // base case: add feature for current conjunction of iters
			// avoid redundant doubling of feature space; include only upper triangle
			if (redundant (conjunctions, j, iterIndices)) {
				return newfs;
			}
			@Var
			String newFeature = "";
			@Var
			double newValue = 1.0;
			for (int i=0; i < iters.length; i++) {
				String s = iters[i].getKey();
				if (featureRegex != null && !featureRegex.matcher(s).matches())
					return newfs;
				newFeature += (i==0 ? "" : "_&_") + s + (conjunctions[j][i]==0 ? "" : ("@" + conjunctions[j][i]));
				newValue *= iters[i].getNumericValue();
			}
			//System.err.println ("Adding new feature " + newFeature);
			newfs = PropertyList.add (newFeature, newValue, newfs);
		}
		else { // recursive step
			while (iters[currIndex].hasNext()) {
				iters[currIndex].next();
				iterIndices[currIndex]++;
				newfs = makeConjunctions (iters, currIndex+1, conjunctions, j, tsSize, newfs, tsi, oldfs, iterIndices);
			}
			// reset iterator at currIndex 
			iters[currIndex] = getOffsetIter (conjunctions, j, currIndex, tsSize, tsi, oldfs);
			iterIndices[currIndex] = -1;
		}
		return newfs;
	}

	/** Is the current feature redundant? The current feature is
	 * determined by the current values in iterIndices, which tells us
	 * where we are in each PropertyList.Iterator. We do this test to
	 * ensure we only include the upper triange of conjunctions.
	 * @param conjunctions conjunction array
	 * @param j which offset we're on
	 * @param iterIndices counters for each PropertyList.Iterator
	 * @return true if feature is redundant
	 */
	private boolean redundant (int[][] conjunctions, int j, int[] iterIndices) {
		for (int i=1; i < iterIndices.length; i++) {
			if (conjunctions[j][i-1] == conjunctions[j][i] && iterIndices[i] <= iterIndices[i-1])
				return true;
		}
		return false;		
	}

	/** Get iterators for each token in this offset */
	private PropertyList.Iterator[] getOffsetIters (int [][] conjunctions, int j, int tsSize, int tsi,
																									PropertyList[] oldfs) {		
		PropertyList.Iterator[] iters = new PropertyList.Iterator[conjunctions[j].length];
		// get iterators for offsets
		for (int iteri=0; iteri < iters.length; iteri++) {
			iters[iteri] = getOffsetIter (conjunctions, j, iteri, tsSize, tsi, oldfs);
			if (iters[iteri]==null)
				return null;
		}
		return iters;
	}

	private PropertyList.Iterator getOffsetIter (int [][] conjunctions, int j, int iteri, int tsSize, int tsi,
																							 PropertyList[] oldfs) {
		PropertyList.Iterator iter;
		if (tsi+conjunctions[j][iteri] < 0)
			iter = startfs[-(tsi+conjunctions[j][iteri])-1].iterator();
		else if (conjunctions[j][iteri]+tsi > tsSize-1)
			iter = endfs[tsi+conjunctions[j][iteri]-tsSize].iterator();
		else if (oldfs[conjunctions[j][iteri]+tsi] == null)
			iter = null;
		else
			iter = oldfs[tsi+conjunctions[j][iteri]].iterator();
		return iter;
	}
	
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		@Var
		int size1;
		@Var
		int size2;
		size1 = (conjunctions == null) ? NULL_INTEGER : conjunctions.length;
		out.writeInt(size1);
		if (size1 != NULL_INTEGER) {
			for (int i = 0; i <size1; i++) {
				size2 = (conjunctions[i] == null) ? NULL_INTEGER: conjunctions[i].length;
				out.writeInt(size2);
				if (size2 != NULL_INTEGER) {
					for (int j = 0; j <size2; j++) {
						out.writeInt(conjunctions[i][j]);
					}
				}
			}
		}
		out.writeBoolean(includeOriginalSingletons);
		
		out.writeObject(featureRegex); //add by fuchun
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		@Var
		int size1;
		@Var
		int size2;
		int version = in.readInt ();
		size1 = in.readInt();
		// Deserialization doesn't call the unnamed class initializer, so do it here
		if (startfs[0] == null)
			initStartEndFs ();
		if (size1 == NULL_INTEGER) {
			conjunctions = null;
		}
		else {
			conjunctions = new int[size1][];
			for (int i = 0; i < size1; i++) {
				size2 = in.readInt();
				if (size2 == NULL_INTEGER) {
					conjunctions[i] = null;
				}
				else {
					conjunctions[i] = new int[size2];
					for (int j = 0; j < size2; j++) {
						conjunctions[i][j] = in.readInt();
					}
				}
			}
		}
		includeOriginalSingletons = in.readBoolean();
		featureRegex = (Pattern) in.readObject();//add by fuchun
	
	}
}
