package cc.mallet.topics;

import cc.mallet.types.*;
import gnu.trove.TIntIntHashMap;
import java.io.*;

/** A wrapper for a topic model to be used from the R statistical package through rJava.
	R does not distinguish between integers and floating point numbers, so many of these
	methods simply translate doubles to ints.
 */

public class RTopicModel extends ParallelTopicModel {
	
	public InstanceList instances = null;

	public RTopicModel(double numTopics, double alpha, double beta) {
		super((int) Math.floor(numTopics), alpha, beta);
	}

	public void loadDocuments(String filename) {
		instances = InstanceList.load(new File(filename));
		addInstances(instances);
	}

	public void loadDocuments(InstanceList instances) {
		this.instances = instances;
		addInstances(instances);		
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
		setBurninPeriod((int) Math.floor(burnin));
		setOptimizeInterval((int) Math.floor(frequency));
	}

	public void train(double numIterations) {
		try {
			setNumIterations((int) Math.floor(numIterations));
			estimate();
		} catch (Exception e) {

		}
	}

	/** Run iterated conditional modes */
	public void maximize(double numIterations) {
		maximize((int) Math.floor(numIterations));
	}

	public double[] getAlpha() {
		return alpha;
	}

	public String[] getVocabulary() {
		String[] vocab = new String[ alphabet.size() ];
		for (int type = 0; type < numTypes; type++) {
			vocab[type] = (String) alphabet.lookupObject(type);
		}
		return vocab;
	}

	public String[] getDocumentNames() {
		String[] docNames = new String[ data.size() ];
		for (int doc = 0; doc < docNames.length; doc++) {
			docNames[doc] = (String) data.get(doc).instance.getName();
		}
		return docNames;
	}

	public double[][] getSubCorpusTopicWords(boolean[] documentMask, boolean normalized, boolean smoothed) {		
		double[][] result = new double[numTopics][numTypes];
		int[] subCorpusTokensPerTopic = new int[numTopics];
		
		for (int doc = 0; doc < data.size(); doc++) {
			if (documentMask[doc]) {
				int[] words = ((FeatureSequence) data.get(doc).instance.getData()).getFeatures();
				int[] topics = data.get(doc).topicSequence.getFeatures();
				for (int position = 0; position < topics.length; position++) {
					result[ topics[position] ][ words[position] ]++;
					subCorpusTokensPerTopic[ topics[position] ]++;
				}
			}
		}

		if (smoothed) {
			for (int topic = 0; topic < numTopics; topic++) {
				for (int type = 0; type < numTypes; type++) {
					result[topic][type] += beta;
				}
			}
		}

		if (normalized) {
			double[] topicNormalizers = new double[numTopics];
			if (smoothed) {
				for (int topic = 0; topic < numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / (subCorpusTokensPerTopic[topic] + numTypes * beta);
				}
			}
			else {
				for (int topic = 0; topic < numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / subCorpusTokensPerTopic[topic];
				}
			}

			for (int topic = 0; topic < numTopics; topic++) {
				for (int type = 0; type < numTypes; type++) {
					result[topic][type] *= topicNormalizers[topic];
				}
			}
		}

		return result;
	}

	public double[][] getTopicWords(boolean normalized, boolean smoothed) {
		double[][] result = new double[numTopics][numTypes];

		for (int type = 0; type < numTypes; type++) {
			int[] topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {

				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;

				result[topic][type] += count;

				index++;
			}
		}

		if (smoothed) {
			for (int topic = 0; topic < numTopics; topic++) {
				for (int type = 0; type < numTypes; type++) {
					result[topic][type] += beta;
				}
			}
		}

		if (normalized) {
			double[] topicNormalizers = new double[numTopics];
			if (smoothed) {
				for (int topic = 0; topic < numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / (tokensPerTopic[topic] + numTypes * beta);
				}
			}
			else {
				for (int topic = 0; topic < numTopics; topic++) {
					topicNormalizers[topic] = 1.0 / tokensPerTopic[topic];
				}
			}

			for (int topic = 0; topic < numTopics; topic++) {
				for (int type = 0; type < numTypes; type++) {
					result[topic][type] *= topicNormalizers[topic];
				}
			}
		}

		return result;
	}

	public double[][] getDocumentTopics(boolean normalized, boolean smoothed) {
		double[][] result = new double[data.size()][numTopics];

		for (int doc = 0; doc < data.size(); doc++) {
			int[] topics = data.get(doc).topicSequence.getFeatures();
			for (int position = 0; position < topics.length; position++) {
				result[doc][ topics[position] ]++;
			}

			if (smoothed) {
				for (int topic = 0; topic < numTopics; topic++) {
					result[doc][topic] += alpha[topic];
				}
			}

			if (normalized) {
				double sum = 0.0;
				for (int topic = 0; topic < numTopics; topic++) {
					sum += result[doc][topic];
				}
				double normalizer = 1.0 / sum;
				for (int topic = 0; topic < numTopics; topic++) {
					result[doc][topic] *= normalizer;
				}				
			}
		}

		return result;
	}

	public double[][] getWordFrequencies() {

		if (instances == null) { throw new IllegalStateException("You must load instances before you can count features"); }

		double[][] result = new double[ numTypes ][ 2 ];

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
			printState(new File(filename));
		} catch (Exception e) {
			System.err.println(e);
		}
	}
}