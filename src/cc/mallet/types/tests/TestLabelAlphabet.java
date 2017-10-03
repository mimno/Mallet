/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.types.tests;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;
import java.io.IOException;
import java.io.Serializable;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;

/**
 * Created: Nov 24, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestLabelAlphabet.java,v 1.1 2007/10/22 21:37:55 mccallum Exp $
 */
public class TestLabelAlphabet extends TestCase {

  public TestLabelAlphabet (String name)
  {
    super (name);
  }

  private static class Labelee implements Serializable {
    LabelAlphabet dict;
    Label theLabel;

    public Labelee (LabelAlphabet dict, Label theLabel)
    {
      this.dict = dict;
      this.theLabel = theLabel;
    }

  }

  /** Tests how serializing labels separately can lead to big losses.
   *   This currently fails.  I'm not sure what to do about this. -cas
   */
  public void testReadResolve () throws IOException, ClassNotFoundException
  {
    LabelAlphabet dict = new LabelAlphabet ();
    dict.lookupIndex ("TEST1");
    dict.lookupIndex ("TEST2");
    dict.lookupIndex ("TEST3");


    Label t1 = dict.lookupLabel ("TEST1");
    Labelee l = new Labelee (dict, t1);
    Labelee l2 = (Labelee) TestSerializable.cloneViaSerialization (l);

    assertTrue (l.dict == l2.dict);
    assertTrue (dict.lookupLabel("TEST1") == l.theLabel);
    assertTrue (dict.lookupLabel("TEST1") == l2.theLabel);
    assertTrue (l.theLabel == l2.theLabel);
  }

  public static Test suite ()
  {
    return new TestSuite (TestLabelAlphabet.class);
  }


  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestLabelAlphabet (args[i]));
      }
    } else {
      theSuite = (TestSuite) TestLabelAlphabet.suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
