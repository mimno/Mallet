/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;

import cc.mallet.extract.Tokenization;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;

/**
 * Created: Mar 17, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: RememberTokenizationPipe.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
public class RememberTokenizationPipe extends Pipe {

  public Instance pipe (Instance carrier)
  {
    Tokenization tok = (Tokenization) carrier.getData ();
    carrier.setProperty ("TOKENIZATION", tok);
    return carrier;
  }
}
