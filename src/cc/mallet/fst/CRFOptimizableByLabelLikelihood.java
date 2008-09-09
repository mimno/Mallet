package cc.mallet.fst;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.BitSet;
import java.util.logging.Logger;

import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;

import cc.mallet.optimize.Optimizable;

import cc.mallet.util.MalletLogger;

/** An objective function for CRFs that is the label likelihood plus a Gaussian or hyperbolic prior on parameters. */
public class CRFOptimizableByLabelLikelihood implements Optimizable.ByGradientValue, Serializable
{
	private static Logger logger = MalletLogger.getLogger(CRFOptimizableByLabelLikelihood.class.getName());
	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1.0;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SLOPE = 0.2;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS = 10.0;

  // gsc: changing field access to make this class extensible
	protected InstanceList trainingSet;
	protected double cachedValue = -123456789;
	protected double[] cachedGradient;
	protected BitSet infiniteValues = null;
	protected CRF crf;
	protected CRF.Factors constraints, expectations;
	// Various values from CRF acting as indicators of when we need to ...
	private int cachedValueWeightsStamp = -1;  // ... re-calculate expectations and values to getValue() because weights' values changed
	private int cachedGradientWeightsStamp = -1; // ... re-calculate to getValueGradient() because weights' values changed

	boolean usingHyperbolicPrior = false;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double hyperbolicPriorSlope = DEFAULT_HYPERBOLIC_PRIOR_SLOPE;
	double hyperbolicPriorSharpness = DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS;

	public CRFOptimizableByLabelLikelihood (CRF crf, InstanceList ilist)
	{
		// Set up
		this.crf = crf;
		this.trainingSet = ilist;
		//cachedGradient = new DenseVector (numParameters);
		cachedGradient = new double[crf.parameters.getNumFactors()];
		
		constraints = new CRF.Factors(crf.parameters);
		expectations = new CRF.Factors(crf.parameters);

		// This resets and values that may have been in expectations and constraints
		//reallocateSufficientStatistics();

		// This is unfortunately necessary, b/c cachedValue & cachedValueStale not in same place!
		cachedValueWeightsStamp = -1;
		cachedGradientWeightsStamp = -1;

		gatherConstraints (ilist);
	}

	protected void gatherConstraints (InstanceList ilist)
	{
		// Set the constraints by running forward-backward with the *output
		// label sequence provided*, thus restricting it to only those
		// paths that agree with the label sequence.
		// Zero the constraints[]
		// Reset constraints[] to zero before we fill them again
		assert (constraints.structureMatches(crf.parameters));
		constraints.zero();

		for (Instance instance : ilist) {
			FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			double instanceWeight = ilist.getInstanceWeight(instance);
			//System.out.println ("Constraint-gathering on instance "+i+" of "+ilist.size());
			Transducer.Incrementor incrementor = instanceWeight == 1.0 ? constraints.new Incrementor() : constraints.new WeightedIncrementor(instanceWeight);
			new SumLatticeDefault (this.crf, input, output, incrementor); 
		}
//		System.out.println ("testing Value and Gradient");
//		TestOptimizable.testValueAndGradientCurrentParameters (this);
	}


