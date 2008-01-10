package cc.mallet.pipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import cc.mallet.pipe.iterator.EmptyInstanceIterator;
import cc.mallet.types.Instance;

/** A Pipe that works like a rule list.  Evaluate predicate() on each Pipe in the array;
 * the first one that returns true, call that one ("the called Pipe"), and ignore the 
 * remainder in the array.  The called Pipe will then get control of the source 
 * InstanceIterator until it produces an Instance---in other words it will be able to call
 * next() on the source Pipe as often as necessary to produce an Instance.
 * You must be very careful that none of the iterators from Pipes in the rule list buffer
 * any Instances---in other words they shouldn't call next() to pre-gather any Instances 
 * they they themselves don't consume and process immediately.  Otherwise, Instances
 * that should have been processed by some other constituent Pipe could get lost in 
 * this buffering process. */

@ Deprecated
//Implementation not yet complete, and seems quite dangerous and error-prone.
public class BranchingPipe extends Pipe {

	ArrayList<Pipe> pipes;

	public BranchingPipe ()
	{
		this.pipes = new ArrayList<Pipe> ();
	}

	public BranchingPipe (Pipe[] pipes)
	{
		this.pipes = new ArrayList<Pipe> (pipes.length);
		for (int i = 0; i < pipes.length; i++)
			this.pipes.add (pipes[i]);
	}

	public BranchingPipe (Collection<Pipe> pipeList)
	{
		pipes = new ArrayList<Pipe> (pipeList);
	}

	private class PeekingInstanceIterator implements Iterator<Instance> {
		Iterator<Instance> source;
		Instance nextInstance = null;
		public PeekingInstanceIterator (Iterator<Instance> source) {
			this.source = source; 
		}
		public boolean hasNext () { return source.hasNext(); }
		public Instance peekNext () { 
			if (nextInstance == null && !hasNext())
				return null;
			else if (nextInstance == null)
				nextInstance = next();
			return nextInstance; 
		}
		public Instance next () {
			if (nextInstance != null) {
				Instance tmp = nextInstance;
				nextInstance = null;
				return tmp;
			} else {
				return source.next(); 
			}
		} 
		public void remove () { throw new IllegalStateException ("This Iterator<Instance> does not support remove()."); }
	}

	private class GateKeepingInstanceIterator implements Iterator<Instance>
	{
		PeekingInstanceIterator source;
		Pipe testingPipe;
		public GateKeepingInstanceIterator (PeekingInstanceIterator source, Pipe testingPipe) {
			this.source = source;
			this.testingPipe = testingPipe;
		}
		public Instance next () {
			// Make sure this is not an Instance we were supposed to skip.
			assert (testingPipe.precondition(source.peekNext()));
			return source.next();
		}
		public boolean hasNext () { 
			return source.hasNext() && testingPipe.precondition(source.peekNext()); 
		}
		public void remove () { throw new IllegalStateException ("This Iterator<Instance> does not support remove()."); }
	}

	private class BranchingInstanceIterator implements Iterator<Instance>
	{
		PeekingInstanceIterator source;
		ArrayList<Iterator<Instance>> iterators;
		public BranchingInstanceIterator (PeekingInstanceIterator source) {
			this.source = new PeekingInstanceIterator (source);
			this.iterators = new ArrayList<Iterator<Instance>>(pipes.size());
			for (Pipe p : pipes)
				iterators.add (new GateKeepingInstanceIterator (source, p));
		}
		public boolean hasNext () { return source.hasNext(); }
		public Instance next() {
			Instance input = source.peekNext();
			for (int i = 0; i < pipes.size(); i++) {
				if (pipes.get(i).precondition(input)) {
					return iterators.get(i).next();
				}
			}
			throw new IllegalStateException ("Next Instance satisfied none of the branches' preconditions.");
		}
		/** Return the @link{Pipe} that processes @link{Instance}s going through this iterator. */ 
		public Pipe getPipe () { return null; }
		public Iterator<Instance> getSourceIterator () { return source; }
		public void remove () { throw new IllegalStateException ("This Iterator<Instance> does not support remove()."); }
	}

	public Iterator<Instance> newIteratorFrom (Iterator<Instance> source)
	{
		if (pipes.size() == 0)
			return source;
		Iterator<Instance> ret = pipes.get(0).newIteratorFrom(source);
		for (int i = 1; i < pipes.size(); i++)
			ret = pipes.get(i).newIteratorFrom(ret);
		return ret;
	}


	private static void test () {
		new BranchingPipe (new Pipe[] {
				new CharSequence2TokenSequence("\\w*") {
					public boolean skipIfFalse (Instance inst) { return ! (inst instanceof CharSequence); }
				}
		});
	}
}
