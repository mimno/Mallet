/* Copyright (C) 2003 University of Pennsylvania.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 */

package cc.mallet.pipe.iterator;

import java.util.Iterator;

import cc.mallet.pipe.*;
import cc.mallet.types.Instance;

/**
 * Provides a {@link PipeExtendedIterator} that applies a {@link Pipe} to
 * the {@link Instance}s returned by a given {@link PipeExtendedIterator},
 * It is intended to encapsulate preprocessing that should not belong to the
 * input {@link Pipe} of a {@link Classifier} or {@link Transducer}.
 *
 * @author <a href="mailto:pereira@cis.upenn.edu">Fernando Pereira</a>
 * @version 1.0
 */
@Deprecated // Now that Pipe's support iteration directly, this should no longer be necessary? -AKM 9/2007
public class PipeExtendedIterator implements Iterator<Instance>
{
	private Iterator<Instance> iterator;
  private Pipe pipe;

	/**
   * Creates a new <code>PipeExtendedIterator</code> instance.
   *
   * @param iterator the base <code>PipeExtendedIterator</code>
   * @param pipe The <code>Pipe</code> to postprocess the iterator output
   */
  public PipeExtendedIterator (Iterator<Instance> iterator, Pipe pipe)
	{
		this.iterator = iterator;
    this.pipe = pipe;
	}

	//public PipeExtendedIterator(ArrayDataAndTargetIterator iterator2, CharSequenceArray2TokenSequence sequence) {
		// TODO Auto-generated constructor stub
	//}

	public boolean hasNext ()
	{
		return iterator.hasNext();
	}

	public Instance next ()
	{
    return pipe.pipe(iterator.next());
	}

	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}


}



