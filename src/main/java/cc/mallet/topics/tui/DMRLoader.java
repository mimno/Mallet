package cc.mallet.topics.tui;

import cc.mallet.classify.*;
import cc.mallet.types.*;
import cc.mallet.pipe.*;

import java.util.logging.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

/**
 *  This class loads data into the format for the MALLET 
 *   Dirichlet-multinomial regression (DMR). DMR topic models
 *  learn topic assignments conditioned on observed features.
 *  <p>
 *  The input format consists of two files, one for text and
 *   the other for features. The "text" file consists of one document
 *   per line. This class will tokenize and remove stopwords.
 *  <p>
 *  The "features" file contains whitespace-delimited features in this format:
 *    <code>blue heavy width=12.08</code>
 *  Features without explicit values ("blue" and "heavy" in the example) are set to 1.0.
 */

public class DMRLoader implements Serializable {

    public static BufferedReader openReader(File file) throws IOException {
		BufferedReader reader = null;
	
		if (file.toString().endsWith(".gz")) {
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
        }
        else {
            reader = new BufferedReader(new FileReader (file));
        }

		return reader;
    }

	public void load(File wordsFile, File featuresFile, File instancesFile) throws IOException, FileNotFoundException {

		Pipe instancePipe =
			new SerialPipes (new Pipe[] {
					(Pipe) new TargetStringToFeatures(),
					(Pipe) new CharSequence2TokenSequence(),
					(Pipe) new TokenSequenceLowercase(),
					(Pipe) new TokenSequenceRemoveStopwords(false, false),
					(Pipe) new TokenSequence2FeatureSequence()
				});

		InstanceList instances = new InstanceList (instancePipe);
		
		ArrayList<Instance> instanceBuffer = new ArrayList<Instance>();

        BufferedReader wordsReader = openReader(wordsFile);
        BufferedReader featuresReader = openReader(featuresFile);
        
        int lineNumber = 1;
        String wordsLine = null;
		String featuresLine = null;

        while ((wordsLine = wordsReader.readLine()) != null) {
			if ((featuresLine = featuresReader.readLine()) == null) {
				System.err.println("ran out of features");
				System.exit(0);
			}

			if (featuresLine.equals("")) { continue; }
	
			instanceBuffer.add(new Instance(wordsLine, featuresLine, String.valueOf(lineNumber), null));

			lineNumber++;
        }

		instances.addThruPipe(instanceBuffer.iterator());

        ObjectOutputStream oos = 
			new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(instancesFile)));
        oos.writeObject(instances);
        oos.close();


    }

    public static void main (String[] args) throws FileNotFoundException, IOException {

		if (args.length != 3) {
			System.err.println("Usage: DMRLoader [words file] [features file] [instances file]");
			System.exit(0);
		}

		File wordsFile = new File(args[0]);
		File featuresFile = new File(args[1]);
		File instancesFile = new File(args[2]);

		DMRLoader loader = new DMRLoader();
		loader.load(wordsFile, featuresFile, instancesFile);

	}

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
}
