package cc.mallet.classify.tui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import cc.mallet.classify.FeatureConstraintUtil;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.types.Alphabet;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;

/**
 * Create "feature constraints" from data for use in GE training.
 * @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 */

public class Vectors2FeatureConstraints {

	private static Logger logger = MalletLogger.getLogger(Vectors2FeatureConstraints.class.getName());
	
  public static CommandOption.File vectorsFile = new 
    CommandOption.File(Vectors2FeatureConstraints.class, "input", "FILENAME",
    true, null, "Data file used to generate constraints.", null);
  
  public static CommandOption.File constraintsFile = new 
    CommandOption.File(Vectors2FeatureConstraints.class, "output", "FILENAME",
    true, null, "Output file for constraints.", null);
  
  public static CommandOption.File featuresFile = new 
    CommandOption.File(Vectors2FeatureConstraints.class, "features-file", "FILENAME",
    false, null, "File with list of features used to generate constraints.", null);
  
  public static CommandOption.File ldaFile = new 
    CommandOption.File(Vectors2FeatureConstraints.class, "lda-file", "FILENAME",
    false, null, "File with serialized LDA object (if using LDA feature constraint selection).", null);
  
  public static CommandOption.Integer numConstraints = new 
    CommandOption.Integer(Vectors2FeatureConstraints.class, "num-constraints", "FILENAME",
    true, 10, "Number of feature constraints.", null);
  
  public static CommandOption.String featureSelection = new 
  CommandOption.String(Vectors2FeatureConstraints.class, "feature-selection", "STRING",
  true, "infogain | lda", "Method used to choose feature constraints.", null);
  
  public static CommandOption.String targets = new 
  CommandOption.String(Vectors2FeatureConstraints.class, "targets", "STRING",
  true, "none | oracle | heuristic | voted", "Method used to estimate constraint targets.", null);
  
  public static CommandOption.Double majorityProb = new
  CommandOption.Double(Vectors2FeatureConstraints.class, "majority-prob", "DOUBLE",
      false, 0.9, "Probability for majority labels when using heuristic target estimation.", null);

