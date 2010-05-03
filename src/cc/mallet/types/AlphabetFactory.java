package cc.mallet.types;

import java.io.*;

public class AlphabetFactory {

	/** Create a dummy alphabet with <code>n</code> dimensions */
	public static Alphabet alphabetOfSize (int n) {
		Alphabet alphabet = new Alphabet();
		for (int i = 0; i < n; i++) {
			alphabet.lookupIndex("d" + i);
		}
		return alphabet;
	}

	/** Create a dummy label alphabet with <code>n</code> dimensions */
	public static LabelAlphabet labelAlphabetOfSize (int n) {
		LabelAlphabet alphabet = new LabelAlphabet();
		for (int i = 0; i < n; i++) {
			alphabet.lookupIndex("d" + i);
		}
		return alphabet;
	}

	/** Load an alphabet from a file, one item per line */
	public static Alphabet loadFromFile(File alphabetFile) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(alphabetFile));
		Alphabet alphabet = new Alphabet();
		String item;

		while ((item = reader.readLine()) != null) {
			alphabet.lookupIndex(item);
		}
		
		reader.close();

		return alphabet;
	}
	
}