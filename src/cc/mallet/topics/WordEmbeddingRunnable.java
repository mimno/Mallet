package cc.mallet.topics;

import java.util.*;
import cc.mallet.types.*;

public class WordEmbeddingRunnable implements Runnable {
	public WordEmbeddings model;
	public InstanceList instances;
	public int numSamples;
	public boolean shouldRun = true;

	double residual = 0.0;
	int numUpdates = 0;

	int numThreads;
	int threadID;
	
	int stride;
	
	public int docID;

	public Random random;

	int numColumns;

	public int wordsSoFar = 0;

	public WordEmbeddingRunnable(WordEmbeddings model, InstanceList instances, int numSamples, int numThreads, int threadID) {
		this.model = model;
		this.stride = model.stride;
		this.instances = instances;
		this.numSamples = numSamples;
		
		this.numThreads = numThreads;
		this.threadID = threadID;
		random = new Random();

		numColumns = model.numColumns;
	}

	public double getMeanError() {
		if (numUpdates == 0) { return docID; }
		
		double result = residual / numUpdates;
		residual = 0.0;
		numUpdates = 0;
		return result;
	}

	public void run() {
		int numDocuments = instances.size();

		double sampleNormalizer = 1.0f / numSamples;

		double[] gradient = new double[numColumns];

		int outputOffset = model.numColumns;

		docID = threadID * (numDocuments / numThreads);
		int maxDocID = (threadID + 1) * (numDocuments / numThreads);
		if (maxDocID > numDocuments) {
			maxDocID = numDocuments;
		}
		
		double cacheScale = 1.0 / (model.maxExpValue - model.minExpValue);
		
		int[] tokenBuffer = new int[100000];
		
		while (shouldRun) {
			Instance instance = instances.get( docID );
			docID++;

			if (docID == maxDocID) { 
				// start over at the beginning
				docID = threadID * (numDocuments / numThreads);
			}

			double learningRate = Math.max(0.0001, 0.025 * (1.0 - (double) numThreads * wordsSoFar / model.totalWords));
			
			FeatureSequence tokens = (FeatureSequence) instance.getData();
			int originalLength = tokens.getLength();
			int length = 0;
			
			for (int inputPosition = 0; inputPosition < originalLength; inputPosition++) {
				int inputType = tokens.getIndexAtPosition(inputPosition);
				
				wordsSoFar++;
				
				double frequencyScore = (double) model.wordCounts[inputType] / (0.0001 * model.totalWords);
				if (random.nextDouble() < (Math.sqrt(frequencyScore) + 1) / frequencyScore) {
					tokenBuffer[length] = inputType;
					length++;
				}				
			}			
				
			// Skip short documents
			if (length < 10) { continue; }
			
			for (int inputPosition = 0; inputPosition < length; inputPosition++) {
				int inputType = tokenBuffer[inputPosition];
				
				int subWindow = model.windowSize;
				int start = Math.max(0, inputPosition - subWindow);
				int end = Math.min(length - 1, inputPosition + subWindow);
				for (int outputPosition = start; outputPosition <= end; outputPosition++) {
					if (inputPosition == outputPosition) { continue; }
					int outputType = tokenBuffer[outputPosition];
					if (inputType == outputType) { continue; }
					
					double innerProduct = model.weights[inputType * stride + 0] + model.weights[inputType * stride + outputOffset];
					for (int col = 1; col < numColumns; col++) {
						innerProduct += model.weights[inputType * stride + col] * model.weights[outputType * stride + outputOffset + col];
					}
					
					double prediction;
					if (innerProduct < model.minExpValue) {
						prediction = 0.0;
					}
					else if (innerProduct > model.maxExpValue) {
						prediction = 1.0;
					}
					else {
						prediction = model.sigmoidCache[ (int) Math.floor( model.sigmoidCacheSize *
								(innerProduct - model.minExpValue) * cacheScale ) ];
					}
					
					//assert (! Double.isNaN(prediction));
					
					gradient[0] = (1.0f - prediction);
					model.weights[outputType * stride + outputOffset] += learningRate * (1.0f - prediction);
					for (int col = 1; col < numColumns; col++) {
						gradient[col] = (1.0f - prediction) * model.weights[outputType * stride + outputOffset + col];
						model.weights[outputType * stride + outputOffset + col] += learningRate * ((1.0f - prediction) * model.weights[inputType * stride + col]);
					}
					//if (outputType == queryID) { System.out.format("neg: %f g: %f\n", model.negativeWeights[inputType][0], learningRate * (1.0 - prediction)); }
					
					double meanNegativePrediction = 0.0;
					for (int sample = 0; sample < numSamples; sample++) {
						int sampledType = model.samplingTable[ random.nextInt(model.samplingTableSize) ];
						//System.out.format("%s %s %d\n", vocabulary.lookupObject(outputType), vocabulary.lookupObject(sampledType), 0);
						int sampledTypeOffset = sampledType * stride;
						
						innerProduct = model.weights[inputType * stride + 0] + model.weights[sampledTypeOffset + outputOffset];;
						for (int col = 0; col < numColumns; col++) {
							innerProduct += model.weights[inputType * stride + col] * model.weights[sampledTypeOffset + outputOffset + col];
						}
						
						double negativePrediction = 0.0;
						
						if (innerProduct < model.minExpValue) {
							negativePrediction = 0.0;
						}
						else if (innerProduct > model.maxExpValue) {
							negativePrediction = 1.0;
						}
						else {
							negativePrediction = model.sigmoidCache[ (int) Math.floor( model.sigmoidCacheSize * (innerProduct - model.minExpValue) * cacheScale ) ];
						}
						
						//assert (! Double.isNaN(negativePrediction));
						
						meanNegativePrediction += negativePrediction;
						
						gradient[0] += sampleNormalizer * (-negativePrediction);
						model.weights[sampledTypeOffset + outputOffset] += learningRate * sampleNormalizer * (-negativePrediction);
						for (int col = 1; col < numColumns; col++) {
							gradient[col] += sampleNormalizer * (-negativePrediction * model.weights[sampledType * stride + outputOffset + col]);
							model.weights[sampledTypeOffset + outputOffset + col] += learningRate * sampleNormalizer * (-negativePrediction * model.weights[inputType * stride + col]);
						}
					}
					
					residual += prediction - meanNegativePrediction * sampleNormalizer;
					numUpdates++;
					
					for (int col = 0; col < numColumns; col++) {
						model.weights[inputType * stride + col] += learningRate * (gradient[col]);
					}
				}
			}					
		}
	}
}