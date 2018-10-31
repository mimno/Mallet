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

import cc.mallet.types.Instance;
import java.io.*;
import java.util.Iterator;
import java.util.regex.*;
import java.net.URI;
import java.net.URISyntaxException;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;

/**
 *  This iterator, perhaps more properly called a Line Pattern Iterator, 
 *   reads through a file and returns one instance per line,
 *   based on a regular expression.<p>
 *   
 *  If you have data of the form 
 *   <pre>[name]  [label]  [data]</pre>
 *  and a {@link Pipe} <code>instancePipe</code>, you could read instances using this code:
<pre>    InstanceList instances = new InstanceList(instancePipe);

    instances.addThruPipe(new CsvIterator(new FileReader(dataFile),
                                          "(\\w+)\\s+(\\w+)\\s+(.*)",
                                          3, 2, 1)  // (data, target, name) field indices                    
                         );
</pre>
 *
 */
public class CsvIterator implements Iterator<Instance>
{
	LineNumberReader reader;
	Pattern lineRegex;
	int uriGroup, targetGroup, dataGroup;
	String currentLine;
	
	public CsvIterator (Reader input, Pattern lineRegex, int dataGroup, int targetGroup, int uriGroup)
	{
		this.reader = new LineNumberReader (input);
		this.lineRegex = lineRegex;
		this.targetGroup = targetGroup;
		this.dataGroup = dataGroup;
		this.uriGroup = uriGroup;
		if (dataGroup <= 0)
			throw new IllegalStateException ("You must extract a data field.");
		try {
			this.currentLine = reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException ();
		}
	}

	public CsvIterator (Reader input, String lineRegex, int dataGroup, int targetGroup, int uriGroup)
	{
		this (input, Pattern.compile (lineRegex), dataGroup, targetGroup, uriGroup);
	}

	public CsvIterator (String filename, String lineRegex, int dataGroup, int targetGroup, int uriGroup)
		throws java.io.FileNotFoundException
	{
		this (new FileReader (new File(filename)),
					Pattern.compile (lineRegex), dataGroup, targetGroup, uriGroup);
	}
	
	// The PipeInputIterator interface

	public Instance next ()
	{
		String uriStr = null;
		String data = null;
		String target = null;
		Matcher matcher = lineRegex.matcher(currentLine);
		if (matcher.find()) {
			if (uriGroup > 0)
				uriStr = matcher.group(uriGroup);
			if (targetGroup > 0)
				target = matcher.group(targetGroup);
			if (dataGroup > 0)
				data = matcher.group(dataGroup);
		} else {
			String msg = "Line #"+reader.getLineNumber()+" does not match regex:\n" +
					currentLine;
			try {
				reader.getLineNumber();
				this.currentLine = reader.readLine();
			} catch (IOException e) {
				throw new IllegalStateException ();
			}
			throw new IllegalStateException (msg);
		}

		String uri;
		if (uriStr == null) {
			uri = "csvline:"+reader.getLineNumber();
		} else {
			uri = uriStr;
		}
		assert (data != null);
		Instance carrier = new Instance (data, target, uri, null);
		try {
			this.currentLine = reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException ();
		}
		return carrier;
	}

	public boolean hasNext ()	{	return currentLine != null;	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}
