package cc.mallet.fst;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import java.util.logging.Logger;

import cc.mallet.optimize.Optimizable;

import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;

import cc.mallet.util.MalletLogger;


/**
 * Implements label likelihood gradient computations for batches of data, can be
 * easily parallelized. <p>
 *
 * The gradient computations are the same as that of
 * <tt>CRFOptimizableByLabelLikelihood</tt>. <p>
 *
 * *Note*: Expectations corresponding to each batch of data can be computed in
 * parallel. During gradient computation, the prior and the constraints are
 * incorporated into the expectations of the last batch (see
 * <tt>getBatchValue, getBatchValueGradient</tt>).
 *
 * *Note*: This implementation ignores instances with infinite weights (see
 * <tt>getExpectationValue</tt>).
 *
 * @author Gaurav Chandalia
 */
public class CRFOptimizableByBatchLabelLikelihood implements Optimizable.ByCombiningBatchGradient, Serializable {
	private static Logger logger = MalletLogger.getLogger(CRFOptimizableByBatchLabelLikelihood.class.getName());

	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1.0;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SLOPE = 0.2;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS = 10.0;

	protected CRF crf;
	protected InstanceList trainingSet;

	// number of batches of training set
	protected int numBatches;

	// batch specific expectations
	protected List<CRF.Factors> expectations;
	// constraints over whole training set
	protected CRF.Factors constraints;

	// value and gradient for each batch, to avoid sharing
	protected double[] cachedValue;
	protected List<double[]> cachedGradient;

	boolean usingHyperbolicPrior = false;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double hyperbolicPriorSlope = DEFAULT_HYPERBOLIC_PRIOR_SLOPE;
	double hyperbolicPriorSharpness = DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS;

	public CRFOptimizableByBatchLabelLikelihood(CRF crf, InstanceList ilist, int numBatches) {
		// set up
		this.crf = crf;
		this.trainingSet = ilist;
		this.numBatches = numBatches;

		cachedValue = new double[this.numBatches];
		cachedGradient = new ArrayList<double[]>(this.numBatches);
		expectations = new ArrayList<CRF.Factors>(this.numBatches);
		int numFactors = crf.parameters.getNumFactors();
		for (int i = 0; i < this.numBatches; ++i) {
			cachedGradient.add(new double[numFactors]);
			expectations.add(new CRF.Factors(crf.parameters));
		}
		constraints = new CRF.Factors(crf.parameters);

		gatherConstraints(ilist);
	}

	/**
	 * Set the constraints by running forward-backward with the <i>output label
	 * sequence provided</i>, thus restricting it to only those paths that agree with
	 * the label sequence.
	 */
	protected void gatherConstraints(InstanceList ilist) {
		logger.info("Gathering constraints...");
		assert (constraints.structureMatches(crf.parameters));
		constraints.zero();

		for (Instance instance : ilist) {
			FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			double instanceWeight = ilist.getInstanceWeight(instance);
			Transducer.Incrementor incrementor =
				instanceWeight == 1.0 ? constraints.new Incrementor()
			: constraints.new WeightedIncrementor(instanceWeight);
				new SumLatticeDefault (this.crf, input, output, incrementor); 
		}
		constraints.assertNotNaNOrInfinite();
	}

	/**
	 * Computes log probability of a batch of training data, fill in corresponding
	 * expectations as well
	 */
	protected double getExpectationValue(int batchIndex, int[] batchAssignments) {
		// Reset expectations to zero before we fill them again
		CRF.Factors batchExpectations = expectations.get(batchIndex);
		batchExpectations.zero();

		// count the number of instances that have infinite weight
		int numInfLabeledWeight = 0;
		int numInfUnlabeledWeight = 0;
		int numInfWeight = 0;

		double value = 0;
		double unlabeledWeight, labeledWeight, weight;
		for (int ii = batchAssignments[0]; ii < batchAssignments[1]; ii++) {
			Instance instance = trainingSet.get(ii);
			double instanceWeight = trainingSet.getInstanceWeight(instance);
			FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();

			labeledWeight = new SumLatticeDefault (this.crf, input, output, null).getTotalWeight();
			if (Double.isInfinite (labeledWeight)) {
				++numInfLabeledWeight;
			}

			Transducer.Incrementor incrementor = instanceWeight == 1.0 ? batchExpectations.new Incrementor()
				: batchExpectations.new WeightedIncrementor (instanceWeight);
			unlabeledWeight = new SumLatticeDefault (this.crf, input, null, incrementor).getTotalWeight();
			if (Double.isInfinite (unlabeledWeight)) {
				++numInfUnlabeledWeight;
			}

			// weight is log(conditional probability correct label sequence)
			weight = labeledWeight - unlabeledWeight;
			if (Double.isInfinite(weight)) {
				++numInfWeight;
			} else {
				// Weights are log probabilities, and we want to return a log probability
				value += weight * instanceWeight;
			}
		}
		batchExpectations.assertNotNaNOrInfinite();

		if (numInfLabeledWeight > 0 || numInfUnlabeledWeight > 0 || numInfWeight > 0) {
			logger.warning("Batch: " + batchIndex + ", Number of instances with:\n" +
					"\t -infinite labeled weight: " + numInfLabeledWeight + "\n" +
					"\t -infinite unlabeled weight: " + numInfUnlabeledWeight + "\n" +
					"\t -infinite weight: " + numInfWeight);
		}
		
		return value;
	}

