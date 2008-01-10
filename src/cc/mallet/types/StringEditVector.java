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

import java.util.logging.*;
import java.util.StringTokenizer;
import java.util.Vector;
import java.io.*;

import cc.mallet.util.MalletLogger;

public class StringEditVector implements Serializable
{
  private static Logger logger = MalletLogger.getLogger(StringEditVector.class.getName());
  String _delimiter;
  String _string1 = null, _string2 = null;
  int _match = -2;
  
  public static final int MATCH = 1;
  public static final int NONMATCH = 0;

  public StringEditVector(String delimiter) {
    if (delimiter == null || delimiter.equals("")) 
      _delimiter = " ";
    else
      _delimiter = delimiter;
  }
  
  public StringEditVector() {
  	this (" ");
  }

  public String formatString() {
    return "<String1>" + _delimiter + "<String2>" + _delimiter + "<BooleanMatch>";
  }

  public boolean parseString(String line) {
    StringTokenizer stok = new StringTokenizer(line, _delimiter);
    boolean success = true;

    // First String
    if (stok.hasMoreTokens()) _string1 = stok.nextToken();
    else success = false;

    // Second String
    if (stok.hasMoreTokens()) _string2 = stok.nextToken();
    else success = false;

    // Match/non-Match
    if (stok.hasMoreTokens()) 
      try {
        _match = Integer.parseInt(stok.nextToken());
      }
    catch (Exception e) {
      logger.info ("Error while returning third integer - " + e.getMessage());
      _match = -1;
      success = false;
    }
    else success = false;

    return success;
  }

  public void setFirstString(String s1) {
  	_string1 = s1;
  }
  
  public String getFirstString() {
    return _string1;
  }

  public char getFirstStringChar(int index) {
    index = index - 1;
    if (index < 0 || index >= _string1.length()) return (char) 0;
    else return _string1.charAt(index);
  }

  public int getLengthFirstString() {
    return _string1.length();
  }

  public void setSecondString(String s2) {
  	_string2 = s2;  	
  }
  
  public String getSecondString() {
    return _string2;
  }

  public char getSecondStringChar(int index) {
    index = index - 1;
    if (index < 0 || index >= _string2.length()) return (char) 0;
    else return _string2.charAt(index);
  }

  public int getLengthSecondString() {
    return _string2.length();
  }

  public void setMatch(int match) {
  	_match = match;
  }
  
  public int getMatch() {
    return _match;
  }

  //Serialization

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 0;

  private void writeObject (ObjectOutputStream out) throws IOException {
    out.writeInt (CURRENT_SERIAL_VERSION);
    out.writeObject (_delimiter);
    out.writeObject (_string1);
    out.writeObject (_string2);
    out.writeInt (_match);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt ();
    _delimiter = (String) in.readObject();
    _string1 = (String) in.readObject();
    _string2 = (String) in.readObject();
    _match = in.readInt();
  }
}
