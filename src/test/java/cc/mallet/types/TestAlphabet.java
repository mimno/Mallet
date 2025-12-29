/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.types;
import org.junit.Test;
import static org.junit.Assert.*;
import cc.mallet.types.Alphabet;
import java.io.IOException;

/**
 * Created: Nov 24, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestAlphabet.java,v 1.1 2007/10/22 21:37:55 mccallum Exp $
 */
public class TestAlphabet {

  @Test
  public void testNotFound ()
  {
    Alphabet dict = new Alphabet ();
    dict.lookupIndex ("TEST1");
    dict.lookupIndex ("TEST2");
    dict.lookupIndex ("TEST3");
    assertEquals (-1, dict.lookupIndex ("TEST4", false));
    assertEquals (3, dict.size());
    assertEquals (3, dict.lookupIndex ("TEST4", true));
  }

  @Test
  public void testReadResolve () throws IOException, ClassNotFoundException
  {
    Alphabet dict = new Alphabet ();
    dict.lookupIndex ("TEST1");
    dict.lookupIndex ("TEST2");
    dict.lookupIndex ("TEST3");
    Alphabet dict2 = (Alphabet) TestSerializable.cloneViaSerialization (dict);
    assertTrue (dict == dict2);
  }

}
