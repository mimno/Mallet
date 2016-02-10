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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.*;
import java.io.*;

import cc.mallet.types.Instance;

/**
 * An iterator that generates instances from an initial
 * directory or set of directories. The iterator will recurse through sub-directories.
 * Each filename becomes the data field of an instance, and the targets are set to null.
 * To set the target values to the directory name, use FileIterator instead.
 * <p>
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 *  @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 */
public class UnlabeledFileIterator implements Iterator<Instance>
{
	FileFilter fileFilter;
	ArrayList<File> fileArray;
	Iterator<File> subIterator;
	File[] startingDirectories;
	int[] minFileIndex;
	int fileCount;

	/** Special value that means to use the directories[i].getPath() as the target name */
	// xxx Note that these are specific to UNIX directory delimiter characters!  Fix this.

	/** Use as label names the directories specified in the constructor,
	 * optionally removing common prefix of all starting directories
	 */
	public static final Pattern STARTING_DIRECTORIES = Pattern.compile ("_STARTING_DIRECTORIES_");
	/** Use as label names the first directory in the filename. */
	public static final Pattern FIRST_DIRECTORY = Pattern.compile ("/?([^/]*)/.+");
	/** Use as label name the last directory in the filename. */
	public static final Pattern LAST_DIRECTORY = Pattern.compile(".*/([^/]+)/[^/]+"); // was ("([^/]*)/[^/]+");
	/** Use as label names all the directory names in the filename. */
	public static final Pattern ALL_DIRECTORIES = Pattern.compile ("^(.*)/[^/]+");


	// added by Fuchun Peng	
	public ArrayList<File> getFileArray()
	{
		return fileArray;
	}

	/**
	 * Construct a FileIterator that will supply filenames within initial directories
	 * as instances
	 * @param directories  Array of directories to collect files from
	 * @param fileFilter   class implementing interface FileFilter that will decide which names to accept.
	 *                     May be null.
	 * @param targetPattern  regex Pattern applied to the filename whose first parenthesized group
	 *                       on matching is taken to be the target value of the generated instance. The pattern is applied to
	 *                       the directory with the matcher.find() method. If null, then all instances
   *                       will have target null.
	 * @param removeCommonPrefix boolean that modifies the behavior of the STARTING_DIRECTORIES pattern,
	 *                           removing the common prefix of all initially specified directories,
	 *                          leaving the remainder of each filename as the target value.
	 *
	 */
	protected UnlabeledFileIterator(File[] directories, FileFilter fileFilter) {
		this.startingDirectories = directories;
		this.fileFilter = fileFilter;
		this.minFileIndex = new int[directories.length];
		this.fileArray = new ArrayList<File> ();

		for (int i = 0; i < directories.length; i++) {
			if (!directories[i].isDirectory())
				throw new IllegalArgumentException (directories[i].getAbsolutePath()
				                                    + " is not a directory.");
			minFileIndex[i] = fileArray.size();
			fillFileArray (directories[i], fileFilter, fileArray);
		}
		this.subIterator = fileArray.iterator();
		this.fileCount = 0;

		String[] dirStrings = new String[directories.length];
		for (int i = 0; i < directories.length; i++)
			dirStrings[i] = directories[i].toString();
	}

	public static File[] stringArray2FileArray (String[] sa)
	{
		File[] ret = new File[sa.length];
		for (int i = 0; i < sa.length; i++)
			ret[i] = new File (sa[i]);
		return ret;
	}

	public UnlabeledFileIterator (String[] directories, FileFilter ff)
	{
		this (stringArray2FileArray(directories), ff);
	}

	public  UnlabeledFileIterator (File directory, FileFilter fileFilter)
	{
		this (new File[] {directory}, fileFilter);
	}
	
	public UnlabeledFileIterator (File directory)
	{
		this (new File[] {directory}, null);
	}
	
	public UnlabeledFileIterator (File[] directories)
	{
		this (directories, null);
	}

	public UnlabeledFileIterator (String directory)
	{
		this (new File[] {new File(directory)}, null);
	}

    public UnlabeledFileIterator (String directory, FileFilter filter) {
       this (new File[] {new File(directory) }, filter);
    }

	private int fillFileArray (File directory, FileFilter filter, ArrayList<File> files)
	{
		int count = 0;
		File[] directoryContents = directory.listFiles();
		for (int i = 0; i < directoryContents.length; i++) {
			if (directoryContents[i].isDirectory())
				count += fillFileArray (directoryContents[i], filter, files);
			else if (filter == null || filter.accept(directoryContents[i])) {
				files.add (directoryContents[i]);
				count++;
			}
		}
		return count;
	}

	// The PipeInputIterator interface
	public Instance next ()
	{
		File nextFile = subIterator.next();
		fileCount++;
		return new Instance (nextFile, null, nextFile.toURI(), null);
	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

	// culotta - 9.11.03
	public File nextFile ()
	{
		return subIterator.next();		
	}

	public boolean hasNext ()	{	return subIterator.hasNext();	}
	
}

