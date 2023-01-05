package cc.mallet.pipe.iterator;

import cc.mallet.types.Instance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.Scanner;
/**
*  An iterator that iterates through a directory and returns
 *  one instance per file. <p>
 */

public class TextFileIterator implements Iterator<Instance>
{
  File[] fileList;
  int currentFile;



  /**
   * Defaults constructor
   * @param path  the path of the file (s)
   * @param filter  filename ending
   */
  public TextFileIterator(String path, String filter){
    try {
      fileIterator(path, filter);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }


  /**
   * A private file iterator that creates an array containing all files at the given directory path.
   * @param path  The directory path.
   * @throws FileNotFoundException  if the path is not a directory.
   */
  private void fileIterator(String path, String langCode) throws FileNotFoundException {
    if (langCode.equals(null))
      langCode = "";
    if (fileList == null) {
      File dir = new File(path);
      if (dir.isDirectory()) {
        final String finalLangCode = langCode;
        fileList = dir.listFiles(new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            return name.endsWith(finalLangCode +".txt");
          }
        });
      }
      else {
        throw new IllegalStateException("The given path is not a directory.");
      }
    }
  }

  /**
   * A private file reader that reads through the current file.
   * @return  The content of the file read.
   * @throws FileNotFoundException  if the file is not found.
   */
  private String readFile() throws FileNotFoundException {
    Scanner scanner = new Scanner(fileList[currentFile], "UTF-8");
    String text = "";
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine().trim();
      if(line.length()> 2)
        text += line;
    }
    scanner.close();
    return text;
  }

  /**
   * Next instance
   * @return  The next instance (next file)
   */
  public Instance next(){
    String data = null;
    try {
      data = readFile();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    String name = fileList[currentFile].getName();

    int underScorePos = 0;
    if(name.contains("_"))
      underScorePos = name.indexOf("_");
    String target = name.substring(underScorePos+1, name.indexOf("."));
    String uri = name.substring(0, underScorePos);
    currentFile++;
    if (data == null){
      throw new IllegalStateException("The iterated file contained no data.");
    }
    return new Instance(data, target, uri, null);
  }

  /**
   * @return true if there are more files in the iterator.
   */
  public boolean hasNext ()	{ return fileList.length > currentFile;	}

  public void remove () {
    throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
  }

}
