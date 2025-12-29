package cc.mallet.types;

import java.util.Iterator;


public class SingleInstanceIterator implements Iterator<Instance> {
	
	Instance nextInstance;
	boolean doesHaveNext;
	
	public SingleInstanceIterator (Instance inst) {
		nextInstance = inst;
		doesHaveNext = true;
	}

	public boolean hasNext() {
		return doesHaveNext;
	}

	public Instance next() {
		doesHaveNext = false;
		return nextInstance;
	}
	
	public void remove () { throw new IllegalStateException ("This iterator does not support remove().");	}

}
