package cc.mallet.util;

import java.io.*;
import java.util.regex.*;

public class Replacement implements Serializable {
	String target;
	String[] tokens;

	/** if the input string contains two sections
            separated by a tab, the target is the second field,
            with all whitespace replaced by underscores.
            if the input is a single field, the target is
            the input with all whitespace replace by underscores.
	*/
	public Replacement(String line) {
		String[] fields = line.split("\t");

		this.tokens = fields[0].split(" ");

		if (fields.length == 2) {
			target = fields[1].replaceAll("\\s+", "_");
		}
		else if (fields.length == 1) {
			target = fields[0].replaceAll("\\s+", "_");
		}

		// Normally we do not allow tokens that start with numbers.
		//  If the user has specifically requested a replacement that
		//  starts with a number, escape it with an underscore.
		if (Character.getType(target.codePointAt(0)) == Character.DECIMAL_DIGIT_NUMBER) {
			target = "_" + target;
		}
	}

	/** A version that specifies a target, which may be an empty string */
	public Replacement(String source, String target) {
		this.tokens = source.split(" ");
		this.target = target;
	}

	public int apply(String[] input, int startPosition, StringBuilder output) {

		// Start at the second token, since we assume that the first token matches
		//  if this replacement is being matched.
		for (int i=1; i < tokens.length; i++) {
			if (startPosition + i >= input.length) { return startPosition; }
			if (! input[startPosition + i].equals(tokens[i])) { return startPosition; }
		}
		
		// We've matched all tokens in the source string, 
		//  so modify the input to notify the preprocessor of the replacement, 
		//  output the target string and advance the position
		input[startPosition] = target;
		output.append(target + " ");
		return startPosition + tokens.length;
	}

	public String getFirstToken() {
		return tokens[0];
	}

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(target);
		out.writeObject(tokens);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		target = (String) in.readObject();
		tokens = (String[]) in.readObject();
	}
}
