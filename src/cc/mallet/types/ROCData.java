/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package cc.mallet.types;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

import com.google.errorprone.annotations.Var;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.Trial;

/**
 * Tracks ROC data for instances in {@link Trial} results.
 * 
 * @see Trial
 * @see InstanceList
 * @see Classifier
 * @see Classification
 * 
 * @author Michael Bond <a href="mailto:mikejbond@gmail.com">mikejbond@gmail.com</a>
 */
public class ROCData implements AlphabetCarrying, Serializable {

    private static final long serialVersionUID  = -2060194953037720640L;
    public static final int TRUE_POSITIVE       = 0;
    public static final int FALSE_POSITIVE      = 1;
    public static final int FALSE_NEGATIVE      = 2;
    public static final int TRUE_NEGATIVE       = 3;
    
    private final LabelAlphabet labelAlphabet;

    /** Matrix of class, threshold, [tp, fp, fn, tn] */
    private final int[][][] counts;
    private final double[] thresholds;
    
    /**
     * Constructs a new object
     * 
     * @param thresholds        Array of thresholds to track counts for
     * @param labelAlphabet     Label alphabet for instances in {@link Trial}
     */
    public ROCData(double[] thresholds, LabelAlphabet labelAlphabet) {
        // ensure that thresholds are sorted
        Arrays.sort(thresholds);
        this.counts = new int[labelAlphabet.size()][thresholds.length][4];
        this.labelAlphabet = labelAlphabet;
        this.thresholds = thresholds;
    }
    
    /**
     * Adds classification results to the ROC data
     * 
     * @param trial Trial results to add to ROC data
     */
    public void add(Classification classification) {
        int correctIndex = classification.getInstance().getLabeling().getBestIndex();
        LabelVector lv = classification.getLabelVector();
        double[] values = lv.getValues();
        
        if (!Alphabet.alphabetsMatch(this, lv)) {
            throw new IllegalArgumentException ("Alphabets do not match");
        }
        
        int numLabels = this.labelAlphabet.size();
        for (int label = 0; label < numLabels; label++) {
            double labelValue = values[label];
            int[][] thresholdCounts = this.counts[label];
            @Var
            int threshold = 0;
            
            // add the trial to all the thresholds it would be positive for
            for (; threshold < this.thresholds.length && labelValue >= this.thresholds[threshold]; threshold++) {
                if (correctIndex == label) {
                    thresholdCounts[threshold][TRUE_POSITIVE]++;
                } else {
                    thresholdCounts[threshold][FALSE_POSITIVE]++;
                }
            }
            
            // add the trial to the thresholds it would be negative for
            for (; threshold < this.thresholds.length; threshold++) {
                if (correctIndex == label) {
                    thresholdCounts[threshold][FALSE_NEGATIVE]++;
                } else {
                    thresholdCounts[threshold][TRUE_NEGATIVE]++;
                }
            }
        }
    }

    /**
     * Adds trial results to the ROC data
     * 
     * @param trial Trial results to add to ROC data
     */
    public void add(Trial trial) {
        for (Classification classification : trial) {
            add(classification);
        }
    }

    /**
     * Adds existing ROC data to this ROC data
     *
     * @param rocData ROC data to add
     */
    public void add(ROCData rocData) {
        if (!Alphabet.alphabetsMatch(this, rocData)) {
            throw new IllegalArgumentException ("Alphabets do not match");
        }

        if (!Arrays.equals(this.thresholds, rocData.thresholds)) {
            throw new IllegalArgumentException ("Thresholds do not match");
        }

        int countsLength = this.counts.length;
        for (int c = 0; c < countsLength; c++) {
            int[][] thisClassCounts = this.counts[c];
            int[][] otherClassCounts = rocData.counts[c];
            int classLength = thisClassCounts.length;
            for (int t = 0; t < classLength; t++) {
                int[] thisThrCounts = thisClassCounts[t];
                int[] otherThrCounts = otherClassCounts[t];
                int thrLength = thisThrCounts.length;
                for (int s = 0; s < thrLength; s++) {
                    thisThrCounts[s] += otherThrCounts[s];
                }
            }
        }
    }

    //@Override
    public Alphabet getAlphabet() {
        return this.labelAlphabet;
    }

    //@Override
    public Alphabet[] getAlphabets() {
        return new Alphabet[] { this.labelAlphabet };
    }
    
    /**
     * Gets the raw counts for a specified label.
     * 
     * @param label     Label to get counts for
     * @see #TRUE_POSITIVE
     * @see #FALSE_POSITIVE
     * @see #FALSE_NEGATIVE
     * @see #TRUE_NEGATIVE
     * @return Array of raw counts for specified label
     */
    public int[][] getCounts(Label label) {
        return this.counts[label.getIndex()];
    }
    
    /**
     * Gets the raw counts for a specified label and threshold.
     * 
     * If data was not collected for the exact threshold specified, then results
     * for the highest threshold <= the specified threshold will be returned.
     * 
     * @param label     Label to get counts for
     * @param threshold Threshold to get counts for
     * @see #TRUE_POSITIVE
     * @see #FALSE_POSITIVE
     * @see #FALSE_NEGATIVE
     * @see #TRUE_NEGATIVE
     * @return Array of raw counts for specified label and threshold
     */
    public int[] getCounts(Label label, double threshold) {
        @Var
        int index = Arrays.binarySearch(this.thresholds, threshold);
        if (index < 0) {
            index = (-index) - 2;
        }
        return this.counts[label.getIndex()][index];
    }

