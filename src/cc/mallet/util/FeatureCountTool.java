package cc.mallet.util;

import cc.mallet.types.*;

import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.cursors.IntCursor;

import java.util.Formatter;
import java.util.Locale;
import java.util.logging.*;
import java.io.*;

import java.text.NumberFormat;

public class FeatureCountTool {

	protected static Logger logger = MalletLogger.getLogger(FeatureCountTool.class.getName());

	static cc.mallet.util.CommandOption.String inputFile = new cc.mallet.util.CommandOption.String
		(FeatureCountTool.class, "input", "FILENAME", true, null,
		 "Filename for the input instance list", null);
	
	double[] featureCounts;
	InstanceList instances;
	int numFeatures;
	int[] documentFrequencies;

	public FeatureCountTool (InstanceList instances) {
		this.instances = instances;
		numFeatures = instances.getDataAlphabet().size();

		featureCounts = new double[numFeatures];
		documentFrequencies = new int[numFeatures];
	}

	public double[] getFeatureCounts() {
		return featureCounts;
	}

	public int[] getDocumentFrequencies() {
		return documentFrequencies;
	}

	public void count() {

		IntIntHashMap docCounts = new IntIntHashMap();
		
		int index = 0;

		if (instances.size() == 0) { 
			logger.info("Instance list is empty");
			return;
		}

		if (instances.get(0).getData() instanceof FeatureSequence) {

			for (Instance instance: instances) {
				FeatureSequence features = (FeatureSequence) instance.getData();
				
				for (int i=0; i<features.getLength(); i++) {
					docCounts.putOrAdd(features.getIndexAtPosition(i), 1, 1);
				}

				for (IntCursor cursor: docCounts.values()) {
					int feature = cursor.value;
					featureCounts[feature] += docCounts.get(feature);
					documentFrequencies[feature]++;
				}
				
				docCounts = new IntIntHashMap();
				
				index++;
				if (index % 1000 == 0) { System.err.println(index); }
			}
		}
		else if (instances.get(0).getData() instanceof FeatureVector) {
			
			for (Instance instance: instances) {
				FeatureVector features = (FeatureVector) instance.getData();
				
				for (int location = 0; location < features.numLocations(); location++) {
					int feature = features.indexAtLocation(location);
					double value = features.valueAtLocation(location);

					documentFrequencies[feature]++;
					featureCounts[feature] += value;
				}

				index++;
				if (index % 1000 == 0) { System.err.println(index); }
			}
		}
		else {
			logger.info("Unsupported data class: " + instances.get(0).getData().getClass().getName());
		}
	}

	public void printCounts() {
		
		Alphabet alphabet = instances.getDataAlphabet();

		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumFractionDigits(0);
		nf.setMaximumFractionDigits(6);
		nf.setGroupingUsed(false);

		for (int feature = 0; feature < numFeatures; feature++) {

			Formatter formatter = new Formatter(new StringBuilder(), Locale.US);
			
			formatter.format("%s\t%s\t%d", 
							 alphabet.lookupObject(feature).toString(),
							 nf.format(featureCounts[feature]), documentFrequencies[feature]);

			System.out.println(formatter);
		}
		
	}
	
	public Alphabet getPrunedAlphabet(int minDocs, int maxDocs, int minCount, int maxCount) {
		Alphabet inputAlphabet = instances.getDataAlphabet();
		Alphabet outputAlphabet = new Alphabet();
		for (int inputType = 0; inputType < numFeatures; inputType++) {
			if (featureCounts[inputType] >= minCount && featureCounts[inputType] <= maxCount && documentFrequencies[inputType] >= minDocs && documentFrequencies[inputType] <= maxDocs) {
				outputAlphabet.lookupIndex(inputAlphabet.lookupObject(inputType));
			}
		}
		
		return outputAlphabet;
	}

	public static void main (String[] args) throws Exception {
		CommandOption.setSummary (FeatureCountTool.class,
								  "Print feature counts and instances per feature (eg document frequencies) in an instance list");
		CommandOption.process (FeatureCountTool.class, args);

		InstanceList instances = InstanceList.load (new File(inputFile.value));
		FeatureCountTool counter = new FeatureCountTool(instances);
		counter.count();
		counter.printCounts();
	}


}