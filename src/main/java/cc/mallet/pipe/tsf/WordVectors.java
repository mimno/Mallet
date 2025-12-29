package cc.mallet.pipe.tsf;

import java.io.*;
import java.util.HashMap;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class WordVectors extends Pipe implements Serializable {
	
	String prefix;
	HashMap<String, double[]> wordVectors;
	
	public WordVectors(String prefix, File vectorsFile) throws IOException {
		this.prefix = prefix;
		wordVectors = new HashMap<String, double[]>();
		
		BufferedReader in = new BufferedReader(new FileReader(vectorsFile));
		String line;
		while ((line = in.readLine()) != null) {
			String[] fields = line.split("\\s+");
			
			String word = fields[0];
			double[] vector = new double[fields.length - 1];
			for (int i = 0; i < vector.length; i++) {
				vector[i] = Double.parseDouble(fields[i+1]);
			}
			wordVectors.put(word, vector);
		}
		in.close();
	}
	
	public Instance pipe (Instance carrier) {
		
		TokenSequence tokens = (TokenSequence) carrier.getData();
		
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			String s = token.getText().toLowerCase();
			if (wordVectors.containsKey(s)) {
				double[] vector = wordVectors.get(s);
				for (int j = 0; j < vector.length; j++) {
					token.setFeatureValue (prefix + j, vector[j]);
				}
			}
		}
		
		return carrier;
	}
	
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (wordVectors);
		out.writeObject(prefix);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		wordVectors = (HashMap<String, double[]>) in.readObject();
		prefix = (String) in.readObject();
	}
	
}