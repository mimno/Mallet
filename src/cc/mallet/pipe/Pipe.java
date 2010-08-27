/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.util.logging.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.io.*;
import java.rmi.dgc.VMID;

import cc.mallet.types.Alphabet;
import cc.mallet.types.AlphabetCarrying;
import cc.mallet.types.Instance;
import cc.mallet.types.SingleInstanceIterator;
import cc.mallet.util.MalletLogger;
/**
	The abstract superclass of all Pipes, which transform one data type to another.
	Pipes are most often used for feature extraction.
	<p>
	Although Pipe does not have any "abstract methods", in order to use a Pipe subclass
	you must override either the {@link pipe} method or the {@link newIteratorFrom} method.
	The former is appropriate when the pipe's processing of an Instance is strictly
	one-to-one.  For every Instance coming in, there is exactly one Instance coming out.
	The later is appropriate when the pipe's processing may result in more or fewer
	Instances than arrive through its source iterator.
  <p>	
	A pipe operates on an {@link cc.mallet.types.Instance}, which is a carrier of data.
	A pipe reads from and writes to fields in the Instance when it is requested
	to process the instance. It is up to the pipe which fields in the Instance it
	reads from and writes to, but usually a pipe will read its input from and write
	its output to the "data" field of an instance.
    <p>
    A pipe doesn't have any direct notion of input or output - it merely modifies instances
    that are handed to it.  A set of helper classes, which implement the interface {@link Iterator<Instance>},
    iterate over commonly encountered input data structures and feed the elements of these
    data structures to a pipe as instances.
    <p>
    A pipe is frequently used in conjunction with an {@link cc.mallet.types.InstanceList}  As instances are added
	to the list, they are processed by the pipe associated with the instance list and
	the processed Instance is kept in the list.
    <p>
    In one common usage, a {@link cc.mallet.pipe.iterator.FileIterator} is given a list of directories to operate over.
	The FileIterator walks through each directory, creating an instance for each
	file and putting the data from the file in the data field of the instance.
	The directory of the file is stored in the target field of the instance.  The
    FileIterator feeds instances to an InstanceList, which processes the instances through
    its associated pipe and keeps the results.
	<p>
    Pipes can be hierachically composed. In a typical usage, a SerialPipe is created, which
    holds other pipes in an ordered list. Piping
	an instance through a SerialPipe means piping the instance through each of the child pipes
	in sequence.
    <p>
    A pipe holds two separate Alphabets: one for the symbols (feature names)
    encountered in the data fields of the instances processed through the pipe,
    and one for the symbols (e.g. class labels) encountered in the target fields.
    <p>

 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public abstract class Pipe implements Serializable, AlphabetCarrying
{
	private static Logger logger = MalletLogger.getLogger(Pipe.class.getName());

	Alphabet dataAlphabet = null;
	Alphabet targetAlphabet = null;

	boolean dataAlphabetResolved = false;
	boolean targetAlphabetResolved = false;
	boolean targetProcessing = true;

	VMID instanceId = new VMID();  //used in readResolve to distinguish persistent instances

	/** Construct a pipe with no data and target dictionaries
	 */
	public Pipe ()
	{
		this (null, null);
	}

	/**
	 * Construct pipe with data and target dictionaries.
	 * Note that, since the default values of the dataDictClass and targetDictClass are null,
	 * that if you specify null for one of the arguments here, this pipe step will not
	 * ever create 	any corresponding dictionary for the argument.
	 *  @param dataDict  Alphabet that will be used as the data dictionary.
	 *  @param targetDict Alphabet that will be used as the target dictionary.
	 */
	public Pipe (Alphabet dataDict, Alphabet targetDict)
	{
		this.dataAlphabet = dataDict;
		this.targetAlphabet = targetDict;
	}

	/** Each instance processed is tested by this method.  
	 * If it returns true, then the instance by-passes processing by this Pipe. 
	 * Common usage is to override this method in an anonymous inner sub-class of Pipe. 
	 * <code>
	  		SerialPipes sp = new SerialPipes (new Pipe[] {
				new CharSequence2TokenSequence() {
					public boolean precondition (Instance inst) { return inst instanceof CharSequence; }
				},
				new TokenSequence2FeatureSequence(), 
		});
	 * </code> */
	// TODO "precondition" doesn't seem like the best name for this because if false, we don't fail, we pass thru.  
	// Consider alternatives: skipIfTrue, passThru, skipPredicate,
	// TODO Actually, we might really want multiple different methods like this: 
	//  (a) if false, drop this instance and go on to next, (b) if false, pass through unchanged (current implementation)
	public boolean precondition (Instance inst) {
		return true;
	}
	
	// TODO Really this should be 'protected', but isn't for historical reasons. 
	/** Really this should be 'protected', but isn't for historical reasons.  */
	public Instance pipe (Instance inst) {
		throw new UnsupportedOperationException ("Pipes of class "+this.getClass().getName()
				+" do not guarantee one-to-one mapping of Instances.  Use 'newIteratorFrom' method instead.");
	}
	
	// TODO: Consider naming this simply "iterator"
	/** Given an InstanceIterator, return a new InstanceIterator whose instances 
	 * have also been processed by this pipe.  If you override this method, be sure to check
	 * and obey this pipe's {@link skipIfFalse(Instance)} method. */
	public Iterator<Instance> newIteratorFrom (Iterator<Instance> source) 
	{
		return new SimplePipeInstanceIterator (source);
	}
	
	/** A convenience method that will pull all instances from source through this pipe,
	 *  and return the results as an array.
	 */
	public Instance[] instancesFrom (Iterator<Instance> source)
	{
		source = this.newIteratorFrom(source);
		if (!source.hasNext())
			return new Instance[0];
		Instance inst = source.next();
		if (!source.hasNext())
			return new Instance[] {inst};
		ArrayList<Instance> ret = new ArrayList<Instance>();
		ret.add(inst);
		while (source.hasNext())
			ret.add (source.next());
		return (Instance[])ret.toArray();
	}
	
	public Instance[] instancesFrom (Instance inst) 
	{
		return instancesFrom (new SingleInstanceIterator(inst));
	}
	
	
	// TODO Do we really want to encourage behavior like this?  Consider removing this method.
	// This only works properly if the pipe is one-to-one.
	public Instance instanceFrom (Instance inst)
	{
		Instance[] results = instancesFrom (inst);
		if (results.length == 0)
			return null;
		else
			return results[0];
	}

	/** Set whether input is taken from target field of instance during processing.
	 *  If argument is false, don't expect to find input material for the target.
	 *  By default, this is true. */
	public void setTargetProcessing (boolean lookForAndProcessTarget)
	{
		targetProcessing = lookForAndProcessTarget;
	}

	/** Return true iff this pipe expects and processes information in
			the <tt>target</tt> slot. */
	public boolean isTargetProcessing ()
	{
		return targetProcessing;
	}

	// If this Pipe produces objects that use a Alphabet, this
	// method returns that dictionary.  Even if this particular Pipe
	// doesn't use a Alphabet it may return non-null if
	// objects passing through it use a dictionary.

	// This method should not be called until the dictionary is really
	// needed, because it may set off a chain of events that "resolve"
	// the dictionaries of an entire pipeline, and generally this
	// resolution should not take place until the pipeline is completely
	// in place, and pipe() is being called.
	// xxx Perhaps desire to wait until pipe() is being called is unrealistic
	// and unnecessary.

	public Alphabet getDataAlphabet ()
	{
		return dataAlphabet;
	}

	public Alphabet getTargetAlphabet ()
	{
		return targetAlphabet;
	}
	
	public Alphabet getAlphabet () {
		return getDataAlphabet();
	}
	
	public Alphabet[] getAlphabets()
	{
		return new Alphabet[] {getDataAlphabet(), getTargetAlphabet()};
	}
	
	public boolean alphabetsMatch (AlphabetCarrying object)
	{
		Alphabet[] oas = object.getAlphabets();
		return  oas.length == 2 && oas[0].equals(getDataAlphabet()) && oas[1].equals(getTargetAlphabet());
	}

	public void setDataAlphabet (Alphabet dDict)
	{
		if (dataAlphabet != null && dataAlphabet.size() > 0)
			throw new IllegalStateException
			("Can't set this Pipe's Data  Alphabet; it already has one.");
		dataAlphabet = dDict;
	}

	public boolean isDataAlphabetSet() 
	{
		if (dataAlphabet != null && dataAlphabet.size() > 0)
			return true;
		return false;
	}
	
	public void setOrCheckDataAlphabet (Alphabet a) {
		if (dataAlphabet == null)
			dataAlphabet = a;
		else if (! dataAlphabet.equals(a))
			throw new IllegalStateException ("Data alphabets do not match");
	}

	public void setTargetAlphabet (Alphabet tDict)
	{
		if (targetAlphabet != null)
			throw new IllegalStateException
			("Can't set this Pipe's Target Alphabet; it already has one.");
		targetAlphabet = tDict;
	}
	
	public void setOrCheckTargetAlphabet (Alphabet a) {
		if (targetAlphabet == null)
			targetAlphabet = a;
		else if (! targetAlphabet.equals(a))
			throw new IllegalStateException ("Target alphabets do not match");
	}

	protected void preceedingPipeDataAlphabetNotification (Alphabet a)
	{
		if (dataAlphabet == null)
			dataAlphabet = a;
	}

	protected void preceedingPipeTargetAlphabetNotification (Alphabet a)
	{
		if (targetAlphabet == null)
			targetAlphabet = a;
	}

	public VMID getInstanceId() { return instanceId;} // for debugging
	
	
	// The InstanceIterator used to implement the one-to-one pipe() method behavior.
	private class SimplePipeInstanceIterator implements Iterator<Instance>
	{
		Iterator<Instance> source;
		public SimplePipeInstanceIterator (Iterator<Instance> source) {
			this.source = source;
		}
		public boolean hasNext () { return source.hasNext(); }
		public Instance next() { 
			Instance input = source.next();
			if (!precondition(input))
				return input;
			else
				return pipe (input); 
			}
		/** Return the @link{Pipe} that processes @link{Instance}s going through this iterator. */ 
		public Pipe getPipe () { return null; }
		public Iterator<Instance> getSourceIterator () { return source; }
		public void remove() { throw new IllegalStateException ("Not supported."); }
	}
	


	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(dataAlphabet);
		out.writeObject(targetAlphabet);
		out.writeBoolean(dataAlphabetResolved);
		out.writeBoolean(targetAlphabetResolved);
		out.writeBoolean(targetProcessing);
		out.writeObject(instanceId);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		dataAlphabet = (Alphabet) in.readObject();
		targetAlphabet = (Alphabet) in.readObject();
		dataAlphabetResolved = in.readBoolean();
		targetAlphabetResolved = in.readBoolean();
		targetProcessing = in.readBoolean();
		instanceId = (VMID) in.readObject();
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
		//System.out.println(" *** Pipe ReadResolve: instance id= " + instanceId);
		Object previous = deserializedEntries.get(instanceId);
		if (previous != null){
			//System.out.println(" *** Pipe ReadResolve:Resolving to previous instance. instance id= " + instanceId);
			return previous;
		}
		if (instanceId != null){
			deserializedEntries.put(instanceId, this);
		}
		//System.out.println(" *** Pipe ReadResolve: new instance. instance id= " + instanceId);
		return this;
	}
}
