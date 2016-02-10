/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */




/** 
  @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
  */

package cc.mallet.pipe.iterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.net.URI;
import java.util.regex.*;
import java.io.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;
import cc.mallet.util.Strings;

/**
 * An iterator that generates instances for a pipe from a list of filenames.
 * Each file is treated as a text file whose target is determined by 
 * a user-specified regular expression pattern applied to the filename
 *
 *  @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class FileListIterator implements Iterator<Instance>
{
  FileFilter fileFilter;
  ArrayList fileArray;
  Iterator subIterator;
  Pattern targetPattern;  // Set target slot to string coming from 1st group of this Pattern
  int commonPrefixIndex;

  /** Special value that means to use the directories[i].getPath() as the target name */
  // xxx Note that these are specific to UNIX directory delimiter characters!  Fix this.

  /** Use as label names the directories of the given files,
   * optionally removing common prefix of all starting directories
   */
  public static final Pattern STARTING_DIRECTORIES = Pattern.compile ("_STARTING_DIRECTORIES_");
  /** Use as label names the first directory in the filename. */
  public static final Pattern FIRST_DIRECTORY = Pattern.compile ("/?([^/]*)/.+");
  /** Use as label name the last directory in the filename. */
  public static final Pattern LAST_DIRECTORY = Pattern.compile(".*/([^/]+)/[^/]+"); // was ("([^/]*)/[^/]+");
  /** Use as label names all the directory names in the filename. */
  public static final Pattern ALL_DIRECTORIES = Pattern.compile ("^(.*)/[^/]+");


  /* Pass null as targetPattern to get null targets */
  /**
   * Construct an iterator over the given arry of Files
   *
   * The instances constructed from the files are returned in the same order
   * as they appear in the given array
   *
   * @param files  Array of files from which to construct instances
   * @param fileFilter   class implementing interface FileFilter that will decide which names to accept.
   *                     May be null.
   * @param targetPattern  regex Pattern applied to the filename whose first parenthesized group
   *                       on matching is taken to be the target value of the generated instance.
   *                       The pattern is applied to the filename with the matcher.find() method.
   * @param removeCommonPrefix boolean that modifies the behavior of the STARTING_DIRECTORIES 
   *                           pattern, removing the common prefix of all initially specified 
   *                           directories, leaving the remainder of each filename as the target value.
   *
   */
  public FileListIterator(File[] files, FileFilter fileFilter,
      Pattern targetPattern, boolean removeCommonPrefix) 
  {
    this.fileFilter = fileFilter;
    this.fileArray = new ArrayList();
    this.targetPattern = targetPattern;

    fillFileArrayAssignCommonPrefixIndexAndSubIterator(files, removeCommonPrefix);
  }

  public FileListIterator(String[] filenames, FileFilter fileFilter,
      Pattern targetPattern, boolean removeCommonPrefix) 
  {
    this(FileIterator.stringArray2FileArray(filenames), fileFilter, 
        targetPattern, removeCommonPrefix);
  }

  /**
   * Construct a FileListIterator with the file containing the list of files, which
   * contains one filename per line.  
   *
   * The instances constructed from the filelist are returned in the same order
   * as listed
   */
  public FileListIterator(File filelist, FileFilter fileFilter,
      Pattern targetPattern, boolean removeCommonPrefix) throws FileNotFoundException, IOException 
  {
    this.fileFilter = fileFilter;
    this.fileArray = new ArrayList();
    this.targetPattern = targetPattern;

    List filenames = readFileNames (filelist);
    File[] fa = stringList2FileArray (filenames, null);

    fillFileArrayAssignCommonPrefixIndexAndSubIterator(fa, removeCommonPrefix);
  }

  /**
   * Construct a FileListIterator with the file containing the list of files
   *   of RELATIVE pathnames, one filename per line.
   * <p>
   * The instances constructed from the filelist are returned in the same order
   * as listed
   * @param filelist List of relative file names.
   * @param baseDirectory Base directory for relative file names.
   *
   */
  public FileListIterator(File filelist, File baseDirectory, FileFilter fileFilter,
      Pattern targetPattern, boolean removeCommonPrefix) throws FileNotFoundException, IOException
  {
    this.fileFilter = fileFilter;
    this.fileArray = new ArrayList();
    this.targetPattern = targetPattern;

    List filenames = readFileNames (filelist);
    File[] fa = stringList2FileArray (filenames, baseDirectory);

    fillFileArrayAssignCommonPrefixIndexAndSubIterator(fa, removeCommonPrefix);
  }


  private static File[] stringList2FileArray (List filenames, File baseDir)
  {
    File[] fa = new File[filenames.size()];

    for (int i = 0; i < filenames.size(); i++)
      if (baseDir != null) {
        fa[i] = new File (baseDir, (String) filenames.get(i));
      } else {
        fa[i] = new File ((String) filenames.get(i));
      }
    return fa;
  }

  private static List readFileNames (File filelist) throws IOException
  {
    ArrayList filenames = new ArrayList();
    BufferedReader reader = new BufferedReader(new FileReader (filelist));
    String filename = reader.readLine();

    while (filename != null && filename.trim().length() > 0) {
      filenames.add(filename.trim());
      filename = reader.readLine();
    }

    reader.close();
    return filenames;
  }

  public FileListIterator(String filelistName, FileFilter fileFilter,
                          Pattern targetPattern, boolean removeCommonPrefix) throws FileNotFoundException, IOException
  {
    this (new File(filelistName), fileFilter, targetPattern, removeCommonPrefix);
  }

  public FileListIterator(String filelistName, Pattern targetPattern) throws FileNotFoundException, IOException
  {
    this (new File(filelistName), null, targetPattern, true);
  }

  // The PipeInputIterator interface
  public Instance next ()
  {
    File nextFile = (File) subIterator.next();
    String path = nextFile.getParent();
    String targetName = null;

    if (targetPattern == STARTING_DIRECTORIES) {
      targetName = path.substring(commonPrefixIndex);
    } 
    else if (targetPattern != null) {
      Matcher m = targetPattern.matcher(path);
      if (m.find ()){
        targetName = m.group (1);
      }
    }

    return new Instance (nextFile, targetName, nextFile.toURI(), null);
  }

  public File nextFile ()
  {
    return (File) subIterator.next();		
  }

  public boolean hasNext ()	
  {
    return subIterator.hasNext();
  }
  
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}


  public ArrayList getFileArray()
  {
    return fileArray;
  }

  private void fillFileArrayAssignCommonPrefixIndexAndSubIterator(File[] files, boolean removeCommonPrefix)
  {
    ArrayList filenames = new ArrayList();

    for (int i = 0; i < files.length; i++) {
      if (files[i].isDirectory())
        throw new IllegalArgumentException(files[i] + " is not a file.");
      else if (! files[i].exists())
        throw new IllegalArgumentException(files[i] + " does not exist.");

      if (this.fileFilter == null || this.fileFilter.accept(files[i])) {
        this.fileArray.add(files[i]);

        if (removeCommonPrefix)
          filenames.add(files[i].getPath());
      }
    }

    this.subIterator = this.fileArray.iterator();

    if (removeCommonPrefix) { // find the common prefix index of all filenames

      String[] fn = new String[filenames.size()];

      for (int i = 0; i < fn.length; i++)
        fn[i] = (String) filenames.get(i);

      this.commonPrefixIndex = Strings.commonPrefixIndex(fn);
    }
    else 
      this.commonPrefixIndex = 0;


  }

}

