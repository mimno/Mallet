package cc.mallet.pipe.iterator;

import cc.mallet.types.Instance;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Scanner;

/**
 *  An iterator that iterates through a directory and returns
 *  one instance per file. <p>
 */
public class TextFileIterator implements Iterator<Instance>
{
  String category;
  File[] fileList;
  int currentFile;

  /**
   * The default constructor.
   * @param path  The directory path to iterate through.
   * @param category  The category given to all files in the given directory.
   */
  public TextFileIterator(String path, String category){
    this.category = category;
    currentFile = 0;
    if (category == null)
      throw new IllegalStateException ("You must provide a category field.");
    try {
      fileIterator(path);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  /**
   * A private file iterator that creates an array containing all files at the given directory path.
   * @param path  The directory path.
   * @throws FileNotFoundException  if the path is not a directory.
   */
  private void fileIterator(String path) throws FileNotFoundException {
    if (fileList == null) {
      File dir = new File(path);
      if (dir.isDirectory())
        fileList = dir.listFiles();
      else
        throw new IllegalStateException("The given path is not a directory.");
    }
  }

  /**
   * A private file reder that reads through the current file.
   * @return  The content of the file read.
   * @throws FileNotFoundException  if the file is not found.
   */
  private String readFile() throws FileNotFoundException {
    Scanner scanner = new Scanner(fileList[currentFile]);
    String text = "";
    while (scanner.hasNextLine())
      text += scanner.nextLine();
    return text;
  }

  /**
   * Next instance
   * @return  The next instance (next file)
   */
  public Instance next(){
    String text = null;
    try {
      text = readFile();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    currentFile++;
    if (text == null){
      throw new IllegalStateException("The iterated file contained no data.");
    }
    return new Instance(text, category, null ,null);
  }

  /**
   * @return true if there are more files in the iterator.
   */
  public boolean hasNext ()	{
    return fileList.length > currentFile;	}

  public void remove () {
    throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
  }

}
