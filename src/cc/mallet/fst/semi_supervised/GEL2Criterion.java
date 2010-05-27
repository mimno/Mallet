package cc.mallet.fst.semi_supervised;

public class GEL2Criterion extends GECriterion {

	public GEL2Criterion(String name, double[] target, double weight) {
		super(name, target, weight);
	}

	@Override
	protected double getGradientConstant(int labelIndex) {
		return 2 * (target[labelIndex] - expectation[labelIndex]) / count;
	}
}
