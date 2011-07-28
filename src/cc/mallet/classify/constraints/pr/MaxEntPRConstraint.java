package cc.mallet.classify.constraints.pr;

import java.util.BitSet;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.InstanceList;

public interface MaxEntPRConstraint {

  int numDimensions();
  
  double getScore(FeatureVector input, int label, double[] parameters);
  
  void incrementExpectations(FeatureVector fv, double[] dist, double weight);

  double getAuxiliaryValueContribution(double[] parameters);
  
  double getCompleteValueContribution();
  
  void getGradient(double[] parameters, double[] gradient);
  
  /**
   * Zero expectation values. Called before re-computing gradient.
   */
  void zeroExpectations();
  
  /**
   * @param data Unlabeled data
   * @return Returns a bitset of the size of the data, with the bit set if a constraint feature fires in that instance.
   */
  BitSet preProcess(InstanceList data);
  
  /**
   * Gives the constraint the option to do some caching
   * using only the FeatureVector.  For example, the
   * constrained input features could be cached.
   * 
   * @param input FeatureVector input
   */
  void preProcess(FeatureVector input);
  
  /**
   * This is used in multi-threading.  
   * 
   * @return A copy of the GEConstraint.  
   */
}
