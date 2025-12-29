/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Created: Jan 19, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestStrings.java,v 1.1 2007/10/22 21:37:57 mccallum Exp $
 */
public class TestStrings {

  @Test
  public void testCount() {
    assertEquals(5, Strings.count("abracadabra", 'a'));
    assertEquals(0, Strings.count("hocus pocus", 'z'));
  }

}
