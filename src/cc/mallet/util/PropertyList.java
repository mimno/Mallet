/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.util;

import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;
import java.io.*;

public class PropertyList implements Serializable
{

	protected PropertyList next;
	protected String key;

	public static PropertyList add (String key, Object value,
																	PropertyList rest)
	{
		assert (key != null);
		return new ObjectProperty (key, value, rest);
	}

	public static PropertyList add (String key, String value,
																	PropertyList rest)
	{
		assert (key != null);
		return new ObjectProperty (key, value, rest);
	}
	
	public static PropertyList add (String key, double value,
																	PropertyList rest)
	{
		assert (key != null);
		return new NumericProperty (key, value, rest);
	}

	public static PropertyList remove (String key, 
																		 PropertyList rest)
	{
		assert (key != null);
		return new ObjectProperty (key, null, rest);
	}

	
	public Object lookupObject (String key)
	{
		if (this.key.equals (key)) {
			if (this instanceof ObjectProperty)
				return ((ObjectProperty)this).value;
			else if (this instanceof NumericProperty)
				return new Double(((NumericProperty)this).value);
			else
				throw new IllegalStateException ("Unrecognitized PropertyList entry.");
		} else if (this.next == null) {
			return null;
		} else {
			return next.lookupObject (key);
		}
	}

	public double lookupNumber (String key)
	{
		if (this.key.equals (key)) {
			if (this instanceof NumericProperty)
				return ((NumericProperty)this).value;
			else if (this instanceof ObjectProperty) {
				Object obj = ((ObjectProperty)this).value;
				if (obj == null) return 0;
				// xxx Remove these?  Use might ask for numericIterator expecting to get these (and not!)
				if (obj instanceof Double) return ((Double)obj).doubleValue();
				if (obj instanceof Integer) return ((Double)obj).intValue();
				if (obj instanceof Float) return ((Double)obj).floatValue();
				if (obj instanceof Short) return ((Double)obj).shortValue();
				if (obj instanceof Long) return ((Double)obj).longValue();
				// xxx? throw new IllegalStateException ("Property is not numeric.");
				return 0;
			} else
				throw new IllegalStateException ("Unrecognitized PropertyList entry.");
		} else if (this.next == null) {
			return 0;
		} else {
			return next.lookupNumber (key);
		}
	}

	public boolean hasProperty (String key)
	{
		if (this.key.equals (key)) {
			if (this instanceof ObjectProperty && ((ObjectProperty)this).value == null)
				return false;
			else
				return true;
		} else if (this.next == null) {
			return false;
		} else {
			return next.hasProperty (key);
		}
	}
	
	public Iterator iterator ()
	{
		return new Iterator (this);
	}
	
	public static PropertyList sumDuplicateKeyValues (PropertyList pl ) {
		return sumDuplicateKeyValues(pl,false);
	}

	// culotta 2/02/04: to increment counts of properties values.
	public static PropertyList sumDuplicateKeyValues (PropertyList pl, boolean ignoreZeros) {
		if (!(pl instanceof NumericProperty))
			throw new IllegalArgumentException ("PropertyList must be Numeric to sum values");
		HashMap key2value = new HashMap ();
		Iterator iter = pl.numericIterator();
		while (iter.hasNext()) {
			iter.nextProperty ();
			String key = iter.getKey();
			double val = iter.getNumericValue();
			Double storedValue = (Double)key2value.get (key);
			if (storedValue == null)
				key2value.put (key, new Double (val));
			else // sum stored value with current value
				key2value.put (key, new Double (storedValue.doubleValue() + val));
		}
		PropertyList ret = null;
		java.util.Iterator hashIter = key2value.keySet().iterator();
		while (hashIter.hasNext()) { // create new property list
			String key = (String) hashIter.next();
			double val = ((Double)key2value.get (key)).doubleValue();
			if(ignoreZeros && val==0.0)
				continue;
			ret = PropertyList.add (key, val, ret);
		}
		return ret;
	}
	
	public Iterator numericIterator ()
	{
		return new NumericIterator (this);
	}

	public Iterator objectIterator ()
	{
		return new ObjectIterator (this);
	}
	
	protected PropertyList ()
	{
		throw new IllegalArgumentException ("Zero args constructor not allowed.");
	}
	
	protected PropertyList (String key, PropertyList rest)
	{
		this.key = key;
		this.next = rest;
	}

	public void print ()
	{
		if (this instanceof NumericProperty)
			System.out.println (this.key.toString() + "=" + ((NumericProperty)this).value);
		else if (this instanceof ObjectProperty)
			System.out.println (this.key.toString() + "=" + ((ObjectProperty)this).value);
		else
			throw new IllegalArgumentException ("Unrecognized PropertyList type");
		if (this.next != null)
			this.next.print();
	}

