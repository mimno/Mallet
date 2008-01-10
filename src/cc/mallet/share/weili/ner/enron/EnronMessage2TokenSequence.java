/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
   @author Wei Li <a href="mailto:weili@cs.umass.edu">weili@cs.umass.edu</a>
 */

package cc.mallet.share.weili.ner.enron;

import java.util.regex.*;
import java.util.*;
import java.io.*;

import cc.mallet.pipe.*;
import cc.mallet.share.weili.ner.*;
import cc.mallet.types.*;

public class EnronMessage2TokenSequence extends Pipe implements Serializable
{
	boolean saveSource = false;
	public static String[] skip = new String[] {"=_part_", "sent by:"};
	public static String[] skipToBlankLine = new String[] {"subject:", "original message", 
																		"content-type:", "content-transfer-encoding:", "forwarded by", 
																		"from:", "sent:", "to:", "bcc:", "cc:"};
	public static String[] labels = new String[] {"DATE", "TIME", "LOCATION", "PERSON",
													 					"ORGANIZATION", "ACRONYM", "PHONE", "MONEY", "PERCENT"};
	HashSet headerPersonNames;
	
	public EnronMessage2TokenSequence ()
	{
		super (null, new LabelAlphabet());
		headerPersonNames = new HashSet();
	}

	public Instance pipe (Instance carrier)
	{
		TokenSequence data = new TokenSequence ();
		LabelSequence target = new LabelSequence ((LabelAlphabet)getTargetAlphabet());
		StringBuffer source = saveSource ? new StringBuffer() : null;
		WordTransformation wt = new WordTransformation();

		File f = (File) carrier.getData();
		StringBuffer message = new StringBuffer();
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			//skip the header before the first blank line
			String line = br.readLine();
			while (line != null) {
				if (line.equals("")) break;
				int i;
				line = line.toLowerCase();
				for (i = 5; i <= 9; i++) {
					if (line.startsWith(skipToBlankLine[i])) break;
				}
				if (i <= 9) {
					String header = line.substring(skipToBlankLine[i].length());
					while ((line = br.readLine()) != null) {
						if (line.equals("")) break;
						if (line.startsWith(" ") || line.startsWith("\t")) 
							header += line;
						else break;
					}
					StringTokenizer st = new StringTokenizer(header, " \t,");
					while (st.hasMoreTokens()) {
						String token = st.nextToken();
						if (!token.endsWith("@enron.com")) {
							continue;
						}
						token = token.substring(0, token.length()-10);
						int dot = token.indexOf(".");
						if (dot == -1) {
							continue;
						}
						if (dot != token.lastIndexOf(".")) {
							if (dot == token.lastIndexOf(".")-1) {
								dot++;
								if (dot+1 < token.length()-1)
									headerPersonNames.add(token.substring(dot+1));
							}
							continue;
						}
						if (dot > 1)
							headerPersonNames.add(token.substring(0, dot));
						if (dot+1 < token.length()-1)
							headerPersonNames.add(token.substring(dot+1));
					}
				}
				else line = br.readLine();
			}

			while ((line = br.readLine()) != null) {
				boolean header = false;
				for (int i = 0; i < skip.length; i++) {
					int index = line.toLowerCase().indexOf(skip[i]);
					if (index != -1) {
						String prefix = line.substring(0, index).trim();
						header = true;
						for (int j = 0; j < prefix.length(); j++) {
							if (prefix.charAt(j) != '-' && prefix.charAt(j) != '>' && prefix.charAt(j) != ' ') {
								header = false;
								break;
							}
						}
						if (header) break;
					}
				}
				if (header) continue;

				for (int i = 0; i < skipToBlankLine.length; i++) {
					int index = line.toLowerCase().indexOf(skipToBlankLine[i]);
					if (index != -1) {
						String prefix = line.substring(0, index).trim();
						header = true;
						for (int j = 0; j < prefix.length(); j++) {
							if (prefix.charAt(j) != '-' && prefix.charAt(j) != '>' && prefix.charAt(j) != ' ') {
								header = false;
								break;
							}
						}
						if (header) break;
					}
				}
				if (header) {
					while ((line = br.readLine()) != null) {
						if (line.equals("")) break;
					}
					continue;
				}				

				message.append(line);
				message.append("\n");
			}
		}
		catch (IOException e) {System.err.println(e);}
		
		String currentLabel = "O";
		StringTokenizer st = new StringTokenizer(message.toString(), "<>", true);
		boolean readText = true;
		String text = null;
		while (st.hasMoreTokens()) {
			if (readText) text = st.nextToken();
			readText = true;

			if (text.equals("<")) {
				String tag = st.nextToken();
				if (tag.equals("/ENAMEX") || tag.equals("/TIMEX") || tag.equals("/NUMEX")) {
					String nextToken = st.nextToken();
					assert (nextToken.equals(">"));
					currentLabel = "O";
					continue;
				}
				else if (tag.startsWith("ENAMEX") || tag.startsWith("TIMEX") || tag.startsWith("NUMEX")) {
					String type = tag.substring(tag.indexOf(" ")+1);
					assert (type.startsWith("TYPE="));
					type = type.substring(type.indexOf("\"")+1, type.lastIndexOf("\""));
					// nested entities (should do something)
					//if (!currentLabel.equals("O")) {
					//}

					for (int i = 0; i < labels.length; i++) {
						if (labels[i].equals(type)) {
							currentLabel = "B-" + type;
							break;
						}
					}
					String nextToken = st.nextToken();
					assert (nextToken.equals(">"));
					continue;
				}
				else {//false alarm
					data.add(new Token("<"));
					target.add(currentLabel);
					if (saveSource) {
						source.append ("<");
						source.append ("\n");
					}
					text = tag;
					readText = false;
				}
			}

			// there is no tag in "text"
			StringTokenizer wordst = new StringTokenizer(text, "~`!@#$%^&*()_-+={[}]|\\:;\"',<.>?/ \t\n\r", true);
			while (wordst.hasMoreTokens()) {
				String word = wordst.nextToken();
				if (word.equals(" ") || word.equals("\t") || word.equals("\n") || word.equals("\r")) continue;				
				String originalWord = word;
				Token token = wt.transformedToken (word);
				// Check if the token is in headerPersonNames
				if (headerPersonNames.contains(word.toLowerCase())) {
					token.setFeatureValue("HEADER-PERSON", 1.0);
				}
		
				// Append
				data.add (token);
				target.add (currentLabel);
				if (saveSource) {
					source.append (originalWord);
					source.append ("\n");
				}
		
				if (currentLabel.startsWith("B-")) currentLabel = "I-" + currentLabel.substring(2);
			}
		}

		carrier.setData(data);
		carrier.setTarget(target);
		if (saveSource)
			carrier.setSource(source);
		return carrier;
	}

	public void write (File f) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
			oos.writeObject(headerPersonNames);
			oos.close();
		}
		catch (IOException e) {
			System.err.println("Exception writing file " + f + ": " + e);
		}
	}
	// Serialization 
	
	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeBoolean(saveSource);
		out.writeObject(headerPersonNames);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt();
		saveSource = in.readBoolean();
		headerPersonNames = (HashSet)in.readObject();
	}
}
