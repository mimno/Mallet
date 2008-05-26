/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.io.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.EmptyInstanceIterator;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
/**
 * Convert an instance through a sequence of pipes.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class SerialPipes extends Pipe implements Serializable
{
	ArrayList<Pipe> pipes;

	public SerialPipes ()
	{
		this.pipes = new ArrayList<Pipe> ();
	}
	
	public SerialPipes (Pipe[] pipes)
	{
		this.pipes = new ArrayList<Pipe> (pipes.length);
		for (int i = 0; i < pipes.length; i++)
			this.pipes.add (pipes[i]);
		resolveAlphabets();
	}

	public SerialPipes (Collection<Pipe> pipeList)
	{
		pipes = new ArrayList<Pipe> (pipeList);
		resolveAlphabets();
	}
	
	public abstract class Predicate {
		public abstract boolean predicate (Pipe p);
	}
	
	public SerialPipes newSerialPipesFromSuffix (Predicate testForStartingNewPipes) {
		int i = 0;
		while (i < pipes.size())
			if (testForStartingNewPipes.predicate(pipes.get(i))) {
				return new SerialPipes(pipes.subList(i, pipes.size()-1));
			}
		throw new IllegalArgumentException ("No pipes in this SerialPipe satisfied starting predicate.");
	}
	
	public SerialPipes newSerialPipesFromRange (int start, int end) {
		return new SerialPipes(pipes.subList(start, end));
	}
	
	private void resolveAlphabets ()
	{
		Alphabet da = null, ta = null;
		for (Pipe p : pipes) {
			p.preceedingPipeDataAlphabetNotification(da);
			da = p.getDataAlphabet();
			p.preceedingPipeTargetAlphabetNotification(ta);
			ta = p.getTargetAlphabet();
		}
		dataAlphabet = da;
		targetAlphabet = ta;
	}

	// protected void add (Pipe pipe)
	// protected void remove (int i)
	// This method removed because pipes should be immutable to be safe.
	// If you need an augmented pipe, you can make a new SerialPipes containing this one.

	public void setTargetProcessing (boolean lookForAndProcessTarget)
	{
		super.setTargetProcessing (lookForAndProcessTarget);
		for (Pipe p : pipes)
			p.setTargetProcessing (lookForAndProcessTarget);
	}
	
	public Iterator<Instance> newIteratorFrom (Iterator<Instance> source)
	{
		if (pipes.size() == 0)
			return new EmptyInstanceIterator();
		Iterator<Instance> ret = pipes.get(0).newIteratorFrom(source);
		for (int i = 1; i < pipes.size(); i++)
			ret = pipes.get(i).newIteratorFrom(ret);
		return ret;
	}
	
	public int size()
	{
		return pipes.size();
	}

	public Pipe getPipe (int index) {
		Pipe retPipe = null;
		try {
			retPipe = pipes.get(index);
		}
		catch (Exception e) {
			System.err.println("Error getting pipe. Index = " + index + ".  " + e.getMessage());
		}
		return retPipe;
	}
	
	/** Allows access to the underlying collection of Pipes.  Use with caution. */
	public ArrayList<Pipe> pipes() {
		return pipes;
	}
	
	public String toString ()
	{
		StringBuffer sb = new StringBuffer();
		for (Pipe p : pipes)
			sb.append (p.toString()+",");
		return sb.toString();
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(pipes);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		pipes = (ArrayList) in.readObject();
	}

}
