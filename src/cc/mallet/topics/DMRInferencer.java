package cc.mallet.topics;

import cc.mallet.types.*;
import cc.mallet.util.*;
import cc.mallet.classify.MaxEnt;

import java.util.Arrays;
import java.io.*;

public class DMRInferencer extends TopicInferencer implements Serializable {
	
	protected MaxEnt dmrParameters = null;
	protected int numFeatures;
	protected int defaultFeatureIndex;

	public DMRInferencer (int[][] typeTopicCounts, int[] tokensPerTopic, MaxEnt dmrParameters, Alphabet alphabet,
							double beta, double betaSum) {

		this.dmrParameters = dmrParameters;
		this.numFeatures = dmrParameters.getAlphabet().size();
		this.defaultFeatureIndex = dmrParameters.getDefaultFeatureIndex();

		this.tokensPerTopic = tokensPerTopic;
		this.typeTopicCounts = typeTopicCounts;

		this.alphabet = alphabet;

		numTopics = tokensPerTopic.length;
		numTypes = typeTopicCounts.length;
		
		if (Integer.bitCount(numTopics) == 1) {
			// exact power of 2
			topicMask = numTopics - 1;
			topicBits = Integer.bitCount(topicMask);
		}
		else {
			// otherwise add an extra bit
			topicMask = Integer.highestOneBit(numTopics) * 2 - 1;
			topicBits = Integer.bitCount(topicMask);
		}

		
		this.beta = beta;
		this.betaSum = betaSum;

		cachedCoefficients = new double[numTopics];
		alpha = new double[numTopics];
		
		for (int topic=0; topic < numTopics; topic++) {
			smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
			cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);
		}

		random = new Randoms();
	}
	
	public double[] getSampledDistribution(Instance instance, int numIterations, int thinning, int burnIn) {
		// we can't use the standard score functions from MaxEnt,
		//  since our features are currently in the Target.
		FeatureVector features = (FeatureVector) instance.getTarget();
		double[] parameters = dmrParameters.getParameters();
		
		for (int topic = 0; topic < numTopics; topic++) {
			alpha[topic] = parameters[topic*numFeatures + defaultFeatureIndex]
				+ MatrixOps.rowDotProduct (parameters, numFeatures, topic, features, defaultFeatureIndex, null);
			
			alpha[topic] = Math.exp(alpha[topic]);
			
			cachedCoefficients[topic] = alpha[topic] / (tokensPerTopic[topic] + betaSum);
		}
		
		return super.getSampledDistribution(instance, numIterations, thinning, burnIn);
	}
	
	// Serialization

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	private static final int NULL_INTEGER = -1;

	private void writeObject (ObjectOutputStream out) throws IOException {
		System.out.println("writing");
		
		out.writeInt (CURRENT_SERIAL_VERSION);

		out.writeObject(dmrParameters);
		out.writeObject(alphabet);

		out.writeInt(numTopics);

		out.writeInt(topicMask);
		out.writeInt(topicBits);

		out.writeInt(numTypes);

		out.writeObject(alpha);
		out.writeDouble(beta);
		out.writeDouble(betaSum);

		out.writeObject(typeTopicCounts);
		out.writeObject(tokensPerTopic);

		out.writeObject(random);

		out.writeDouble(smoothingOnlyMass);
		out.writeObject(cachedCoefficients);
		System.out.println("done");
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {

		int version = in.readInt ();

		dmrParameters = (MaxEnt) in.readObject();
		numFeatures = dmrParameters.getAlphabet().size();
		defaultFeatureIndex = dmrParameters.getDefaultFeatureIndex();
		
		alphabet = (Alphabet) in.readObject();

		numTopics = in.readInt();

		topicMask = in.readInt();
		topicBits = in.readInt();

		numTypes = in.readInt();

		alpha = (double[]) in.readObject();
		beta = in.readDouble();
		betaSum = in.readDouble();

		typeTopicCounts = (int[][]) in.readObject();
		tokensPerTopic = (int[]) in.readObject();

		random = (Randoms) in.readObject();

		smoothingOnlyMass = in.readDouble();
		cachedCoefficients = (double[]) in.readObject();
	}

	public static DMRInferencer read (File f) throws Exception {

		DMRInferencer inferencer = null;

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
		inferencer = (DMRInferencer) ois.readObject();
		ois.close();

		return inferencer;
	}
}