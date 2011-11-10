package cc.mallet.regression;

import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.optimize.*;
import cc.mallet.util.MVNormal;
import cc.mallet.util.StatFunctions;

public class CoordinateDescent {

	LinearRegression regression;
	double[] parameters;

	InstanceList trainingData;

	// Keep the unthresholded values of each parameter here
	double[] scaledResiduals;

	double tuningConstant;

	double[] sumSquaredX;
	double[] scaledThresholds;
	InvertedIndex featureIndex;

	int interceptIndex, precisionIndex, dimension;

	NumberFormat formatter;

	public CoordinateDescent (InstanceList data, double l1Weight) {

		tuningConstant = l1Weight;

		trainingData = data;
		regression = new LinearRegression(trainingData.getDataAlphabet());
		parameters = regression.getParameters();

		interceptIndex = parameters.length - 2;
		precisionIndex = parameters.length - 1;

		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(3);

		// We're not concerned with the precision variable
		dimension = parameters.length - 1;

		scaledResiduals = new double[ dimension ];
		sumSquaredX = new double[ dimension ];
		scaledThresholds = new double[ dimension ];

		featureIndex = new InvertedIndex(data);
		
		for (Instance instance: data) {
			FeatureVector predictors = (FeatureVector) instance.getData();
			double y = ((Double) instance.getTarget()).doubleValue();

			scaledResiduals[interceptIndex] += y;

			for (int i = 0; i < predictors.numLocations(); i++) {

				int index = predictors.indexAtLocation(i);
				double value = predictors.valueAtLocation(i);
				
				scaledResiduals[index] += y * value;
				sumSquaredX[index] += value * value;
			}

		}

		// data.size() is sum of squared values for the default feature
		scaledResiduals[interceptIndex] /= data.size();

		for (int index = 0; index < dimension - 1; index++) {
			scaledResiduals[index] /= sumSquaredX[index];
			scaledThresholds[index] = tuningConstant / sumSquaredX[index];
			
		}

		boolean converged = false;

		int iteration = 0;

		while (! converged) {
			
			double totalDiff = 0;
			double diff = parameters[interceptIndex] - scaledResiduals[interceptIndex];
			totalDiff += Math.abs(diff);

			// Don't use soft threshold for intercept
			parameters[interceptIndex] = scaledResiduals[interceptIndex];

			// Update scaledResiduals for remaining instances.

			for (Instance instance: data) {
				FeatureVector predictors = (FeatureVector) instance.getData();
				for (int i = 0; i < predictors.numLocations(); i++) {
					int index = predictors.indexAtLocation(i);
					double value = predictors.valueAtLocation(i);
					
					scaledResiduals[index] += value * diff / sumSquaredX[index];
				}
			}
			
			for (int index = 0; index < dimension - 1; index++) {
				diff = parameters[index];
				
				if (scaledResiduals[index] > tuningConstant) {
					parameters[index] = scaledResiduals[index] - tuningConstant;
				}
				else if (scaledResiduals[index] < -tuningConstant) {
					parameters[index] = scaledResiduals[index] + tuningConstant;
				}

				diff -= parameters[index];

				totalDiff += Math.abs(diff);

				for (Object o: featureIndex.getInstancesWithFeature(index)) {
					Instance instance = (Instance) o;
					FeatureVector predictors = (FeatureVector) instance.getData();

					// Loop through once to get the value we are changing

					double value = 0.0;

					for (int i = 0; i < predictors.numLocations(); i++) {
						if (predictors.indexAtLocation(i) == index) {
							value = predictors.valueAtLocation(i);
							break;
						}
					}

					// Update the residual for the intercept

					scaledResiduals[interceptIndex] += value * diff / data.size();

					// Update the residual for all other non-zero features

					for (int i = 0; i < predictors.numLocations(); i++) {
						int otherIndex = predictors.indexAtLocation(i);
						double otherValue = predictors.valueAtLocation(i);

						if (otherIndex != index) {
							scaledResiduals[otherIndex] += value * otherValue * diff / sumSquaredX[otherIndex];
						}
					}

				}
			}

			if (totalDiff < 0.0001) { converged = true; }
			else {
				iteration++;
				if (iteration % 100 == 0) {
					System.out.println(totalDiff);
				}
			}
		}
		

	}

	public String toString() {

		double sumSquaredError = 0.0;

		for (int i = 0; i < trainingData.size(); i++) {
			Instance instance = trainingData.get(i);

			double prediction = regression.predict(instance);
			double y = ((Double) instance.getTarget()).doubleValue();

			double residual = (y - prediction);

			sumSquaredError += residual * residual;
		}

		StringBuilder out = new StringBuilder();

		out.append("(Int)\t" + formatter.format(parameters[interceptIndex]) + "\n");
		for (int index=0; index < dimension - 1; index++) {
			out.append(trainingData.getDataAlphabet().lookupObject(index) + "\t");
			out.append(formatter.format(parameters[index]) + "\n");
		}

		out.append("SSE: " + formatter.format(sumSquaredError) + "\n");

		return out.toString();
	}

	public static void main(String[] args) throws Exception {

		InstanceList data = InstanceList.load(new File(args[0]));
		CoordinateDescent trainer = new CoordinateDescent(data, Double.parseDouble(args[1]));
		System.out.println(trainer);
	}
}