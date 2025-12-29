/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe;

import static org.junit.Assert.*;

import org.junit.Test;

import cc.mallet.pipe.StringIterator;

public class TestStringIterator {

  @Test
  public void testNullStringConstructor() {

    try {
      StringIterator iterator = new StringIterator((String) null);
      assertTrue(false);
    } catch (NullPointerException e) {
      assertTrue(true);
    }
  }

  @Test
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

  @Test
  public void testStringConstructor() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    assertEquals(string, iterator.string());
    assertEquals(0, iterator.position());
    assertEquals(string.length(), iterator.remaining());
    assertTrue(iterator.hasNext());
    assertFalse(iterator.isEndOfText());
  }

  @Test
  public void testPeek() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    assertEquals('t', iterator.peek());
    assertEquals('t', iterator.peek(0));
    assertEquals('!', iterator.peek(string.length() - 1));
    assertEquals(0, iterator.peek(string.length()));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testMoveToChar() {

    String string = "this is a <a href=\"https://www.google.com\">hyperlink</a>!";
    StringIterator iterator = new StringIterator(string);

    iterator.moveTo('<');
    assertEquals('<', iterator.peek());
    assertEquals(10, iterator.position());
    assertEquals(47, iterator.remaining());
  }

  @Test
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

  @Test
  public void testMoveToEndOfLine() {

    String string = "this is a <a href=\"https://www.google.com\">\nhyperlink\n</a>!";
    StringIterator iterator = new StringIterator(string);

    iterator.moveToEndOfLine();
    iterator.movePastWhitespace();
    assertEquals('h', iterator.peek());
    assertEquals(44, iterator.position());
    assertEquals(15, iterator.remaining());
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testIsBlank() {

    assertTrue(StringIterator.isBlank(" \n\r\f\t "));
    assertFalse(StringIterator.isBlank(" nrft "));
  }

  @Test
  public void testTrimLeft() {

    assertEquals("Capitalized", StringIterator.trimLeft(" \n Capitalized"));
    assertEquals("Capitalized", StringIterator.trimLeft(" \r Capitalized"));

    assertEquals("Capitalized \n ", StringIterator.trimLeft("Capitalized \n "));
    assertEquals("Capitalized \r ", StringIterator.trimLeft("Capitalized \r "));
  }

  @Test
  public void testTrimRight() {

    assertEquals(" \n Capitalized", StringIterator.trimRight(" \n Capitalized"));
    assertEquals(" \r Capitalized", StringIterator.trimRight(" \r Capitalized"));

    assertEquals("Capitalized", StringIterator.trimRight("Capitalized \n "));
    assertEquals("Capitalized", StringIterator.trimRight("Capitalized \r "));
  }
}
