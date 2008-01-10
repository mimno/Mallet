/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://mallet.cs.umass.edu/
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.test;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.IOException;

import cc.mallet.grmm.types.*;
import cc.mallet.types.tests.TestSerializable;

/**
 * Created: Aug 11, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestAssignment.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestAssignment extends TestCase {
  private Variable[] vars;

  /**
   * Constructs a test case with the given name.
   */
  public TestAssignment (String name)
  {
    super (name);
  }

  protected void setUp () throws Exception
  {
    vars = new Variable[] {
      new Variable (2),
      new Variable (2),
    };
  }

  public void testSimple ()
  {
    Assignment assn = new Assignment (vars, new int[] { 1, 0 });
    assertEquals (1, assn.get (vars [0]));
    assertEquals (0, assn.get (vars [1]));
    assertEquals (new Integer (0), assn.getObject (vars[1]));
  }

  public void testScale ()
  {
    Assignment assn = new Assignment (vars, new int[] { 1, 0 });
    assn.addRow (vars, new int[] { 1, 0 });
    assn.addRow (vars, new int[] { 1, 1 });
    Assignment assn2 = new Assignment (vars, new int[] { 1, 0 });
    assn.normalize ();
    assertEquals (0.666666, assn.value (assn2), 1e-5);
  }

  public void testScaleMarginalize ()
  {
    Assignment assn = new Assignment (vars, new int[] { 1, 0 });
    assn.addRow (vars, new int[] { 1, 0 });
    assn.addRow (vars, new int[] { 1, 1 });
    assn.normalize ();
    Factor mrg = assn.marginalize (vars[1]);
    Assignment assn2 = new Assignment (vars[1], 0);
    assertEquals (0.666666, mrg.value (assn2), 1e-5);
  }

  public void testSerialization () throws IOException, ClassNotFoundException
  {
    Assignment assn = new Assignment (vars, new int[] { 1, 0 });

    Assignment assn2 = (Assignment) TestSerializable.cloneViaSerialization (assn);
    assertEquals (2, assn2.numVariables ());
    assertEquals (1, assn2.numRows ());
    assertEquals (1, assn.get (vars [0]));
    assertEquals (0, assn.get (vars [1]));
  }

  public void testMarginalize ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.addRow (vars, new int[] { 1, 0 });

    Assignment assn2 = (Assignment) assn.marginalize (vars[0]);
    assertEquals (2, assn2.numRows ());
    assertEquals (1, assn2.size ());
    assertEquals (vars[0], assn2.getVariable (0));
    assertEquals (1, assn.get (0, vars[0]));
    assertEquals (1, assn.get (1, vars[0]));
  }

  public void testMarginalizeOut ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.addRow (vars, new int[] { 1, 0 });

    Assignment assn2 = (Assignment) assn.marginalizeOut (vars[1]);
    assertEquals (2, assn2.numRows ());
    assertEquals (1, assn2.size ());
    assertEquals (vars[0], assn2.getVariable (0));
    assertEquals (1, assn.get (0, vars[0]));
    assertEquals (1, assn.get (1, vars[0]));
  }

  public void testUnion ()
  {
    Assignment assn1 = new Assignment ();
    assn1.addRow (new Variable[] { vars[0] }, new int[] { 1 });
    Assignment assn2 = new Assignment ();
    assn2.addRow (new Variable[] { vars[1] }, new int[] { 0 });

    Assignment assn3 = Assignment.union (assn1, assn2);
    assertEquals (1, assn3.numRows ());
    assertEquals (2, assn3.numVariables ());
    assertEquals (1, assn3.get (0, vars[0]));
    assertEquals (0, assn3.get (0, vars[1]));
  }

  public void testMultiRow ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.addRow (vars, new int[] { 1, 0 });
    assertEquals (2, assn.numRows ());
    assertEquals (1, assn.get (0, vars[1]));
    assertEquals (0, assn.get (1, vars[1]));

    try {
      assn.get (vars[1]);
      fail ();
    } catch (IllegalArgumentException e) {}

  }

  public void testSetRow ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.addRow (vars, new int[] { 1, 0 });
    assertEquals (1, assn.get (0, vars[0]));

    assn.setRow (0, new int[] { 0, 0 });
    assertEquals (2, assn.numRows ());
    assertEquals (0, assn.get (0, vars[0]));
    assertEquals (0, assn.get (0, vars[1]));
    assertEquals (1, assn.get (1, vars[0]));
    assertEquals (0, assn.get (1, vars[1]));
  }

  public void testSetRowFromAssn ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.addRow (vars, new int[] { 1, 0 });
    assertEquals (1, assn.get (0, vars[0]));

    Assignment assn2 = new Assignment ();
    assn2.addRow (vars, new int[] { 0, 0 });

    assn.setRow (0, assn2);

    assertEquals (2, assn.numRows ());
    assertEquals (0, assn.get (0, vars[0]));
    assertEquals (0, assn.get (0, vars[1]));
    assertEquals (1, assn.get (1, vars[0]));
    assertEquals (0, assn.get (1, vars[1]));
  }

  public void testSetValue ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.setValue (vars[0], 0);
    assertEquals (1, assn.numRows ());
    assertEquals (0, assn.get (0, vars[0]));
    assertEquals (1, assn.get (0, vars[1]));
  }

  public void testSetValueDup ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });

    Assignment dup = (Assignment) assn.duplicate ();
    dup.setValue (vars[0], 0);
    assertEquals (1, dup.numRows ());
    assertEquals (0, dup.get (0, vars[0]));
    assertEquals (1, dup.get (0, vars[1]));
  }

  public void testSetValueExpand ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 0, 0 });

    Variable v3 = new Variable (2);
    assn.setValue (v3, 1);

    assertEquals (3, assn.size ());
    assertEquals (0, assn.get (vars[0]));
    assertEquals (0, assn.get (vars[1]));
    assertEquals (1, assn.get (v3));
  }

  public void testAsTable ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.addRow (vars, new int[] { 1, 0 });
    assn.addRow (vars, new int[] { 1, 0 });

    AbstractTableFactor tbl = assn.asTable ();
    TableFactor exp = new TableFactor (vars, new double[] { 0, 0, 2, 1 });
    assertTrue (exp.almostEquals (tbl));
  }

  public void testAddRowMixed ()
  {
    Assignment assn = new Assignment ();
    assn.addRow (vars, new int[] { 1, 1 });
    assn.addRow (vars, new int[] { 1, 0 });

    Assignment assn2 = new Assignment ();
    assn2.addRow (new Variable[] { vars[1], vars[0] }, new int[] { 0, 1 });

    assn.addRow (assn2);

    AbstractTableFactor tbl = assn.asTable ();
    TableFactor exp = new TableFactor (vars, new double[] { 0, 0, 2, 1 });
    assertTrue (exp.almostEquals (tbl));
  }

  public static Test suite()
  {
    return new TestSuite (TestAssignment.class);
  }


  public static void main(String[] args) throws Exception
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest(new TestAssignment (args[i]));
      }
    } else {
      theSuite = (TestSuite) TestAssignment.suite ();
    }

    junit.textui.TestRunner.run(theSuite);
  }

}
