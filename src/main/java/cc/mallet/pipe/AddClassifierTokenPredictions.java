/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.logging.Logger;

import cc.mallet.classify.BalancedWinnowTrainer;
import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.Trial;
import cc.mallet.types.Alphabet;
import cc.mallet.types.AugmentableFeatureVector;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Labeling;
import cc.mallet.util.MalletLogger;


/**
 * This pipe uses a Classifier to label each token (i.e., using 0-th order Markov assumption), 
 * then adds the predictions as features to each token.
 * 
 * This pipe assumes the input Instance's data is of type FeatureVectorSequence 
 * (each an augmentable feature vector). 
 * 
 * Example usage:<pre>
 * 		1) Create and serialize a featurePipe that converts raw input to FeatureVectorSequences
 * 		2) Pipe input data through featurePipe, train a TokenClassifiers via cross validation, then serialize the classifiers
 * 		2) Pipe input data through featurePipe and this pipe (using the saved classifiers), and train a Transducer 
 * 		4) Serialize the trained Transducer 
 * </pre>
 * @author ghuang
 */
public class AddClassifierTokenPredictions extends Pipe implements Serializable 
{
	private static Logger logger = MalletLogger.getLogger(AddClassifierTokenPredictions.class.getName());
	
	// Specify which predictions are to be added as features.  
	// E.g., { 1, 2 } = add labels of the top 2 highest-scoring predictions as features.
	int[] m_predRanks2add;
	
	// The trained token classifier 
	TokenClassifiers m_tokenClassifiers; 

	// Whether to treat each instance's feature values as binary 
	boolean m_binary;

	// Whether the pipe is currently being used at production time 
	// (i.e., not being used as pipeline for training a transducer)   
	boolean m_inProduction;

	// Augmented data alphabet that includes the class predictions
	Alphabet m_dataAlphabet;
	
	
	public AddClassifierTokenPredictions(InstanceList trainList)
	{
		this(trainList, null);
	}

	
	public AddClassifierTokenPredictions(InstanceList trainList, InstanceList testList)
	{
		this(new TokenClassifiers(convert(trainList, (Noop) trainList.getPipe())), new int[] { 1 }, true, 
					convert(testList, (Noop) trainList.getPipe()));
	}
	
	
	public AddClassifierTokenPredictions(TokenClassifiers tokenClassifiers, int[] predRanks2add, 
			boolean binary, InstanceList testList)
	{
		m_predRanks2add = predRanks2add;
		m_binary = binary;
		m_tokenClassifiers = tokenClassifiers;
		m_inProduction = false;
		m_dataAlphabet = (Alphabet) tokenClassifiers.getAlphabet().clone();
		Alphabet labelAlphabet = tokenClassifiers.getLabelAlphabet();
		
		// add the token prediction features to the alphabet
		for (int i = 0; i < m_predRanks2add.length; i++) {
			for (int j = 0; j < labelAlphabet.size(); j++) {
				String featName = "TOK_PRED=" + labelAlphabet.lookupObject(j).toString() + "_@_RANK_" + m_predRanks2add[i];
				m_dataAlphabet.lookupIndex(featName, true);
			}
		}
		
		// evaluate token classifier  
		if (testList != null) {
			Trial trial = new Trial(m_tokenClassifiers, testList);
			logger.info("Token classifier accuracy on test set = " + trial.getAccuracy());
		}
	}

	
	public void setInProduction(boolean inProduction) { m_inProduction = inProduction; }
	public boolean getInProduction() { return m_inProduction; }

	public static void setInProduction(Pipe p, boolean value)
	{
		if (p instanceof AddClassifierTokenPredictions) 
			((AddClassifierTokenPredictions) p).setInProduction(value);
		else if (p instanceof SerialPipes) {
			SerialPipes sp = (SerialPipes) p;
			for (int i = 0; i < sp.size(); i++)
				setInProduction(sp.getPipe(i), value);
		}
	}
	
	public Alphabet getDataAlphabet() { return m_dataAlphabet; }
	
