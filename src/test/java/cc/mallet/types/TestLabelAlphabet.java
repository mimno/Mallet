/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.types;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.io.Serializable;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;

/**
 * Created: Nov 24, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu">casutton@cs.umass.edu</A>
 * @version $Id: TestLabelAlphabet.java,v 1.1 2007/10/22 21:37:55 mccallum Exp $
 */
public class TestLabelAlphabet {

  private static class Labelee implements Serializable {
    LabelAlphabet dict;
    Label theLabel;

    public Labelee (LabelAlphabet dict, Label theLabel)
    {
      this.dict = dict;
      this.theLabel = theLabel;
    }

    @Override public String toString() {
        return dict.toString() + "\n" + theLabel.toString();
    }
  }

  /** Tests how serializing labels separately can lead to big losses.
   *   This currently fails.  I'm not sure what to do about this. -cas
   */
  @Test
  public void testReadResolve () throws IOException, ClassNotFoundException
  {
    LabelAlphabet dict = new LabelAlphabet ();
    dict.lookupIndex ("TEST1");
    dict.lookupIndex ("TEST2");
    dict.lookupIndex ("TEST3");


    Label t1 = dict.lookupLabel ("TEST1");
    Labelee l = new Labelee (dict, t1);
    Labelee l2 = (Labelee) TestSerializable.cloneViaSerialization (l);

    System.out.println(l);
    System.out.println(l2);

    // DM: Changing this from identity comparison (==) to value comparison (.equals()). LabelAlphabet does not serialize the Label objects, but creates them as needed. They will have the same value, but will be different objects.
    assertTrue (l.dict == l2.dict);
    assertTrue (dict.lookupLabel("TEST1").equals(l.theLabel));
    assertTrue (dict.lookupLabel("TEST1").equals(l2.theLabel));
    assertTrue (l.theLabel.equals(l2.theLabel));
  }

}