  public static void main(String[] args) {
    CommandOption.process(Vectors2FeatureConstraints.class, args);
    InstanceList list = InstanceList.load(vectorsFile.value);  
    
    // Here we will assume that we use all labeled data available.  
    ArrayList<Integer> features = null;
    HashMap<Integer,ArrayList<Integer>> featuresAndLabels = null;

    // if a features file was specified, then load features from the file
    if (featuresFile.wasInvoked()) {
      if (fileContainsLabels(featuresFile.value)) {
      	// better error message from dfrankow@gmail.com
        if (targets.value.equals("oracle")) {
      	  throw new RuntimeException("with --targets oracle, features file must be unlabeled");
        }
        featuresAndLabels = readFeaturesAndLabelsFromFile(featuresFile.value, list.getDataAlphabet(), list.getTargetAlphabet());
      }
      else {
        features = readFeaturesFromFile(featuresFile.value, list.getDataAlphabet());        
      }
    }
    
    // otherwise select features using specified method
    else {
      if (featureSelection.value.equals("infogain")) {
        features = FeatureConstraintUtil.selectFeaturesByInfoGain(list,numConstraints.value);
      }
      else if (featureSelection.value.equals("lda")) {
        try {
          ObjectInputStream ois = new ObjectInputStream(new FileInputStream(ldaFile.value));
          ParallelTopicModel lda = (ParallelTopicModel)ois.readObject();
          features = FeatureConstraintUtil.selectTopLDAFeatures(numConstraints.value, lda, list.getDataAlphabet());
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
      else {
        throw new RuntimeException("Unsupported value for feature selection: " + featureSelection.value);
      }
    }
    
    // If the target method is oracle, then we do not need feature "labels".
    HashMap<Integer,double[]> constraints = null;
    
    if (targets.value.equals("none")) {
      constraints = new HashMap<Integer,double[]>();
      for (int fi : features) {     
        constraints.put(fi, null);
      }
    }
    else if (targets.value.equals("oracle")) {
      constraints = FeatureConstraintUtil.setTargetsUsingData(list, features);
    }
    else {
      // For other methods, we need to get feature labels, as
      // long as they haven't been already loaded from disk.
      if (featuresAndLabels == null) {
        featuresAndLabels = FeatureConstraintUtil.labelFeatures(list,features);
        
        for (int fi : featuresAndLabels.keySet()) {
          logger.info(list.getDataAlphabet().lookupObject(fi) + ":  ");
          for (int li : featuresAndLabels.get(fi)) {
            logger.info(list.getTargetAlphabet().lookupObject(li) + " ");
          }
        }
        
      }
      if (targets.value.equals("heuristic")) {
        constraints = FeatureConstraintUtil.setTargetsUsingHeuristic(featuresAndLabels,list.getTargetAlphabet().size(),majorityProb.value);
      }
      else if (targets.value.equals("voted")) {
        constraints = FeatureConstraintUtil.setTargetsUsingFeatureVoting(featuresAndLabels,list);
      }
      else {
        throw new RuntimeException("Unsupported value for targets: " + targets.value);
      }
    }
    writeConstraints(constraints,constraintsFile.value,list.getDataAlphabet(),list.getTargetAlphabet());  
  }
  
  private static boolean fileContainsLabels(File file) {
    String line = "";
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      line = reader.readLine().trim();
    }
    catch (Exception e) {  
      e.printStackTrace();
      System.exit(1);
    }
      
    String[] split = line.split("\\s+");
    
    if (split.length == 1) {
      return false;
    }
    return true;
  }
  
  private static ArrayList<Integer> readFeaturesFromFile(File file, Alphabet dataAlphabet) {
    ArrayList<Integer> features = new ArrayList<Integer>();
    
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      
      String line = reader.readLine();
      while (line != null) {
        line = line.trim();
        int featureIndex = dataAlphabet.lookupIndex(line,false);
        features.add(featureIndex);
        line = reader.readLine();
      }
    }
    catch (Exception e) {  
      e.printStackTrace();
      System.exit(1);
    }
    return features;
  }
  
  private static HashMap<Integer,ArrayList<Integer>> readFeaturesAndLabelsFromFile(File file, Alphabet dataAlphabet, Alphabet targetAlphabet) {
    HashMap<Integer,ArrayList<Integer>> featuresAndLabels = new HashMap<Integer,ArrayList<Integer>>();
    
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      
      String line = reader.readLine();
      while (line != null) {
        line = line.trim();
        String[] split = line.split("\\s+");
        int featureIndex = dataAlphabet.lookupIndex(split[0],false);
      	// better error message from dfrankow@gmail.com
        if (featureIndex == -1) {
        	throw new RuntimeException("Couldn't find feature '"
        	  + split[0] + "' in the data alphabet.");
        }
        
        ArrayList<Integer> labels = new ArrayList<Integer>();
        for (int i = 1; i < split.length; i++) {
          // TODO should these be label names?
          int li = targetAlphabet.lookupIndex(split[i]);
          labels.add(li);
          logger.info("found label " + li);
        }
        featuresAndLabels.put(featureIndex,labels);
        line = reader.readLine();
      }
    }
    catch (Exception e) {  
      e.printStackTrace();
      System.exit(1);
    }
    return featuresAndLabels;
  }
  
  private static void writeConstraints(HashMap<Integer,double[]> constraints, File constraintsFile, Alphabet dataAlphabet, Alphabet targetAlphabet) {
    
    if (constraints.size() == 0) {
      logger.warning("No constraints written!");
      return;
    }
    
    try {
      FileWriter writer = new FileWriter(constraintsFile);
      for (int fi : constraints.keySet()) {
        writer.write(dataAlphabet.lookupObject(fi) + " ");
        double[] p = constraints.get(fi);
        if (p != null) {
          for (int li = 0; li < p.length; li++) {
            writer.write(targetAlphabet.lookupObject(li) + ":" + p[li] + " ");
          }
        }
        writer.write("\n");
      }
      writer.close();
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
