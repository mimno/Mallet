package cc.mallet.share.upenn.ner;


import java.util.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
import com.carrotsearch.hppc.ObjectDoubleHashMap;

/**
 * Adds all features of tokens in the window to the center token.
 */
public class FeatureWindow extends Pipe implements java.io.Serializable {

    int left, right;
    public FeatureWindow (int left, int right) {
        assert (left >= 0 && right >= 0);
        this.left = left;
        this.right = right;
    }

    public Instance pipe (Instance carrier) {
        TokenSequence seq = (TokenSequence)carrier.getData();
        ObjectDoubleHashMap[] original = new ObjectDoubleHashMap[seq.size()];
        for (int i=0; i<seq.size(); i++) {
            Token t = seq.get(i);
            original[i] = new ObjectDoubleHashMap();
            PropertyList.Iterator pl = t.getFeatures().iterator();
            while (pl.hasNext()) {
                pl.nextProperty();
                original[i].put(pl.getKey(), pl.getNumericValue());
            }
        }
        
        for (int i=0; i<original.length; i++) { // add to features of token i...
            for (int j = -1 * left; j <= right; j++) {
                int index = i + j; //...the features of token index
                String append = (j < 0) ? "/"+j : "/+"+j; 
                if (index<0 || index==i || index>=original.length) continue;
                
                Token t = seq.get(i);

                for (Object feature : original[index].keys())
                    t.setFeatureValue((String)feature+append,
                                      original[index].get(feature));
            }
        }
        return carrier;
    }
}
