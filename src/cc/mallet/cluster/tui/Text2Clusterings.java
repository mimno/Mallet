package cc.mallet.cluster.tui;

import gnu.trove.TIntArrayList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;

import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.Clusterings;
import cc.mallet.cluster.Record;
import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.FileIterator;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;

//In progress
public class Text2Clusterings {

	private static Logger logger =
			MalletLogger.getLogger(Text2Clusterings.class.getName());

	public static void main (String[] args) throws IOException {
		CommandOption
									.setSummary(Text2Clusterings.class,
															"A tool to convert a list of text files to a Clusterings.");
		CommandOption.process(Text2Clusterings.class, args);

		if (classDirs.value.length == 0) {
			logger
						.warning("You must include --input DIR1 DIR2 ...' in order to specify a"
											+ "list of directories containing the documents for each class.");
			System.exit(-1);
		}

		Clustering[] clusterings = new Clustering[classDirs.value.length];
		int fi = 0;
		for (int i = 0; i < classDirs.value.length; i++) {
			Alphabet fieldAlph = new Alphabet();
			Alphabet valueAlph = new Alphabet();
			File directory = new File(classDirs.value[i]);
			File[] subdirs = getSubDirs(directory);
			Alphabet clusterAlph = new Alphabet();
			InstanceList instances = new InstanceList(new Noop());
			TIntArrayList labels = new TIntArrayList();
			for (int j = 0; j < subdirs.length; j++) {
				ArrayList<File> records = new FileIterator(subdirs[j]).getFileArray();
				int label = clusterAlph.lookupIndex(subdirs[j].toString());
				for (int k = 0; k < records.size(); k++) {
					if (fi % 100 == 0) System.out.print(fi);
					else if (fi % 10 == 0) System.out.print(".");
					if (fi % 1000 == 0 && fi > 0) System.out.println();
					System.out.flush();
					fi++;


					File record = records.get(k);
					labels.add(label);
					instances.add(new Instance(new Record(fieldAlph, valueAlph, parseFile(record)),
												new Integer(label), record.toString(),
												record.toString()));
				}
			}
			clusterings[i] =
					new Clustering(instances, subdirs.length, labels.toNativeArray());
		}

		logger.info("\nread " + fi + " objects in " + clusterings.length + " clusterings.");
		try {
			ObjectOutputStream oos =
					new ObjectOutputStream(new FileOutputStream(outputFile.value));
			oos.writeObject(new Clusterings(clusterings));
			oos.close();
		} catch (Exception e) {
			logger.warning("Exception writing clustering to file " + outputFile.value
											+ " " + e);
			e.printStackTrace();
		}

	}

	public static File[] getSubDirs (File dir) throws IOException {
		ArrayList<File> ret = new ArrayList<File>();
		File[] fs = dir.listFiles();
		for (File f : fs)
			if (f.isDirectory() && !f.getName().matches("^\\.+$"))
				ret.add(f);
		return ret.toArray(new File[] {});
	}

	public static String[][] parseFile (File f) throws IOException {
		BufferedReader r = new BufferedReader(new FileReader(f));
		String line = "";
		ArrayList<String[]> lines = new ArrayList<String[]>();
		while ((line = r.readLine()) != null) {
			line = line.trim();
			String[] words = line.split("\\s+");
			if (words.length > 1)
				lines.add(words);
		}
		String[][] ret = new String[lines.size()][];
		for (int i = 0; i < lines.size(); i++)
			ret[i] = lines.get(i);
		return ret;
	}

	static CommandOption.SpacedStrings classDirs =
			new CommandOption.SpacedStrings(
																			Text2Clusterings.class,
																			"input",
																			"DIR...",
																			true,
																			null,
																			"The directories containing text files to be clustered, one directory per clustering",
																			null);

	static CommandOption.String outputFile =
			new CommandOption.String(Text2Clusterings.class, "output", "FILENAME",
																true, "text.clusterings",
																"The filename to write the Clustering.", null);

}
