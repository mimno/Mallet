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
public class MaxEntOptimizableByL2GE extends MaxEntOptimizableByGE {
  
  private static Logger progressLogger = MalletProgressMessageLogger.getLogger(MaxEntOptimizableByL2GE.class.getName()+"-pl");
  
  private boolean normalize;
  
  /**
   * @param trainingList List with unlabeled training instances.
   * @param constraints Feature expectation constraints.
   * @param initClassifier Initial classifier.
   */
  public MaxEntOptimizableByL2GE(InstanceList trainingList, HashMap<Integer,double[]> constraints, MaxEnt initClassifier, boolean normalize) {
    super(trainingList,constraints,initClassifier);
    this.normalize = normalize;
  } 

  public double getValue() {   
    if (!cacheStale) {
      return cachedValue;
    }
    
    if (objWeight == 0) {
      return 0.0;
    }
    
    Arrays.fill(cachedGradient,0);

    int numRefDist = constraints.size();
    int numFeatures = trainingList.getDataAlphabet().size() + 1;
    int numLabels = trainingList.getTargetAlphabet().size();
    double scalingFactor = objWeight;      
    
    if (mapping == null) {
      // mapping maps between feature indices to 
      // constraint indices
      setMapping();
    }
    
    double[][] modelExpectations = new double[numRefDist][numLabels];
    double[][] ratio = new double[numRefDist][numLabels];
    double[] featureCounts = new double[numRefDist];

    double[][] scores = new double[trainingList.size()][numLabels];
    
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
      
      for (int loc = 0; loc < fv.numLocations(); loc++) {
        int featureIndex = fv.indexAtLocation(loc);
        if (constraints.containsKey(featureIndex)) {
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
            modelExpectations[cIndex][l] += scores[ii][l] * val * instanceWeight;
          }
        }
      }
      
      // special case of label regularization
      if (constraints.containsKey(defaultFeatureIndex)) {
        int cIndex = mapping.get(defaultFeatureIndex); 
        featureCounts[cIndex] += 1;
        for (int l = 0; l < numLabels; l++) {
          modelExpectations[cIndex][l] += scores[ii][l] * instanceWeight;
        }        
      }
    }
    
    double value = 0;
    for (int featureIndex : constraints.keySet()) {
      int cIndex = mapping.get(featureIndex);
      if (featureCounts[cIndex] > 0) {
        for (int label = 0; label < numLabels; label++) {
          double cProb = constraints.get(featureIndex)[label];
          // optionally normalize by count
          if (normalize) { modelExpectations[cIndex][label] /= featureCounts[cIndex]; }
          ratio[cIndex][label] =  2 * (cProb - modelExpectations[cIndex][label]);
          // add to the L2 term
          value -= scalingFactor * Math.pow(cProb - modelExpectations[cIndex][label],2);
        }
      }
    }

    // pass 2: determine per example gradient
    for (int ii = 0; ii < trainingList.size(); ii++) {
      Instance instance = trainingList.get(ii);
      
      // skip if labeled
      if (instance.getTarget() != null) {
        continue;
      }
      
      double instanceWeight = trainingList.getInstanceWeight(instance);
      FeatureVector fv = (FeatureVector) instance.getData();

      for (int loc = 0; loc < fv.numLocations() + 1; loc++) {
        int featureIndex;
        if (loc == fv.numLocations()) {
          featureIndex = defaultFeatureIndex;
        }
        else {
          featureIndex = fv.indexAtLocation(loc);
        }
        
        if (constraints.containsKey(featureIndex)) {
          int cIndex = mapping.get(featureIndex);

          // skip if this feature never occurred
          if (featureCounts[cIndex] == 0) {
            continue;
          }

          double val;
          if ((featureIndex == defaultFeatureIndex)||(!useValues)) {
            val = 1;
          }
          else {
            val = fv.valueAtLocation(loc);
          }
          
          if (normalize) {
            val /= featureCounts[cIndex];
          }
          
          // compute \sum_y p(y|x) \hat{g}_y / \bar{g}_y
          double instanceExpectation = 0;
          for (int label = 0; label < numLabels; label++) {
            instanceExpectation += ratio[cIndex][label] * scores[ii][label];
          }
          
          // define C = \sum_y p(y|x) g_y(y,x) \hat{g}_y / \bar{g}_y
          // compute \sum_y  p(y|x) g_y(x,y) f(x,y) * (\hat{g}_y / \bar{g}_y - C)
          for (int label = 0; label < numLabels; label++) {
            if (scores[ii][label] == 0)
              continue;
            assert (!Double.isInfinite(scores[ii][label]));
            double weight = scalingFactor * instanceWeight * temperature * val * scores[ii][label] * (ratio[cIndex][label] - instanceExpectation);
            MatrixOps.rowPlusEquals(cachedGradient, numFeatures, label, fv, weight);
            cachedGradient[numFeatures * label + defaultFeatureIndex] += weight;
          }  
        }
      }
    }

    cachedValue = value;
    cacheStale = false;
    
    double reg = getRegularization();
    progressLogger.info ("Value (GE=" + value + " Gaussian prior= " + reg + ") = " + cachedValue);
    
    return value;
  }
}