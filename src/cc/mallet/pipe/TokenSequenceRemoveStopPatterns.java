/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;


import java.util.regex.*;
import java.util.ArrayList;
import java.io.*;

import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
/**
 * Remove tokens from the token sequence in the data field whose text matches any of a set of regular expressions.
 @author David Mimno
*/

public class TokenSequenceRemoveStopPatterns extends Pipe implements Serializable {

	ArrayList<Pattern> stopPatterns = null;

	public TokenSequenceRemoveStopPatterns() {
		stopPatterns = new ArrayList<Pattern>();
	}

	/**
	 *  Load a stop patterns from a file.
	 *  @param stoplistFile    The file to load
	 */
	public TokenSequenceRemoveStopPatterns(File patternFile) {
		stopPatterns = new ArrayList<Pattern>();
		addPatterns(patternFile);
	}

	/** 
	 *	@param patterns    An array of strings representing patterns
	 */
	public TokenSequenceRemoveStopPatterns(String[] patterns) {
		stopPatterns = new ArrayList<Pattern>();
		addPatterns(patterns);
	}

	public TokenSequenceRemoveStopPatterns addPatterns (String[] patterns) {
		for (String pattern : patterns) {
			stopPatterns.add(Pattern.compile(pattern));
		}
		return this;
	}

	public TokenSequenceRemoveStopPatterns addPatterns (File patternFile) {
		try {
			BufferedReader in = new BufferedReader(new FileReader(patternFile));
			String line = null;
			while ((line = in.readLine()) != null) {
				stopPatterns.add(Pattern.compile(line));
			}
		} catch (IOException e) {
			System.err.println("Problem reading stop pattern file: " + e.getMessage());
		} catch (PatternSyntaxException e) {
			System.err.println("Problem compiling regular expression: " + e.getMessage());
		}
		return this;
	}
	
	public Instance pipe (Instance carrier) {
		
		TokenSequence originalSequence = (TokenSequence) carrier.getData();
		TokenSequence newSequence = new TokenSequence();
		
		for (int i = 0; i < originalSequence.size(); i++) {
			Token t = originalSequence.get(i);
			
			boolean passed = true;
			String text = t.getText();
			for (Pattern pattern : stopPatterns) {
				Matcher matcher = pattern.matcher(text);
				if (matcher.matches()) {
					passed = false;
					break;
				}
			}
			
			if (passed) {
				newSequence.add (t);
			}
		}
		
		carrier.setData(newSequence);
		return carrier;
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject(stopPatterns);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		stopPatterns = (ArrayList<Pattern>) in.readObject();
	}
}
