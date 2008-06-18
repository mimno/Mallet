package cc.mallet.cluster.tui;


import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.logging.Logger;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.Clusterings;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;

//In progress
public class Clusterings2Info {

	private static Logger logger =
			MalletLogger.getLogger(Clusterings2Info.class.getName());

	public static void main (String[] args) {
		CommandOption
									.setSummary(Clusterings2Info.class,
															"A tool to print statistics about a Clusterings.");
		CommandOption.process(Clusterings2Info.class, args);

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

		if (printOption.value) {
			for (int i = 0; i < clusterings.size(); i++) {
				Clustering c = clusterings.get(i);
				for (int j = 0; j < c.getNumClusters(); j++) {
					InstanceList cluster = c.getCluster(j);
					for (int k = 0; k < cluster.size(); k++) {
						System.out.println("clustering " + i + " cluster " + j + " element " + k + " " + cluster.get(k).getData());
					}
					System.out.println();
				}
			}
		}
		logger.info("number clusterings=" + clusterings.size());

		int totalInstances = 0;
		int totalClusters = 0;

		for (int i = 0; i < clusterings.size(); i++) {
			Clustering c = clusterings.get(i);
			totalClusters += c.getNumClusters();
			totalInstances += c.getNumInstances();
		}
		logger.info("total instances=" + totalInstances);
		logger.info("total clusters=" + totalClusters);
		logger.info("instances per clustering=" + (double) totalInstances
								/ clusterings.size());
		logger.info("instances per cluster=" + (double) totalInstances
								/ totalClusters);
		logger.info("clusters per clustering=" + (double) totalClusters
								/ clusterings.size());
	}

	static CommandOption.String inputFile =
			new CommandOption.String(
																Clusterings2Info.class,
																"input",
																"FILENAME",
																true,
																"text.vectors",
																"The filename from which to read the list of instances.",
																null);

	static CommandOption.Boolean printOption = 
			new CommandOption.Boolean(Clusterings2Info.class,
			                          "print",
			                          "BOOLEAN",			                          	
			                          false,
			                          false,
			                          "If true, print all clusters",
			                          null);
}
