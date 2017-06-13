package cc.mallet.topics;

/** This class implements the value and gradient functions for
 *   Dirichlet-multinomial Regression. See Guimaraes and Lindrooth, 
 *   for a general introduction to DMR, 
 *   and Mimno and McCallum (UAI, 2008) for an application to 
 *   multinomial mixture models.
 */

import cc.mallet.optimize.Optimizable;
import cc.mallet.classify.MaxEnt;

import cc.mallet.types.InstanceList;
import cc.mallet.types.Instance;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.MatrixOps;

import cc.mallet.util.MalletLogger;
import cc.mallet.util.MalletProgressMessageLogger;

import java.util.logging.*;
import java.util.*;

import java.text.NumberFormat;
import java.text.DecimalFormat;


public class DMROptimizable implements Optimizable.ByGradientValue {

	private static Logger logger = MalletLogger.getLogger(DMROptimizable.class.getName());
	private static Logger progressLogger = MalletProgressMessageLogger.getLogger(DMROptimizable.class.getName()+"-pl");

	MaxEnt classifier;
	InstanceList trainingList;

	int numGetValueCalls = 0;
	int numGetValueGradientCalls = 0;
	int numIterations = Integer.MAX_VALUE;

	NumberFormat formatter = null;

	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1;
	static final double DEFAULT_LARGE_GAUSSIAN_PRIOR_VARIANCE = 100;
	static final double DEFAULT_GAUSSIAN_PRIOR_MEAN = 0.0;

	double gaussianPriorMean = DEFAULT_GAUSSIAN_PRIOR_MEAN;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;

	// Allowing the default feature (the base level) to 
	//  fluctuate more freely than the feature parameters leads
	//  to much better results.
	double defaultFeatureGaussianPriorVariance = DEFAULT_LARGE_GAUSSIAN_PRIOR_VARIANCE;

	double[] parameters;
	double[] cachedGradient;

	double cachedValue;
	boolean cachedValueStale;
	boolean cachedGradientStale;
	int numLabels;
	int numFeatures;
	int defaultFeatureIndex;

	public DMROptimizable () {}

	public DMROptimizable (InstanceList instances, MaxEnt initialClassifier) {

		this.trainingList = instances;
		Alphabet alphabet = instances.getDataAlphabet();
		Alphabet labelAlphabet = instances.getTargetAlphabet();

		this.numLabels = labelAlphabet.size();

		// Add one feature for the "default feature".
		this.numFeatures = alphabet.size() + 1; // add a spot for the intercept term

		//System.out.println("num features: " + numFeatures + " numLabels: " + numLabels);

		this.defaultFeatureIndex = numFeatures - 1;

		this.parameters = new double [numLabels * numFeatures];

		//this.constraints = new double [numLabels * numFeatures];
		this.cachedGradient = new double [numLabels * numFeatures];

		if (initialClassifier != null) {
			this.classifier = initialClassifier;
			this.parameters = classifier.getParameters();
			this.defaultFeatureIndex = classifier.getDefaultFeatureIndex();
			assert (initialClassifier.getInstancePipe() == instances.getPipe());
		}
		else if (this.classifier == null) {
			this.classifier =
				new MaxEnt (instances.getPipe(), parameters);
		}

		formatter = new DecimalFormat("0.###E0");

		cachedValueStale = true;
		cachedGradientStale = true;

		// Initialize the constraints

		logger.fine("Number of instances in training list = " + trainingList.size());

		for (Instance instance : trainingList) {
			FeatureVector multinomialValues = (FeatureVector) instance.getTarget();

			if (multinomialValues == null)
				continue;

			FeatureVector features = (FeatureVector) instance.getData();
			assert (features.getAlphabet() == alphabet);

			boolean hasNaN = false;

			for (int i = 0; i < features.numLocations(); i++) {
				if (Double.isNaN(features.valueAtLocation(i))) {
					logger.info("NaN for feature " + alphabet.lookupObject(features.indexAtLocation(i)).toString()); 
					hasNaN = true;
				}
			}

			if (hasNaN) {
				logger.info("NaN in instance: " + instance.getName());
			}

		}

		//TestMaximizable.testValueAndGradientCurrentParameters (this);
	}
	
	/** Set the variance for the default features (aka intercept terms), generally 
	 *   larger than the variance for the regular features.
	 */
	public void setInterceptGaussianPriorVariance(double sigmaSquared) {
		this.defaultFeatureGaussianPriorVariance = sigmaSquared;
	}

