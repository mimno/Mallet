package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;


public class FilterEmptyFeatureVectors extends Pipe {

	private class FilteringPipeInstanceIterator implements Iterator<Instance>
	{
		Iterator<Instance> source;
		Instance nextInstance = null;
		boolean doesHaveNext = false;
		
		public FilteringPipeInstanceIterator (Iterator<Instance> source) {
			this.source = source;
			if (source.hasNext())	{
				nextInstance = source.next();
				doesHaveNext = true;
			} else 
				doesHaveNext = false;
		}
		public boolean hasNext () { 
			return doesHaveNext;
		}
		public Instance next() { 
			Instance ret = nextInstance;
			doesHaveNext = false;
			while (source.hasNext()) {
					nextInstance = source.next();
					if (((FeatureVector)nextInstance.getData()).numLocations() > 0) {
						doesHaveNext = true;
						break;
					}
			}	
			if (!doesHaveNext)  
				nextInstance = null;
			return ret;
		}
		public void remove () { throw new IllegalStateException ("This iterator does not support remove().");	}
		/** Return the @link{Pipe} that processes @link{Instance}s going through this iterator. */ 
		public Pipe getPipe () { return null; }
		public Iterator<Instance> getSourceIterator () { return source; }
	}
	
	public Iterator<Instance> newIteratorFrom (Iterator<Instance> source) 
	{
		return new FilteringPipeInstanceIterator (source);
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
	}
	
	
}
