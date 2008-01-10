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

import java.util.ArrayList;
import java.io.*;
import java.util.Iterator;
import java.util.HashMap;
import java.rmi.dgc.VMID;

/**
 *  A mapping between integers and objects where the mapping in each
 * direction is efficient.  Integers are assigned consecutively, starting
 * at zero, as objects are added to the Alphabet.  Objects can not be
 * deleted from the Alphabet and thus the integers are never reused.
 * <p>
 * The most common use of an alphabet is as a dictionary of feature names
 * associated with a {@link cc.mallet.types.FeatureVector} in an
 * {@link cc.mallet.types.Instance}. In a simple document
 * classification usage,
 * each unique word in a document would be a unique entry in the Alphabet
 * with a unique integer associated with it.   FeatureVectors rely on
 * the integer part of the mapping to efficiently represent the subset of
 * the Alphabet present in the FeatureVector.
 * @see FeatureVector
 * @see Instance
 * @see cc.mallet.pipe.Pipe
 */
public class Alphabet implements Serializable
{
	gnu.trove.TObjectIntHashMap map;
	ArrayList entries;
	boolean growthStopped = false;
	Class entryClass = null;
	VMID instanceId = new VMID();  //used in readResolve to identify persitent instances

	public Alphabet (int capacity, Class entryClass)
	{
		this.map = new gnu.trove.TObjectIntHashMap (capacity);
		this.entries = new ArrayList (capacity);
		this.entryClass = entryClass;
		// someone could try to deserialize us into this image (e.g., by RMI).  Handle this.
		deserializedEntries.put (instanceId, this);
	}

	public Alphabet (Class entryClass)
	{
		this (8, entryClass);
	}

	public Alphabet (int capacity)
	{
		this (capacity, null);
	}

	public Alphabet ()
	{
		this (8, null);
	}

	public Object clone ()
	{
		//try {
		// Wastes effort, because we over-write ivars we create
		Alphabet ret = new Alphabet ();
		ret.map = (gnu.trove.TObjectIntHashMap) map.clone();
		ret.entries = (ArrayList) entries.clone();
		ret.growthStopped = growthStopped;
		ret.entryClass = entryClass;
		return ret;
		//} catch (CloneNotSupportedException e) {
		//e.printStackTrace();
		//throw new IllegalStateException ("Couldn't clone InstanceList Vocabuary");
		//}
	}

	/** Return -1 if entry isn't present. */
	public int lookupIndex (Object entry, boolean addIfNotPresent)
	{
		if (entry == null)
			throw new IllegalArgumentException ("Can't lookup \"null\" in an Alphabet.");
		if (entryClass == null)
			entryClass = entry.getClass();
		else
			// Insist that all entries in the Alphabet are of the same
			// class.  This may not be strictly necessary, but will catch a
			// bunch of easily-made errors.
			if (entry.getClass() != entryClass)
				throw new IllegalArgumentException ("Non-matching entry class, "+entry.getClass()+", was "+entryClass);

		int retIndex = -1;
		if (map.containsKey( entry )) {
			retIndex = map.get( entry );
		}
		else if (!growthStopped && addIfNotPresent) {
			retIndex = entries.size();
			map.put (entry, retIndex);
			entries.add (entry);
		}
		return retIndex;
	}

	public int lookupIndex (Object entry)
	{
		return lookupIndex (entry, true);
	}

	public Object lookupObject (int index)
	{
		return entries.get(index);
	}

	public Object[] toArray () {
		return entries.toArray();
	}

	/**
	 * Returns an array containing all the entries in the Alphabet.
	 *  The runtime type of the returned array is the runtime type of in.
	 *  If in is large enough to hold everything in the alphabet, then it
	 *  it used.  The returned array is such that for all entries <tt>obj</tt>,
	 *  <tt>ret[lookupIndex(obj)] = obj</tt> .
	 */ 
	public Object[] toArray (Object[] in) {
		return entries.toArray (in);
	}

	// xxx This should disable the iterator's remove method...
	public Iterator iterator () {
		return entries.iterator();
	}