	// Serialization 
	// PropertyList
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(next);
		out.writeObject(key);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		next = (PropertyList) in.readObject();
		key = (String) in.readObject();
	}

  public int size ()
  {
    PropertyList pl = this;
    int size = 1;
    while (pl.next != null) {
      pl = pl.next;
      size++;
    }
    return size;
  }


  private static class NumericProperty extends PropertyList implements Serializable
  {
    protected double value;

    public NumericProperty (String key, double value, PropertyList rest)
    {
      super (key, rest);
      this.value = value;
    }

    // Serialization
    // NumericProperty
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;



    private void writeObject (ObjectOutputStream out) throws IOException {
      out.writeInt(CURRENT_SERIAL_VERSION);
      out.writeDouble(value);
    }

    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
      int version = in.readInt();
      value = in.readDouble();
    }

  }

	private static class ObjectProperty extends PropertyList
	{
		protected Object value;

		public ObjectProperty (String key, Object value, PropertyList rest)
		{
			super (key, rest);
			this.value = value;
		}
		// Serialization 
		// ObjectProperty
		
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;

		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt(CURRENT_SERIAL_VERSION);
			out.writeObject(value);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		  int version = in.readInt();
			value = (Object) in.readObject();
		}

	}

	public class Iterator implements java.util.Iterator, Serializable
	{
		PropertyList property, nextProperty;
		HashSet deletedKeys = null;
		boolean nextCalled = false;
		boolean returnNumeric = true;
		boolean returnObject = true;

		public Iterator (PropertyList pl)
		{
			property = findReturnablePropertyAtOrAfter (pl);
			if (property == null)
				nextProperty = null;
			else
				nextProperty = findReturnablePropertyAtOrAfter (property.next);
		}

		private PropertyList findReturnablePropertyAtOrAfter (PropertyList property)
		{
			while (property != null) {
				if (property instanceof NumericProperty && returnNumeric) {
					if (((NumericProperty)property).value == 0.0) {
						if (deletedKeys == null) deletedKeys = new HashSet();
						deletedKeys.add (property.key);
						property = property.next;
					} else
						break;
				} else if (property instanceof ObjectProperty && returnObject) {
					if (((ObjectProperty)property).value == null) {
						if (deletedKeys == null) deletedKeys = new HashSet();
						deletedKeys.add (property.key);
						property = property.next;
					} else
						break;
				} else
					throw new IllegalStateException ("Unrecognized property type "+property.getClass().getName());
			}
			return property;
		}
		
		public boolean hasNext ()
		{
			return ((nextCalled && nextProperty != null) || (!nextCalled && property != null));
		}

		public boolean isNumeric ()
		{
			return (property instanceof NumericProperty);
		}

		public double getNumericValue ()
		{
			return ((NumericProperty)property).value;
		}

		public Object getObjectValue ()
		{
			return ((ObjectProperty)property).value;
		}
		
		public String getKey ()
		{
			return property.key;
		}
		
		public PropertyList nextProperty ()
		{
			if (nextCalled) {
				property = nextProperty;
				nextProperty = findReturnablePropertyAtOrAfter (property.next);
			} else
				nextCalled = true;
			return property;
		}

		public Object next ()
		{
			return nextProperty ();
		}
		
		public void remove ()
		{
			throw new UnsupportedOperationException ();
		}
		
			// Serialization 
		// Iterator
	
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;
		
		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject(property);
			out.writeObject(nextProperty);
			out.writeObject(deletedKeys);
			out.writeBoolean(nextCalled);
			out.writeBoolean(returnNumeric);
			out.writeBoolean(returnObject);
		}
		
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			property = (PropertyList) in.readObject();
			nextProperty = (PropertyList) in.readObject();
			deletedKeys = (HashSet) in.readObject();
			nextCalled = in.readBoolean();
			returnNumeric = in.readBoolean();
			returnObject = in.readBoolean();
		}

	}


	public class NumericIterator extends Iterator implements Serializable
	{
		public NumericIterator (PropertyList pl) {
			super (pl);
			this.returnObject = false;
		}
	}

	public class ObjectIterator extends Iterator implements Serializable
	{
		public ObjectIterator (PropertyList pl) {
			super (pl);
			this.returnNumeric = false;
		}
	}

    // for fast merging of PropertLists
    //  gmann 8/14/6
    public PropertyList last(){
	if (next == null){
	    return this;
	}
	else return next.last();
    }

    public PropertyList append(PropertyList nextPl) throws UnsupportedOperationException{
	if (this.next != null){
	    throw new UnsupportedOperationException("PropertyList.java: Cannot append to middle of a list\n");
	}
	
	this.next = nextPl;
	return last();
    }
	
}
