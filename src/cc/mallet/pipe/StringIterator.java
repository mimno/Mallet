/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe;

import java.text.Normalizer;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java implementation of Jonathan Wood's "Text Parsing Helper Class".
 *
 * @see <a href="http://www.blackbeltcoder.com/Articles/strings/a-text-parsing-helper-class">Text
 *      Parsing Helper Class</a>
 */
final public class StringIterator implements Iterator<Character> {

  public static final char CR = '\n';
  public static final char LF = '\r';
  public static final char SPACE = ' ';

  private String text_ = null;
  private int position_ = 0;

  public StringIterator(String text) {
    reset(text);
  }

  /**
   * Normalize quotation marks and apostrophes.
   *
   * @param text document.
   * @return A normalized text.
   */
  public static String normalize(String text) {

    // Preconditions.checkArgument(!Strings.isNullOrEmpty(text));

    StringBuilder builder = new StringBuilder(text.length());
    StringIterator iterator = new StringIterator(text);

    while (iterator.hasNext()) {
      char c = iterator.next();
      if (isApostrophe(c)) {
        builder.append('\'');
      } else if (isSingleQuotationMark(c)) {
        builder.append('\'');
      } else if (isDoubleQuotationMark(c)) {
        builder.append('"');
      } else if (c == '\u00a0' /* non-breaking space */) {
        builder.append(SPACE);
      } else {
        if (c != '\r' || iterator.peek() != '\n') { // convert Windows EOL to Unix EOL
          builder.append(c);
        }
      }
    }
    return builder.toString();
  }

  /**
   * Remove whitespace prefix from string.
   *
   * @param s string.
   * @return string without whitespaces at the beginning.
   */
  public static String trimLeft(String s) {

    // Preconditions.checkNotNull(s);

    for (int i = 0; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (!isWhitespace(c)) {
        return i == 0 ? s : s.substring(i);
      }
    }
    return "";
  }

  /**
   * Remove whitespace suffix from string.
   *
   * @param s string.
   * @return string without whitespaces at the end.
   */
  public static String trimRight(String s) {

    // Preconditions.checkNotNull(s);

    for (int i = s.length() - 1; i >= 0; i--) {
      int c = s.codePointAt(i);
      if (!isWhitespace(c)) {
        return i == s.length() - 1 ? s : s.substring(0, i + 1);
      }
    }
    return "";
  }

