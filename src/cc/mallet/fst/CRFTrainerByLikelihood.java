package cc.mallet.fst;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.logging.Logger;

import com.sun.org.apache.xml.internal.utils.UnImplNode;

import cc.mallet.fst.Transducer;
import cc.mallet.fst.CRF.State;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.DenseVector;
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
import cc.mallet.types.Matrix;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.RankedFeatureVector;
import cc.mallet.types.Sequence;
import cc.mallet.types.SparseVector;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;

/** Unlike ClassifierTrainer, TransducerTrainer is not "stateless" between calls to train. 
 *  A TransducerTrainer is constructed paired with a specific Transducer, and can only train that Transducer.
 * 
 *  CRF stores and has methods for FeatureSelection and weight freezing.
 *  CRFTrainer stores and has methods for determining the contents/dimensions/sparsity/FeatureInduction 
 *   of the CRF's weights as determined by training data.   
 *  
 *  */

/** In the future this class may go away in favor of some default version of CRFTrainerByValueGradients... */
public class CRFTrainerByLikelihood extends TransducerTrainer implements TransducerTrainer.ByOptimization {
	private static Logger logger = MalletLogger.getLogger(CRFTrainerByLikelihood.class.getName());

	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1.0;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SLOPE = 0.2;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS = 10.0;

	CRF crf;
	//OptimizableCRF ocrf;
	CRFOptimizableByLabelLikelihood ocrf;
	Optimizer opt;
	int iterationCount = 0;
	boolean converged;
	
	boolean usingHyperbolicPrior = false;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double hyperbolicPriorSlope = DEFAULT_HYPERBOLIC_PRIOR_SLOPE;
	double hyperbolicPriorSharpness = DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS;
	boolean useSparseWeights = true;
	boolean useNoWeights = false; // TODO remove this; it is just for debugging
	private transient boolean useSomeUnsupportedTrick = true;

	// Various values from CRF acting as indicators of when we need to ...
	private int cachedValueWeightsStamp = -1;  // ... re-calculate expectations and values to getValue() because weights' values changed
	private int cachedGradientWeightsStamp = -1; // ... re-calculate to getValueGradient() because weights' values changed
	private int cachedWeightsStructureStamp = -1; // ... re-allocate crf.weights, expectations & constraints because new states, transitions
	// Use mcrf.trainingSet to see when we need to re-allocate crf.weights, expectations & constraints because we are using a different TrainingList than last time

	// xxx temporary hack.  This is quite useful to have, though!! -cas
	public boolean printGradient = false;

	
	
	public CRFTrainerByLikelihood (CRF crf) {
		this.crf = crf;
	}
	
	public Transducer getTransducer() { return crf; }
	public CRF getCRF () { return crf; }
	public Optimizer getOptimizer() { return opt; }
	public boolean isConverged() { return converged; }
	public boolean isFinishedTraining() { return converged; }
	public int getIteration () { return iterationCount; }
	

