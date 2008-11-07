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
	
	HashSet<String> stoplist;

	public SimpleTokenizer(int languageFlag) {
		stoplist = new HashSet<String>();

		if (languageFlag == USE_DEFAULT_ENGLISH_STOPLIST) {
			
			// articles
			stop("the"); stop("a");	stop("an");
			
			// conjunctions
			stop("and"); stop("or");
			
			// prepositions
			stop("of");	stop("for"); stop("in");
			stop("on");	stop("to");	stop("with");
			stop("by");
			
			// definite pronouns
			stop("this"); stop("that"); stop("these");
			stop("those"); stop("some"); stop("other");
			
			// personal pronouns
			stop("it");	stop("its"); stop("we");
			stop("our");
			
			// conjuctions
			stop("as"); stop("but"); stop("not");
			
			// verbs
			stop("do"); stop("does"); stop("is");
			stop("be"); stop("are"); stop("can");
			stop("was"); stop("were");
		}
	}

	public SimpleTokenizer(File stopfile) {
		
		stoplist = new HashSet<String>();
		
		try {
			BufferedReader in =
				new BufferedReader(new InputStreamReader(new FileInputStream(stopfile), "UTF-8"));
			
			String word = null;
			while ((word = in.readLine()) != null) {
				stop(word);
			}

			in.close();

		} catch (Exception e) {
			System.err.println("problem loading stoplist: " + e);
		}
		
	}

	public SimpleTokenizer(HashSet<String> stoplist) {
		this.stoplist = stoplist;
	}

	public SimpleTokenizer deepClone() {
		return new SimpleTokenizer((HashSet<String>) stoplist.clone());
	}

	public void stop(String word) {
		stoplist.add(word);
	}

	public Instance pipe(Instance instance) {
			
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

			instance.setData(tokens);
		}
		else {
			throw new IllegalArgumentException("Looking for a CharSequence, found a " + 
											   instance.getData().getClass());
		}
		
		return instance;
	}

	static final long serialVersionUID = 1;

}
