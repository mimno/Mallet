/* This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.AlphabetCarrying;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;

/**
 * A Classifier Trainer to be used with MostFrequentClassifier.
 * 
 * @see MostFrequentClassifier
 * 
 * @author Martin Wunderlich <a href="mailto:martin@wunderlich.com">martin@wunderlich.com</a>
*/
public class MostFrequentClassAssignmentTrainer extends ClassifierTrainer<MostFrequentClassifier> implements AlphabetCarrying {
	
	MostFrequentClassifier classifier = null;
	
	Pipe instancePipe;        // Needed to construct a new classifier
    Alphabet dataAlphabet;    // Extracted from InstanceList. Must be the same for all calls to incrementalTrain()
    Alphabet targetAlphabet; // Extracted from InstanceList. Must be the same for all calls to incrementalTrain
	
	@Override
	public MostFrequentClassifier getClassifier() {
		return this.classifier;
	}

  /**
   * Create a MostFrequent classifier from a set of training data.
   * 
   * @param trainingList The InstanceList to be used to train the classifier.
   * @return The MostFrequent classifier as trained on the trainingList
   */
	@Override
	public MostFrequentClassifier train(InstanceList trainingSet) {
		// Initialize or check the instancePipe
	  	if (trainingSet != null) {
	  		if (this.instancePipe == null)
	  			this.instancePipe = trainingSet.getPipe();
	  		else if (this.instancePipe != trainingSet.getPipe())
	  			// Make sure that this pipes match.  Is this really necessary??
	  			// I don't think so, but it could be confusing to have each returned classifier have a different pipe?  -akm 1/08
	  			throw new IllegalArgumentException ("Training set pipe does not match that of NaiveBayesTrainer.");
	  		
	  		this.dataAlphabet = this.instancePipe.getDataAlphabet();
	  		this.targetAlphabet = this.instancePipe.getTargetAlphabet();
	  	}
	  	
	  	this.classifier = new MostFrequentClassifier(this.instancePipe);
	  	
	  	
	  	// Init alphabets and extract label from instance
		for(Instance instance : trainingSet) {
			if (dataAlphabet == null) {
		  		this.dataAlphabet = instance.getDataAlphabet();
		  		this.targetAlphabet = instance.getTargetAlphabet();
		  	}	else if (!Alphabet.alphabetsMatch(instance, this))
		  		// Make sure the alphabets match
		  		throw new IllegalArgumentException ("Training set alphabets do not match those of NaiveBayesTrainer.");
			
			Label label = (Label) instance.getTarget();
			
			this.classifier.addTargetLabel(label);
		}
		
		return this.classifier;
	}

	// AlphabetCarrying interface
	public boolean alphabetsMatch(AlphabetCarrying object) {
		return Alphabet.alphabetsMatch (this, object);
	}
	
	@Override
	public Alphabet getAlphabet() {
		return dataAlphabet;
	}
	
	@Override
	public Alphabet[] getAlphabets() {
		return new Alphabet[] { dataAlphabet, targetAlphabet };
	}
}
