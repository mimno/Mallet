package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;

import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelSequence;

/** Given instances with a FeatureVectorSequence in the data field, break up the sequence into 
 * the individual FeatureVectors, producing one FeatureVector per Instance. */
public class FeatureVectorSequence2FeatureVectors extends Pipe 
{

	final class FeatureVectorIterator implements Iterator<Instance> 
	{
		Iterator<Instance> superIterator;
		Instance superInstance;
		Iterator dataSubiterator, targetSubiterator;
		int count = 0;
		public FeatureVectorIterator (Iterator<Instance> inputIterator) {
			superInstance = inputIterator.next();
			dataSubiterator = ((FeatureVectorSequence)superInstance.getData()).iterator();
			targetSubiterator = ((LabelSequence)superInstance.getTarget()).iterator();
		}
		public Instance next () {
			if (!dataSubiterator.hasNext()) {
				assert (superIterator.hasNext());
				superInstance = superIterator.next();
				dataSubiterator = ((FeatureVectorSequence)superInstance.getData()).iterator();
				targetSubiterator = ((LabelSequence)superInstance.getTarget()).iterator();
			}
			// We are assuming sequences don't have zero length
			assert (dataSubiterator.hasNext());
			assert (targetSubiterator.hasNext());
			return new Instance (dataSubiterator.next(), targetSubiterator.next(), 
					superInstance.getSource()+" tokensequence:"+count++,	null);
		}
		public boolean hasNext () {
			return dataSubiterator.hasNext() || superIterator.hasNext();
		}
		public void remove () { }
	}

	public FeatureVectorSequence2FeatureVectors() {}

	public Iterator<Instance> newIteratorFrom (Iterator<Instance> inputIterator) {
		return new FeatureVectorIterator (inputIterator);
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
