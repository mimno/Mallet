package cc.mallet.util;

import cc.mallet.types.*;

import gnu.trove.map.hash.TIntIntHashMap;

import java.util.Arrays;
import java.util.logging.*;
import java.text.NumberFormat;
import java.io.*;

public class FeatureCooccurrenceCounter {

	private static Logger logger = MalletLogger.getLogger(FeatureCooccurrenceCounter.class.getName());
	
	static CommandOption.String inputFile = new CommandOption.String
		(FeatureCooccurrenceCounter.class, "input", "FILENAME", true, null,
		  "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);
	
	static CommandOption.String weightsFile = new CommandOption.String
		(FeatureCooccurrenceCounter.class, "weights-filename", "FILENAME", true, null,
		 "The filename to write the word-word weights file.", null);
	
	static CommandOption.Double idfCutoff = new CommandOption.Double
		(FeatureCooccurrenceCounter.class, "idf-cutoff", "NUMBER", true, 3.0,
		 "Words with IDF below this threshold will not be linked to any other word.", null);
	
	static CommandOption.String unlinkedFile = new CommandOption.String
		(FeatureCooccurrenceCounter.class, "unlinked-filename", "FILENAME", true, null,
		 "A file to write words that were not linked.", null);


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

	public void printCounts() throws IOException {
		
		NumberFormat formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(3);

		Alphabet alphabet = instances.getDataAlphabet();

		double logTotalDocs = Math.log(instances.size());
		double[] logCache = new double[ instances.size() + 1 ];
		for (int n = 1; n < logCache.length; n++) {
			logCache[n] = Math.log(n);
		}

		if (unlinkedFile.value != null) {
			PrintWriter out = new PrintWriter(unlinkedFile.value);

			for (int feature = 0; feature < numFeatures; feature++) {
				double featureIDF = logTotalDocs - logCache[documentFrequencies[feature]];

				if (featureIDF < idfCutoff.value) {
					out.println(alphabet.lookupObject(feature));
				}
			}
			
			out.close();
		}

		PrintWriter out = new PrintWriter(weightsFile.value);

		for (int feature = 0; feature < numFeatures; feature++) {

			TIntIntHashMap featureCounts = featureFeatureCounts[feature];
			int[] keys = featureCounts.keys();

			double featureIDF = logTotalDocs - logCache[documentFrequencies[feature]];

			StringBuilder output = new StringBuilder();
			output.append(alphabet.lookupObject(feature));
			output.append("\t");
			output.append("1.0");

			if (documentFrequencies[feature] <= 5) { out.println(output); continue; }

			if (featureIDF - idfCutoff.value > 0) {
				IDSorter[] sortedWeights = new IDSorter[keys.length];
				
				int i = 0;
				for (int key: keys) {
					double keyIDF = (logTotalDocs - logCache[documentFrequencies[key]]);

					if (keyIDF - idfCutoff.value > 0) {
						sortedWeights[i] =
							new IDSorter(key,
										 ((keyIDF - idfCutoff.value) / (featureIDF - idfCutoff.value)) *
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

			out.println(output);
		}
		
		out.close();
	}

	public static void main (String[] args) throws Exception {
		CommandOption.setSummary (FeatureCooccurrenceCounter.class,
								  "Build a file containing weights between word types");
		CommandOption.process (FeatureCooccurrenceCounter.class, args);

		InstanceList training = InstanceList.load (new File(inputFile.value));

		FeatureCooccurrenceCounter counter = new FeatureCooccurrenceCounter(training);
		counter.count();
		counter.printCounts();
	}


}