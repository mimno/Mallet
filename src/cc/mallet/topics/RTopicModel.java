package cc.mallet.topics;

import cc.mallet.types.*;
import gnu.trove.TIntIntHashMap;
import java.io.*;

/** A wrapper for a topic model to be used from the R statistical package through rJava. */

public class RTopicModel {
	
	public InstanceList instances = null;
	public ParallelTopicModel model = null;

	public RTopicModel(double numTopics, double alpha, double beta) {
		model = new ParallelTopicModel((int) Math.floor(numTopics), alpha, beta);
	}

	public void loadDocuments(String filename) {
		instances = InstanceList.load(new File(filename));
		model.addInstances(instances);
	}

	public void loadDocuments(InstanceList instances) {
		this.instances = instances;
		model.addInstances(instances);		
	}

	/** This is a helper method that simplifies class casting from rJava. */
	public static void addInstance(InstanceList instances, String id, String text) {
		instances.addThruPipe(new Instance(text, null, id, null));
	}

	public static void addInstances(InstanceList instances, String[] ids, String[] texts) {
		for (int i = 0; i < ids.length; i++) {
			instances.addThruPipe(new Instance(texts[i], null, ids[i], null));
		}
	}

	public void setAlphaOptimization(double frequency, double burnin) {
		model.setBurninPeriod((int) Math.floor(burnin));
		model.setOptimizeInterval((int) Math.floor(frequency));
	}

	public void train(double numIterations) {
		try {
			model.setNumIterations((int) Math.floor(numIterations));
			model.estimate();
		} catch (Exception e) {

		}
	}

	/** Run iterated conditional modes */
	public void maximize(double numIterations) {
		model.maximize((int) Math.floor(numIterations));
	}

	public double[] getAlpha() {
		return model.alpha;
	}

	public String[] getVocabulary() {
		String[] vocab = new String[ model.alphabet.size() ];
		for (int type = 0; type < model.numTypes; type++) {
			vocab[type] = (String) model.alphabet.lookupObject(type);
		}
		return vocab;
	}

	public String[] getDocumentNames() {
		String[] docNames = new String[ model.data.size() ];
		for (int doc = 0; doc < docNames.length; doc++) {
			docNames[doc] = (String) model.data.get(doc).instance.getName();
		}
		return docNames;
	}

	public double[][] getSubCorpusTopicWords(boolean[] documentMask, boolean normalized, boolean smoothed) {		
		double[][] result = new double[model.numTopics][model.numTypes];
		int[] subCorpusTokensPerTopic = new int[model.numTopics];
		
		for (int doc = 0; doc < model.data.size(); doc++) {
			if (documentMask[doc]) {
				int[] words = ((FeatureSequence) model.data.get(doc).instance.getData()).getFeatures();
				int[] topics = model.data.get(doc).topicSequence.getFeatures();
				for (int position = 0; position < topics.length; position++) {
					result[ topics[position] ][ words[position] ]++;
					subCorpusTokensPerTopic[ topics[position] ]++;
				}
			}
		}

		if (smoothed) {
			for (int topic = 0; topic < model.numTopics; topic++) {
				for (int type = 0; type < model.numTypes; type++) {
					result[topic][type] += model.beta;
				}
			}
		}

		if (normalized) {
			double[] topicNormalizers = new double[model.numTopics];
			if (smoothed) {
				for (int topic = 0; topic < model.numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / (subCorpusTokensPerTopic[topic] + model.numTypes * model.beta);
				}
			}
			else {
				for (int topic = 0; topic < model.numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / subCorpusTokensPerTopic[topic];
				}
			}

			for (int topic = 0; topic < model.numTopics; topic++) {
				for (int type = 0; type < model.numTypes; type++) {
					result[topic][type] *= topicNormalizers[topic];
				}
			}
		}

		return result;
	}

	public double[][] getTopicWords(boolean normalized, boolean smoothed) {
		double[][] result = new double[model.numTopics][model.numTypes];

		for (int type = 0; type < model.numTypes; type++) {
			int[] topicCounts = model.typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {

				int topic = topicCounts[index] & model.topicMask;
				int count = topicCounts[index] >> model.topicBits;

				result[topic][type] += count;

				index++;
			}
		}

		if (smoothed) {
			for (int topic = 0; topic < model.numTopics; topic++) {
				for (int type = 0; type < model.numTypes; type++) {
					result[topic][type] += model.beta;
				}
			}
		}

		if (normalized) {
			double[] topicNormalizers = new double[model.numTopics];
			if (smoothed) {
				for (int topic = 0; topic < model.numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / (model.tokensPerTopic[topic] + model.numTypes * model.beta);
				}
			}
			else {
				for (int topic = 0; topic < model.numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / model.tokensPerTopic[topic];
				}
			}

			for (int topic = 0; topic < model.numTopics; topic++) {
				for (int type = 0; type < model.numTypes; type++) {
					result[topic][type] *= topicNormalizers[topic];
				}
			}
		}

		return result;
	}

	public double[][] getDocumentTopics(boolean normalized, boolean smoothed) {
		double[][] result = new double[model.data.size()][model.numTopics];

		for (int doc = 0; doc < model.data.size(); doc++) {
			int[] topics = model.data.get(doc).topicSequence.getFeatures();
			for (int position = 0; position < topics.length; position++) {
				result[doc][ topics[position] ]++;
			}

			if (smoothed) {
				for (int topic = 0; topic < model.numTopics; topic++) {
					result[doc][topic] += model.alpha[topic];
				}
			}

			if (normalized) {
				double sum = 0.0;
				for (int topic = 0; topic < model.numTopics; topic++) {
					sum += result[doc][topic];
				}
				double normalizer = 1.0 / sum;
				for (int topic = 0; topic < model.numTopics; topic++) {
					result[doc][topic] *= normalizer;
				}				
			}
		}

		return result;
	}

	public double[][] getWordFrequencies() {

		if (instances == null) { throw new IllegalStateException("You must load instances before you can count features"); }

		double[][] result = new double[ model.numTypes ][ 2 ];

		TIntIntHashMap docCounts = new TIntIntHashMap();
		
		for (Instance instance: instances) {
			FeatureSequence features = (FeatureSequence) instance.getData();
            
			for (int i=0; i<features.getLength(); i++) {
				docCounts.adjustOrPutValue(features.getIndexAtPosition(i), 1, 1);
			}
            
			int[] keys = docCounts.keys();
			for (int i = 0; i < keys.length - 1; i++) {
				int feature = keys[i];
				result[feature][0] += docCounts.get(feature);
				result[feature][1]++;
			}
            
			docCounts = new TIntIntHashMap();
            
		}
		
		return result;
	}

	public void writeState(String filename) {
		try {
			model.printState(new File(filename));
		} catch (Exception e) {
			System.err.println(e);
		}
	}
}