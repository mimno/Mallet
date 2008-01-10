/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe.iterator;

import java.io.*;
import java.util.Iterator;

import cc.mallet.types.*;

/**
 * Iterator that takes a Reader, breaks up the input into
 *  top-level parenthesized expressions.  For example,
 *  in the input <tt>(a (a b) c) f (d e)</tt>, there
 *  are two top-level expressions '(a (a b) c)' and '(d e)'.
 *
 * Text that is not within parentheses is ignored. 
 *
 * Created: Thu Feb 26 13:45:43 2004
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: ParenGroupIterator.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class ParenGroupIterator implements Iterator<Instance> {

	private Reader reader;
	private char open;
	private char close;

	private String nextGroup;
	private int groupIdx;

	public ParenGroupIterator (Reader input) {
		this (input, '(', ')');
	}

	public ParenGroupIterator (Reader input, char openParen, char closeParen) {
		this.reader = new BufferedReader (input);
		this.open = openParen;
		this.close = closeParen;
		nextGroup = getNextGroup ();
	} 
	

	private String getNextGroup ()
	{
		StringBuffer buf = new StringBuffer ();
		int depth = 1;

		try {
			// Eat up nonparen characters
			int b;
			while ((b = reader.read()) != (int)open) {
				if (b == -1) return null;
			}
			
			buf.append (open);
			while ((b = reader.read()) != -1) {
				char ch = (char)b;
				buf.append (ch);
				
				if (ch == open) {
				depth++;
				} else if (ch == close) {
					depth--;
					if (depth == 0) break;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException (e);
		}

		return buf.toString();
	}

	// Interface PipeInputIterate

	public Instance next ()
	{
		Instance carrier = new Instance (nextGroup, null, 
																		 "parengroup"+(groupIdx++), 
																		 null);
		nextGroup = getNextGroup ();
		return carrier;
	}

	public boolean hasNext () {
		return nextGroup != null;
	}

	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}
