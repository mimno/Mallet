package cc.mallet.fst;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.logging.Logger;

import cc.mallet.fst.CRF.Factors.Incrementor;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.ExpGain;
import cc.mallet.types.FeatureInducer;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.GradientGain;
import cc.mallet.types.InfoGain;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.RankedFeatureVector;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

/** A CRF trainer that can combine multiple objective functions, each represented by a Optmizable.ByValueGradient. */
public class CRFTrainerByValueGradients extends TransducerTrainer implements TransducerTrainer.ByOptimization {

	private static Logger logger = MalletLogger.getLogger(CRFTrainerByLikelihood.class.getName());

	CRF crf;
  // gsc: keep objects instead of classnames, this will give more flexibility to the 
  // user to setup new CRFOptimizable* objects and then pass them directly in the constructor,
  // so the CRFOptimizable inner class no longer creates CRFOptimizable* objects
	Optimizable.ByGradientValue[] optimizableByValueGradientObjects;
//	Class[] optimizableByValueGradientClasses;
	OptimizableCRF ocrf;
	Optimizer opt;
	int iterationCount = 0;
	boolean converged;
	boolean useSparseWeights = true;
	
	// Various values from CRF acting as indicators of when we need to ...
	private int cachedValueWeightsStamp = -1;  // ... re-calculate expectations and values to getValue() because weights' values changed
	private int cachedGradientWeightsStamp = -1; // ... re-calculate to getValueGradient() because weights' values changed
	private int cachedWeightsStructureStamp = -1; // ... re-allocate crf.weights, expectations & constraints because new states, transitions
	// Use mcrf.trainingSet to see when we need to re-allocate crf.weights, expectations & constraints because we are using a different TrainingList than last time

	
	public CRFTrainerByValueGradients (CRF crf, Optimizable.ByGradientValue[] optimizableByValueGradientObjects) {
		this.crf = crf;
		this.optimizableByValueGradientObjects = optimizableByValueGradientObjects;
	}
	
//	public CRFTrainerByValueGradients (CRF crf, Class... optimizableByValueGradientClasses) {
//		// The second argument is not type-checking, but it would be such a pain to define Factory classes and interfaces for this...
//		this.crf = crf;
//		this.optimizableByValueGradientClasses = optimizableByValueGradientClasses;
//	}
	
	public Transducer getTransducer() { return crf; }
	public CRF getCRF () { return crf; }
	public Optimizer getOptimizer() { return opt; }
	public boolean isConverged() { return converged; }
	public boolean isFinishedTraining() { return converged; }
	public int getIteration () { return iterationCount; }
	

	public OptimizableCRF getOptimizableCRF (InstanceList trainingSet) {
		if (cachedWeightsStructureStamp != crf.weightsStructureChangeStamp) {
				if (useSparseWeights)
					crf.setWeightsDimensionAsIn (trainingSet, false);	
				else 
					crf.setWeightsDimensionDensely ();
			ocrf = null;
			cachedWeightsStructureStamp = crf.weightsStructureChangeStamp;
		}
		if (ocrf == null || ocrf.trainingSet != trainingSet) {
			ocrf = new OptimizableCRF (crf, trainingSet);
			opt = null;
		}
		return ocrf;
	}
	
	public Optimizer getOptimizer (InstanceList trainingSet) {
		getOptimizableCRF(trainingSet); // this will set this.mcrf if necessary
		if (opt == null || ocrf != opt.getOptimizable())
			opt = new LimitedMemoryBFGS(ocrf);  // Alternative: opt = new ConjugateGradient (0.001);
		return opt;
	}
	

	public boolean trainIncremental (InstanceList training)
	{
		return train (training, Integer.MAX_VALUE);
	}


	public boolean train (InstanceList trainingSet, int numIterations) {
		if (numIterations <= 0)
			return false;
		assert (trainingSet.size() > 0);

		getOptimizableCRF(trainingSet); // This will set this.mcrf if necessary
		getOptimizer(trainingSet); // This will set this.opt if necessary

		boolean converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		for (int i = 0; i < numIterations; i++) {
			try {
				converged = opt.optimize (1);
				iterationCount++;
				logger.info ("CRF finished one iteration of maximizer, i="+i);
				runEvaluators();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				logger.info ("Catching exception; saying converged.");
				converged = true;
			}
			if (converged) {
				logger.info ("CRF training has converged, i="+i);
				break;
			}
		}
		return converged;
	}
	
	

	/**
	 * Train a CRF on various-sized subsets of the data.  This method is typically used to accelerate training by 
	 * quickly getting to reasonable parameters on only a subset of the parameters first, then on progressively more data. 
	 * @param training The training Instances.
	 * @param numIterationsPerProportion Maximum number of Maximizer iterations per training proportion.
	 * @param trainingProportions If non-null, train on increasingly
	 * larger portions of the data, e.g. new double[] {0.2, 0.5, 1.0}.  This can sometimes speedup convergence. 
	 * Be sure to end in 1.0 if you want to train on all the data in the end.  
	 * @return True if training has converged.
	 */
	public boolean train (InstanceList training, int numIterationsPerProportion, double[] trainingProportions)
	{
		int trainingIteration = 0;
		assert (trainingProportions.length > 0);
		boolean converged = false;
		for (int i = 0; i < trainingProportions.length; i++) {
			assert (trainingProportions[i] <= 1.0);
			logger.info ("Training on "+trainingProportions[i]+"% of the data this round.");
			if (trainingProportions[i] == 1.0)
				converged = this.train (training, numIterationsPerProportion);
			else 
				converged = this.train (training.split (new Random(1),	
						new double[] {trainingProportions[i],	1-trainingProportions[i]})[0], numIterationsPerProportion);
			trainingIteration += numIterationsPerProportion;
		}
		return converged;
	}
	
