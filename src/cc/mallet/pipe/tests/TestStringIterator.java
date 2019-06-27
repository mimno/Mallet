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
    assertEquals("this", iterator.extract(0, 4));
    assertEquals(" ", iterator.extract(4, 5));
    assertEquals("\"", iterator.extract(18, 19));
    assertEquals("https://www.google.com", iterator.extract(19, 41));
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

  public void testBeginsWithWhitespaces() {

    String textIn = "\n\n\nvéhicule de - de 3,5t";
    String textOut = "véhicule de - de 3,5t";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testEndsWithWhitespaces() {

    String textIn = "véhicule de - de 3,5t\n\n\n";
    String textOut = "véhicule de - de 3,5t";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testNumericBulletPoints() {

    String textIn = "Ma liste:\n1) première entrée ;\n2) dernière entrée.";
    String textOut = "Ma liste:\n1) première entrée ;\n2) dernière entrée.";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testHyphenBulletPoints() {

    String textIn = "Ma liste:\n- première entrée ;\n- dernière entrée.";
    String textOut = "Ma liste:\n- première entrée ;\n- dernière entrée.";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testLinebreakInsideSentence() {

    String textIn = "Ceci est un\nretour à la ligne.";
    String textOut = "Ceci est un retour à la ligne.";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testHyphenInsideSentence() {

    String textIn = "Ceci est un re-\ntour à la ligne.";
    String textOut = "Ceci est un re- tour à la ligne.";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testIdEst() {

    String textIn = "i.e.\ntopics";
    String textOut = "i.e. topics";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testExempliGratia() {

    String textIn = "e.g. topics";
    String textOut = "e.g. topics";

    assertEquals(textOut, StringIterator.clean(textIn));
  }

  public void testFormula() {

    String text =
        "Prime HT de l'année N = (indice N / indice N-1) x somme des primes par véhicule.";
    List<String> sentences = StringIterator.sentences(text, false);

    assertEquals(text, sentences.get(0));
  }

  public void testIsLowerCase() {

    assertTrue(StringIterator.isLowerCase("lowercase"));
    assertFalse(StringIterator.isLowerCase("CamelCase"));
    assertFalse(StringIterator.isLowerCase("UPPERCASE"));

    assertTrue(StringIterator.isLowerCase(" \n\r lowercase"));
    assertTrue(StringIterator.isLowerCase("lowercase \n\r "));

    assertFalse(StringIterator.isLowerCase(" \n\r CamelCase"));
    assertFalse(StringIterator.isLowerCase("CamelCase \n\r "));

    assertFalse(StringIterator.isLowerCase(" \n\r UPPERCASE"));
    assertFalse(StringIterator.isLowerCase("UPPERCASE \n\r "));
  }

  public void testIsUpperCase() {

    assertTrue(StringIterator.isUpperCase("UPPERCASE"));
    assertFalse(StringIterator.isUpperCase("CamelCase"));
    assertFalse(StringIterator.isUpperCase("lowercase"));

    assertTrue(StringIterator.isUpperCase(" \n\r UPPERCASE"));
    assertTrue(StringIterator.isUpperCase("UPPERCASE \n\r "));

    assertFalse(StringIterator.isUpperCase(" \n\r CamelCase"));
    assertFalse(StringIterator.isUpperCase("CamelCase \n\r "));

    assertFalse(StringIterator.isUpperCase(" \n\r lowercase"));
    assertFalse(StringIterator.isUpperCase("lowercase \n\r "));
  }

  public void testIsCapitalized() {

    assertTrue(StringIterator.isCapitalized("Capitalized"));
    assertFalse(StringIterator.isCapitalized("UPPERCASE"));
    assertFalse(StringIterator.isCapitalized("lowercase"));
    assertFalse(StringIterator.isCapitalized("CamelCase"));

    assertTrue(StringIterator.isCapitalized(" \n\r Capitalized"));
    assertTrue(StringIterator.isCapitalized("Capitalized \n\r "));

    assertFalse(StringIterator.isCapitalized(" \n\r UPPERCASE"));
    assertFalse(StringIterator.isCapitalized("UPPERCASE \n\r "));

    assertFalse(StringIterator.isCapitalized(" \n\r lowercase"));
    assertFalse(StringIterator.isCapitalized("lowercase \n\r "));

    assertFalse(StringIterator.isCapitalized(" \n\r CamelCase"));
    assertFalse(StringIterator.isCapitalized("CamelCase \n\r "));
  }

  public void testIsBlank() {

    assertTrue(StringIterator.isBlank(" \n\r\f\t "));
    assertFalse(StringIterator.isBlank(" nrft "));
  }

  public void testTrimLeft() {

    assertEquals("Capitalized", StringIterator.trimLeft(" \n Capitalized"));
    assertEquals("Capitalized", StringIterator.trimLeft(" \r Capitalized"));

    assertEquals("Capitalized \n ", StringIterator.trimLeft("Capitalized \n "));
    assertEquals("Capitalized \r ", StringIterator.trimLeft("Capitalized \r "));
  }

  public void testTrimRight() {

    assertEquals(" \n Capitalized", StringIterator.trimRight(" \n Capitalized"));
    assertEquals(" \r Capitalized", StringIterator.trimRight(" \r Capitalized"));

    assertEquals("Capitalized", StringIterator.trimRight("Capitalized \n "));
    assertEquals("Capitalized", StringIterator.trimRight("Capitalized \r "));
  }

  public void testUpperCaseSectionHeader() {

    List<String> strings = new ArrayList<>();
    strings.add("CONDUITE EN ETAT D'EBRIETE OU SOUS L'EMPRISE DE STUPEFIANTS");
    strings.add("Les garanties du contrat, y compris de dommages, restent acquises à l'assuré");
    strings.add("lorsqu'un sinistre intervient alors que le conducteur,");
    strings.add("employé de l'assuré, conduit, à l'insu de l'assuré,");
    strings.add("en état d'ébriété ou sous l'emprise de stupéfiants.");

    String text = join(strings);
    List<String> sentences = StringIterator.sentences(text, false);

    assertEquals("CONDUITE EN ETAT D'EBRIETE OU SOUS L'EMPRISE DE STUPEFIANTS", sentences.get(0));
    assertEquals(
        "Les garanties du contrat, y compris de dommages, restent acquises à l'assuré lorsqu'un sinistre intervient alors que le conducteur, employé de l'assuré, conduit, à l'insu de l'assuré, en état d'ébriété ou sous l'emprise de stupéfiants.",
        sentences.get(1));
  }

  public void testSimpleText() {

    List<String> strings = new ArrayList<>();
    strings.add("Note de R10 rédigée le 25/10/2000");
    strings.add("Objet : Observation sur l'individu Christian Dumont");
    strings
        .add("Il a été constaté par R10 que Christian Dumont a rencontré les individus suivants :");
    strings.add("- Nathalie Guerin");
    strings.add("- Christine Morel");
    strings.add(
        "Le rendez-vous a eu lieu le 5/8/2012 à 12:32 dans son habitation situé à Paris.\nCette rencontre a duré environ 5 h.\nD'après notre source il semblerait que le sujet de la réunion était la préparation d'une recette de cuisine.");

    String text = join(strings);
    List<String> sentences = StringIterator.sentences(text, false);

    assertEquals("Note de R10 rédigée le 25/10/2000", sentences.get(0));
    assertEquals("Objet : Observation sur l'individu Christian Dumont", sentences.get(1));
    assertEquals(
        "Il a été constaté par R10 que Christian Dumont a rencontré les individus suivants :",
        sentences.get(2));
    assertEquals("- Nathalie Guerin", sentences.get(3));
    assertEquals("- Christine Morel", sentences.get(4));
    assertEquals("Le rendez-vous a eu lieu le 5/8/2012 à 12:32 dans son habitation situé à Paris.",
        sentences.get(5));
    assertEquals("Cette rencontre a duré environ 5 h.", sentences.get(6));
    assertEquals(
        "D'après notre source il semblerait que le sujet de la réunion était la préparation d'une recette de cuisine.",
        sentences.get(7));
  }

  public void testHeader() {

    List<String> strings = new ArrayList<>();
    strings.add("1. Conduite en état d'ébriété ou sous l'emprise de stupéfiants");
    strings.add("Les garanties du contrat, y compris de dommages, restent acquises à l'assuré");
    strings.add("lorsqu'un sinistre intervient alors que le conducteur,");
    strings.add("employé de l'assuré, conduit, à l'insu de l'assuré,");
    strings.add("en état d'ébriété ou sous l'emprise de stupéfiants.");

    String text = join(strings);
    List<String> sentences = StringIterator.sentences(text, false);

    assertEquals("1. Conduite en état d'ébriété ou sous l'emprise de stupéfiants",
        sentences.get(0));
    assertEquals(
        "Les garanties du contrat, y compris de dommages, restent acquises à l'assuré lorsqu'un sinistre intervient alors que le conducteur, employé de l'assuré, conduit, à l'insu de l'assuré, en état d'ébriété ou sous l'emprise de stupéfiants.",
        sentences.get(1));
  }

  /**
   * Join a list of strings. Similar to Guava's
   *
   * <pre>
   * Joiner.on('\n').join(strings)
   * </pre>
   *
   * @return a string.
   */
  private String join(List<String> strings) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < strings.size(); i++) {
      if (i > 0) {
        builder.append('\n');
      }
      builder.append(strings.get(i));
    }
    return builder.toString();
  }
}
