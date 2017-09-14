/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe;

import java.util.Iterator;

/**
 * Java implementation of Jonathan Wood's "Text Parsing Helper Class".
 *
 * @see <a href="http://www.blackbeltcoder.com/Articles/strings/a-text-parsing-helper-class">Text
 *      Parsing Helper Class</a>
 */
final public class StringIterator implements Iterator<Character> {

  private String text_ = null;
  private int position_ = 0;

  public StringIterator(String text) {
    reset(text);
  }

  @Override
  public boolean hasNext() {
    return !isEndOfText();
  }

  @Override
  public Character next() {
    char c = peek();
    moveAhead();
    return c;
  }

  public String string() {
    return text_;
  }

  public int position() {
    return position_;
  }

  public int remaining() {
    return text_.length() - position_;
  }

  /**
   * Sets the current document and resets the current position to the start of it.
   *
   * @param text
   */
  public void reset(String text) {

    if (text == null) {
      throw new IllegalArgumentException("StringIterator expects a non-null String.");
    }

    text_ = text;
    position_ = 0;
  }

  /**
   * Indicates if the current position is at the end of the current document.
   *
   * @return
   */
  public boolean isEndOfText() {
    return position_ >= text_.length();
  }

  /**
   * Returns the character beyond the current position, or a null character if the specified
   * position is at the end of the document.
   *
   * @return The character at the current position.
   */
  public char peek() {
    return peek(0);
  }

  /**
   * Returns the character at the specified number of characters beyond the current position, or a
   * null character if the specified position is at the end of the document.
   *
   * @param ahead The number of characters beyond the current position.
   * @return The character at the current position.
   */
  public char peek(int ahead) {

    // Preconditions.checkArgument(ahead >= 0);

    int pos = position_ + ahead;
    if (pos < text_.length()) {
      return text_.charAt(pos);
    }
    return 0;
  }

  /**
   * Extracts a substring from the specified range of the current text.
   *
   * @param start
   * @return
   */
  public String extract(int start) {
    return extract(start, text_.length());
  }

  /**
   * Extracts a substring from the specified range of the current text.
   *
   * @param start
   * @param end
   * @return
   */
  public String extract(int start, int end) {

    // Preconditions.checkArgument(start >= 0 && start <= text_.length());
    // Preconditions.checkArgument(end >= 0 && end <= text_.length() && end >= start);

    return text_.substring(start, end);
  }

  /**
   * Moves the current position ahead of one character.
   */
  public void moveAhead() {
    moveAhead(1);
  }

  /**
   * Moves the current position ahead the specified number of characters.
   *
   * @param ahead The number of characters to move ahead.
   */
  public void moveAhead(int ahead) {

    // Preconditions.checkArgument(ahead >= 0);

    position_ = Math.min(position_ + ahead, text_.length());
  }

  /**
   * Moves to the next occurrence of the specified string.
   *
   * @param s String to find.
   */
  public void moveTo(String s) {

    // Preconditions.checkNotNull(s);

    position_ = text_.indexOf(s, position_);
    if (position_ < 0) {
      position_ = text_.length();
    }
  }

  /**
   * Moves to the next occurrence of the specified character.
   *
   * @param c Character to find.
   */
  public void moveTo(char c) {
    position_ = text_.indexOf(c, position_);
    if (position_ < 0) {
      position_ = text_.length();
    }
  }

  /**
   * Moves to the next occurrence of any one of the specified.
   *
   * @param chars Array of characters to find.
   */
  public void moveTo(char[] chars) {

    // Preconditions.checkNotNull(chars);

    while (!isInArray(peek(), chars) && !isEndOfText()) {
      moveAhead();
    }
  }

  /**
   * Moves to the next occurrence of any character that is not one of the specified characters.
   *
   * @param chars Array of characters to move past.
   */
  public void movePast(char[] chars) {

    // Preconditions.checkNotNull(chars);

    while (isInArray(peek(), chars) && !isEndOfText()) {
      moveAhead();
    }
  }

  /**
   * Moves the current position to the first character that is part of a newline.
   */
  public void moveToEndOfLine() {

    char c = peek();
    while (c != '\r' && c != '\n' && !isEndOfText()) {
      moveAhead();
      c = peek();
    }
  }

  /**
   * Moves the current position to the next character that is a whitespace.
   */
  public void moveToWhitespace() {
    while (!Character.isWhitespace(peek()) && !isEndOfText()) {
      moveAhead();
    }
  }

  /**
   * Moves the current position to the next character that is not whitespace.
   */
  public void movePastWhitespace() {
    while (Character.isWhitespace(peek()) && !isEndOfText()) {
      moveAhead();
    }
  }

  /**
   * Determines if the specified character exists in the specified character array.
   *
   * @param c Character to find.
   * @param chars Character array to search.
   * @return
   */
  private boolean isInArray(char c, char[] chars) {

    // Preconditions.checkNotNull(chars);

    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == c) {
        return true;
      }
    }
    return false;
  }
}
