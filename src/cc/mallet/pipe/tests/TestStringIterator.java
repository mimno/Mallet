/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe.tests;

import java.util.ArrayList;
import java.util.List;

import cc.mallet.pipe.StringIterator;

import junit.framework.TestCase;

public class TestStringIterator extends TestCase {

  private static String text1() {
    return "In our article, we examine network visualizations as a\n"
        + "means of enhancing the interpretability of probabilistic topic\n"
        + "models for insight discovery. We focus on what is perhaps\n"
        + "the most popular and prevalently used topic model: latent\n"
        + "Dirichlet allocation or LDA (Blei, Ng and Jordan 2003). Topic\n"
        + "modeling algorithms like LDA discover latent themes (i.e.,\n"
        + "topics) in document collections and represent documents as a\n"
        + "combination of these themes. Thus, they are critical tools for\n"
        + "exploring text data across many domains. It is often the case\n"
        + "that users must discover the subject matter buried within large\n"
        + "and unfamiliar document sets (e.g., sensemaking in text data).\n"
        + "Keyword searches are inadequate here, since even to begin\n"
        + "searching is unclear. Topic discovery techniques such as LDA\n"
        + "are a boon to users in such scenarios, because they reveal the\n"
        + "content in an unsupervised and automated fashion. However,\n"
        + "obtaining a “big picture” view of the larger trends in a document\n"
        + "collection from only the raw output of an LDA model can be\n"
        + "challenging. In our article, we investigate, the use of what we\n"
        + "refer to as topic similarity networks to address this challenge.\n"
        + "Topic similarity networks are graphs in which nodes represent\n"
        + "latent topics in text collections, and links represent similarity\n"
        + "among topics. We described efficient and effective methods to\n"
        + "both building and labeling such networks.";
  }

  private static String text2() {
    return "Note de R10 rédigée le 25/10/2000\n" + "\n" + "\n"
        + "Objet : Observation sur l’individu Christian Dumont\n" + "\n"
        + "Il a été constaté par R10 que Christian Dumont a rencontré les individus suivants :\n"
        + "\n" + "-\t Nathalie Guerin\n" + "\n" + "-\t Christine Morel\n" + "\n" + "\n"
        + "Le rendez-vous a eu lieu le 5/8/2012  à 12:32 dans son habitation situé à Paris.\n"
        + "\n"
        + "Cette rencontre a duré environ 5 h.D’après  notre source il semblerait que le sujet de la réunion était la préparation d'une recette de cuisine.\n";
  }

  private static String text3() {
    return "Compatibility of systems of linear constraints over the set of natural numbers\n\n"
        + "Criteria of compatibility of a system of linear Diophantine equations, strict "
        + "inequations,\n"
        + "and nonstrict inequations are considered. Upper bounds for components of a minimal set\n"
        + "of solutions and algorithms of construction of minimal generating sets of solutions for "
        + "all\n"
        + "types of systems are given. These criteria and the corresponding algorithms for\n"
        + "constructing a minimal supporting set of solutions can be used in solving all the\n"
        + "considered types of systems and systems of mixed types.";
  }

  public void testNullStringConstructor() {

    try {
      StringIterator iterator = new StringIterator((String) null);
      assertTrue(false);
    } catch (NullPointerException e) {
      assertTrue(true);
    }
  }

  public void testEmptyStringConstructor() {

    String string = "";
    StringIterator iterator = new StringIterator(string);

    assertEquals(string, iterator.string());
    assertEquals(0, iterator.position());
    assertEquals(0, iterator.remaining());
    assertEquals(0, iterator.peek());
    assertEquals((char) 0, (char) iterator.next());
    assertEquals(string, iterator.extract(0));
    assertEquals(string, iterator.extract(0, string.length()));
    assertFalse(iterator.hasNext());
    assertTrue(iterator.isEndOfText());
  }

