package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;

import cc.mallet.types.Instance;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.TokenSequence;


public class TokenSequence2TokenInstances extends Pipe {

	private class TokenInstanceIterator implements Iterator<Instance>
	{
		Iterator<Instance> source;
		Instance currentInstance = null;
		TokenSequence currentTokenSequence;
		int currentIndex;
		public TokenInstanceIterator (Iterator<Instance> source) {
			if (source.hasNext()) {
				currentInstance = source.next();
				currentTokenSequence = (TokenSequence) currentInstance.getData();
			}
			currentIndex = 0;
		}
		public Instance next ()	{
			if (currentIndex >= currentTokenSequence.size()) {
				currentInstance = source.next();
				currentTokenSequence = (TokenSequence) currentInstance.getData();
        currentIndex = 0;
			}
			Instance ret = new Instance (currentTokenSequence.get(currentIndex),
					((LabelSequence)currentInstance.getTarget()).getLabelAtPosition(currentIndex),
					null, null);
			currentIndex++;
			return ret;
		}
		public boolean hasNext ()	{
			return currentInstance != null && (currentIndex < currentTokenSequence.size() || source.hasNext());
		}
		public void remove () { throw new IllegalStateException ("This iterator does not support remove().");	}
	}

	public Iterator<Instance> newIteratorFrom (Iterator<Instance> source) 
	{
		return new TokenInstanceIterator (source);
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
