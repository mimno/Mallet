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

public class MaxEntOptimizableByGE implements Optimizable.ByGradientValue {
  
  private static Logger progressLogger = MalletProgressMessageLogger.getLogger(MaxEntOptimizableByLabelLikelihood.class.getName()+"-pl");

  private boolean cacheStale = true;
  private boolean useValues;
  private int defaultFeatureIndex;
  private double temperature;
  private double objWeight;
  private double cachedValue;
  private double gaussianPriorVariance;
  private double[] cachedGradient;
  private double[] parameters;
  private InstanceList trainingList;
  private MaxEnt classifier;
  private HashMap<Integer,double[]> refEx;
  private HashMap<Integer,Integer> mapping;
  
  public MaxEntOptimizableByGE(InstanceList trainingList, HashMap<Integer,double[]> refDist, MaxEnt classifier) {
    useValues = false;
    temperature = 1.0;
    objWeight = 1.0;
    this.trainingList = trainingList;

    
    int numFeatures = trainingList.getDataAlphabet().size();
    defaultFeatureIndex = numFeatures;
    int numLabels = trainingList.getTargetAlphabet().size();
    parameters = new double[(numFeatures + 1) * numLabels];
    cachedGradient = new double[(numFeatures + 1) * numLabels];
    cachedValue = 0;
       
    if (classifier != null) {
      this.classifier = classifier;
    }
    else {
      this.classifier = new MaxEnt(trainingList.getPipe(),parameters);
    }
    
     this.refEx = refDist;
  }
  
  public void setGaussianPriorVariance(double variance) {
    this.gaussianPriorVariance = variance;
  }
  
  public void setTemperature(double temp) {
    this.temperature = temp;
  }
  
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
    
    Arrays.fill(cachedGradient,0);

    int numRefDist = refEx.size();
    int numFeatures = trainingList.getDataAlphabet().size() + 1;
    int numLabels = trainingList.getTargetAlphabet().size();
    double scalingFactor = objWeight;      
    
    if (mapping == null) {
      setMapping();
    }
    
    double[][] modelExScores = new double[numRefDist][numLabels];
    double[][] modelExDists = new double[numRefDist][numLabels];
    double[][] ratio = new double[numRefDist][numLabels];
    double[] featureCounts = new double[numRefDist];

    double[][] scores = new double[trainingList.size()][numLabels];
    
    // pass 1: calculate model distribution
    Iterator<Instance> iter = trainingList.iterator();
    int ii = 0;
    while (iter.hasNext()) {
      Instance instance = iter.next();
      double instanceWeight = trainingList.getInstanceWeight(instance);
      
      if (instance.getTarget() != null) {
        ii++;
        continue;
      }
      
      FeatureVector fv = (FeatureVector) instance.getData();
      classifier.getClassificationScoresWithTemperature(instance, temperature, scores[ii]);
      
      for (int loc = 0; loc < fv.numLocations(); loc++) {
        int featureIndex = fv.indexAtLocation(loc);
        if (refEx.containsKey(featureIndex)) {
          int cIndex = mapping.get(featureIndex);            
          double val;
          if (!useValues) {
            val = 1.;
          }
          else {
            val = fv.valueAtLocation(loc);
          }
          featureCounts[cIndex] += val;
          for (int l = 0; l < numLabels; l++) {
            modelExScores[cIndex][l] += scores[ii][l] * val * instanceWeight;
          }
        }
      }
      
      // special case of label regularization
      if (refEx.containsKey(defaultFeatureIndex)) {
        int cIndex = mapping.get(defaultFeatureIndex); 
        featureCounts[cIndex] += 1;
        for (int l = 0; l < numLabels; l++) {
          modelExScores[cIndex][l] += scores[ii][l] * instanceWeight;
        }        
      }
      
      ii++;
    }
     
    Iterator<Integer> keys = refEx.keySet().iterator();
    while (keys.hasNext()) {
      int featureIndex = keys.next();
      int cIndex = mapping.get(featureIndex);
      if (featureCounts[cIndex] > 0) {
        for (int label = 0; label < numLabels; label++) {
          modelExDists[cIndex][label] = modelExScores[cIndex][label] / featureCounts[cIndex];
          ratio[cIndex][label] = refEx.get(featureIndex)[label] / modelExScores[cIndex][label];
        }
        assert(Maths.almostEquals(MatrixOps.sum(modelExDists[cIndex]),1));
      }
    }

