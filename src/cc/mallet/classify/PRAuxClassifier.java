package cc.mallet.classify;

import java.util.ArrayList;

import cc.mallet.classify.constraints.pr.MaxEntPRConstraint;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;

public class PRAuxClassifier extends Classifier {
  private static final long serialVersionUID = 1L;
  
  private int numLabels;
  private double[][] parameters;
  private ArrayList<MaxEntPRConstraint> constraints;

  public PRAuxClassifier(Pipe pipe, ArrayList<MaxEntPRConstraint> constraints) {
    super(pipe);
    this.constraints = constraints;
    this.parameters = new double[constraints.size()][];
    for (int i = 0; i < constraints.size(); i++) {
      this.parameters[i] = new double[constraints.get(i).numDimensions()];
    }
    this.numLabels = pipe.getTargetAlphabet().size();
  }
  
  public void getClassificationScores(Instance instance, double[] scores) {
    FeatureVector input = (FeatureVector)instance.getData();
    for (MaxEntPRConstraint feature : constraints) {
      feature.preProcess(input);
    }
    for (int li = 0; li < numLabels; li++) {
      int ci = 0;
      for (MaxEntPRConstraint feature : constraints) {
        scores[li] += feature.getScore(input, li, parameters[ci]);
        ci++;
      }
    }
  }
  
  public void getClassificationProbs(Instance instance, double[] scores) {
    getClassificationScores(instance,scores);
    MatrixOps.expNormalize(scores);
  }
  
  @Override
  public Classification classify(Instance instance) {
    double[] scores = new double[numLabels];
    getClassificationScores(instance,scores);
    return new Classification (instance, this, new LabelVector (getLabelAlphabet(), scores));
  }
  
  public double[][] getParameters() { 
    return parameters;
  }
  
  public ArrayList<MaxEntPRConstraint> getConstraintFeatures() { 
    return constraints;
  }

  public void zeroExpectations() {
    for (MaxEntPRConstraint constraint : constraints) {
      constraint.zeroExpectations();
    }
  }
}
