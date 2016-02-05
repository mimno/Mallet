/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.util;

import java.io.*;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;



/**
 * Contains static utilities for manipulating files.
 *
 * Created: Thu Nov 20 15:14:16 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: FileUtils.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class FileUtils {

  private FileUtils() {} // All static methods


  /**
   * Serializes an object to a file, masking out annoying exceptions.
   *  Any IO exceptions are caught, and printed to standard error.
   *  Consider using {@link #writeGzippedObject(java.io.File, java.io.Serializable)}
   *  instead, for that method will compress the serialized file, and it'll still
   * be reaoable by {@link #readObject}.
   * @param f File to write to
   * @param obj Object to serialize
   * @see #writeGzippedObject(java.io.File, java.io.Serializable)
   */
	public static void writeObject (File f, Serializable obj)
  {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
			oos.writeObject(obj);
			oos.close();
		}
		catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}

  /**
   * Reads a Serialized object, which may or may not be zipped.
   *   Guesses from the file name whether to decompress or not.
   * @param f File to read data from
   * @return A deserialized object.
   */
  public static Object readObject (File f)
  {
    String fname = f.getName ();
    if (fname.endsWith (".gz")) {
      return readGzippedObject (f);
    } else {
      return readUnzippedObject (f);
    }
  }

    /**
   * Reads a serialized object from a file.
   *  You probably want to use {@link #readObject} instead, because that method will automatically guess
   *  from the extension whether the file is compressed, and call this method if necessary.
   * @param f File to read object from
   * @see #readObject
   */
	public static Object readUnzippedObject (File f)
  {
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
			Object obj = ois.readObject();
			ois.close ();
			return obj;
		}
		catch (IOException e) {
			throw new RuntimeException (e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException (e);
		}
	}

	/**
	 * Reads every line from a given text file.
	 * @param f Input file.
	 * @return String[] Array containing each line in <code>f</code>.
	 */
  public static String[] readFile (File f) throws IOException
  {
    BufferedReader in = new BufferedReader(new FileReader (f));
    ArrayList list = new ArrayList ();

    String line;
    while ((line = in.readLine()) != null)
      list.add (line);

    return (String[]) list.toArray(new String[0]);
  }

	/**
	 * Creates a file, making sure that its name is unique.
   *  The file will be created as if by <tt>new File(dir, prefix+i+extension)</tt>,
   *  where i is an integer chosen such that the returned File does not exist.
   * @param dir Directory to use for the returned file
   * @param prefix Prefix of the file name (before the uniquifying integer)
   * @param extension Suffix of the file name (after the uniquifying integer)
	 */
	public static File uniqueFile (File dir, String prefix, String extension)
		throws IOException
	{
		File f = null;
		int i = 0;
		boolean wasCreated = false;
		while (!wasCreated) {
			if (dir != null) {
				f = new File (dir, prefix+i+extension);
			} else {
				f = new File (prefix+i+extension);
			}
			wasCreated = f.createNewFile ();
			i++;
		}
		return f;
	}


  /**
   * Writes a serialized version of obj to a given file, compressing it using gzip.
   * @param f File to write to
   * @param obj Object to serialize
   */
  public static void writeGzippedObject (File f, Serializable obj)
  {
    try {
      ObjectOutputStream oos = new ObjectOutputStream (new BufferedOutputStream (new GZIPOutputStream (new FileOutputStream(f))));
      oos.writeObject(obj);
      oos.close();
    }
    catch (IOException e) {
      System.err.println("Exception writing file " + f + ": " + e);
    }
  }


  /**
   * Reads a serialized object from a file that has been compressed using gzip.
   *  You probably want to use {@link #readObject} instead, because it will automatically guess
   *  from the extension whether the file is compressed, and call this method if necessary.
   * @param f Compressed file to read object from
   * @see #readObject
   */
  public static Object readGzippedObject (File f)
  {
    try {
      ObjectInputStream ois = new ObjectInputStream (new BufferedInputStream (new GZIPInputStream (new FileInputStream(f))));
      Object obj = ois.readObject();
      ois.close ();
      return obj;
    }
    catch (IOException e) {
      throw new RuntimeException (e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException (e);
    }
  }

} // FileUtils
