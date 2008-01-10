/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import java.io.*;
import java.util.regex.*;
import java.util.HashMap;
import gnu.trove.TObjectIntHashMap;
import java.util.Set;
import java.util.Iterator;

// xxx A not very space-efficient version.  I'll compress it later.

public class StringEditFeatureVectorSequence extends FeatureVectorSequence implements Serializable
{
  private int string1Length, string2Length;
  private String string1, string2;
  private String[] string1Blocks, string2Blocks;
  private TObjectIntHashMap string1Present, string2Present;
  private TObjectIntHashMap lexicon;
  private int[] block1Indices, block2Indices;
  private char delim = ':';
  private static final char defaultDelimiter = ':';

  public StringEditFeatureVectorSequence (FeatureVector[] featureVectors, String s1, String s2)
  {
    this (featureVectors, s1, s2, defaultDelimiter);
  }

  public StringEditFeatureVectorSequence(FeatureVector[] featureVectors, String s1, String s2, char delimiter)
  {
    this (featureVectors, s1, s2, delimiter, null);
  }

  public StringEditFeatureVectorSequence(FeatureVector[] featureVectors, String s1, String s2, HashMap lexic)
  {
    this (featureVectors, s1, s2, defaultDelimiter, lexic);
  }

  public StringEditFeatureVectorSequence(FeatureVector[] featureVectors, String s1, String s2, char delimiter, HashMap lexic)
  {
    super (featureVectors);
    this.delim = delimiter;
    
    this.lexicon = new TObjectIntHashMap();
    if (lexic != null) {
      Set keys = lexic.keySet();
      java.util.Iterator iter = keys.iterator();
      while (iter.hasNext())
        this.lexicon.put((String) iter.next(), 1);
    }

    this.string1 = s1;
    this.string2 = s2;
    this.string1Length = s1.length() + 2;
    this.string2Length = s2.length() + 2;
    string1Blocks = string1.split("" + delim);
    string2Blocks = string2.split("" + delim);
    string1Present = new TObjectIntHashMap();
    string2Present = new TObjectIntHashMap();
    block1Indices = new int[string1Length];
    if (string1Blocks.length > 0) {
      int whichBlock = 0;
      block1Indices[0] = whichBlock++;
      for (int i = 0; i < string1Blocks.length; i++)
        string1Present.put(string1Blocks[i], 1);
      for (int i = 1; i < string1Length-1; i++)
        block1Indices[i] = ((string1.charAt(i-1) == delim) ? whichBlock++ : -1);
      block1Indices[string1Length-1] = -1;
    }
    block2Indices = new int[string2Length];
    if (string2Blocks.length > 0) {
      int whichBlock = 0;
      block2Indices[0] = whichBlock++;
      for (int i = 0; i < string2Blocks.length; i++)
        string2Present.put(string2Blocks[i], 1);
      for (int i = 1; i < string2Length - 1; i++)
        block2Indices[i] = ((string2.charAt(i-1) == delim) ? whichBlock++ : -1);
      block2Indices[string2Length-1] = -1;
    }
  }
 
  public String getString1() {
    return string1;
  }

  public String getString2() {
    return string2;
  }

  public int getString1Length () {
    return string1Length;
  }

  public int getString2Length () {
    return string2Length;
  }

  // End of Block
  public int getString1EOBIndex(String delimiter) {
    return getString1EOBIndex(delimiter, 0);
  }

  public int getString1EOBIndex(String delimiter, int start) {
    return getString1IndexOf(delimiter, start);
  }

  public String getString1BlockAtIndex(int idx) {
    if (idx < 0 || idx >= block1Indices.length || block1Indices[idx] < 0 || block1Indices[idx] >= string1Blocks.length) return null;
    else return string1Blocks[block1Indices[idx]];
  }

  public int getString1IndexOf(String str, int start) {
    int toret = string1.indexOf(str, start);
  
    if (toret == -1)
      toret = string1.length() - 1 - start;
    else
      toret = toret - start;

    if (toret < 1)
      return -1;

    return toret;
  }

  public boolean isPresent1(String patternStr) {
    Pattern p = Pattern.compile(patternStr);
    Matcher m = p.matcher(string1);
    boolean b = m.matches();

    return b;
  }

  public boolean isPresentInString1(String str) {
    return string1Present.containsKey(str);
  }

  public char getString1Char(int index) {
    index = index - 1;
    if (index < 0 || index >= string1.length()) return (char) 0;
    else return string1.charAt(index);
  }

  public int getString2EOBIndex(String delimiter) {
    return getString2EOBIndex(delimiter, 0);
  }

