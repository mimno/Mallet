/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;


import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.IOException;
import java.util.Arrays;

import cc.mallet.classify.Classifier;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;
import cc.mallet.types.Multinomial.Logged;

/**
 * A classifier that classifies instances according to the NaiveBayes method.
 * In an Bayes classifier,
 *     the p(Classification|Data) = p(Data|Classification)p(Classification)/p(Data)
 * <p>
 *  To compute the likelihood:   <br>
 *      p(Data|Classification) = p(d1,d2,..dn | Classification)  <br>
 * Naive Bayes makes the assumption  that all of the data are conditionally
 * independent given the Classification:  <br>
 *      p(d1,d2,...dn | Classification) = p(d1|Classification)p(d2|Classification)..   <br>
 * <p>
 * As with other classifiers in Mallet, NaiveBayes is implemented as two classes:
 * a trainer and a classifier.  The {@link cc.mallet.classify.NaiveBayesTrainer} produces estimates of the various
 * p(dn|Classifier) and contructs this class with those estimates.
 * <p>
 * Instances are assumed to be {@link cc.mallet.types.FeatureVector}s
 * <p>
 * As with other Mallet classifiers, classification may only be performed on instances
 * processed with the pipe associated with this classifer, ie naiveBayes.getPipeInstance();
 * The NaiveBayesTrainer sets this pipe to the pipe used to process the training instances.
 * <p>
 * A NaiveBayes classifier can be persisted and reused using serialization.
 * @see NaiveBayesTrainer
 * @see FeatureVector
 *
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class NaiveBayes extends Classifier implements Serializable
{
  Multinomial.Logged prior;
  Multinomial.Logged[] p;

      /**
       * Construct a NaiveBayes classifier from a pipe, prior estimates for each Classification,
       * and feature estimates of each Classification.  A NaiveBayes classifier is generally
       * generated from a NaiveBayesTrainer, not constructed directly by users.
       * Proability estimates are converted and saved as logarithms internally.
       * @param instancePipe  Used to check that feature vector dictionary for each instance
       * is the same as that associated with the pipe. Null suppresses check
       * @param prior Mulinomial that gives an estimate of the prior probability for
       *  each Classification
       * @param classIndex2FeatureProb  An array of multinomials giving an estimate
       * of the probability of a classification for each feature of each featurevector.
       */
  public NaiveBayes (Pipe instancePipe,
                     Multinomial.Logged prior,
                     Multinomial.Logged[] classIndex2FeatureProb)
  {
    super (instancePipe);
    this.prior = prior;
    this.p = classIndex2FeatureProb;
  }

  private static Multinomial.Logged[] logMultinomials (Multinomial[] m)
  {
    Multinomial.Logged[] ml = new Multinomial.Logged[m.length];
    for (int i = 0; i < m.length; i++)
      ml[i] = new Multinomial.Logged (m[i]);
    return ml;
  }

      /**
       * Construct a NaiveBayes classifier from a pipe, prior estimates for each Classification,
       * and feature estimates of each Classification.  A NaiveBayes classifier is generally
       * generated from a NaiveBayesTrainer, not constructed directly by users.
       *
       * @param dataPipe  Used to check that feature vector dictionary for each instance
       * is the same as that associated with the pipe. Null suppresses check
       * @param prior Mulinomial that gives an estimate of the prior probability for
       *  each Classification
       * @param classIndex2FeatureProb  An array of multinomials giving an estimate
       * of the probability of a classification for each feature of each featurevector.
       */
  public NaiveBayes (Pipe dataPipe,
                     Multinomial prior,
                     Multinomial[] classIndex2FeatureProb)
  {
              this (dataPipe,
                    new Multinomial.Logged (prior),
                    logMultinomials (classIndex2FeatureProb));
  }
  
  public Multinomial.Logged[] getMultinomials() {
	  return p;
  }
  
  public Logged getPriors() {
	return prior;
  }

  public void printWords (int numToPrint)
  {
    Alphabet alphabet = instancePipe.getDataAlphabet();
    int numFeatures = alphabet.size();
    int numLabels = instancePipe.getTargetAlphabet().size();
    double[] probs = new double[numFeatures];
    numToPrint = Math.min(numToPrint, numFeatures);
    for (int li = 0; li < numLabels; li++) {
      Arrays.fill (probs, 0.0);
      p[li].addProbabilities(probs);
      RankedFeatureVector rfv = new RankedFeatureVector (alphabet, probs);
      System.out.println ("\nFeature probabilities "+instancePipe.getTargetAlphabet().lookupObject(li));
      for (int i = 0; i < numToPrint; i++)
        System.out.println (rfv.getObjectAtRank(i)+" "+rfv.getValueAtRank(i));
    }
  }

       /**
        * Classify an instance using NaiveBayes according to the trained data.
        * The alphabet of the featureVector of the instance must match the 
        * alphabe of the pipe used to train the classifier.
        * @param instance to be classified. Data field must be a FeatureVector
        * @return Classification containing the labeling of  the instance
        */
  public Classification classify (Instance instance)
  {
        // Note that the current size of the label alphabet can be larger
      // than it was at the time of training.  We are careful here
      // to correctly handle those labels here. For example,
      // we assume the log prior probability of those classes is
      // minus infinity.
    int numClasses = getLabelAlphabet().size();
    double[] scores = new double[numClasses];
    FeatureVector fv = (FeatureVector) instance.getData ();
    // Make sure the feature vector's feature dictionary matches
    // what we are expecting from our data pipe (and thus our notion
    // of feature probabilities.
    assert (instancePipe == null
            || fv.getAlphabet () == instancePipe.getDataAlphabet ());
    int fvisize = fv.numLocations();

    prior.addLogProbabilities (scores);

         // Set the scores according to the feature weights and per-class probabilities
    for (int fvi = 0; fvi < fvisize; fvi++) {
      int fi = fv.indexAtLocation(fvi);
      for (int ci = 0; ci < numClasses; ci++) {
        // guard against dataAlphabet or target alphabet growing; can happen if classifying
        // a never before seen feature.  Ignore these.
        if (ci >= p.length || fi >= p[ci].size()) continue;

        scores[ci] += fv.valueAtLocation(fvi) * p[ci].logProbability(fi);
      }
    }

    // Get the scores in the range near zero, where exp() is more accurate
    double maxScore = Double.NEGATIVE_INFINITY;
    for (int ci = 0; ci < numClasses; ci++)
      if (scores[ci] > maxScore)
        maxScore = scores[ci];
    for (int ci = 0; ci < numClasses; ci++)
      scores[ci] -= maxScore;

    // Exponentiate and normalize
    double sum = 0;
    for (int ci = 0; ci < numClasses; ci++)
      sum += (scores[ci] = Math.exp (scores[ci]));
    for (int ci = 0; ci < numClasses; ci++)
      scores[ci] /= sum;

    // Create and return a Classification object
    return new Classification (instance, this,
                               new LabelVector (getLabelAlphabet(),
                                                scores));
  }

  private double dataLogProbability (Instance instance, int labelIndex) {
    FeatureVector fv = (FeatureVector) instance.getData ();
    int fvisize = fv.numLocations();
    double logProb = 0;

    for (int fvi = 0; fvi < fvisize; fvi++)
      logProb += fv.valueAtLocation(fvi) * p[labelIndex].logProbability(fv.indexAtLocation(fvi));
    return logProb;
 }

  public double dataLogLikelihood (InstanceList ilist) {
    double logLikelihood = 0;
    for (int ii = 0; ii < ilist.size(); ii++) {
      double instanceWeight = ilist.getInstanceWeight(ii);
      Instance inst = ilist.get(ii);
      Labeling labeling = inst.getLabeling ();
      if (labeling != null)
        logLikelihood += instanceWeight * dataLogProbability (inst, labeling.getBestIndex());
      else {
        Labeling predicted = this.classify(inst).getLabeling();
        //System.err.println ("label = \n"+labeling);
        //System.err.println ("predicted = \n"+predicted);
        for (int lpos = 0; lpos < predicted.numLocations(); lpos++) {
          int li = predicted.indexAtLocation (lpos);
          double labelWeight = predicted.valueAtLocation (lpos);
          //System.err.print (", "+labelWeight);
          if (labelWeight == 0) continue;
          logLikelihood += instanceWeight * labelWeight * dataLogProbability (inst, li);
        }
      }
    }
    return logLikelihood;
  }


  public double labelLogLikelihood (InstanceList ilist) {
    double logLikelihood = 0;
    for (int ii = 0; ii < ilist.size(); ii++) {
      double instanceWeight = ilist.getInstanceWeight(ii);
      Instance inst = ilist.get(ii);
      Labeling labeling = inst.getLabeling ();
      if (labeling == null)
        continue;
      Labeling predicted = this.classify(inst).getLabeling();
      //System.err.println ("label = \n"+labeling);
      //System.err.println ("predicted = \n"+predicted);
      if (labeling.numLocations() == 1) {
        logLikelihood += instanceWeight * Math.log (predicted.value(labeling.getBestIndex()));
      } else {
        for (int lpos = 0; lpos < labeling.numLocations(); lpos++) {
          int li = labeling.indexAtLocation (lpos);
          double labelWeight = labeling.valueAtLocation (lpos);
          //System.err.print (", "+labelWeight);
          if (labelWeight == 0) continue;
          logLikelihood += instanceWeight * labelWeight * Math.log (predicted.value(li));
        }
      }
    }
    return logLikelihood;
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
    out.writeObject(getInstancePipe());

    // write prior for each class
    out.writeObject(prior);

    // write array of conditional probability estimates
    out.writeObject(p);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt();
    if (version != CURRENT_SERIAL_VERSION)
      throw new ClassNotFoundException("Mismatched NaiveBayes versions: wanted " +
                                       CURRENT_SERIAL_VERSION + ", got " +
                                       version);
    instancePipe = (Pipe) in.readObject();
    prior = (Multinomial.Logged) in.readObject();
    p = (Multinomial.Logged [])  in.readObject();

  }
}
