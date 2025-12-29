/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.    For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics;

import java.util.*;
import java.util.zip.*;

import java.io.*;
import java.text.NumberFormat;

import cc.mallet.types.*;
import cc.mallet.util.CommandOption;
import cc.mallet.util.Randoms;

/**
 * Latent Dirichlet Allocation for loosely parallel corpora in arbitrary languages
 * 
 * @author David Mimno, Andrew McCallum
 */

public class PolylingualTopicModel implements Serializable {

    static CommandOption.SpacedStrings languageInputFiles = new CommandOption.SpacedStrings(PolylingualTopicModel.class, "language-inputs", "FILENAME [FILENAME ...]", true, null,
          "Filenames for polylingual topic model. Each language should have its own file, " +
          "with the same number of instances in each file. If a document is missing in " + 
         "one language, there should be an empty instance.", null);

    static CommandOption.String outputModelFilename = new CommandOption.String(PolylingualTopicModel.class, "output-model", "FILENAME", true, null,
          "The filename in which to write the binary topic model at the end of the iterations.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String inputModelFilename = new CommandOption.String(PolylingualTopicModel.class, "input-model", "FILENAME", true, null,
          "The filename from which to read the binary topic model to which the --input will be appended, " +
          "allowing incremental training.  " +
         "By default this is null, indicating that no file will be read.", null);

    static CommandOption.String inferencerFilename = new CommandOption.String(PolylingualTopicModel.class, "inferencer-filename", "FILENAME", true, null,
          "A topic inferencer applies a previously trained topic model to new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String evaluatorFilename = new CommandOption.String(PolylingualTopicModel.class, "evaluator-filename", "FILENAME", true, null,
          "A held-out likelihood evaluator for new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String stateFile = new CommandOption.String(PolylingualTopicModel.class, "output-state", "FILENAME", true, null,
          "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String topicKeysFile = new CommandOption.String(PolylingualTopicModel.class, "output-topic-keys", "FILENAME", true, null,
          "The filename in which to write the top words for each topic and any Dirichlet parameters.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String docTopicsFile = new CommandOption.String(PolylingualTopicModel.class, "output-doc-topics", "FILENAME", true, null,
          "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.Double docTopicsThreshold = new CommandOption.Double(PolylingualTopicModel.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
          "When writing topic proportions per document with --output-doc-topics, " +
         "do not print topics with proportions less than this threshold value.", null);

    static CommandOption.Integer docTopicsMax = new CommandOption.Integer(PolylingualTopicModel.class, "doc-topics-max", "INTEGER", true, -1,
          "When writing topic proportions per document with --output-doc-topics, " +
          "do not print more than INTEGER number of topics.  "+
         "A negative value indicates that all topics should be printed.", null);

    static CommandOption.Integer outputModelIntervalOption = new CommandOption.Integer(PolylingualTopicModel.class, "output-model-interval", "INTEGER", true, 0,
          "The number of iterations between writing the model (and its Gibbs sampling state) to a binary file.  " +
         "You must also set the --output-model to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Integer outputStateIntervalOption = new CommandOption.Integer(PolylingualTopicModel.class, "output-state-interval", "INTEGER", true, 0,
          "The number of iterations between writing the sampling state to a text file.  " +
         "You must also set the --output-state to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Integer numTopicsOption = new CommandOption.Integer(PolylingualTopicModel.class, "num-topics", "INTEGER", true, 10,
         "The number of topics to fit.", null);

    static CommandOption.Integer numIterationsOption = new CommandOption.Integer(PolylingualTopicModel.class, "num-iterations", "INTEGER", true, 1000,
         "The number of iterations of Gibbs sampling.", null);

