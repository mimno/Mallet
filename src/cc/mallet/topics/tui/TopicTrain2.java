package cc.mallet.topics.tui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;

import cc.mallet.topics.LDAStream;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;

public class TopicTrain2 {

	/**
	 * This class is designed for three inference methods
	 * Read modelfile and testfile, modelfile includes train instances
	 * From modelfile, we get topic number, values of parameters and so on
	 * commented by Limin Yao
	 * @param args
	 */
	static CommandOption.String modelFile = new CommandOption.String
	(TopicTrain2.class, "model", "FILENAME", true, null,
	 "The filename from which to read the trained topic model.  Use - for stdin.  " +
	 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

	static CommandOption.String dataFile = new CommandOption.String
	(TopicTrain2.class, "data", "FILENAME", true, null,
	 "The filename from which to read all instances.  Use - for stdin.  " +
	 "The instances must be FeatureSequence, each corresponds to a topic", null);

	static CommandOption.String seqFile = new CommandOption.String
	(TopicTrain2.class, "sequence", "FILENAME", true, null,
	 "The filename from which to read testing instances.  Use - for stdin.  " +
	 "The instances must be FeatureSequence, each corresponds to a test document", null);

/*	static CommandOption.String testFile = new CommandOption.String
	(TopicTrain2.class, "test", "FILENAME", true, null,
	 "The filename from which to store testing instances.  Use - for stdin.  " +
	 "The instances must be FeatureVector, each corresponds to a test document", null);*/

	 static CommandOption.Integer infIterations = new CommandOption.Integer
		(TopicTrain2.class, "inf-iterations", "INTEGER", true, 0,
		 "The number of iterations of Gibbs sampling.", null);

	 //you must design this argument to do inferenceAll, usually it is not needed
    static CommandOption.Integer infAllIterations = new CommandOption.Integer
	(TopicTrain2.class, "inf-all-iterations", "INTEGER", true, 0,
	 "The number of inference iterations of Gibbs sampling.", null);

    //you should design this argument to call inferenceOneByOne
    static CommandOption.Integer infOneByOne = new CommandOption.Integer
	(TopicTrain2.class, "inf-onebyone", "INTEGER", true, 0,
	 "The number of iterations of Gibbs sampling.", null);

    static CommandOption.Integer numTopics = new CommandOption.Integer
	(TopicTrain2.class, "num-topics", "INTEGER", true, 10,
	 "The number of topics to fit.", null);

    static CommandOption.Integer numIterations = new CommandOption.Integer
	(TopicTrain2.class, "num-iterations", "INTEGER", true, 1000,
	 "The number of iterations of Gibbs sampling.", null);

    static CommandOption.Double alpha = new CommandOption.Double
	(TopicTrain2.class, "alpha", "DECIMAL", true, 50.0,
	 "Alpha parameter: smoothing over topic distribution.",null);

    static CommandOption.Double beta = new CommandOption.Double
	(TopicTrain2.class, "beta", "DECIMAL", true, 0.01,
	 "Beta parameter: smoothing over unigram distribution.",null);

    static CommandOption.String stateFile = new CommandOption.String
	(TopicTrain2.class, "output-state", "FILENAME", true, null,
	 "The filename in which to write the Gibbs sampling state after at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String stateTestFile = new CommandOption.String
	(TopicTrain2.class, "output-state-test", "FILENAME", true, null,
	 "The filename in which to write the Gibbs sampling state for test after at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String topicKeysFile = new CommandOption.String
	(TopicTrain2.class, "output-topic-keys", "FILENAME", true, null,
     "The filename in which to write the top words for each topic and any Dirichlet parameters.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String topicTypesFile = new CommandOption.String
	(TopicTrain2.class, "output-type-topics", "FILENAME", true, null,
     "The filename in which to write the matrix of phi, the probability of each type/word belonging to each topic " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String docTopicsFile = new CommandOption.String
	(TopicTrain2.class, "output-doc-topics", "FILENAME", true, null,
	 "The filename in which to write the topic proportions per document, at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String testDocTopicsFile = new CommandOption.String
	(TopicTrain2.class, "output-testdoc-topics", "inf.theta", true, null,
	 "The filename in which to write the topic proportions per test document, at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.String serialTestDocTopicsFile = new CommandOption.String
	(TopicTrain2.class, "output-testdoc-serial", "inf.theta", true, null,
	 "The filename in which to write the topic proportions per test document, at the end of the iterations.  " +
	 "By default this is null, indicating that no file will be written.", null);

    static CommandOption.Double docTopicsThreshold = new CommandOption.Double
	(TopicTrain2.class, "doc-topics-threshold", "DECIMAL", true, 0.0,
	 "When writing topic proportions per document with --output-doc-topics, " +
	 "do not print topics with proportions less than this threshold value.", null);

    static CommandOption.Integer docTopicsMax = new CommandOption.Integer
	(TopicTrain2.class, "doc-topics-max", "INTEGER", true, -1,
	 "When writing topic proportions per document with --output-doc-topics, " +
	 "do not print more than INTEGER number of topics.  "+
	 "A negative value indicates that all topics should be printed.", null);

    static CommandOption.Integer topWords = new CommandOption.Integer
	(TopicTrain2.class, "num-top-words", "INTEGER", true, 20,
	 "The number of most probable words to print for each topic after model estimation.", null);


	public static void main(String[] args) throws IOException, ClassNotFoundException {
		// TODO Auto-generated method stub
		// Process the command-line options
		CommandOption.setSummary (TopicTrain2.class,
								  "A tool for training and test streamline topic model.");
		CommandOption.process (TopicTrain2.class, args);

	//	InstanceList training = InstanceList.load(new File (dataFile.value));

		InstanceList testing = InstanceList.load(new File (seqFile.value));

	/*	for (Instance instance:training) {
			if(testing.contains(instance)) {
				training.remove(instance);
			}
		}

		training.save(new File ("nips.train.seq"));*/
		LDAStream lda = null;
		lda = new LDAStream(numTopics.value, alpha.value, beta.value);
		//we get data (Topication)

		lda.loadModel(new ObjectInputStream (new FileInputStream (modelFile.value)));  //results of burn-in period estimation

    	lda.setTestingInstances(testing);

    	if(infIterations.value != 0) {
    		//sampling method 2
    		System.out.print("Inference started!\n");
    		lda.inference(infIterations.value);

    		if (stateTestFile.value != null) {
    			lda.printState (lda.getTest(), new File(stateTestFile.value));
    		}
    		if (testDocTopicsFile.value != null) {
    			lda.printDocumentTopics(lda.getTest(), new PrintWriter (new FileWriter ((new File(testDocTopicsFile.value)))),
    									docTopicsThreshold.value, docTopicsMax.value);
    			lda.printSerialDocumentTopics(lda.getTest(), new File(testDocTopicsFile.value + ".mallet"),
    					docTopicsThreshold.value, docTopicsMax.value);
    			//
    		}
    		System.out.print("save inference output completed!\n");
    	}
    	if(infAllIterations.value != 0) {
			//all, sample1

			System.out.print("Inference started!\n");
			lda.inferenceAll(infAllIterations.value);

			System.out.print("Re-estimation started!\n");
			lda.estimateAll(infAllIterations.value);
			if (topicKeysFile.value != null) {
				lda.printTopWords(new File(topicKeysFile.value), topWords.value, false);
			}
			if (topicTypesFile.value != null) {
				lda.printTopicWords(new File(topicTypesFile.value), false, 1e-4);
			}
			if (stateFile.value != null) {
				lda.printState (lda.getData(), new File(stateFile.value));
			}

			if (docTopicsFile.value != null) {
				lda.printDocumentTopics(lda.getData(), new PrintWriter (new FileWriter ((new File(docTopicsFile.value)))),
										docTopicsThreshold.value, docTopicsMax.value);
				lda.printSerialDocumentTopics(lda.getData(), new File(docTopicsFile.value + ".mallet"),
						docTopicsThreshold.value, docTopicsMax.value);

			}

			//save the model, we need typeTopicCounts and tokensPerTopic for empirical likelihood
			lda.writeModel(new ObjectOutputStream (new FileOutputStream (dataFile.value + "all.model")));

			return;
		}

		if(infOneByOne.value != 0) {
			// sample3
			System.out.print("inference started!\n");
			lda.inferenceOneByOne(infOneByOne.value);

			if (stateTestFile.value != null) {
				lda.printState (lda.getTest(), new File(stateTestFile.value));
			}
			if (testDocTopicsFile.value != null) {
				lda.printDocumentTopics(lda.getTest(), new PrintWriter (new FileWriter ((new File(testDocTopicsFile.value)))),
										docTopicsThreshold.value, docTopicsMax.value);
				lda.printSerialDocumentTopics(lda.getTest(), new File(testDocTopicsFile.value + ".mallet"),
						docTopicsThreshold.value, docTopicsMax.value);
				//
			}
			System.out.print("save inference output completed!\n");

		}
	}

}
