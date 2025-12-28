/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.pipe;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.types.Alphabet;

/**
 * Created: Aug 28, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: PipeUtils.java,v 1.1 2007/10/22 21:37:39 mccallum Exp $
 */
public class PipeUtils {

  private PipeUtils () {}; // no instances

  public static Pipe concatenatePipes (Pipe p1, Pipe p2)
  {
    Alphabet dataDict = combinedDataDicts (p1, p2);
    Alphabet targetDict = combinedTargetDicts (p1, p2);
    Pipe ret = new SerialPipes (new Pipe[] { p1, p2 });

    if (dataDict != null) ret.dataAlphabetResolved = true;
    if (targetDict != null) ret.targetAlphabetResolved = true;
    
    ret.dataAlphabet = dataDict;
    ret.targetAlphabet = targetDict;
    return ret;
  }

  private static Alphabet combinedDataDicts (Pipe p1, Pipe p2)
  {
    if (p1.dataAlphabet == null) return p2.dataAlphabet;
    if (p2.dataAlphabet == null) return p1.dataAlphabet;
    if (p1.dataAlphabet == p2.dataAlphabet) return p2.dataAlphabet;
    throw new IllegalArgumentException ("Attempt to concat pipes with incompatible data dicts.");
  }

  private static Alphabet combinedTargetDicts (Pipe p1, Pipe p2)
  {
    if (p1.targetAlphabet == null) return p2.targetAlphabet;
    if (p2.targetAlphabet == null) return p1.targetAlphabet;
    if (p1.targetAlphabet == p2.targetAlphabet) return p2.targetAlphabet;
    throw new IllegalArgumentException ("Attempt to concat pipes with incompatible target dicts.");
  }


}
