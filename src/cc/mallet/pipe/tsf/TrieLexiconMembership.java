/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
 This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 http://www.cs.umass.edu/~mccallum/mallet
 This software is provided under the terms of the Common Public License,
 version 1.0, as published by http://www.opensource.org.  For further
 information, see the file `LICENSE' included with this distribution. */

/**
 Tests membership of the token text in the provided list of phrases.
 The lexicon words are provided in a file, one space-separated phrase per line.

 @author Wei Lee and Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 Modifications by
 @author Kedar Bellare <a href="mailto:kedarb@cs.umass.edu">kedarb@cs.umass.edu</a> for joint extraction.
 */

package cc.mallet.pipe.tsf;

import java.io.*;
import java.util.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class TrieLexiconMembership extends Pipe implements Serializable {
	// Perhaps give it your own tokenizer?
	String name; // perhaps make this an array of names

	boolean ignoreCase;

	TrieLexicon lexicon;

	public TrieLexiconMembership(String name, Reader lexiconReader,
			boolean ignoreCase) {
		this.name = name;
		this.lexicon = new TrieLexicon(name, ignoreCase);
		LineNumberReader reader = new LineNumberReader(lexiconReader);
		String line;
		while (true) {
			try {
				line = reader.readLine();
			} catch (IOException e) {
				throw new IllegalStateException();
			}
			if (line == null) {
				break;
			} else {
				lexicon.add(line.intern());
			}
		}
		if (lexicon.size() == 0)
			throw new IllegalArgumentException("Empty lexicon");
	}

	public TrieLexiconMembership(String name, Reader lexiconReader,
			boolean ignoreCase, boolean includeDelims, String delim) {
		this.name = name;
		this.lexicon = new TrieLexicon(name, ignoreCase);
		LineNumberReader reader = new LineNumberReader(lexiconReader);
		String line;
		while (true) {
			try {
				line = reader.readLine();
			} catch (IOException e) {
				throw new IllegalStateException();
			}
			if (line == null) {
				break;
			} else {
				lexicon.add(line.intern(), includeDelims, delim);
			}
		}
		if (lexicon.size() == 0)
			throw new IllegalArgumentException("Empty lexicon");
	}

	public TrieLexiconMembership(String name, File lexiconFile,
			boolean ignoreCase) throws FileNotFoundException {
		this(name, new BufferedReader(new FileReader(lexiconFile)), ignoreCase);
	}

	public TrieLexiconMembership(String name, File lexiconFile,
			boolean ignoreCase, boolean includeDelims, String delim)
			throws FileNotFoundException {
		this(name, new BufferedReader(new FileReader(lexiconFile)), ignoreCase,
				includeDelims, delim);
	}

	public TrieLexiconMembership(File lexiconFile, boolean ignoreCase)
			throws FileNotFoundException {
		this(lexiconFile.getName(), lexiconFile, ignoreCase);
	}

	public TrieLexiconMembership(File lexiconFile) throws FileNotFoundException {
		this(lexiconFile.getName(), lexiconFile, true);
	}

	public Instance pipe(Instance carrier) {
		TokenSequence ts = (TokenSequence) carrier.getData();
		lexicon.addFeatures(ts);
		return carrier;
	}

	// Serialization

	private static final long serialVersionUID = 1;

	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(name);
		out.writeObject(lexicon);
		out.writeBoolean(ignoreCase);
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		int version = in.readInt();
		this.name = (String) in.readObject();
		this.lexicon = (TrieLexicon) in.readObject();
		this.ignoreCase = in.readBoolean();
	}

	private static class TrieLexicon implements Serializable {
		static final String END_OF_WORD_TOKEN = "end_of_word";

		String name;

		boolean ignoreCase;

		Hashtable lex;

		int size;

		public TrieLexicon(String name, boolean ignoreCase) {
			this.name = name;
			this.ignoreCase = ignoreCase;
			this.lex = new Hashtable();
			this.size = 0;
		}

		public void add(String word) {
			add(word, false, " ");
		}

		public void add(String word, boolean includeDelims, String delim) {
			boolean newWord = false;
			StringTokenizer st = new StringTokenizer(word, delim, includeDelims);
			Hashtable currentLevel = lex;
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if (ignoreCase)
					token = token.toLowerCase();
				if (!currentLevel.containsKey(token)) {
					currentLevel.put(token, new Hashtable());
					newWord = true;
				}
				currentLevel = (Hashtable) currentLevel.get(token);
			}
			currentLevel.put(END_OF_WORD_TOKEN, "");
			if (newWord)
				size++;
		}

		public void addFeatures(TokenSequence ts) {
			int i = 0;
			while (i < ts.size()) {
				int j = endOfWord(ts, i);
				if (j == -1) {
					i++;
				} else {
					for (; i <= j; i++) {
						Token t = ts.get(i);
						t.setFeatureValue(name, 1.0);
					}
				}
			}
		}

		private int endOfWord(TokenSequence ts, int start) {
			if (start < 0 || start >= ts.size()) {
				System.err
						.println("Lexicon.lastIndexOf: error - out of TokenSequence boundaries");
				return -1;
			}
			Hashtable currentLevel = lex;
			int end = -1;
			for (int i = start; i < ts.size(); i++) {
				Token t = ts.get(i);
				String s = t.getText();
				if (ignoreCase)
					s = s.toLowerCase();
				currentLevel = (Hashtable) currentLevel.get(s);
				if (currentLevel == null) {
					return end;
				}
				if (currentLevel.containsKey(END_OF_WORD_TOKEN)) {
					end = i;
				}
			}
			return end;
		}

		public int size() {
			return size;
		}

		// Serialization

		private static final long serialVersionUID = 1;

		private static final int CURRENT_SERIAL_VERSION = 0;

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.writeInt(CURRENT_SERIAL_VERSION);
			out.writeObject(name);
			out.writeObject(lex);
			out.writeBoolean(ignoreCase);
			out.writeInt(size);
		}

		private void readObject(ObjectInputStream in) throws IOException,
				ClassNotFoundException {
			int version = in.readInt();
			this.name = (String) in.readObject();
			this.lex = (Hashtable) in.readObject();
			this.ignoreCase = in.readBoolean();
			this.size = in.readInt();
		}

	}

}
