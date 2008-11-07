package cc.mallet.pipe;

import cc.mallet.types.*;

import java.util.HashSet;
import java.util.ArrayList;
import java.io.*;

/** 
 *  A simple unicode tokenizer that accepts sequences of letters 
 *  as tokens.
 */

public class SimpleTokenizer extends Pipe {

	public static final int USE_EMPTY_STOPLIST = 0;
	public static final int USE_DEFAULT_ENGLISH_STOPLIST = 1;
	
	public long totalNanos = 0;
	HashSet<String> stoplist;

	public SimpleTokenizer(int languageFlag) {
		stoplist = new HashSet<String>();

		if (languageFlag == USE_DEFAULT_ENGLISH_STOPLIST) {
			
			// articles
			stoplist.add("the"); stoplist.add("a");	stoplist.add("an");
			
			// conjunctions
			stoplist.add("and"); stoplist.add("or");
			
			// prepositions
			stoplist.add("of");	stoplist.add("for"); stoplist.add("in");
			stoplist.add("on");	stoplist.add("to");	stoplist.add("with");
			stoplist.add("by");
			
			// definite pronouns
			stoplist.add("this"); stoplist.add("that"); stoplist.add("these");
			stoplist.add("those"); stoplist.add("some"); stoplist.add("other");
			
			// personal pronouns
			stoplist.add("it");	stoplist.add("its"); stoplist.add("we");
			stoplist.add("our");
			
			// conjuctions
			stoplist.add("as"); stoplist.add("but"); stoplist.add("not");
			
			// verbs
			stoplist.add("do"); stoplist.add("does"); stoplist.add("is");
			stoplist.add("be"); stoplist.add("are"); stoplist.add("can");
			stoplist.add("was"); stoplist.add("were");
		}
	}

	public SimpleTokenizer(File stopfile) {
		
		stoplist = new HashSet<String>();
		
		try {
			BufferedReader in =
				new BufferedReader(new InputStreamReader(new FileInputStream(stopfile), "UTF-8"));
			
			String word = null;
			while ((word = in.readLine()) != null) {
				stoplist.add(word);
			}

			in.close();

		} catch (Exception e) {
			System.err.println("problem loading stoplist: " + e);
		}
		
	}

	public void addStopword(String word) {
		stoplist.add(word);
	}

	public Instance pipe(Instance instance) {
			
		long start = System.nanoTime();

		if (instance.getData() instanceof CharSequence) {
				
			CharSequence characters = (CharSequence) instance.getData();

			ArrayList<String> tokens = new ArrayList<String>();


			int[] tokenBuffer = new int[1000];
			int length = -1;

			// Using code points instead of chars allows us
			//  to support extended Unicode, and has no significant
			//  efficiency costs.
			
			int totalCodePoints = Character.codePointCount(characters, 0, characters.length());

			for (int i=0; i < totalCodePoints; i++) {

				int codePoint = Character.codePointAt(characters, i);

				if (Character.isLetter(codePoint)) {
					length++;
					tokenBuffer[length] = codePoint;
				}
				else if (length != -1) {
					String token = new String(tokenBuffer, 0, length + 1);
					if (! stoplist.contains(token)) {
						tokens.add(token);
					}
					length = -1;
				}
			}

			if (length != -1) {
				String token = new String(tokenBuffer, 0, length + 1);
				if (! stoplist.contains(token)) {
					tokens.add(token);
				}
			}


			/*


			StringBuffer tokenBuffer = null;

			// Using code points instead of chars allows us
			//  to support extended Unicode, and has no significant
			//  efficiency costs.
			
			int totalCodePoints = Character.codePointCount(characters, 0, characters.length());

			for (int i=0; i < totalCodePoints; i++) {

				int codePoint = Character.codePointAt(characters, i);

				if (Character.isLetter(codePoint)) {
					if (tokenBuffer == null) { tokenBuffer = new StringBuffer(); }
					//tokenBuffer.append(Character.toChars(codePoint));
				}
				else if (tokenBuffer != null) {
					String token = tokenBuffer.toString();
					if (! stoplist.contains(token)) {
						tokens.add(token);
					}
					tokenBuffer = null;
				}
			}

			if (tokenBuffer != null) {
				String token = tokenBuffer.toString();
				if (! stoplist.contains(token)) {
					tokens.add(token);
				}
				tokenBuffer = null;
			}

			*/

			instance.setData(tokens);
		}
		else {
			throw new IllegalArgumentException("Looking for a CharSequence, found a " + 
											   instance.getData().getClass());
		}

		totalNanos += System.nanoTime() - start;
		
		return instance;
	}

	static final long serialVersionUID = 1;

}
