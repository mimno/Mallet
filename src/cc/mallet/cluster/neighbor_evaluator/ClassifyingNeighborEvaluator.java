package cc.mallet.cluster.neighbor_evaluator;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import cc.mallet.classify.Classifier;

/**
 * A {@link NeighborEvaluator} that is backed by a {@link
 * Classifier}. The score for a {@link Neighbor} is the Classifier's
 * predicted value for the label corresponding to <code>scoringLabel</code>.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see NeighborEvaluator
 */
public class ClassifyingNeighborEvaluator implements NeighborEvaluator, Serializable {

	/**
	 * The Classifier used to assign a score to each {@link Neighbor}.
	 */
	Classifier classifier;

	/**
	 * The label corresponding to a positive instance (e.g. "YES").
	 */
	String scoringLabel;
	
	/**
	 *
	 * @param classifier The Classifier used to assign a score to each {@link Neighbor}.
	 * @param scoringLabel The label corresponding to a positive instance (e.g. "YES").
	 * @return
	 */
	public ClassifyingNeighborEvaluator (Classifier classifier,
																			 String scoringLabel) {
		this.classifier = classifier;
		this.scoringLabel = scoringLabel;
	}
	
	/**
	 *
	 * @return The classifier.
	 */
	public Classifier getClassifier () { return classifier; }

	public double evaluate (Neighbor neighbor) {
		return classifier.classify(neighbor).getLabelVector().value(scoringLabel);		
	}

	public double[] evaluate (Neighbor[] neighbors) {
		double[] scores = new double[neighbors.length];
		for (int i = 0; i < neighbors.length; i++)
			scores[i] = evaluate(neighbors[i]);
		return scores;
	}

	public void reset () {
	}

	public String toString () {
		return "class=" + this.getClass().getName() +
			" classifier=" + classifier.getClass().getName() +
			" scoringLabel=" + scoringLabel;
	}

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
