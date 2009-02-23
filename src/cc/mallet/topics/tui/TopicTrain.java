package cc.mallet.topics.tui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;

import cc.mallet.topics.LDAStream;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;

//This TUI first train a topic model, and save the model to modelFile
public class TopicTrain {

	/**
	 * @param args
	 */
	//inputFile for training sequence, testFile for testing sequence, the two should share the same Alphabets
	static CommandOption.String inputFile = new CommandOption.String
	(TopicTrain.class, "input", "FILENAME", true, null,
	 "The filename from which to read the list of training instances.  Use - for stdin.  " +
	 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

	static CommandOption.String testFile = new CommandOption.String
	(TopicTrain.class, "test", "FILENAME", true, null,
	 "The filename from which to read the list of training instances.  Use - for stdin.  " +
	 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

    static CommandOption.String outputModelFilename = new CommandOption.String
	(TopicTrain.class, "output-model", "FILENAME", true, null,
	 "The filename in which to write the binary topic model at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String inputModelFilename = new CommandOption.String
	(TopicTrain.class, "input-model", "FILENAME", true, null,
	 "The filename from which to read the binary topic model to which the --input will be appended, " +
	 "allowing incremental training.  " +
	 "By default this is null, indicating that no file will be read.", null);

    static CommandOption.String stateFile = new CommandOption.String
	(TopicTrain.class, "output-state", "FILENAME", true, null,
	 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String stateTestFile = new CommandOption.String
	(TopicTrain.class, "output-state-test", "FILENAME", true, null,
	 "The filename in which to write the Gibbs sampling state for test after at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String topicKeysFile = new CommandOption.String
	(TopicTrain.class, "output-topic-keys", "FILENAME", true, null,
     "The filename in which to write the top words for each topic and any Dirichlet parameters.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String topicTypesFile = new CommandOption.String
	(TopicTrain.class, "output-type-topics", "FILENAME", true, null,
     "The filename in which to write the matrix of phi, the probability of each type/word belonging to each topic " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String docTopicsFile = new CommandOption.String
	(TopicTrain.class, "output-doc-topics", "FILENAME", true, null,
	 "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String testDocTopicsFile = new CommandOption.String
	(TopicTrain.class, "output-testdoc-topics", "inf.theta", true, null,
	 "The filename in which to write the topic proportions per test document, at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String serialTestDocTopicsFile = new CommandOption.String
	(TopicTrain.class, "output-testdoc-serial", "inf.theta", true, null,
	 "The filename in which to write the topic proportions per test document, at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.Double docTopicsThreshold = new CommandOption.Double
	(TopicTrain.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
	 "When writing topic proportions per document with --output-doc-topics, " +
	 "do not print topics with proportions less than this threshold value.", null);

    static CommandOption.Integer docTopicsMax = new CommandOption.Integer
	(TopicTrain.class, "doc-topics-max", "INTEGER", true, -1,
	 "When writing topic proportions per document with --output-doc-topics, " +
	 "do not print more than INTEGER number of topics.  "+
	 "A negative value indicates that all topics should be printed.", null);

    static CommandOption.Integer numTopics = new CommandOption.Integer
	(TopicTrain.class, "num-topics", "INTEGER", true, 10,
	 "The number of topics to fit.", null);

    static CommandOption.Integer numIterations = new CommandOption.Integer
	(TopicTrain.class, "num-iterations", "INTEGER", true, 1000,
	 "The number of iterations of Gibbs sampling.", null);

    static CommandOption.Integer infIterations = new CommandOption.Integer
	(TopicTrain.class, "inf-iterations", "INTEGER", true, 1000,
	 "The number of iterations of Gibbs sampling.", null);

    //you should design this argument to call inferenceOneByOne
    static CommandOption.Integer infOneByOne = new CommandOption.Integer
	(TopicTrain.class, "inf-onebyone", "INTEGER", true, 0,
	 "The number of iterations of Gibbs sampling.", null);

    //you must design this argument to do inferenceAll, usually it is not needed
    static CommandOption.Integer infAllIterations = new CommandOption.Integer
	(TopicTrain.class, "inf-all-iterations", "INTEGER", true, 0,
	 "The number of inference iterations of Gibbs sampling.", null);


    static CommandOption.Integer randomSeed = new CommandOption.Integer
	(TopicTrain.class, "random-seed", "INTEGER", true, 0,
	 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

    static CommandOption.Integer topWords = new CommandOption.Integer
	(TopicTrain.class, "num-top-words", "INTEGER", true, 20,
	 "The number of most probable words to print for each topic after model estimation.", null);

    static CommandOption.Integer showTopicsInterval = new CommandOption.Integer
	(TopicTrain.class, "show-topics-interval", "INTEGER", true, 50,
	 "The number of iterations between printing a brief summary of the topics so far.", null);

    static CommandOption.Integer outputModelInterval = new CommandOption.Integer
	(TopicTrain.class, "output-model-interval", "INTEGER", true, 0,
	 "The number of iterations between writing the model (and its Gibbs sampling state) to a binary file.  " +
	 "You must also set the --output-model to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Integer outputStateInterval = new CommandOption.Integer
    (TopicTrain.class, "output-state-interval", "INTEGER", true, 0,
     "The number of iterations between writing the sampling state to a text file.  " +
     "You must also set the --output-state to use this option, whose argument will be the prefix of the filenames.", null);

    static CommandOption.Double alpha = new CommandOption.Double
	(TopicTrain.class, "alpha", "DECIMAL", true, 50.0,
	 "Alpha parameter: smoothing over topic distribution.",null);

    static CommandOption.Double beta = new CommandOption.Double
	(TopicTrain.class, "beta", "DECIMAL", true, 0.01,
	 "Beta parameter: smoothing over unigram distribution.",null);


	public static void main(String[] args) throws java.io.IOException
	{
		// TODO Auto-generated method stub
		// Process the command-line options
		CommandOption.setSummary (TopicTrain.class,
								  "A tool for training and test streamline topic model.");
		CommandOption.process (TopicTrain.class, args);

		LDAStream lda = null;

		if (inputFile.value != null) {
			InstanceList instances = InstanceList.load (new File(inputFile.value));
			System.out.println ("Training Data loaded.");
			lda=new LDAStream(numTopics.value, alpha.value, beta.value);
			lda.addInstances(instances);
		}
		if(testFile.value != null) {
			InstanceList testing = InstanceList.load(new File(testFile.value));
			lda.setTestingInstances(testing);
		}	
		
 		lda.setTopicDisplay(showTopicsInterval.value, topWords.value);

		if (outputModelInterval.value != 0) {
			lda.setModelOutput(outputModelInterval.value, outputModelFilename.value);
		}

		lda.setNumIterations(numIterations.value);

        if (randomSeed.value != 0) {
            lda.setRandomSeed(randomSeed.value);
        }

        if (outputStateInterval.value != 0) {
            lda.setSaveState(outputStateInterval.value, stateFile.value);
        }

		lda.estimate();
		//save the model, we need typeTopicCounts and tokensPerTopic for empirical likelihood
		lda.write(new File (inputFile.value + ".model"));

		if (topicKeysFile.value != null) {
			lda.printTopWords(new File(topicKeysFile.value), topWords.value, false);
		}
		if (topicKeysFile.value != null) {
			lda.printTopWords(new File(topicKeysFile.value), topWords.value, false);
		}
		if (topicTypesFile.value != null) {
			lda.printPhi(new File(topicTypesFile.value), 1e-4);
		}
		if (stateFile.value != null) {
			lda.printState (lda.getData(), new File(stateFile.value));
		}
		if (docTopicsFile.value != null) {
			lda.printDocumentTopics(lda.getData(), new PrintWriter (new FileWriter ((new File(docTopicsFile.value)))),
									docTopicsThreshold.value, docTopicsMax.value);

		}

	
 	}

}