	// TODO Move these implementations into CRF.java, and put here stubs that call them!
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

	
	// log probability of the training sequence labels, and fill in expectations[]
	protected double getExpectationValue ()
	{
		// Instance values must either always or never be included in
		// the total values; we can't just sometimes skip a value
		// because it is infinite, this throws off the total values.
		boolean initializingInfiniteValues = false;
		double value = 0;
		if (infiniteValues == null) {
			infiniteValues = new BitSet ();
			initializingInfiniteValues = true;
		}

		// Reset expectations to zero before we fill them again
		assert (expectations.structureMatches(crf.parameters));
		expectations.zero();

		// count the number of instances that have infinite weight
		int numInfLabeledWeight = 0;
		int numInfUnlabeledWeight = 0;
		int numInfWeight = 0;
		
		// Calculate the value of each instance, and also fill in expectations
		double unlabeledWeight, labeledWeight, weight;
		for (int ii = 0; ii < trainingSet.size(); ii++) {
			Instance instance = trainingSet.get(ii);
			double instanceWeight = trainingSet.getInstanceWeight(instance);
			FeatureVectorSequence input = (FeatureVectorSequence) instance.getData();
			FeatureSequence output = (FeatureSequence) instance.getTarget();
			labeledWeight = new SumLatticeDefault (this.crf, input, output, (Transducer.Incrementor)null).getTotalWeight();
			String instanceName = instance.getName() == null ? "instance#"+ii : instance.getName().toString();
			//System.out.println ("labeledWeight = "+labeledWeight);
			if (Double.isInfinite (labeledWeight)) {
				++numInfLabeledWeight;
				logger.warning (instanceName + " has -infinite labeled weight.\n"+(instance.getSource() != null ? instance.getSource() : ""));
			}
			
			Transducer.Incrementor incrementor = instanceWeight == 1.0 ? expectations.new Incrementor() : expectations.new WeightedIncrementor (instanceWeight);
			unlabeledWeight = new SumLatticeDefault (this.crf, input, null, incrementor).getTotalWeight();
			//System.out.println ("unlabeledWeight = "+unlabeledWeight);
			if (Double.isInfinite (unlabeledWeight)) {
				++numInfUnlabeledWeight;
				logger.warning (instance.getName().toString() + " has -infinite unlabeled weight.\n"+(instance.getSource() != null ? instance.getSource() : ""));
			}
			
			// Here weight is log(conditional probability correct label sequence)
			weight = labeledWeight - unlabeledWeight;
			//System.out.println ("Instance "+ii+" CRF.MaximizableCRF.getWeight = "+weight);
			if (Double.isInfinite(weight)) {
				++numInfWeight;
				logger.warning (instanceName + " has -infinite weight; skipping.");
				if (initializingInfiniteValues)
					infiniteValues.set (ii);
				else if (!infiniteValues.get(ii))
					throw new IllegalStateException ("Instance i used to have non-infinite value, but now it has infinite value.");
				continue;
			}
      // Weights are log probabilities, and we want to return a log probability
      value += weight * instanceWeight;
		}

		if (numInfLabeledWeight > 0 || numInfUnlabeledWeight > 0 || numInfWeight > 0) {
			logger.warning("Number of instances with:\n" +
					"\t -infinite labeled weight: " + numInfLabeledWeight + "\n" +
					"\t -infinite unlabeled weight: " + numInfUnlabeledWeight + "\n" +
					"\t -infinite weight: " + numInfWeight);
		}
		
		return value;
	}

	/** Returns the log probability of the training sequence labels and the prior over parameters. */
	public double getValue ()
	{
		if (crf.weightsValueChangeStamp != cachedValueWeightsStamp) {
			// The cached value is not up to date; it was calculated for a different set of CRF weights.
			cachedValueWeightsStamp = crf.weightsValueChangeStamp;  // cachedValue will soon no longer be stale
			long startingTime = System.currentTimeMillis();
			//crf.print();

			// Get the value of all the all the true labels, also filling in expectations at the same time.
			cachedValue = getExpectationValue ();

			// Incorporate prior on parameters
			if (usingHyperbolicPrior) // Hyperbolic prior
				cachedValue += crf.parameters.hyberbolicPrior(hyperbolicPriorSlope, hyperbolicPriorSharpness);
			else // Gaussian prior
				cachedValue += crf.parameters.gaussianPrior(gaussianPriorVariance);
			
			// gsc: make sure the prior gives a correct value
			assert(!(Double.isNaN(cachedValue) || Double.isInfinite(cachedValue))) : "Label likelihood is NaN/Infinite";
			
			logger.info ("getValue() (loglikelihood, optimizable by label likelihood) = "+cachedValue);
			long endingTime = System.currentTimeMillis();
			logger.fine ("Inference milliseconds = "+(endingTime - startingTime));
		}
		return cachedValue;
	}

