/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.pipe.iterator;

import java.io.*;
import java.util.Iterator;
import java.util.regex.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;

/** Iterate over groups of lines of text, separated by lines that
		match a regular expression.  For example, the WSJ BaseNP data
		consists of sentences with one word per line, each sentence
		separated by a blank line.  If the "boundary" line is to be
		included in the group, it is placed at the end of the group. */

public class LineGroupIterator implements Iterator<Instance>
{
	LineNumberReader reader;
	Pattern lineBoundaryRegex;
	boolean skipBoundary;
	//boolean putBoundaryLineAtEnd; // Not yet implemented
	String nextLineGroup;
	int groupIndex = 0;
	
	public LineGroupIterator (Reader input, Pattern lineBoundaryRegex, boolean skipBoundary)
	{
		this.reader = new LineNumberReader (input);
		this.lineBoundaryRegex = lineBoundaryRegex;
		this.skipBoundary = skipBoundary;
		this.nextLineGroup = getNextLineGroup();
	}

	// added by Fuchun Peng
	public String getLineGroup ()
	{
		return nextLineGroup;
	}
	// added by Fuchun Peng
	public void nextLineGroup()
	{
		nextLineGroup = getNextLineGroup();
	}

	public String getNextLineGroup ()
	{
		StringBuffer sb = new StringBuffer ();
		String line;
		while (true) {
			try {
				line = reader.readLine();
			} catch (IOException e) {
				throw new RuntimeException (e);
			}
			//System.out.println ("LineGroupIterator: got line: "+line);
			if (line == null) {
				break;
			} else if (lineBoundaryRegex.matcher (line).matches()) {
				if (!skipBoundary) {
					sb.append(line);
					sb.append('\n');
				}
				if (sb.length() > 0)
					break;
			} else {
				sb.append(line);
				sb.append('\n');
			}
		}
		if (sb.length() == 0)
			return null;
		else
			return sb.toString();
	}
	
	// The PipeInputIterator interface

	public Instance next ()
	{
		assert (nextLineGroup != null);
		Instance carrier = new Instance (nextLineGroup, null, "linegroup"+groupIndex++,
																		 null);
		nextLineGroup = getNextLineGroup ();
		return carrier;
	}

	public boolean hasNext ()	{	return nextLineGroup != null;	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}
