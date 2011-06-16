package cc.mallet.util;

import cc.mallet.types.*;
import gnu.trove.*;

import java.util.Formatter;
import java.util.Locale;
import java.io.*;

import java.text.NumberFormat;

public class FeatureCounter {

	double[] featureCounts;
	InstanceList instances;
	int numFeatures;
	int[] documentFrequencies;

	public FeatureCounter (InstanceList instances) {
		this.instances = instances;
		numFeatures = instances.getDataAlphabet().size();

		featureCounts = new double[numFeatures];
		documentFrequencies = new int[numFeatures];
	}

	public void count() {

		TIntIntHashMap docCounts = new TIntIntHashMap();
		
		int index = 0;

		if (instances.size() == 0) { 
			System.err.println("Instance list is empty");
			return;
		}

		if (instances.get(0).getData() instanceof FeatureSequence) {

			for (Instance instance: instances) {
				FeatureSequence features = (FeatureSequence) instance.getData();
				
				for (int i=0; i<features.getLength(); i++) {
					docCounts.adjustOrPutValue(features.getIndexAtPosition(i), 1, 1);
				}
				
				int[] keys = docCounts.keys();
				for (int i = 0; i < keys.length - 1; i++) {
					int feature = keys[i];
					featureCounts[feature] += docCounts.get(feature);
					documentFrequencies[feature]++;
				}
				
				docCounts = new TIntIntHashMap();
				
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

	public static void main (String[] args) throws Exception {
		InstanceList instances = InstanceList.load(new File(args[0]));
		FeatureCounter counter = new FeatureCounter(instances);
		counter.count();
		counter.printCounts();
	}


}