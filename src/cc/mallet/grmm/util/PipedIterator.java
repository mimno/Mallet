/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.util;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.PipeInputIterator;
import cc.mallet.types.Instance;
import java.util.Iterator;

/**
 * Created: Mar 3, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: PipedIterator.java,v 1.1 2007/10/22 21:37:58 mccallum Exp $
 */
@Deprecated // With the new Pipe's able to act as iterators themselves, this should no longer be necessary
public class PipedIterator implements Iterator<Instance> {

  Iterator<Instance> subIt;
  Pipe pipe;

  public PipedIterator (Iterator<Instance> subIt, Pipe pipe)
  {
    this.subIt = subIt;
    this.pipe = pipe;
  }

  // The PipeInputIterator interface
  public Instance next ()
  {
    Instance inst = subIt.next ();
    inst = pipe.pipe (inst);
    return new Instance (inst.getData (), inst.getTarget (), inst.getName (), inst.getSource ());
  }

  public boolean hasNext ()
  {
    return subIt.hasNext ();
  }
  
  public void remove () { throw new IllegalStateException ("This Iterator<Instance> does not implement remove()."); }
}
