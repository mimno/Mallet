package cc.mallet.fst.semi_supervised;

public class GEKLCriterion extends GECriterion {
	public GEKLCriterion(String name, double[] target, double weight) {
		super(name, target, weight);
	}

	/**
	 * Returns the target/expectation ratio required in lattice computations. <p>
	 *
	 * *Note*: The ratio is divided by the feature count if the label expectations
	 * have been normalized.
	 */
	@Override
	protected double getGradientConstant(int labelIndex) {
		return target[labelIndex] / (expectation[labelIndex] * count);
	}
}
