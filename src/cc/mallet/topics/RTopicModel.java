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