	// gsc: changing method from assertNotNaN to assertNotNaNOrInfinite
	private void assertNotNaNOrInfinite ()
	{
		// crf.parameters are allowed to have infinite values
		crf.parameters.assertNotNaN();
		expectations.assertNotNaNOrInfinite();
		constraints.assertNotNaNOrInfinite();
	}

	public void getValueGradient (double [] buffer)
	{
		// PriorGradient is -parameter/gaussianPriorVariance
		// Gradient is (constraint - expectation + PriorGradient)
		// == -(expectation - constraint - PriorGradient).
		// Gradient points "up-hill", i.e. in the direction of higher value
		if (cachedGradientWeightsStamp != crf.weightsValueChangeStamp) {
			cachedGradientWeightsStamp = crf.weightsValueChangeStamp; // cachedGradient will soon no longer be stale
			// This will fill in the this.expectation, updating it if necessary
			getValue ();
			assertNotNaNOrInfinite();
			
			// Gradient is constraints - expectations + prior.  We do this by -(expectations - constraints - prior).
			expectations.plusEquals(constraints, -1.0);
			if (usingHyperbolicPrior)
				expectations.plusEqualsHyperbolicPriorGradient(crf.parameters, -hyperbolicPriorSlope, hyperbolicPriorSharpness);
			else
				expectations.plusEqualsGaussianPriorGradient(crf.parameters, -gaussianPriorVariance);
			expectations.assertNotNaNOrInfinite();
			expectations.getParameters(cachedGradient);
			MatrixOps.timesEquals (cachedGradient, -1.0);  // This implements the -(...) in the above comment

			// xxx Show the feature with maximum gradient
			
			// TODO Is something like this negation still necessary?????
			// up to now we've been calculating the weightGradient.
			// take the opposite to get the valueGradient
			//cachedGradient.timesEquals (-1.0); // point uphill
			
		}
		// What the heck was this!?: if (buffer.length != this.numParameters) buffer = new double[this.numParameters];
		System.arraycopy(cachedGradient, 0, buffer, 0, cachedGradient.length);
		//Arrays.fill (buffer, 0.0);
		//System.arraycopy(cachedGradie, 0, buffer, 0, 2*crf.parameters.initialWeights.length); // TODO For now, just copy the state inital/final weights
	}

	// gsc: adding these get/set methods for the prior
	public void setUseHyperbolicPrior (boolean f) { usingHyperbolicPrior = f; }
	public void setHyperbolicPriorSlope (double p) { hyperbolicPriorSlope = p; }
	public void setHyperbolicPriorSharpness (double p) { hyperbolicPriorSharpness = p; }
	public double getUseHyperbolicPriorSlope () { return hyperbolicPriorSlope; }
	public double getUseHyperbolicPriorSharpness () { return hyperbolicPriorSharpness; }
	public void setGaussianPriorVariance (double p) { gaussianPriorVariance = p; }
	public double getGaussianPriorVariance () { return gaussianPriorVariance; }

	//Serialization of MaximizableCRF

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(trainingSet);
		out.writeDouble(cachedValue);
		out.writeObject(cachedGradient);
		out.writeObject(infiniteValues);
		out.writeObject(crf);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		trainingSet = (InstanceList) in.readObject();
		cachedValue = in.readDouble();
		cachedGradient = (double[]) in.readObject();
		infiniteValues = (BitSet) in.readObject();
		crf = (CRF)in.readObject();
	}
	
	public static class Factory {
		public Optimizable.ByGradientValue newCRFOptimizable (CRF crf, InstanceList trainingData) {
			return new CRFOptimizableByLabelLikelihood (crf, trainingData);
		}
	}
}