    static CommandOption.Integer randomSeedOption = new CommandOption.Integer(PolylingualTopicModel.class, "random-seed", "INTEGER", true, 0,
         "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

    static CommandOption.Integer topWordsOption = new CommandOption.Integer(PolylingualTopicModel.class, "num-top-words", "INTEGER", true, 20,
         "The number of most probable words to print for each topic after model estimation.", null);

    static CommandOption.Integer showTopicsIntervalOption = new CommandOption.Integer(PolylingualTopicModel.class, "show-topics-interval", "INTEGER", true, 50,
         "The number of iterations between printing a brief summary of the topics so far.", null);

    static CommandOption.Integer optimizeIntervalOption = new CommandOption.Integer(PolylingualTopicModel.class, "optimize-interval", "INTEGER", true, 0,
         "The number of iterations between reestimating dirichlet hyperparameters.", null);

    static CommandOption.Integer optimizeBurnInOption = new CommandOption.Integer(PolylingualTopicModel.class, "optimize-burn-in", "INTEGER", true, 200,
         "The number of iterations to run before first estimating dirichlet hyperparameters.", null);

    static CommandOption.Double alphaOption = new CommandOption.Double(PolylingualTopicModel.class, "alpha", "DECIMAL", true, 50.0,
         "Alpha parameter: smoothing over topic distribution.",null);

    static CommandOption.Double betaOption = new CommandOption.Double(PolylingualTopicModel.class, "beta", "DECIMAL", true, 0.01,
         "Beta parameter: smoothing over unigram distribution.",null);
    
    public class TopicAssignment implements Serializable {
        public Instance[] instances;
        public LabelSequence[] topicSequences;
        public Labeling topicDistribution;
        
        public TopicAssignment (final Instance[] instances, final LabelSequence[] topicSequences) {
            this.instances = instances;
            this.topicSequences = topicSequences;
        }
    }

    int numLanguages = 1;

    protected ArrayList<TopicAssignment> data; // the training instances and their topic assignments
    protected LabelAlphabet topicAlphabet; // the alphabet for the topics

    protected int numStopwords = 0;

    protected int numTopics; // Number of topics to be fit

    HashSet<String> testingIDs = null;

    // These values are used to encode type/topic counts as
    // count/topic pairs in a single int.
    protected int topicMask;
    protected int topicBits;

    protected Alphabet[] alphabets;
    protected int[] vocabularySizes;

    protected double[] alpha; // Dirichlet(alpha,alpha,...) is the distribution over topics
    protected double alphaSum;
    protected double[] betas; // Prior on per-topic multinomial distribution over words
    protected double[] betaSums;

    protected int[] languageMaxTypeCounts;

    public static final double DEFAULT_BETA = 0.01;

    protected double[] languageSmoothingOnlyMasses;
    protected double[][] languageCachedCoefficients;
    int topicTermCount = 0;
    int betaTopicCount = 0;
    int smoothingOnlyCount = 0;

    // An array to put the topic counts for the current document.
    // Initialized locally below. Defined here to avoid
    // garbage collection overhead.
    protected int[] oneDocTopicCounts; // indexed by <document index, topic index>

    protected int[][][] languageTypeTopicCounts; // indexed by <feature index, topic index>
    protected int[][] languageTokensPerTopic; // indexed by <topic index>

    // for dirichlet estimation
    protected int[] docLengthCounts; // histogram of document sizes, summed over languages
    protected int[][] topicDocCounts; // histogram of document/topic counts, indexed by <topic index, sequence
                                      // position index>

    protected int iterationsSoFar = 1;
    public int numIterations = 1000;
    public int burninPeriod = 5;
    public int saveSampleInterval = 5; // was 10;
    public int optimizeInterval = 10;
    public int showTopicsInterval = 10; // was 50;
    public int wordsPerTopic = 7;

    protected int saveModelInterval = 0;
    protected String modelFilename;

    protected int saveStateInterval = 0;
    protected String stateFilename = null;

    protected Randoms random;
    protected NumberFormat formatter;
    protected boolean printLogLikelihood = false;

    public PolylingualTopicModel(final int numberOfTopics) {
        this(numberOfTopics, numberOfTopics);
    }

    public PolylingualTopicModel(final int numberOfTopics, final double alphaSum) {
        this(numberOfTopics, alphaSum, new Randoms());
    }

    private static LabelAlphabet newLabelAlphabet(final int numTopics) {
        final LabelAlphabet ret = new LabelAlphabet();
        for (int i = 0; i < numTopics; i++)
            ret.lookupIndex("topic" + i);
        return ret;
    }

    public PolylingualTopicModel(final int numberOfTopics, final double alphaSum, final Randoms random) {
        this(newLabelAlphabet(numberOfTopics), alphaSum, random);
    }

    public PolylingualTopicModel(final LabelAlphabet topicAlphabet, final double alphaSum, final Randoms random) {
        this.data = new ArrayList<TopicAssignment>();
        this.topicAlphabet = topicAlphabet;
        this.numTopics = topicAlphabet.size();

        if (Integer.bitCount(numTopics) == 1) {
            // exact power of 2
            topicMask = numTopics - 1;
            topicBits = Integer.bitCount(topicMask);
        } else {
            // otherwise add an extra bit
            topicMask = Integer.highestOneBit(numTopics) * 2 - 1;
            topicBits = Integer.bitCount(topicMask);
        }

        this.alphaSum = alphaSum;
        this.alpha = new double[numTopics];
        Arrays.fill(alpha, alphaSum / numTopics);
        this.random = random;

        formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(5);

        System.err.println("Polylingual LDA: " + numTopics + " topics, " + topicBits + " topic bits, "
                + Integer.toBinaryString(topicMask) + " topic mask");
    }

    public void loadTestingIDs(final File testingIDFile) throws IOException {
        testingIDs = new HashSet();

        final BufferedReader in = new BufferedReader(new FileReader(testingIDFile));
        String id = null;
        while ((id = in.readLine()) != null) {
            testingIDs.add(id);
        }
        in.close();
    }

    public LabelAlphabet getTopicAlphabet() {
        return topicAlphabet;
    }

    public int getNumTopics() {
        return numTopics;
    }

    public ArrayList<TopicAssignment> getData() {
        return data;
    }

    public void setNumIterations(final int numIterations) {
        this.numIterations = numIterations;
    }

    public void setBurninPeriod(final int burninPeriod) {
        this.burninPeriod = burninPeriod;
    }

    public void setTopicDisplay(final int interval, final int n) {
        this.showTopicsInterval = interval;
        this.wordsPerTopic = n;
    }

    public void setRandomSeed(final int seed) {
        random = new Randoms(seed);
    }

    public void setOptimizeInterval(final int interval) {
        this.optimizeInterval = interval;
    }

    public void setModelOutput(final int interval, final String filename) {
        this.saveModelInterval = interval;
        this.modelFilename = filename;
    }

    /**
     * Define how often and where to save the state
     *
     * @param interval Save a copy of the state every <code>interval</code>
     *                 iterations.
     * @param filename Save the state to this file, with the iteration number as a
     *                 suffix
     */
    public void setSaveState(final int interval, final String filename) {
        this.saveStateInterval = interval;
        this.stateFilename = filename;
    }

    public void addInstances(final InstanceList[] training) {

        numLanguages = training.length;

        languageTokensPerTopic = new int[numLanguages][numTopics];

        alphabets = new Alphabet[numLanguages];
        vocabularySizes = new int[numLanguages];
        betas = new double[numLanguages];
        betaSums = new double[numLanguages];
        languageMaxTypeCounts = new int[numLanguages];
        languageTypeTopicCounts = new int[numLanguages][][];

        final int numInstances = training[0].size();

        final HashSet[] stoplists = new HashSet[numLanguages];

        for (int language = 0; language < numLanguages; language++) {

            if (training[language].size() != numInstances) {
                System.err.println("Warning: language " + language + " has " + training[language].size()
                        + " instances, lang 0 has " + numInstances);
            }

            alphabets[language] = training[language].getDataAlphabet();
            vocabularySizes[language] = alphabets[language].size();

            betas[language] = DEFAULT_BETA;
            betaSums[language] = betas[language] * vocabularySizes[language];

            languageTypeTopicCounts[language] = new int[vocabularySizes[language]][];

            final int[][] typeTopicCounts = languageTypeTopicCounts[language];

            // Get the total number of occurrences of each word type
            final int[] typeTotals = new int[vocabularySizes[language]];

            for (final Instance instance : training[language]) {
                if (testingIDs != null && testingIDs.contains(instance.getName())) {
                    continue;
                }

                final FeatureSequence tokens = (FeatureSequence) instance.getData();
                for (int position = 0; position < tokens.getLength(); position++) {
                    final int type = tokens.getIndexAtPosition(position);
                    typeTotals[type]++;
                }
            }

            /*
             * Automatic stoplist creation, currently disabled TreeSet<IDSorter> sortedWords
             * = new TreeSet<IDSorter>(); for (int type = 0; type <
             * vocabularySizes[language]; type++) { sortedWords.add(new IDSorter(type,
             * typeTotals[type])); }
             * 
             * stoplists[language] = new HashSet<Integer>(); Iterator<IDSorter> typeIterator
             * = sortedWords.iterator(); int totalStopwords = 0;
             * 
             * while (typeIterator.hasNext() && totalStopwords < numStopwords) {
             * stoplists[language].add(typeIterator.next().getID()); }
             */

            // Allocate enough space so that we never have to worry about
            // overflows: either the number of topics or the number of times
            // the type occurs.
            for (int type = 0; type < vocabularySizes[language]; type++) {
                if (typeTotals[type] > languageMaxTypeCounts[language]) {
                    languageMaxTypeCounts[language] = typeTotals[type];
                }
                typeTopicCounts[type] = new int[Math.min(numTopics, typeTotals[type])];
            }
        }

        for (int doc = 0; doc < numInstances; doc++) {

            if (testingIDs != null && testingIDs.contains(training[0].get(doc).getName())) {
                continue;
            }

            final Instance[] instances = new Instance[numLanguages];
            final LabelSequence[] topicSequences = new LabelSequence[numLanguages];

            for (int language = 0; language < numLanguages; language++) {

                final int[][] typeTopicCounts = languageTypeTopicCounts[language];
                final int[] tokensPerTopic = languageTokensPerTopic[language];

                instances[language] = training[language].get(doc);
                final FeatureSequence tokens = (FeatureSequence) instances[language].getData();
                topicSequences[language] = new LabelSequence(topicAlphabet, new int[tokens.size()]);

                final int[] topics = topicSequences[language].getFeatures();
                for (int position = 0; position < tokens.size(); position++) {

                    final int type = tokens.getIndexAtPosition(position);
                    final int[] currentTypeTopicCounts = typeTopicCounts[type];

                    final int topic = random.nextInt(numTopics);

                    // If the word is one of the [numStopwords] most
                    // frequent words, put it in a non-sampled topic.
                    // if (stoplists[language].contains(type)) {
                    // topic = -1;
                    // }

                    topics[position] = topic;
                    tokensPerTopic[topic]++;

                    // The format for these arrays is
                    // the topic in the rightmost bits
                    // the count in the remaining (left) bits.
                    // Since the count is in the high bits, sorting (desc)
                    // by the numeric value of the int guarantees that
                    // higher counts will be before the lower counts.

                    // Start by assuming that the array is either empty
                    // or is in sorted (descending) order.

                    // Here we are only adding counts, so if we find
                    // an existing location with the topic, we only need
                    // to ensure that it is not larger than its left neighbor.

                    int index = 0;
                    int currentTopic = currentTypeTopicCounts[index] & topicMask;
                    int currentValue;

                    while (currentTypeTopicCounts[index] > 0 && currentTopic != topic) {
                        index++;

                        /*
                         * // Debugging output... if (index >= currentTypeTopicCounts.length) { for (int
                         * i=0; i < currentTypeTopicCounts.length; i++) {
                         * System.out.println((currentTypeTopicCounts[i] & topicMask) + ":" +
                         * (currentTypeTopicCounts[i] >> topicBits) + " "); }
                         * 
                         * System.out.println(type + " " + typeTotals[type]); }
                         */
                        currentTopic = currentTypeTopicCounts[index] & topicMask;
                    }
                    currentValue = currentTypeTopicCounts[index] >> topicBits;

                    if (currentValue == 0) {
                        // new value is 1, so we don't have to worry about sorting
                        // (except by topic suffix, which doesn't matter)

                        currentTypeTopicCounts[index] = (1 << topicBits) + topic;
                    } else {
                        currentTypeTopicCounts[index] = ((currentValue + 1) << topicBits) + topic;

                        // Now ensure that the array is still sorted by
                        // bubbling this value up.
                        while (index > 0 && currentTypeTopicCounts[index] > currentTypeTopicCounts[index - 1]) {
                            final int temp = currentTypeTopicCounts[index];
                            currentTypeTopicCounts[index] = currentTypeTopicCounts[index - 1];
                            currentTypeTopicCounts[index - 1] = temp;

                            index--;
                        }
                    }
                }
            }

            final TopicAssignment t = new TopicAssignment(instances, topicSequences);
            data.add(t);
        }

        initializeHistograms();

        languageSmoothingOnlyMasses = new double[numLanguages];
        languageCachedCoefficients = new double[numLanguages][numTopics];

        cacheValues();
    }

    /**
     * Gather statistics on the size of documents and create histograms for use in
     * Dirichlet hyperparameter optimization.
     */
    private void initializeHistograms() {

        int maxTokens = 0;
        int totalTokens = 0;

        for (int doc = 0; doc < data.size(); doc++) {
            int length = 0;
            for (final LabelSequence sequence : data.get(doc).topicSequences) {
                length += sequence.getLength();
            }

            if (length > maxTokens) {
                maxTokens = length;
            }

            totalTokens += length;
        }

        System.err.println("max tokens: " + maxTokens);
        System.err.println("total tokens: " + totalTokens);

        docLengthCounts = new int[maxTokens + 1];
        topicDocCounts = new int[numTopics][maxTokens + 1];

    }

    private void cacheValues() {

        for (int language = 0; language < numLanguages; language++) {
            languageSmoothingOnlyMasses[language] = 0.0;

            for (int topic = 0; topic < numTopics; topic++) {
                languageSmoothingOnlyMasses[language] += alpha[topic] * betas[language]
                        / (languageTokensPerTopic[language][topic] + betaSums[language]);
                languageCachedCoefficients[language][topic] = alpha[topic]
                        / (languageTokensPerTopic[language][topic] + betaSums[language]);
            }

        }

    }

    private void clearHistograms() {
        Arrays.fill(docLengthCounts, 0);
        for (int topic = 0; topic < topicDocCounts.length; topic++)
            Arrays.fill(topicDocCounts[topic], 0);
    }

    public void estimate() throws IOException {
        estimate(numIterations);
    }

    public void estimate(final int iterationsThisRound) throws IOException {

        final long startTime = System.currentTimeMillis();
        final int maxIteration = iterationsSoFar + iterationsThisRound;

        long totalTime = 0;

        for (; iterationsSoFar <= maxIteration; iterationsSoFar++) {
            final long iterationStart = System.currentTimeMillis();

            if (showTopicsInterval != 0 && iterationsSoFar != 0 && iterationsSoFar % showTopicsInterval == 0) {
                System.out.println();
                printTopWords(System.out, wordsPerTopic, false);

            }

            if (saveStateInterval != 0 && iterationsSoFar % saveStateInterval == 0) {
                this.printState(new File(stateFilename + '.' + iterationsSoFar));
            }

            /*
             * if (saveModelInterval != 0 && iterations % saveModelInterval == 0) {
             * this.write (new File(modelFilename+'.'+iterations)); }
             */

            // TODO this condition should also check that we have more than one sample to
            // work with here
            // (The number of samples actually obtained is not yet tracked.)
            if (iterationsSoFar > burninPeriod && optimizeInterval != 0 && iterationsSoFar % optimizeInterval == 0) {

                alphaSum = Dirichlet.learnParameters(alpha, topicDocCounts, docLengthCounts);
                optimizeBetas();
                clearHistograms();
                cacheValues();
            }

            // Loop over every document in the corpus
            topicTermCount = betaTopicCount = smoothingOnlyCount = 0;

            for (int doc = 0; doc < data.size(); doc++) {

                sampleTopicsForOneDoc(data.get(doc),
                        (iterationsSoFar >= burninPeriod && iterationsSoFar % saveSampleInterval == 0));
            }

            final long elapsedMillis = System.currentTimeMillis() - iterationStart;
            totalTime += elapsedMillis;

            if ((iterationsSoFar + 1) % 10 == 0) {

                final double ll = modelLogLikelihood();
                System.out.println(elapsedMillis + "\t" + totalTime + "\t" + ll);
            } else {
                System.out.print(elapsedMillis + " ");
            }
        }

        /*
         * long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
         * long minutes = seconds / 60; seconds %= 60; long hours = minutes / 60;
         * minutes %= 60; long days = hours / 24; hours %= 24; System.out.print
         * ("\nTotal time: "); if (days != 0) { System.out.print(days);
         * System.out.print(" days "); } if (hours != 0) { System.out.print(hours);
         * System.out.print(" hours "); } if (minutes != 0) { System.out.print(minutes);
         * System.out.print(" minutes "); } System.out.print(seconds);
         * System.out.println(" seconds");
         */
    }

    public void optimizeBetas() {

        for (int language = 0; language < numLanguages; language++) {

            // The histogram starts at count 0, so if all of the
            // tokens of the most frequent type were assigned to one topic,
            // we would need to store a maxTypeCount + 1 count.
            final int[] countHistogram = new int[languageMaxTypeCounts[language] + 1];

            // Now count the number of type/topic pairs that have
            // each number of tokens.

            final int[][] typeTopicCounts = languageTypeTopicCounts[language];
            final int[] tokensPerTopic = languageTokensPerTopic[language];

            int index;
            for (int type = 0; type < vocabularySizes[language]; type++) {
                final int[] counts = typeTopicCounts[type];
                index = 0;
                while (index < counts.length && counts[index] > 0) {
                    final int count = counts[index] >> topicBits;
                    countHistogram[count]++;
                    index++;
                }
            }

            // Figure out how large we need to make the "observation lengths"
            // histogram.
            int maxTopicSize = 0;
            for (int topic = 0; topic < numTopics; topic++) {
                if (tokensPerTopic[topic] > maxTopicSize) {
                    maxTopicSize = tokensPerTopic[topic];
                }
            }

            // Now allocate it and populate it.
            final int[] topicSizeHistogram = new int[maxTopicSize + 1];
            for (int topic = 0; topic < numTopics; topic++) {
                topicSizeHistogram[tokensPerTopic[topic]]++;
            }

            betaSums[language] = Dirichlet.learnSymmetricConcentration(countHistogram, topicSizeHistogram,
                    vocabularySizes[language], betaSums[language]);
            betas[language] = betaSums[language] / vocabularySizes[language];
        }
    }

    protected void sampleTopicsForOneDoc(final TopicAssignment topicAssignment, final boolean shouldSaveState) {

        int[] currentTypeTopicCounts;
        int type, oldTopic, newTopic;
        final double topicWeightsSum;

        final int[] localTopicCounts = new int[numTopics];
        final int[] localTopicIndex = new int[numTopics];

        for (int language = 0; language < numLanguages; language++) {

            final int[] oneDocTopics = topicAssignment.topicSequences[language].getFeatures();
            final int docLength = topicAssignment.topicSequences[language].getLength();

            // populate topic counts
            for (int position = 0; position < docLength; position++) {
                localTopicCounts[oneDocTopics[position]]++;
            }
        }

        // Build an array that densely lists the topics that
        // have non-zero counts.
        int denseIndex = 0;
        for (int topic = 0; topic < numTopics; topic++) {
            if (localTopicCounts[topic] != 0) {
                localTopicIndex[denseIndex] = topic;
                denseIndex++;
            }
        }

        // Record the total number of non-zero topics
        int nonZeroTopics = denseIndex;

        for (int language = 0; language < numLanguages; language++) {

            final int[] oneDocTopics = topicAssignment.topicSequences[language].getFeatures();
            final int docLength = topicAssignment.topicSequences[language].getLength();
            final FeatureSequence tokenSequence = (FeatureSequence) topicAssignment.instances[language].getData();

            final int[][] typeTopicCounts = languageTypeTopicCounts[language];
            final int[] tokensPerTopic = languageTokensPerTopic[language];
            final double beta = betas[language];
            final double betaSum = betaSums[language];

            // Initialize the smoothing-only sampling bucket
            double smoothingOnlyMass = languageSmoothingOnlyMasses[language];
            // for (int topic = 0; topic < numTopics; topic++)
            // smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);

            // Initialize the cached coefficients, using only smoothing.
            // cachedCoefficients = new double[ numTopics ];
            // for (int topic=0; topic < numTopics; topic++)
            // cachedCoefficients[topic] = alpha[topic] / (tokensPerTopic[topic] + betaSum);

            final double[] cachedCoefficients = languageCachedCoefficients[language];

            // Initialize the topic count/beta sampling bucket
            double topicBetaMass = 0.0;

            // Initialize cached coefficients and the topic/beta
            // normalizing constant.

            for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
                final int topic = localTopicIndex[denseIndex];
                final int n = localTopicCounts[topic];

                // initialize the normalization constant for the (B * n_{t|d}) term
                topicBetaMass += beta * n / (tokensPerTopic[topic] + betaSum);

                // update the coefficients for the non-zero topics
                cachedCoefficients[topic] = (alpha[topic] + n) / (tokensPerTopic[topic] + betaSum);
            }

            double topicTermMass = 0.0;

            final double[] topicTermScores = new double[numTopics];
            final int[] topicTermIndices;
            final int[] topicTermValues;
            int i;
            double score;

            // Iterate over the positions (words) in the document
            for (int position = 0; position < docLength; position++) {
                type = tokenSequence.getIndexAtPosition(position);
                oldTopic = oneDocTopics[position];
                if (oldTopic == -1) {
                    continue;
                }

                currentTypeTopicCounts = typeTopicCounts[type];

                // Remove this token from all counts.

                // Remove this topic's contribution to the
                // normalizing constants
                smoothingOnlyMass -= alpha[oldTopic] * beta / (tokensPerTopic[oldTopic] + betaSum);
                topicBetaMass -= beta * localTopicCounts[oldTopic] / (tokensPerTopic[oldTopic] + betaSum);

                // Decrement the local doc/topic counts

                localTopicCounts[oldTopic]--;

                // Maintain the dense index, if we are deleting
                // the old topic
                if (localTopicCounts[oldTopic] == 0) {

                    // First get to the dense location associated with
                    // the old topic.

                    denseIndex = 0;

                    // We know it's in there somewhere, so we don't
                    // need bounds checking.
                    while (localTopicIndex[denseIndex] != oldTopic) {
                        denseIndex++;
                    }

                    // shift all remaining dense indices to the left.
                    while (denseIndex < nonZeroTopics) {
                        if (denseIndex < localTopicIndex.length - 1) {
                            localTopicIndex[denseIndex] = localTopicIndex[denseIndex + 1];
                        }
                        denseIndex++;
                    }

                    nonZeroTopics--;
                }

                // Decrement the global topic count totals
                tokensPerTopic[oldTopic]--;
                // assert(tokensPerTopic[oldTopic] >= 0) : "old Topic " + oldTopic + " below 0";

                // Add the old topic's contribution back into the
                // normalizing constants.
                smoothingOnlyMass += alpha[oldTopic] * beta / (tokensPerTopic[oldTopic] + betaSum);
                topicBetaMass += beta * localTopicCounts[oldTopic] / (tokensPerTopic[oldTopic] + betaSum);

                // Reset the cached coefficient for this topic
                cachedCoefficients[oldTopic] = (alpha[oldTopic] + localTopicCounts[oldTopic])
                        / (tokensPerTopic[oldTopic] + betaSum);

                // Now go over the type/topic counts, decrementing
                // where appropriate, and calculating the score
                // for each topic at the same time.

                int index = 0;
                int currentTopic, currentValue;

                boolean alreadyDecremented = false;

                topicTermMass = 0.0;

                while (index < currentTypeTopicCounts.length && currentTypeTopicCounts[index] > 0) {
                    currentTopic = currentTypeTopicCounts[index] & topicMask;
                    currentValue = currentTypeTopicCounts[index] >> topicBits;

                    if (!alreadyDecremented && currentTopic == oldTopic) {

                        // We're decrementing and adding up the
                        // sampling weights at the same time, but
                        // decrementing may require us to reorder
                        // the topics, so after we're done here,
                        // look at this cell in the array again.

                        currentValue--;
                        if (currentValue == 0) {
                            currentTypeTopicCounts[index] = 0;
                        } else {
                            currentTypeTopicCounts[index] = (currentValue << topicBits) + oldTopic;
                        }

                        // Shift the reduced value to the right, if necessary.

                        int subIndex = index;
                        while (subIndex < currentTypeTopicCounts.length - 1
                                && currentTypeTopicCounts[subIndex] < currentTypeTopicCounts[subIndex + 1]) {
                            final int temp = currentTypeTopicCounts[subIndex];
                            currentTypeTopicCounts[subIndex] = currentTypeTopicCounts[subIndex + 1];
                            currentTypeTopicCounts[subIndex + 1] = temp;

                            subIndex++;
                        }

                        alreadyDecremented = true;
                    } else {
                        score = cachedCoefficients[currentTopic] * currentValue;
                        topicTermMass += score;
                        topicTermScores[index] = score;

                        index++;
                    }
                }

                double sample = random.nextUniform() * (smoothingOnlyMass + topicBetaMass + topicTermMass);
                final double origSample = sample;

                // Make sure it actually gets set
                newTopic = -1;

                if (sample < topicTermMass) {
                    // topicTermCount++;

                    i = -1;
                    while (sample > 0) {
                        i++;
                        sample -= topicTermScores[i];
                    }

                    newTopic = currentTypeTopicCounts[i] & topicMask;
                    currentValue = currentTypeTopicCounts[i] >> topicBits;

                    currentTypeTopicCounts[i] = ((currentValue + 1) << topicBits) + newTopic;

                    // Bubble the new value up, if necessary

                    while (i > 0 && currentTypeTopicCounts[i] > currentTypeTopicCounts[i - 1]) {
                        final int temp = currentTypeTopicCounts[i];
                        currentTypeTopicCounts[i] = currentTypeTopicCounts[i - 1];
                        currentTypeTopicCounts[i - 1] = temp;

                        i--;
                    }

                } else {
                    sample -= topicTermMass;

                    if (sample < topicBetaMass) {
                        // betaTopicCount++;

                        sample /= beta;

                        for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
                            final int topic = localTopicIndex[denseIndex];

                            sample -= localTopicCounts[topic] / (tokensPerTopic[topic] + betaSum);

                            if (sample <= 0.0) {
                                newTopic = topic;
                                break;
                            }
                        }

                    } else {
                        // smoothingOnlyCount++;

                        sample -= topicBetaMass;

                        sample /= beta;

                        newTopic = 0;
                        sample -= alpha[newTopic] / (tokensPerTopic[newTopic] + betaSum);

                        while (sample > 0.0) {
                            newTopic++;
                            sample -= alpha[newTopic] / (tokensPerTopic[newTopic] + betaSum);
                        }

                    }

                    // Move to the position for the new topic,
                    // which may be the first empty position if this
                    // is a new topic for this word.

                    index = 0;
                    while (currentTypeTopicCounts[index] > 0
                            && (currentTypeTopicCounts[index] & topicMask) != newTopic) {
                        index++;
                    }

                    // index should now be set to the position of the new topic,
                    // which may be an empty cell at the end of the list.

                    if (currentTypeTopicCounts[index] == 0) {
                        // inserting a new topic, guaranteed to be in
                        // order w.r.t. count, if not topic.
                        currentTypeTopicCounts[index] = (1 << topicBits) + newTopic;
                    } else {
                        currentValue = currentTypeTopicCounts[index] >> topicBits;
                        currentTypeTopicCounts[index] = ((currentValue + 1) << topicBits) + newTopic;

                        // Bubble the increased value left, if necessary
                        while (index > 0 && currentTypeTopicCounts[index] > currentTypeTopicCounts[index - 1]) {
                            final int temp = currentTypeTopicCounts[index];
                            currentTypeTopicCounts[index] = currentTypeTopicCounts[index - 1];
                            currentTypeTopicCounts[index - 1] = temp;

                            index--;
                        }
                    }

                }

                if (newTopic == -1) {
                    System.err.println("PolylingualTopicModel sampling error: " + origSample + " " + sample + " "
                            + smoothingOnlyMass + " " + topicBetaMass + " " + topicTermMass);
                    newTopic = numTopics - 1; // TODO is this appropriate
                    // throw new IllegalStateException ("PolylingualTopicModel: New topic not
                    // sampled.");
                }
                // assert(newTopic != -1);

                // Put that new topic into the counts
                oneDocTopics[position] = newTopic;

                smoothingOnlyMass -= alpha[newTopic] * beta / (tokensPerTopic[newTopic] + betaSum);
                topicBetaMass -= beta * localTopicCounts[newTopic] / (tokensPerTopic[newTopic] + betaSum);

                localTopicCounts[newTopic]++;

                // If this is a new topic for this document,
                // add the topic to the dense index.
                if (localTopicCounts[newTopic] == 1) {

                    // First find the point where we
                    // should insert the new topic by going to
                    // the end (which is the only reason we're keeping
                    // track of the number of non-zero
                    // topics) and working backwards

                    denseIndex = nonZeroTopics;

                    while (denseIndex > 0 && localTopicIndex[denseIndex - 1] > newTopic) {

                        localTopicIndex[denseIndex] = localTopicIndex[denseIndex - 1];
                        denseIndex--;
                    }

                    localTopicIndex[denseIndex] = newTopic;
                    nonZeroTopics++;
                }

                tokensPerTopic[newTopic]++;

                // update the coefficients for the non-zero topics
                cachedCoefficients[newTopic] = (alpha[newTopic] + localTopicCounts[newTopic])
                        / (tokensPerTopic[newTopic] + betaSum);

                smoothingOnlyMass += alpha[newTopic] * beta / (tokensPerTopic[newTopic] + betaSum);
                topicBetaMass += beta * localTopicCounts[newTopic] / (tokensPerTopic[newTopic] + betaSum);

                // Save the smoothing-only mass to the global cache
                languageSmoothingOnlyMasses[language] = smoothingOnlyMass;

            }
        }

