package cc.mallet.fst.semi_supervised;

public abstract class GECriterion {

  	protected String name;
    protected double weight;

    // target expectation
    protected double[] target;
    // model expectation
    protected double[] expectation;

    protected double count;

    public GECriterion(String name, double[] target, double weight) {
    	this.name = name;
      this.weight = weight;
      this.target = target;
    }

    /**
     * Returns the constraint name.
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the weight (gamma) for the constraint.
     */
    public double getWeight() {
      return weight;
    }
    
    /**
     * Returns the target expectation for the feature.
     */
    public double[] getTarget() {
      return target;
    }
    
    /**
     * Returns the target expectation for the feature and label li.
     * @param li label index
     */
    public double getTarget(int li) {
    	return target[li];
    }

    /**
     * Returns the model expectation of the feature.
     */
    public double[] getExpectation() {
      return expectation;
    }
    
    /**
     * Returns the model expectation for the feature and label li.
     * @param li label index
     */
    public double getExpectation(int li) {
    	return expectation[li];
    }

    protected void setExpectation(double[] expectation) {
      this.expectation = expectation;
    }

    /**
     * Returns the count of the feature.
     */
    public double getCount() {
      return count;
    }

    protected void setCount(double count) {
      this.count = count;
    }
    
    /**
     * Returns the constant value from the gradient, which
     * will be different for different criteria.
     */
    protected abstract double getGradientConstant(int labelIndex);
}
