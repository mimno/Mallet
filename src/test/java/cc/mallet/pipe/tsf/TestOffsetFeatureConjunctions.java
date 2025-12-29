/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe.tsf;

import static org.junit.Assert.*;

import java.util.regex.Pattern;
import java.io.IOException;

import org.junit.Test;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PrintInputAndTarget;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.SimpleTaggerSentence2TokenSequence;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.pipe.tsf.OffsetFeatureConjunction;
import cc.mallet.pipe.tsf.RegexMatches;
import cc.mallet.pipe.tsf.TokenText;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.types.TestSerializable;

/**
 * $Id: TestOffsetFeatureConjunctions.java,v 1.1 2007/10/22 21:37:57 mccallum Exp $
 */
public class TestOffsetFeatureConjunctions {

  private static String[] doc1 =  { "Meet\nme\nat\n4\nPM\ntomorrow" };

  @Test
  public void testMultiTag ()
  {
    Pipe mtPipe = new SerialPipes (new Pipe[] {
            new SimpleTaggerSentence2TokenSequence (),
            new TokenText (),
            new RegexMatches ("digits", Pattern.compile ("[0-9]+")),
            new RegexMatches ("ampm", Pattern.compile ("[aApP][mM]")),
            new OffsetFeatureConjunction ("time",
                    new String[] { "digits", "ampm" },
                    new int[] { 0, 1 },
                    true),
            new PrintInputAndTarget (),
    });
    Pipe noMtPipe = new SerialPipes (new Pipe[] {
            new SimpleTaggerSentence2TokenSequence (),
            new TokenText (),
            new RegexMatches ("digits", Pattern.compile ("[0-9]+")),
            new RegexMatches ("ampm", Pattern.compile ("[aApP][mM]")),
            new OffsetFeatureConjunction ("time",
                    new String[] { "digits", "ampm" },
                    new int[] { 0, 1 },
                    false),
            new PrintInputAndTarget (),
    });

    InstanceList mtLst = new InstanceList (mtPipe);
    InstanceList noMtLst = new InstanceList (noMtPipe);

    mtLst.addThruPipe (new ArrayIterator (doc1));
    noMtLst.addThruPipe (new ArrayIterator (doc1));

    Instance mtInst = mtLst.get (0);
    Instance noMtInst = noMtLst.get (0);

    TokenSequence mtTs = (TokenSequence) mtInst.getData ();
    TokenSequence noMtTs = (TokenSequence) noMtInst.getData ();

    assertEquals (6, mtTs.size ());
    assertEquals (6, noMtTs.size ());

    assertEquals (1.0, mtTs.get (3).getFeatureValue ("time"), 1e-5);
    assertEquals (1.0, noMtTs.get (3).getFeatureValue ("time"), 1e-5);
    assertEquals (1.0, mtTs.get (4).getFeatureValue ("time"), 1e-5);
    assertEquals (0.0, noMtTs.get (4).getFeatureValue ("time"), 1e-5);
  }

  @Test
  public void testMultiTagSerialization () throws IOException, ClassNotFoundException
  {
    Pipe origPipe = new SerialPipes (new Pipe[] {
            new SimpleTaggerSentence2TokenSequence (),
            new TokenText (),
            new RegexMatches ("digits", Pattern.compile ("[0-9]+")),
            new RegexMatches ("ampm", Pattern.compile ("[aApP][mM]")),
            new OffsetFeatureConjunction ("time",
                    new String[] { "digits", "ampm" },
                    new int[] { 0, 1 },
                    true),
            new PrintInputAndTarget (),
    });

    Pipe mtPipe = (Pipe) TestSerializable.cloneViaSerialization (origPipe);
    InstanceList mtLst = new InstanceList (mtPipe);
    mtLst.addThruPipe (new ArrayIterator (doc1));
    Instance mtInst = mtLst.get (0);
    TokenSequence mtTs = (TokenSequence) mtInst.getData ();
    assertEquals (6, mtTs.size ());
    assertEquals (1.0, mtTs.get (3).getFeatureValue ("time"), 1e-5);
    assertEquals (1.0, mtTs.get (4).getFeatureValue ("time"), 1e-5);
  }

}
