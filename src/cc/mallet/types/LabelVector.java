/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.types;

import cc.mallet.types.Label;
import cc.mallet.types.RankedFeatureVector;

public class LabelVector extends RankedFeatureVector implements Labeling {
    public LabelVector (LabelAlphabet dict, int[] features, double[] values) {
        super (dict, features, values);
    }

    private static int[] indicesForLabels (Label[] labels) {
        int[] indices = new int[labels.length];
        for (int i = 0; i < labels.length; i++)
            indices[i] = labels[i].getIndex();
        return indices;
    }

    public LabelVector (Label[] labels, double[] values) {
        super (labels[0].dictionary, indicesForLabels(labels), values);
    }

    public LabelVector (LabelAlphabet dict, double[] values) {
        super (dict, values);
    }

    @Override public final Label labelAtLocation (int loc) {
        return ((LabelAlphabet)dictionary).lookupLabel(indexAtLocation (loc));
    }

    @Override public LabelAlphabet getLabelAlphabet () {
        return (LabelAlphabet) dictionary;
    }
    
    // Labeling interface

    // xxx Change these names to better match RankedFeatureVector?

    @Override public int getBestIndex () {
        if (rankOrder == null) {
            setRankOrder ();
        }
        return rankOrder[0];
    }

    @Override public Label getBestLabel () {
        return ((LabelAlphabet)dictionary).lookupLabel (getBestIndex());
    }

    @Override public double getBestValue () {
        if (rankOrder == null) {
            setRankOrder ();
        }
        return values[rankOrder[0]];
    }

    @Override public double value (Label label) {
        assert (label.dictionary  == this.dictionary);
        return values[this.location (label.toString ())];
    }

    @Override public int getRank (Label label) {

        //throw new UnsupportedOperationException ();
        // CPAL - Implemented this
        
        if (rankOrder == null) {
            setRankOrder();
        }
        
        int ii=-1;
        int tmpIndex = ((LabelAlphabet)dictionary).lookupIndex(label.entry);
        // Now find this index in the ordered list with a linear search
        for (ii=0; ii<rankOrder.length; ii++) {
            if (rankOrder[ii] == tmpIndex) {
                break;
            }
        }

        // CPAL if ii == -1 we have a problem
        
        return ii;
    }

    @Override public int getRank (int labelIndex) {
        return getRank(((LabelAlphabet)dictionary).lookupLabel(labelIndex));
    }

    @Override public Label getLabelAtRank (int rank) {
        if (rankOrder == null){
            setRankOrder ();
        }
        return ((LabelAlphabet)dictionary).lookupLabel (rankOrder[rank]);
    }

    @Override public double getValueAtRank (int rank) {
        if (rankOrder == null) {
            setRankOrder ();
        }
        return values[rankOrder[rank]];
    }

    @Override public LabelVector toLabelVector () {
        return this;
    }


    // Inherited from FeatureVector or SparseVector
    // public void addTo (double[] values)
    // public void addTo (double[] values, double scale)
    // public int numLocations ();
    // public double valueAtLocation (int loc)

    
}
