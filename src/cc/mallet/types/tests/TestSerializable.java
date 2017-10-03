/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.types.tests;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import java.io.*;


/**
 * Static utility for testing serializable classes in MALLET.
 * 
 * Created: Aug 24, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestSerializable.java,v 1.1 2007/10/22 21:37:55 mccallum Exp $
 */
public class TestSerializable extends TestCase {

  public TestSerializable (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite(TestSerializable.class);
  }

  /**
   * Clones a given object by serializing it to a byte array and reading it back.
   *  This is useful for testing serialization methods.
   * 
   * @param obj
   * @return A copy of obj.
   * @throws IOException
   * @throws ClassNotFoundException
   */ 
  public static Object cloneViaSerialization (Serializable obj)
          throws IOException, ClassNotFoundException
  {
    ByteArrayOutputStream boas = new ByteArrayOutputStream ();
    ObjectOutputStream oos = new ObjectOutputStream (boas);
    oos.writeObject (obj);

    ByteArrayInputStream bias = new ByteArrayInputStream (boas.toByteArray ());
    ObjectInputStream ois = new ObjectInputStream (bias);
    return ois.readObject ();
  }
  
  private static class WriteMe implements Serializable {
    String foo;
    int bar;

    public boolean equals (Object o)
    {
      if (this == o) return true;
      if (!(o instanceof WriteMe)) return false;

      final WriteMe writeMe = (WriteMe) o;

      if (bar != writeMe.bar) return false;
      if (foo != null ? !foo.equals (writeMe.foo) : writeMe.foo != null) return false;

      return true;
    }

    public int hashCode ()
    {
      int result;
      result = (foo != null ? foo.hashCode () : 0);
      result = 29 * result + bar;
      return result;
    }
  }

  public void testTestSerializable () throws IOException, ClassNotFoundException
  {
    WriteMe w = new WriteMe ();
    w.foo = "hi there";
    w.bar = 1;
    WriteMe w2 = (WriteMe) cloneViaSerialization (w);
    assertTrue (w != w2);
    assertTrue (w.equals (w2));
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestSerializable (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