	/**
	 * Add the token classifier's predictions as features to the instance.
	 * This method assumes the input instance contains FeatureVectorSequence as data  
	 */
	public Instance pipe(Instance carrier) 
	{
		FeatureVectorSequence fvs = (FeatureVectorSequence) carrier.getData();
		InstanceList ilist = convert(carrier, (Noop) m_tokenClassifiers.getInstancePipe());
		assert (fvs.size() == ilist.size());

		// For passing instances to the token classifier, each instance's data alphabet needs to 
		// match that used by the token classifier at training time.  For the resulting piped 
		// instance, each instance's data alphabet needs to contain token classifier's prediction 
		// as features 
		FeatureVector[] fva = new FeatureVector[fvs.size()];

		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			Classification c = m_tokenClassifiers.classify(inst, ! m_inProduction);
			LabelVector lv = c.getLabelVector();
			AugmentableFeatureVector afv1 = (AugmentableFeatureVector) inst.getData();
			int[] indices = afv1.getIndices();
			AugmentableFeatureVector afv2 = new AugmentableFeatureVector(m_dataAlphabet, 
					indices, afv1.getValues(), indices.length + m_predRanks2add.length);

			for (int j = 0; j < m_predRanks2add.length; j++) {
				Label label = lv.getLabelAtRank(m_predRanks2add[j]);
				int idx = m_dataAlphabet.lookupIndex("TOK_PRED=" + label.toString() + "_@_RANK_" + m_predRanks2add[j]);

				assert(idx >= 0);
				afv2.add(idx, 1);
			}
			fva[i] = afv2; 
		}

