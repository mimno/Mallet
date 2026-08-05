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
    
    protected HashSet<String> stoplist;

    public SimpleTokenizer(int languageFlag) {
        stoplist = new HashSet<String>();

        if (languageFlag == USE_DEFAULT_ENGLISH_STOPLIST) {
            
            // articles
            stop("the"); stop("a");    stop("an");
            
            // conjunctions
            stop("and"); stop("or");
            
            // prepositions
            stop("of");    stop("for"); stop("in");
            stop("on");    stop("to");    stop("with");
            stop("by");
            
            // definite pronouns
            stop("this"); stop("that"); stop("these");
            stop("those"); stop("some"); stop("other");
            
            // personal pronouns
            stop("it");    stop("its"); stop("we");
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

    public HashSet<String> getStoplist() {
        return stoplist;
    }

    public void stop(String word) {
        stoplist.add(word);
    }

    public Instance pipe(Instance instance) {
            
        int underscoreCodePoint = Character.codePointAt("_", 0);

        if (instance.getData() instanceof CharSequence) {
                
            CharSequence characters = (CharSequence) instance.getData();

            ArrayList<String> tokens = new ArrayList<String>();

            int[] tokenBuffer = new int[1000];
            int length = -1;

            // Iterate by code point, not by char index. Character.codePointAt(CharSequence, int)
            //  expects a *char* (UTF-16 code unit) index, not a code-point index. Advancing the
            //  index by a fixed 1 each iteration -- while bounding the loop by codePointCount(),
            //  as this used to do -- silently drops characters from the end of any string that
            //  contains a supplementary-plane character (e.g. most emoji), and feeds the low
            //  surrogate half through Character.getType() as if it were its own character.
            //  Advancing by Character.charCount(codePoint) (1 for BMP, 2 for supplementary)
            //  keeps the char index and the decoded code point in sync.

            int stringLength = characters.length();
            int charIndex = 0;

            while (charIndex < stringLength) {

                int codePoint = Character.codePointAt(characters, charIndex);
                int codePointType = Character.getType(codePoint);

                if (codePointType == Character.LOWERCASE_LETTER ||
                    codePointType == Character.UPPERCASE_LETTER ||
                    codePoint == underscoreCodePoint) {
                    length++;
                    tokenBuffer[length] = codePoint;
                }
                else if (codePointType == Character.SPACE_SEPARATOR ||
                         codePointType == Character.LINE_SEPARATOR ||
                         codePointType == Character.PARAGRAPH_SEPARATOR ||
                         codePointType == Character.END_PUNCTUATION ||
                         codePointType == Character.DASH_PUNCTUATION ||
                         codePointType == Character.CONNECTOR_PUNCTUATION ||
                         codePointType == Character.START_PUNCTUATION ||
                         codePointType == Character.INITIAL_QUOTE_PUNCTUATION ||
                         codePointType == Character.FINAL_QUOTE_PUNCTUATION ||
                         codePointType == Character.OTHER_PUNCTUATION) {
                    
                    // Things that delimit words
                    if (length != -1) {
                        String token = new String(tokenBuffer, 0, length + 1);
                        if (! stoplist.contains(token)) {
                            tokens.add(token);
                        }
                        length = -1;
                    }
                }
                else if (codePointType == Character.COMBINING_SPACING_MARK ||
                         codePointType == Character.ENCLOSING_MARK ||
                         codePointType == Character.NON_SPACING_MARK ||
                         codePointType == Character.TITLECASE_LETTER ||
                         codePointType == Character.MODIFIER_LETTER ||
                         codePointType == Character.OTHER_LETTER) {
                    // Obscure things that are technically part of words.
                    //  Marks are especially useful for Indic scripts.

                    length++;
                    tokenBuffer[length] = codePoint;
                }
                else {
                    // Character.DECIMAL_DIGIT_NUMBER
                    // Character.CONTROL
                    // Character.MATH_SYMBOL
                    //System.out.println("type " + codePointType);
                }

                // Avoid buffer overflows
                if (length + 1 == tokenBuffer.length) {
                    String token = new String(tokenBuffer, 0, length + 1);
                    if (! stoplist.contains(token)) {
                        tokens.add(token);
                    }
                    length = -1;
                }

                charIndex += Character.charCount(codePoint);
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
