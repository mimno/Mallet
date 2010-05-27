package cc.mallet.fst.semi_supervised;

import java.util.Map;

public class GEKLCriteria extends GECriteria {

	public GEKLCriteria(int numStates, StateLabelMap stateLabelMap,
			Map<Integer, GECriterion> constraints) {
		super(numStates, stateLabelMap, constraints);
	}

  /**
   * Computes sum of GE constraint values. <p>
   *
   * <b>Note:</b> Label expectations are <b>not</b> re-computed here. If
   * desired, then make a call to <tt>calculateLabelExp</tt>.
   */
  public double getGEValue() {
  	//System.err.println("here!!!!!!!!!");
    double value = 0.0;
    for (int fi : constraints.keySet()) {
      GECriterion constraint = constraints.get(fi);
      if ( constraint.getCount() > 0.0) {
        double[] target = constraint.getTarget();
        double[] expectation = constraint.getExpectation();

        // value due to current constraint
        double featureValue = 0.0;
        for (int labelIndex = 0; labelIndex < stateLabelMap.getNumLabels();
             ++labelIndex) {
          if (expectation[labelIndex] > 0.0 && target[labelIndex] > 0.0) {
            // p*log(q) - p*log(p)
            featureValue +=
            	target[labelIndex] * Math.log(expectation[labelIndex]) -
            	target[labelIndex] * Math.log(target[labelIndex]);
          }
        }
  			assert(!Double.isNaN(featureValue) &&
               !Double.isInfinite(featureValue));

        value += featureValue *  constraint.getWeight();
      }
    }
    return value;
  }
}
