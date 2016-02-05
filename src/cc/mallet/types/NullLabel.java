/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.types;

import cc.mallet.types.Alphabet;
import cc.mallet.types.AlphabetCarrying;
import cc.mallet.types.LabelAlphabet;

/**
 * Object that carries a LabelAlphabet. Can be used in place of a
 * null target for unlabeled instances, which avoids issues
 * with alphabet checks.
 *
 * @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 */

public class NullLabel implements AlphabetCarrying {

  private LabelAlphabet alphabet;
  
  public NullLabel(LabelAlphabet alphabet) {
    this.alphabet = alphabet;  
  }

  public Alphabet getAlphabet() {
    return alphabet;
  }

  public Alphabet[] getAlphabets() {
    return new Alphabet[] { alphabet };
  }
}
