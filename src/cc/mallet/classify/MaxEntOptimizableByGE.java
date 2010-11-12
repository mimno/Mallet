package cc.mallet.classify;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import cc.mallet.optimize.Optimizable;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletProgressMessageLogger;
import cc.mallet.util.Maths;

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
public abstract class MaxEntOptimizableByGE implements Optimizable.ByGradientValue {
  
  protected boolean cacheStale = true;
  protected boolean useValues;
  protected int defaultFeatureIndex;
  protected double temperature;
  protected double objWeight;
  protected double cachedValue;
  protected double gaussianPriorVariance;
  protected double[] cachedGradient;
  protected double[] parameters;
  protected InstanceList trainingList;
  protected MaxEnt classifier;
  protected HashMap<Integer,double[]> constraints;
  protected HashMap<Integer,Integer> mapping;
  
  /**
   * @param trainingList List with unlabeled training instances.
   * @param constraints Feature expectation constraints.
   * @param initClassifier Initial classifier.
   */
  public MaxEntOptimizableByGE(InstanceList trainingList, HashMap<Integer,double[]> constraints, MaxEnt initClassifier) {
    useValues = false;
    temperature = 1.0;
    objWeight = 1.0;
    gaussianPriorVariance = 1.0;
    this.trainingList = trainingList;
    
    int numFeatures = trainingList.getDataAlphabet().size();
    defaultFeatureIndex = numFeatures;
    int numLabels = trainingList.getTargetAlphabet().size();
    parameters = new double[(numFeatures + 1) * numLabels];
    cachedGradient = new double[(numFeatures + 1) * numLabels];
    cachedValue = 0;
       
    if (classifier != null) {
      this.classifier = initClassifier;
    }
    else {
      this.classifier = new MaxEnt(trainingList.getPipe(),parameters);
    }
    
     this.constraints = constraints;
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

  public abstract double getValue();

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
    for (int i = 0; i < buffer.length; i++) {
      buffer[i] = cachedGradient[i];
    }
  }
  
  public void setUseValues(boolean flag) {
    this.useValues = flag;
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
  
  protected void setMapping() {
    int cCounter = 0;
    mapping = new HashMap<Integer,Integer>();
    Iterator<Integer> keys = constraints.keySet().iterator();
    while (keys.hasNext()) {
      int featureIndex = keys.next();
      mapping.put(featureIndex, cCounter);
      cCounter++;
    }
  }
}