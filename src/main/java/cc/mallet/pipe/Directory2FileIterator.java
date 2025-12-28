/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URI;
import java.util.Iterator;
import java.util.ArrayList;

import cc.mallet.pipe.iterator.*;
import cc.mallet.types.Instance;
import cc.mallet.util.RegexFileFilter;
/**
 * Convert a File object representing a directory into a FileIterator which
 * iterates over files in the directory matching a pattern and which extracts
 * a label from each file path to become the target field of the instance.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Directory2FileIterator extends Pipe
{
    FileFilter fileFilter = null;
    Pattern labelPattern = null;

    public Directory2FileIterator (FileFilter fileFilter, Pattern labelRegex)
    {
        this.fileFilter = fileFilter;
        this.labelPattern = labelRegex;
    }
        
    public Directory2FileIterator (Pattern absolutePathRegex,
                                                                 Pattern filenameRegex,
                                                                 Pattern labelRegex)
    {
        this (new RegexFileFilter (absolutePathRegex, filenameRegex), labelRegex);
    }

    public Directory2FileIterator (String filenameRegex)
    {
        this (new RegexFileFilter (filenameRegex), null);
    }

    public Directory2FileIterator ()
    {
        // Leave fileFilter == null
    }

    @Override public Instance pipe (Instance carrier)
    {
        File directory = (File) carrier.getData();
        carrier.setData(new FileIterator (directory, fileFilter, labelPattern));
        return carrier;
    }
    
    public Iterator pipe (File directory)
    {
        return new FileIterator (directory, fileFilter, labelPattern);
    }

    public Iterator pipe (URI directory)
    {
        return pipe (new File (directory));
    }

    public Iterator pipe (String directory)
    {
        return pipe (new File (directory));
    }

    


}
