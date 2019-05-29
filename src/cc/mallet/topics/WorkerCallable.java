/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.    For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;

/**
 * A parallel topic model callable task. This class replaces the WorkerRunnable class.
 * 
 * @author David Mimno, Andrew McCallum
 */

public class WorkerCallable implements Callable<Integer> {

    ArrayList<TopicAssignment> data;
    int startDoc, numDocs;

    protected int numTopics; // Number of topics to be fit

    // These values are used to encode type/topic counts as
    //  count/topic pairs in a single int.
    protected int topicMask;
    protected int topicBits;

    protected int numTypes;

    protected double[] alpha;     // Dirichlet(alpha,alpha,...) is the distribution over topics
    protected double alphaSum;
    protected double beta;   // Prior on per-topic multinomial distribution over words
    protected double betaSum;
    public static final double DEFAULT_BETA = 0.01;
    
    protected double smoothingOnlyMass = 0.0;
    protected double[] cachedCoefficients;

    protected int[][] typeTopicCounts; // indexed by <feature index, topic index>
    protected int[] tokensPerTopic; // indexed by <topic index>

    // for dirichlet estimation
    protected int[] docLengthCounts; // histogram of document sizes
    protected int[][] topicDocCounts; // histogram of document/topic counts, indexed by <topic index, sequence position index>

    boolean shouldSaveState = false;
    boolean shouldBuildLocalCounts = true;
    
    protected Randoms random;
    
    public WorkerCallable() {}
    
    public WorkerCallable (int numTopics,
                           double[] alpha, double alphaSum,
                           double beta, Randoms random,
                           ArrayList<TopicAssignment> data,
                           int[][] typeTopicCounts, 
                           int[] tokensPerTopic,
                           int startDoc, int numDocs) {

        this.data = data;

        this.numTopics = numTopics;
        this.numTypes = typeTopicCounts.length;

        if (Integer.bitCount(numTopics) == 1) {
            // exact power of 2
            topicMask = numTopics - 1;
            topicBits = Integer.bitCount(topicMask);
        }
        else {
            // otherwise add an extra bit
            topicMask = Integer.highestOneBit(numTopics) * 2 - 1;
            topicBits = Integer.bitCount(topicMask);
        }

        this.typeTopicCounts = typeTopicCounts;
        this.tokensPerTopic = tokensPerTopic;
        
        this.alphaSum = alphaSum;
        this.alpha = alpha;
        this.beta = beta;
        this.betaSum = beta * numTypes;
        this.random = random;
        
        this.startDoc = startDoc;
        this.numDocs = numDocs;

        cachedCoefficients = new double[ numTopics ];

        //System.err.println("WorkerCallable Thread: " + numTopics + " topics, " + topicBits + " topic bits, " + 
        //                   Integer.toBinaryString(topicMask) + " topic mask");

    }

    /**
     *  If there is only one thread, we don't need to go through 
     *   communication overhead. This method asks this worker not
     *   to prepare local type-topic counts. The method should be
     *   called when we are using this code in a non-threaded environment.
     */
    public void makeOnlyThread() {
        shouldBuildLocalCounts = false;
    }

    public int[] getTokensPerTopic() { return tokensPerTopic; }
    public int[][] getTypeTopicCounts() { return typeTopicCounts; }

    public int[] getDocLengthCounts() { return docLengthCounts; }
    public int[][] getTopicDocCounts() { return topicDocCounts; }

    public void initializeAlphaStatistics(int size) {
        docLengthCounts = new int[size];
        topicDocCounts = new int[numTopics][size];
    }
    
    public void collectAlphaStatistics() {
        shouldSaveState = true;
    }

    public void resetBeta(double beta, double betaSum) {
        this.beta = beta;
        this.betaSum = betaSum;
    }

