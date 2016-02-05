package cc.mallet.cluster.tui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;

import cc.mallet.classify.Classifier;
import cc.mallet.classify.MaxEntTrainer;
import cc.mallet.classify.Trial;
import cc.mallet.cluster.Clusterer;
import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.Clusterings;
import cc.mallet.cluster.GreedyAgglomerativeByDensity;
import cc.mallet.cluster.Record;
import cc.mallet.cluster.evaluate.AccuracyEvaluator;
import cc.mallet.cluster.evaluate.BCubedEvaluator;
import cc.mallet.cluster.evaluate.ClusteringEvaluator;
import cc.mallet.cluster.evaluate.ClusteringEvaluators;
import cc.mallet.cluster.evaluate.MUCEvaluator;
import cc.mallet.cluster.evaluate.PairF1Evaluator;
import cc.mallet.cluster.iterator.PairSampleIterator;
import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.neighbor_evaluator.NeighborEvaluator;
import cc.mallet.cluster.neighbor_evaluator.PairwiseEvaluator;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.InfoGain;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.PropertyList;
import cc.mallet.util.Randoms;
import cc.mallet.util.Strings;

//In progress
public class Clusterings2Clusterer {

	private static Logger logger =
		MalletLogger.getLogger(Clusterings2Clusterer.class.getName());

	public static void main(String[] args) throws Exception {

		CommandOption.setSummary(Clusterings2Clusterer.class,
				"A tool to train and test a Clusterer.");
		CommandOption.process(Clusterings2Clusterer.class, args);

		// TRAIN

		Randoms random = new Randoms(123);
		Clusterer clusterer = null;
		if (!loadClusterer.value.exists()) {
			Clusterings training = readClusterings(trainingFile.value);

			Alphabet fieldAlphabet = ((Record) training.get(0).getInstances()
					.get(0).getData()).fieldAlphabet();

			Pipe pipe = new ClusteringPipe(string2ints(exactMatchFields.value, fieldAlphabet), 
					                       string2ints(approxMatchFields.value, fieldAlphabet), 
					                       string2ints(substringMatchFields.value, fieldAlphabet));

			InstanceList trainingInstances = new InstanceList(pipe);
			for (int i = 0; i < training.size(); i++) {
				PairSampleIterator iterator = new PairSampleIterator(training
						.get(i), random, 0.5, training.get(i).getNumInstances());
				while(iterator.hasNext()) {
					Instance inst = iterator.next();
					trainingInstances.add(pipe.pipe(inst));
				}
			}
			logger.info("generated " + trainingInstances.size()
					+ " training instances");
			Classifier classifier = new MaxEntTrainer().train(trainingInstances);
			logger.info("InfoGain:\n");
			new InfoGain(trainingInstances).printByRank(System.out);
			logger.info("pairwise training accuracy="
					+ new Trial(classifier, trainingInstances).getAccuracy());
			NeighborEvaluator neval = new PairwiseEvaluator(classifier, "YES",
					new PairwiseEvaluator.Average(), true);				
			clusterer = new GreedyAgglomerativeByDensity(
					training.get(0).getInstances().getPipe(), neval, 0.5, false,
					random);
			training = null;
			trainingInstances = null;
		} else {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(loadClusterer.value));
			clusterer = (Clusterer) ois.readObject();
		}

		// TEST

		Clusterings testing = readClusterings(testingFile.value);
		ClusteringEvaluator evaluator = (ClusteringEvaluator) clusteringEvaluatorOption.value;
		if (evaluator == null)
			evaluator = new ClusteringEvaluators(
					new ClusteringEvaluator[] { new BCubedEvaluator(),
							new PairF1Evaluator(), new MUCEvaluator(), new AccuracyEvaluator() });
		ArrayList<Clustering> predictions = new ArrayList<Clustering>();
		for (int i = 0; i < testing.size(); i++) {
			Clustering clustering = testing.get(i);
			Clustering predicted = clusterer.cluster(clustering.getInstances());
			predictions.add(predicted);
			logger.info(evaluator.evaluate(clustering, predicted));
		}
		logger.info(evaluator.evaluateTotals());
		
