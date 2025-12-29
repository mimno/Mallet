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

public class MergeCallable implements Callable<String> {
    WorkerCallable[] callables;
    int[][] typeTopicCounts; // indexed by <feature index, topic index>
    int numTypes;
    int numTopics;
    int threadID;
    int topicMask, topicBits;

    public MergeCallable(WorkerCallable[] callables, int[][] typeTopicCounts,
                         int numTypes, int numTopics, int threadID,
                         int topicMask, int topicBits) {
        this.callables = callables;
        this.typeTopicCounts = typeTopicCounts;
        this.numTypes = numTypes;
        this.numTopics = numTopics;
        this.threadID = threadID;
        this.topicMask = topicMask;
        this.topicBits = topicBits;
    }

    public String call() throws Exception {
        int numCallables = callables.length;

        for (int type = 0; type < numTypes; type++) {
            // Only look at every n'th word.
            if (type % numCallables != threadID) { continue; }

            int[] targetCounts = typeTopicCounts[type];
            for (int thread = 0; thread < numCallables; thread++) {
                // Here the source is the individual thread counts,
                //  and the target is the global counts.

                int[] sourceCounts = callables[thread].getTypeTopicCounts()[type];
                int sourceIndex = 0;
                while (sourceIndex < sourceCounts.length &&
                       sourceCounts[sourceIndex] > 0) {
                    
                    int topic = sourceCounts[sourceIndex] & topicMask;
                    int count = sourceCounts[sourceIndex] >> topicBits;

                    int targetIndex = 0;
                    int currentTopic = targetCounts[targetIndex] & topicMask;
                    int currentCount;
                    
                    while (targetCounts[targetIndex] > 0 && currentTopic != topic) {
                        targetIndex++;
                        if (targetIndex == targetCounts.length) {
                            System.out.println("overflow in merging on type " + type + " for topic " + topic);
                            StringBuilder out = new StringBuilder();
                            for (int value: targetCounts) {
                                out.append(value + " ");
                            }
                            System.out.println(out.toString());
                        }
                        currentTopic = targetCounts[targetIndex] & topicMask;
                    }
                    currentCount = targetCounts[targetIndex] >> topicBits;
                    
                    targetCounts[targetIndex] =
                        ((currentCount + count) << topicBits) + topic;
                    
                    // Now ensure that the array is still sorted by 
                    //  bubbling this value up.
                    while (targetIndex > 0 &&
                           targetCounts[targetIndex] > targetCounts[targetIndex - 1]) {
                        int temp = targetCounts[targetIndex];
                        targetCounts[targetIndex] = targetCounts[targetIndex - 1];
                        targetCounts[targetIndex - 1] = temp;
                        
                        targetIndex--;
                    }
                    
                    sourceIndex++;
                }
                
            }
        }
        return "Done";
    }
}