/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract.pipe;


import java.util.ArrayList;

import cc.mallet.extract.StringSpan;
import cc.mallet.extract.StringTokenization;
import cc.mallet.extract.Tokenization;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;

/**
 * Heuristically converts a simple token sequence into a Tokenization
 *   that can be used with all the extract package goodies.
 * <P>
 * Users of this class should be warned that the tokens' features and properties
 *  list are moved over directly, with no deep-copying. 
 *
 * Created: Jan 21, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TokenSequence2Tokenization.java,v 1.1 2007/10/22 21:38:00 mccallum Exp $
 */
public class TokenSequence2Tokenization extends Pipe {

  public Instance pipe (Instance carrier)
  {
    Object data = carrier.getData ();
    if (data instanceof Tokenization) {
      // we're done
    } else if (data instanceof TokenSequence) {
      StringBuffer buf = new StringBuffer ();
      TokenSequence ts = (TokenSequence) data;
      StringTokenization spans = new StringTokenization (buf);  // I can use a StringBuffer as the doc! Awesome!

      for (int i = 0; i < ts.size(); i++) {
        Token token = ts.getToken (i);

        int start = buf.length ();
        buf.append (token.getText());
        int end = buf.length();

        StringSpan span = new StringSpan (buf, start, end);
        span.setFeatures (token.getFeatures ());
        span.setProperties (token.getProperties ());

        spans.add (span);
        buf.append (" ");
      }

      carrier.setData (spans);
    } else {
      throw new IllegalArgumentException ("Can't convert "+data+" to Tokenization.");
    }

    return carrier;
  }

}
