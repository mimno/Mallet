/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify;
//package edu.umass.cs.mallet.users.culotta.cluster.classify;

//import edu.umass.cs.mallet.base.classify.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.ExpGain;
import cc.mallet.types.FeatureInducer;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.GradientGain;
import cc.mallet.types.InfoGain;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Labels;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.RankedFeatureVector;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.MalletProgressMessageLogger;
import cc.mallet.util.Maths;


/**
 * The trainer for a {@link RankMaxEnt} classifier. Expects Instance data to be a
 * FeatureVectorSequence, and the target to be a String representation of the
 * index of the true best FeatureVectorSequence. Note that the Instance target
 * may be a Labels to indicate a tie for the best Instance.
 * 
 *  @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

public class RankMaxEntTrainer extends MaxEntTrainer
{
	private static Logger logger = MalletLogger.getLogger(RankMaxEntTrainer.class.getName());
	private static Logger progressLogger = MalletProgressMessageLogger.getLogger(RankMaxEntTrainer.class.getName()+"-pl");

	
	public RankMaxEntTrainer () {
	}
			
	/** Constructs a trainer with a parameter to avoid overtraining.  1.0 is
	 * usually a reasonable default value. */	
	public RankMaxEntTrainer (double gaussianPriorVariance)
	{
		super (gaussianPriorVariance);
	}


	public Optimizable.ByGradientValue getMaximizableTrainer (InstanceList ilist)
	{
		if (ilist == null)
			return new MaximizableTrainer ();
		return new MaximizableTrainer (ilist, null);
	}

	public MaxEnt train (InstanceList trainingSet)
	{
		logger.fine ("trainingSet.size() = "+trainingSet.size());
		RankMaxEntTrainer.MaximizableTrainer mt =
			new RankMaxEntTrainer.MaximizableTrainer (trainingSet, (RankMaxEnt)initialClassifier);
		Optimizer maximizer = new LimitedMemoryBFGS(mt);
		maximizer.optimize (); // XXX given the loop below, this seems wrong.
 		boolean converged;

	 	for (int i = 0; i < numIterations; i++) {
			converged = maximizer.optimize (1);
			if (converged)
			 	break;
		}
		progressLogger.info("\n"); //  progess messages are on one line; move on.
		return mt.getClassifier ();
	}


	// xxx this won't work here.. must fix.
  /**
   * <p>Like the other version of <code>trainWithFeatureInduction</code>, but
   * allows some default options to be changed.</p>
   *
   * @param maxent An initial partially-trained classifier (default <code>null</code>).
   * This classifier may be modified during training.
   * @param gainName The estimate of gain (log-likelihood increase) we want our chosen
   * features to maximize.
   * Should be one of <code>MaxEntTrainer.EXP_GAIN</code>,
   * <code>MaxEntTrainer.GRADIENT_GAIN</code>, or
   * <code>MaxEntTrainer.INFORMATION_GAIN</code> (default <code>EXP_GAIN</code>).
   *
   * @return The trained <code>MaxEnt</code> classifier
   */
	/*
  public Classifier trainWithFeatureInduction (InstanceList trainingData,
                                               InstanceList validationData,
                                               InstanceList testingData,
                                               ClassifierEvaluating evaluator,
                                               MaxEnt maxent,

                                               int totalIterations,
                                               int numIterationsBetweenFeatureInductions,
                                               int numFeatureInductions,
                                               int numFeaturesPerFeatureInduction,
                                               String gainName) {

    // XXX This ought to be a parameter, except that setting it to true can
    // crash training ("Jump too small").
    boolean saveParametersDuringFI = false;
    
    Alphabet inputAlphabet = trainingData.getDataAlphabet();
    Alphabet outputAlphabet = trainingData.getTargetAlphabet();

    if (maxent == null)
      maxent = new RankMaxEnt(trainingData.getPipe(), 
															new double[(1+inputAlphabet.size()) * outputAlphabet.size()]);

		
		int trainingIteration = 0;
		int numLabels = outputAlphabet.size();

    // Initialize feature selection
		FeatureSelection globalFS = trainingData.getFeatureSelection();
		if (globalFS == null) {
			// Mask out all features; some will be added later by FeatureInducer.induceFeaturesFor(.)
			globalFS = new FeatureSelection (trainingData.getDataAlphabet());
			trainingData.setFeatureSelection (globalFS);
		}
		if (validationData != null) validationData.setFeatureSelection (globalFS);
		if (testingData != null) testingData.setFeatureSelection (globalFS);
    maxent = new RankMaxEnt(maxent.getInstancePipe(), maxent.getParameters(), globalFS);
		
    // Run feature induction
    for (int featureInductionIteration = 0;
         featureInductionIteration < numFeatureInductions;
         featureInductionIteration++) {

      // Print out some feature information
			logger.info ("Feature induction iteration "+featureInductionIteration);

			// Train the model a little bit.  We don't care whether it converges; we
      // execute all feature induction iterations no matter what.
			if (featureInductionIteration != 0) {
				// Don't train until we have added some features
        setNumIterations(numIterationsBetweenFeatureInductions);
				maxent = (RankMaxEnt)this.train (trainingData, validationData, testingData, evaluator,
																				 maxent);
      }
			trainingIteration += numIterationsBetweenFeatureInductions;

			logger.info ("Starting feature induction with "+(1+inputAlphabet.size())+
                   " features over "+numLabels+" labels.");
			
			// Create the list of error tokens
//			InstanceList errorInstances = new InstanceList (trainingData.getDataAlphabet(),
			//                                                     trainingData.getTargetAlphabet());
			InstanceList errorInstances = new InstanceList (inputAlphabet, outputAlphabet);
			// This errorInstances.featureSelection will get examined by FeatureInducer,
			// so it can know how to add "new" singleton features
			errorInstances.setFeatureSelection (globalFS);
			List errorLabelVectors = new ArrayList();    // these are length-1 vectors
      for (int i = 0; i < trainingData.size(); i++) {
				Instance inst = trainingData.get(i);
				
        // Having trained using just the current features, see how we classify
        // the training data now.
        Classification classification = maxent.classify(inst);
        if (!classification.bestLabelIsCorrect()) {
					InstanceList il = (InstanceList) inst.getData();
					Instance subInstance =
						il.get(((Integer)inst.getLabeling().getBestLabel().getEntry()).intValue());
          errorInstances.add(subInstance);
          errorLabelVectors.add(classification.getLabelVector());
//          errorLabelVectors.add(createLabelVector(subInstance, classification));
        }
      }
      logger.info ("Error instance list size = "+errorInstances.size());
      int s = errorLabelVectors.size();

      LabelVector[] lvs = new LabelVector[s];
      for (int i = 0; i < s; i++) {
        lvs[i] = (LabelVector)errorLabelVectors.get(i);
      }

      RankedFeatureVector.Factory gainFactory = null;
      if (gainName.equals (EXP_GAIN))
        gainFactory = new ExpGain.Factory (lvs, gaussianPriorVariance);
      else if (gainName.equals(GRADIENT_GAIN))
        gainFactory =	new GradientGain.Factory (lvs);
      else if (gainName.equals(INFORMATION_GAIN))
        gainFactory =	new InfoGain.Factory ();
      else
        throw new IllegalArgumentException("Unsupported gain name: "+gainName);
            
      FeatureInducer klfi =
        new FeatureInducer (gainFactory,
                            errorInstances, 
                            numFeaturesPerFeatureInduction,
                            2*numFeaturesPerFeatureInduction,
                            2*numFeaturesPerFeatureInduction);
            
      // Note that this adds features globally, but not on a per-transition basis
      klfi.induceFeaturesFor (trainingData, false, false);
      if (testingData != null) klfi.induceFeaturesFor (testingData, false, false);
      logger.info ("MaxEnt FeatureSelection now includes "+globalFS.cardinality()+" features");
      klfi = null;

      double[] newParameters = new double[(1+inputAlphabet.size()) * outputAlphabet.size()];

      // XXX (Executing this block often causes an error during training; I don't know why.)
      if (saveParametersDuringFI) {
        // Keep current parameter values
        // XXX This relies on the implementation detail that the most recent features
        // added to an Alphabet get the highest indices.
        
        // Count parameters per output label
        int oldParamCount = maxent.parameters.length / outputAlphabet.size();
        int newParamCount = 1+inputAlphabet.size();
        // Copy params into the proper locations
        for (int i=0; i<outputAlphabet.size(); i++) {
          System.arraycopy(maxent.parameters, i*oldParamCount,
                           newParameters, i*newParamCount,
                           oldParamCount);
        }
        for (int i=0; i<oldParamCount; i++)
          if (maxent.parameters[i] != newParameters[i]) {
            System.out.println(maxent.parameters[i]+" "+newParameters[i]);
            System.exit(0);
          }
      }
      
      maxent.parameters = newParameters;
      maxent.defaultFeatureIndex = inputAlphabet.size();            
    }
        
    // Finished feature induction
    logger.info("Ended with "+globalFS.cardinality()+" features.");
    setNumIterations(totalIterations - trainingIteration);
    return this.train (trainingData, validationData, testingData,
                       evaluator, maxent);
  }
  */

	
	public String toString()
	{
		return "RankMaxEntTrainer"
			//	+ "("+maximizerClass.getName()+") "
			+ ",numIterations=" + numIterations
			+ ",gaussianPriorVariance="+gaussianPriorVariance;
	}

	

  // A private inner class that wraps up a RankMaxEnt
	// classifier and its training data.  The result is a
	// maximize.Maximizable function.
	private class MaximizableTrainer implements Optimizable.ByGradientValue
	{
		double[] parameters, constraints, cachedGradient;
		RankMaxEnt theClassifier;
		InstanceList trainingList;
		// The expectations are (temporarily) stored in the cachedGradient
		double cachedValue;
		boolean cachedValueStale;
		boolean cachedGradientStale;
		int numLabels;
		int numFeatures;
		int defaultFeatureIndex;						// just for clarity
		FeatureSelection featureSelection;
		FeatureSelection[] perLabelFeatureSelection;
		
		public MaximizableTrainer (){}

		public MaximizableTrainer (InstanceList ilist, RankMaxEnt initialClassifier)
		{
			this.trainingList = ilist;
			Alphabet fd = ilist.getDataAlphabet();
			LabelAlphabet ld = (LabelAlphabet) ilist.getTargetAlphabet();
			// Don't fd.stopGrowth, because someone might want to do feature induction
			//ld.stopGrowth();
			// Add one feature for the "default feature".
			// assume underlying Instances are binary
			//this.numLabels = underlyingLabelAlphabet.size();
			// xxx
			this.numLabels = 2;

			this.numFeatures = fd.size() + 1;
			this.defaultFeatureIndex = numFeatures-1;
			this.parameters = new double [numLabels * numFeatures];
			this.constraints = new double [numLabels * numFeatures];
			this.cachedGradient = new double [numLabels * numFeatures];
			Arrays.fill (parameters, 0.0);
			Arrays.fill (constraints, 0.0);
			Arrays.fill (cachedGradient, 0.0);
			this.featureSelection = ilist.getFeatureSelection();
			this.perLabelFeatureSelection = ilist.getPerLabelFeatureSelection();
			// Add the default feature index to the selection
			if (featureSelection != null)
				featureSelection.add (defaultFeatureIndex);
			if (perLabelFeatureSelection != null)
				for (int i = 0; i < perLabelFeatureSelection.length; i++)
					perLabelFeatureSelection[i].add (defaultFeatureIndex);
			// xxx Later change this to allow both to be set, but select which one to use by a boolean flag?
			assert (featureSelection == null || perLabelFeatureSelection == null);
			if (initialClassifier != null) {        
        this.theClassifier = initialClassifier;
        this.parameters = theClassifier.parameters;
        this.featureSelection = theClassifier.featureSelection;
        this.perLabelFeatureSelection = theClassifier.perClassFeatureSelection;
        this.defaultFeatureIndex = theClassifier.defaultFeatureIndex;
				assert (initialClassifier.getInstancePipe() == ilist.getPipe());
			}
			else if (this.theClassifier == null) {
				this.theClassifier = new RankMaxEnt (ilist.getPipe(), parameters, featureSelection, perLabelFeatureSelection);
			}
			cachedValueStale = true;
			cachedGradientStale = true;

			// Initialize the constraints, using only the constraints from
			// the "positive" instance
			Iterator<Instance> iter = trainingList.iterator ();
			logger.fine("Number of instances in training list = " + trainingList.size());
			while (iter.hasNext()) {
				Instance instance = iter.next();
				double instanceWeight = trainingList.getInstanceWeight(instance);
				FeatureVectorSequence fvs = (FeatureVectorSequence) instance.getData();
				// label of best instance in subList
				Object target = instance.getTarget();
				Label label = null;
				if (target instanceof Labels)
					label = ((Labels)target).get(0);
				else label = (Label)target;
				int positiveIndex =
					Integer.valueOf(label.getBestLabel().getEntry().toString()).intValue();
				if (positiveIndex == -1) { // invalid instance
					logger.warning("True label is -1. Skipping...");
 					continue;
				}
				FeatureVector fv = (FeatureVector)fvs.get(positiveIndex);
				Alphabet fdict = fv.getAlphabet();
				assert (fv.getAlphabet() == fd);

				// xxx ensure dimensionality of constraints correct
				MatrixOps.rowPlusEquals (constraints, numFeatures, 0, fv, instanceWeight);

				// For the default feature, whose weight is 1.0
				assert(!Double.isNaN(instanceWeight)) : "instanceWeight is NaN";
				//assert(!Double.isNaN(li)) : "bestIndex is NaN";
				boolean hasNaN = false;
				for(int i = 0; i < fv.numLocations(); i++) {
					if(Double.isNaN(fv.valueAtLocation(i))) {
						logger.info("NaN for feature " + fdict.lookupObject(fv.indexAtLocation(i)).toString()); 
						hasNaN = true;
					}
				}
				if(hasNaN)
					logger.info("NaN in instance: " + instance.getName());

				// default constraints for positive instances xxx
				constraints[0*numFeatures + defaultFeatureIndex] += 1.0 * instanceWeight;
			}
			//TestMaximizable.testValueAndGradientCurrentParameters (this);
		}

		public RankMaxEnt getClassifier () { return theClassifier; }
		
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
			if (buff == null || buff.length != parameters.length)
				buff = new double [parameters.length];
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

		// log probability of the training labels, which here means the
		// probability of the positive example being labeled as such
		public double getValue ()
		{
			if (cachedValueStale) {
				cachedValue = 0;
				// We'll store the expectation values in "cachedGradient" for now
				cachedGradientStale = true;
				MatrixOps.setAll (cachedGradient, 0.0);

				// Incorporate likelihood of data
				double value = 0.0;
				Iterator<Instance> iter = trainingList.iterator();
				int ii=0;				
				while (iter.hasNext()) {
					ii++;
					Instance instance = iter.next();
					FeatureVectorSequence fvs = (FeatureVectorSequence) instance.getData();
					// scores stores Pr of subList[i] being positive instance
					double[] scores = new double[fvs.size()];
					double instanceWeight = trainingList.getInstanceWeight(instance);

					// labeling is a String representation of an int, indicating which FeatureVector from
					// the subList is the positive example					
					
					// If is String, proceed as usual. Else, if is String[], do
					// not penalize scores for duplicate entries. This improved accuracy in some expts.
					Object target = instance.getTarget();
					int li = -1;
					if (target instanceof Label) {
						li = Integer.valueOf(((Label)target).toString()).intValue();
						if (li == -1) // hack to avoid invalid instances
							continue;
						assert (li >=0 && li < fvs.size());
						this.theClassifier.getClassificationScores (instance, scores);
					} else if (target instanceof Labels){
						Labels labels = (Labels)target;
						int[] bestPositions = new int[labels.size()];
						for (int pi = 0; pi < labels.size(); pi++)
							bestPositions[pi] = Integer.valueOf(labels.get(pi).toString());
						li = bestPositions[0];
						this.theClassifier.getClassificationScoresForTies (instance, scores, bestPositions);							
					}
					value = - (instanceWeight * Math.log (scores[li]));
					if(Double.isNaN(value)) {
						logger.fine ("MaxEntTrainer: Instance " + instance.getName() +
												 "has NaN value. log(scores)= " + Math.log(scores[li]) +
												 " scores = " + scores[li] + 
												 " has instance weight = " + instanceWeight);
						
					}
					if (Double.isInfinite(value)) {
						logger.warning ("Instance "+instance.getSource() + " has infinite value; skipping value and gradient");
						cachedValue -= value;
						cachedValueStale = false;
						return -value;
					}
					cachedValue += value;
					double positiveScore = scores[li];

					
					for (int si=0; si < fvs.size(); si++) {
						if (scores[si]==0)
							continue;
						assert (!Double.isInfinite(scores[si]));
						FeatureVector cfv = (FeatureVector)fvs.get(si);
						MatrixOps.rowPlusEquals (cachedGradient, numFeatures,
																		 0, cfv, -instanceWeight * scores[si]);
						cachedGradient[numFeatures*0 + defaultFeatureIndex] += (-instanceWeight * scores[si]);						
					}
				}
				
				// Incorporate prior on parameters
				for (int li = 0; li < numLabels; li++)
					for (int fi = 0; fi < numFeatures; fi++) {
						double param = parameters[li*numFeatures + fi];
						cachedValue += param * param / (2 * gaussianPriorVariance);
					}
				cachedValue *= -1.0; // MAXIMIZE, NOT MINIMIZE
				cachedValueStale = false;
				progressLogger.info ("Value (loglikelihood) = "+cachedValue);
			}
			return cachedValue;
		}

		public void getValueGradient (double [] buffer)
		{
			// Gradient is (constraint - expectation - parameters/gaussianPriorVariance)
			if (cachedGradientStale) {
				if (cachedValueStale)
					// This will fill in the cachedGradient with the "-expectation"
					getValue ();
				MatrixOps.plusEquals (cachedGradient, constraints);
				// Incorporate prior on parameters
				MatrixOps.plusEquals (cachedGradient, parameters,	-1.0 / gaussianPriorVariance);
				
				// A parameter may be set to -infinity by an external user.
				// We set gradient to 0 because the parameter's value can
				// never change anyway and it will mess up future calculations
				// on the matrix, such as norm().
				MatrixOps.substitute (cachedGradient, Double.NEGATIVE_INFINITY, 0.0);
				// Set to zero all the gradient dimensions that are not among the selected features
				if (perLabelFeatureSelection == null) {
					for (int labelIndex = 0; labelIndex < numLabels; labelIndex++)
						MatrixOps.rowSetAll (cachedGradient, numFeatures,
																 labelIndex, 0.0, featureSelection, false);
				} else {
					for (int labelIndex = 0; labelIndex < numLabels; labelIndex++)
						MatrixOps.rowSetAll (cachedGradient, numFeatures,
																 labelIndex, 0.0,
																 perLabelFeatureSelection[labelIndex], false);
				}
				cachedGradientStale = false;
			}
			assert (buffer != null && buffer.length == parameters.length);
			System.arraycopy (cachedGradient, 0, buffer, 0, cachedGradient.length);
		}
	}
	
	// SERIALIZATION

	  private static final long serialVersionUID = 1;
	  private static final int CURRENT_SERIAL_VERSION = 1;

	  private void writeObject (ObjectOutputStream out) throws IOException {
	    out.defaultWriteObject ();
	    out.writeInt (CURRENT_SERIAL_VERSION);
	  }

	  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
	    in.defaultReadObject ();
	    int version = in.readInt ();
	  }	
}