	public Object[] lookupObjects (int[] indices)
	{
		Object[] ret = new Object[indices.length];
		for (int i = 0; i < indices.length; i++)
			ret[i] = entries.get(indices[i]);
		return ret;
	}

	/**
	 * Returns an array of the objects corresponding to
	 * @param indices An array of indices to look up
	 * @param buf An array to store the returned objects in.
	 * @return An array of values from this Alphabet.  The runtime type of the array is the same as buf
	 */
	public Object[] lookupObjects (int[] indices, Object[] buf)
	{
		for (int i = 0; i < indices.length; i++)
			buf[i] = entries.get(indices[i]);
		return buf;
	}

	public int[] lookupIndices (Object[] objects, boolean addIfNotPresent)
	{
		int[] ret = new int[objects.length];
		for (int i = 0; i < objects.length; i++)
			ret[i] = lookupIndex (objects[i], addIfNotPresent);
		return ret;
	}

	public boolean contains (Object entry)
	{
		return map.contains (entry);
	}

	public int size ()
	{
		return entries.size();
	}

	public void stopGrowth ()
	{
		growthStopped = true;
	}

	public void startGrowth ()
	{
		growthStopped = false;
	}

	public boolean growthStopped ()
	{
		return growthStopped;
	}

	public Class entryClass ()
	{
		return entryClass;
	}

	/** Return String representation of all Alphabet entries, each
	separated by a newline. */
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < entries.size(); i++) {
			sb.append (entries.get(i).toString());
			sb.append ('\n');
		}
		return sb.toString();
	}

	public void dump () { dump (System.out); }

	public void dump (PrintStream out)
	{
		dump (new PrintWriter (new OutputStreamWriter (out), true));
	}

	public void dump (PrintWriter out)
	{
		for (int i = 0; i < entries.size(); i++) {
			out.println (i+" => "+entries.get (i));
		}
	}

	/** Convenience method that can often implement alphabetsMatch in classes that implement the AlphabetsCarrying interface. */
	public static boolean alphabetsMatch (AlphabetCarrying object1, AlphabetCarrying object2) {
		Alphabet[] a1 = object1.getAlphabets();
		Alphabet[] a2 = object2.getAlphabets();
		if (a1.length != a2.length) return false;
		for (int i = 0; i < a1.length; i++) {
			if (a1[i] == a2[i]) continue;
			if (a1[i] == null || a2[i] == null) return false;  // One is null, but the other isn't
			if (! a1[i].equals(a2[i])) return false;
		}
		return true;
	}

	public VMID getInstanceId() { return instanceId;} // for debugging
	public void setInstanceId(VMID id) { this.instanceId = id; }
	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeInt (entries.size());
		for (int i = 0; i < entries.size(); i++)
			out.writeObject (entries.get(i));
		out.writeBoolean (growthStopped);
		out.writeObject (entryClass);
		out.writeObject(instanceId);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		int size = in.readInt();
		entries = new ArrayList (size);
		map = new gnu.trove.TObjectIntHashMap (size);
		for (int i = 0; i < size; i++) {
			Object o = in.readObject();
			map.put (o, i);
			entries. add (o);
		}
		growthStopped = in.readBoolean();
		entryClass = (Class) in.readObject();
		if (version >0 ){ // instanced id added in version 1S
			instanceId = (VMID) in.readObject();
		}
	}

	private transient static HashMap deserializedEntries = new HashMap();
	/**
	 * This gets called after readObject; it lets the object decide whether
	 * to return itself or return a previously read in version.
	 * We use a hashMap of instanceIds to determine if we have already read
	 * in this object.
	 * @return
	 * @throws ObjectStreamException
	 */

	public Object readResolve() throws ObjectStreamException {
		Object previous = deserializedEntries.get(instanceId);
		if (previous != null){
			//System.out.println(" ***Alphabet ReadResolve:Resolving to previous instance. instance id= " + instanceId);
			return previous;
		}
		if (instanceId != null){
			deserializedEntries.put(instanceId, this);
		}
		//System.out.println(" *** Alphabet ReadResolve: new instance. instance id= " + instanceId);
		return this;
	}
}