		// WRITE OUTPUT

		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(saveClusterer.value));
		oos.writeObject(clusterer);
		oos.close();
		
		if (outputClusterings.value != null) {
			BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputClusterings.value)));
			writer.write(predictions.toString());
			writer.flush();
			writer.close();
		}
	}

	public static int[] string2ints(String[] ss, Alphabet alph) {
		int[] ret = new int[ss.length];
		for (int i = 0; i < ss.length; i++)
			ret[i] = alph.lookupIndex(ss[i]);
		return ret;
	}

	public static Clusterings readClusterings(String f) throws Exception {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
				new File(f)));
		return (Clusterings) ois.readObject();
	}

	static CommandOption.File loadClusterer = new CommandOption.File(
			Clusterings2Clusterer.class,
			"load-clusterer",
			"FILE",
			false,
			null,
			"The file from which to read the clusterer.",
			null);

	static CommandOption.File saveClusterer = new CommandOption.File(
			Clusterings2Clusterer.class,
			"save-clusterer",
			"FILE",
			false,
			new File("clusterer.mallet"),			
			"The filename in which to write the clusterer after it has been trained.",
			null);

	static CommandOption.String outputClusterings = new CommandOption.String(
			Clusterings2Clusterer.class,
			"output-clusterings",
			"FILENAME",
			false,
			"predictions",
			"The filename in which to write the predicted clusterings.",
			null);

	static CommandOption.String trainingFile = new CommandOption.String(
			Clusterings2Clusterer.class,
			"train",
			"FILENAME",
			false,
			"text.clusterings.train",
			"Read the training set Clusterings from this file. "
					+ "If this is specified, the input file parameter is ignored",
			null);

	static CommandOption.String testingFile = new CommandOption.String(
			Clusterings2Clusterer.class,
			"test",
			"FILENAME",
			false,
			"text.clusterings.test",
			"Read the test set Clusterings from this file. "
					+ "If this option is specified, the training-file parameter must be specified and "
					+ " the input-file parameter is ignored", null);

	 static CommandOption.Object clusteringEvaluatorOption = new CommandOption.Object(
			Clusterings2Clusterer.class, "clustering-evaluator", "CONSTRUCTOR",
			true, null,
			"Java code for constructing a ClusteringEvaluator object", null);

	static CommandOption.SpacedStrings exactMatchFields = new CommandOption.SpacedStrings(
			Clusterings2Clusterer.class, "exact-match-fields", "STRING...",
			false, null,
			"The field names to be checked for exactly matching values", null);

	static CommandOption.SpacedStrings approxMatchFields = new CommandOption.SpacedStrings(
			Clusterings2Clusterer.class, "approx-match-fields", "STRING...",
			false, null,
			"The field names to be checked for approx matching values", null);

	static CommandOption.SpacedStrings substringMatchFields = new CommandOption.SpacedStrings(
			Clusterings2Clusterer.class, "substring-match-fields", "STRING...",
			false, null,
			"The field names to be checked for substring matching values. Note that values fewer than 3 characters are ignored.", null);

	
	
	public static class ClusteringPipe extends Pipe {
		private static final long serialVersionUID = 1L;

		int[] exactMatchFields;

		int[] approxMatchFields;

		int[] substringMatchFields;


		double approxMatchThreshold;

		public ClusteringPipe(int[] exactMatchFields, int[] approxMatchFields,
				int[] substringMatchFields) {
			super(new Alphabet(), new LabelAlphabet());
			this.exactMatchFields = exactMatchFields;
			this.approxMatchFields = approxMatchFields;
			this.substringMatchFields = substringMatchFields;
		}

		private Record[] array2Records(int[] a, InstanceList list) {
			ArrayList<Record> records = new ArrayList<Record>();
			for (int i = 0; i < a.length; i++)
				records.add((Record) list.get(a[i]).getData());
			return (Record[]) records.toArray(new Record[] {});
		}

		public Instance pipe(Instance carrier) {
			AgglomerativeNeighbor neighbor = (AgglomerativeNeighbor) carrier
					.getData();
			Clustering original = neighbor.getOriginal();
			int[] cluster1 = neighbor.getOldClusters()[0];
			int[] cluster2 = neighbor.getOldClusters()[1];
			InstanceList list = original.getInstances();
			int[] mergedIndices = neighbor.getNewCluster();
			Record[] records = array2Records(mergedIndices, list);
			Alphabet fieldAlph = records[0].fieldAlphabet();
			Alphabet valueAlph = records[0].valueAlphabet();

			PropertyList features = null;
			features = addExactMatch(records, fieldAlph, valueAlph, features);
			features = addApproxMatch(records, fieldAlph, valueAlph, features);
			features = addSubstringMatch(records, fieldAlph, valueAlph, features);
			carrier
					.setData(new FeatureVector(getDataAlphabet(), features,
							true));

			LabelAlphabet ldict = (LabelAlphabet) getTargetAlphabet();
			String label = (original.getLabel(cluster1[0]) == original
					.getLabel(cluster2[0])) ? "YES" : "NO";
			carrier.setTarget(ldict.lookupLabel(label));			
			return carrier;
		}

		private PropertyList addExactMatch(Record[] records,
				Alphabet fieldAlph, Alphabet valueAlph, PropertyList features) {

			for (int fi = 0; fi < exactMatchFields.length; fi++) {
				int matches = 0;
				int comparisons = 0;
				for (int i = 0; i < records.length
						&& exactMatchFields.length > 0; i++) {
					FeatureVector valsi = records[i]
							.values(exactMatchFields[fi]);
					for (int j = i + 1; j < records.length && valsi != null; j++) {
						FeatureVector valsj = records[j]
								.values(exactMatchFields[fi]);
						if (valsj != null) {
							comparisons++;
							for (int ii = 0; ii < valsi.numLocations(); ii++) {
								if (valsj.contains(valueAlph.lookupObject(valsi
										.indexAtLocation(ii)))) {
									matches++;
									break;
								}
							}
						}
					}
					if (matches == comparisons && comparisons > 1)
						features = PropertyList.add(fieldAlph
								.lookupObject(exactMatchFields[fi])
								+ "_all_match", 1.0, features);
					if (matches > 0)
						features = PropertyList.add(fieldAlph
								.lookupObject(exactMatchFields[fi])
								+ "_exists_match", 1.0, features);
				}
			}
			return features;
		}

		private PropertyList addApproxMatch(Record[] records,
				Alphabet fieldAlph, Alphabet valueAlph, PropertyList features) {

			for (int fi = 0; fi < approxMatchFields.length; fi++) {
				int matches = 0;
				int comparisons = 0;
				for (int i = 0; i < records.length
						&& approxMatchFields.length > 0; i++) {
					FeatureVector valsi = records[i]
							.values(approxMatchFields[fi]);
					for (int j = i + 1; j < records.length && valsi != null; j++) {
						FeatureVector valsj = records[j]
								.values(approxMatchFields[fi]);
						if (valsj != null) {
							comparisons++;
							for (int ii = 0; ii < valsi.numLocations(); ii++) {
								String si = (String) valueAlph
										.lookupObject(valsi.indexAtLocation(ii));
								for (int jj = 0; jj < valsj.numLocations(); jj++) {
									String sj = (String) valueAlph
											.lookupObject(valsj
													.indexAtLocation(jj));
									if (Strings.levenshteinDistance(si, sj) < approxMatchThreshold) {
										matches++;
										break;
									}
								}
							}
						}
					}
					if (matches == comparisons && comparisons > 1)
						features = PropertyList.add(fieldAlph
								.lookupObject(approxMatchFields[fi])
								+ "_all_approx_match", 1.0, features);
					if (matches > 0)
						features = PropertyList.add(fieldAlph
								.lookupObject(approxMatchFields[fi])
								+ "_exists_approx_match", 1.0, features);
				}
			}
			return features;
		}

		private PropertyList addSubstringMatch(Record[] records,
				Alphabet fieldAlph, Alphabet valueAlph, PropertyList features) {

			for (int fi = 0; fi < substringMatchFields.length; fi++) {
				int matches = 0;
				int comparisons = 0;
				for (int i = 0; i < records.length
						&& substringMatchFields.length > 0; i++) {
					FeatureVector valsi = records[i]
							.values(substringMatchFields[fi]);
					for (int j = i + 1; j < records.length && valsi != null; j++) {
						FeatureVector valsj = records[j]
								.values(substringMatchFields[fi]);
						if (valsj != null) {
							comparisons++;
							for (int ii = 0; ii < valsi.numLocations(); ii++) {
								String si = (String) valueAlph
								.lookupObject(valsi.indexAtLocation(ii));
								if (si.length() < 2) break;
								for (int jj = 0; jj < valsj.numLocations(); jj++) {
									String sj = (String) valueAlph
											.lookupObject(valsj
													.indexAtLocation(jj));
									if (sj.length() > 2 && (si.contains(si) || sj.contains(si))) {
										matches++;
										break;
									}
								}
							}
						}
					}
					if (matches == comparisons && comparisons > 1)
						features = PropertyList.add(fieldAlph
								.lookupObject(exactMatchFields[fi])
								+ "_all_substring_match", 1.0, features);
					if (matches > 0)
						features = PropertyList.add(fieldAlph
								.lookupObject(exactMatchFields[fi])
								+ "_exists_substring_match", 1.0, features);
				}
			}
			return features;
		}

	}
}