	public void setUseSparseWeights (boolean b) { useSparseWeights = b; }
	public boolean getUseSparseWeights () { return useSparseWeights; }

	
	
	
	/** An optimizable CRF that contains a collection of objective functions. */
	public class OptimizableCRF implements Optimizable.ByGradientValue, Serializable
	{
		InstanceList trainingSet;
		double cachedValue = -123456789;
		double[] cachedGradie;
		BitSet infiniteValues = null;
		CRF crf;
		Optimizable.ByGradientValue[] opts;
		
		protected OptimizableCRF (CRF crf, InstanceList ilist)
		{
			// Set up
			this.crf = crf;
			this.trainingSet = ilist;
			this.opts = optimizableByValueGradientObjects;
			cachedGradie = new double[crf.parameters.getNumFactors()];
			cachedValueWeightsStamp = -1;
			cachedGradientWeightsStamp = -1;
		}


//		protected OptimizableCRF (CRF crf, InstanceList ilist)
//		{
//			// Set up
//			this.crf = crf;
//			this.trainingSet = ilist;
//			cachedGradie = new double[crf.parameters.getNumFactors()];
//			Class[] parameterTypes = new Class[] {CRF.class, InstanceList.class};
//			for (int i = 0; i < optimizableByValueGradientClasses.length; i++) {
//				try {	
//					Constructor c = optimizableByValueGradientClasses[i].getConstructor(parameterTypes); 
//					opts[i] = (Optimizable.ByGradientValue) c.newInstance(crf, ilist);
//				} catch (Exception e) { throw new IllegalStateException ("Couldn't contruct Optimizable.ByGradientValue"); }
//			}
//			cachedValueWeightsStamp = -1;
//			cachedGradientWeightsStamp = -1;
//		}


		// TODO Move these implementations into CRF.java, and put here stubs that call them!
		public int getNumParameters () {
			return crf.parameters.getNumFactors();
		}

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

		

		// log probability of the training sequence labels and the prior over parameters
		public double getValue ()
		{
			if (crf.weightsValueChangeStamp != cachedValueWeightsStamp) {
				// The cached value is not up to date; it was calculated for a different set of CRF weights.
				long startingTime = System.currentTimeMillis();

				cachedValue = 0;
				for (int i = 0; i < opts.length; i++)
					cachedValue += opts[i].getValue();
				
				cachedValueWeightsStamp = crf.weightsValueChangeStamp;  // cachedValue is now no longer stale
				logger.info ("getValue() (loglikelihood) = "+cachedValue);
				logger.fine ("Inference milliseconds = "+(System.currentTimeMillis() - startingTime));
			}
			return cachedValue;
		}

		public void getValueGradient (double [] buffer)
		{
			// PriorGradient is -parameter/gaussianPriorVariance
			// Gradient is (constraint - expectation + PriorGradient)
			// == -(expectation - constraint - PriorGradient).
			// Gradient points "up-hill", i.e. in the direction of higher value
			if (cachedGradientWeightsStamp != crf.weightsValueChangeStamp) {
				getValue (); // This will fill in the this.expectation, updating it if necessary
				MatrixOps.setAll(cachedGradie, 0);
				double[] b2 = new double[buffer.length];
				for (int i = 0; i < opts.length; i++) {
					MatrixOps.setAll(b2, 0);
					opts[i].getValueGradient(b2);
					MatrixOps.plusEquals(cachedGradie, b2);
				}
				cachedGradientWeightsStamp = crf.weightsValueChangeStamp;
			}
			System.arraycopy(cachedGradie, 0, buffer, 0, cachedGradie.length);
		}

		//Serialization of MaximizableCRF

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;

		private void writeObject (ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject(trainingSet);
			out.writeDouble(cachedValue);
			out.writeObject(cachedGradie);
			out.writeObject(infiniteValues);
			out.writeObject(crf);
		}

		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			trainingSet = (InstanceList) in.readObject();
			cachedValue = in.readDouble();
			cachedGradie = (double[]) in.readObject();
			infiniteValues = (BitSet) in.readObject();
			crf = (CRF)in.readObject();
		}

	}

	// Serialization for CRFTrainerByLikelihood

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	static final int NULL_INTEGER = -1;

	/* Need to check for null pointers. */
	private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt (CURRENT_SERIAL_VERSION);
		//out.writeInt(defaultFeatureIndex);
		out.writeInt(cachedGradientWeightsStamp);
		out.writeInt(cachedValueWeightsStamp);
		out.writeInt(cachedWeightsStructureStamp);
		out.writeBoolean (useSparseWeights);
		throw new IllegalStateException("Implementation not yet complete.");		
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int size, i;
		int version = in.readInt ();
		//defaultFeatureIndex = in.readInt();
		useSparseWeights = in.readBoolean();
		throw new IllegalStateException("Implementation not yet complete.");		
	}
	
}
