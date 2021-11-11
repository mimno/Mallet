package cc.mallet.transform;

import java.util.logging.*;
import java.util.ArrayList;
import java.util.Random;
import java.io.*;

import cc.mallet.types.*;
import cc.mallet.util.*;

/** 
 * This class implements the method from "Authorless Topic Models"
 * by Thompson and Mimno, COLING 2018.
 * 
 * The goal is to reduce the frequency of words that are 
 * unusually associated with a particular label. This is useful
 * as a pre-processing step for topic modeling becuase it reduces
 * the correlation of topics to known class labels. The problem
 * comes up most often in fiction, where topics tend to simply
 * reproduce lists of characters.
 * 
 * The input is a labeled feature sequence, of the sort used
 * for topic modeling. Unlike the regular topic modeling system,
 * labels are required, since we need something to correlate.
 * 
 * The output is another feature sequence with word tokens removed.
 * Note that some words may disappear from the corpus, but they will
 * still be present in the alphabet.
 * 
 * The code takes one parameter, equivalent to a p-value where the 
 * null hypothesis is that a word occurs no more frequently in one 
 * category than in the collection as a whole.
 * 
 * @author David Mimno
 */

public class DownsampleLabelWords {
    private static Logger logger = MalletLogger.getLogger(DownsampleLabelWords.class.getName());

    static CommandOption.File inputFile = new CommandOption.File(DownsampleLabelWords.class, "input", "FILE", true, null,
         "Read the instance list from this file. This should be a Mallet instance list preserving feature sequence and a class label.", null);
         
    static CommandOption.File outputFile = new CommandOption.File(DownsampleLabelWords.class, "output", "FILE", true, null,
         "Write pruned instance list to this file.", null);
         
    static CommandOption.File reportFile = new CommandOption.File(DownsampleLabelWords.class, "report-file", "FILE", true, new File("removed_words.tsv"),
         "Write a tab-delimited report on words that were removed to this file", null);
    
    static CommandOption.Integer verboseInstances = new CommandOption.Integer(DownsampleLabelWords.class, "show", "INTEGER", false, 0, "Display the first [this number] instances, showing any deletions. This option is intended to help you feel confident that you know what this process is doing.", null); 

    static CommandOption.Double samplingThreshold = new CommandOption.Double(DownsampleLabelWords.class, "threshold", "NUMBER", true, 0.05, "Threshold value for deciding whether a word is over-represented. Lower values will remove fewer tokens, higher values will remove more. The default should be a good choice for most applications.", null);

    static CommandOption.Integer randomSeed = new CommandOption.Integer(DownsampleLabelWords.class, "random-seed", "INTEGER", true, 0, "The random seed for randomly selecting subset of input words. Use this option if you need to repeat a process exactly.", null);

