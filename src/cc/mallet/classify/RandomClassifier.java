/* This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;

/**
 * A Classifier that will return a randomly selected class label.
 * The Classifier needs to be trained using the RandomAssignmentTrainer.
 * 
 * Note that the frequency distribution gives more weight to class labels with
 * a higher frequency in the training data. To create a Classifier which gives
 * equal weight to each class, simply use a Set<Label> instead of the List<Label>.
 * 
 * @see RandomAssignmentTrainer
 * 
 * @author Martin Wunderlich <a href="mailto:martin@wunderlich.com">martin@wunderlich.com</a>
*/
public class RandomClassifier extends Classifier {

	List<Label> labels = new ArrayList<Label>();	// using a List instead of a Set, so that more frequent labels in the training data are more likely in classification
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3689741912639283481L;

	public RandomClassifier(Pipe instancePipe) {
		this.instancePipe = instancePipe;
	}

    /**
     * Classify an instance using random selection based on the trained data.
     * 
     * @param Instance to be classified. Data field must be a FeatureVector.
     * @return Classification containing the labeling of the instance.
     */
	@Override
	public Classification classify(Instance instance) {
		int max = this.labels.size() - 1;
		Random random = new Random();
		int rndIndex = random.nextInt(max + 1);
		
		Label randomLabel = this.labels.get(rndIndex);
		Classification randomClassification = new Classification(instance, this, randomLabel);
		
		return randomClassification;
	}

	public void addTargetLabel(Label label) {
		this.labels.add(label);
	}
}
