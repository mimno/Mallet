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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.Hashtable;
import java.util.StringTokenizer;

import com.google.errorprone.annotations.Var;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;

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
		@Var
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
				lexicon.add(line);
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
		@Var
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
				lexicon.add(line, includeDelims, delim);
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
			@Var
			boolean newWord = false;
			StringTokenizer st = new StringTokenizer(word, delim, includeDelims);
			@Var
			Hashtable currentLevel = lex;
			while (st.hasMoreTokens()) {
				@Var
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
			@Var
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
			@Var
			Hashtable currentLevel = lex;
			@Var
			int end = -1;
			for (int i = start; i < ts.size(); i++) {
				Token t = ts.get(i);
				@Var
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
