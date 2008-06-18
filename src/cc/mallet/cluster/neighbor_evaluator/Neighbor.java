package cc.mallet.cluster.neighbor_evaluator;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import cc.mallet.cluster.Clustering;
import cc.mallet.types.Alphabet;

/**
 * A Clustering and a modified version of that Clustering.
 * 
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 */
public class Neighbor implements Serializable {

	Clustering original;
	Clustering modified;

	Alphabet alphabet;
	
	public Neighbor(Clustering original, Clustering modified) {
		this.original = original;
		this.modified = modified;
	}

	/**
	 * 
	 * @return The original Clustering.
	 */
	public Clustering getOriginal() {
		return original;
	}

	/**
	 * 
	 * @return The modified Clustering.
	 */
	public Clustering getModified() {
		return modified;
	}

	public String toString() {
		return "original=" + original + "\nmodified=" + modified;
	}

	// SERIALIZATION

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		out.writeInt(CURRENT_SERIAL_VERSION);
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		in.defaultReadObject();
		int version = in.readInt();
	}
}
