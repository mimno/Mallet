package cc.mallet.regression;

import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.optimize.*;

public class LinearRegressionTrainer implements Optimizable.ByGradientValue {

	LinearRegression regression;
	double[] parameters;

	InstanceList trainingData;
	double[] residuals;

	boolean cachedResidualsStale = true;

	NumberFormat formatter;

	int precisionIndex;
	int interceptIndex;

	public LinearRegressionTrainer(InstanceList data) {
		trainingData = data;
		regression = new LinearRegression(trainingData.getDataAlphabet());
		parameters = regression.getParameters();

		interceptIndex = parameters.length - 2;
		precisionIndex = parameters.length - 1;

		residuals = new double[ trainingData.size() ];

		//parameters[0] = -Math.log(Math.random());
		//parameters[ interceptIndex ] = 7.0; //-Math.log(Math.random());
		parameters[ precisionIndex ] = 1.0;

		formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(3);
	}

	private void computeResiduals() {

		for (int i = 0; i < trainingData.size(); i++) {
			Instance instance = trainingData.get(i);
			residuals[i] = ((Double) instance.getTarget()).doubleValue();

			//System.out.print(residuals[i]);
			
			FeatureVector predictors = (FeatureVector) instance.getData();
			for (int location = 0; location < predictors.numLocations(); location++) {
				int index = predictors.indexAtLocation(location);
				residuals[i] -= parameters[index] * predictors.valueAtLocation(location);
				//System.out.print(" - " + formatter.format(parameters[index]) + " * " + 
				//				 formatter.format(predictors.valueAtLocation(location)));
			}

			// add the intercept term
			residuals[i] -= parameters[ interceptIndex ];
			//System.out.println(" - " + formatter.format(parameters[ parameters.length - 1 ]));
		}

		cachedResidualsStale = false;
	}

	public double getValue() {

		for (int parameter = 0; parameter < parameters.length; parameter++) {
			System.out.println(parameters[parameter]);
		}
		System.out.println();

		double logLikelihood = 0.0;

		logLikelihood += (residuals.length / 2.0) * Math.log(parameters[ precisionIndex ]);

		//if (cachedResidualsStale) { computeResiduals(); }
		computeResiduals();

		for (int i = 0; i < residuals.length; i++) {
			logLikelihood -=
				(parameters[ precisionIndex ] * parameters[ precisionIndex ]) *
				(residuals[i] * residuals[i]) / 2.0;
		}

		// Gaussian prior on non-intercept parameters
		for (int parameter = 0; parameter < parameters.length - 1; parameter++) {
			//logLikelihood -= parameters[parameter] * parameters[parameter] / 2.0;
		}
		
		return logLikelihood;
		
    }

    public void getValueGradient(double[] gradient) {
		
		//if (cachedResidualsStale) { computeResiduals(); }
		computeResiduals();

		Arrays.fill(gradient, 0.0);
		
		gradient[ precisionIndex ] += 0.5 * residuals.length / parameters[ precisionIndex ];
		
		for (int i = 0; i < residuals.length; i++) {

			Instance instance = trainingData.get(i);
            FeatureVector predictors = (FeatureVector) instance.getData();
			for (int location = 0; location < predictors.numLocations(); location++) {
				int index = predictors.indexAtLocation(location);

				if (index == 3) {
					gradient[index] += 
						(parameters[ precisionIndex ] * parameters[ precisionIndex ]) *
						(residuals[i] * predictors.valueAtLocation(location));
					//System.out.println(gradient[index] + "\t" + formatter.format(residuals[i]) + " * " +
					//formatter.format(predictors.valueAtLocation(location)) + " = " +
					//formatter.format(residuals[i] * predictors.valueAtLocation(location)));
				}
            }

			gradient[ interceptIndex ] += 
				(parameters[ precisionIndex ] * parameters[ precisionIndex ]) *
				residuals[i];

			gradient[ precisionIndex ] -= 
				parameters[ precisionIndex ] *
				residuals[i] * residuals[i];
		}

		// Include a zero-mean gaussian prior on all parameters except 
		//  the intercept term.
		for (int parameter = 0; parameter < parameters.length - 1; parameter++) {
			//gradient[parameter] -= parameters[parameter];
		}		

		for (int parameter = 0; parameter < parameters.length; parameter++) {
			System.out.println("G\t" + gradient[parameter] + "\t" + parameters[parameter]);
		}
		System.out.println();
		
    }

    // The following get/set methods satisfy the Optimizable interface

    public int getNumParameters() { return parameters.length; }
    public double getParameter(int i) { return parameters[i]; }
    public void getParameters(double[] buffer) {
		for (int i=0; i < parameters.length; i++) {
			buffer[i] = parameters[i];
		}
    }

    public void setParameter(int i, double r) {
		if (i == precisionIndex && r <= 0.0) {
			System.err.println("attempted to set precision at or less than 0");
			r = 0.001;
		}
		
		cachedResidualsStale = true;
        parameters[i] = r;
    }
    public void setParameters(double[] newParameters) {
		cachedResidualsStale = true;
		for (int i=0; i< parameters.length; i++) {
			if (i == precisionIndex && newParameters[i] <= 0.0) {
				System.err.println("attempted to set precision at or less than 0");
				parameters[i] = 0.001;
			}
			else {
				parameters[i] = newParameters[i];
			}
		}
    }

	public static void main (String[] args) throws Exception {
		InstanceList data = InstanceList.load(new File(args[0]));

		LinearRegressionTrainer trainer = new LinearRegressionTrainer(data);

		Optimizer optimizer = new OrthantWiseLimitedMemoryBFGS(trainer);
		//Optimizer optimizer = new LimitedMemoryBFGS(trainer);
		optimizer.optimize();
		optimizer.optimize();
	}
	
}