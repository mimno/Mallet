package cc.mallet.share.upenn.ner;


import java.io.*;
import java.util.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

import gnu.trove.*;

/**
 * Checks membership in a lexicon in a text file.  Multi-token items are supported,
 * but only if the tokens are uniformly separated or not separated by spaces: that is,
 * U.S.A. is acceptable, as is San Francisco, but not St. Petersburg.
 */
public class ListMember extends Pipe implements java.io.Serializable {
    
    String name;
    Set lexicon;
    boolean ignoreCase;
    int min, max;

    public ListMember (String featureName, File lexFile, boolean ignoreCase) {
        this.name = featureName;
        this.ignoreCase = ignoreCase;

        if (!lexFile.exists())
            throw new IllegalArgumentException("File "+lexFile+" not found.");

        try {
            lexicon = new THashSet();
            min = 99999;
            max = -1;
            BufferedReader br = new BufferedReader(new FileReader(lexFile));
            while (br.ready()) {
                String s = br.readLine().trim();
                if (s.equals("")) continue; // ignore blank lines
                
                int count = countTokens(s);
                if (count < min) min = count;
                if (count > max) max = count;
                if (ignoreCase)
                    lexicon.add(s.toLowerCase());
                else
                    lexicon.add(s);
            }            
        } catch (IOException e) {
            System.err.println("Problem with "+lexFile+": "+e);
            System.exit(0);
        }
    }

    public Instance pipe (Instance carrier) {
        TokenSequence seq = (TokenSequence)carrier.getData();
        boolean[] marked = new boolean[seq.size()];
        for (int i=0; i<seq.size(); i++) {
            StringBuffer sb = new StringBuffer();
            StringBuffer sbs = new StringBuffer(); // separate tokens by spaces
            for (int j=i; j<i+max && j<seq.size(); j++) {
                // test tokens from i to j
                String text = seq.getToken(j).getText();
                sb.append(text);
                if (sbs.length() == 0) sbs.append(text);
                else sbs.append(" "+text);
                String test = ignoreCase ? sb.toString().toLowerCase() : sb.toString();
                String tests = ignoreCase ? sbs.toString().toLowerCase() : sbs.toString();
                if (j-i+1 >= min && (lexicon.contains(test) || lexicon.contains(tests)))
                    markFrom(i, j, marked);
            }
        }

        for (int i=0; i<seq.size(); i++) {
            if (marked[i])
                seq.getToken(i).setFeatureValue(name, 1.0);
        }
        return carrier;
    }
    private void markFrom (int a, int b, boolean[] marked) {
        for (int i=a; i<=b; i++) marked[i] = true;
    }

    // This method MUST count tokens the same way as the main tokenizer does!
    private int countTokens (String s) {
        // copied from Wei Li's EnronMessage2TokenSequence class
        StringTokenizer wordst = new StringTokenizer(s, "~`!@#$%^&*()_-+={[}]|\\:;\"',<.>?/ \t\n\r", true);
        return wordst.countTokens();
    }
}