  public int getString2EOBIndex(String delimiter, int start) {
    return getString2IndexOf(delimiter, start);
  }

  public String getString2BlockAtIndex(int idx) {
    if (idx < 0 || idx >= block2Indices.length || block2Indices[idx] < 0 || block2Indices[idx] >= string2Blocks.length) return null;
    else return string2Blocks[block2Indices[idx]];
  }

  public boolean isPresentInString2(String str) {
    return string2Present.containsKey(str);
  }

  public int getString2IndexOf(String str, int start) {
    int toret = string2.indexOf(str, start);
  
    if (toret == -1)
      toret = string2.length() - 1 - start;
    else
      toret = toret - start;

    if (toret < 1)
      return -1;

    return toret;
  }

  public boolean isPresent2(String patternStr) {
    Pattern p = Pattern.compile(patternStr);
    Matcher m = p.matcher(string2);
    boolean b = m.matches();

    return b;
  }

  public char getString2Char(int index) {
    index = index - 1;
    if (index < 0 || index >= string2.length()) return (char) 0;
    else return string2.charAt(index);
  }

  public boolean isInLexicon(String str) {
    if (lexicon == null || str == null) return false;

    return lexicon.containsKey(str);
  }

  public String toString ()
  {
    StringBuffer sb = new StringBuffer ();
    sb.append (super.toString());
    sb.append ('\n');
    sb.append ("String 1: " + string1Length + " String 2: " + string2Length);

    return sb.toString();
  }

  // Serialization of Instance

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 0;
  private static final int NULL_INTEGER = -1;

  private void writeObject (ObjectOutputStream out) throws IOException {
    out.writeInt (CURRENT_SERIAL_VERSION);
    out.writeInt (string1Length);
    out.writeInt (string2Length);
    out.writeObject (string1);
    out.writeObject (string2);

    if (string1Blocks == null) {
      out.writeInt(NULL_INTEGER);
    }
    else {
      int size = string1Blocks.length;
      out.writeInt(size);
      for(int i=0; i<size; i++) {
        out.writeObject(string1Blocks[i]);
      }
    }

    if (string2Blocks == null) {
      out.writeInt(NULL_INTEGER);
    }
    else {
      int size = string2Blocks.length;
      out.writeInt(size);
      for(int i=0; i<size; i++) {
        out.writeObject(string2Blocks[i]);
      }
    }

    out.writeObject(string1Present); 
    out.writeObject(string2Present); 
    out.writeObject(lexicon); 

    if (block1Indices == null) {
      out.writeInt(NULL_INTEGER);
    }
    else {
      int size = block1Indices.length;
      out.writeInt(size);
      for (int i=0; i<size; i++) {
        out.writeInt(block1Indices[i]);
      }
    }

    if (block2Indices == null) {
      out.writeInt(NULL_INTEGER);
    }
    else {
      int size = block2Indices.length;
      out.writeInt(size);
      for (int i=0; i<size; i++) {
        out.writeInt(block2Indices[i]);
      }
    }

    out.writeChar(delim);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt ();
    int string1Length = in.readInt();
    int string2Length = in.readInt();
    String string1 = (String) in.readObject();
    String string2 = (String) in.readObject();
    int size = in.readInt();
    if (size == NULL_INTEGER) {
      string1Blocks = null;
    }
    else {
      string1Blocks = new String[size];
      for (int i = 0; i<size; i++) {
        string1Blocks[i] = (String) in.readObject();
      }
    }

    size = in.readInt();
    if (size == NULL_INTEGER) {
      string2Blocks = null;
    }
    else {
      string2Blocks = new String[size];
      for (int i = 0; i<size; i++) {
        string2Blocks[i] = (String) in.readObject();
      }
    }

    TObjectIntHashMap string1Present = (TObjectIntHashMap) in.readObject();
    TObjectIntHashMap string2Present = (TObjectIntHashMap) in.readObject();
    TObjectIntHashMap lexicon = (TObjectIntHashMap) in.readObject();

    size = in.readInt();
    if (size == NULL_INTEGER) {
      block1Indices = null;
    }
    else {
      block1Indices = new int[size];
      for (int i = 0; i<size; i++) {
        block1Indices[i] = in.readInt();
      }
    }

    size = in.readInt();
    if (size == NULL_INTEGER) {
      block2Indices = null;
    }
    else {
      block2Indices = new int[size];
      for (int i = 0; i<size; i++) {
        block2Indices[i] = in.readInt();
      }
    }

    delim = in.readChar();
  }
}