	/** Set the variance for regular (non default) features, generally 
	 *   smaller than the variance for the default features.
	 */
	public void setRegularGaussianPriorVariance(double sigmaSquared) {
		this.gaussianPriorVariance = sigmaSquared;
	}

	public MaxEnt getClassifier () { return classifier; }

	public double getParameter (int index) {
		return parameters[index];
	}

	public void setParameter (int index, double v) {
		cachedValueStale = true;
		cachedGradientStale = true;
		parameters[index] = v;
	}

	public int getNumParameters() {
		return parameters.length;
	}

	public void getParameters (double[] buff) {
		if (buff == null || buff.length != parameters.length) {
			buff = new double [parameters.length];
		}
		System.arraycopy (parameters, 0, buff, 0, parameters.length);
	}

	public void setParameters (double [] buff) {
		assert (buff != null);
		cachedValueStale = true;
		cachedGradientStale = true;
		if (buff.length != parameters.length)
			parameters = new double[buff.length];
		System.arraycopy (buff, 0, parameters, 0, buff.length);
	}

	/** The log probability of the observed count vectors given the features. */
	public double getValue () {

		if (! cachedValueStale) { return cachedValue; }

		numGetValueCalls++;
		cachedValue = 0;

		// Incorporate likelihood of data
		double[] scores = new double[ trainingList.getTargetAlphabet().size() ];
		double value = 0.0;

		int instanceIndex = 0;
		
		for (Instance instance: trainingList) {

			FeatureVector multinomialValues = (FeatureVector) instance.getTarget();
			if (multinomialValues == null) { continue; }

			//System.out.println("L Now "+inputAlphabet.size()+" regular features.");

			// Get the predicted probability of each class
			//   under the current model parameters
			this.classifier.getUnnormalizedClassificationScores(instance, scores);

			double sumScores = 0.0;

			// Exponentiate the scores
			for (int i=0; i<scores.length; i++) {
				// Due to underflow, it's very likely that some of these scores will be 0.0.
				scores[i] = Math.exp(scores[i]);
				sumScores += scores[i];
			}

			FeatureVector features = (FeatureVector) instance.getData();

			// This is really an int, but since FeatureVectors are defined as doubles, 
			//  avoid casting.
			double totalLength = 0;

			for (int i = 0; i < multinomialValues.numLocations(); i++) {
				int label = multinomialValues.indexAtLocation(i);
				double count = multinomialValues.valueAtLocation(i);
				value += (Dirichlet.logGammaStirling(scores[label] + count) -
						  Dirichlet.logGammaStirling(scores[label]));
				totalLength += count;
			}

			value -= (Dirichlet.logGammaStirling(sumScores + totalLength) -
					  Dirichlet.logGammaStirling(sumScores));
                    
			// Error Checking:
                
			if (Double.isNaN(value)) {
				logger.fine ("DCMMaxEntTrainer: Instance " + instance.getName() +
							 "has NaN value.");

				for (int label: multinomialValues.getIndices()) {
					logger.fine ("log(scores)= " + Math.log(scores[label]) +
								 " scores = " + scores[label]);
				}
			}

			if (Double.isInfinite(value)) {
				logger.warning ("Instance " + instance.getSource() + 
								" has infinite value; skipping value and gradient");
				cachedValue -= value;
				cachedValueStale = false;
				return -value;
			}

			//System.out.println(value);

			cachedValue += value;
                
			instanceIndex++;
		}

		// Incorporate prior on parameters

		double prior = 0;

		// The log of a gaussian prior is x^2 / -2sigma^2

		for (int label = 0; label < numLabels; label++) {
			for (int feature = 0; feature < numFeatures - 1; feature++) {
				double param = parameters[label*numFeatures + feature];
				prior -= (param - gaussianPriorMean) * (param - gaussianPriorMean) / (2 * gaussianPriorVariance);
			}
			double param = parameters[label*numFeatures + defaultFeatureIndex];
			prior -= (param - gaussianPriorMean) * (param - gaussianPriorMean) /
				(2 * defaultFeatureGaussianPriorVariance);
		}

		double labelProbability = cachedValue;
		cachedValue += prior;
		cachedValueStale = false;
		progressLogger.info ("Value (likelihood=" + formatter.format(labelProbability) +
							 " prior=" + formatter.format(prior) +
							 ") = " + formatter.format(cachedValue));

		return cachedValue;
	}

