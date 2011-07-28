package cc.mallet.classify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import cc.mallet.classify.constraints.ge.MaxEntGEConstraint;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletProgressMessageLogger;

/**
 * Training of MaxEnt models with labeled features using
 * Generalized Expectation Criteria.
 * 
 * Based on: 
 * "Learning from Labeled Features using Generalized Expectation Criteria"
 * Gregory Druck, Gideon Mann, Andrew McCallum
 * SIGIR 2008
 * 
 * @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 */

/**
 * @author gdruck
 *
 */
public class MaxEntOptimizableByGE implements Optimizable.ByGradientValue {
  
  private static Logger progressLogger = MalletProgressMessageLogger.getLogger(MaxEntOptimizableByGE.class.getName()+"-pl");
  
  protected boolean cacheStale = true;
  protected int defaultFeatureIndex;
  protected double temperature;
  protected double objWeight;
  protected double cachedValue;
  protected double gaussianPriorVariance;
  protected double[] cachedGradient;
  protected double[] parameters;
  protected InstanceList trainingList;
  protected MaxEnt classifier;
  protected ArrayList<MaxEntGEConstraint> constraints;
  
  /**
   * @param trainingList List with unlabeled training instances.
   * @param constraints Feature expectation constraints.
   * @param initClassifier Initial classifier.
   */
  public MaxEntOptimizableByGE(InstanceList trainingList, ArrayList<MaxEntGEConstraint> constraints, MaxEnt initClassifier) {
    temperature = 1.0;
    objWeight = 1.0;
    gaussianPriorVariance = 1.0;
    this.trainingList = trainingList;
    
    int numFeatures = trainingList.getDataAlphabet().size();
    defaultFeatureIndex = numFeatures;
    int numLabels = trainingList.getTargetAlphabet().size();

    cachedGradient = new double[(numFeatures + 1) * numLabels];
    cachedValue = 0;
       
    if (initClassifier != null) {
      this.parameters = initClassifier.parameters;
      this.classifier = initClassifier;
    }
    else {
      this.parameters = new double[(numFeatures + 1) * numLabels];
      this.classifier = new MaxEnt(trainingList.getPipe(),parameters);
    }
    
     this.constraints = constraints;
     
     for (MaxEntGEConstraint constraint : constraints) {
       constraint.preProcess(trainingList);
     }
  } 
  
  /**
   * Sets the variance for Gaussian prior or
   * equivalently the inverse of the weight 
   * of the L2 regularization term.
   * 
   * @param variance Gaussian prior variance.
   */
  public void setGaussianPriorVariance(double variance) {
    this.gaussianPriorVariance = variance;
  }
  
  
  /**
   * Set the temperature, 1 / the exponent model predicted probabilities 
   * are raised to when computing model expectations.  As the temperature
   * increases, model probabilities approach 1 for the maximum probability
   * class, and 0 for other classes.  DEFAULT: 1  
   * 
   * @param temp Temperature.
   */
  public void setTemperature(double temp) {
    this.temperature = temp;
  }
  
  /**
   * The weight of GE term in the objective function.
   * 
   * @param weight GE term weight.
   */
  public void setWeight(double weight) {
    this.objWeight = weight;
  }
  
  public MaxEnt getClassifier() {
    return classifier;
  }

  public double getValue() {
    if (!cacheStale) {
      return cachedValue;
    }
    
    if (objWeight == 0) {
      return 0.0;
    }
    
    for (MaxEntGEConstraint constraint : constraints) {
      constraint.zeroExpectations();
    }
    
    Arrays.fill(cachedGradient,0);

    int numFeatures = trainingList.getDataAlphabet().size() + 1;
    int numLabels = trainingList.getTargetAlphabet().size();     

    double[][] scores = new double[trainingList.size()][numLabels];
    double[] constraintValue = new double[numLabels];
    
    // pass 1: calculate model distribution
    for (int ii = 0; ii < trainingList.size(); ii++) {
      Instance instance = trainingList.get(ii);
      double instanceWeight = trainingList.getInstanceWeight(instance);
      
      // skip if labeled
      if (instance.getTarget() != null) {
        continue;
      }
      
      FeatureVector fv = (FeatureVector) instance.getData();
      classifier.getClassificationScoresWithTemperature(instance, temperature, scores[ii]);
      for (MaxEntGEConstraint constraint : constraints) {
        constraint.computeExpectations(fv,scores[ii],instanceWeight);
      }
    }
    
    // compute value
    double value = 0;
    for (MaxEntGEConstraint constraint : constraints) {
      value += constraint.getValue();
    }
    value *= objWeight;

    // pass 2: determine per example gradient
    for (int ii = 0; ii < trainingList.size(); ii++) {
      Instance instance = trainingList.get(ii);
      
      // skip if labeled
      if (instance.getTarget() != null) {
        continue;
      }
      
      Arrays.fill(constraintValue,0);
      double instanceExpectation = 0;
      double instanceWeight = trainingList.getInstanceWeight(instance);
      FeatureVector fv = (FeatureVector) instance.getData();

      for (MaxEntGEConstraint constraint : constraints) {
        constraint.preProcess(fv);
        for (int label = 0; label < numLabels; label++) {
          double val = constraint.getCompositeConstraintFeatureValue(fv, label);
          constraintValue[label] += val; 
          instanceExpectation += val * scores[ii][label];
        }
      }

      for (int label = 0; label < numLabels; label++) {
        if (scores[ii][label] == 0) continue;
        assert (!Double.isInfinite(scores[ii][label]));
        double weight = objWeight * instanceWeight * scores[ii][label] * (constraintValue[label] - instanceExpectation) / temperature;
        assert(!Double.isNaN(weight));
        MatrixOps.rowPlusEquals(cachedGradient, numFeatures, label, fv, weight);
        cachedGradient[numFeatures * label + defaultFeatureIndex] += weight;
      }  
    }

    cachedValue = value;
    cacheStale = false;
    
    double reg = getRegularization();
    progressLogger.info ("Value (GE=" + value + " Gaussian prior= " + reg + ") = " + cachedValue);
    
    return value;
  }

  protected double getRegularization() {
    double regularization = 0;
    for (int pi = 0; pi < parameters.length; pi++) {
      double p = parameters[pi];
      regularization -= p * p / (2 * gaussianPriorVariance);
      cachedGradient[pi] -= p / gaussianPriorVariance;
    }
    cachedValue += regularization;
    return regularization;
  }
  
  public void getValueGradient(double[] buffer) {
    if (cacheStale) {
      getValue();  
    }
    assert(buffer.length == cachedGradient.length);
    System.arraycopy (cachedGradient, 0, buffer, 0, buffer.length);
  }

  public int getNumParameters() {
    return parameters.length;
  }

  public double getParameter(int index) {
    return parameters[index];
  }

  public void getParameters(double[] buffer) {
    assert(buffer.length == parameters.length);
    System.arraycopy (parameters, 0, buffer, 0, buffer.length);
  }

  public void setParameter(int index, double value) {
    cacheStale = true;
    parameters[index] = value;
  }

  public void setParameters(double[] params) {
    assert(params.length == parameters.length);
    cacheStale = true;
    System.arraycopy (params, 0, parameters, 0, parameters.length);
  }
}