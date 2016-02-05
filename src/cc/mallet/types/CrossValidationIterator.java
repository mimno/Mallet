/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */




package cc.mallet.types;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import cc.mallet.types.InstanceList;

/**
 * An iterator which splits an {@link InstanceList} into n-folds and iterates
 * over the folds for use in n-fold cross-validation. For each iteration,
 * list[0] contains a {@link InstanceList} with n-1 folds typically used for
 * training and list[1] contains an {@link InstanceList} with 1 fold typically
 * used for validation.
 * 
 * This class uses {@link MultiInstanceList} to avoid creating a new
 * {@link InstanceList} each iteration.
 * 
 * TODO - currently the distribution is completely random, an improvement would
 * be to provide a stratified random distribution.
 * 
 * @see MultiInstanceList
 * @see InstanceList
 * 
 * @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */
public class CrossValidationIterator
implements java.util.Iterator<InstanceList[]>, Serializable {
    private static final long serialVersionUID = 234516468015114991L;
    private final int nfolds;
    private final InstanceList[] folds;
    private int index;

    /**
     * Constructs a new n-fold cross-validation iterator
     * 
     * @param ilist instance list to split into folds and iterate over
     * @param nfolds number of folds to split InstanceList into
     * @param r The source of randomness to use in shuffling.
     */
    public CrossValidationIterator (InstanceList ilist, int nfolds, java.util.Random r) {                       
        this.nfolds = nfolds;
        assert (nfolds > 0) : "nfolds: " + this.nfolds;
        this.index = 0;
        double fraction = (double) 1 / nfolds;
        double[] proportions = new double[nfolds];
        for (int i=0; i < nfolds; i++) { 
            proportions[i] = fraction;
        }
        this.folds = ilist.split (r, proportions);
    }

    /**
     * Constructs a new n-fold cross-validation iterator
     * 
     * @param ilist instance list to split into folds and iterate over
     * @param _nfolds number of folds to split InstanceList into
     */
    public CrossValidationIterator (InstanceList ilist, int _nfolds) {
        this (ilist, _nfolds, new java.util.Random (System.currentTimeMillis ()));
    }

    /**
     * Calls clear on each fold. It is recommended that this be always be called
     * when the iterator is no longer needed so that implementations of
     * InstanceList such as PagedInstanceList can clean up any temporary data
     * they may have outside the JVM.
     */
    public void clear () {
        for (InstanceList list : this.folds) {
            list.clear();
        }
    }
    
    
    public boolean hasNext () {
        return this.index < this.nfolds;
    }

    /**
     * Returns the next training/testing split.
     * 
     * @return A two element array of {@link InstanceList}, where
     *         <code>InstanceList[0]</code> contains n-1 folds for training and
     *         <code>InstanceList[1]</code> contains 1 fold for testing.
     */
    public InstanceList[] nextSplit () {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        InstanceList[] ret = new InstanceList[2];
        
        if (this.folds.length == 1) {
            ret[0] = this.folds[0];
            ret[1] = this.folds[0];
        } else {
            InstanceList[] training = new InstanceList[this.folds.length - 1];
            int j = 0;
            for (int i = 0; i < this.folds.length; i++) {
                if (i == this.index) {
                    continue;
                }
                training[j++] = this.folds[i];
            }
            ret[0] = new MultiInstanceList (training);
            ret[1] = this.folds[this.index];
        }
        
        this.index++;
        return ret;
    }

    /**
     * Returns the next training/testing split.
     * 
     * @return A two element array of {@link InstanceList}, where
     *         <code>InstanceList[0]</code> contains <code>numTrainingFolds</code>
     *         folds for training and <code>InstanceList[1]</code> contains
     *         n - <code>numTrainingFolds</code> folds for testing.
     */
    public InstanceList[] nextSplit (int numTrainFolds) {
        if (!hasNext()) {
            throw new NoSuchElementException ();
        }

        List<InstanceList> trainingSet = new ArrayList<InstanceList> ();
        List<InstanceList> testSet = new ArrayList<InstanceList> ();

        // train on folds [index, index+numTrainFolds), test on rest
        for (int i = 0; i < this.folds.length; i++) {
            int foldno = (this.index + i) % this.folds.length;
            if (i < numTrainFolds) {
                trainingSet.add (this.folds[foldno]);
            } else {
                testSet.add (this.folds[foldno]);
            }
        }

        InstanceList[] ret = new InstanceList[2];
        ret[0] = new MultiInstanceList (trainingSet);
        ret[1] = new MultiInstanceList (testSet);

        this.index++;
        return ret;
    }

    /**
     * Returns the next training/testing split.
     * 
     * @see java.util.Iterator#next()
     * @return A two element array of {@link InstanceList}, where
     *         <code>InstanceList[0]</code> contains n-1 folds for training and
     *         <code>InstanceList[1]</code> contains 1 fold for testing.
     */
    public InstanceList[] next () {
        return nextSplit();
    }
    
    public void remove () {
        throw new UnsupportedOperationException ();
    }
    
}
