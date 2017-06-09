/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;

/**
 * A string normalizer which performs the following steps:
 * <ol>
 * <li>Unicode canonical decomposition ({@link Form#NFD})</li>
 * <li>Removal of diacritical marks</li>
 * <li>Unicode canonical composition ({@link Form#NFC})</li>
 * </ol>
 */
public class CharSequenceNoDiacritics extends Pipe implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final int CURRENT_SERIAL_VERSION = 0;

  private final Pattern diacriticals_ = Pattern.compile("\\p{InCombiningDiacriticalMarks}");
  private final Matcher matcher_ = diacriticals_.matcher("");

  public CharSequenceNoDiacritics() {}

  @Override
  public Instance pipe(Instance carrier) {
    if (carrier.getData() instanceof String) {
      String data = (String) carrier.getData();
      carrier.setData(normalize(data));
      return carrier;
    } else {
      throw new IllegalArgumentException("CharSequenceNoDiacritics expects a String, found a "
          + carrier.getData().getClass());
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeInt(CURRENT_SERIAL_VERSION);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt();
  }

  private String normalize(String string) {
    String decomposed = Normalizer.normalize(string, Normalizer.Form.NFD);
    String noDiacriticals = removeDiacriticalMarks(decomposed);
    return Normalizer.normalize(noDiacriticals, Normalizer.Form.NFC);
  }

  private String removeDiacriticalMarks(String str) {
    matcher_.reset(str);
    return matcher_.replaceAll("");
  }
}
