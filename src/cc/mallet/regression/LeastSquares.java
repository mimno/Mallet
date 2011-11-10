package cc.mallet.regression;

import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.optimize.*;
import cc.mallet.util.MVNormal;
import cc.mallet.util.StatFunctions;

public class LeastSquares {

	LinearRegression regression;
	double[] parameters;

	InstanceList trainingData;
	double[] residuals;

	double meanSquaredError = 0.0;
	double sumSquaredError, sumSquaredModel;
	int degreesOfFreedom;

	NumberFormat formatter;

	int precisionIndex;
	int interceptIndex;
	int dimension;

	double[] xTransposeXInverse;

	public LeastSquares(InstanceList data) {
		this(data, 0.0);
	}

	public LeastSquares(InstanceList data, double regularization) {
		trainingData = data;
		regression = new LinearRegression(trainingData.getDataAlphabet());
		parameters = regression.getParameters();

		interceptIndex = parameters.length - 2;
		precisionIndex = parameters.length - 1;

		residuals = new double[ trainingData.size() ];

		formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(8);

		// We're not concerned with the precision variable
		dimension = parameters.length - 1;

		double[] xTransposeX = new double[ dimension * dimension ];
		double[] xTransposeY = new double[dimension];
		double meanY = 0.0;

		for (Instance instance: data) {
			FeatureVector predictors = (FeatureVector) instance.getData();
			double y = ((Double) instance.getTarget()).doubleValue();

			meanY += y;

			for (int i = 0; i < predictors.numLocations(); i++) {
				int index1 = predictors.indexAtLocation(i);
				double value1 = predictors.valueAtLocation(i);
				for (int j = 0; j < predictors.numLocations(); j++) {
					int index2 = predictors.indexAtLocation(j);
					double value2 = predictors.valueAtLocation(j);

					xTransposeX[ (dimension * index1) + index2 ] += value1 * value2;
				}

				// Handle the off-diagonal intercept terms
				xTransposeX[ (dimension * index1) + interceptIndex ] += value1;
				xTransposeX[ (dimension * interceptIndex) + index1 ] += value1;

				// Now do X'y
				xTransposeY[ index1 ] += value1 * y;
			}

			// The intercept term counts the instances
			xTransposeX[ (dimension * interceptIndex) + interceptIndex ] ++;
			xTransposeY[ interceptIndex ] += y;
		}
		
		// L2 regularized regression (aka ridge regression)
		if (regularization > 0.0) {
			for (int d = 0; d < dimension; d++) {
				xTransposeX[ (dimension * d) + d ] += regularization;
			}
		}

		meanY /= data.size();
		xTransposeXInverse = MVNormal.invertSPD(xTransposeX, dimension);
		
		double oneOverNSquared = 1.0 / (data.size() * data.size());

		// Now multiply the matrix X'X^-1 by the vector X'y
		for (int index1 = 0; index1 < dimension; index1++) {
			for (int index2 = 0; index2 < dimension; index2++) {
				parameters[ index1 ] +=
					xTransposeXInverse[ (index1 * dimension) + index2 ] *
					xTransposeY[ index2 ];
			}
		}

		// Compute residuals and mean squared error
		sumSquaredError = 0.0;
		sumSquaredModel = 0.0;
		degreesOfFreedom = trainingData.size() - dimension;

		for (int i = 0; i < trainingData.size(); i++) {
			Instance instance = trainingData.get(i);

			double prediction = regression.predict(instance);
			double y = ((Double) instance.getTarget()).doubleValue();

			residuals[i] = (y - prediction);
			
			sumSquaredError += residuals[i] * residuals[i];
			sumSquaredModel += (meanY - prediction) * (meanY - prediction);
		}

		meanSquaredError = sumSquaredError / degreesOfFreedom;
	}

	/** Print a summary of the regression, similar to summary(lm(...)) in R */
	public void printSummary() {
		double standardError, tPercentile;

		System.out.println("\tparam\tStd.Err\tt value\tPr(>|t|)");
		System.out.print("(Int)\t");
		System.out.print(formatter.format(parameters[interceptIndex]) + "\t");

		standardError = 
			Math.sqrt(meanSquaredError *
					  xTransposeXInverse[(dimension * interceptIndex) + interceptIndex]);

		System.out.print(formatter.format(standardError) + "\t");
		System.out.print(formatter.format(parameters[interceptIndex] / standardError) + "\t");

		tPercentile = 
			2 * (1.0 - StatFunctions.pt(Math.abs(parameters[interceptIndex] / standardError),
										degreesOfFreedom));

		System.out.println(formatter.format(tPercentile) + " " +
						   significanceStars(tPercentile));

		for (int index=0; index < dimension - 1; index++) {
			System.out.print(trainingData.getDataAlphabet().lookupObject(index) + "\t");
			System.out.print(formatter.format(parameters[index]) + "\t");
			
			standardError = 
				Math.sqrt(meanSquaredError *
						  xTransposeXInverse[(dimension * index) + index]);
			
			System.out.print(formatter.format(standardError) + "\t");
			System.out.print(formatter.format(parameters[index] / standardError) + "\t");

			tPercentile = 
				2 * (1.0 - StatFunctions.pt(Math.abs(parameters[index] / standardError),
											degreesOfFreedom));
			
			System.out.println(formatter.format(tPercentile) + " " +
							   significanceStars(tPercentile));
		}

		System.out.println();

		System.out.println("SSE: " + formatter.format(sumSquaredError) +
						   " DF: " + degreesOfFreedom);
		System.out.println("R^2: " + 
						   formatter.format(sumSquaredModel / (sumSquaredError + sumSquaredModel)));
		
	}
 
	public String significanceStars(double p) {
		if (p < 0.001) { return "***"; }
		else if (p < 0.01) { return "**"; }
		else if (p < 0.05) { return "*"; }
		else if (p < 0.1) { return "."; }
		else return " ";
	}

	public int getNumParameters() { return parameters.length; }
	public double getParameter(int i) { return parameters[i]; }
	public void getParameters(double[] buffer) {
		for (int i=0; i < parameters.length; i++) {
			buffer[i] = parameters[i];
		}
	}

	public LinearRegression getRegression() { return regression; }
	
	public static void main (String[] args) throws Exception {
		InstanceList data = InstanceList.load(new File(args[0]));

		LeastSquares ls = null;

		if (args.length > 1) {
			ls = new LeastSquares(data, Double.parseDouble(args[1]));
		}
		else {
			ls = new LeastSquares(data);
		}

		ls.printSummary();
	}
}