	/**
	 * Returns the log probability of a batch of training sequence labels and the prior over
	 * parameters, if last batch then incorporate the prior on parameters as well.
	 */
	public double getBatchValue(int batchIndex, int[] batchAssignments) {
		assert(batchIndex < this.numBatches) : "Incorrect batch index: " + batchIndex + ", range(0, " +
		this.numBatches + ")";
		assert(batchAssignments.length == 2 && batchAssignments[0] <= batchAssignments[1])
			: "Invalid batch assignments: " + Arrays.toString(batchAssignments);

		// Get the value of all the true labels for current batch, also filling in expectations
		double value = getExpectationValue(batchIndex, batchAssignments);

		if (batchIndex == numBatches-1) {
			if (usingHyperbolicPrior) // Hyperbolic prior
				value += crf.parameters.hyberbolicPrior(hyperbolicPriorSlope, hyperbolicPriorSharpness);
			else // Gaussian prior
				value += crf.parameters.gaussianPrior(gaussianPriorVariance);
		}
		assert(!(Double.isNaN(value) || Double.isInfinite(value)))
			: "Label likelihood is NaN/Infinite, batchIndex: " + batchIndex + "batchAssignments: " + Arrays.toString(batchAssignments);
		// update cache
		cachedValue[batchIndex] = value;
		
		return value;
	}

	public void getBatchValueGradient(double[] buffer, int batchIndex, int[] batchAssignments) {
		assert(batchIndex < this.numBatches) : "Incorrect batch index: " + batchIndex + ", range(0, " +
		this.numBatches + ")";
		assert(batchAssignments.length == 2 && batchAssignments[0] <= batchAssignments[1])
			: "Invalid batch assignments: " + Arrays.toString(batchAssignments);

		CRF.Factors batchExpectations = expectations.get(batchIndex);

		if (batchIndex == numBatches-1) {
			// crf parameters' check has to be done only once, infinite values are allowed
			crf.parameters.assertNotNaN();

			// factor the constraints and the prior into the expectations of last batch
			// Gradient = (constraints - expectations + prior) = -(expectations - constraints - prior)
			// The minus sign is factored in combineGradients method after all gradients are computed
			batchExpectations.plusEquals(constraints, -1.0);
			if (usingHyperbolicPrior)
				batchExpectations.plusEqualsHyperbolicPriorGradient(crf.parameters, -hyperbolicPriorSlope, hyperbolicPriorSharpness);
			else
				batchExpectations.plusEqualsGaussianPriorGradient(crf.parameters, -gaussianPriorVariance);
			batchExpectations.assertNotNaNOrInfinite();
		}

		double[] gradient = cachedGradient.get(batchIndex);
		// set the cached gradient
		batchExpectations.getParameters(gradient);
		System.arraycopy(gradient, 0, buffer, 0, gradient.length);
	}

	/**
	 * Adds gradients from all batches. <p>
	 * <b>Note:</b> assumes buffer is already initialized.
	 */
	public void combineGradients(Collection<double[]> batchGradients, double[] buffer) {
		assert(buffer.length == crf.parameters.getNumFactors())
			: "Incorrect buffer length: " + buffer.length + ", expected: " + crf.parameters.getNumFactors();

		for (double[] gradient : batchGradients) {
			MatrixOps.plusEquals(buffer, gradient);
		}
		// -(...) from getBatchValueGradient
		MatrixOps.timesEquals(buffer, -1.0);
	}

	public int getNumBatches() { return numBatches; }

	public void setUseHyperbolicPrior (boolean f) { usingHyperbolicPrior = f; }
	public void setHyperbolicPriorSlope (double p) { hyperbolicPriorSlope = p; }
	public void setHyperbolicPriorSharpness (double p) { hyperbolicPriorSharpness = p; }
	public double getUseHyperbolicPriorSlope () { return hyperbolicPriorSlope; }
	public double getUseHyperbolicPriorSharpness () { return hyperbolicPriorSharpness; }
	public void setGaussianPriorVariance (double p) { gaussianPriorVariance = p; }
	public double getGaussianPriorVariance () { return gaussianPriorVariance; }
	public int getNumParameters () {return crf.parameters.getNumFactors();}

	public void getParameters (double[] buffer) {
		crf.parameters.getParameters(buffer);
	}

	public double getParameter (int index) {
		return crf.parameters.getParameter(index);
	}

	public void setParameters (double [] buff) {
		crf.parameters.setParameters(buff);
		crf.weightsValueChanged();
	}

	public void setParameter (int index, double value) {
		crf.parameters.setParameter(index, value);
		crf.weightsValueChanged();
	}

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(trainingSet);
		out.writeObject(crf);
		out.writeInt(numBatches);
		out.writeObject(cachedValue);
		for (double[] gradient : cachedGradient)
			out.writeObject(gradient);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.readInt ();
		trainingSet = (InstanceList) in.readObject();
		crf = (CRF)in.readObject();
		numBatches = in.readInt();
		cachedValue = (double[]) in.readObject();
		cachedGradient = new ArrayList<double[]>(numBatches);
		for (int i = 0; i < numBatches; ++i)
			cachedGradient.set(i, (double[]) in.readObject());
	}

	public static class Factory {
		public Optimizable.ByCombiningBatchGradient newCRFOptimizable (CRF crf, InstanceList trainingData, int numBatches) {
			return new CRFOptimizableByBatchLabelLikelihood (crf, trainingData, numBatches);
		}
	}
}
