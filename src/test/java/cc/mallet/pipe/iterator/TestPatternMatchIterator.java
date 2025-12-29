/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.pipe.iterator;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

import com.google.errorprone.annotations.Var;

import cc.mallet.pipe.iterator.PatternMatchIterator;
import cc.mallet.types.Instance;

public class TestPatternMatchIterator
{
  String data = "<p>Inside inside inside</p> outside <p>inside\ninside</p> outside\noutside";

  @Test
  public void testOne () {
    Iterator iter = new PatternMatchIterator( data, Pattern.compile("<p>(.+?)</p>", Pattern.DOTALL));
    @Var
    int i=0;
    while (iter.hasNext()) {
      Instance inst = (Instance) iter.next();
      System.out.println( inst.getName() + " : " + inst.getData() );
      if (i++==0)
        assertTrue (inst.getData().equals("Inside inside inside"));
      else
        assertTrue (inst.getData().equals("inside\ninside"));
    }
  }

  @Before
  public void setUp ()
  {
  }

}
