/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.pipe.iterator;

import java.io.*;
import java.util.Iterator;
import java.util.regex.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;

/** Iterates over matching regular expresions. E.g. 
 *   regexp = Pattern.compile ("<p>(.+?)</p>")  will 
 *  extract <p> elements from:
 *
 * <p> This block is an element </p> this is not <p> but this is </p>
 *
*/

public class PatternMatchIterator implements Iterator<Instance>
{
  Pattern regexp;
  Matcher matcher;
  String nextElement;
  int elementIndex;
  
  public PatternMatchIterator (CharSequence input, Pattern regexp)
  {
    this.elementIndex = 0;
    this.regexp = regexp;
    this.matcher = regexp.matcher (input);
    this.nextElement = getNextElement();
  }
  
  public String getNextElement ()
  {
    if (matcher.find())
      return matcher.group(1);
    else return null;
  }
  
  // The PipeInputIterator interface
  
  public Instance next ()
  {
    assert (nextElement != null);
    Instance carrier = new Instance (nextElement, null, "element"+elementIndex++,
                                     null);
    nextElement = getNextElement ();
    return carrier;
  }
  
  public boolean hasNext () { return nextElement != null; }
 
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}