	public void getValueGradient (double [] buffer) {

		MatrixOps.setAll (cachedGradient, 0.0);

		// Incorporate likelihood of data
		double[] scores = new double[ trainingList.getTargetAlphabet().size() ];

		int instanceIndex = 0;

		for (Instance instance: trainingList) {

			FeatureVector multinomialValues = (FeatureVector) instance.getTarget();
			if (multinomialValues == null) { continue; }

			// Get the predicted probability of each class
			//   under the current model parameters
			this.classifier.getUnnormalizedClassificationScores(instance, scores);

			double sumScores = 0.0;

			// Exponentiate the scores
			for (int i=0; i<scores.length; i++) {
				// Due to underflow, it's very likely that some of these scores will be 0.0.
				scores[i] = Math.exp(scores[i]);
				sumScores += scores[i];
			}

			FeatureVector features = (FeatureVector) instance.getData();

			double totalLength = 0;

			for (double count : multinomialValues.getValues()) {
				totalLength += count;
			}
			
			double digammaDifferenceForSums = 
				Dirichlet.digamma(sumScores + totalLength) -
				Dirichlet.digamma(sumScores);
			
			for (int loc = 0; loc < features.numLocations(); loc++) {
				int index = features.indexAtLocation(loc);
				double value = features.valueAtLocation(loc);
                    
				if (value == 0.0) { continue; }

				// In a FeatureVector, there's no easy way to say "do you know
				//   about this id?" so I've broken this into two for loops,
				//  one for all labels, the other for just the non-zero ones.

				for (int label=0; label<numLabels; label++) {
					cachedGradient[label * numFeatures + index] -=
						value * scores[label] * digammaDifferenceForSums;
				}

				for (int labelLoc = 0; labelLoc <multinomialValues.numLocations(); labelLoc++) {
					int label = multinomialValues.indexAtLocation(labelLoc);
					double count = multinomialValues.valueAtLocation(labelLoc);

					double diff = 0.0;
                            
					if (count < 20) {
						for (int i=0; i < count; i++) {
							diff += 1 / (scores[label] + i);
						}
					}
					else {
						diff = Dirichlet.digamma(scores[label] + count) -
							Dirichlet.digamma(scores[label]);
					}

					cachedGradient[label * numFeatures + index] +=
						value * scores[label] * diff;

				}
			}
			// Now add the default feature

			for (int label=0; label<numLabels; label++) {
				cachedGradient[label * numFeatures + defaultFeatureIndex] -=
					scores[label] * digammaDifferenceForSums;
			}


            for(int labelLoc = 0; labelLoc <multinomialValues.numLocations(); labelLoc++) {
				int label = multinomialValues.indexAtLocation(labelLoc);
                double count = multinomialValues.valueAtLocation(labelLoc);
				
				double diff = 0.0;

				if (count < 20) {
					for (int i=0; i < count; i++) {
						diff += 1 / (scores[label] + i);
					}
				}
				else {
					diff = Dirichlet.digamma(scores[label] + count) -
						Dirichlet.digamma(scores[label]);
				}

				cachedGradient[label * numFeatures + defaultFeatureIndex] +=
					scores[label] * diff;
                    

			}

		}

		numGetValueGradientCalls++;
            
		for (int label = 0; label < numLabels; label++) {
			for (int feature = 0; feature < numFeatures - 1; feature++) {
				double param = parameters[label*numFeatures + feature];

				cachedGradient[label * numFeatures + feature] -= 
					(param - gaussianPriorMean) / gaussianPriorVariance;
			}

			double param = parameters[label*numFeatures + defaultFeatureIndex];
                
			cachedGradient[label * numFeatures + defaultFeatureIndex] -= 
				(param - gaussianPriorMean) / defaultFeatureGaussianPriorVariance;
		}

		// A parameter may be set to -infinity by an external user.
		// We set gradient to 0 because the parameter's value can
		// never change anyway and it will mess up future calculations
		// on the matrix, such as norm().
		MatrixOps.substitute (cachedGradient, Double.NEGATIVE_INFINITY, 0.0);

		assert (buffer != null && buffer.length == parameters.length);
		System.arraycopy (cachedGradient, 0, buffer, 0, cachedGradient.length);
		//System.out.println ("DCMMaxEntTrainer gradient infinity norm = "+MatrixOps.infinityNorm(cachedGradient));
	}
}

