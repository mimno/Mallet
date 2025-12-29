package cc.mallet.share.upenn.ner;


import java.util.regex.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

/**
 * Matches a regular expression which spans several tokens.
 */
public class LongRegexMatches extends Pipe implements java.io.Serializable {

    String name;
    Pattern regex;
    int min; // how many tokens to merge for a match
    int max;

    public LongRegexMatches (String featureName, Pattern regex, int min, int max) {
		this.name = featureName;
		this.regex = regex;
        this.min = min;
        this.max = max;
	}

    public Instance pipe (Instance carrier) {
        TokenSequence ts = (TokenSequence) carrier.getData();
        boolean[] marked = new boolean[ts.size()]; // avoid setting features twice

        for (int i=0; i < ts.size(); i++) {
            // On reaching a new token, test all strings with at least
            // min tokens which end in the new token.
            StringBuffer sb = new StringBuffer();
            // start by testing rightmost suffix, and grow leftward
            for (int length = 1; length <= max; length++) {
                int loc = i - length + 1;
                if (loc < 0) break; // take another token
                sb.insert(0, ts.get(loc).getText()); // else prepend token
                // On a match, mark all participating tokens.
                if (length >= min && regex.matcher(sb.toString()).matches()) {
                    for (int j=0; j<length; j++)
                        marked[loc+j] = true;
                }
            }
        }

        // Set feature on all tokens participating in any match
        for (int i=0; i < ts.size(); i++)
            if (marked[i])
                ts.get(i).setFeatureValue(name, 1.0);
        
		return carrier;
    }

}
