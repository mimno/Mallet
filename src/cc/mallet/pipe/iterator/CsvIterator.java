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
import java.util.NoSuchElementException;
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
	private boolean lastLine;
	LineNumberReader reader;
	Pattern lineRegex;
	int uriGroup, targetGroup, dataGroup;
//	String currentLine;
	
	public CsvIterator (Reader input, Pattern lineRegex, int dataGroup, int targetGroup, int uriGroup)
	{
		this.reader = new LineNumberReader (input);
		this.lineRegex = lineRegex;
		this.targetGroup = targetGroup;
		this.dataGroup = dataGroup;
		this.uriGroup = uriGroup;
		this.lastLine	= false;
		if (dataGroup <= 0)
			throw new IllegalStateException ("You must extract a data field.");
//		try {
//			this.currentLine = reader.readLine();
//		} catch (IOException e) {
//			throw new IllegalStateException ();
//		}
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

//	public Instance next ()
//	{
//		String uriStr = null;
//		String data = null;
//		String target = null;
//		Matcher matcher = lineRegex.matcher(currentLine);
//		if (matcher.find()) {
//			if (uriGroup > 0)
//				uriStr = matcher.group(uriGroup);
//			if (targetGroup > 0)
//				target = matcher.group(targetGroup);
//			if (dataGroup > 0)
//				data = matcher.group(dataGroup);
//		} else {
//			String msg = "Line #"+reader.getLineNumber()+" does not match regex:\n" +
//					currentLine;
//			try {
//				reader.getLineNumber();
//				this.currentLine = reader.readLine();
//			} catch (IOException e) {
//				throw new IllegalStateException ();
//			}
//			throw new IllegalStateException (msg);
//		}
//
//		String uri;
//		if (uriStr == null) {
//			uri = "csvline:"+reader.getLineNumber();
//		} else {
//			uri = uriStr;
//		}
//		assert (data != null);
//		Instance carrier = new Instance (data, target, uri, null);
//		try {
//			this.currentLine = reader.readLine();
//		} catch (IOException e) {
//			throw new IllegalStateException ();
//		}
//		return carrier;
//	}

	public Instance next ()
	{
		String uriStr = null;
		String data = null;
		String target = null;
		try {
			FileLine line = getAndUpdateCurrentLine();
			if (line.isEmpty()) throw new NoSuchElementException("end of file");
			Matcher matcher = lineRegex.matcher(line.getText());
			if (matcher.find()) {
				if (uriGroup > 0)
					uriStr = matcher.group(uriGroup);
				if (targetGroup > 0)
					target = matcher.group(targetGroup);
				if (dataGroup > 0)
					data = matcher.group(dataGroup);
			} else {
				String msg = "Line #"+line.getNumber()+" does not match regex:\n" + line.getText();
				throw new IllegalStateException (msg);
			}
			String uri;
			if (uriStr == null) {
				uri = "csvline:"+line.getNumber();
			} else {
				uri = uriStr;
			}
			assert (data != null);
			Instance carrier = new Instance (data, target, uri, null);
			return carrier;
		} catch (IOException e) {
			throw new IllegalStateException (e.getMessage());
		}
	}

	private synchronized FileLine getAndUpdateCurrentLine() throws IOException {
		FileLine line = new FileLine(reader.readLine(), reader.getLineNumber());
		if (line.isEmpty()) setLastLine();
		return line;
	}


	public boolean hasNext ()	{
		//return currentLine != null;
		return !isLastLine();
	}

	private synchronized boolean isLastLine(){
		return this.lastLine;
	}

	private synchronized void setLastLine(){
		this.lastLine = true;
	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}


	private class FileLine{
		String text;
		Integer number;

		public FileLine(String text, Integer number) {
			this.text = text;
			this.number = number;
		}

		public boolean isEmpty(){
			return ((text == null) || (text.trim().equalsIgnoreCase("")));
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public Integer getNumber() {
			return number;
		}

		public void setNumber(Integer number) {
			this.number = number;
		}
	}

}
