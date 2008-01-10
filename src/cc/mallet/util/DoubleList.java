/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
	 A dynamically growable list of doubles.
	 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.util;

import java.util.Arrays;
import java.io.*;

public class DoubleList implements Serializable
{
	double[] data;
	int size;

	public DoubleList ()
	{
		this (2);
	}

	// Creates a list of zero size
	public DoubleList (int capacity)
	{
		if (capacity < 2)
			capacity = 2;
		this.data = new double[capacity];
		this.size = 0;
	}

	public DoubleList (int size, double fillValue)
	{
		int capacity = size;
		if (capacity < 2)
			capacity = 2;
		this.data = new double[capacity];
		Arrays.fill (this.data, fillValue);
		this.size = size;
	}
	
	public DoubleList (double[] initialValues, int size)
	{
		this.data = new double[initialValues.length];
		System.arraycopy (initialValues, 0, this.data, 0, initialValues.length);
		this.size = size;
	}

	public DoubleList (double[] initialValues)
	{
		this (initialValues, initialValues.length);
	}

	public DoubleList cloneDoubleList ()
	{
		return new DoubleList (data, size);
	}

	public Object clone ()
	{
		return cloneDoubleList ();
	}

	private void growIfNecessary (int index)
	{
		int newDataLength = data.length;
		while (index >= newDataLength) {
			if (newDataLength < 100)
				newDataLength *= 2;
			else
				newDataLength = (newDataLength * 3) / 2;
		}
		if (newDataLength != data.length) {
			double[] newData = new double[newDataLength];
			System.arraycopy (data, 0, newData, 0, data.length);
			data = newData;
		}
	}

	public void add (double value)
	{
		growIfNecessary (size);
		data[size++] = value;
	}

	public double get (int index)
	{
		if (index >= size)
			throw new IllegalArgumentException ("Index "+index+" out of bounds; size="+size);
		return data[index];
	}

	public void set (int index, double value)
	{
		growIfNecessary (index);
		data[index] = value;
		if (index >= size)
			size = index+1;
	}

	// Serialization 
		
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		int size = data.length;
		out.writeInt(size);
		for (int i=1; i<size; i++) {
			out.writeDouble(data[i]);
		}
		out.writeInt(this.size);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		int len = in.readInt();
		data = new double[len];
		for (int i = 1; i<len; i++) {
			data[i] = in.readDouble();
		}
		size = in.readInt();
	}

	
}