    public static void main (String[] args) throws FileNotFoundException, IOException {

        CommandOption.setSummary(DownsampleLabelWords.class, "A tool for removing words that are strongly associated with a particular document label.");
        CommandOption.process(DownsampleLabelWords.class, args);

        // If users don't supply any command line arguments, the 
        //  default error message comes from not finding the input
        //  file. In this case, they probably really just want to 
        //  know what the options are.
		if (args.length == 0) {
			CommandOption.getList(DownsampleLabelWords.class).printUsage(false);
			System.exit (-1);
        }
        
        Random random = randomSeed.wasInvoked() ? new Random (randomSeed.value) : new Random ();

		// Read the InstanceList
        InstanceList instances = InstanceList.load (inputFile.value);
        
        Alphabet alphabet = instances.getDataAlphabet();
        LabelAlphabet labelAlphabet = (LabelAlphabet) instances.getTargetAlphabet();

        int numWords = alphabet.size();
        int numLabels = labelAlphabet.size();

        System.out.format("%d words, %d labels\n", numWords, numLabels);

        int[][] wordLabelCounts = new int[numWords][numLabels];
        int[] labelCounts = new int[numLabels];

        double[][] labelWordProbs = new double[numLabels][numWords];

        for (Instance instance: instances) {
            Label labelObject = (Label) instance.getTarget();
            int label = labelObject.getIndex();
            FeatureSequence tokens = (FeatureSequence) instance.getData();

            labelCounts[ label ] += tokens.size();
            for (int position = 0; position < tokens.size(); position++) {
                int type = tokens.getIndexAtPosition(position);
                wordLabelCounts[type][label] += 1;
            }
        }

        for (int word = 0; word < numWords; word++) {

            double[] proportions = new double[numLabels];
            double sum = 0;
            double sumSquares = 0;
            double nonZeros = 0;

            for (int label = 0; label < numLabels; label++) {
                if (wordLabelCounts[word][label] > 0) {
                    proportions[label] = ((double) wordLabelCounts[word][label]) / labelCounts[label];
                    nonZeros++;
                    sum += proportions[label];
                    sumSquares += proportions[label] * proportions[label];
                }
            }

            // Calculate the mean and variance from sum and sum of squares
            double mean = sum / numLabels;
            double variance = sumSquares - 2 * sum * mean + numLabels * mean * mean;
            variance += (numLabels - nonZeros) * mean * mean;
            variance /= numLabels;

            double shape = mean * mean / variance;
            double scale = variance / mean;
            double threshold = StatFunctions.gammaInverseCDF(1.0 - samplingThreshold.value, shape, scale);

            //System.out.format("%s %f %f %.0f shape %f scale %f %f\n", alphabet.lookupObject(word), mean, variance, nonZeros, shape, scale, threshold);

            // if a word's frequency exceeds the proportion beyond which 
            // we reject the hypothesis of equal use, calculate the 
            // ratio we need to downsample to reduce it to that level.
            // otherwise keep it 100% of the time.
            for (int label = 0; label < numLabels; label++) {
                if (proportions[label] > threshold) {
                    labelWordProbs[label][word] = threshold / proportions[label];
                    //System.out.format("downsampling %s for %s %f (p: %f mean: %f sd: %f)\n", alphabet.lookupObject(word), labelAlphabet.lookupObject(label), labelWordProbs[label][word], proportions[label], mean, Math.sqrt(variance));
                }
                else {
                    labelWordProbs[label][word] = 1.0;
                }
            }
        }

        // Now create a new instance list with words randomly downsampled
        // according to the ratios we determined.

        InstanceList downsampledInstances = new InstanceList(instances.getPipe());

        int instanceCounter = 0;
        StringBuilder instanceDisplay = null;

        // Count how many tokens we dropped
        int inputTokens = 0;
        int outputTokens = 0;

        int[][] wordLabelRemovalCounts = new int[numWords][numLabels];

        for (Instance instance: instances) {
            Label labelObject = (Label) instance.getTarget();
            int label = labelObject.getIndex();
            FeatureSequence tokens = (FeatureSequence) instance.getData();

            if (instanceCounter < verboseInstances.value) {
                instanceDisplay = new StringBuilder();
            }

            int[] sampledWords = new int[tokens.size()];
            int actualLength = 0;
            for (int position = 0; position < tokens.size(); position++) {
                int type = tokens.getIndexAtPosition(position);
                double prob = labelWordProbs[label][type];

                inputTokens++;
                if (random.nextDouble() < prob) {
                    sampledWords[actualLength] = type;
                    actualLength++;
                    outputTokens++;

                    if (instanceDisplay != null) {
                        instanceDisplay.append(alphabet.lookupObject(type) + " ");
                    }
                }
                else {
                    wordLabelRemovalCounts[type][label] += 1;

                    if (instanceDisplay != null) {
                        instanceDisplay.append("[" + alphabet.lookupObject(type) + "] ");
                    }
                }
            }

            if (instanceDisplay != null) {
                logger.info(instanceDisplay.toString());
                instanceDisplay = null;
            }

            FeatureSequence downsampledFS = new FeatureSequence(alphabet, sampledWords, actualLength);

            downsampledInstances.add(new Instance(downsampledFS, instance.getTarget(), instance.getName(), instance.getSource()));

            instanceCounter++;
        }

        if (reportFile.value != null) {
            PrintWriter reportWriter = new PrintWriter(reportFile.value);
            reportWriter.println("Word\tLabel\tCount");

            for (int word = 0; word < numWords; word++) {
                for (int label = 0; label < numLabels; label++) {
                    if (wordLabelRemovalCounts[word][label] > 0) {
                        reportWriter.format("%s\t%s\t%d\n", alphabet.lookupObject(word), labelAlphabet.lookupObject(label), wordLabelRemovalCounts[word][label]);
                    }
                }
            }
        }

        logger.info("reduced " + inputTokens + " to " + outputTokens + " tokens");
        downsampledInstances.save(outputFile.value);
    }
}