package cc.mallet.topics;

import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.OptimizationException;

import cc.mallet.util.*;
import cc.mallet.types.*;
import cc.mallet.classify.MaxEnt;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.Noop;

import java.io.*;
import java.util.concurrent.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import java.text.NumberFormat;

public class DMRTopicModel extends ParallelTopicModel {

    static CommandOption.String inputFile = new CommandOption.String(DMRTopicModel.class, "input", "FILENAME", true, null,
          "Filename for DMR topic model. Each instance should have features in its Target field.", null);

    static CommandOption.String outputModelFilename = new CommandOption.String(DMRTopicModel.class, "output-model", "FILENAME", true, null,
          "The filename in which to write the binary topic model at the end of the iterations.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String outputParametersFilename = new CommandOption.String(DMRTopicModel.class, "output-params", "FILENAME", true, "parameters.tsv",
          "The filename in which to write the DMR parameters.", null);

    static CommandOption.String inputModelFilename = new CommandOption.String(DMRTopicModel.class, "input-model", "FILENAME", true, null,
          "The filename from which to read the binary topic model to which the --input will be appended, " +
          "allowing incremental training.  " +
         "By default this is null, indicating that no file will be read.", null);

    static CommandOption.String inferencerFilename = new CommandOption.String(DMRTopicModel.class, "inferencer-filename", "FILENAME", true, null,
          "A topic inferencer applies a previously trained topic model to new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String evaluatorFilename = new CommandOption.String(DMRTopicModel.class, "evaluator-filename", "FILENAME", true, null,
          "A held-out likelihood evaluator for new documents.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String stateFile = new CommandOption.String(DMRTopicModel.class, "output-state", "FILENAME", true, null,
          "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String topicKeysFile = new CommandOption.String(DMRTopicModel.class, "output-topic-keys", "FILENAME", true, null,
          "The filename in which to write the top words for each topic and any Dirichlet parameters.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String docTopicsFile = new CommandOption.String(DMRTopicModel.class, "output-doc-topics", "FILENAME", true, null,
          "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
         "By default this is null, indicating that no file will be written.", null);

    static CommandOption.Double docTopicsThreshold = new CommandOption.Double(DMRTopicModel.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
          "When writing topic proportions per document with --output-doc-topics, " +
         "do not print topics with proportions less than this threshold value.", null);

    static CommandOption.Integer docTopicsMax = new CommandOption.Integer(DMRTopicModel.class, "doc-topics-max", "INTEGER", true, -1,
          "When writing topic proportions per document with --output-doc-topics, " +
          "do not print more than INTEGER number of topics.  "+
         "A negative value indicates that all topics should be printed.", null);

    static CommandOption.Integer outputStateIntervalOption = new CommandOption.Integer(DMRTopicModel.class, "output-state-interval", "INTEGER", true, 0,
          "The number of iterations between writing the sampling state to a text file.  " +
         "You must also set the --output-state to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Integer outputParametersIntervalOption = new CommandOption.Integer(DMRTopicModel.class, "output-params-interval", "INTEGER", true, 0,
          "The number of iterations between writing the DMR parameters to a text file.  " +
         "You must also set the --output-params to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Integer numTopicsOption = new CommandOption.Integer(DMRTopicModel.class, "num-topics", "INTEGER", true, 10,
         "The number of topics to fit.", null);

    static CommandOption.Integer numThreadsOption = new CommandOption.Integer(DMRTopicModel.class, "num-threads", "INTEGER", true, 1,
         "The number of threads to run in parallel.", null);

    static CommandOption.Integer numIterationsOption = new CommandOption.Integer(DMRTopicModel.class, "num-iterations", "INTEGER", true, 1000,
         "The number of iterations of Gibbs sampling.", null);

