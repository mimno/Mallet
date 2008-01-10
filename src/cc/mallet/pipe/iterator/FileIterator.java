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
import java.net.URI;
import java.util.regex.*;
import java.io.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;
import cc.mallet.util.Strings;

/**
 * An iterator that generates instances from an initial
 * directory or set of directories.
 * Each filename becomes the data field of an instance, and the result of
 * a user-specified regular expression pattern applied to the filename becomes
 * the target value of the instance.
 * <p>
 * In document classification it is common that the file name in the data field
 * will be subsequently processed by one or more pipes until it contains a feature vector.
 * The pattern applied to the file name is often
 * used to extract a directory name
 * that will be used as the true label of the instance; this label is kept in the target
 * field.
 *
 *
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class FileIterator implements Iterator<Instance>
{
	FileFilter fileFilter;
	ArrayList fileArray;
	Iterator subIterator;
	Pattern targetPattern;								// Set target slot to string coming from 1st group of this Pattern
	File[] startingDirectories;
	int[] minFileIndex;
	int fileCount;
	int commonPrefixIndex;

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
	public ArrayList getFileArray()
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
	protected FileIterator(File[] directories, FileFilter fileFilter,
	                       Pattern targetPattern, boolean removeCommonPrefix) {
		this.startingDirectories = directories;
		this.fileFilter = fileFilter;
		this.minFileIndex = new int[directories.length];
		this.fileArray = new ArrayList ();
		this.targetPattern = targetPattern;

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

		if (removeCommonPrefix)
			this.commonPrefixIndex = Strings.commonPrefixIndex (dirStrings);

		//print the files
		//		System.out.println("FileIterator fileArray");
		//		for(int i=0; i<fileArray.size(); i++){
		//			File file = (File) fileArray.get(i);
		//			System.out.println(file.toString());
		//		}

	}

	public FileIterator (File[] directories, FileFilter fileFilter, Pattern targetPattern)
	{
		this (directories, fileFilter, targetPattern, false);
	}

	/** Iterate over Files that pass the fileFilter test, setting... */
	public FileIterator (File[] directories, Pattern targetPattern)
	{
		this (directories, null, targetPattern);
	}

	public FileIterator (File[] directories, Pattern targetPattern, boolean removeCommonPrefix )
	{
		this (directories, null, targetPattern, removeCommonPrefix);
	}

	public static File[] stringArray2FileArray (String[] sa)
	{
		File[] ret = new File[sa.length];
		for (int i = 0; i < sa.length; i++)
			ret[i] = new File (sa[i]);
		return ret;
	}

	public FileIterator (String[] directories, FileFilter ff)
	{
		this (stringArray2FileArray(directories), ff, null);
	}
	
	public FileIterator (String[] directories, String targetPattern)
	{
		this (stringArray2FileArray(directories), Pattern.compile(targetPattern));
	}

	public FileIterator (String[] directories, Pattern targetPattern)
	{
		this (stringArray2FileArray(directories), targetPattern);
	}
	
	public FileIterator (String[] directories, Pattern targetPattern, boolean removeCommonPrefix)
	{
		this (stringArray2FileArray(directories), targetPattern, removeCommonPrefix);
	}

	public FileIterator (File directory, FileFilter fileFilter, Pattern targetPattern)
	{
		this (new File[] {directory}, fileFilter, targetPattern);
	}

	public FileIterator (File directory, FileFilter fileFilter,
	                     Pattern targetPattern, boolean removeCommonPrefix)
	{
		this (new File[] {directory}, fileFilter, targetPattern, removeCommonPrefix);
	}

	public  FileIterator (File directory, FileFilter fileFilter)
	{
		this (new File[] {directory}, fileFilter, null);
	}
	
	public FileIterator (File directory, Pattern targetPattern)
	{
		this (new File[] {directory}, null, targetPattern);
	}

	public FileIterator (File directory, Pattern targetPattern, boolean removeCommonPrefix)
	{
		this (new File[] {directory}, null, targetPattern, removeCommonPrefix);
	}

	public FileIterator (String directory, Pattern targetPattern)
	{
		this (new File[] {new File(directory)}, null, targetPattern);
	}
	
	public FileIterator (String directory, Pattern targetPattern, boolean removeCommonPrefix)
	{
		this (new File[] {new File(directory)}, null, targetPattern, removeCommonPrefix);
	}

	public FileIterator (File directory)
	{
		this (new File[] {directory}, null, null, false);
	}

	public FileIterator (String directory)
	{
		this (new File[] {new File(directory)}, null, null, false);
	}

    public FileIterator (String directory, FileFilter filter) {
       this (new File[] {new File(directory) }, filter, null);
    }

	private int fillFileArray (File directory, FileFilter filter, ArrayList files)
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
		File nextFile = (File) subIterator.next();
		String path = nextFile.getAbsolutePath();
		String targetName = null;

		if (targetPattern == STARTING_DIRECTORIES) {
			int i;
			for (i = 0; i < minFileIndex.length; i++)
				if (minFileIndex[i] > fileCount)
					break;
			targetName = startingDirectories[--i].getPath().substring(commonPrefixIndex);
		} else if (targetPattern != null) {
			Matcher m = targetPattern.matcher(path);
			if (m.find ()){
				targetName = m.group (1);
			}
		}
		fileCount++;
		return new Instance (nextFile, targetName, nextFile.toURI(), null);
	}
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

	// culotta - 9.11.03
	public File nextFile ()
	{
		return (File) subIterator.next();		
	}

	public boolean hasNext ()	{	return subIterator.hasNext();	}
	
}