    // pass 2: determine per example gradient
    iter = trainingList.iterator();
    ii = 0;
    while (iter.hasNext()) {
      Instance instance = iter.next();
      
      if (instance.getTarget() != null) {
        ii++;
        continue;
      }
      
      double instanceWeight = trainingList.getInstanceWeight(instance);
      FeatureVector fv = (FeatureVector) instance.getData();
      // (this is normalized, despite the name)

      for (int loc = 0; loc < fv.numLocations() + 1; loc++) {
        int featureIndex;
        if (loc == fv.numLocations()) {
          featureIndex = defaultFeatureIndex;
        }
        else {
          featureIndex = fv.indexAtLocation(loc);
        }
        if (refEx.containsKey(featureIndex)) {
          int cIndex = mapping.get(featureIndex);

          // skip if this feature never occured
          if (MatrixOps.sum(modelExDists[cIndex]) == 0) {
            continue;
          }

          double val;
          if ((featureIndex == defaultFeatureIndex)||(!useValues)) {
            val = 1;
          }
          else {
            val = fv.valueAtLocation(loc);
          }
          
          double x = 0;
          for (int label = 0; label < numLabels; label++) {
            x += ratio[cIndex][label] * scores[ii][label];
          }
          
          // sum over y for KL divergence
          for (int label = 0; label < numLabels; label++) {
            if (scores[ii][label] == 0)
              continue;
            assert (!Double.isInfinite(scores[ii][label]));
            // ratio * q(y|x) x_k
            double weight = scalingFactor * instanceWeight * temperature * val * scores[ii][label] * (ratio[cIndex][label] - x);

            MatrixOps.rowPlusEquals(cachedGradient, numFeatures, label, fv, weight);
            cachedGradient[numFeatures * label + defaultFeatureIndex] += weight;
          }  
        }
      }

      ii++;
    }

    double totalValue = 0;
    keys = refEx.keySet().iterator();
    while (keys.hasNext()) {
      int featureIndex = keys.next();
      int cIndex = mapping.get(featureIndex);
      
      // skip if this feature never occured
      if (MatrixOps.sum(modelExDists[cIndex])==0) {
        continue;
      }
      
      // = H ( y_j^emp , \sum_i p(y|x) ) -- cross entropy
      // = - \sum_j [ y_j^emp log 1/|u| \sum_i p(y|x) ],
      // which is the (positive!) KL(y^emp||p_\Lambda(y|x)), which we negate because we want to minimize KL.
      double value = 0.0;
      for (int label = 0; label < numLabels; label++) {
        // System.err.println("labeledScore[label]="+labeledScore[label]+" scoreSumNorm[label]"+scoreSumNorm[label]);
        value -= scalingFactor * refEx.get(featureIndex)[label] * Math.log(modelExDists[cIndex][label]);
      }

      // = - H( y_j^emp)
      for (int label = 0; label < numLabels; label++) {
        value += scalingFactor * refEx.get(featureIndex)[label] * Math.log(refEx.get(featureIndex)[label]);
      }
      totalValue -= value;
    }

    cachedValue = totalValue;
    cacheStale = false;
    
    double reg = getRegularization();
    progressLogger.info ("Value (GE=" + totalValue + " Gaussian prior= " + reg + ") = " + cachedValue);
    
    return totalValue; // This is the -KL(p1||p2) = - H(p1,p2) + H(p1)
  }

  public double getRegularization() {
    double regularization;
    if (!Double.isInfinite(gaussianPriorVariance)) {
      regularization = Math.log(gaussianPriorVariance * Math.sqrt(2 * Math.PI));
    }
    else {
      regularization = 0;
    }
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
  
  private void setMapping() {
    int cCounter = 0;
    mapping = new HashMap<Integer,Integer>();
    Iterator<Integer> keys = refEx.keySet().iterator();
    while (keys.hasNext()) {
      int featureIndex = keys.next();
      mapping.put(featureIndex, cCounter);
      cCounter++;
    }
  }
}
