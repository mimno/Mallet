package cc.mallet.pipe;

import java.util.*;
import java.io.*;

import cc.mallet.util.*;
import cc.mallet.types.*;
import java.util.regex.*;

/** This pipe changes text to lowercase, removes common XML entities (quot, apos, lt, gt), and replaces all punctuation
	except the - character with whitespace. It then breaks up tokens on whitespace and applies n-gram token replacements
	and deletions. Replacements are applied in the order they are specified, first by file and then within files.
*/

public class NGramPreprocessor extends Pipe implements Serializable {

	public ArrayList<ReplacementSet> replacementSets;

	public NGramPreprocessor () {
		replacementSets = new ArrayList<ReplacementSet>();
	}

	public int loadReplacements(String filename) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(filename));
		
		ReplacementSet set = new ReplacementSet();

		int totalReplacements = 0;
		
		String line;
		while ((line = in.readLine()) != null) {
			set.addReplacement(new Replacement(line));
			totalReplacements++;
		}
		in.close();
		
		replacementSets.add(set);
		
		return totalReplacements;
	}
	
	public int loadDeletions(String filename) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(filename));
			
		ReplacementSet set = new ReplacementSet();

		int totalReplacements = 0;
		
		String line;
		while ((line = in.readLine()) != null) {
			set.addReplacement(new Replacement(line, ""));
			totalReplacements++;
		}
		in.close();
		
		replacementSets.add(set);

		return totalReplacements;
	}

	public Instance pipe (Instance instance) {

		String input = (String) instance.getData();

		input = input.toLowerCase();

		//System.out.println(input);

		// Remove XML entities
		input = input.replaceAll("&apos;", "'");
		input = input.replaceAll("&quot;", "\"");
		input = input.replaceAll("&gt;", "<");
		input = input.replaceAll("&lt;", ">");

		// Remove all non letter/number characters except dash and apostrophe
		input = input.replaceAll("[^\\p{L}\\p{N}\\-\\']", " ");

		// Reduce all blocks of whitespace to a single space
		input = input.replaceAll("\\s+", " ");
		
		for (ReplacementSet set: replacementSets) {
			input = set.applyReplacements(input);
		}
	
		//System.out.println(input);
	
		instance.setData(input);

		return instance;
		
	}

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(replacementSets);
	}
    
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		replacementSets = (ArrayList<ReplacementSet>) in.readObject();
	}

	public class ReplacementSet implements Serializable {
		HashMap<String, ArrayList<Replacement>> replacementIndex;

		public ReplacementSet() {
			replacementIndex = new HashMap<String, ArrayList<Replacement>>();
		}
		
		public void addReplacement(Replacement replacement) {
			String key = replacement.getFirstToken();
			
			if (! replacementIndex.containsKey(key)) {
				replacementIndex.put(key, new ArrayList<Replacement>());
			}
			
			replacementIndex.get(key).add(replacement);
		}

		public String applyReplacements(String input) {
			String[] tokens = input.split(" ");
			StringBuilder output = new StringBuilder();

			int position = 0;
			while (position < tokens.length) {
				String token = tokens[position];
				
				int initialPosition = position;
				
				if (replacementIndex.containsKey(token)) {
					for (Replacement replacement: replacementIndex.get(token)) {
						position = replacement.apply(tokens, position, output);
						if (position > initialPosition) {
							break;
						}
					}
				}
				
				if (position == initialPosition) {
					// No replacements were made, so nothing
					//  has been output.
					output.append(token + " ");
					position ++;
				}
			}

			return output.toString();
		}

		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 1;
		
		private void writeObject(ObjectOutputStream out) throws IOException {
			out.writeInt (CURRENT_SERIAL_VERSION);
			out.writeObject(replacementIndex);
		}
        
		private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
			int version = in.readInt ();
			replacementIndex = (HashMap<String, ArrayList<Replacement>>) in.readObject();
		}

	}
	
}