  public void testStringConstructor() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    assertEquals(string, iterator.string());
    assertEquals(0, iterator.position());
    assertEquals(string.length(), iterator.remaining());
    assertTrue(iterator.hasNext());
    assertFalse(iterator.isEndOfText());
  }

  public void testPeek() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    assertEquals('t', iterator.peek());
    assertEquals('t', iterator.peek(0));
    assertEquals('!', iterator.peek(string.length() - 1));
    assertEquals(0, iterator.peek(string.length()));
  }

  public void testExtract() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    assertEquals(string, iterator.extract(0));
    assertEquals(string, iterator.extract(0, string.length()));
    assertEquals("this", iterator.extract(0, 4).toString());
    assertEquals(" ", iterator.extract(4, 5).toString());
    assertEquals("\"", iterator.extract(18, 19).toString());
    assertEquals("https://www.google.com", iterator.extract(19, 41).toString());
  }

  public void testMoveAhead() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    // Next
    assertEquals('t', iterator.peek());
    assertEquals('h', iterator.peek(1));
    assertEquals(57, iterator.remaining());
    assertFalse(iterator.isEndOfText());

    // Next
    iterator.moveAhead();
    assertEquals('h', iterator.peek());
    assertEquals('i', iterator.peek(1));
    assertEquals(56, iterator.remaining());
    assertFalse(iterator.isEndOfText());

    // Move to last char
    iterator.moveAhead(string.length() - 2);
    assertEquals('!', iterator.peek());
    assertEquals(0, iterator.peek(1));
    assertEquals(1, iterator.remaining());
    assertFalse(iterator.isEndOfText());

    // Move past the last char
    iterator.moveAhead();
    assertEquals(0, iterator.peek());
    assertEquals(0, iterator.peek(1));
    assertEquals(0, iterator.remaining());
    assertTrue(iterator.isEndOfText());
  }

  public void testMoveToString() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    // Forward search
    iterator.moveTo("https");
    assertEquals('h', iterator.peek());
    assertEquals(19, iterator.position());
    assertEquals(38, iterator.remaining());

    // Stay still
    iterator.moveTo("");
    assertEquals('h', iterator.peek());
    assertEquals(19, iterator.position());
    assertEquals(38, iterator.remaining());

    // Backward search
    iterator.moveTo("this");
    assertEquals(0, iterator.peek());
    assertEquals(57, iterator.position());
    assertEquals(0, iterator.remaining());
  }

  public void testMoveToChar() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    iterator.moveTo('<');
    assertEquals('<', iterator.peek());
    assertEquals(10, iterator.position());
    assertEquals(47, iterator.remaining());
  }

  public void testMoveToOneChar() {

    char[] chars = new char[] {'<', '>'};
    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    iterator.moveTo(chars);
    assertEquals('<', iterator.peek());
    assertEquals(10, iterator.position());

    iterator.moveAhead();
    int begin = iterator.position();

    iterator.moveTo(chars);
    int end = iterator.position();
    assertEquals('>', iterator.peek());
    assertEquals(42, end);

    assertEquals("a href=\"https://www.google.com\"", iterator.extract(begin, end).toString());
  }

  public void testMoveToEndOfLine() {

    String string = "this is a <a href=\"https://www.google.com\">\nhyperlink\n</a>!";
    StringIterator iterator = new StringIterator(string);

    iterator.moveToEndOfLine();
    iterator.movePastWhitespace();
    assertEquals('h', iterator.peek());
    assertEquals(44, iterator.position());
    assertEquals(15, iterator.remaining());
  }

  public void testMovePast() {

    char[] chars = new char[] {'<', '>'};
    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    iterator.moveTo(chars);
    assertEquals('<', iterator.peek());
    assertEquals(10, iterator.position());

    iterator.movePast(chars);
    int begin = iterator.position();

    iterator.moveTo(chars);
    int end = iterator.position();
    assertEquals('>', iterator.peek());
    assertEquals(42, end);

    assertEquals("a href=\"https://www.google.com\"", iterator.extract(begin, end).toString());
  }

  public void testMovePastWhitespace() {

    String string = "this is a <a href=\"https://www.google.com\">\nhyperlink\n</a>!";
    StringIterator iterator = new StringIterator(string);

    // Move past ' '
    iterator.moveTo('a');
    iterator.moveAhead();
    iterator.movePastWhitespace();
    assertEquals('<', iterator.peek());
    assertEquals(10, iterator.position());
    assertEquals(49, iterator.remaining());

    // Move past '\n'
    iterator.moveTo('>');
    iterator.moveAhead();
    iterator.movePastWhitespace();
    assertEquals('h', iterator.peek());
    assertEquals(44, iterator.position());
    assertEquals(15, iterator.remaining());
  }

  public void testNextSentence1() {

    List<String> sentences = new ArrayList<>();
    StringIterator iterator = new StringIterator(text1());

    while (iterator.hasNextSentence()) {

      String sentence = iterator.nextSentence();
      if (sentence != null && !sentence.isEmpty()) {
        sentences.add(sentence);
      }
    }

    assertEquals(12, sentences.size());
    assertEquals(
        "In our article, we examine network visualizations as a means of enhancing the interpretability of probabilistic topic models for insight discovery.",
        sentences.get(0));
    assertEquals("We focus on what is perhaps the most popular and prevalently used topic model:",
        sentences.get(1));
    assertEquals("latent Dirichlet allocation or LDA .", sentences.get(2));
    assertEquals(
        "Topic modeling algorithms like LDA discover latent themes in document collections and represent documents as a combination of these themes.",
        sentences.get(3));
    assertEquals("Thus, they are critical tools for exploring text data across many domains.",
        sentences.get(4));
    assertEquals(
        "It is often the case that users must discover the subject matter buried within large and unfamiliar document sets .",
        sentences.get(5));
    assertEquals("Keyword searches are inadequate here, since even to begin searching is unclear.",
        sentences.get(6));
    assertEquals(
        "Topic discovery techniques such as LDA are a boon to users in such scenarios, because they reveal the content in an unsupervised and automated fashion.",
        sentences.get(7));
    assertEquals(
        "However, obtaining a “big picture” view of the larger trends in a document collection from only the raw output of an LDA model can be challenging.",
        sentences.get(8));
    assertEquals(
        "In our article, we investigate, the use of what we refer to as topic similarity networks to address this challenge.",
        sentences.get(9));
    assertEquals(
        "Topic similarity networks are graphs in which nodes represent latent topics in text collections, and links represent similarity among topics.",
        sentences.get(10));
    assertEquals(
        "We described efficient and effective methods to both building and labeling such networks.",
        sentences.get(11));
  }

  public void testNextSentence2() {

    List<String> sentences = new ArrayList<>();
    StringIterator iterator = new StringIterator(text2());

    while (iterator.hasNextSentence()) {

      String sentence = iterator.nextSentence();
      if (sentence != null && !sentence.isEmpty()) {
        sentences.add(sentence);
      }
    }

    assertEquals(9, sentences.size());
    assertEquals("Note de R10 rédigée le 25/10/2000", sentences.get(0));
    assertEquals("Objet :", sentences.get(1));
    assertEquals("Observation sur l’individu Christian Dumont", sentences.get(2));
    assertEquals(
        "Il a été constaté par R10 que Christian Dumont a rencontré les individus suivants :",
        sentences.get(3));
    assertEquals("- Nathalie Guerin", sentences.get(4));
    assertEquals("- Christine Morel", sentences.get(5));
    assertEquals("Le rendez-vous a eu lieu le 5/8/2012 à 12:32 dans son habitation situé à Paris.",
        sentences.get(6));
    assertEquals("Cette rencontre a duré environ 5 h.", sentences.get(7));
    assertEquals(
        "D’après notre source il semblerait que le sujet de la réunion était la préparation d'une recette de cuisine.",
        sentences.get(8));
  }

  public void testNextParagraph1() {

    List<String> paragraphs = new ArrayList<>();
    StringIterator iterator = new StringIterator(text1());

    while (iterator.hasNextSentence()) {

      String sentence = iterator.nextParagraph();
      if (sentence != null && !sentence.isEmpty()) {
        paragraphs.add(sentence);
      }
    }

    assertEquals(1, paragraphs.size());
    assertEquals(text1(), paragraphs.get(0));
  }

  public void testNextParagraph2() {

    List<String> paragraphs = new ArrayList<>();
    StringIterator iterator = new StringIterator(text2());

    while (iterator.hasNextSentence()) {

      String sentence = iterator.nextParagraph();
      if (sentence != null && !sentence.isEmpty()) {
        paragraphs.add(sentence);
      }
    }

    assertEquals(7, paragraphs.size());
    assertEquals("Note de R10 rédigée le 25/10/2000", paragraphs.get(0));
    assertEquals("Objet : Observation sur l’individu Christian Dumont", paragraphs.get(1));
    assertEquals(
        "Il a été constaté par R10 que Christian Dumont a rencontré les individus suivants :",
        paragraphs.get(2));
    assertEquals("-\t Nathalie Guerin", paragraphs.get(3));
    assertEquals("-\t Christine Morel", paragraphs.get(4));
    assertEquals("Le rendez-vous a eu lieu le 5/8/2012  à 12:32 dans son habitation situé à Paris.",
        paragraphs.get(5));
    assertEquals(
        "Cette rencontre a duré environ 5 h.D’après  notre source il semblerait que le sujet de la réunion était la préparation d'une recette de cuisine.",
        paragraphs.get(6));
  }

  public void testNextParagraph3() {

    List<String> paragraphs = new ArrayList<>();
    StringIterator iterator = new StringIterator(text3());

    while (iterator.hasNextSentence()) {

      String sentence = iterator.nextParagraph();
      if (sentence != null && !sentence.isEmpty()) {
        paragraphs.add(sentence);
      }
    }

    assertEquals(2, paragraphs.size());
    assertEquals("Compatibility of systems of linear constraints over the set of natural numbers",
        paragraphs.get(0));
    assertEquals("Criteria of compatibility of a system of linear Diophantine equations, strict "
        + "inequations,\n"
        + "and nonstrict inequations are considered. Upper bounds for components of a minimal set\n"
        + "of solutions and algorithms of construction of minimal generating sets of solutions for "
        + "all\n"
        + "types of systems are given. These criteria and the corresponding algorithms for\n"
        + "constructing a minimal supporting set of solutions can be used in solving all the\n"
        + "considered types of systems and systems of mixed types.", paragraphs.get(1));
  }
}
