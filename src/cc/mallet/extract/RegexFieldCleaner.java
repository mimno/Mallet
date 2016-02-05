/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;

import java.util.regex.Pattern;

/**
 * A field cleaner that removes all occurrences of a given regex.
 *
 * Created: Nov 26, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: RegexFieldCleaner.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class RegexFieldCleaner implements FieldCleaner {

  public static final String REMOVE_PUNCT = "\\p{Punct}+";

  private Pattern badRegex;

  public RegexFieldCleaner (String regex) {
    badRegex = Pattern.compile (regex);
  }

  public RegexFieldCleaner (Pattern regex) {
    badRegex = regex;
  }


  public String cleanFieldValue (String rawFieldValue)
  {
    String cleanString = badRegex.matcher (rawFieldValue).replaceAll ("");
    return cleanString;
  }

}