    /**
     *  Once we have sampled the local counts, trash the 
     *   "global" type topic counts and reuse the space to 
     *   build a summary of the type topic counts specific to 
     *   this worker's section of the corpus.
     */
    public void buildLocalTypeTopicCounts () {

        // Clear the topic totals
        Arrays.fill(tokensPerTopic, 0);

        // Clear the type/topic counts

        for (int type = 0; type < typeTopicCounts.length; type++) {
            int[] topicCounts = typeTopicCounts[type];
            Arrays.fill(topicCounts, 0);
        }

        for (int doc = startDoc; doc < data.size() && doc < startDoc + numDocs; doc++) {

            TopicAssignment document = data.get(doc);

            FeatureSequence tokens = (FeatureSequence) document.instance.getData();
            FeatureSequence topicSequence =  (FeatureSequence) document.topicSequence;

            int[] topics = topicSequence.getFeatures();
            for (int position = 0; position < tokens.size(); position++) {

                int topic = topics[position];

                if (topic == ParallelTopicModel.UNASSIGNED_TOPIC) { continue; }

                tokensPerTopic[topic]++;
                
                // The format for these arrays is 
                //  the topic in the rightmost bits
                //  the count in the remaining (left) bits.
                // Since the count is in the high bits, sorting (desc)
                //  by the numeric value of the int guarantees that
                //  higher counts will be before the lower counts.
                
                int type = tokens.getIndexAtPosition(position);

                int[] currentTypeTopicCounts = typeTopicCounts[ type ];
                
                // Start by assuming that the array is either empty
                //  or is in sorted (descending) order.
                
                // Here we are only adding counts, so if we find 
                //  an existing location with the topic, we only need
                //  to ensure that it is not larger than its left neighbor.
                
                int index = 0;
                int currentTopic = currentTypeTopicCounts[index] & topicMask;
                int currentValue;
                
                while (currentTypeTopicCounts[index] > 0 && currentTopic != topic) {
                    index++;

                    // There's a hard-to-reproduce bug for multithreaded processes.
                    // This code is designed to produce some diagnostic information.
                    if (index == currentTypeTopicCounts.length) {
                        System.out.println("overflow on type " + type + " for topic " + topic);
                        StringBuilder out = new StringBuilder();
                        for (int value: currentTypeTopicCounts) {
                            out.append(value + " ");
                        }
                        System.out.println(out);
                    }
                    currentTopic = currentTypeTopicCounts[index] & topicMask;
                }
                currentValue = currentTypeTopicCounts[index] >> topicBits;
                
                if (currentValue == 0) {
                    // new value is 1, so we don't have to worry about sorting
                    //  (except by topic suffix, which doesn't matter)
                    
                    currentTypeTopicCounts[index] = (1 << topicBits) + topic;
                }
                else {
                    currentTypeTopicCounts[index] =
                        ((currentValue + 1) << topicBits) + topic;
                    
                    // Now ensure that the array is still sorted by 
                    //  bubbling this value up.
                    while (index > 0 && currentTypeTopicCounts[index] > currentTypeTopicCounts[index - 1]) {
                        int temp = currentTypeTopicCounts[index];
                        currentTypeTopicCounts[index] = currentTypeTopicCounts[index - 1];
                        currentTypeTopicCounts[index - 1] = temp;
                        
                        index--;
                    }
                }
            }
        }
    }

    public Integer call () throws Exception {

        // Initialize the smoothing-only sampling bucket
        smoothingOnlyMass = 0;
        
        // Initialize the cached coefficients, using only smoothing.
        //  These values will be selectively replaced in documents with
        //  non-zero counts in particular topics.
        
        for (int topic=0; topic < numTopics; topic++) {
            smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
            cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);
        }
        
        int changed = 0;

        for (int doc = startDoc; doc < data.size() && doc < startDoc + numDocs; doc++) {
            
            /*
                if (doc % 10000 == 0) {
                System.out.println("processing doc " + doc);
                }
            */
            
            FeatureSequence tokenSequence =
                (FeatureSequence) data.get(doc).instance.getData();
            LabelSequence topicSequence =
                (LabelSequence) data.get(doc).topicSequence;
            
            changed += sampleTopicsForOneDoc (tokenSequence, topicSequence, true);
        }
        
        if (shouldBuildLocalCounts) {
            buildLocalTypeTopicCounts();
        }
        
