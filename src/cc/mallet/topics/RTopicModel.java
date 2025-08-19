package cc.mallet.topics;

import cc.mallet.types.*;
import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import java.io.*;
import java.util.ArrayList;

/** A wrapper for a topic model to be used from the R statistical package through rJava.
	R does not distinguish between integers and floating point numbers, so many of these
	methods simply translate doubles to ints.
 */

public class RTopicModel extends ParallelTopicModel {
	
	public InstanceList instances = null;

	public RTopicModel(double numTopics, double alpha, double beta) {
		super((int) Math.floor(numTopics), alpha, beta);
	}
	
	private RTopicModel(ParallelTopicModel model) {
		super(model.getTopicAlphabet(), model.alphaSum, model.beta);
		this.data = (ArrayList) model.data.clone();
	    this.alphabet = model.alphabet;
	    this.topicAlphabet = model.topicAlphabet;
	    this.numTopics = model.numTopics;
	    this.topicMask = model.topicMask;
	    this.topicBits = model.topicBits;
	    this.numTypes = model.numTypes;
	    this.totalTokens = model.totalTokens;
	    this.alpha = model.alpha;
	    this.alphaSum = model.alphaSum;
	    this.beta = model.beta;
	    this.betaSum = model.betaSum;
	    this.usingSymmetricAlpha = model.usingSymmetricAlpha;
	    this.typeTopicCounts = model.typeTopicCounts;
	    this.tokensPerTopic = model.tokensPerTopic;
	    this.docLengthCounts = model.docLengthCounts;
	    this.topicDocCounts = model.topicDocCounts;
	    this.numIterations = model.numIterations;
	    this.burninPeriod = model.burninPeriod;
	    this.saveSampleInterval = model.saveSampleInterval;
	    this.optimizeInterval = model.optimizeInterval;
	    this.temperingInterval = model.temperingInterval;
	    this.showTopicsInterval = model.showTopicsInterval;
	    this.wordsPerTopic = model.wordsPerTopic;
	    this.saveStateInterval = model.saveStateInterval;
	    this.stateFilename = model.stateFilename;
	    this.saveModelInterval = model.saveModelInterval;
	    this.modelFilename = model.modelFilename;
	    this.randomSeed = model.randomSeed;
	    this.formatter = model.formatter;
	    this.printLogLikelihood = model.printLogLikelihood;
	    this.typeTotals = model.typeTotals;
	    this.maxTypeCount = model.maxTypeCount;
	    this.numThreads = model.numThreads;
	    
	    InstanceList instanceList = new InstanceList();
	    for (TopicAssignment topic : model.data) {
			instanceList.add(topic.instance);
		}
	    
	    this.instances = instanceList;
	    
	    this.initializeHistograms();
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

		IntIntHashMap docCounts = new IntIntHashMap();
		
		for (Instance instance: instances) {
			FeatureSequence features = (FeatureSequence) instance.getData();
            
			for (int i=0; i<features.getLength(); i++) {
				docCounts.putOrAdd(features.getIndexAtPosition(i), 1, 1);
			}

			for (IntCursor cursor : docCounts.keys()) {
				int feature = cursor.value;
				result[feature][0] += docCounts.get(feature);
				result[feature][1]++;
			}
            
			docCounts = new IntIntHashMap();
            
		}
		
		return result;
	}

	public TopicModelDiagnostics getDiagnostics(double numWords) {
		return new TopicModelDiagnostics(this, (int) Math.floor(numWords));
	}

	public void writeState(String filename) {
		try {
			printState(new File(filename));
		} catch (Exception e) {
			System.err.println(e);
		}
	}
	
    public static RTopicModel read (File f) throws Exception {
        return new RTopicModel(ParallelTopicModel.read(f));
    }
    
}