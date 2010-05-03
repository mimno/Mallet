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
import java.util.logging.Logger;

import cc.mallet.extract.StringSpan;
import cc.mallet.extract.StringTokenization;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.util.Lexer;
import cc.mallet.util.MalletLogger;
/**
	 Converts a string containing simple SGML tags into a dta TokenSequence of words,
	 paired with a target TokenSequence containing the SGML tags in effect for each word.

	 It does not handle nested SGML tags, nor gracefully handle malformed SGML.

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
public class SGML2TokenSequence extends Pipe implements Serializable
{
    private static Logger logger = MalletLogger.getLogger (SGML2TokenSequence.class.getName());
	Pattern sgmlPattern = Pattern.compile ("</?([^>]*)>");
	CharSequenceLexer lexer;
	String backgroundTag;

  private boolean saveSource = true;

	public SGML2TokenSequence (CharSequenceLexer lexer, String backgroundTag, boolean saveSource)
	{
		this.lexer = lexer;
		this.backgroundTag = backgroundTag;
    this.saveSource = saveSource;
  }

	public SGML2TokenSequence (CharSequenceLexer lexer, String backgroundTag)
	{
		this.lexer = lexer;
		this.backgroundTag = backgroundTag;
	}

	public SGML2TokenSequence (String regex, String backgroundTag)
	{
		this.lexer = new CharSequenceLexer (regex);
		this.backgroundTag = backgroundTag;
	}

	public SGML2TokenSequence ()
	{
		this (new CharSequenceLexer(), "O");
	}

	public Instance pipe (Instance carrier)
	{
    CharSequence string = (CharSequence) carrier.getData();
		StringTokenization dataTokens = new StringTokenization (string);
		TokenSequence targetTokens = new TokenSequence ();
		String tag = backgroundTag;
		String nextTag = backgroundTag;
		Matcher m = sgmlPattern.matcher (string);
		int textStart = 0;
		int textEnd = 0;
		int nextStart = 0;
		boolean done = false;

		logger.fine(sgmlPattern.pattern());
		logger.finer(string.toString());

		while (!done) {
			done = !(m.find());
			if (done)
				textEnd = string.length(); // culotta: changed from string.length()-1 
			else {
				String sgml = m.group();
				logger.finer ("SGML = "+sgml);

				int groupCount = m.groupCount();
				logger.finer(Integer.toString (groupCount));

				if (sgml.charAt(1) == '/')
					nextTag = backgroundTag;
				else{
					//nextTag = m.group(0);
					nextTag = sgml.substring(1, sgml.length()-1);
				}
				logger.finer("nextTag: " + nextTag);

				nextStart = m.end();  // m.end returns one beyond index of last match char
				textEnd = m.start();  // String.subtring does not include index end
				logger.finer ("Text start/end "+textStart+" "+textEnd);
			}
			if (textEnd - textStart > 0) {
				logger.finer ("Tag = "+tag);
				logger.finer ("Target = "+string.subSequence (textStart, textEnd));
				lexer.setCharSequence (string.subSequence (textStart, textEnd));
				while (lexer.hasNext()) {
          lexer.next ();
          int tokStart = textStart + lexer.getStartOffset ();
          int tokEnd = textStart + lexer.getEndOffset ();
          dataTokens.add (new StringSpan (string, tokStart, tokEnd));
					targetTokens.add (new Token (tag));
				}
			}
			textStart = nextStart;
			tag = nextTag;
		}
		carrier.setData(dataTokens);
		carrier.setTarget(targetTokens);

    if (saveSource)
		  carrier.setSource(dataTokens);

		return carrier;
	}

	public static void main (String[] args)
	{
		try {
			Pipe p = new SerialPipes (new Pipe[] {
				new Input2CharSequence (),
				new SGML2TokenSequence()
//				new SGML2TokenSequence (new CharSequenceLexer (Pattern.compile (".")), "O")
				});

			for (int i = 0; i < args.length; i++) {
				Instance carrier = p.instanceFrom(new Instance (new File(args[i]), null, null, null));
				TokenSequence data = (TokenSequence) carrier.getData();
				TokenSequence target = (TokenSequence) carrier.getTarget();
				logger.finer ("===");
				logger.info (args[i]);
				for (int j = 0; j < data.size(); j++)
					logger.info (target.get(j).getText()+" "+data.get(j).getText());
			}
		} catch (Exception e) {
			System.out.println (e);
			e.printStackTrace();
		}
	}

	// Serialization 
	
	private static final long serialVersionUID = 1;
  // Version history
  //   1: add save source
	private static final int CURRENT_SERIAL_VERSION = 1;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeObject(sgmlPattern);
		out.writeObject(lexer);
		out.writeObject(backgroundTag);
    out.writeBoolean(saveSource);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		sgmlPattern = (Pattern) in.readObject();
		lexer = (CharSequenceLexer) in.readObject();
		backgroundTag = (String) in.readObject();
    if (version == 0) saveSource = true;
	}


	
}
