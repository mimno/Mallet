/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Multinomial;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestMultinomial
{
	@Test
	public void testMultinomial ()
	{
		double[] c = new double[] {.2, .3, .1, .4};
		Multinomial m = new Multinomial (c);
		assertTrue (m.probability (0) == .2);
	}

	@Test
	public void testEstimating ()
	{
		Alphabet dict = new Alphabet ();
		Multinomial.Estimator e = new Multinomial.LaplaceEstimator (dict);
		FeatureSequence fs = new FeatureSequence (dict);
		fs.add (dict.lookupIndex ("a"));
		fs.add (dict.lookupIndex ("n"));
		fs.add (dict.lookupIndex ("d"));
		fs.add (dict.lookupIndex ("r"));
		fs.add (dict.lookupIndex ("e"));
		fs.add (dict.lookupIndex ("w"));
		fs.add (dict.lookupIndex ("m"));
		fs.add (dict.lookupIndex ("c"));
		fs.add (dict.lookupIndex ("c"));
		fs.add (dict.lookupIndex ("a"));
		fs.add (dict.lookupIndex ("l"));
		fs.add (dict.lookupIndex ("l"));
		fs.add (dict.lookupIndex ("u"));
		fs.add (dict.lookupIndex ("m"));
		e.increment (fs);
		assertTrue (e.size() == 10);

		Multinomial m = e.estimate ();
		assertTrue (m.size() == 10);
		assertTrue (m.probability (dict.lookupIndex ("a")) == (2.0+1)/(14.0+10));
		assertTrue (m.probability ("w") == (1.0+1)/(14.0+10));
		Multinomial.Logged ml = new Multinomial.Logged (m);
		assertTrue (m.logProbability ("w") == ml.logProbability ("w"));
	}

}