        return changed;

    }
    
    protected int sampleTopicsForOneDoc (FeatureSequence tokenSequence,
                                          FeatureSequence topicSequence,
                                          boolean readjustTopicsAndStats /* currently ignored */) {

        int[] oneDocTopics = topicSequence.getFeatures();

        int[] currentTypeTopicCounts;
        int type, oldTopic, newTopic;
        double topicWeightsSum;
        int docLength = tokenSequence.getLength();

        int[] localTopicCounts = new int[numTopics];
        int[] localTopicIndex = new int[numTopics];

        //        populate topic counts
        for (int position = 0; position < docLength; position++) {
            if (oneDocTopics[position] == ParallelTopicModel.UNASSIGNED_TOPIC) { continue; }
            localTopicCounts[oneDocTopics[position]]++;
        }

        // Build an array that densely lists the topics that
        //  have non-zero counts.
        int denseIndex = 0;
        for (int topic = 0; topic < numTopics; topic++) {
            if (localTopicCounts[topic] != 0) {
                localTopicIndex[denseIndex] = topic;
                denseIndex++;
            }
        }

        // Record the total number of non-zero topics
        int nonZeroTopics = denseIndex;

        //        Initialize the topic count/beta sampling bucket
        double topicBetaMass = 0.0;

        // Initialize cached coefficients and the topic/beta 
        //  normalizing constant.

        for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
            int topic = localTopicIndex[denseIndex];
            int n = localTopicCounts[topic];

            //    initialize the normalization constant for the (B * n_{t|d}) term
            topicBetaMass += beta * n /    (tokensPerTopic[topic] + betaSum);    

            //    update the coefficients for the non-zero topics
            cachedCoefficients[topic] =    (alpha[topic] + n) / (tokensPerTopic[topic] + betaSum);
        }

        double topicTermMass = 0.0;

        double[] topicTermScores = new double[numTopics];
        int[] topicTermIndices;
        int[] topicTermValues;
        int i;
        double score;

        int changed = 0;

        //    Iterate over the positions (words) in the document 
        for (int position = 0; position < docLength; position++) {
            type = tokenSequence.getIndexAtPosition(position);
            oldTopic = oneDocTopics[position];

            currentTypeTopicCounts = typeTopicCounts[type];

            if (oldTopic != ParallelTopicModel.UNASSIGNED_TOPIC) {
                //    Remove this token from all counts. 
                
                // Remove this topic's contribution to the 
                //  normalizing constants
                smoothingOnlyMass -= alpha[oldTopic] * beta / 
                    (tokensPerTopic[oldTopic] + betaSum);
                topicBetaMass -= beta * localTopicCounts[oldTopic] /
                    (tokensPerTopic[oldTopic] + betaSum);
                
                // Decrement the local doc/topic counts
                
                localTopicCounts[oldTopic]--;
                
                // Maintain the dense index, if we are deleting
                //  the old topic
                if (localTopicCounts[oldTopic] == 0) {
                    
                    // First get to the dense location associated with
                    //  the old topic.
                    
                    denseIndex = 0;
                    
                    // We know it's in there somewhere, so we don't 
                    //  need bounds checking.
                    while (localTopicIndex[denseIndex] != oldTopic) {
                        denseIndex++;
                    }
                
                    // shift all remaining dense indices to the left.
                    while (denseIndex < nonZeroTopics) {
                        if (denseIndex < localTopicIndex.length - 1) {
                            localTopicIndex[denseIndex] = 
                                localTopicIndex[denseIndex + 1];
                        }
                        denseIndex++;
                    }
                    
                    nonZeroTopics --;
                }

                // Decrement the global topic count totals
                tokensPerTopic[oldTopic]--;
                assert(tokensPerTopic[oldTopic] >= 0) : "old Topic " + oldTopic + " below 0";
            

                // Add the old topic's contribution back into the
                //  normalizing constants.
                smoothingOnlyMass += alpha[oldTopic] * beta / 
                    (tokensPerTopic[oldTopic] + betaSum);
                topicBetaMass += beta * localTopicCounts[oldTopic] /
                    (tokensPerTopic[oldTopic] + betaSum);

                // Reset the cached coefficient for this topic
                cachedCoefficients[oldTopic] = 
                    (alpha[oldTopic] + localTopicCounts[oldTopic]) /
                    (tokensPerTopic[oldTopic] + betaSum);
            }


            // Now go over the type/topic counts, decrementing
            //  where appropriate, and calculating the score
            //  for each topic at the same time.

            int index = 0;
            int currentTopic, currentValue;

            boolean alreadyDecremented = (oldTopic == ParallelTopicModel.UNASSIGNED_TOPIC);

            topicTermMass = 0.0;

            while (index < currentTypeTopicCounts.length && 
                   currentTypeTopicCounts[index] > 0) {
                currentTopic = currentTypeTopicCounts[index] & topicMask;
                currentValue = currentTypeTopicCounts[index] >> topicBits;

                if (! alreadyDecremented && 
                    currentTopic == oldTopic) {

                    // We're decrementing and adding up the 
                    //  sampling weights at the same time, but
                    //  decrementing may require us to reorder
                    //  the topics, so after we're done here,
                    //  look at this cell in the array again.

                    currentValue --;
                    if (currentValue == 0) {
                        currentTypeTopicCounts[index] = 0;
                    }
                    else {
                        currentTypeTopicCounts[index] =
                            (currentValue << topicBits) + oldTopic;
                    }
                    
                    // Shift the reduced value to the right, if necessary.

                    int subIndex = index;
                    while (subIndex < currentTypeTopicCounts.length - 1 && 
                           currentTypeTopicCounts[subIndex] < currentTypeTopicCounts[subIndex + 1]) {
                        int temp = currentTypeTopicCounts[subIndex];
                        currentTypeTopicCounts[subIndex] = currentTypeTopicCounts[subIndex + 1];
                        currentTypeTopicCounts[subIndex + 1] = temp;
                        
                        subIndex++;
                    }

                    alreadyDecremented = true;
                }
                else {
                    score = 
                        cachedCoefficients[currentTopic] * currentValue;
                    topicTermMass += score;
                    topicTermScores[index] = score;

                    index++;
                }
            }
            
            double sample = random.nextUniform() * (smoothingOnlyMass + topicBetaMass + topicTermMass);
            double origSample = sample;

            //    Make sure it actually gets set
            newTopic = -1;

            if (sample < topicTermMass) {
                //topicTermCount++;

                i = -1;
                while (sample > 0) {
                    i++;
                    sample -= topicTermScores[i];
                }

                newTopic = currentTypeTopicCounts[i] & topicMask;
                currentValue = currentTypeTopicCounts[i] >> topicBits;
                
                currentTypeTopicCounts[i] = ((currentValue + 1) << topicBits) + newTopic;

                // Bubble the new value up, if necessary
                
                while (i > 0 &&
                       currentTypeTopicCounts[i] > currentTypeTopicCounts[i - 1]) {
                    int temp = currentTypeTopicCounts[i];
                    currentTypeTopicCounts[i] = currentTypeTopicCounts[i - 1];
                    currentTypeTopicCounts[i - 1] = temp;

                    i--;
                }

            }
            else {
                sample -= topicTermMass;

                if (sample < topicBetaMass) {
                    //betaTopicCount++;

                    sample /= beta;

                    for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
                        int topic = localTopicIndex[denseIndex];

                        sample -= localTopicCounts[topic] /
                            (tokensPerTopic[topic] + betaSum);

                        if (sample <= 0.0) {
                            newTopic = topic;
                            break;
                        }
                    }

                }
                else {
                    //smoothingOnlyCount++;

                    sample -= topicBetaMass;

                    sample /= beta;

                    newTopic = 0;
                    sample -= alpha[newTopic] /
                        (tokensPerTopic[newTopic] + betaSum);

                    while (sample > 0.0) {
                        newTopic++;
                        sample -= alpha[newTopic] / 
                            (tokensPerTopic[newTopic] + betaSum);
                    }
                    
                }

                // Move to the position for the new topic,
                //  which may be the first empty position if this
                //  is a new topic for this word.
                
                index = 0;
                while (currentTypeTopicCounts[index] > 0 &&
                       (currentTypeTopicCounts[index] & topicMask) != newTopic) {
                    index++;
                    if (index == currentTypeTopicCounts.length) {
                        System.err.println("type: " + type + " new topic: " + newTopic);
                        for (int k=0; k<currentTypeTopicCounts.length; k++) {
                            System.err.print((currentTypeTopicCounts[k] & topicMask) + ":" + 
                                             (currentTypeTopicCounts[k] >> topicBits) + " ");
                        }
                        System.err.println();

                    }
                }


                // index should now be set to the position of the new topic,
                //  which may be an empty cell at the end of the list.

                if (currentTypeTopicCounts[index] == 0) {
                    // inserting a new topic, guaranteed to be in
                    //  order w.r.t. count, if not topic.
                    currentTypeTopicCounts[index] = (1 << topicBits) + newTopic;
                }
                else {
                    currentValue = currentTypeTopicCounts[index] >> topicBits;
                    currentTypeTopicCounts[index] = ((currentValue + 1) << topicBits) + newTopic;

                    // Bubble the increased value left, if necessary
                    while (index > 0 &&
                           currentTypeTopicCounts[index] > currentTypeTopicCounts[index - 1]) {
                        int temp = currentTypeTopicCounts[index];
                        currentTypeTopicCounts[index] = currentTypeTopicCounts[index - 1];
                        currentTypeTopicCounts[index - 1] = temp;

                        index--;
                    }
                }

            }

            if (newTopic == -1) {
                System.err.println("WorkerCallable sampling error: "+ origSample + " " + sample + " " + smoothingOnlyMass + " " + 
                        topicBetaMass + " " + topicTermMass);
                newTopic = numTopics-1; // TODO is this appropriate
                //throw new IllegalStateException ("WorkerCallable: New topic not sampled.");
            }
            //assert(newTopic != -1);

            //            Put that new topic into the counts
            oneDocTopics[position] = newTopic;

            smoothingOnlyMass -= alpha[newTopic] * beta / 
                (tokensPerTopic[newTopic] + betaSum);
            topicBetaMass -= beta * localTopicCounts[newTopic] /
                (tokensPerTopic[newTopic] + betaSum);

            localTopicCounts[newTopic]++;

            // If this is a new topic for this document,
            //  add the topic to the dense index.
            if (localTopicCounts[newTopic] == 1) {
                
                // First find the point where we 
                //  should insert the new topic by going to
                //  the end (which is the only reason we're keeping
                //  track of the number of non-zero
                //  topics) and working backwards

                denseIndex = nonZeroTopics;

                while (denseIndex > 0 &&
                       localTopicIndex[denseIndex - 1] > newTopic) {

                    localTopicIndex[denseIndex] =
                        localTopicIndex[denseIndex - 1];
                    denseIndex--;
                }
                
                localTopicIndex[denseIndex] = newTopic;
                nonZeroTopics++;
            }

            tokensPerTopic[newTopic]++;

            //    update the coefficients for the non-zero topics
            cachedCoefficients[newTopic] =
                (alpha[newTopic] + localTopicCounts[newTopic]) /
                (tokensPerTopic[newTopic] + betaSum);

            smoothingOnlyMass += alpha[newTopic] * beta / 
                (tokensPerTopic[newTopic] + betaSum);
            topicBetaMass += beta * localTopicCounts[newTopic] /
                (tokensPerTopic[newTopic] + betaSum);

            if (newTopic != oldTopic) {
                changed ++;
            }

        }

        if (shouldSaveState) {
            // Update the document-topic count histogram,
            //  for dirichlet estimation
            docLengthCounts[ docLength ]++;

            for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
                int topic = localTopicIndex[denseIndex];
                
                topicDocCounts[topic][ localTopicCounts[topic] ]++;
            }
        }

        //    Clean up our mess: reset the coefficients to values with only
        //    smoothing. The next doc will update its own non-zero topics...

        for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
            int topic = localTopicIndex[denseIndex];

            cachedCoefficients[topic] =
                alpha[topic] / (tokensPerTopic[topic] + betaSum);
        }

        return changed;
    }

}
