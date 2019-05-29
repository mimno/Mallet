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
* This task copies topic-word counts from a global array to a local 
*  thread-specific array.
* 
* @author David Mimno
*/

public class CopyCallable implements Callable<String> {
    WorkerCallable targetCallable;
    int[][] typeTopicCounts; // indexed by <feature index, topic index>
    int[] tokensPerTopic; // indexed by <topic index>

    public CopyCallable(WorkerCallable targetCallable, int[][] typeTopicCounts, int[] tokensPerTopic) {
        this.targetCallable = targetCallable;
        this.typeTopicCounts = typeTopicCounts;
        this.tokensPerTopic = tokensPerTopic;
    }

    public String call() throws Exception {
        int numTypes = typeTopicCounts.length;
        int numTopics = tokensPerTopic.length;

        int[] callableTotals = targetCallable.getTokensPerTopic();
        System.arraycopy(tokensPerTopic, 0, callableTotals, 0, numTopics);
        
        int[][] callableCounts = targetCallable.getTypeTopicCounts();
        for (int type = 0; type < numTypes; type++) {
            int[] targetCounts = callableCounts[type];
            int[] sourceCounts = typeTopicCounts[type];
            
            int index = 0;
            while (index < sourceCounts.length) {
                
                if (sourceCounts[index] != 0) {
                    targetCounts[index] = sourceCounts[index];
                }
                else if (targetCounts[index] != 0) {
                    targetCounts[index] = 0;
                }
                else {
                    break;
                }
                
                index++;
            }
            //System.arraycopy(typeTopicCounts[type], 0, counts, 0, counts.length);
        }

        return "Done";
    }
}