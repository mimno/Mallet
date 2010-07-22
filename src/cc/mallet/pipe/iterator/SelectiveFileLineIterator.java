/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe.iterator;

import java.io.*;
import java.util.Iterator;
import java.net.URI;

import cc.mallet.types.*;

/**
 * Very similar to the SimpleFileLineIterator, 
 * but skips lines that match a regular expression.
 * 
 * @author Gregory Druck
 */
public class SelectiveFileLineIterator implements Iterator<Instance> {

	BufferedReader reader = null;
	int index = -1;
	String currentLine = null;
	boolean hasNextUsed = false;
	String skipRegex;
	
	public SelectiveFileLineIterator (Reader reader, String skipRegex) {
		this.reader = new BufferedReader (reader);
		this.index = 0;
		this.skipRegex = skipRegex;
	}

	public Instance next () {
		if (!hasNextUsed) {
			try {
				currentLine = reader.readLine();
				while (currentLine != null && currentLine.matches(skipRegex)) {
					currentLine = reader.readLine();
				}
			}
			catch (IOException e) {
				throw new RuntimeException (e);
			}
		}
		else {
			hasNextUsed = false;
		}

		URI uri = null;
		try { uri = new URI ("array:" + index++); }
		catch (Exception e) { throw new RuntimeException (e); }
		return new Instance (currentLine, null, uri, null);
	}

	public boolean hasNext ()	{	
		hasNextUsed = true; 
		try {
			currentLine = reader.readLine();
			while (currentLine != null && currentLine.matches(skipRegex)) {
				currentLine = reader.readLine();
			} 
		}
		catch (IOException e) {
			throw new RuntimeException (e);
		}
		return (currentLine != null);	
	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}
}
