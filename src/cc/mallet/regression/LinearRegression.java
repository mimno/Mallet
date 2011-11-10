package cc.mallet.regression;

import cc.mallet.types.*;

public class LinearRegression {
	
	public double[] parameters;
	Alphabet alphabet;

    int precisionIndex;
    int interceptIndex;

	public LinearRegression (Alphabet alphabet) {

		this.alphabet = alphabet;

		// Allocate one parameter for every feature, plus an intercept term and a precision
		parameters = new double[ alphabet.size() + 2 ];
		
		interceptIndex = parameters.length - 2;
        precisionIndex = parameters.length - 1;
	}

	public double[] getParameters() { return parameters; }

	public double predict(Instance instance) {
		double prediction = parameters[interceptIndex];

		FeatureVector predictors = (FeatureVector) instance.getData();
		for (int location = 0; location < predictors.numLocations(); location++) {
			int index = predictors.indexAtLocation(location);
			prediction += parameters[index] * predictors.valueAtLocation(location);
		}

		return prediction;
	}

}