    /**
     * Gets the label alphabet
     */
    public LabelAlphabet getLabelAlphabet() {
        return this.labelAlphabet;
    }
    
    /**
     * Gets the precision for a specified label and threshold.
     * 
     * If data was not collected for the exact threshold specified, then results
     * will for the highest threshold <= the specified threshold will be
     * returned.
     * 
     * @param label     Label to get precision for
     * @param threshold Threshold to get precision for
     * @return Precision for specified label and threshold
     */
    public double getPrecision(Label label, double threshold) {
        int[] counts = getCounts(label, threshold);
        return (double) counts[TRUE_POSITIVE] / (double) (counts[TRUE_POSITIVE] + counts[FALSE_POSITIVE]);
    }
    
    /**
     * Gets the precision for a specified label and score. This differs from
     * {@link ROCData.getPrecision(Label, double)} in that it is the precision
     * for only scores falling in the one score value, not for all scores
     * above the threshold.
     * 
     * If data was not collected for the exact threshold specified, then results
     * will for the highest threshold <= the specified threshold will be
     * returned.
     * 
     * @param label     Label to get precision for
     * @param threshold Threshold to get precision for
     * @return Precision for specified label and score
     */
    public double getPrecisionForScore(Label label, double score) {
        int[][] buckets = this.counts[label.getIndex()];

        @Var
        int index = Arrays.binarySearch(this.thresholds, score);
        if (index < 0) {
            index = (-index) - 2;
        }

        double tp;
        double fp;
        if (index == this.thresholds.length - 1) {
            tp = buckets[index][TRUE_POSITIVE];
            fp = buckets[index][FALSE_POSITIVE];
        } else {
            tp = buckets[index][TRUE_POSITIVE] - buckets[index + 1][TRUE_POSITIVE];
            fp = buckets[index][FALSE_POSITIVE] - buckets[index + 1][FALSE_POSITIVE];
        }

        return (double) tp / (double) (tp + fp);
    }
    
    /**
     * Gets the estimated percentage of training events that exceed the
     * threshold.
     * 
     * @param label     Label to get precision for
     * @param threshold Threshold to get precision for
     * @return Estimated percentage of events exceeding threshold
     */
    public double getPositivePercent(Label label, double threshold) {
        int[] counts = getCounts(label, threshold);
        int positive = counts[TRUE_POSITIVE] + counts[FALSE_POSITIVE];
        return ((double) positive / (double) (positive + counts[FALSE_NEGATIVE] + counts[TRUE_NEGATIVE])) * 100.0;
    }
    
    /**
     * Gets the recall rate for a specified label and threshold.
     * 
     * If data was not collected for the exact threshold specified, then results
     * will for the highest threshold <= the specified threshold will be
     * returned.
     * 
     * @param label     Label to get recall for
     * @param threshold Threshold to get recall for
     * @return Recall rate for specified label and threshold
     */
    public double getRecall(Label label, double threshold) {
        int[] counts = getCounts(label, threshold);
        return (double) counts[TRUE_POSITIVE] / (double) (counts[TRUE_POSITIVE] + counts[FALSE_NEGATIVE]);
    }

    /**
     * Gets the thresholds being tracked
     *
     * @return Array of thresholds
     */
    public double[] getThresholds() {
        return this.thresholds;
    }
    
    /**
     * Sets the raw counts for a specified label and threshold.
     * 
     * If data is not collected for the exact threshold specified, then counts
     * for the highest threshold <= the specified threshold will be set.
     * 
     * @param label     Label to get counts for
     * @param threshold Threshold to get counts for
     * @param newCounts New count values for the label and threshold
     * @see #TRUE_POSITIVE
     * @see #FALSE_POSITIVE
     * @see #FALSE_NEGATIVE
     * @see #TRUE_NEGATIVE
     */
    public void setCounts(Label label, double threshold, int[] newCounts) {
        @Var
        int index = Arrays.binarySearch(this.thresholds, threshold);
        if (index < 0) {
            index = (-index) - 2;
        }
        
        int[] oldCounts = this.counts[label.getIndex()][index];
        if (newCounts.length != oldCounts.length) {
            throw new IllegalArgumentException ("Array of counts must contain " + oldCounts.length + " elements.");
        }

        for (int i = 0; i < oldCounts.length; i++) {
            oldCounts[i] = newCounts[i];
        }
    }
    
    //@Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        NumberFormat format = new DecimalFormat("0.####");
        
        for (int i = 0; i < this.labelAlphabet.size(); i++) {
            int[][] labelData = this.counts[i];
            
            buf.append("ROC data for ");
            buf.append(this.labelAlphabet.lookupObject(i).toString());
            buf.append('\n');
            buf.append("THR\tTP\tFP\tFN\tTN\tPrecis\tRecall\n");
            
            // add one row for each threshold
            for (int t = 0; t < this.thresholds.length; t++) {
                buf.append(this.thresholds[t]);
                for (int res : labelData[t]) {
                    buf.append('\t').append(res);
                }

                double tp = labelData[t][TRUE_POSITIVE];
                @Var
                double sum = tp + labelData[t][FALSE_POSITIVE];
                @Var
                double precision = 0.0;
                if (sum != 0) {
                    precision = tp / sum;
                }
                
                sum = tp + labelData[t][FALSE_NEGATIVE];
                @Var
                double recall = 0.0;
                if (sum != 0) {
                    recall = tp / sum;
                }
                
                buf.append('\t').append(format.format(precision));
                buf.append('\t').append(format.format(recall));
                buf.append('\n');
            }
            
            buf.append('\n');
        }
        
        return buf.toString();
    }
    
}
