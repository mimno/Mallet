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

import java.io.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.PropertyList;

public class OffsetPropertyConjunctions extends Pipe implements Serializable
{
	int[][] conjunctions;
	boolean includeOriginalSingletons;
	String propertyKey;
	
	// To include all the old previous singleton features, pass {{0}}
	// For a conjunction at the current time step, pass {{0,0}}
	// For a conjunction of current and previous, pass {{0,-1}}
	// For a conjunction of the current and next two, pass {{0,1,2}}
	private OffsetPropertyConjunctions (boolean includeOriginalSingletons, String propertyKey, int[][] conjunctions)
	{
		this.conjunctions = conjunctions;
		this.includeOriginalSingletons = includeOriginalSingletons;
		this.propertyKey = propertyKey;
	}

	public OffsetPropertyConjunctions (boolean includeOriginalSingletons, int[][] conjunctions)
	{
		this (includeOriginalSingletons, null, conjunctions);
	}
		
	public OffsetPropertyConjunctions (int[][] conjunctions)
	{
		this (true, conjunctions);
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		int tsSize = ts.size();
		PropertyList[] oldfs = new PropertyList[ts.size()];
		PropertyList[] newfs = new PropertyList[ts.size()];
		for (int i = 0; i < tsSize; i++)
			oldfs[i] = ts.get(i).getFeatures ();
		if (includeOriginalSingletons)
			for (int i = 0; i < tsSize; i++)
				newfs[i] = ts.get(i).getFeatures ();

		for (int i = 0; i < ts.size(); i++) {
			//System.out.println ("OffsetPropertyConjunctions: ts index="+i+", conjunction =");
			conjunctionList: for (int j = 0; j < conjunctions.length; j++) {
				// Make sure that the offsets in the conjunction are all available at this position
				for (int k = 0; k < conjunctions[j].length; k++) {
					if (conjunctions[j][k] + i < 0
							|| conjunctions[j][k] + i > tsSize-1
							|| oldfs[i+conjunctions[j][k]] == null)
						continue conjunctionList;
					//System.out.print (" "+conjunctions[j][k]);
				}
				//System.out.print ("\n");

				// Add the features for this conjunction
				if (conjunctions[j].length == 1) {
					int offset = conjunctions[j][0];
					if (offset == 0 && includeOriginalSingletons)
						throw new IllegalArgumentException ("Original singletons already there.");
					PropertyList.Iterator iter = oldfs[i+offset].iterator();
					while (iter.hasNext()) {
						iter.next();
						if (propertyKey != null && !propertyKey.equals(iter.getKey()))
							continue;
						String key = iter.getKey() + (offset==0 ? "" : "@"+offset);
						newfs[i] = PropertyList.add (key, iter.getNumericValue(), newfs[i]);
					}

				} else if (conjunctions[j].length == 2) {
					//System.out.println ("token="+ts.getToken(i).getText()+" conjunctionIndex="+j);
					int offset0 = conjunctions[j][0];
					int offset1 = conjunctions[j][1];
					PropertyList.Iterator iter0 = oldfs[i+offset0].iterator();
					int iter0i = -1;
					while (iter0.hasNext()) {
						iter0i++;
						iter0.next();
						if (propertyKey != null && !propertyKey.equals(iter0.getKey()))
							continue;
						PropertyList.Iterator iter1 = oldfs[i+offset1].iterator();
						int iter1i = -1;
						while (iter1.hasNext()) {
							iter1i++;
							iter1.next();
							if (propertyKey != null && !propertyKey.equals(iter1.getKey()))
								continue;
							// Avoid redundant doubling of feature space; include only upper triangle
							//System.out.println ("off0="+offset0+" off1="+offset1+" iter0i="+iter0i+" iter1i="+iter1i);
							if (offset0 == offset1 && iter1i <= iter0i) continue; 
							//System.out.println (">off0="+offset0+" off1="+offset1+" iter0i="+iter0i+" iter1i="+iter1i);
							String key = iter0.getKey() + (offset0==0 ? "" : "@"+offset0)
													 +"&"+iter1.getKey() + (offset1==0 ? "" : "@"+offset1);
							newfs[i] = PropertyList.add (key, iter0.getNumericValue() * iter1.getNumericValue(), newfs[i]);
						}
					}

				} else if (conjunctions[j].length == 3) {
					int offset0 = conjunctions[j][0];
					int offset1 = conjunctions[j][1];
					int offset2 = conjunctions[j][2];
					PropertyList.Iterator iter0 = oldfs[i+offset0].iterator();
					int iter0i = -1;
					while (iter0.hasNext()) {
						iter0i++;
						iter0.next();
						if (propertyKey != null && !propertyKey.equals(iter0.getKey()))
							continue;
						PropertyList.Iterator iter1 = oldfs[i+offset1].iterator();
						int iter1i = -1;
						while (iter1.hasNext()) {
							iter1i++;
							iter1.next();
							if (propertyKey != null && !propertyKey.equals(iter1.getKey()))
								continue;
							// Avoid redundant doubling of feature space; include only upper triangle
							if (offset0 == offset1 && iter1i <= iter0i) continue; 
							PropertyList.Iterator iter2 = oldfs[i+offset2].iterator();
							int iter2i = -1;
							while (iter2.hasNext()) {
								iter2i++;
								iter2.next();
								if (propertyKey != null && !propertyKey.equals(iter2.getKey()))
									continue;
								// Avoid redundant doubling of feature space; include only upper triangle
								if (offset1 == offset2 && iter2i <= iter1i) continue; 
								String key = iter0.getKey() + (offset0==0 ? "" : "@"+offset0)
														 +"&"+iter1.getKey() + (offset1==0 ? "" : "@"+offset1)
														 +"&"+iter2.getKey() + (offset2==0 ? "" : "@"+offset2);
								newfs[i] = PropertyList.add (key, iter0.getNumericValue() * iter1.getNumericValue()
																						 * iter2.getNumericValue(), newfs[i]);
							}
						}
					}
				} else {
					throw new UnsupportedOperationException ("Conjunctions of length 4 or more not yet implemented.");
				}
			}
		}

		// Put the new PropertyLists in place
		for (int i = 0; i < ts.size(); i++)
			ts.get(i).setFeatures (newfs[i]);
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		int size1, size2;
		size1 = (conjunctions == null) ? NULL_INTEGER : conjunctions.length;
		out.writeInt(size1);
		if (size1 != NULL_INTEGER) {
			for (int i = 0; i <size1; i++) {
				size2 = (conjunctions[i] == null) ? NULL_INTEGER: conjunctions.length;
				out.writeInt(size2);
				if (size2 != NULL_INTEGER) {
					for (int j = 0; j <size2; j++) {
						out.writeInt(conjunctions[i][j]);
					}
				}
			}
		}
		out.writeBoolean(includeOriginalSingletons);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int size1, size2;
		int version = in.readInt ();
		size1 = in.readInt();;
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
	}

}