		carrier.setData(new FeatureVectorSequence(fva));
		return carrier;
	}


	/**
	 * Converts each instance containing a FeatureVectorSequence to multiple instances, 
	 * each containing an AugmentableFeatureVector as data.  
	 *  
	 * @param ilist Instances with FeatureVectorSequence as data field
	 * @param alphabetsPipe a Noop pipe containing the data and target alphabets for the resulting InstanceList 
	 * @return an InstanceList where each Instance contains one Token's AugmentableFeatureVector as data 
	 */
	public static InstanceList convert(InstanceList ilist, Noop alphabetsPipe)
	{
		if (ilist == null) return null;
		
		// This monstrosity is necessary b/c Classifiers obtain the data/target alphabets via pipes
		InstanceList ret = new InstanceList(alphabetsPipe);

		for (Instance inst : ilist)
			ret.add(inst);
		//for (int i = 0; i < ilist.size(); i++) ret.add(convert(ilist.get(i), alphabetsPipe));

		return ret;
	}


	/**
	 * 
	 * @param inst input instance, with FeatureVectorSequence as data.
	 * @param alphabetsPipe a Noop pipe containing the data and target alphabets for 
	 * the resulting InstanceList and AugmentableFeatureVectors
	 * @return list of instances, each with one AugmentableFeatureVector as data
	 */
	public static InstanceList convert(Instance inst, Noop alphabetsPipe)
	{
		InstanceList ret = new InstanceList(alphabetsPipe);
		Object obj = inst.getData();
		assert(obj instanceof FeatureVectorSequence);

		FeatureVectorSequence fvs = (FeatureVectorSequence) obj;
		LabelSequence ls = (LabelSequence) inst.getTarget();
		assert(fvs.size() == ls.size());

		Object instName = (inst.getName() == null ? "NONAME" : inst.getName());
		
		for (int j = 0; j < fvs.size(); j++) {
			FeatureVector fv = fvs.getFeatureVector(j);
			int[] indices = fv.getIndices();
			FeatureVector data = new AugmentableFeatureVector (alphabetsPipe.getDataAlphabet(),
					indices, fv.getValues(), indices.length); 
			Labeling target = ls.getLabelAtPosition(j);
			String name = instName.toString() + "_@_POS_" + (j + 1);
			Object source = inst.getSource();
			Instance toAdd = alphabetsPipe.pipe(new Instance(data, target, name, source));

			ret.add(toAdd);
		}

		return ret;
	}


	// Serialization 
	private static final long serialVersionUID = 1;

	/**
	 * This inner class represents the trained token classifiers.
	 * @author ghuang
	 */
	public static class TokenClassifiers extends Classifier implements Serializable
	{
		// number of folds in cross-validation training 
		int m_numCV;

		// random seed to split training data for cross-validation
		int m_randSeed;
		
		// trainer for token classifier
		ClassifierTrainer m_trainer;
		
		// token classifier trained on the entirety of the training set
		Classifier m_tokenClassifier;
		
		// table storing instance name -->  out-of-fold classifier 
		// Used to prevent overfitting to the token classifier's predictions
		HashMap m_table;
		

		/**
		 * Train a token classifier using the given Instances with 5-fold cross validation
		 * @param trainList training instances
		 */
		public TokenClassifiers(InstanceList trainList)
		{
			this(trainList, 0, 5);
		}
		
		
		public TokenClassifiers(InstanceList trainList, int randSeed, int numCV)
		{
//			this(new AdaBoostM2Trainer(new DecisionTreeTrainer(2), 10), trainList, randSeed, numCV);
//			this(new NaiveBayesTrainer(), trainList, randSeed, numCV);
			this(new BalancedWinnowTrainer(), trainList, randSeed, numCV);
//			this(new SVMTrainer(), trainList, randSeed, numCV);
		}
		
		
		public TokenClassifiers(ClassifierTrainer trainer, InstanceList trainList, int randSeed, int numCV)
		{
			super(trainList.getPipe());

			m_trainer = trainer;
			m_randSeed = randSeed;
			m_numCV = numCV;
			m_table = new HashMap();

			doTraining(trainList);
		}


		// train the token classifier
		private void doTraining(InstanceList trainList)
		{
			// train a classifier on the entire training set
			logger.info("Training token classifier on entire data set (size=" + trainList.size() + ")...");
			m_tokenClassifier = m_trainer.train(trainList);

			Trial t = new Trial(m_tokenClassifier, trainList);
			logger.info("Training set accuracy = " + t.getAccuracy());
			
			if (m_numCV == 0)
				return;

			// train classifiers using cross validation
			InstanceList.CrossValidationIterator cvIter = trainList.new CrossValidationIterator(m_numCV, m_randSeed);
			int f = 1;

			while (cvIter.hasNext()) {
				f++;
				InstanceList[] fold = cvIter.nextSplit();

				logger.info("Training token classifier on cv fold " + f + " / " + m_numCV + " (size=" + fold[0].size() + ")...");
				
				Classifier foldClassifier = m_trainer.train(fold[0]);
				Trial t1 = new Trial(foldClassifier, fold[0]);
				Trial t2 = new Trial(foldClassifier, fold[1]);

				logger.info("Within-fold accuracy = " + t1.getAccuracy());
				logger.info("Out-of-fold accuracy = " + t2.getAccuracy());

				/*for (int x = 0; x < t2.size(); x++) {
					logger.info("xxx pred:" + t2.getClassification(x).getLabeling().getBestLabel() + " true:" + t2.getClassification(x).getInstance().getLabeling());
				}*/
				
				for (int i = 0; i < fold[1].size(); i++) {
					Instance inst = fold[1].get(i);
					m_table.put(inst.getName(), foldClassifier);
				}
			}
		}


		public Classification classify(Instance instance)
		{
			return classify(instance, false);
		}


		/**
		 * 
		 * @param instance the instance to classify
		 * @param useOutOfFold whether to check the instance name and use the out-of-fold classifier
		 * if the instance name matches one in the training data
		 * @return the token classifier's output
		 */
		public Classification classify(Instance instance, boolean useOutOfFold)
		{
			Object instName = instance.getName();
			
			if (! useOutOfFold || ! m_table.containsKey(instName))
				return m_tokenClassifier.classify(instance);
			
			Classifier classifier = (Classifier) m_table.get(instName);

			return classifier.classify(instance);
		}

		// serialization
		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 1;
		
		private void writeObject(ObjectOutputStream out) throws IOException
		{
			out.writeInt(CURRENT_SERIAL_VERSION);
			out.writeObject(getInstancePipe());
			out.writeInt(m_numCV);
			out.writeInt(m_randSeed);
			out.writeObject(m_table);
			out.writeObject(m_tokenClassifier);
			out.writeObject(m_trainer);
		}
		
		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt();
			if (version != CURRENT_SERIAL_VERSION)
				throw new ClassNotFoundException("Mismatched TokenClassifiers versions: wanted " +
						CURRENT_SERIAL_VERSION + ", got " +
						version);
			instancePipe = (Pipe) in.readObject();
			m_numCV = in.readInt();
			m_randSeed = in.readInt();
			m_table = (HashMap) in.readObject();
			m_tokenClassifier = (Classifier) in.readObject();
			m_trainer = (ClassifierTrainer) in.readObject();
		}
	}
}