  /**
   * Check if a string is blank.
   *
   * @param s string.
   * @return true iif s is only made of whitespace characters.
   */
  public static boolean isBlank(String s) {

    // Preconditions.checkNotNull(s);

    for (int i = 0; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (!isWhitespace(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if a string is capitalized.
   *
   * @param s string.
   * @return true iif s starts with an upper case character and all other characters are lower case.
   */
  public static boolean isCapitalized(String s) {

    // Preconditions.checkNotNull(s);

    boolean isFirst = true;

    for (int i = 0; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (isFirst) {
        if (c != Character.toUpperCase(c)) {
          return false;
        }
        isFirst = isWhitespace(c);
      } else {
        if (c != Character.toLowerCase(c)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Check if a string is upper case.
   *
   * @param s string.
   * @return true iif s is only made of upper case characters.
   */
  public static boolean isUpperCase(String s) {

    // Preconditions.checkNotNull(s);

    for (int i = 0; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (c != Character.toUpperCase(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if a string is lower case.
   *
   * @param s string.
   * @return true iif s is only made of lower case characters.
   */
  public static boolean isLowerCase(String s) {

    // Preconditions.checkNotNull(s);

    for (int i = 0; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (c != Character.toLowerCase(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * A string normalizer which performs the following steps:
   * <ol>
   * <li>Unicode canonical decomposition ({@link Normalizer.Form#NFD})</li>
   * <li>Removal of diacritical marks</li>
   * <li>Unicode canonical composition ({@link Normalizer.Form#NFC})</li>
   * </ol>
   */
  public static String removeDiacriticalMarks(String s) {

    // Preconditions.checkNotNull(s);

    String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
    Pattern diacriticals = Pattern.compile("\\p{InCombiningDiacriticalMarks}");
    Matcher matcher = diacriticals.matcher(decomposed);
    String noDiacriticals = matcher.replaceAll("");
    return Normalizer.normalize(noDiacriticals, Normalizer.Form.NFC);
  }

  /**
   * Check if a character is a whitespace. This method takes into account Unicode space characters.
   *
   * @param c character as a unicode code point.
   * @return true if c is a space character.
   */
  public static boolean isWhitespace(int c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  /**
   * Check if a character is a punctuation in the standard ASCII.
   *
   * @param c character.
   * @return true iif c is a punctuation character.
   */
  public static boolean isPunctuation(char c) {
    return isInRange(c, '!', '/') || isInRange(c, ':', '@') || isInRange(c, '[', '`')
        || isInRange(c, '{', '~');
  }

  /**
   * Check if a character is a punctuation in Unicode.
   *
   * @param c character.
   * @return true iif c is a punctuation character.
   */
  public static boolean isGeneralPunctuation(char c) {
    return isInRange(c, '\u2000', '\u206F');
  }

  /**
   * Check if a character is a CJK symbol.
   *
   * @param c character.
   * @return true iif c is a CJK symbol.
   */
  public static boolean isCjkSymbol(char c) {
    return isInRange(c, '\u3001', '\u3003') || isInRange(c, '\u3008', '\u301F');
  }

  /**
   * Check if a character is a currency symbol.
   *
   * @param c character.
   * @return true iif c is a currency symbol.
   */
  public static boolean isCurrency(char c) {
    return (c == '$') || isInRange(c, '\u00A2', '\u00A5') || isInRange(c, '\u20A0', '\u20CF');
  }

  /**
   * Check if a character is an arrow symbol.
   *
   * @param c character.
   * @return true iif c is an arrow symbol.
   */
  public static boolean isArrow(char c) {
    return isInRange(c, '\u2190', '\u21FF') || isInRange(c, '\u27F0', '\u27FF')
        || isInRange(c, '\u2900', '\u297F');
  }

  /**
   * Check if a character is an hyphen.
   *
   * @param c character.
   * @return true iif c is an hyphen.
   */
  public static boolean isHyphen(char c) {
    return c == '-' || isInRange(c, '\u2010', '\u2014');
  }

  /**
   * Check if a character is an apostrophe.
   *
   * @param c character.
   * @return true iif c is an apostrophe.
   */
  public static boolean isApostrophe(char c) {
    return c == '\'' || c == '\u2019';
  }

  /**
   * Check if a character is a list mark.
   *
   * @param c character.
   * @return true iif c is a list mark.
   */
  public static boolean isListMark(char c) {
    return c == '-' || c == '\uF0F0' || c == '\u2022' || c == '\u2023' || c == '\u203B'
        || c == '\u2043';
  }

  /**
   * Check if a character is a final mark.
   *
   * @param c character.
   * @return true iif c is a final mark.
   */
  public static boolean isTerminalMark(char c) {
    return c == '.' || c == '?' || c == '!' || c == '\u203C' || isInRange(c, '\u2047', '\u2049');
  }

  /**
   * Check if a character is a separator.
   *
   * @param c character.
   * @return true iif c is a separator.
   */
  public static boolean isSeparatorMark(char c) {
    return c == ',' || c == ';' || c == ':' || c == '|' || c == '/' || c == '\\';
  }

  /**
   * Check if a character is a quotation mark.
   *
   * @param c character.
   * @return true iif c is a quotation mark.
   */
  public static boolean isQuotationMark(char c) {
    return isSingleQuotationMark(c) || isDoubleQuotationMark(c);
  }

  /**
   * Check if a character is a single quotation mark.
   *
   * @param c character.
   * @return true iif c is a single quotation mark.
   */
  public static boolean isSingleQuotationMark(char c) {
    return c == '\'' || c == '`' || isInRange(c, '\u2018', '\u201B');
  }

  /**
   * Check if a character is a double quotation mark.
   *
   * @param c character.
   * @return true iif c is a double quotation mark.
   */
  public static boolean isDoubleQuotationMark(char c) {
    return c == '"' || c == '«' || c == '»' || isInRange(c, '\u201C', '\u201F');
  }

  /**
   * Check if a character is a bracket.
   *
   * @param c character.
   * @return true iif c is a bracket.
   */
  public static boolean isBracket(char c) {
    return isLeftBracket(c) || isRightBracket(c);
  }

  /**
   * Check if a character is a left bracket.
   *
   * @param c character.
   * @return true iif c is a left bracket.
   */
  public static boolean isLeftBracket(char c) {
    return c == '(' || c == '{' || c == '[' || c == '<';
  }

  /**
   * Check if a character is a right bracket.
   *
   * @param c character.
   * @return true iif c is a right bracket.
   */
  public static boolean isRightBracket(char c) {
    return c == ')' || c == '}' || c == ']' || c == '>';
  }

  /**
   * Check if a character is contained in an interval.
   *
   * @param c character.
   * @param start lower bound (inclusive).
   * @param end upper bound (inclusive).
   * @return true iif c is contained in [start, end].
   */
  private static boolean isInRange(char c, int start, int end) {
    return start <= c && c <= end;
  }

  /**
   * Join a list of strings. Similar to Guava's
   *
   * <pre>
   * Joiner.on(separator).join(strings)
   * </pre>
   *
   * @return a string.
   */
  public static String join(List<String> strings, char separator) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < strings.size(); i++) {
      if (i > 0) {
        builder.append(separator);
      }
      builder.append(strings.get(i));
    }
    return builder.toString();
  }

  /**
   * Sets the current document and resets the current position to the start of it.
   */
  public void reset(String text) {

    // Preconditions.checkNotNull(text);

    if (text == null) {
      throw new NullPointerException("\"text\" should not be null");
    }

    text_ = text;
    position_ = 0;
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

  /**
   * Indicates if the current position is at the end of the current document.
   *
   * @return true iif we reached the end of the document, false otherwise.
   */
  public boolean isEndOfText() {
    return position_ >= text_.length();
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
   * Returns the character beyond the current position, or a null character if the specified
   * position is at the end of the document.
   *
   * @return The character at the current position.
   */
  public char peek() {
    return peek(0);
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
   * Extracts a substring from the specified range of the current text.
   */
  public String extract(int start) {
    return extract(start, text_.length());
  }

  /**
   * Extracts a substring from the specified range of the current text.
   */
  public String extract(int start, int end) {

    // Preconditions.checkArgument(start >= 0 && start <= text_.length());
    // Preconditions.checkArgument(end >= 0 && end <= text_.length() && end >= start);

    return text_.substring(start, end);
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
    while (c != LF && c != CR && !isEndOfText()) {
      moveAhead();
      c = peek();
    }
  }

  /**
   * Moves the current position to the next character that is a whitespace.
   */
  public void moveToWhitespace() {
    while (!isWhitespace(peek()) && !isEndOfText()) {
      moveAhead();
    }
  }

  /**
   * Moves the current position to the next character that is not whitespace.
   */
  public void movePastWhitespace() {
    while (isWhitespace(peek()) && !isEndOfText()) {
      moveAhead();
    }
  }

  /**
   * Determines if the specified character exists in the specified character array.
   *
   * @param c Character to find.
   * @param chars Character array to search.
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
