/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.util.*;
import java.io.*;

import cc.mallet.types.*;
/**
 * Add specified conjunctions to each instance.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class AugmentableFeatureVectorAddConjunctions extends Pipe implements Serializable
{
	FeatureConjunction.List conjunctions;
	
	public AugmentableFeatureVectorAddConjunctions ()
	{
		conjunctions = new FeatureConjunction.List ();
	}

	public AugmentableFeatureVectorAddConjunctions addConjunction (String name, Alphabet v,
																																 int[] features, boolean[] negations)
	{
		conjunctions.add (new FeatureConjunction (name, v, features, negations));
		return this;
	}
	
	public Instance pipe (Instance carrier)
	{
		AugmentableFeatureVector afv = (AugmentableFeatureVector) carrier.getData();
		conjunctions.addTo (afv, 1.0);
		return carrier;
	}
	
}

