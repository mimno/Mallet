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
	int iteration = 0;
	
	int stride, docID;

	public Random random;

	int numColumns;
	
	int orderingStrategy = WordEmbeddings.LINEAR_ORDERING;

	public long wordsSoFar = 0;
	private int minDocumentLength;

	public WordEmbeddingRunnable(WordEmbeddings model, InstanceList instances, int numSamples, int numThreads, int threadID) {
		this.model = model;
		this.stride = model.stride;
		this.instances = instances;
		this.numSamples = numSamples;
		
		this.numThreads = numThreads;
		this.threadID = threadID;
		random = new Random();

		numColumns = model.numColumns;
		minDocumentLength = model.getMinDocumentLength();
	}
	
	public void setRandomSeed(int seed) {
		random = new Random(seed);
	}
	
	public void setOrdering(int strategy) {
		this.orderingStrategy = strategy;
	}

	public double getMeanError() {
		if (numUpdates == 0) { return docID; }
		
		double result = residual / numUpdates;
		residual = 0.0;
		numUpdates = 0;
		return result;
	}

	public void run() {
		long previousWordsSoFar = 0;
		long wordsConsidered = 0;
		long wordsSampled = 0;
		
		int numDocuments = instances.size();
		int inputTypeOffset, outputTypeOffset, sampledTypeOffset;

		int inputType, outputType, sampledType;
		int start, end, subWindow;
		
		double innerProduct, weightedResidual;
		double learningRate = 0.025;

		double gradientSum = 0.0;
		double[] gradient = new double[numColumns];

		int minDoc = threadID * (numDocuments / numThreads);
		int maxDoc = (threadID + 1) * (numDocuments / numThreads);
		int numDocs = maxDoc - minDoc;
		int[] agenda = new int[numDocs];

		if (orderingStrategy == WordEmbeddings.SHUFFLED_ORDERING) {
			for (int i = 0; i < numDocs; i++) {
				agenda[i] = i;
			}
			for (int i = 0; i < numDocs; i++) {
				int swapIndex = i + random.nextInt(numDocs - i);
				int temp = agenda[swapIndex];
				agenda[swapIndex] = agenda[i];
				agenda[i] = temp;
			}
		}
		else if (orderingStrategy == WordEmbeddings.RANDOM_ORDERING) {
			for (int i = 0; i < numDocs; i++) {
				agenda[i] = minDoc + random.nextInt(numDocs);
			}
		}
		else { // Default: linear ordering
			for (int i = 0; i < numDocs; i++) {
				agenda[i] = minDoc + i;
			}
		}

		docID = 0;
		int maxDocID = (threadID + 1) * (numDocuments / numThreads);
		if (maxDocID > numDocuments) {
			maxDocID = numDocuments;
		}
		
		double cacheScale = (double) model.sigmoidCacheSize / (model.maxExpValue - model.minExpValue);
		
		int[] tokenBuffer = new int[100000];
				
		while (shouldRun) {
			Instance instance = instances.get( agenda[docID] );
			docID++;

			if (docID == numDocs) { 
				// start over at the beginning
				docID = 0;
				iteration++;
				if (iteration >= model.numIterations) {
					shouldRun = false;
					return;
				}
			}

			if (wordsSoFar - previousWordsSoFar > 10000) {
				learningRate = Math.max(0.025 * 0.0001, 0.025 * (1.0 - (double) numThreads * wordsSoFar / (model.numIterations * model.totalWords)));			
				previousWordsSoFar = wordsSoFar;
				//System.out.format("%f\t%f\t%d\t%d\t%d\t%f\t%f\n", learningRate, 100.0 * wordsSoFar / model.totalWords, wordsSoFar, wordsConsidered, updates, (double) updates / wordsConsidered, gradientSum / updates);
			}
			
			double[] weights = model.weights;
			double[] negativeWeights = model.negativeWeights;
			
			FeatureSequence tokens = (FeatureSequence) instance.getData();
			int originalLength = tokens.getLength();
			int length = 0;
			
			// Subsample the document, dropping frequent words frequently.
			for (int inputPosition = 0; inputPosition < originalLength; inputPosition++) {
				inputType = tokens.getIndexAtPosition(inputPosition);
				
				wordsSoFar++;

				if (random.nextDouble() < model.retentionProbability[inputType]) {
					tokenBuffer[length] = inputType;
					length++;
					wordsSampled++;
				}				
			}			
				
			// Skip short documents
			assert minDocumentLength > 0;
			if (length < minDocumentLength) { continue; }
			
			for (int inputPosition = 0; inputPosition < length; inputPosition++) {

				wordsConsidered++;
				inputType = tokenBuffer[inputPosition];
				inputTypeOffset = inputType * stride;
				
				subWindow = random.nextInt(model.windowSize) + 1;
				start = Math.max(0, inputPosition - subWindow);
				end = Math.min(length - 1, inputPosition + subWindow);
				for (int outputPosition = start; outputPosition <= end; outputPosition++) {
					if (inputPosition == outputPosition) { continue; }
					outputType = tokenBuffer[outputPosition];
					//if (inputType == outputType) { continue; }
					
					outputTypeOffset = outputType * stride;
					
					innerProduct = 0.0;
					for (int col = 0; col < numColumns; col++) {
						innerProduct += negativeWeights[inputTypeOffset + col] * weights[outputTypeOffset + col];
					}
					
					if (innerProduct < model.minExpValue) {
						weightedResidual = learningRate;  // (1.0 - 0.0)
					}
					else if (innerProduct > model.maxExpValue) {
						weightedResidual = 0.0; // (1.0 - 1.0)
					}
					else {
						weightedResidual = learningRate * (1.0 - model.sigmoidCache[ (int) Math.floor( (innerProduct - model.minExpValue) * cacheScale ) ]);
					}
					
					for (int col = 0; col < numColumns; col++) {
						gradient[col] = weightedResidual * negativeWeights[inputTypeOffset + col];
						negativeWeights[inputTypeOffset + col] += weightedResidual * weights[outputTypeOffset + col];
					}

					double meanNegativePrediction = 0.0;
					for (int sample = 0; sample < numSamples; sample++) {
						sampledType = model.samplingTable[ random.nextInt(model.samplingTableSize) ];
						if (sampledType == inputType) { continue; }
						
						sampledTypeOffset = sampledType * stride;
						
						innerProduct = 0.0;
						for (int col = 0; col < numColumns; col++) {
							innerProduct += negativeWeights[sampledTypeOffset + col] * weights[outputTypeOffset + col];
						}
						
						if (innerProduct < model.minExpValue) {
							weightedResidual = 0.0; // (0.0 - 0.0)
						}
						else if (innerProduct > model.maxExpValue) {
							weightedResidual = -learningRate; // (0.0 - 1.0)
						}
						else {
							weightedResidual = learningRate * -model.sigmoidCache[ (int) Math.floor( (innerProduct - model.minExpValue) * cacheScale ) ];
						}
						
						for (int col = 0; col < numColumns; col++) {
							gradient[col] += weightedResidual * negativeWeights[sampledTypeOffset + col];
							negativeWeights[sampledTypeOffset + col] += weightedResidual * weights[outputTypeOffset + col];
						}
					}
					
					//residual += prediction - meanNegativePrediction;
					numUpdates++;
					
					for (int col = 0; col < numColumns; col++) {
						weights[outputTypeOffset + col] += gradient[col];
					}
				}
			}					
		}
	}
}