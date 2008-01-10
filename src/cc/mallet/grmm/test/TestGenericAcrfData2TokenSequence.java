package cc.mallet.grmm.test;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

import cc.mallet.extract.StringTokenization;
import cc.mallet.grmm.learning.GenericAcrfData2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.*;
import cc.mallet.types.tests.TestSerializable;


/**
 * Created: Sep 15, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestGenericAcrfData2TokenSequence.java,v 1.1 2007/10/22 21:37:41 mccallum Exp $
 */
public class TestGenericAcrfData2TokenSequence extends TestCase {

  String sampleData = "LBLA LBLC ---- f1 f5 f7\n" +
          "LBLB LBLC ---- f5 f6\n" +
          "LBLB LBLD ----\n" +
          "LBLA LBLD ---- f2 f1\n";

  String sampleData2 = "LBLB LBLD ---- f1 f5 f7\n" +
          "LBLA LBLC ---- f5 f6\n" +
          "LBLA LBLC ----\n" +
          "LBLB LBLD ---- f2 f1\n";

  String sampleFixedData = "LBLA LBLC f1 f5 f7\n" +
          "LBLB LBLC f5 f6\n" +
          "LBLB LBLD\n" +
          "LBLA LBLD f2 f1\n";

  String sampleFixedData2 = "LBLB LBLD f1 f5 f7\n" +
          "LBLA LBLC f5 f6\n" +
          "LBLA LBLC\n" +
          "LBLB LBLD f2 f1\n";

  String labelsAtEndData = "f1 f5 f7 LBLB LBLD\n" +
          "f5 f6 LBLA LBLC\n" +
          "LBLA LBLC\n" +
          "f2 f1 LBLB LBLD\n";


  public TestGenericAcrfData2TokenSequence (String name)
  {
    super (name);
  }

  public void testFromSerialization () throws IOException, ClassNotFoundException
  {
    Pipe p = new GenericAcrfData2TokenSequence ();
    InstanceList training = new InstanceList (p);
    training.add (new LineGroupIterator (new StringReader (sampleData), Pattern.compile ("^$"), true));

    Pipe p2 = (Pipe) TestSerializable.cloneViaSerialization (p);

    InstanceList l1 = new InstanceList (p);
    l1.add (new LineGroupIterator (new StringReader (sampleData2), Pattern.compile ("^$"), true));
    InstanceList l2 = new InstanceList (p2);
    l2.add (new LineGroupIterator (new StringReader (sampleData2), Pattern.compile ("^$"), true));

    // the readResolve alphabet thing doesn't kick in on first deserialization
    assertTrue (p.getTargetAlphabet () != p2.getTargetAlphabet ());

    assertEquals (1, l1.size ());
    assertEquals (1, l2.size ());

    Instance inst1 = l1.get (0);
    Instance inst2 = l2.get (0);

    LabelsSequence ls1 = (LabelsSequence) inst1.getTarget ();
    LabelsSequence ls2 = (LabelsSequence) inst2.getTarget ();

    assertEquals (4, ls1.size ());
    assertEquals (4, ls2.size ());

    for (int i = 0; i < 4; i++) {
      assertEquals (ls1.get (i).toString (), ls2.get (i).toString ());
    }
  }

  public void testFixedNumLabels () throws IOException, ClassNotFoundException
  {
    Pipe p = new GenericAcrfData2TokenSequence (2);
    InstanceList training = new InstanceList (p);
    training.add (new LineGroupIterator (new StringReader (sampleFixedData), Pattern.compile ("^$"), true));

    assertEquals (1, training.size ());

    Instance inst1 = training.get (0);
    LabelsSequence ls1 = (LabelsSequence) inst1.getTarget ();

    assertEquals (4, ls1.size ());
  }

  public void testLabelsAtEnd () throws IOException, ClassNotFoundException
  {
    GenericAcrfData2TokenSequence p = new GenericAcrfData2TokenSequence (2);
    p.setLabelsAtEnd (true);

    InstanceList training = new InstanceList (p);
    training.add (new LineGroupIterator (new StringReader (labelsAtEndData), Pattern.compile ("^$"), true));

    assertEquals (1, training.size ());

    Instance inst1 = training.get (0);
    StringTokenization toks = (StringTokenization) inst1.getData ();
    LabelsSequence ls1 = (LabelsSequence) inst1.getTarget ();

    assertEquals (4, ls1.size ());
    assertEquals (3, toks.getToken (0).getFeatures ().size ());
    assertEquals ("LBLB LBLD", ls1.getLabels (0).toString ());

    LabelAlphabet globalDict = p.getLabelAlphabet (0);
    assertEquals (2, p.numLevels ());
    assertEquals (globalDict, ls1.getLabels (0).get (0).getLabelAlphabet ());
  }

  public void testNoTokenText ()
  {
    GenericAcrfData2TokenSequence p = new GenericAcrfData2TokenSequence (2);
    p.setFeaturesIncludeToken(false);
    p.setIncludeTokenText(false);

    InstanceList training = new InstanceList (p);
    training.add (new LineGroupIterator (new StringReader (sampleFixedData), Pattern.compile ("^$"), true));

    assertEquals (1, training.size ());

    Instance inst1 = training.get (0);

    LabelsSequence ls1 = (LabelsSequence) inst1.getTarget ();
    assertEquals (4, ls1.size ());

    TokenSequence ts1 = (TokenSequence) inst1.getData ();
    assertEquals (3, ts1.getToken(0).getFeatures().size ());
    assertEquals (2, ts1.getToken(1).getFeatures().size ());
  }

  public static Test suite ()
  {
    return new TestSuite (TestGenericAcrfData2TokenSequence.class);
  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestGenericAcrfData2TokenSequence (args[i]));
      }
    } else {
      theSuite = (TestSuite) TestGenericAcrfData2TokenSequence.suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