        if (shouldSaveState) {
            // Update the document-topic count histogram,
            // for dirichlet estimation

            int totalLength = 0;

            for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
                final int topic = localTopicIndex[denseIndex];

                topicDocCounts[topic][localTopicCounts[topic]]++;
                totalLength += localTopicCounts[topic];
            }

            docLengthCounts[totalLength]++;

        }

    }

    public void printTopWords(final File file, final int numWords, final boolean useNewLines) throws IOException {
        final PrintStream out = new PrintStream(file);
        printTopWords(out, numWords, useNewLines);
        out.close();
    }

    public void printTopWords(final PrintStream out, final int numWords, final boolean usingNewLines) {

        final TreeSet[][] languageTopicSortedWords = new TreeSet[numLanguages][numTopics];

        for (int language = 0; language < numLanguages; language++) {
            final TreeSet[] topicSortedWords = languageTopicSortedWords[language];
            final int[][] typeTopicCounts = languageTypeTopicCounts[language];

            for (int topic = 0; topic < numTopics; topic++) {
                topicSortedWords[topic] = new TreeSet<IDSorter>();
            }

            for (int type = 0; type < vocabularySizes[language]; type++) {

                final int[] topicCounts = typeTopicCounts[type];

                int index = 0;
                while (index < topicCounts.length && topicCounts[index] > 0) {

                    final int topic = topicCounts[index] & topicMask;
                    final int count = topicCounts[index] >> topicBits;

                    topicSortedWords[topic].add(new IDSorter(type, count));

                    index++;
                }
            }
        }

        for (int topic = 0; topic < numTopics; topic++) {

            out.println(topic + "\t" + formatter.format(alpha[topic]));

            for (int language = 0; language < numLanguages; language++) {

                out.print(" " + language + "\t" + languageTokensPerTopic[language][topic] + "\t" + betas[language]
                        + "\t");

                final TreeSet<IDSorter> sortedWords = languageTopicSortedWords[language][topic];
                final Alphabet alphabet = alphabets[language];

                int word = 1;
                final Iterator<IDSorter> iterator = sortedWords.iterator();
                while (iterator.hasNext() && word < numWords) {
                    final IDSorter info = iterator.next();

                    out.print(alphabet.lookupObject(info.getID()) + " ");
                    word++;
                }

                out.println();
            }
        }
    }

    public void printDocumentTopics(final File f) throws IOException {
        printDocumentTopics(new PrintWriter(f, "UTF-8"));
    }

    public void printDocumentTopics(final PrintWriter pw) {
        printDocumentTopics(pw, 0.0, -1);
    }

    /**
     * @param pw        A print writer
     * @param threshold Only print topics with proportion greater than this number
     * @param max       Print no more than this many topics
     */
    public void printDocumentTopics(final PrintWriter pw, final double threshold, int max) {
        pw.print("#doc source topic proportion ...\n");
        int docLength;
        final int[] topicCounts = new int[numTopics];

        final IDSorter[] sortedTopics = new IDSorter[numTopics];
        for (int topic = 0; topic < numTopics; topic++) {
            // Initialize the sorters with dummy values
            sortedTopics[topic] = new IDSorter(topic, topic);
        }

        if (max < 0 || max > numTopics) {
            max = numTopics;
        }

        for (int di = 0; di < data.size(); di++) {

            pw.print(di);
            pw.print(' ');

            int totalLength = 0;

            for (int language = 0; language < numLanguages; language++) {

                final LabelSequence topicSequence = (LabelSequence) data.get(di).topicSequences[language];
                final int[] currentDocTopics = topicSequence.getFeatures();

                docLength = topicSequence.getLength();
                totalLength += docLength;

                // Count up the tokens
                for (int token = 0; token < docLength; token++) {
                    topicCounts[currentDocTopics[token]]++;
                }
            }

            // And normalize
            for (int topic = 0; topic < numTopics; topic++) {
                sortedTopics[topic].set(topic, (float) topicCounts[topic] / totalLength);
            }

            Arrays.sort(sortedTopics);

            for (int i = 0; i < max; i++) {
                if (sortedTopics[i].getWeight() < threshold) {
                    break;
                }

                pw.print(sortedTopics[i].getID() + " " + sortedTopics[i].getWeight() + " ");
            }
            pw.print(" \n");

            Arrays.fill(topicCounts, 0);
        }

    }

    public void printState(final File f) throws IOException {
        final PrintStream out = new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(f))),
                false, "UTF-8");
        printState(out);
        out.close();
    }

    public void printState(final PrintStream out) {

        out.println("#doc lang pos typeindex type topic");

        for (int doc = 0; doc < data.size(); doc++) {
            for (int language = 0; language < numLanguages; language++) {
                final FeatureSequence tokenSequence = (FeatureSequence) data.get(doc).instances[language].getData();
                final LabelSequence topicSequence = (LabelSequence) data.get(doc).topicSequences[language];

                for (int pi = 0; pi < topicSequence.getLength(); pi++) {
                    final int type = tokenSequence.getIndexAtPosition(pi);
                    final int topic = topicSequence.getIndexAtPosition(pi);
                    out.print(doc);
                    out.print(' ');
                    out.print(language);
                    out.print(' ');
                    out.print(pi);
                    out.print(' ');
                    out.print(type);
                    out.print(' ');
                    out.print(alphabets[language].lookupObject(type));
                    out.print(' ');
                    out.print(topic);
                    out.println();
                }
            }
        }
    }

    public double modelLogLikelihood() {
        double logLikelihood = 0.0;
        final int nonZeroTopics;

        // The likelihood of the model is a combination of a
        // Dirichlet-multinomial for the words in each topic
        // and a Dirichlet-multinomial for the topics in each
        // document.

        // The likelihood function of a dirichlet multinomial is
        // Gamma( sum_i alpha_i ) prod_i Gamma( alpha_i + N_i )
        // prod_i Gamma( alpha_i ) Gamma( sum_i (alpha_i + N_i) )

        // So the log likelihood is
        // logGamma ( sum_i alpha_i ) - logGamma ( sum_i (alpha_i + N_i) ) +
        // sum_i [ logGamma( alpha_i + N_i) - logGamma( alpha_i ) ]

        // Do the documents first

        int[] topicCounts = new int[numTopics];
        final double[] topicLogGammas = new double[numTopics];
        final int[] docTopics;

        for (int topic = 0; topic < numTopics; topic++) {
            topicLogGammas[topic] = Dirichlet.logGammaStirling(alpha[topic]);
        }

        for (int doc = 0; doc < data.size(); doc++) {

            int totalLength = 0;

            for (int language = 0; language < numLanguages; language++) {

                final LabelSequence topicSequence = (LabelSequence) data.get(doc).topicSequences[language];
                final int[] currentDocTopics = topicSequence.getFeatures();

                totalLength += topicSequence.getLength();

                // Count up the tokens
                for (int token = 0; token < topicSequence.getLength(); token++) {
                    topicCounts[currentDocTopics[token]]++;
                }
            }

            for (int topic = 0; topic < numTopics; topic++) {
                if (topicCounts[topic] > 0) {
                    logLikelihood += (Dirichlet.logGammaStirling(alpha[topic] + topicCounts[topic])
                            - topicLogGammas[topic]);
                }
            }

            // subtract the (count + parameter) sum term
            logLikelihood -= Dirichlet.logGammaStirling(alphaSum + totalLength);

            Arrays.fill(topicCounts, 0);
        }

        // add the parameter sum term
        logLikelihood += data.size() * Dirichlet.logGammaStirling(alphaSum);

        // And the topics

        for (int language = 0; language < numLanguages; language++) {
            final int[][] typeTopicCounts = languageTypeTopicCounts[language];
            final int[] tokensPerTopic = languageTokensPerTopic[language];
            final double beta = betas[language];

            // Count the number of type-topic pairs
            int nonZeroTypeTopics = 0;

            for (int type = 0; type < vocabularySizes[language]; type++) {
                // reuse this array as a pointer

                topicCounts = typeTopicCounts[type];

                int index = 0;
                while (index < topicCounts.length && topicCounts[index] > 0) {
                    final int topic = topicCounts[index] & topicMask;
                    final int count = topicCounts[index] >> topicBits;

                    nonZeroTypeTopics++;
                    logLikelihood += Dirichlet.logGammaStirling(beta + count);

                    if (Double.isNaN(logLikelihood)) {
                        System.out.println(count);
                        System.exit(1);
                    }

                    index++;
                }
            }

            for (int topic = 0; topic < numTopics; topic++) {
                logLikelihood -= Dirichlet.logGammaStirling((beta * vocabularySizes[language]) + tokensPerTopic[topic]);
                if (Double.isNaN(logLikelihood)) {
                    System.out.println("after topic " + topic + " " + tokensPerTopic[topic]);
                    System.exit(1);
                }

            }

            logLikelihood += (Dirichlet.logGammaStirling(beta * vocabularySizes[language]))
                    - (Dirichlet.logGammaStirling(beta) * nonZeroTypeTopics);
        }

        if (Double.isNaN(logLikelihood)) {
            System.out.println("at the end");
            System.exit(1);
        }

        return logLikelihood;
    }

    /** Return a tool for estimating topic distributions for new documents */
    public TopicInferencer getInferencer(final int language) {
        return new TopicInferencer(languageTypeTopicCounts[language], languageTokensPerTopic[language],
                alphabets[language], alpha, betas[language], betaSums[language]);
    }

    /** Return a tool for estimating topic distributions for new documents */
    public MarginalProbEstimator getProbEstimator(final int language) {
        return new MarginalProbEstimator(numTopics, alpha, alphaSum, betas[language], languageTypeTopicCounts[language],
                languageTokensPerTopic[language]);
    }

    // Serialization

    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    private static final int NULL_INTEGER = -1;

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.writeInt(CURRENT_SERIAL_VERSION);

        out.writeInt(numLanguages);
        out.writeObject(data);
        out.writeObject(topicAlphabet);

        out.writeInt(numTopics);

        out.writeObject(testingIDs);

        out.writeInt(topicMask);
        out.writeInt(topicBits);

        out.writeObject(alphabets);
        out.writeObject(vocabularySizes);

        out.writeObject(alpha);
        out.writeDouble(alphaSum);
        out.writeObject(betas);
        out.writeObject(betaSums);

        out.writeObject(languageMaxTypeCounts);

        out.writeObject(languageTypeTopicCounts);
        out.writeObject(languageTokensPerTopic);

        out.writeObject(languageSmoothingOnlyMasses);
        out.writeObject(languageCachedCoefficients);

        out.writeObject(docLengthCounts);
        out.writeObject(topicDocCounts);

        out.writeInt(numIterations);
        out.writeInt(burninPeriod);
        out.writeInt(saveSampleInterval);
        out.writeInt(optimizeInterval);
        out.writeInt(showTopicsInterval);
        out.writeInt(wordsPerTopic);

        out.writeInt(saveStateInterval);
        out.writeObject(stateFilename);

        out.writeInt(saveModelInterval);
        out.writeObject(modelFilename);

        out.writeObject(random);
        out.writeObject(formatter);
        out.writeBoolean(printLogLikelihood);

    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {

        final int version = in.readInt();

        numLanguages = in.readInt();
        data = (ArrayList<TopicAssignment>) in.readObject();
        topicAlphabet = (LabelAlphabet) in.readObject();

        numTopics = in.readInt();

        testingIDs = (HashSet<String>) in.readObject();

        topicMask = in.readInt();
        topicBits = in.readInt();

        alphabets = (Alphabet[]) in.readObject();
        vocabularySizes = (int[]) in.readObject();

        alpha = (double[]) in.readObject();
        alphaSum = in.readDouble();
        betas = (double[]) in.readObject();
        betaSums = (double[]) in.readObject();

        languageMaxTypeCounts = (int[]) in.readObject();

        languageTypeTopicCounts = (int[][][]) in.readObject();
        languageTokensPerTopic = (int[][]) in.readObject();

        languageSmoothingOnlyMasses = (double[]) in.readObject();
        languageCachedCoefficients = (double[][]) in.readObject();

        docLengthCounts = (int[]) in.readObject();
        topicDocCounts = (int[][]) in.readObject();

        numIterations = in.readInt();
        burninPeriod = in.readInt();
        saveSampleInterval = in.readInt();
        optimizeInterval = in.readInt();
        showTopicsInterval = in.readInt();
        wordsPerTopic = in.readInt();

        saveStateInterval = in.readInt();
        stateFilename = (String) in.readObject();

        saveModelInterval = in.readInt();
        modelFilename = (String) in.readObject();

        random = (Randoms) in.readObject();
        formatter = (NumberFormat) in.readObject();
        printLogLikelihood = in.readBoolean();

    }

    public void write(final File serializedModelFile) {
        try {
            final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(serializedModelFile));
            oos.writeObject(this);
            oos.close();
        } catch (final IOException e) {
            System.err.println("Problem serializing PolylingualTopicModel to file " + serializedModelFile + ": " + e);
        }
    }

    public static PolylingualTopicModel read(final File f) throws Exception {

        PolylingualTopicModel topicModel = null;

        final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
        topicModel = (PolylingualTopicModel) ois.readObject();
        ois.close();

        topicModel.initializeHistograms();

        return topicModel;
    }

    public static void main(final String[] args) throws IOException {

        CommandOption.setSummary(PolylingualTopicModel.class,
                "A tool for estimating, saving and printing diagnostics for topic models over comparable corpora.");
        CommandOption.process(PolylingualTopicModel.class, args);

        PolylingualTopicModel topicModel = null;

        if (inputModelFilename.value != null) {

            try {
                topicModel = PolylingualTopicModel.read(new File(inputModelFilename.value));
            } catch (final Exception e) {
                System.err.println("Unable to restore saved topic model " + inputModelFilename.value + ": " + e);
                System.exit(1);
            }
        } else {

            final int numLanguages = languageInputFiles.value.length;

            final InstanceList[] training = new InstanceList[numLanguages];
            for (int i = 0; i < training.length; i++) {
                training[i] = InstanceList.load(new File(languageInputFiles.value[i]));
                if (training[i] != null) {
                    System.out.println(i + " is not null");
                } else {
                    System.out.println(i + " is null");
                }
            }

            System.out.println("Data loaded.");

            // For historical reasons we currently only support FeatureSequence data,
            // not the FeatureVector, which is the default for the input functions.
            // Provide a warning to avoid ClassCastExceptions.
            if (training[0].size() > 0 && training[0].get(0) != null) {
                final Object data = training[0].get(0).getData();
                if (!(data instanceof FeatureSequence)) {
                    System.err.println(
                            "Topic modeling currently only supports feature sequences: use --keep-sequence option when importing data.");
                    System.exit(1);
                }
            }

            topicModel = new PolylingualTopicModel(numTopicsOption.value, alphaOption.value);
            if (randomSeedOption.value != 0) {
                topicModel.setRandomSeed(randomSeedOption.value);
            }

            topicModel.addInstances(training);
        }

        topicModel.setTopicDisplay(showTopicsIntervalOption.value, topWordsOption.value);

        topicModel.setNumIterations(numIterationsOption.value);
        topicModel.setOptimizeInterval(optimizeIntervalOption.value);
        topicModel.setBurninPeriod(optimizeBurnInOption.value);

        if (outputStateIntervalOption.value != 0) {
            topicModel.setSaveState(outputStateIntervalOption.value, stateFile.value);
        }

        if (outputModelIntervalOption.value != 0) {
            topicModel.setModelOutput(outputModelIntervalOption.value, outputModelFilename.value);
        }

        topicModel.estimate();

        if (topicKeysFile.value != null) {
            topicModel.printTopWords(new File(topicKeysFile.value), topWordsOption.value, false);
        }

        if (stateFile.value != null) {
            topicModel.printState(new File(stateFile.value));
        }

        if (docTopicsFile.value != null) {
            final PrintWriter out = new PrintWriter(new FileWriter((new File(docTopicsFile.value))));
            topicModel.printDocumentTopics(out, docTopicsThreshold.value, docTopicsMax.value);
            out.close();
        }

        if (inferencerFilename.value != null) {
            try {
                for (int language = 0; language < topicModel.numLanguages; language++) {

                    final ObjectOutputStream oos = new ObjectOutputStream(
                            new FileOutputStream(inferencerFilename.value + "." + language));
                    oos.writeObject(topicModel.getInferencer(language));
                    oos.close();
                }

            } catch (final Exception e) {
                System.err.println(e.getMessage());

            }

        }

        if (evaluatorFilename.value != null) {
            try {
                for (int language = 0; language < topicModel.numLanguages; language++) {

                    final ObjectOutputStream oos = new ObjectOutputStream(
                            new FileOutputStream(evaluatorFilename.value + "." + language));
                    oos.writeObject(topicModel.getProbEstimator(language));
                    oos.close();
                }
            } catch (final Exception e) {
                System.err.println(e.getMessage());

            }

        }

        if (outputModelFilename.value != null) {
            assert (topicModel != null);
            
            topicModel.write(new File(outputModelFilename.value));
        }

    }
    
}
