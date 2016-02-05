/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract.test;

import junit.framework.*;

import java.util.regex.Pattern;

import cc.mallet.extract.*;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.util.CharSequenceLexer;

/**
 * Created: Oct 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestDocumentExtraction.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class TestDocumentExtraction extends TestCase {

  public TestDocumentExtraction (String name)
  {
    super (name);
  }


  public static Test suite ()
  {
    return new TestSuite (TestDocumentExtraction.class);
  }


  public void testToXml () {
    LabelAlphabet dict = new LabelAlphabet ();
    String document = "the quick brown fox leapt over the lazy dog";
    StringTokenization toks = new StringTokenization (document, new CharSequenceLexer ());

    Label O = dict.lookupLabel ("O");
    Label ANML = dict.lookupLabel ("ANIMAL");
    Label VB = dict.lookupLabel ("VERB");
    LabelSequence tags = new LabelSequence (new Label[] { O, ANML, ANML, ANML, VB, O, O, ANML, ANML });

    DocumentExtraction extr = new DocumentExtraction ("Test", dict, toks, tags, "O");
    String actualXml = extr.toXmlString();
    String expectedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<doc>the <ANIMAL>quick brown fox </ANIMAL><VERB>leapt </VERB>over the <ANIMAL>lazy dog</ANIMAL></doc>\r\n";
    assertEquals (expectedXml, actualXml);
  }

   public void testToXmlBIO () {
    LabelAlphabet dict = new LabelAlphabet ();
    String document = "the quick brown fox leapt over the lazy dog";
    StringTokenization toks = new StringTokenization (document, new CharSequenceLexer ());

    Label O = dict.lookupLabel ("O");
    Label BANML = dict.lookupLabel ("B-ANIMAL");
    Label ANML = dict.lookupLabel ("ANIMAL");
    Label BVB = dict.lookupLabel ("B-VERB");
    Label VB = dict.lookupLabel ("I-VERB");
    LabelSequence tags = new LabelSequence (new Label[] { O, BANML, ANML, BANML, BVB, VB, O, ANML, ANML });

    DocumentExtraction extr = new DocumentExtraction ("Test", dict, toks, tags, null, "O", new BIOTokenizationFilter());
    String actualXml = extr.toXmlString();
    String expectedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<doc>the <ANIMAL>quick brown </ANIMAL><ANIMAL>fox </ANIMAL><VERB>leapt over </VERB>the <ANIMAL>lazy dog</ANIMAL></doc>\r\n";
    assertEquals (expectedXml, actualXml);
  }

  public void testNestedToXML ()
  {
    LabelAlphabet dict = new LabelAlphabet ();
    String document = "the quick brown fox leapt over the lazy dog";
    StringTokenization toks = new StringTokenization (document, new CharSequenceLexer ());

    Label O = dict.lookupLabel ("O");
    Label ANML = dict.lookupLabel ("ANIMAL");
    Label VB = dict.lookupLabel ("VERB");
    Label JJ = dict.lookupLabel ("ADJ");
    Label MAMMAL = dict.lookupLabel ("MAMMAL");

    LabelSequence tags = new LabelSequence (new Label[] { O, ANML, ANML, ANML, VB, O, ANML, ANML, ANML });

    LabeledSpans spans = new DefaultTokenizationFilter ().constructLabeledSpans (dict, document, O, toks, tags);

    Span foxToken = toks.subspan (3, 4);
    spans.add (new LabeledSpan (foxToken, MAMMAL, false));
    Span bigDogToken = toks.subspan (7, 8);
    spans.add (new LabeledSpan (bigDogToken, JJ, false));

    DocumentExtraction extr = new DocumentExtraction ("Test", dict, toks, spans, null, "O");
    String actualXml = extr.toXmlString();
    String expectedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<doc>the <ANIMAL>quick brown <MAMMAL>fox </MAMMAL></ANIMAL><VERB>leapt </VERB>over <ANIMAL>the <ADJ>lazy </ADJ>dog</ANIMAL></doc>\r\n";
    assertEquals (expectedXml, actualXml);

  }

  public void testNestedXMLTokenizationFilter ()
  {
    LabelAlphabet dict = new LabelAlphabet ();
    String document = "the quick brown fox leapt over the lazy dog";
    StringTokenization toks = new StringTokenization (document, new CharSequenceLexer ());

    Label O = dict.lookupLabel ("O");
    Label ANML = dict.lookupLabel ("ANIMAL");
    Label ANML_MAMM = dict.lookupLabel ("ANIMAL|MAMMAL");
    Label VB = dict.lookupLabel ("VERB");
    Label ANML_JJ = dict.lookupLabel ("ANIMAL|ADJ");
    Label ANML_JJ_MAMM = dict.lookupLabel ("ANIMAL|ADJ|MAMMAL");

    LabelSequence tags = new LabelSequence (new Label[] { O, ANML, ANML, ANML_MAMM, VB, O, ANML, ANML_JJ, ANML_JJ_MAMM });
    DocumentExtraction extr = new DocumentExtraction ("Test", dict, toks, tags, null, "O", new HierarchicalTokenizationFilter ());

    String actualXml = extr.toXmlString();
    String expectedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<doc>the <ANIMAL>quick brown <MAMMAL>fox </MAMMAL></ANIMAL><VERB>leapt </VERB>over <ANIMAL>the <ADJ>lazy <MAMMAL>dog</MAMMAL></ADJ></ANIMAL></doc>\r\n";
    assertEquals (expectedXml, actualXml);

    // Test the ignore function

    extr = new DocumentExtraction ("Test", dict, toks, tags, null, "O", new HierarchicalTokenizationFilter (Pattern.compile ("AD.*")));

    actualXml = extr.toXmlString();
    expectedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            "<doc>the <ANIMAL>quick brown <MAMMAL>fox </MAMMAL></ANIMAL><VERB>leapt </VERB>over <ANIMAL>the lazy <MAMMAL>dog</MAMMAL></ANIMAL></doc>\r\n";
    assertEquals (expectedXml, actualXml);



  }

  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestDocumentExtraction (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
