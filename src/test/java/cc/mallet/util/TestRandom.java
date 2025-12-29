/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
     This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
     http://mallet.cs.umass.edu/
     This software is provided under the terms of the Common Public License,
     version 1.0, as published by http://www.opensource.org.    For further
     information, see the file `LICENSE' included with this distribution. */
package cc.mallet.util;

import cc.mallet.types.MatrixOps;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Created: Jan 19, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu">casutton@cs.umass.edu</A>
 * @version $Id: TestRandom.java,v 1.1 2007/10/22 21:37:57 mccallum Exp $
 */
public class TestRandom {

    @Test
    public void testAsJava() {
        Randoms mRand = new Randoms();
        java.util.Random jRand = mRand.asJavaRandom();

        int size = 100000;
        double[] vals = new double[size];
        for (int i = 0; i < size; i++) {
            vals[i] = jRand.nextGaussian();
        }

        assertEquals(0.0, MatrixOps.mean(vals), 0.01);
        assertEquals(1.0, MatrixOps.stddev(vals), 0.01);
    }

}