    static CommandOption.Integer randomSeedOption = new CommandOption.Integer(DMRTopicModel.class, "random-seed", "INTEGER", true, 0,
         "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

    static CommandOption.Integer topWordsOption = new CommandOption.Integer(DMRTopicModel.class, "num-top-words", "INTEGER", true, 20,
         "The number of most probable words to print for each topic after model estimation.", null);

    static CommandOption.Integer showTopicsIntervalOption = new CommandOption.Integer(DMRTopicModel.class, "show-topics-interval", "INTEGER", true, 50,
         "The number of iterations between printing a brief summary of the topics so far.", null);

    static CommandOption.Integer optimizeIntervalOption = new CommandOption.Integer(DMRTopicModel.class, "optimize-interval", "INTEGER", true, 20,
         "The number of iterations between reestimating DMR parameters.", null);

    static CommandOption.Integer optimizeBurnInOption = new CommandOption.Integer(DMRTopicModel.class, "optimize-burn-in", "INTEGER", true, 20,
         "The number of iterations to run before first estimating DMR parameters.", null);

    static CommandOption.Double alphaOption = new CommandOption.Double(DMRTopicModel.class, "alpha", "DECIMAL", true, 50.0,
         "Alpha parameter: initial smoothing over topic distribution.",null);

    static CommandOption.Double betaOption = new CommandOption.Double(DMRTopicModel.class, "beta", "DECIMAL", true, 0.01,
         "Beta parameter: smoothing over unigram distribution.",null);

    MaxEnt dmrParameters = null;
    int numFeatures;
    int defaultFeatureIndex;

    Pipe parameterPipe = null;
    
    double[][] alphaCache;
    double[] alphaSumCache;
    
    double initialAlpha = 0.1;
    
    public String parametersFilename = "dmr_parameters.txt";
    public int saveParametersInterval = 0;

    public DMRTopicModel(int numberOfTopics) {
        super(numberOfTopics);
    }

    public void addInstances (InstanceList training) {

        alphabet = training.getDataAlphabet();
        numTypes = alphabet.size();
        
        betaSum = beta * numTypes;

        Randoms random = null;
        if (randomSeed == -1) {
            random = new Randoms();
        }
        else {
            random = new Randoms(randomSeed);
        }

        alphaCache = new double[ training.size() ][numTopics];
        alphaSumCache = new double[ training.size() ];
        
        int index = 0;
        for (Instance instance : training) {
            FeatureSequence tokens = (FeatureSequence) instance.getData();
            LabelSequence topicSequence =
                new LabelSequence(topicAlphabet, new int[ tokens.size() ]);
            
            int[] topics = topicSequence.getFeatures();
            for (int position = 0; position < topics.length; position++) {

                int topic = random.nextInt(numTopics);
                topics[position] = topic;
                
            }

            TopicAssignment t = new TopicAssignment(instance, topicSequence);
            data.add(t);
            
            Arrays.fill(alphaCache[index], initialAlpha);
            alphaSumCache[index] = numTopics * initialAlpha;
            
            index++;
        }
        
        alphaCache = new double[ data.size() ][numTopics];
        alphaSumCache = new double[ data.size() ];
        
        buildInitialTypeTopicCounts();
    
        // Create a "fake" pipe with the features in the data and 
        //  an int-int hashmap of topic counts in the target.
    
        parameterPipe = new Noop();
        Alphabet featureAlphabet = training.getTargetAlphabet();
        numFeatures = featureAlphabet.size() + 1;

        parameterPipe.setDataAlphabet(featureAlphabet);
        parameterPipe.setTargetAlphabet(topicAlphabet);

        dmrParameters = new MaxEnt(parameterPipe, new double[numFeatures * numTopics]);
        defaultFeatureIndex = dmrParameters.getDefaultFeatureIndex();
        double logInitialAlpha = Math.log(initialAlpha);
        for (int topic=0; topic < numTopics; topic++) {
            dmrParameters.setParameter(topic, defaultFeatureIndex, logInitialAlpha);
        }
        
        for (int doc=0; doc < data.size(); doc++) {
            cacheAlphas(data.get(doc).instance, doc);
        }
        
        totalTokens = 0;
        for (int doc = 0; doc < data.size(); doc++) {
            FeatureSequence fs = (FeatureSequence) data.get(doc).instance.getData();
            totalTokens += fs.getLength();
        }
    }


    public void estimate () throws IOException {

        long startTime = System.currentTimeMillis();

        DMRCallable[] callables = new DMRCallable[numThreads];

        int docsPerThread = data.size() / numThreads;
        int offset = 0;

        if (numThreads > 1) {
        
            for (int thread = 0; thread < numThreads; thread++) {
                int[] callableTotals = new int[numTopics];
                System.arraycopy(tokensPerTopic, 0, callableTotals, 0, numTopics);
                
                int[][] callableCounts = new int[numTypes][];
                for (int type = 0; type < numTypes; type++) {
                    int[] counts = new int[typeTopicCounts[type].length];
                    System.arraycopy(typeTopicCounts[type], 0, counts, 0, counts.length);
                    callableCounts[type] = counts;
                }
                
                // some docs may be missing at the end due to integer division
                if (thread == numThreads - 1) {
                    docsPerThread = data.size() - offset;
                }
                
                Randoms random = null;
                if (randomSeed == -1) {
                    random = new Randoms();
                }
                else {
                    random = new Randoms(randomSeed);
                }

                callables[thread] = new DMRCallable(numTopics, this, beta, random, data, callableCounts, callableTotals, offset, docsPerThread);
                
                offset += docsPerThread;
            
            }
        }
        else {
            
            // If there is only one thread, copy the typeTopicCounts
            //  arrays directly, rather than allocating new memory.

            Randoms random = null;
            if (randomSeed == -1) {
                random = new Randoms();
            }
            else {
                random = new Randoms(randomSeed);
            }

            callables[0] = new DMRCallable(numTopics, this, beta, random, data, typeTopicCounts, tokensPerTopic, offset, docsPerThread);

            // If there is only one thread, we 
            //  can avoid communications overhead.
            // This switch informs the thread not to 
            //  gather statistics for its portion of the data.
            callables[0].makeOnlyThread();
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    
        for (int iteration = 1; iteration <= numIterations; iteration++) {

            long iterationStart = System.currentTimeMillis();

            if (showTopicsInterval != 0 && iteration != 0 && iteration % showTopicsInterval == 0) {
                double[] parameters = dmrParameters.getParameters();
                for (int topic = 0; topic < numTopics; topic++) {
                    alpha[topic] = Math.exp(parameters[topic*numFeatures + defaultFeatureIndex]);
                }
                logger.info("\n" + displayTopWords (wordsPerTopic, false));
            }

            if (saveStateInterval != 0 && iteration % saveStateInterval == 0) {
                this.printState(new File(stateFilename + '.' + iteration));
            }

            if (saveModelInterval != 0 && iteration % saveModelInterval == 0) {
                this.write(new File(modelFilename + '.' + iteration));
            }

            if (saveParametersInterval != 0 && iteration % saveParametersInterval == 0) {
                this.writeParameters(new File(parametersFilename + '.' + iteration));
            }

            if (numThreads > 1) {
            
                // Submit callables to thread pool
                
                // The main sampling process.
                int totalChanges = 0;
                try {
                    List<Future<Integer>> futures = executor.invokeAll(Arrays.asList(callables));
                    for (Future<Integer> future: futures) {
                        totalChanges += future.get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Each thread has now become out of synch. Merge all the 
                //  sampling statistics back together.

                // Clear the type/topic counts
                Arrays.fill(tokensPerTopic, 0);
                for (int type = 0; type < numTypes; type++) {
                    int[] targetCounts = typeTopicCounts[type];
                    Arrays.fill(targetCounts, 0);
                }

                for (int thread = 0; thread < numThreads; thread++) {
                    // Handle the total-tokens-per-topic array
                    int[] sourceTotals = callables[thread].getTokensPerTopic();
                    for (int topic = 0; topic < numTopics; topic++) {
                        tokensPerTopic[topic] += sourceTotals[topic];
                    }
                }

                List mergeCallables = new ArrayList();
                for (int thread = 0; thread < numThreads; thread++) {
                    mergeCallables.add(new MergeCallable(callables, typeTopicCounts, numTypes, numTopics, thread, topicMask, topicBits));
                }
                try {
                    List<Future<String>> futures = executor.invokeAll(mergeCallables);
                    for (Future<String> future: futures) {
                        future.get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                //System.out.print("[" + (System.currentTimeMillis() - iterationStart) + "] ");
                
                // Now that we have merged the sampling statistics, propagate 
                //  them back out to the individual threads.

                List copyCallables = new ArrayList();
                for (int thread = 0; thread < numThreads; thread++) {
                    copyCallables.add(new CopyCallable(callables[thread], typeTopicCounts, tokensPerTopic));
                }
                try {
                    List<Future<String>> futures = executor.invokeAll(copyCallables);
                    for (Future<String> future: futures) {
                        future.get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                callables[0].call();
            }

            long elapsedMillis = System.currentTimeMillis() - iterationStart;
            if (elapsedMillis < 1000) {
                logger.fine(elapsedMillis + "ms ");
            }
            else {
                logger.fine((elapsedMillis/1000) + "s ");
            }

            if (iteration > burninPeriod && optimizeInterval != 0 &&
                iteration % optimizeInterval == 0) {

                // Optimize DMR parameters
                learnParameters();
                
                logger.fine("[O " + (System.currentTimeMillis() - iterationStart) + "] ");
            }
            
            if (iteration % 10 == 0) {
                if (printLogLikelihood) {
                    logger.info ("<" + iteration + "> LL/token: " + formatter.format(modelLogLikelihood() / totalTokens));
                }
                else {
                    logger.info ("<" + iteration + ">");
                }
            }
        }

        executor.shutdownNow();
    
        long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
        long minutes = seconds / 60;    seconds %= 60;
        long hours = minutes / 60;    minutes %= 60;
        long days = hours / 24;    hours %= 24;

        StringBuilder timeReport = new StringBuilder();
        timeReport.append("\nTotal time: ");
        if (days != 0) { timeReport.append(days); timeReport.append(" days "); }
        if (hours != 0) { timeReport.append(hours); timeReport.append(" hours "); }
        if (minutes != 0) { timeReport.append(minutes); timeReport.append(" minutes "); }
        timeReport.append(seconds); timeReport.append(" seconds");
        
        logger.info(timeReport.toString());
    }

    /**
     *  Set alpha based on features in an instance
     */
    public void cacheAlphas(Instance instance, int docIndex) {
        
        // we can't use the standard score functions from MaxEnt,
        //  since our features are currently in the Target.
        FeatureVector features = (FeatureVector) instance.getTarget();
        double[] parameters = dmrParameters.getParameters();
        
        alphaSumCache[docIndex] = 0.0;
        
        for (int topic = 0; topic < numTopics; topic++) {
            alphaCache[docIndex][topic] = parameters[topic*numFeatures + defaultFeatureIndex]
                + MatrixOps.rowDotProduct (parameters, numFeatures, topic, features, defaultFeatureIndex, null);
            
            alphaCache[docIndex][topic] = Math.exp(alphaCache[docIndex][topic]);
            alphaSumCache[docIndex] += alphaCache[docIndex][topic];
        }
    }
    

    public void learnParameters() {

        // Create a "fake" pipe with the features in the data and 
        //  an int-int hashmap of topic counts in the target.
        
        if (parameterPipe == null) {
            parameterPipe = new Noop();

            parameterPipe.setDataAlphabet(data.get(0).instance.getTargetAlphabet());
            parameterPipe.setTargetAlphabet(topicAlphabet);
        }

        InstanceList parameterInstances = new InstanceList(parameterPipe);

        if (dmrParameters == null) {
            dmrParameters = new MaxEnt(parameterPipe, new double[numFeatures * numTopics]);
        }

        for (int doc=0; doc < data.size(); doc++) {
            
            if (data.get(doc).instance.getTarget() == null) {
                continue;
            }

            FeatureCounter counter = new FeatureCounter(topicAlphabet);

            for (int topic : data.get(doc).topicSequence.getFeatures()) {
                counter.increment(topic);
            }

            // Put the real target in the data field, and the
            //  topic counts in the target field
            parameterInstances.add( new Instance(data.get(doc).instance.getTarget(), counter.toFeatureVector(), null, null) );

        }

        DMROptimizable optimizable = new DMROptimizable(parameterInstances, dmrParameters);
        optimizable.setRegularGaussianPriorVariance(0.5);
        optimizable.setInterceptGaussianPriorVariance(100.0);

        LimitedMemoryBFGS optimizer = new LimitedMemoryBFGS(optimizable);

        // Optimize once
        try {
            optimizer.optimize();
        } catch (OptimizationException e) {
            // step size too small
        }

        // Restart with a fresh initialization to improve likelihood
        try {
            optimizer.optimize();
        } catch (OptimizationException e) {
            // step size too small
        }
        dmrParameters = optimizable.getClassifier();
        
        for (int doc=0; doc < data.size(); doc++) {
            cacheAlphas(data.get(doc).instance, doc);
        }
    }

    public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {
        super.printTopWords(out, numWords, usingNewLines);
    }

    public void writeParameters(File parameterFile) throws IOException {
        if (dmrParameters != null) {
            PrintStream out = new PrintStream(parameterFile);
            dmrParameters.print(out);
            out.close();
        }
    }
    
    public double modelLogLikelihood() {
        double logLikelihood = 0.0;
        int nonZeroTopics;

        // The likelihood of the model is a combination of a 
        // Dirichlet-multinomial for the words in each topic
        // and a Dirichlet-multinomial for the topics in each
        // document.

        // The likelihood function of a dirichlet multinomial is
        //     Gamma( sum_i alpha_i )     prod_i Gamma( alpha_i + N_i )
        //    prod_i Gamma( alpha_i )      Gamma( sum_i (alpha_i + N_i) )

        // So the log likelihood is 
        //    logGamma ( sum_i alpha_i ) - logGamma ( sum_i (alpha_i + N_i) ) + 
        //     sum_i [ logGamma( alpha_i + N_i) - logGamma( alpha_i ) ]

        // Do the documents first

        int[] topicCounts = new int[numTopics];
        int[] docTopics;

        for (int doc=0; doc < data.size(); doc++) {
            LabelSequence topicSequence =    (LabelSequence) data.get(doc).topicSequence;
            FeatureVector features = (FeatureVector) data.get(doc).instance.getTarget();
        
            double[] parameters = dmrParameters.getParameters();

            docTopics = topicSequence.getFeatures();

            for (int token=0; token < docTopics.length; token++) {
                topicCounts[ docTopics[token] ]++;
            }

            for (int topic=0; topic < numTopics; topic++) {
                if (topicCounts[topic] > 0) {
                    logLikelihood += (Dirichlet.logGammaStirling(alphaCache[doc][topic] + topicCounts[topic]) -
                                      Dirichlet.logGammaStirling(alphaCache[doc][topic]));
                }
            }

            // subtract the (count + parameter) sum term
            logLikelihood -= Dirichlet.logGammaStirling(alphaSumCache[doc] + docTopics.length) + Dirichlet.logGammaStirling(alphaSumCache[doc]);

            Arrays.fill(topicCounts, 0);
        }

        // And the topics

        // Count the number of type-topic pairs that are not just (logGamma(beta) - logGamma(beta))
        int nonZeroTypeTopics = 0;

        for (int type=0; type < numTypes; type++) {
            // reuse this array as a pointer

            topicCounts = typeTopicCounts[type];

            int index = 0;
            while (index < topicCounts.length &&
                   topicCounts[index] > 0) {
                int topic = topicCounts[index] & topicMask;
                int count = topicCounts[index] >> topicBits;
                
                nonZeroTypeTopics++;
                logLikelihood += Dirichlet.logGammaStirling(beta + count);

                if (Double.isNaN(logLikelihood)) {
                    logger.warning("NaN in log likelihood calculation");
                    return 0;
                }
                else if (Double.isInfinite(logLikelihood)) {
                    logger.warning("infinite log likelihood");
                    return 0;
                }

                index++;
            }
        }
    
        for (int topic=0; topic < numTopics; topic++) {
            logLikelihood -= 
                Dirichlet.logGammaStirling( (beta * numTypes) +
                                            tokensPerTopic[ topic ] );

            if (Double.isNaN(logLikelihood)) {
                logger.info("NaN after topic " + topic + " " + tokensPerTopic[ topic ]);
                return 0;
            }
            else if (Double.isInfinite(logLikelihood)) {
                logger.info("Infinite value after topic " + topic + " " + tokensPerTopic[ topic ]);
                return 0;
            }

        }
    
        // logGamma(|V|*beta) for every topic
        logLikelihood += 
            Dirichlet.logGammaStirling(beta * numTypes) * numTopics;

        // logGamma(beta) for all type/topic pairs with non-zero count
        logLikelihood -=
            Dirichlet.logGammaStirling(beta) * nonZeroTypeTopics;
        
        if (Double.isNaN(logLikelihood)) {
            logger.info("at the end");
        }
        else if (Double.isInfinite(logLikelihood)) {
            logger.info("Infinite value beta " + beta + " * " + numTypes);
            return 0;
        }

        return logLikelihood;
    }
    
    /** Return a tool for estimating topic distributions for new documents */
    public DMRInferencer getInferencer() {
        return new DMRInferencer(typeTopicCounts, tokensPerTopic, dmrParameters, alphabet, beta, betaSum);
    }

    // Serialization

    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    private static final int NULL_INTEGER = -1;

    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt (CURRENT_SERIAL_VERSION);
        
        out.writeObject(dmrParameters);

        out.writeObject(data);
        out.writeObject(alphabet);
        out.writeObject(topicAlphabet);

        out.writeInt(numTopics);

        out.writeInt(topicMask);
        out.writeInt(topicBits);

        out.writeInt(numTypes);

        out.writeObject(alpha);
        out.writeDouble(alphaSum);
        out.writeDouble(beta);
        out.writeDouble(betaSum);

        out.writeObject(typeTopicCounts);
        out.writeObject(tokensPerTopic);

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

        out.writeInt(randomSeed);
        out.writeObject(formatter);
        out.writeBoolean(printLogLikelihood);

        out.writeInt(numThreads);
    }

    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        
        int version = in.readInt ();

        dmrParameters = (MaxEnt) in.readObject();
        numFeatures = dmrParameters.getAlphabet().size();
        defaultFeatureIndex = dmrParameters.getDefaultFeatureIndex();

        data = (ArrayList<TopicAssignment>) in.readObject ();
        alphabet = (Alphabet) in.readObject();
        topicAlphabet = (LabelAlphabet) in.readObject();
        
        numTopics = in.readInt();
        
        topicMask = in.readInt();
        topicBits = in.readInt();
        
        numTypes = in.readInt();
        
        alpha = (double[]) in.readObject();
        alphaSum = in.readDouble();
        beta = in.readDouble();
        betaSum = in.readDouble();
        
        typeTopicCounts = (int[][]) in.readObject();
        tokensPerTopic = (int[]) in.readObject();
        
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
        
        randomSeed = in.readInt();
        formatter = (NumberFormat) in.readObject();
        printLogLikelihood = in.readBoolean();

        numThreads = in.readInt();
    }

    public void write (File serializedModelFile) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(serializedModelFile));
            oos.writeObject(this);
            oos.close();
        } catch (IOException e) {
            System.err.println("Problem serializing DMRTopicModel to file " +
                               serializedModelFile + ": " + e);
        }
    }

    public static DMRTopicModel read (File f) throws Exception {

        DMRTopicModel topicModel = null;

        ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
        topicModel = (DMRTopicModel) ois.readObject();
        ois.close();

        return topicModel;
    }
    
    public static void main (String[] args) throws IOException {

        CommandOption.setSummary (DMRTopicModel.class,
                                  "A tool for estimating, saving and printing diagnostics for topic models with arbitrary document features.");
        CommandOption.process (DMRTopicModel.class, args);

        if (! inputFile.wasInvoked()) {
            System.err.println("You must specify a data set with the --input option.");
            System.exit(1);
        }

        DMRTopicModel topicModel = null;

        if (inputModelFilename.value != null) {

            try {
                topicModel = DMRTopicModel.read(new File(inputModelFilename.value));
            } catch (Exception e) {
                System.err.println("Unable to restore saved topic model " + 
                                   inputModelFilename.value + ": " + e);
                System.exit(1);
            }
        }
        else {
            InstanceList training = InstanceList.load(new File(inputFile.value));

            System.out.println ("Data loaded.");
        
            // For historical reasons we currently only support FeatureSequence data,
            //  not the FeatureVector, which is the default for the input functions.
            //  Provide a warning to avoid ClassCastExceptions.
            if (training.size() > 0 &&
                training.get(0) != null) {
                Object data = training.get(0).getData();
                if (! (data instanceof FeatureSequence)) {
                    System.err.println("Topic modeling currently only supports feature sequences: use --keep-sequence option when importing data.");
                    System.exit(1);
                }
            }
            
            topicModel = new DMRTopicModel (numTopicsOption.value);
            if (randomSeedOption.value != 0) {
                topicModel.setRandomSeed(randomSeedOption.value);
            }
            
            topicModel.addInstances(training);
        }

        topicModel.setNumThreads(numThreadsOption.value);
        topicModel.setTopicDisplay(showTopicsIntervalOption.value, topWordsOption.value);

        topicModel.setNumIterations(numIterationsOption.value);
        topicModel.setOptimizeInterval(optimizeIntervalOption.value);
        topicModel.setBurninPeriod(optimizeBurnInOption.value);

        if (outputStateIntervalOption.value != 0) {
            topicModel.setSaveState(outputStateIntervalOption.value, stateFile.value);
        }
        
        topicModel.parametersFilename = outputParametersFilename.value;
        topicModel.saveParametersInterval = outputParametersIntervalOption.value;

        topicModel.estimate();

        topicModel.writeParameters(new File(outputParametersFilename.value));

        if (topicKeysFile.value != null) {
            topicModel.printTopWords(new File(topicKeysFile.value), topWordsOption.value, false);
        }

        if (stateFile.value != null) {
            topicModel.printState (new File(stateFile.value));
        }

        if (docTopicsFile.value != null) {
            PrintWriter out = new PrintWriter (new FileWriter ((new File(docTopicsFile.value))));
            topicModel.printDocumentTopics(out, docTopicsThreshold.value, docTopicsMax.value);
            out.close();
        }

        if (inferencerFilename.value != null) {
            DMRInferencer inferencer = topicModel.getInferencer();
            
            try {
                ObjectOutputStream oos =
                    new ObjectOutputStream(new FileOutputStream(inferencerFilename.value));
                oos.writeObject(topicModel.getInferencer());
                oos.close();
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }

        if (outputModelFilename.value != null) {
            assert (topicModel != null);
            
            topicModel.write(new File(outputModelFilename.value));
        }

    }
}