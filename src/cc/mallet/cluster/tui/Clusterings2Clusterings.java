package cc.mallet.cluster.tui;

import com.carrotsearch.hppc.IntHashSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Logger;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.Clusterings;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Randoms;

// In progress
public class Clusterings2Clusterings {

	private static Logger logger =
			MalletLogger.getLogger(Clusterings2Clusterings.class.getName());

	public static void main (String[] args) {
		CommandOption
									.setSummary(Clusterings2Clusterings.class,
															"A tool to manipulate Clusterings.");
		CommandOption.process(Clusterings2Clusterings.class, args);

		Clusterings clusterings = null;
		try {
			ObjectInputStream iis =
					new ObjectInputStream(new FileInputStream(inputFile.value));
			clusterings = (Clusterings) iis.readObject();
		} catch (Exception e) {
			System.err.println("Exception reading clusterings from "
													+ inputFile.value + " " + e);
			e.printStackTrace();
		}

		logger.info("number clusterings=" + clusterings.size());

		// Prune clusters based on size.
		if (minClusterSize.value > 1) {
			for (int i = 0; i < clusterings.size(); i++) {
				Clustering clustering = clusterings.get(i);
				InstanceList oldInstances = clustering.getInstances();
				Alphabet alph = oldInstances.getDataAlphabet();
				LabelAlphabet lalph = (LabelAlphabet) oldInstances.getTargetAlphabet();
				if (alph == null) alph = new Alphabet();
				if (lalph == null) lalph = new LabelAlphabet();
				Pipe noop = new Noop(alph, lalph);
				InstanceList newInstances = new InstanceList(noop);
				for (int j = 0; j < oldInstances.size(); j++) {
					int label = clustering.getLabel(j);
					Instance instance = oldInstances.get(j);
					if (clustering.size(label) >= minClusterSize.value) 
						newInstances.add(noop.pipe(new Instance(instance.getData(), lalph.lookupLabel(new Integer(label)), instance.getName(), instance.getSource())));
				}
				clusterings.set(i, createSmallerClustering(newInstances));
			}
			if (outputPrefixFile.value != null) {
				try {
					ObjectOutputStream oos =
						new ObjectOutputStream(new FileOutputStream(outputPrefixFile.value));
					oos.writeObject(clusterings);
					oos.close();
				} catch (Exception e) {
					logger.warning("Exception writing clustering to file " + outputPrefixFile.value												+ " " + e);
					e.printStackTrace();
				}
			}
		}
		
		
		// Split into training/testing
		if (trainingProportion.value > 0) {
			if (clusterings.size() > 1) 
				throw new IllegalArgumentException("Expect one clustering to do train/test split, not " + clusterings.size());
			Clustering clustering = clusterings.get(0);
			int targetTrainSize = (int)(trainingProportion.value * clustering.getNumInstances());
			IntHashSet clustersSampled = new IntHashSet();
			Randoms random = new Randoms(123);
			LabelAlphabet lalph = new LabelAlphabet();
			InstanceList trainingInstances = new InstanceList(new Noop(null, lalph));
			while (trainingInstances.size() < targetTrainSize) {
				int cluster = random.nextInt(clustering.getNumClusters());
				if (!clustersSampled.contains(cluster)) {
					clustersSampled.add(cluster);
					InstanceList instances = clustering.getCluster(cluster);
					for (int i = 0; i < instances.size(); i++) {
						Instance inst = instances.get(i);
						trainingInstances.add(new Instance(inst.getData(), lalph.lookupLabel(new Integer(cluster)), inst.getName(), inst.getSource()));
					}
				}
			}
			trainingInstances.shuffle(random);
			Clustering trainingClustering = createSmallerClustering(trainingInstances);
			
			InstanceList testingInstances = new InstanceList(null, lalph);
			for (int i = 0; i < clustering.getNumClusters(); i++) {
				if (!clustersSampled.contains(i)) {
					InstanceList instances = clustering.getCluster(i);
					for (int j = 0; j < instances.size(); j++) {
						Instance inst = instances.get(j);
						testingInstances.add(new Instance(inst.getData(), lalph.lookupLabel(new Integer(i)), inst.getName(), inst.getSource()));
					}					
				}
			}
			testingInstances.shuffle(random);
			Clustering testingClustering = createSmallerClustering(testingInstances);
			logger.info(outputPrefixFile.value + ".train : " + trainingClustering.getNumClusters() + " objects");
			logger.info(outputPrefixFile.value + ".test : " + testingClustering.getNumClusters() + " objects");
			if (outputPrefixFile.value != null) {
				try {
					ObjectOutputStream oos =
						new ObjectOutputStream(new FileOutputStream(new File(outputPrefixFile.value + ".train")));
					oos.writeObject(new Clusterings(new Clustering[]{trainingClustering}));
					oos.close();
					oos =
						new ObjectOutputStream(new FileOutputStream(new File(outputPrefixFile.value + ".test")));
					oos.writeObject(new Clusterings(new Clustering[]{testingClustering}));
					oos.close();					
				} catch (Exception e) {
					logger.warning("Exception writing clustering to file " + outputPrefixFile.value												+ " " + e);
					e.printStackTrace();
				}
			}
			
		}
	}

	private static Clustering createSmallerClustering (InstanceList instances) {
		Clustering c = ClusterUtils.createSingletonClustering(instances);
		return ClusterUtils.mergeInstancesWithSameLabel(c);
	}
	
	static CommandOption.String inputFile =
			new CommandOption.String(
																Clusterings2Clusterings.class,
																"input",
																"FILENAME",
																true,
																"text.clusterings",
																"The filename from which to read the list of instances.",
																null);

	static CommandOption.String outputPrefixFile =
		new CommandOption.String(
															Clusterings2Clusterings.class,
															"output-prefix",
															"FILENAME",
															false,
															"text.clusterings",
															"The filename prefix to write output. Suffices 'train' and 'test' appended.",
															null);

	static CommandOption.Integer minClusterSize = 
			new CommandOption.Integer(Clusterings2Clusterings.class,
			                          "min-cluster-size",
			                          "INTEGER",			                          	
			                          false,
			                          1,
			                          "Remove clusters with fewer than this many Instances.",
			                          null);


	static CommandOption.Double trainingProportion = 
		new CommandOption.Double(Clusterings2Clusterings.class,
		                          "training-proportion",
		                          "DOUBLE",			                          	
		                          false,
		                          0.0,
		                          "Split into training and testing, with this percentage of instances reserved for training.",
		                          null);
}
