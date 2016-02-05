/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.pipe.iterator.tests;

import junit.framework.*;
import java.util.Iterator;
import java.util.regex.*;

import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;

public class TestPatternMatchIterator extends TestCase
{
  public TestPatternMatchIterator (String name) {
    super (name);
  }
  
  String data = "<p>Inside inside inside</p> outside <p>inside\ninside</p> outside\noutside";
  
  public void testOne () {
    Iterator iter = new PatternMatchIterator( data, Pattern.compile("<p>(.+?)</p>", Pattern.DOTALL));
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
  
  public static Test suite ()
  {
    return new TestSuite (TestPatternMatchIterator.class);
  }
  
  protected void setUp ()
  {
  }
  
  public static void main (String[] args)
  {
    junit.textui.TestRunner.run (suite());
  }
	
}
