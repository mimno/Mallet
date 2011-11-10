package cc.mallet.util;

import cc.mallet.types.*;
import gnu.trove.*;

import java.util.Arrays;
import java.text.NumberFormat;
import java.io.*;

public class FeatureCooccurrenceCounter {

	TIntIntHashMap[] featureFeatureCounts;
	InstanceList instances;
	int numFeatures;
	int[] documentFrequencies;

	public FeatureCooccurrenceCounter (InstanceList instances) {
		this.instances = instances;
		numFeatures = instances.getDataAlphabet().size();

		featureFeatureCounts = new TIntIntHashMap[numFeatures];
		for (int feature = 0; feature < numFeatures; feature++) {
			featureFeatureCounts[feature] = new TIntIntHashMap();
		}

		documentFrequencies = new int[numFeatures];
	}

	public void count() {
		
		TIntIntHashMap featureCounts = new TIntIntHashMap();
		
		int index = 0;

		for (Instance instance: instances) {
			FeatureSequence features = (FeatureSequence) instance.getData();

			for (int i=0; i<features.getLength(); i++) {
				featureCounts.adjustOrPutValue(features.getIndexAtPosition(i), 1, 1);
			}

			int[] keys = featureCounts.keys();
			for (int i = 0; i < keys.length - 1; i++) {
				int leftFeature = keys[i];
				for (int j = i+1; j < keys.length; j++) {
					int rightFeature = keys[j];
					featureFeatureCounts[leftFeature].adjustOrPutValue(rightFeature, 1, 1);
					featureFeatureCounts[rightFeature].adjustOrPutValue(leftFeature, 1, 1);					
				}
			}

			for (int key: keys) { documentFrequencies[key]++; }

			featureCounts = new TIntIntHashMap();

			index++;
			if (index % 1000 == 0) { System.err.println(index); }
		}
	}

	public double g2(double left, double right, double both, double total) {

		// Form a smoothed contingency table
		double justLeft = left - both + 0.01;
		double justRight = right - both + 0.01;
		both += 0.01;
		double neither = total - left - right + both + 0.01;

		total += 0.04;

		double leftMarginalProb = (justLeft + both) / total;
		double rightMarginalProb = (justRight + both) / total;

		double logLeft = Math.log(leftMarginalProb);
		double logRight = Math.log(rightMarginalProb);
		double logNotLeft = Math.log(1.0 - leftMarginalProb);
		double logNotRight = Math.log(1.0 - rightMarginalProb);
		
		double g2 =
			both * (Math.log(both / total) - logLeft - logRight) +
			justLeft * (Math.log(justLeft / total) - logLeft - logNotRight) +
			justRight * (Math.log(justRight / total) - logNotLeft - logRight) +
			neither * (Math.log(neither / total) - logNotLeft - logNotRight);
		
		return g2;
	}

	public void printCounts() {
		
		NumberFormat formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(3);

		Alphabet alphabet = instances.getDataAlphabet();

		double logTotalDocs = Math.log(instances.size());
		double[] logCache = new double[ instances.size() + 1 ];
		for (int n = 1; n < logCache.length; n++) {
			logCache[n] = Math.log(n);
		}

		for (int feature = 0; feature < numFeatures; feature++) {

			TIntIntHashMap featureCounts = featureFeatureCounts[feature];
			int[] keys = featureCounts.keys();

			double featureIDF = logTotalDocs - logCache[documentFrequencies[feature]];

			StringBuilder output = new StringBuilder();
			output.append(alphabet.lookupObject(feature));
			output.append("\t");
			//output.append(formatter.format(logTotalDocs - logCache[documentFrequencies[feature]]));

			output.append("1.0");

			if (documentFrequencies[feature] <= 5) { System.out.println(output); continue; }

			if (featureIDF - 3.0 > 0) {
				IDSorter[] sortedWeights = new IDSorter[keys.length];
				
				int i = 0;
				for (int key: keys) {
					double keyIDF = (logTotalDocs - logCache[documentFrequencies[key]]);

					if (keyIDF - 3.0 > 0) {
						sortedWeights[i] =
							new IDSorter(key,
										 ((keyIDF - 3.0) / (featureIDF - 3.0)) *
										 ((double) featureCounts.get(key) / (documentFrequencies[feature]) ));
					}
					else { 
						sortedWeights[i] =
                            new IDSorter(key, 0);
					}
					i++;
				}
				
				Arrays.sort(sortedWeights);
				
				for (i = 0; i < 10; i++) {
					if (i >= sortedWeights.length) { break; }
					
					int key = sortedWeights[i].getID();
					
					Object word = alphabet.lookupObject(sortedWeights[i].getID());
					double weight = sortedWeights[i].getWeight();

					if (weight < 0.05) { break; }
					
					output.append("\t" + word + "\t" + weight);
				}
			}

			System.out.println(output);
		}
		
	}

	public static void main (String[] args) throws Exception {
		InstanceList instances = InstanceList.load(new File(args[0]));
		FeatureCooccurrenceCounter counter = new FeatureCooccurrenceCounter(instances);
		counter.count();
		counter.printCounts();
	}


}