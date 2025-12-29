/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.pipe;

import java.io.*;
import java.net.URI;
import java.util.regex.*;
import java.util.Set;

import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.util.Lexer;
/**
	 Similar to {@link SGML2TokenSequence}, except that only the tags
	 listed in <code>allowedTags</code> are converted to {@link Label}s.

   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */
public class SelectiveSGML2TokenSequence extends Pipe implements Serializable
{
	Pattern sgmlPattern = Pattern.compile ("</?([^>]*)>");
	CharSequenceLexer lexer;
	String backgroundTag;
	Set allowedTags;
	
	/**
		 @param lexer to tokenize input
		 @param backgroundTag default tag when not in any other tag
		 @param allowed set of tags (Strings) that will be converted to
		 labels
	 */
	public SelectiveSGML2TokenSequence (CharSequenceLexer lexer, String backgroundTag, Set allowed)
	{
		this.lexer = lexer;
		this.backgroundTag = backgroundTag;
		this.allowedTags = allowed;
	}

	public SelectiveSGML2TokenSequence (String regex, String backgroundTag,
																			Set allowed)
	{
		this (new CharSequenceLexer (regex), backgroundTag, allowed);
	}

	public SelectiveSGML2TokenSequence (Set allowed)
	{
		this (new CharSequenceLexer(), "O", allowed);
	}

	public SelectiveSGML2TokenSequence (CharSequenceLexer lex, Set allowed)
	{
		this (lex, "O", allowed);
	}

	public Instance pipe (Instance carrier)
	{
		if (!(carrier.getData() instanceof CharSequence))
			throw new ClassCastException ("carrier.data is a " + carrier.getData().getClass().getName() +
																	 " not a CharSequence");
		TokenSequence dataTokens = new TokenSequence ();
 		TokenSequence targetTokens = new TokenSequence ();
		CharSequence string = (CharSequence) carrier.getData();
		String tag = backgroundTag;
		String nextTag = backgroundTag;
		Matcher m = sgmlPattern.matcher (string);
		int textStart = 0;
		int textEnd = 0;
		int nextStart = 0;
		boolean done = false;

		while (!done) {
			done = !findNextValidMatch (m);
			if (done)
				textEnd = string.length()-1;
			else {
				String sgml = m.group();
				int groupCount = m.groupCount();
				if (sgml.charAt(1) == '/')
					nextTag = backgroundTag;
				else{
					nextTag = m.group(0);
					nextTag = sgml.substring(1, sgml.length()-1);
				}
				nextStart = m.end();
				textEnd = m.start();
			}
			if (textEnd - textStart > 0) {
				lexer.setCharSequence (string.subSequence (textStart, textEnd));
				while (lexer.hasNext()) {
					dataTokens.add (new Token ((String) lexer.next()));
					targetTokens.add (new Token (tag));
				}
			}
			textStart = nextStart;
			tag = nextTag;
		}
		carrier.setData(dataTokens);
		carrier.setTarget(targetTokens);

		carrier.setSource(dataTokens);

		return carrier;
	}


	/**
		 Finds the next match contained in <code> allowedTags </code>.
	 */
	private boolean findNextValidMatch (Matcher m) {
		if (!m.find ())
			return false;
		String sgml = m.group();		
		int start = m.start ();
		int first = 1;
		int last = sgml.length() - 1; 
		if (sgml.charAt(1) == '/')
			first = 2;
		sgml = sgml.substring (first, last);
		if (allowedTags.contains (sgml)) {
			m.find (start);
			return true;
		}
		else return findNextValidMatch (m);
	}

	public String toString () {
		String ret = "sgml pattern: " + sgmlPattern.toString();
		ret += "\nlexer: " + lexer.getPattern().toString();
		ret += "\nbg tag: " + backgroundTag.toString();
		ret += "\nallowedHash: " + allowedTags + "\n";
		return ret;
	}
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(sgmlPattern);
		out.writeObject(lexer);
		out.writeObject(backgroundTag);
		out.writeObject(allowedTags);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		sgmlPattern = (Pattern) in.readObject();
		lexer = (CharSequenceLexer) in.readObject();
		backgroundTag = (String) in.readObject();
		allowedTags = (Set) in.readObject();
	}
}
