package cc.mallet.topics;

import java.util.Arrays;
import java.util.ArrayList;

import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.util.*;
import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import cc.mallet.classify.MaxEnt;

import java.util.concurrent.Callable;

/**
 * A parallel Dirichlet-multinomial regression topic model runnable task.
 * 
 * @author David Mimno
 */

public class DMRCallable extends WorkerCallable implements Callable<Integer> {

    MaxEnt dmrParameters;
    int numFeatures;
    int defaultFeatureIndex;
    DMRTopicModel model;

    public DMRCallable (int numTopics, DMRTopicModel model, double beta, Randoms random, ArrayList<TopicAssignment> data, int[][] typeTopicCounts, int[] tokensPerTopic, int startDoc, int numDocs) {
        this.data = data;
        
        this.model = model;

        this.dmrParameters = model.dmrParameters;
        this.numFeatures = model.numFeatures;
        this.defaultFeatureIndex = model.defaultFeatureIndex;
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
        
        this.alpha = new double[numTopics];
        this.beta = beta;
        this.betaSum = beta * numTypes;
        this.random = random;
        
        this.startDoc = startDoc;
        this.numDocs = numDocs;

        cachedCoefficients = new double[ numTopics ];
    }
        
    public Integer call () {
        
        // Initialize the smoothing-only sampling bucket
        smoothingOnlyMass = 0;
        
        // Initialize the cached coefficients, using only smoothing.
        //  These values will be selectively replaced in documents with
        //  non-zero counts in particular topics.
        
        int changed = 0;

        for (int doc = startDoc; doc < data.size() && doc < startDoc + numDocs; doc++) {
            
            Instance instance = data.get(doc).instance;
            alpha = model.alphaCache[doc];
            alphaSum = model.alphaSumCache[doc];
            smoothingOnlyMass = 0.0;

            // Use only the default features to set the topic prior (use no document features)
            for (int topic=0; topic < numTopics; topic++) {            
                smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
                cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);
            }
            
            FeatureSequence tokenSequence =
                (FeatureSequence) instance.getData();
            LabelSequence topicSequence =
                (LabelSequence) data.get(doc).topicSequence;
            
            changed += sampleTopicsForOneDoc (tokenSequence, topicSequence, true);
        }
        
        if (shouldBuildLocalCounts) {
            buildLocalTypeTopicCounts();
        }

        shouldSaveState = false;
        
        return changed;
    }
    
}