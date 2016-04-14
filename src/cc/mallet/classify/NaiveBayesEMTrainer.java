/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Logger;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Multinomial;
import cc.mallet.util.MalletLogger;


/**
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class NaiveBayesEMTrainer extends ClassifierTrainer<NaiveBayes> {

	private static Logger logger = MalletLogger.getLogger(NaiveBayesEMTrainer.class.getName());

  Multinomial.Estimator featureEstimator = new Multinomial.LaplaceEstimator();
  Multinomial.Estimator priorEstimator = new Multinomial.LaplaceEstimator();
  double docLengthNormalization = -1;
  double unlabeledDataWeight = 1.0;
  int iteration = 0;
  NaiveBayesTrainer.Factory nbTrainer;
  NaiveBayes classifier;
  
  public NaiveBayesEMTrainer () {
    nbTrainer = new NaiveBayesTrainer.Factory ();
    nbTrainer.setDocLengthNormalization(docLengthNormalization);
    nbTrainer.setFeatureMultinomialEstimator(featureEstimator);
    nbTrainer.setPriorMultinomialEstimator (priorEstimator);

  }

  public Multinomial.Estimator getFeatureMultinomialEstimator () {
  		return featureEstimator;
  }

  public void setFeatureMultinomialEstimator (Multinomial.Estimator me) {
    featureEstimator = me;
    nbTrainer.setFeatureMultinomialEstimator(featureEstimator);
  }

  public Multinomial.Estimator getPriorMultinomialEstimator () {
    return priorEstimator;
  }

  public void setPriorMultinomialEstimator (Multinomial.Estimator me) {
    priorEstimator = me;
    nbTrainer.setPriorMultinomialEstimator(priorEstimator);
  }

  public void setDocLengthNormalization (double d) {
  	docLengthNormalization = d;
  	nbTrainer.setDocLengthNormalization(docLengthNormalization);
  }
  
  public double getDocLengthNormalization () {
  	return docLengthNormalization;
  }
  
	public double getUnlabeledDataWeight () {
		return unlabeledDataWeight;
	}

	public void setUnlabeledDataWeight (double unlabeledDataWeight) {
		this.unlabeledDataWeight = unlabeledDataWeight;
	}
	
	public int getIteration() { return iteration; }
	public boolean isFinishedTraining() { return false; }
	public NaiveBayes getClassifier() { return classifier; }
	

  public NaiveBayes train (InstanceList trainingSet)
  {

    // Get a classifier trained on the labeled examples only
    NaiveBayes c = (NaiveBayes) nbTrainer.newClassifierTrainer().train (trainingSet);
    double prevLogLikelihood = 0, logLikelihood = 0;
    boolean converged = false;

    int iteration = 0;
    while (!converged) {
      // Make a new trainingSet that has some labels set
      InstanceList trainingSet2 = new InstanceList (trainingSet.getPipe());
      for (int ii = 0; ii < trainingSet.size(); ii++) {
        Instance inst = trainingSet.get(ii);
        if (inst.getLabeling() != null)
          trainingSet2.add(inst, 1.0);
        else {
          Instance inst2 = inst.shallowCopy();
          inst2.unLock();
          inst2.setLabeling(c.classify(inst).getLabeling());
          inst2.lock();
          trainingSet2.add(inst2, unlabeledDataWeight);
        }
      }
      c = (NaiveBayes) nbTrainer.newClassifierTrainer().train (trainingSet2);
      logLikelihood = c.dataLogLikelihood (trainingSet2);
      System.err.println ("Loglikelihood = "+logLikelihood);
      // Wait for a change in log-likelihood of less than 0.01% and at least 10 iterations
      if (Math.abs((logLikelihood - prevLogLikelihood)/logLikelihood) < 0.0001)
        converged = true;
      prevLogLikelihood = logLikelihood;
      iteration++;
    }
    return c;    
  }

  public String toString()
  {
  	String ret = "NaiveBayesEMTrainer";
  	if (docLengthNormalization != 1.0) ret += ",docLengthNormalization="+docLengthNormalization;
  	if (unlabeledDataWeight != 1.0) ret += ",unlabeledDataWeight="+unlabeledDataWeight;
    return ret;
  }


  // Serialization
  // serialVersionUID is overriden to prevent innocuous changes in this
  // class from making the serialization mechanism think the external
  // format has changed.

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject(ObjectOutputStream out) throws IOException
  {
    out.writeInt(CURRENT_SERIAL_VERSION);

    //default selections for the kind of Estimator used
    out.writeObject(featureEstimator);
    out.writeObject(priorEstimator);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt();
    if (version != CURRENT_SERIAL_VERSION)
      throw new ClassNotFoundException("Mismatched NaiveBayesTrainer versions: wanted " +
                                       CURRENT_SERIAL_VERSION + ", got " +
                                       version);

    //default selections for the kind of Estimator used
    featureEstimator = (Multinomial.Estimator) in.readObject();
    priorEstimator = (Multinomial.Estimator) in.readObject();
  }


}
