/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.classify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import cc.mallet.fst.TokenAccuracyEvaluator;
import cc.mallet.topics.LDAHyper;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.IDSorter;
import cc.mallet.types.InfoGain;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Labeling;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.Multinomial;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;
import cc.mallet.util.Randoms;

/**
 * Utility functions for creating feature constraints that can be used with GE training.
 * @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 */

public class FeatureConstraintUtil {
  
	private static Logger logger = MalletLogger.getLogger(FeatureConstraintUtil.class.getName());
	
  /**
   * Reads feature constraints from a file, whether they are stored
   * using Strings or indices.
   * 
   * @param filename File with feature constraints.
   * @param data InstanceList used for alphabets.
   * @return Constraints.
   */
  public static HashMap<Integer,double[]> readConstraintsFromFile(String filename, InstanceList data) {
    if (testConstraintsFileIndexBased(filename)) {
      return readConstraintsFromFileIndex(filename,data);
    }
    return readConstraintsFromFileString(filename,data);
  }
  
  /**
   * Reads feature constraints stored using strings from a file.
   * 
   * feature_name (label_name:probability)+
   * 
   * Labels that do appear get probability 0.
   * 
   * @param filename File with feature constraints.
   * @param data InstanceList used for alphabets.
   * @return Constraints.
   */
  public static HashMap<Integer,double[]> readConstraintsFromFileString(String filename, InstanceList data) {
    HashMap<Integer,double[]> constraints = new HashMap<Integer,double[]>();
    
    File file = new File(filename);
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      
      String line = reader.readLine();
      while (line != null) {
        String[] split = line.split("\\s+");
        
        // assume the feature name has no spaces
        String featureName = split[0];
        int featureIndex = data.getDataAlphabet().lookupIndex(featureName,false);
        
        assert(split.length - 1 == data.getTargetAlphabet().size()) : split.length + " " + data.getTargetAlphabet().size();
        double[] probs = new double[split.length - 1];
        for (int index = 1; index < split.length; index++) {
          String[] labelSplit = split[index].split(":");   
          int li = data.getTargetAlphabet().lookupIndex(labelSplit[0],false);
          assert(li != -1) : "Label " + labelSplit[0] + " not found";
          double prob = Double.parseDouble(labelSplit[1]);
          probs[li] = prob;
        }
        constraints.put(featureIndex, probs);
        line = reader.readLine();
      }
    }
    catch (Exception e) {  
      e.printStackTrace();
      System.exit(1);
    }
    return constraints;
  }
  
  /**
   * Reads feature constraints stored using strings from a file.
   * 
   * feature_index label_0_prob label_1_prob ... label_n_prob
   * 
   * Here each label must appear.
   * 
   * @param filename File with feature constraints.
   * @param data InstanceList used for alphabets.
   * @return Constraints.
   */
  public static HashMap<Integer,double[]> readConstraintsFromFileIndex(String filename, InstanceList data) {
    HashMap<Integer,double[]> constraints = new HashMap<Integer,double[]>();
    
    File file = new File(filename);
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      
      String line = reader.readLine();
      while (line != null) {
        String[] split = line.split("\\s+");
        int featureIndex = Integer.parseInt(split[0]);
        
        assert(split.length - 1 == data.getTargetAlphabet().size());
        double[] probs = new double[split.length - 1];
        for (int index = 1; index < split.length; index++) {
          double prob = Double.parseDouble(split[index]);
          probs[index-1] = prob;
        }
        constraints.put(featureIndex, probs);
        line = reader.readLine();
      }
    }
    catch (Exception e) {  
      e.printStackTrace();
      System.exit(1);
    }
    return constraints;
  }
  
  private static boolean testConstraintsFileIndexBased(String filename) {
    File file = new File(filename);
    String firstLine = "";
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      firstLine = reader.readLine();
    }
    catch (Exception e) {  
      e.printStackTrace();
      System.exit(1);
    }
    return !firstLine.contains(":");
  }  
  
  /**
   * Select features with the highest information gain.
   * 
   * @param list InstanceList for computing information gain.
   * @param numFeatures Number of features to select.
   * @return List of features with the highest information gains.
   */
  public static ArrayList<Integer> selectFeaturesByInfoGain(InstanceList list, int numFeatures) {
    ArrayList<Integer> features = new ArrayList<Integer>();
    
    InfoGain infogain = new InfoGain(list);
    for (int rank = 0; rank < numFeatures; rank++) {
      features.add(infogain.getIndexAtRank(rank));
    }
    return features;
  }
  
  /**
   * Select top features in LDA topics.
   * 
   * @param numSelFeatures Number of features to select.
   * @param ldaEst LDAEstimatePr which provides an interface to an LDA model.
   * @param seqAlphabet The alphabet for the sequence dataset, which may be different from the vector dataset alphabet.
   * @param alphabet The vector dataset alphabet.

   * @return ArrayList with the int indices of the selected features.
   */
  public static ArrayList<Integer> selectTopLDAFeatures(int numSelFeatures, ParallelTopicModel lda, Alphabet alphabet) {
    ArrayList<Integer> features = new ArrayList<Integer>();

    Alphabet seqAlphabet = lda.getAlphabet();
    
    int numTopics = lda.getNumTopics();
    
    Object[][] sorted = lda.getTopWords(seqAlphabet.size());

    for (int pos = 0; pos < seqAlphabet.size(); pos++) {
      for (int ti = 0; ti < numTopics; ti++) {
        Object feat = sorted[ti][pos].toString();
        int fi = alphabet.lookupIndex(feat,false);
        if ((fi >=0) && (!features.contains(fi))) {
          logger.info("Selected feature: " + feat);
          features.add(fi);
          if (features.size() == numSelFeatures) {
            return features;
          }
        }
      }
    }
    return features;
  }  
  
  public static HashMap<Integer,double[]> setTargetsUsingData(InstanceList list, ArrayList<Integer> features) {
    return setTargetsUsingData(list,features,true);
  }
  
  public static HashMap<Integer,double[]> setTargetsUsingData(InstanceList list, ArrayList<Integer> features, boolean normalize) {
    return setTargetsUsingData(list,features,false,normalize);
  }
  
  /**
   * Set target distributions using estimates from data.
   * 
   * @param list InstanceList used to estimate targets.
   * @param features List of features for constraints.
   * @param normalize Whether to normalize by feature counts
   * @return Constraints (map of feature index to target), with targets
   *         set using estimates from supplied data.
   */
  public static HashMap<Integer,double[]> setTargetsUsingData(InstanceList list, ArrayList<Integer> features, boolean useValues, boolean normalize) {
    HashMap<Integer,double[]> constraints = new HashMap<Integer,double[]>();
    
    double[][] featureLabelCounts = getFeatureLabelCounts(list,useValues);

    for (int i = 0; i < features.size(); i++) {
      int fi = features.get(i);
      if (fi != list.getDataAlphabet().size()) {
        double[] prob = featureLabelCounts[fi];
        if (normalize) {
          // Smooth probability distributions by adding a (very)
          // small count.  We just need to make sure they aren't
          // zero in which case the KL-divergence is infinite.
          MatrixOps.plusEquals(prob, 1e-8);
          MatrixOps.timesEquals(prob, 1./MatrixOps.sum(prob));
        }
        constraints.put(fi, prob);
      }
    }
    return constraints;
  }
  
  /**
   * Set target distributions using "Schapire" heuristic described in 
   * "Learning from Labeled Features using Generalized Expectation Criteria"
   * Gregory Druck, Gideon Mann, Andrew McCallum.
   * 
   * @param labeledFeatures HashMap of feature indices to lists of label indices for that feature.
   * @param numLabels Total number of labels.
   * @param majorityProb Probability mass divided among majority labels.
   * @return Constraints (map of feature index to target distribution), with target
   *         distributions set using heuristic. 
   */
  public static HashMap<Integer,double[]> setTargetsUsingHeuristic(HashMap<Integer,ArrayList<Integer>> labeledFeatures, int numLabels, double majorityProb) {
    HashMap<Integer,double[]> constraints = new HashMap<Integer,double[]>();
    Iterator<Integer> keyIter = labeledFeatures.keySet().iterator();
    while (keyIter.hasNext()) {
      int fi = keyIter.next();
      ArrayList<Integer> labels = labeledFeatures.get(fi);
      constraints.put(fi, getHeuristicPrior(labels,numLabels,majorityProb));
    }
    return constraints;
  }
  
  /**
   * Set target distributions using feature voting heuristic described in 
   * "Learning from Labeled Features using Generalized Expectation Criteria"
   * Gregory Druck, Gideon Mann, Andrew McCallum.
   * 
   * @param labeledFeatures HashMap of feature indices to lists of label indices for that feature.
   * @param trainingData InstanceList to use for computing expectations with feature voting.
   * @return Constraints (map of feature index to target distribution), with target
   *         distributions set using feature voting. 
   */
  public static HashMap<Integer, double[]> setTargetsUsingFeatureVoting(HashMap<Integer,ArrayList<Integer>> labeledFeatures,
      InstanceList trainingData) {
    HashMap<Integer,double[]> constraints = new HashMap<Integer,double[]>();
    int numLabels = trainingData.getTargetAlphabet().size();

    Iterator<Integer> keyIter = labeledFeatures.keySet().iterator();
    
    double[][] featureCounts = new double[labeledFeatures.size()][numLabels];
    for (int ii = 0; ii < trainingData.size(); ii++) {
      Instance instance = trainingData.get(ii);
      FeatureVector fv = (FeatureVector)instance.getData();
      Labeling labeling = trainingData.get(ii).getLabeling();
      double[] labelDist = new double[numLabels];
      
      if (labeling == null) {
        labelByVoting(labeledFeatures,instance,labelDist);
      } else {
        int li = labeling.getBestIndex();
        labelDist[li] = 1.;
      }
  
      keyIter = labeledFeatures.keySet().iterator();
      int i = 0;
      while (keyIter.hasNext()) {
        int fi = keyIter.next();
        if (fv.location(fi) >= 0) {
          for (int li = 0; li < numLabels; li++) {
            featureCounts[i][li] += labelDist[li] * fv.valueAtLocation(fv.location(fi));
          }
        }
        i++;
      }
    }
    
    keyIter = labeledFeatures.keySet().iterator();
    int i = 0;
    while (keyIter.hasNext()) {
      int fi = keyIter.next();
      // smoothing counts
      MatrixOps.plusEquals(featureCounts[i], 1e-8);
      MatrixOps.timesEquals(featureCounts[i],1./MatrixOps.sum(featureCounts[i]));
      constraints.put(fi, featureCounts[i]);
      i++;
    }
    return constraints;
  }
  
  /**
   * Label features using heuristic described in 
   * "Learning from Labeled Features using Generalized Expectation Criteria"
   * Gregory Druck, Gideon Mann, Andrew McCallum.
   * 
   * @param list InstanceList used to compute statistics for labeling features.
   * @param features List of features to label.
   * @param reject Whether to reject labeling features.
   * @return Labeled features, HashMap mapping feature indices to list of labels.
   */
  public static HashMap<Integer, ArrayList<Integer>> labelFeatures(InstanceList list, ArrayList<Integer> features, boolean reject) {
    HashMap<Integer,ArrayList<Integer>> labeledFeatures = new HashMap<Integer,ArrayList<Integer>>();
    
    double[][] featureLabelCounts = getFeatureLabelCounts(list,true);
    
    int numLabels = list.getTargetAlphabet().size();
    
    int minRank = 100 * numLabels;
    
    InfoGain infogain = new InfoGain(list);
    double sum = 0;
    for (int rank = 0; rank < minRank; rank++) {
      sum += infogain.getValueAtRank(rank);
    }
    double mean = sum / minRank;
    
    for (int i = 0; i < features.size(); i++) {
      int fi = features.get(i);
      
      // reject features with infogain
      // less than cutoff
      if (reject && infogain.value(fi) < mean) {
        //System.err.println("Oracle labeler rejected labeling: " + list.getDataAlphabet().lookupObject(fi));
        logger.info("Oracle labeler rejected labeling: " + list.getDataAlphabet().lookupObject(fi));
        continue;
      }
      
      double[] prob = featureLabelCounts[fi];
      MatrixOps.plusEquals(prob,1e-8);
      MatrixOps.timesEquals(prob, 1./MatrixOps.sum(prob));
      int[] sortedIndices = getMaxIndices(prob);
      ArrayList<Integer> labels = new ArrayList<Integer>();

      if (numLabels > 2) {
        // take anything within a factor of 2 of the best
        // but no more than numLabels/2
        boolean discard = false;
        double threshold = prob[sortedIndices[0]] / 2;
        for (int li = 0; li < numLabels; li++) {
          if (prob[li] > threshold) {
            labels.add(li);
          }
          if (reject && labels.size() > (numLabels / 2)) {
            //System.err.println("Oracle labeler rejected labeling: " + list.getDataAlphabet().lookupObject(fi));
            logger.info("Oracle labeler rejected labeling: " + list.getDataAlphabet().lookupObject(fi));
            discard = true;
            break;
          }
        }
        if (discard) {
          continue;
        }
      }
      else {
        labels.add(sortedIndices[0]);
      }
      
      labeledFeatures.put(fi, labels);
    }
    return labeledFeatures;
  }
  
  public static HashMap<Integer, ArrayList<Integer>> labelFeatures(InstanceList list, ArrayList<Integer> features) {
  	return labelFeatures(list,features,true);
  }
  
  public static double[][] getFeatureLabelCounts(InstanceList list, boolean useValues) {
    int numFeatures = list.getDataAlphabet().size();
    int numLabels = list.getTargetAlphabet().size();
    
    double[][] featureLabelCounts = new double[numFeatures][numLabels];
    
    for (int ii = 0; ii < list.size(); ii++) {
      Instance instance = list.get(ii);
      FeatureVector featureVector = (FeatureVector)instance.getData();
      
      // this handles distributions over labels
      for (int li = 0; li < numLabels; li++) {
        double py = instance.getLabeling().value(li);
        for (int loc = 0; loc < featureVector.numLocations(); loc++) {
          int fi = featureVector.indexAtLocation(loc);
          double val;
          if (useValues) {
            val = featureVector.valueAtLocation(loc);
          }
          else {
            val = 1.0;
          }
          featureLabelCounts[fi][li] += py * val;
        }
      }
    }
    return featureLabelCounts;
  }
  
  private static double[] getHeuristicPrior (ArrayList<Integer> labeledFeatures, int numLabels, double majorityProb) {
    int numIndices = labeledFeatures.size();
    
    double[] dist = new double[numLabels];
    
    if (numIndices == numLabels) {
      for (int i = 0; i < dist.length; i++) {
        dist[i] = 1./numLabels;
      }
      return dist;
    }
    
    double keywordProb = majorityProb / numIndices;
    double otherProb = (1 - majorityProb) / (numLabels - numIndices);

    
    for (int i = 0; i < labeledFeatures.size(); i++) {
      int li = labeledFeatures.get(i);
      dist[li] = keywordProb;
    }
    
    for (int li = 0; li < numLabels; li++) {
      if (dist[li] == 0) {
        dist[li] = otherProb;
      }
    }
    
    assert(Maths.almostEquals(MatrixOps.sum(dist),1));
    return dist;
  }
  
  private static void labelByVoting(HashMap<Integer,ArrayList<Integer>> labeledFeatures, Instance instance, double[] scores) {
    FeatureVector fv = (FeatureVector)instance.getData();
    int numFeatures = instance.getDataAlphabet().size() + 1;
    
    int[] numLabels = new int[instance.getTargetAlphabet().size()];
    Iterator<Integer> keyIterator = labeledFeatures.keySet().iterator();
    while (keyIterator.hasNext()) {
      ArrayList<Integer> majorityClassList = labeledFeatures.get(keyIterator.next());
      for (int i = 0; i < majorityClassList.size(); i++) {
        int li = majorityClassList.get(i);
        numLabels[li]++;
      } 
    }
    
    keyIterator = labeledFeatures.keySet().iterator();
    while (keyIterator.hasNext()) {
      int next = keyIterator.next();
      assert(next < numFeatures);
      int loc = fv.location(next);
      if (loc < 0) {
        continue;
      }
      
      ArrayList<Integer> majorityClassList = labeledFeatures.get(next);
      for (int i = 0; i < majorityClassList.size(); i++) {
        int li = majorityClassList.get(i);
        scores[li] += 1;
      }
    }
    
    double sum = MatrixOps.sum(scores);
    if (sum == 0) {
      MatrixOps.plusEquals(scores, 1.0);
      sum = MatrixOps.sum(scores);
    }
    for (int li = 0; li < scores.length; li++) {
      scores[li] /= sum;
    }
  }
  
  /* 
   * These functions are no longer needed.
   * 
  private static double[][] getPrWordTopic(LDAHyper lda){
    int numTopics = lda.getNumTopics();
    int numTypes = lda.getAlphabet().size();
    
    double[][] prWordTopic = new double[numTopics][numTypes];
    for (int ti = 0 ; ti < numTopics; ti++){
      for (int wi = 0 ; wi < numTypes; wi++){
        prWordTopic[ti][wi] = (double) lda.getCountFeatureTopic(wi, ti) / (double) lda.getCountTokensPerTopic(ti);
      }
    }
    return prWordTopic;
  }
  
  private static int[][] getSortedTopic(double[][] prTopicWord){
    int numTopics = prTopicWord.length;
    int numTypes = prTopicWord[0].length;
    
    int[][] sortedTopicIdx = new int[numTopics][numTypes];
    for (int ti = 0; ti < numTopics; ti++){
      int[] topicIdx = getMaxIndices(prTopicWord[ti]);
      System.arraycopy(topicIdx, 0, sortedTopicIdx[ti], 0, topicIdx.length);
    }
    return sortedTopicIdx;
  }
  */
  
  
  private static int[] getMaxIndices(double[] x) {  
    ArrayList<Element> list = new ArrayList<Element>();
    for (int i = 0; i < x.length; i++) {
      Element element = new Element(i,x[i]);
      list.add(element);
    }
    Collections.sort(list);
    Collections.reverse(list);
    
    int[] sortedIndices = new int[x.length];
    for (int i = 0; i < x.length; i++) {
      sortedIndices[i] = list.get(i).index;
    }
    return sortedIndices;
  }
  
  private static class Element implements Comparable<Element> {
    private int index;
    private double value;
    
    public Element(int index, double value) {
      this.index = index;
      this.value = value;
    }
    
    public int compareTo(Element element) {
      return Double.compare(this.value, element.value);
    }
  }
}
