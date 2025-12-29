/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.pipe;

import static org.junit.Assert.*;

import org.junit.Test;

import cc.mallet.fst.SimpleTagger;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PipeUtils;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.SimpleTaggerSentence2TokenSequence;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;

/**
 * Created: Aug 28, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu"></A>
 * @version $Id: TestPipeUtils.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestPipeUtils {

  private static class StupidPipe extends Pipe {

    public Instance pipe (Instance carrier)
    {
      System.out.println ("StupidPipe says hi.");
      return carrier;
    }
  }

  private static String data = "f1 f2 CL1\nf1 f3 CL2";

  /* This test is not failing, and I don't know what it's supposed to do.
       Pipe.getDataAlphabet() does not test whether the alphabet is null.

  public void testPipesAreStupid ()
  {
    Pipe p1 = new StupidPipe ();
    Pipe p2 = new SimpleTaggerSentence2TokenSequence ();
    // initialize p2's dict
    p2.instanceFrom(new Instance (data, null, null, null));

    Pipe serial = new SerialPipes (new Pipe[] { p1, p2 });
    try {
      serial.getDataAlphabet();
      assertTrue ("Test failed: Should have generated exception.", false);
    } catch (IllegalStateException e) {}
  }
  */

  @Test
  public void testConcatenatePipes ()
  {
    Pipe p1 = new StupidPipe ();
    Pipe p2 = new SimpleTagger.SimpleTaggerSentence2FeatureVectorSequence ();
    // initialize p2's dict
    p2.instanceFrom(new Instance (data, null, null, null));

    assertEquals (3, p2.getDataAlphabet ().size());

    Pipe serial = PipeUtils.concatenatePipes (p1, p2);
    Alphabet dict = serial.getDataAlphabet ();

    assertEquals (3, dict.size ());
    assertTrue (dict == p2.getDataAlphabet ());
  }

  @Test
  public void testConcatenateNullPipes ()
  {
    Pipe p1 = new StupidPipe ();
    Pipe p2 = new SimpleTagger.SimpleTaggerSentence2FeatureVectorSequence ();

    Pipe serial = PipeUtils.concatenatePipes (p1, p2);

    p2.instanceFrom(new Instance (data, null, null, null));
    assertEquals (3, serial.getDataAlphabet ().size ());
  }

  /* This test is not failing, and the function it tests does not appear to be used anywhere but this test.


  public void testConcatenateBadPipes ()
  {
    Pipe p1 = new SimpleTaggerSentence2TokenSequence ();
    // force resolving data alphabet
    Alphabet dict1 = p1.getDataAlphabet ();

    Pipe p2 = new SimpleTaggerSentence2TokenSequence ();
    // force resolving data alphabet
    Alphabet dict2 = p2.getDataAlphabet ();

    assertTrue (dict1 != dict2);

    try {
      PipeUtils.concatenatePipes (p1, p2);
      assertTrue ("Test failed: concatenatePipes() allowed putting together incompatible alphabets.", false);

    } catch (IllegalArgumentException e) {
      // Exception expected
    }
  }
      */

}