	public CRFOptimizableByLabelLikelihood getOptimizableCRF (InstanceList trainingSet) {
		if (cachedWeightsStructureStamp != crf.weightsStructureChangeStamp) {
			if (!useNoWeights) {
				if (useSparseWeights)
					crf.setWeightsDimensionAsIn (trainingSet, false);	
				else 
					crf.setWeightsDimensionDensely ();
			}
			//reallocateSufficientStatistics(); // Not necessary here because it is done in the constructor for OptimizableCRF
			ocrf = null;
			cachedWeightsStructureStamp = crf.weightsStructureChangeStamp;
		}
		if (ocrf == null || ocrf.trainingSet != trainingSet) {
			//ocrf = new OptimizableCRF (crf, trainingSet);
			ocrf = new CRFOptimizableByLabelLikelihood(crf, trainingSet);
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
	
	// Java question:
	// If I make a non-static inner class CRF.Trainer,
	// can that class by subclassed in another .java file,
	// and can that subclass still have access to all the CRF's
	// instance variables?
	// ANSWER: Yes and yes, but you have to use special syntax in the subclass ctor (see mallet-dev archive) -cas


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

	public boolean trainWithFeatureInduction (InstanceList trainingData,
	                                          InstanceList validationData, InstanceList testingData,
	                                          TransducerEvaluator eval, int numIterations,
	                                          int numIterationsBetweenFeatureInductions,
	                                          int numFeatureInductions,
	                                          int numFeaturesPerFeatureInduction,
	                                          double trueLabelProbThreshold,
	                                          boolean clusteredFeatureInduction,
	                                          double[] trainingProportions)
	{
		return trainWithFeatureInduction (trainingData, validationData, testingData,
				eval, numIterations, numIterationsBetweenFeatureInductions,
				numFeatureInductions, numFeaturesPerFeatureInduction,
				trueLabelProbThreshold, clusteredFeatureInduction,
				trainingProportions, "exp");
	}

	/**
	 * Train a CRF using feature induction to generate conjunctions of
	 * features. Feature induction is run periodically during
	 * training. The features are added to improve performance on the
	 * mislabeled instances, with the specific scoring criterion given
	 * by the {@link FeatureInducer} specified by <code>gainName</code>
	 *
	 * @param training The training Instances.
	 * @param validation The validation Instances.
	 * @param testing The testing instances.
	 * @param eval For evaluation during training.
	 * @param numIterations Maximum number of Maximizer iterations.
	 * @param numIterationsBetweenFeatureInductions Number of maximizer
	 * iterations between each call to the Feature Inducer.
	 * @param numFeatureInductions Maximum number of rounds of feature
	 * induction.
	 * @param numFeaturesPerFeatureInduction Maximum number of features
	 * to induce at each round of induction.
	 * @param trueLabelProbThreshold If the model's probability of the
	 * true Label of an Instance is less than this value, it is added as
	 * an error instance to the {@link FeatureInducer}. 
	 * @param clusteredFeatureInduction If true, a separate {@link
	 * FeatureInducer} is constructed for each label pair. This can
	 * avoid inducing a disproportionate number of features for a single
	 * label.
	 * @param trainingProportions If non-null, train on increasingly
	 * larger portions of the data (e.g. [0.2, 0.5, 1.0]. This can
	 * sometimes speedup convergence.
	 * @param gainName The type of {@link FeatureInducer} to use. One of
	 * "exp", "grad", or "info" for {@link ExpGain}, {@link
	 * GradientGain}, or {@link InfoGain}.
	 * @return True if training has converged.
	 */
	public boolean trainWithFeatureInduction (InstanceList trainingData,
	                                          InstanceList validationData, InstanceList testingData,
	                                          TransducerEvaluator eval, int numIterations,
	                                          int numIterationsBetweenFeatureInductions,
	                                          int numFeatureInductions,
	                                          int numFeaturesPerFeatureInduction,
	                                          double trueLabelProbThreshold,
	                                          boolean clusteredFeatureInduction,
	                                          double[] trainingProportions,
	                                          String gainName)
	{
		int trainingIteration = 0;
		int numLabels = crf.outputAlphabet.size();

		crf.globalFeatureSelection = trainingData.getFeatureSelection();
		if (crf.globalFeatureSelection == null) {
			// Mask out all features; some will be added later by FeatureInducer.induceFeaturesFor(.)
			crf.globalFeatureSelection = new FeatureSelection (trainingData.getDataAlphabet());
			trainingData.setFeatureSelection (crf.globalFeatureSelection);
		}
		// TODO Careful!  If validationData and testingData get removed as arguments to this method
		// then the next two lines of work will have to be done somewhere.
		if (validationData != null) validationData.setFeatureSelection (crf.globalFeatureSelection);
		if (testingData != null) testingData.setFeatureSelection (crf.globalFeatureSelection);

		for (int featureInductionIteration = 0;
		featureInductionIteration < numFeatureInductions;
		featureInductionIteration++)
		{
			// Print out some feature information
			logger.info ("Feature induction iteration "+featureInductionIteration);

			// Train the CRF
			InstanceList theTrainingData = trainingData;
			if (trainingProportions != null && featureInductionIteration < trainingProportions.length) {
				logger.info ("Training on "+trainingProportions[featureInductionIteration]+"% of the data this round.");
				InstanceList[] sampledTrainingData = trainingData.split (new Random(1),
						new double[] {trainingProportions[featureInductionIteration],
					1-trainingProportions[featureInductionIteration]});
				theTrainingData = sampledTrainingData[0];
				theTrainingData.setFeatureSelection (crf.globalFeatureSelection); // xxx necessary?
						logger.info ("  which is "+theTrainingData.size()+" instances");
			}
			boolean converged = false;
			if (featureInductionIteration != 0)
				// Don't train until we have added some features
				converged = this.train (theTrainingData, numIterationsBetweenFeatureInductions);
			trainingIteration += numIterationsBetweenFeatureInductions;

			logger.info ("Starting feature induction with "+crf.inputAlphabet.size()+" features.");

			// Create the list of error tokens, for both unclustered and clustered feature induction
			InstanceList errorInstances = new InstanceList (trainingData.getDataAlphabet(),
					trainingData.getTargetAlphabet());
			// This errorInstances.featureSelection will get examined by FeatureInducer,
			// so it can know how to add "new" singleton features
			errorInstances.setFeatureSelection (crf.globalFeatureSelection);
			ArrayList errorLabelVectors = new ArrayList();
			InstanceList clusteredErrorInstances[][] = new InstanceList[numLabels][numLabels];
			ArrayList clusteredErrorLabelVectors[][] = new ArrayList[numLabels][numLabels];

			for (int i = 0; i < numLabels; i++)
				for (int j = 0; j < numLabels; j++) {
					clusteredErrorInstances[i][j] = new InstanceList (trainingData.getDataAlphabet(),
							trainingData.getTargetAlphabet());
					clusteredErrorInstances[i][j].setFeatureSelection (crf.globalFeatureSelection);
					clusteredErrorLabelVectors[i][j] = new ArrayList();
				}

			for (int i = 0; i < theTrainingData.size(); i++) {
				logger.info ("instance="+i);
				Instance instance = theTrainingData.get(i);
				Sequence input = (Sequence) instance.getData();
				Sequence trueOutput = (Sequence) instance.getTarget();
				assert (input.size() == trueOutput.size());
				SumLattice lattice = 
					crf.sumLatticeFactory.newSumLattice (crf, input, (Sequence)null, (Transducer.Incrementor)null,	
							(LabelAlphabet)theTrainingData.getTargetAlphabet());
				int prevLabelIndex = 0;					// This will put extra error instances in this cluster
				for (int j = 0; j < trueOutput.size(); j++) {
					Label label = (Label) ((LabelSequence)trueOutput).getLabelAtPosition(j);
					assert (label != null);
					//System.out.println ("Instance="+i+" position="+j+" fv="+lattice.getLabelingAtPosition(j).toString(true));
					LabelVector latticeLabeling = lattice.getLabelingAtPosition(j);
					double trueLabelProb = latticeLabeling.value(label.getIndex());
					int labelIndex = latticeLabeling.getBestIndex();
					//System.out.println ("position="+j+" trueLabelProb="+trueLabelProb);
					if (trueLabelProb < trueLabelProbThreshold) {
						logger.info ("Adding error: instance="+i+" position="+j+" prtrue="+trueLabelProb+
								(label == latticeLabeling.getBestLabel() ? "  " : " *")+
								" truelabel="+label+
								" predlabel="+latticeLabeling.getBestLabel()+
								" fv="+((FeatureVector)input.get(j)).toString(true));
						errorInstances.add (input.get(j), label, null, null);
						errorLabelVectors.add (latticeLabeling);
						clusteredErrorInstances[prevLabelIndex][labelIndex].add (input.get(j), label, null, null);
						clusteredErrorLabelVectors[prevLabelIndex][labelIndex].add (latticeLabeling);
					}
					prevLabelIndex = labelIndex;
				}
			}
			logger.info ("Error instance list size = "+errorInstances.size());
			if (clusteredFeatureInduction) {
				FeatureInducer[][] klfi = new FeatureInducer[numLabels][numLabels];
				for (int i = 0; i < numLabels; i++) {
					for (int j = 0; j < numLabels; j++) {
						// Note that we may see some "impossible" transitions here (like O->I in a OIB model)
						// because we are using lattice gammas to get the predicted label, not Viterbi.
						// I don't believe this does any harm, and may do some good.
						logger.info ("Doing feature induction for "+
								crf.outputAlphabet.lookupObject(i)+" -> "+crf.outputAlphabet.lookupObject(j)+
								" with "+clusteredErrorInstances[i][j].size()+" instances");
						if (clusteredErrorInstances[i][j].size() < 20) {
							logger.info ("..skipping because only "+clusteredErrorInstances[i][j].size()+" instances.");
							continue;
						}
						int s = clusteredErrorLabelVectors[i][j].size();
						LabelVector[] lvs = new LabelVector[s];
						for (int k = 0; k < s; k++)
							lvs[k] = (LabelVector) clusteredErrorLabelVectors[i][j].get(k);
						RankedFeatureVector.Factory gainFactory = null;
						if (gainName.equals ("exp"))
							gainFactory = new ExpGain.Factory (lvs, gaussianPriorVariance);
						else if (gainName.equals("grad"))
							gainFactory =	new GradientGain.Factory (lvs);
						else if (gainName.equals("info"))
							gainFactory =	new InfoGain.Factory ();
						klfi[i][j] = new FeatureInducer (gainFactory,
								clusteredErrorInstances[i][j], 
								numFeaturesPerFeatureInduction,
								2*numFeaturesPerFeatureInduction,
								2*numFeaturesPerFeatureInduction);
						crf.featureInducers.add(klfi[i][j]);
					}
				}
				for (int i = 0; i < numLabels; i++) {
					for (int j = 0; j < numLabels; j++) {
						logger.info ("Adding new induced features for "+
								crf.outputAlphabet.lookupObject(i)+" -> "+crf.outputAlphabet.lookupObject(j));
						if (klfi[i][j] == null) {
							logger.info ("...skipping because no features induced.");
							continue;
						}
						// Note that this adds features globally, but not on a per-transition basis
						klfi[i][j].induceFeaturesFor (trainingData, false, false);
						if (testingData != null) klfi[i][j].induceFeaturesFor (testingData, false, false);
					}
				}
				klfi = null;
			} else {
				int s = errorLabelVectors.size();
				LabelVector[] lvs = new LabelVector[s];
				for (int i = 0; i < s; i++)
					lvs[i] = (LabelVector) errorLabelVectors.get(i);

				RankedFeatureVector.Factory gainFactory = null;
				if (gainName.equals ("exp"))
					gainFactory = new ExpGain.Factory (lvs, gaussianPriorVariance);
				else if (gainName.equals("grad"))
					gainFactory =	new GradientGain.Factory (lvs);
				else if (gainName.equals("info"))
					gainFactory =	new InfoGain.Factory ();
				FeatureInducer klfi =
					new FeatureInducer (gainFactory,
							errorInstances, 
							numFeaturesPerFeatureInduction,
							2*numFeaturesPerFeatureInduction,
							2*numFeaturesPerFeatureInduction);
				crf.featureInducers.add(klfi);
				// Note that this adds features globally, but not on a per-transition basis
				klfi.induceFeaturesFor (trainingData, false, false);
				if (testingData != null) klfi.induceFeaturesFor (testingData, false, false);
				logger.info ("CRF4 FeatureSelection now includes "+crf.globalFeatureSelection.cardinality()+" features");
				klfi = null;
			}
			// This is done in CRF4.train() anyway
			//this.setWeightsDimensionAsIn (trainingData);
			////this.growWeightsDimensionToInputAlphabet ();
		}
		return this.train (trainingData, numIterations - trainingIteration);
	}
	
	
	
	
	
	public void setUseHyperbolicPrior (boolean f) { usingHyperbolicPrior = f; }
	public void setHyperbolicPriorSlope (double p) { hyperbolicPriorSlope = p; }
	public void setHyperbolicPriorSharpness (double p) { hyperbolicPriorSharpness = p; }
	public double getUseHyperbolicPriorSlope () { return hyperbolicPriorSlope; }
	public double getUseHyperbolicPriorSharpness () { return hyperbolicPriorSharpness; }
	public void setGaussianPriorVariance (double p) { gaussianPriorVariance = p; }
	public double getGaussianPriorVariance () { return gaussianPriorVariance; }
	//public int getDefaultFeatureIndex () { return defaultFeatureIndex;}
	
	public void setUseSparseWeights (boolean b) { useSparseWeights = b; }
	public boolean getUseSparseWeights () { return useSparseWeights; }

	/** Sets whether to use the 'some unsupported trick.' This trick is, if training a CRF
	 * where some training has been done and sparse weights are used, to add a few weights
	 * for feaures that do not occur in the tainig data.
	 * <p>
	 * This generally leads to better accuracy at only a  small memory cost.
	 *
	 * @param b Whether to use the trick
	 */
	public void setUseSomeUnsupportedTrick (boolean b) { useSomeUnsupportedTrick = b; }
	

	


	// Serialization for CRFTrainerByLikelihood

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	static final int NULL_INTEGER = -1;

	/* Need to check for null pointers. */
	private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt (CURRENT_SERIAL_VERSION);
		//out.writeInt(defaultFeatureIndex);
		out.writeBoolean(usingHyperbolicPrior);
		out.writeDouble(gaussianPriorVariance);
		out.writeDouble(hyperbolicPriorSlope);
		out.writeDouble(hyperbolicPriorSharpness);
		out.writeInt(cachedGradientWeightsStamp);
		out.writeInt(cachedValueWeightsStamp);
		out.writeInt(cachedWeightsStructureStamp);
		out.writeBoolean(printGradient);
		out.writeBoolean (useSparseWeights);
		throw new IllegalStateException("Implementation not yet complete.");		
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int size, i;
		int version = in.readInt ();
		//defaultFeatureIndex = in.readInt();
		usingHyperbolicPrior = in.readBoolean();
		gaussianPriorVariance = in.readDouble();
		hyperbolicPriorSlope = in.readDouble();
		hyperbolicPriorSharpness = in.readDouble();
		printGradient = in.readBoolean();
		useSparseWeights = in.readBoolean();
		throw new IllegalStateException("Implementation not yet complete.");		
	}
	
	
}
