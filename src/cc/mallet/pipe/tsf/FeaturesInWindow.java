/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Create new features from features (matching a regex within a window +/- the current position).

	 For example, 
	 <br><code>
	 FeaturesInWindow p = new FeaturesInWindow("PREV-", -1, 1, Pattern.compile("POS-.*"), true)
	 </code> <br>
	 will create a pipe that adds a feature to the current position for each
	 feature in the previous starting with "POS-".  So if the previous position
	 has "POS-NN" we add "PREV-POS-NN".   The last argument to the constructor is
	 currently ignored.  The alternative constructor matches all patterns, so: 
	 <br><code>
	 FeaturesInWindow p = new FeaturesInWindow(s, l, r);
	 </code> <br>
	 is equivalent to 
	 <br><code>
	 FeaturesInWindow p = new FeaturesInWindow("PREV-", -1, 1, Pattern.compile(".*"), true);
	 </code> <br>
	 but more efficient, since we don't actually check using the Pattern. 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;

import java.io.*;
import java.util.regex.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.PropertyList;

public class FeaturesInWindow extends Pipe implements Serializable
{
	String namePrefix, namePrefixLeft;
	int leftBoundary;
	int rightBoundary;
	Pattern featureRegex;
	boolean includeBeginEndBoundaries;
	boolean includeCurrentToken = false;

	private static final int maxWindowSize = 20;
	private static final PropertyList[] startfs = new PropertyList[maxWindowSize];
	private static final PropertyList[] endfs = new PropertyList[maxWindowSize];
	
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

	/** @param namePrefix what to prepend to feature names
		* @param leftBoundaryOffset left boundary of the window (e.g. -1 means
		*                           include the previous word
		* @param rightBoundaryOffset right boundary for this window (e.g. 1 means
		*                           include the current position, but not the next
		* @param featureRegex add only for features matching this (null = always match
		* @param includeBeginEndBoundaries ignored
		*/
	public FeaturesInWindow (String namePrefix, int leftBoundaryOffset, int rightBoundaryOffset,
													 Pattern featureRegex, boolean includeBeginEndBoundaries)
	{
		this.namePrefix = namePrefix;
		this.leftBoundary = leftBoundaryOffset;
		this.rightBoundary = rightBoundaryOffset;
		this.featureRegex = featureRegex;
		this.includeBeginEndBoundaries = includeBeginEndBoundaries;
	}

	/** 
		equivalent to <br>
		<code>
		FeaturesInWindow((namePrefix, leftBoundaryOffset, rightBoundaryOffset, null, true);
		</code>
		*/
	public FeaturesInWindow (String namePrefix, int leftBoundaryOffset, int rightBoundaryOffset)
	{
		this (namePrefix, leftBoundaryOffset, rightBoundaryOffset, null, true);
	}
	
	public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		int tsSize = ts.size();
		PropertyList[] newFeatures = new PropertyList[tsSize];
		for (int i = 0; i < tsSize; i++) {
			Token t = ts.get (i);
			PropertyList pl = t.getFeatures();
			newFeatures[i] = pl;
			for (int position = i + leftBoundary; position < i + rightBoundary; position++) {
				if (position == i && !includeCurrentToken)
					continue;
				PropertyList pl2;
				if (position < 0)
					pl2 = startfs[-position];
				else if (position >= tsSize)
					pl2 = endfs[position-tsSize];
				else
					pl2 = ts.get(position).getFeatures ();
				PropertyList.Iterator pl2i = pl2.iterator();
				while (pl2i.hasNext()) {
					pl2i.next();
					String key = pl2i.getKey();
					if (featureRegex == null || featureRegex.matcher(key).matches()) {
						newFeatures[i] = PropertyList.add ((namePrefixLeft == null || position-i>0 ? namePrefix : namePrefixLeft)+key,
																							 pl2i.getNumericValue(), newFeatures[i]);
					}
				}
			}
		}
		for (int i = 0; i < tsSize; i++) {
			// Put the new PropertyLists in place
			ts.get (i).setFeatures (newFeatures[i]);
		}
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (namePrefix);
		out.writeInt (leftBoundary);
		out.writeInt (rightBoundary);
		out.writeObject (featureRegex);
		out.writeBoolean (includeBeginEndBoundaries);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		namePrefix = (String) in.readObject();
		leftBoundary = in.readInt ();
		rightBoundary = in.readInt ();
		featureRegex = (Pattern) in.readObject();
		includeBeginEndBoundaries = in.readBoolean();
	}

}
