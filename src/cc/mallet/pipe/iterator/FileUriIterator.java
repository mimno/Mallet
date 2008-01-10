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

import java.util.Iterator;
import java.util.ArrayList;
import java.io.*;
import java.net.URI;
import java.util.regex.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;

public class FileUriIterator extends FileIterator
{
	public FileUriIterator (File[] directories, FileFilter filter, Pattern targetPattern)
	{
		super (directories, filter, targetPattern);
	}

	public FileUriIterator (File directory, FileFilter filter, Pattern targetPattern)
	{
		super (directory, filter, targetPattern);
	}
	
	public FileUriIterator (File[] directories, Pattern targetPattern)
	{
		super (directories, null, targetPattern);
	}

	public FileUriIterator (File directory, Pattern targetPattern)
	{
		super (directory, null, targetPattern);
	}

	public Instance next ()
	{
		Instance carrier = super.next();
		carrier.setData(((File)carrier.getData()).toURI());
		return carrier;
	}
	
}

