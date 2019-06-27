/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
  public static final String TAG_TABLE_ROW = "TABLE_ROW | ";
  public static final String TAG_SECTION_HEADER = "SECTION_HEADER | ";
  public static final String TAG_LIST_ITEM = "LIST_ITEM | ";
  public static final String TAG_TEXT = "TEXT | ";
  public static final String TAG_EMPTY_ROW = "EMPTY_ROW | ";
  public static final String TAG_SENTENCE = "SENTENCE | ";

  private static final Pattern TABLE_PATTERN =
      Pattern.compile("(\\s{2,}\\p{L}+)|(\\s{2,}[0-9]+)", Pattern.CASE_INSENSITIVE);

  private String text_ = null;
  private int position_ = 0;

  public StringIterator(String text) {
    this(text, false);
  }

  protected StringIterator(String text, boolean clean) {
    reset(clean ? clean(text) : text);
  }

  /**
   * Split texts extracted with XPDF's pdftotext or Apache Tika into a list of sentences.
   *
   * @param text text.
   * @param addTags true iif each sentence must be prefixed by a type chosen between EMPTY_ROW,
   *        TABLE_ROW, SECTION_HEADER or TAG_SENTENCE.
   * @return A list of sentences.
   */
  public static List<String> sentences(String text, boolean addTags) {

    List<String> rows = new ArrayList<>();
    StringIterator iterator = new StringIterator(clean(text, true), false);

    while (iterator.hasNext()) {
      int begin = iterator.position();
      iterator.moveToEndOfLine();
      int end = iterator.position();
      iterator.movePastWhitespace();
      rows.add(iterator.extract(begin, end));
    }

    List<String> sentences = new ArrayList<>();

    for (int i = 0; i < rows.size(); i++) {

      String row = rows.get(i);
      if (row == null || isBlank(row)) { // Discard blank rows
        continue;
      }
      if (row.startsWith(TAG_EMPTY_ROW)) { // Empty row
        sentences.add(addTags ? row : row.substring(TAG_EMPTY_ROW.length()));
        continue;
      }
      if (row.startsWith(TAG_TABLE_ROW)) { // Table row
        sentences.add(addTags ? row : row.substring(TAG_TABLE_ROW.length()));
        continue;
      }
      if (row.startsWith(TAG_SECTION_HEADER)) { // Section header
        sentences.add(addTags ? row : row.substring(TAG_SECTION_HEADER.length()));
        continue;
      }

      // Remove tags
      String curTag;
      if (row.startsWith(TAG_LIST_ITEM)) {
        row = row.substring(TAG_LIST_ITEM.length());
        curTag = TAG_SENTENCE; // Not a bug!
      } else if (row.startsWith(TAG_TEXT)) {
        row = row.substring(TAG_TEXT.length());
        curTag = TAG_SENTENCE;
      } else {
        curTag = "";
        continue;
      }

      // Parse each row as a list of sentences
      int isInBracket = 0;
      StringBuilder builder = new StringBuilder();
      StringIterator iter = new StringIterator(row, false);

      while (iter.hasNext()) {

        char c = iter.next();
        if (c == CR || c == LF) {
          sentences.add(addTags ? curTag + builder.toString() : builder.toString());
          isInBracket = 0;
          builder.setLength(0);
          continue;
        }
        if (isLeftBracket(c)) {
          isInBracket++;
        }
        if (isRightBracket(c)) {
          isInBracket--;
        }
        if (isInBracket != 0 || !isTerminalMark(c)) {
          builder.append(c);
          continue;
        }
        if (c != '.') {
          builder.append(c);
          sentences.add(addTags ? curTag + builder.toString() : builder.toString());
          isInBracket = 0;
          builder.setLength(0);
          continue;
        }
        if (builder.length() == 0) { // single dot
          builder.append(c);
          sentences.add(addTags ? curTag + builder.toString() : builder.toString());
          isInBracket = 0;
          builder.setLength(0);
          continue;
        }

        char prev = builder.charAt(builder.length() - 1);
        char next = iter.peek();

        builder.append(c);

        // 3.4 or .4
        if ((Character.isDigit(prev) || Character.isWhitespace(prev)) && Character.isDigit(next)) {
          continue;
        }

        int gapBegins = iter.position();
        while (iter.hasNext() && isWhitespace(iter.peek())) {
          c = iter.next();
        }
        int gapEnds = iter.position();

        String gap = gapBegins < gapEnds ? iter.extract(gapBegins, gapEnds) : "";
        next = iter.peek();

        if (Character.isLetter(prev) && Character.isLetter(next)) {

          // i.e.
          if (prev == Character.toLowerCase(prev) && next == Character.toLowerCase(next)) {
            if (!gap.contains("\n")) {
              continue;
            }
          }

          // C.H.U.
          if (prev == Character.toUpperCase(prev) && next == Character.toUpperCase(next)) {
            if (!gap.contains("\n")) {
              continue;
            }
          }
        }

        sentences.add(addTags ? curTag + builder.toString() : builder.toString());
        isInBracket = 0;
        builder.setLength(0);
      }

      if (builder.length() > 0) {
        sentences.add(addTags ? curTag + builder.toString() : builder.toString());
      }
    }
    return sentences;
  }

  /**
   * Remove useless CR/LF characters. A CR/LF character is "useless" if it is inside a paragraph for
   * formatting purpose only (e.g. it does not signify the end of the paragraph).
   *
   * Works quite well with Apache Tika and XPDF's pdf2text when the -layout option activated.
   *
   * This method should NEVER be called directly. This method is "public" for testing purposes only.
   *
   * @param text text.
   * @return trimmed text.
   */
  public static String clean(String text) {
    return clean(text, false);
  }

  /**
   * Remove useless CR/LF characters. A CR/LF character is "useless" if it is inside a paragraph for
   * formatting purpose only (e.g. it does not signify the end of the paragraph).
   *
   * Works quite well with Apache Tika and XPDF's pdf2text when the -layout option activated.
   *
   * @param text text.
   * @param addTags true iif each row must be prefixed by its type.
   * @return trimmed text.
   */
  private static String clean(String text, boolean addTags) {

    // Preconditions.checkArgument(!Strings.isNullOrEmpty(text));

    if (text == null) {
      return "";
    }

    char[] linebreaks = new char[] {CR, LF};

    String rows = tagRows(text);
    StringBuilder builder = new StringBuilder(text.length());
    StringIterator iterator = new StringIterator(rows);
    String rowBeforePrev = null;
    String rowPrev = null;

    while (iterator.hasNext()) {

      int begin = iterator.position();
      iterator.moveToEndOfLine();
      int end = iterator.position();

      String rowCur = iterator.extract(begin, end);
      iterator.movePast(linebreaks); // move to the beginning of the next row

      if (!rowCur.startsWith(TAG_EMPTY_ROW)) {
        mergeRows(builder, rowBeforePrev, rowPrev, rowCur, addTags);
      }

      rowBeforePrev = rowPrev;
      rowPrev = rowCur;
    }
    return builder.toString().trim();
  }

  /**
   * Prefix each row extracted either by Apache Tika or XPDF with a class : TABLE_ROW (this row
   * belongs to a table), SECTION_HEADER (this row is a header), LIST_ITEM (this row is a list
   * item), TEXT (this row is plain text).
   *
   * @param text text.
   * @return tagged rows.
   */
  private static String tagRows(String text) {

    // Preconditions.checkArgument(!Strings.isNullOrEmpty(text));

    if (text == null) {
      return "";
    }

    char[] linebreaks = new char[] {CR, LF};

    text = normalize(text);
    Set<String> tables =
        tables(text).stream().flatMap(Collection::stream).collect(Collectors.toSet());
    StringBuilder builder = new StringBuilder(text.length());
    StringIterator iterator = new StringIterator(text);

    while (iterator.hasNext()) {

      int begin = iterator.position();
      iterator.moveToEndOfLine();
      int end = iterator.position();

      String rowCur = iterator.extract(begin, end);
      iterator.movePast(linebreaks); // move to the beginning of the next row

      String eol = iterator.extract(end, iterator.position());

      if (tables.contains(rowCur)) {
        builder.append(TAG_TABLE_ROW);
        builder.append(rowCur);
      } else if (isSectionHeader(rowCur)) {
        builder.append(TAG_SECTION_HEADER);
        builder.append(rowCur);
      } else if (isListEntry(rowCur)) {
        builder.append(TAG_LIST_ITEM);
        builder.append(rowCur);
      } else {
        builder.append(TAG_TEXT);
        builder.append(rowCur);
      }

      int nbLinebreaks = 0;
      for (int i = 0; eol != null && i < eol.length(); i++) {
        if (eol.charAt(i) == CR) {
          nbLinebreaks++;
        }
      }

      builder.append(CR);
      if (nbLinebreaks >= 2) {
        builder.append(TAG_EMPTY_ROW);
        builder.append(CR);
      }
    }
    return builder.toString();
  }

  /**
   * Merge rows.
   *
   * @param builder
   * @param rowPrev
   * @param rowCur
   * @param keepRowTags
   */
  private static void mergeRows(StringBuilder builder, String rowBeforePrev, String rowPrev,
      String rowCur, boolean keepRowTags) {

    // Preconditions.checkNotNull(builder);
    // Preconditions.checkNotNull(rowCur);

    String row = rowCur.substring(rowCur.indexOf(" | ") + 3);

    if (rowPrev == null) {
      builder.append(keepRowTags ? rowCur : row);
    } else {

      // prev = TAG_LIST_ITEM && cur = TAG_TEXT
      // beforePrev = TAG_LIST_ITEM && prev = TAG_EMPTY_ROW && cur = TAG_TEXT
      boolean isListItem = ((rowBeforePrev != null && rowBeforePrev.startsWith(TAG_LIST_ITEM)
          && rowPrev.startsWith(TAG_EMPTY_ROW)) || rowPrev.startsWith(TAG_LIST_ITEM))
          && rowCur.startsWith(TAG_TEXT);

      // prev = TAG_TEXT && cur = TAG_TEXT
      // beforePrev = TAG_TEXT && prev = TAG_EMPTY_ROW && cur = TAG_TEXT
      boolean isParagraph = ((rowBeforePrev != null && rowBeforePrev.startsWith(TAG_TEXT)
          && rowPrev.startsWith(TAG_EMPTY_ROW)) || rowPrev.startsWith(TAG_TEXT))
          && rowCur.startsWith(TAG_TEXT);

      if (isListItem || isParagraph) {

        boolean hasEmptyRow;
        String prev;
        String cur;

        if (!rowPrev.startsWith(TAG_EMPTY_ROW)) {

          // prev = TAG_TEXT && cur = TAG_TEXT
          // prev = TAG_LIST_ITEM && cur = TAG_TEXT

          hasEmptyRow = false;
          prev = lastToken(rowPrev.substring(rowPrev.indexOf(" | ") + 3));
          cur = firstToken(row);
        } else {

          // beforePrev = TAG_TEXT && prev = TAG_EMPTY_ROW && cur = TAG_TEXT
          // beforePrev = TAG_LIST_ITEM && prev = TAG_EMPTY_ROW && cur = TAG_TEXT

          hasEmptyRow = true;
          prev = lastToken(rowBeforePrev.substring(rowBeforePrev.indexOf(" | ") + 3));
          cur = firstToken(row);
        }

        if (isLowerCase(cur) && isLowerCase(prev)
        /* && !isTerminalMark(prev.charAt(prev.length() - 1)) */) {

          // i.e. \n véhicule
          // véhicule \n acquis
          builder.append(SPACE);
          builder.append(trimLeft(row));
        } else if (isLowerCase(cur) && isUpperCase(prev)
            && !isTerminalMark(prev.charAt(prev.length() - 1)) && !hasEmptyRow) {

          // TTC \n annuelle
          builder.append(SPACE);
          builder.append(trimLeft(row));
        } else if (isLowerCase(cur) && isCapitalized(prev)
            && !isTerminalMark(prev.charAt(prev.length() - 1)) && !hasEmptyRow) {

          // Topic \n modeling algorithms
          builder.append(SPACE);
          builder.append(trimLeft(row));
        } else if (isCapitalized(cur) && isLowerCase(prev)
            && !isTerminalMark(prev.charAt(prev.length() - 1)) && !hasEmptyRow) {

          if (nbLetters(prev) > 0) {

            // latent \n Dirichlet allocation
            builder.append(SPACE);
            builder.append(trimLeft(row));
          } else {

            // Note de R10 rédigée le 25/10/2000 \n Objet : Observation sur l'individu Dumont
            builder.append(CR);
            builder.append(keepRowTags ? rowCur : row);
          }
        } else {
          builder.append(CR);
          builder.append(keepRowTags ? rowCur : row);
        }
      } else {
        builder.append(CR);
        builder.append(keepRowTags ? rowCur : row);
      }
    }
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
   * Extract tables using a regular expression. A row belongs to a table if it contains at least
   * three columns. Kind of.
   *
   * Works quite well with XPDF's pdf2text when the -layout option activated.
   *
   * @param text document.
   * @return A list of tables.
   */
  private static List<List<String>> tables(String text) {

    // Preconditions.checkArgument(!Strings.isNullOrEmpty(text));

    List<List<String>> tables = new ArrayList<>();
    StringIterator iterator = new StringIterator(text);
    List<String> rows = new ArrayList<>();

    while (iterator.hasNext()) {

      int begin = iterator.position();
      iterator.moveToEndOfLine();
      int end = iterator.position();
      iterator.next();

      String row = iterator.extract(begin, end);
      if (row.isEmpty()) {
        continue;
      }

      Matcher matcher = TABLE_PATTERN.matcher(row);
      int count = 0;

      while (matcher.find()) {
        count++;
      }

      if (count >= 2) {
        rows.add(row);
      } else if (count >= 1) {
        if (!rows.isEmpty()) {
          rows.add(row);
        }
      } else {
        if (!rows.isEmpty()) {
          tables.add(rows);
        }
        rows = new ArrayList<>();
      }
    }

    if (rows.size() >= 2) {
      tables.add(rows);
    }
    return tables;
  }

  /**
   * Check if a row is a section header.
   *
   * @param rowCur current row.
   * @return true iif the row is likely a section header.
   */
  private static boolean isSectionHeader(String rowCur) {

    // Preconditions.checkNotNull(row);

    if (isUpperCase(rowCur)) {
      return true;
    }
    if (isListEntry(rowCur)) { // Heuristic for header such as "1. Introduction"
      return Character.isDigit((int) rowCur.charAt(0));
    }
    return false;
  }

  /**
   * Check if a row is a list entry.
   *
   * @param row row.
   * @return true iif the row is likely a list entry.
   */
  private static boolean isListEntry(String row) {

    // Preconditions.checkNotNull(row);

    StringIterator iterator = new StringIterator(row);
    while (iterator.hasNext()) {

      char c = iterator.next();
      if (!isWhitespace(c)) {

        // Here, c is the first non-whitespace character of the row.

        // Simple case : list mark (ex: - or • or =>)
        if (isListMark(c)) {
          return true;
        }

        // Move to the first whitespace character
        int begin = iterator.position() - 1;
        while (iterator.hasNext() && !isWhitespace(iterator.peek())) {
          c = iterator.next();
        }
        int end = iterator.position();

        // The bullet is probably a special character
        if ((end - begin) <= 1 && !Character.isLetterOrDigit((int) c) && !isLeftBracket(c)
            && !isRightBracket(c) && !isQuotationMark(c)) {
          return true;
        }

        // Here, the bullet is probably a group of digits/letters
        // Move to the next non digit/letter character
        while (iterator.hasNext() && (isWhitespace(iterator.peek())
            || !Character.isLetterOrDigit((int) iterator.peek()))) {
          c = iterator.next();
        }
        end = iterator.position();

        // Complex case : alpha-numerical bullets (ex: 3. or 3) or 3/ or 3- or 3°)
        String mark = iterator.extract(begin, end).trim();
        if (mark != null && !mark.isEmpty()) {

          char firstChar = mark.charAt(0);
          char lastChar = mark.charAt(mark.length() - 1);

          if (mark.length() <= 10) { // alpha-numerical bullets should be short!

            // alpha-numerical bullets should start either with a number or a letter
            if (Character.isLetterOrDigit((int) firstChar)) {

              // alpha-numerical bullets should end with a mark
              if (isListMark(lastChar) || lastChar == '/' || lastChar == '°' || lastChar == '=') {
                return true;
              }

              // An attempt to differentiate "a." from "voiture." and "a)" from "voiture)"
              if (isRightBracket(lastChar) || lastChar == '.') {

                int hasDots = 0;
                int hasLetters = 0;
                int hasDigits = 0;

                for (int i = 0; i < mark.length() - 1 /* skip the last dot */; i++) {
                  if (Character.isLetter((int) mark.charAt(i))) {
                    hasDigits++;
                  } else if (Character.isDigit((int) mark.charAt(i))) {
                    hasLetters++;
                  } else if (mark.charAt(i) == '.') {
                    hasDots++;
                  }
                }
                return hasDots > 0 || (hasLetters == 0 && hasDigits <= 2)
                    || (hasDigits == 0 && hasLetters <= 2);
              }
            }
          }
        }
        return false;
      }
    }
    return false;
  }

  private static String firstToken(String s) {

    // Preconditions.checkNotNull(s);

    s = trimLeft(s);

    int i = 0;
    for (; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (isWhitespace(c)) {
        break;
      }
    }
    return s.substring(0, i);
  }

  private static String lastToken(String s) {

    // Preconditions.checkNotNull(s);

    s = trimRight(s);

    int i = s.length() - 1;
    for (; i >= 0; i--) {
      int c = s.codePointAt(i);
      if (isWhitespace(c)) {
        break;
      }
    }
    return s.substring(i + 1);
  }

  /**
   * Compute the number of letters.
   *
   * @param s string.
   * @return the number of letters.
   */
  public static int nbLetters(String s) {

    // Preconditions.checkNotNull(s);

    int nbLetters = 0;

    for (int i = 0; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (Character.isLetter(c)) {
        nbLetters++;
      }
    }
    return nbLetters;
  }

  /**
   * Compute the number of digits.
   *
   * @param s string.
   * @return the number of letters.
   */
  public static int nbDigits(String s) {

    // Preconditions.checkNotNull(s);

    int nbDigits = 0;

    for (int i = 0; i < s.length(); i++) {
      int c = s.codePointAt(i);
      if (Character.isDigit(c)) {
        nbDigits++;
      }
    }
    return nbDigits;
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
