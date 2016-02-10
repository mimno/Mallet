/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe.tests;

import java.io.*;

import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.InstanceList;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 *  Unit Test for PipeInputIterators
 *
 *
 * Created: Thu Feb 26 14:27:15 2004
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: TestIterators.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestIterators extends TestCase {

	public TestIterators (String name){
		super(name);
	}


	public void testParenGroupIterator ()
	{
		String input = "(a (b c) ((d))  ) f\n\n (3\n 4) (  6) ";
		Reader reader = new StringReader (input);
		ParenGroupIterator it = new ParenGroupIterator (reader);
		Pipe pipe = new Noop();
		pipe.setTargetProcessing (false);

		InstanceList lst = new InstanceList (pipe);
		lst.addThruPipe (it);

		assertEquals (3, lst.size());
		assertEquals ("(a (b c) ((d))  )", lst.get(0).getData());
		assertEquals ("(3\n 4)", lst.get(1).getData());
		assertEquals ("(  6)", lst.get(2).getData());
	}


/**
 * @return a <code>TestSuite</code>
 */
	public static TestSuite suite(){
		return new TestSuite (TestIterators.class);
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}// TestIterators
