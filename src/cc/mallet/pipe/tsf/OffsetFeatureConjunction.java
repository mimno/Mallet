/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 Create new feature from the conjunction of features from given
    offsets that match given regular expressions.  This can be seen
    as hand-coding in a few of the conjunctions that you'd get
    from {@link OffsetConjunctions}.
 <P>
   For example, creating a pipe with
     <TT>new OffsetFeatureConjunction ("TIME", new String[] { "number", "W=:" "number" }, new int[] { 0, 1, 2 })<TT>
   will create a feature that is true whenever all of (a) a feature at the
   current time matches "number" (b) a feature at the next time step matches "W=:"
   (b) a feature 2 timesteps from now match "number", so that you have a
   simple time detector.

	 <P>If the conjunction passes, then either the first timestep
   (that is, the one all the offsets were computed from), or all matching timesteps,
	 get the feature "TIME" --- depending on the value of the field tagAllTimesteps.

   @author Charles Sutton <a href="mailto:casutton@cs.umass.edu">casutton@cs.umass.edu</a>
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.tsf;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.regex.Pattern;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.PropertyList;

public class OffsetFeatureConjunction extends Pipe implements Serializable
{
	private String thisFeatureName;
	private Pattern[] featurePatterns;
	private int[] offsets;
	private boolean[] isNonNegated;
	private boolean tagAllTimesteps;
	
  /**
   * Create a Pipe for adding conjunctions of specified features.
   * @param thisFeatureName Name of this conjunction feature.
   * @param featureNames String giving name for each subfeature i.
   * @param offsets For each subfeature i, which offset from the current timestep
   *   must i appear at.
   * @param isNonNegated If element i is false, then the negation of the
   *   feature is added to the conjuction.
   */
	public OffsetFeatureConjunction (String thisFeatureName, String[] featureNames, int[] offsets, boolean[] isNonNegated, boolean tagAllTimesteps)
	{
		this.thisFeatureName = thisFeatureName;
		this.featurePatterns = patternify (featureNames);
		this.offsets = offsets;
		this.isNonNegated = isNonNegated;
		this.tagAllTimesteps = tagAllTimesteps;
	}

	private static boolean[] trueArray (int length) {
		boolean[] ret = new boolean[length];
		for (int i = 0; i < length; i++)
			ret[i] = true;
		return ret;
	}

	private Pattern[] patternify (String[] regex) {
		Pattern[] retval = new Pattern [regex.length];
		for (int i = 0; i < regex.length; i++) {
			retval [i] = Pattern.compile (regex[i]);
		}
		return retval;
	}

	public OffsetFeatureConjunction (String thisFeatureName, String[] featureNames, int[] offsets,
																	 boolean tagAllTimesteps)
	{
		this (thisFeatureName, featureNames, offsets, trueArray(featureNames.length), tagAllTimesteps);
	}

	public OffsetFeatureConjunction (String thisFeatureName, String[] featureNames, int[] offsets)
	{
		this (thisFeatureName, featureNames, offsets, trueArray(featureNames.length), false);
	}

  public boolean isTagAllTimesteps ()
  {
    return tagAllTimesteps;
  }

  public String getFeatureName ()
  {
    return thisFeatureName;
  }

  public Pattern[] getFeaturePatterns ()
  {
    return featurePatterns;
  }

  public int[] getOffsets ()
  {
    return offsets;
  }

  public boolean[] getNonNegated ()
  {
    return isNonNegated;
  }

  public Instance pipe (Instance carrier)
	{
		TokenSequence ts = (TokenSequence) carrier.getData();
		int tsSize = ts.size();
		for (int t = 0; t < tsSize; t++) {
      // Check whether the conjunction is true at time step t
      boolean passes = true;
			for (int fnum = 0; fnum < featurePatterns.length; fnum++) {
				int pos = t + offsets[fnum];
				if (!(pos >= 0 && pos < tsSize)) {
					passes = false;
					break;
				}
        boolean featurePresent = hasMatchingFeature (ts.get(pos), featurePatterns [fnum]);
        if (featurePresent != isNonNegated [fnum]) {
					passes = false;
					break;
				}
			}
			if (passes) {
				if (tagAllTimesteps) {
					for (int fnum = 0; fnum < featurePatterns.length; fnum++) {
						int pos = t + offsets[fnum];
						ts.get(pos).setFeatureValue (thisFeatureName, 1.0);
					}
				} else {
					ts.get(t).setFeatureValue (thisFeatureName, 1.0);
				}
			}
		}

		return carrier;
	}

	private boolean hasMatchingFeature (Token token, Pattern pattern)
	{
		PropertyList.Iterator iter = token.getFeatures ().iterator ();
		while (iter.hasNext()) {
			iter.next();
			if (pattern.matcher (iter.getKey()). matches ()) {
				if (iter.getNumericValue() == 1.0) {
					return true;
				}
			}
		}
		return false;
	}

	// Serialization
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	private static final int NULL_INTEGER = -1;
	
	private void writeObject (ObjectOutputStream out) throws IOException
  {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (thisFeatureName);
    out.writeBoolean (tagAllTimesteps);
    int size;
		size = (featurePatterns == null) ? NULL_INTEGER : featurePatterns.length;
		out.writeInt(size);
		if (size != NULL_INTEGER) {
			for (int i = 0; i <size; i++) {
				out.writeObject (featurePatterns[i]);
				out.writeInt (offsets[i]);
				out.writeBoolean (isNonNegated[i]);
			}
		}
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
		int size;
		int version = in.readInt ();
		thisFeatureName = (String) in.readObject();
    if (version >= 1) tagAllTimesteps = in.readBoolean ();

    size = in.readInt();
		if (size == NULL_INTEGER) {
			featurePatterns = null;
			offsets = null;
			isNonNegated = null;
		}	else {
			featurePatterns = new Pattern[size];
			offsets = new int[size];
			isNonNegated = new boolean[size];
			for (int i = 0; i < size; i++) {
				featurePatterns[i] = (Pattern) in.readObject();
				offsets[i] = in.readInt();
				isNonNegated[i] = in.readBoolean();
			}
		}
	}

}
