package cc.mallet.cluster.neighbor_evaluator;


import java.io.*;

import cc.mallet.util.Randoms;

/**
 * Randomly scores {@link Neighbor}s.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see NeighborEvaluator
 */
public class RandomEvaluator implements NeighborEvaluator, Serializable {

	Randoms random;
	
	public RandomEvaluator (Randoms random) {
		this.random = random;
	}
	
	/**
	 *
	 * @param neighbor
	 * @return A higher score indicates that the modified Clustering is preferred.
	 */
	public double evaluate (Neighbor neighbor) {
		return random.nextUniform(0, 1);
	}

	/**
	 *
	 * @param neighbors
	 * @return One score per neighbor. A higher score indicates that the
	 * modified Clustering is preferred.
	 *
	 */
	public double[] evaluate (Neighbor[] neighbors) {
		double[] scores = new double[neighbors.length];
		for (int i = 0; i < neighbors.length; i++)
			scores[i] = evaluate(neighbors[i]);
		return scores;		
	}

	/**
	 * Reset the state of the evaluator.
	 */
	public void reset () {}
		
	// SERIALIZATION

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject ();
    int version = in.readInt ();
  }	
}
