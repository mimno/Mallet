/**
 * Implement different Gibbs sampling based inference methods
 */
package cc.mallet.topics;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import cc.mallet.types.FeatureCounter;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.util.Randoms;
import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.cursors.IntIntCursor;

/**
 * @author Limin Yao, David Mimno
 *
 */
public class LDAStream extends LDAHyper {

	protected ArrayList<Topication> test; // the test instances and their topic assignments
	/**
	 * @param numberOfTopics
	 */
	public LDAStream(int numberOfTopics) {
		super(numberOfTopics);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param numberOfTopics
	 * @param alphaSum
	 * @param beta
	 */
	public LDAStream(int numberOfTopics, double alphaSum, double beta) {
		super(numberOfTopics, alphaSum, beta);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param numberOfTopics
	 * @param alphaSum
	 * @param beta
	 * @param random
	 */
	public LDAStream(int numberOfTopics, double alphaSum, double beta,
			Randoms random) {
		super(numberOfTopics, alphaSum, beta, random);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param topicAlphabet
	 * @param alphaSum
	 * @param beta
	 * @param random
	 */
	public LDAStream(LabelAlphabet topicAlphabet, double alphaSum, double beta,
			Randoms random) {
		super(topicAlphabet, alphaSum, beta, random);
		// TODO Auto-generated constructor stub
	}

	public ArrayList<Topication> getTest() { return test; }
	
	//first training a topic model on training data,
	//inference on test data, count typeTopicCounts
	// re-sampling on all data
	public void inferenceAll(int maxIteration){
		this.test = new ArrayList<Topication>();  //initialize test
		//initial sampling on testdata
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : testing) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				//sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				FeatureSequence fs = (FeatureSequence) instance.getData();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					int type = fs.getIndexAtPosition(i);
					topics[i] = r.nextInt(numTopics);
					typeTopicCounts[type].putOrAdd(topics[i], 1, 1);
				    tokensPerTopic[topics[i]]++;
				}
			}
			topicSequences.add (topicSequence);
		}

		//construct test
		assert (testing.size() == topicSequences.size());
		for (int i = 0; i < testing.size(); i++) {
			Topication t = new Topication (testing.get(i), this, topicSequences.get(i));
			test.add (t);
		}

		long startTime = System.currentTimeMillis();
		//loop
		int iter = 0;
		for ( ; iter <= maxIteration; iter++) {
			if(iter%100==0)
			{
				System.out.print("Iteration: " + iter);
				System.out.println();
			}
			int numDocs = test.size(); // TODO
			for (int di = 0; di < numDocs; di++) {
				FeatureSequence tokenSequence = (FeatureSequence) test.get(di).instance.getData();
				LabelSequence topicSequence = test.get(di).topicSequence;
				sampleTopicsForOneTestDocAll (tokenSequence, topicSequence);
			}
		}

		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal inferencing time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}

	//called by inferenceAll, using unseen words in testdata
	private void sampleTopicsForOneTestDocAll(FeatureSequence tokenSequence,
			LabelSequence topicSequence) {
		// TODO Auto-generated method stub
		int[] oneDocTopics = topicSequence.getFeatures();

		IntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double tw;
		double[] topicWeights = new double[numTopics];
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		//		populate topic counts
		int[] localTopicCounts = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++){
			localTopicCounts[ti] = 0;
		}
		for (int position = 0; position < docLength; position++) {
			localTopicCounts[oneDocTopics[position]] ++;
		}

		// Iterate over the positions (words) in the document
		for (int si = 0; si < docLength; si++) {
			type = tokenSequence.getIndexAtPosition(si);
			oldTopic = oneDocTopics[si];

			// Remove this token from all counts
			localTopicCounts[oldTopic] --;

			currentTypeTopicCounts = typeTopicCounts[type];
			assert(currentTypeTopicCounts.get(oldTopic) >= 0);

			if (currentTypeTopicCounts.get(oldTopic) == 1) {
				currentTypeTopicCounts.remove(oldTopic);
			}
			else {
				currentTypeTopicCounts.addTo(oldTopic, -1);
			}
			tokensPerTopic[oldTopic]--;

			// Build a distribution over topics for this token
			Arrays.fill (topicWeights, 0.0);
			topicWeightsSum = 0;

			for (int ti = 0; ti < numTopics; ti++) {
				tw = ((currentTypeTopicCounts.get(ti) + beta) / (tokensPerTopic[ti] + betaSum))
				      * ((localTopicCounts[ti] + alpha[ti])); // (/docLen-1+tAlpha); is constant across all topics
				topicWeightsSum += tw;
				topicWeights[ti] = tw;
			}
			// Sample a topic assignment from this distribution
			newTopic = random.nextDiscrete (topicWeights, topicWeightsSum);

			// Put that new topic into the counts
			oneDocTopics[si] = newTopic;
			currentTypeTopicCounts.putOrAdd(newTopic, 1, 1);
			localTopicCounts[newTopic] ++;
			tokensPerTopic[newTopic]++;
		}
	}
	
	//what do we have:
	//typeTopicCounts, tokensPerTopic, topic-sequence of training and test data
	public void estimateAll(int iteration) throws IOException {
		//re-Gibbs sampling on all data
		data.addAll(test);
		initializeHistogramsAndCachedValues();
		estimate(iteration);
	}

	//inference on testdata, one problem is how to deal with unseen words
	//unseen words is in the Alphabet, but typeTopicsCount entry is null
	//added by Limin Yao
	/**
	 * @param maxIteration
	 * @param
	 */
	public void inference(int maxIteration){
		this.test = new ArrayList<Topication>();  //initialize test
		//initial sampling on testdata
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : testing) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				//sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				FeatureSequence fs = (FeatureSequence) instance.getData();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					int type = fs.getIndexAtPosition(i);
					topics[i] = r.nextInt(numTopics);
				/*	if(typeTopicCounts[type].size() != 0) {
						topics[i] = r.nextInt(numTopics);
					} else {
						topics[i] = -1;  // for unseen words
					}*/
				}
			}
			topicSequences.add (topicSequence);
		}

		//construct test
		assert (testing.size() == topicSequences.size());
		for (int i = 0; i < testing.size(); i++) {
			Topication t = new Topication (testing.get(i), this, topicSequences.get(i));
			test.add (t);
			// Include sufficient statistics for this one doc
			// add count on new data to n[k][w] and n[k][*]
			// pay attention to unseen words
			FeatureSequence tokenSequence = (FeatureSequence) t.instance.getData();
			LabelSequence topicSequence = t.topicSequence;
			for (int pi = 0; pi < topicSequence.getLength(); pi++) {
				int topic = topicSequence.getIndexAtPosition(pi);
				int type = tokenSequence.getIndexAtPosition(pi);
				if(topic != -1) // type seen in training
				{
					typeTopicCounts[type].putOrAdd(topic, 1, 1);
				    tokensPerTopic[topic]++;
				}
			}
		}

		long startTime = System.currentTimeMillis();
		//loop
		int iter = 0;
		for ( ; iter <= maxIteration; iter++) {
			if(iter%100==0)
			{
				System.out.print("Iteration: " + iter);
				System.out.println();
			}
			int numDocs = test.size(); // TODO
			for (int di = 0; di < numDocs; di++) {
				FeatureSequence tokenSequence = (FeatureSequence) test.get(di).instance.getData();
				LabelSequence topicSequence = test.get(di).topicSequence;
				sampleTopicsForOneTestDoc (tokenSequence, topicSequence);
			}
		}

		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal inferencing time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}

	private void sampleTopicsForOneTestDoc(FeatureSequence tokenSequence,
			LabelSequence topicSequence) {
		// TODO Auto-generated method stub
		int[] oneDocTopics = topicSequence.getFeatures();

		IntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double tw;
		double[] topicWeights = new double[numTopics];
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		//		populate topic counts
		int[] localTopicCounts = new int[numTopics];
		for (int ti = 0; ti < numTopics; ti++){
			localTopicCounts[ti] = 0;
		}
		for (int position = 0; position < docLength; position++) {
			if(oneDocTopics[position] != -1) {
				localTopicCounts[oneDocTopics[position]] ++;
			}
		}

		// Iterate over the positions (words) in the document
		for (int si = 0; si < docLength; si++) {
			type = tokenSequence.getIndexAtPosition(si);
			oldTopic = oneDocTopics[si];
			if(oldTopic == -1) {
				continue;
			}

			// Remove this token from all counts
     		localTopicCounts[oldTopic] --;
     		currentTypeTopicCounts = typeTopicCounts[type];
			assert(currentTypeTopicCounts.get(oldTopic) >= 0);

			if (currentTypeTopicCounts.get(oldTopic) == 1) {
				currentTypeTopicCounts.remove(oldTopic);
			}
			else {
				currentTypeTopicCounts.addTo(oldTopic, -1);
			}
			tokensPerTopic[oldTopic]--;

			// Build a distribution over topics for this token
			Arrays.fill (topicWeights, 0.0);
			topicWeightsSum = 0;

			for (int ti = 0; ti < numTopics; ti++) {
				tw = ((currentTypeTopicCounts.get(ti) + beta) / (tokensPerTopic[ti] + betaSum))
				      * ((localTopicCounts[ti] + alpha[ti])); // (/docLen-1+tAlpha); is constant across all topics
				topicWeightsSum += tw;
				topicWeights[ti] = tw;
			}
			// Sample a topic assignment from this distribution
			newTopic = random.nextDiscrete (topicWeights, topicWeightsSum);

			// Put that new topic into the counts
			oneDocTopics[si] = newTopic;
			currentTypeTopicCounts.putOrAdd(newTopic, 1, 1);
			localTopicCounts[newTopic] ++;
			tokensPerTopic[newTopic]++;
		}
	}

	//inference method 3, for each doc, for each iteration, for each word
	//compare against inference(that is method2): for each iter, for each doc, for each word
	public void inferenceOneByOne(int maxIteration){
		this.test = new ArrayList<Topication>();  //initialize test
		//initial sampling on testdata
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : testing) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				//sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				FeatureSequence fs = (FeatureSequence) instance.getData();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					int type = fs.getIndexAtPosition(i);
					topics[i] = r.nextInt(numTopics);
					typeTopicCounts[type].putOrAdd(topics[i], 1, 1);
					tokensPerTopic[topics[i]]++;
				/*	if(typeTopicCounts[type].size() != 0) {
						topics[i] = r.nextInt(numTopics);
						typeTopicCounts[type].putOrAdd(topics[i], 1, 1);
						tokensPerTopic[topics[i]]++;
					} else {
						topics[i] = -1;  // for unseen words
					}*/
				}
			}
			topicSequences.add (topicSequence);
		}

		//construct test
		assert (testing.size() == topicSequences.size());
		for (int i = 0; i < testing.size(); i++) {
			Topication t = new Topication (testing.get(i), this, topicSequences.get(i));
			test.add (t);
		}

		long startTime = System.currentTimeMillis();
		//loop
		int iter = 0;
		int numDocs = test.size(); // TODO
		for (int di = 0; di < numDocs; di++) {
			iter = 0;
			FeatureSequence tokenSequence = (FeatureSequence) test.get(di).instance.getData();
			LabelSequence topicSequence = test.get(di).topicSequence;
			for( ; iter <= maxIteration; iter++) {
				sampleTopicsForOneTestDoc (tokenSequence, topicSequence);
			}
			if(di%100==0)
			{
				System.out.print("Docnum: " + di);
				System.out.println();
			}
		}

		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal inferencing time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}
	
	public void inferenceWithTheta(int maxIteration, InstanceList theta){
		this.test = new ArrayList<Topication>();  //initialize test
		//initial sampling on testdata
		ArrayList<LabelSequence> topicSequences = new ArrayList<LabelSequence>();
		for (Instance instance : testing) {
			LabelSequence topicSequence = new LabelSequence(topicAlphabet, new int[instanceLength(instance)]);
			if (false) {
				// This method not yet obeying its last "false" argument, and must be for this to work
				//sampleTopicsForOneDoc((FeatureSequence)instance.getData(), topicSequence, false, false);
			} else {
				Randoms r = new Randoms();
				FeatureSequence fs = (FeatureSequence) instance.getData();
				int[] topics = topicSequence.getFeatures();
				for (int i = 0; i < topics.length; i++) {
					int type = fs.getIndexAtPosition(i);
					topics[i] = r.nextInt(numTopics);
				}
			}
			topicSequences.add (topicSequence);
		}

		//construct test
		assert (testing.size() == topicSequences.size());
		for (int i = 0; i < testing.size(); i++) {
			Topication t = new Topication (testing.get(i), this, topicSequences.get(i));
			test.add (t);
			// Include sufficient statistics for this one doc
			// add count on new data to n[k][w] and n[k][*]
			// pay attention to unseen words
			FeatureSequence tokenSequence = (FeatureSequence) t.instance.getData();
			LabelSequence topicSequence = t.topicSequence;
			for (int pi = 0; pi < topicSequence.getLength(); pi++) {
				int topic = topicSequence.getIndexAtPosition(pi);
				int type = tokenSequence.getIndexAtPosition(pi);
				if(topic != -1) // type seen in training
				{
					typeTopicCounts[type].putOrAdd(topic, 1, 1);
				    tokensPerTopic[topic]++;
				}
			}
		}

		long startTime = System.currentTimeMillis();
		//loop
		int iter = 0;
		for ( ; iter <= maxIteration; iter++) {
			if(iter%100==0)
			{
				System.out.print("Iteration: " + iter);
				System.out.println();
			}
			int numDocs = test.size(); // TODO
			for (int di = 0; di < numDocs; di++) {
				FeatureVector fvTheta = (FeatureVector) theta.get(di).getData();
				double[] topicDistribution = fvTheta.getValues();
				FeatureSequence tokenSequence = (FeatureSequence) test.get(di).instance.getData();
				LabelSequence topicSequence = test.get(di).topicSequence;
				sampleTopicsForOneDocWithTheta (tokenSequence, topicSequence, topicDistribution);
			}
		}

		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal inferencing time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
	}

	//sampling with known theta, from maxent
	private void sampleTopicsForOneDocWithTheta(FeatureSequence tokenSequence,
			LabelSequence topicSequence, double[] topicDistribution) {
		// TODO Auto-generated method stub
		int[] oneDocTopics = topicSequence.getFeatures();

		IntIntHashMap currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		double tw;
		double[] topicWeights = new double[numTopics];
		double topicWeightsSum;
		int docLength = tokenSequence.getLength();
		
		// Iterate over the positions (words) in the document
		for (int si = 0; si < docLength; si++) {
			type = tokenSequence.getIndexAtPosition(si);
			oldTopic = oneDocTopics[si];
			if(oldTopic == -1) {
				continue;
			}

	 		currentTypeTopicCounts = typeTopicCounts[type];
			assert(currentTypeTopicCounts.get(oldTopic) >= 0);

			if (currentTypeTopicCounts.get(oldTopic) == 1) {
				currentTypeTopicCounts.remove(oldTopic);
			}
			else {
				currentTypeTopicCounts.addTo(oldTopic, -1);
			}
			tokensPerTopic[oldTopic]--;

			// Build a distribution over topics for this token
			Arrays.fill (topicWeights, 0.0);
			topicWeightsSum = 0;

			for (int ti = 0; ti < numTopics; ti++) {
				tw = ((currentTypeTopicCounts.get(ti) + beta) / (tokensPerTopic[ti] + betaSum))
				      * topicDistribution[ti]; // (/docLen-1+tAlpha); is constant across all topics
				topicWeightsSum += tw;
				topicWeights[ti] = tw;
			}
			// Sample a topic assignment from this distribution
			newTopic = random.nextDiscrete (topicWeights, topicWeightsSum);

			// Put that new topic into the counts
			oneDocTopics[si] = newTopic;
			currentTypeTopicCounts.putOrAdd(newTopic, 1, 1);
			tokensPerTopic[newTopic]++;
		}
	}

	//print human readable doc-topic matrix, for further IR use
	public void printTheta(ArrayList<Topication> dataset, File f, double threshold, int max) throws IOException{
		PrintWriter pw = new PrintWriter(new FileWriter(f));
		int[] topicCounts = new int[ numTopics ];
		int docLen;
		
		for (int di = 0; di < dataset.size(); di++) {
			LabelSequence topicSequence = dataset.get(di).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();
			docLen = currentDocTopics.length;
			for (int token=0; token < docLen; token++) {
				topicCounts[ currentDocTopics[token] ]++;
			}
			pw.println(dataset.get(di).instance.getName());
			// n(t|d)+alpha(t) / docLen + alphaSum
			for (int topic = 0; topic < numTopics; topic++) {
				double prob = (double) (topicCounts[topic]+alpha[topic]) / (docLen + alphaSum);
				pw.println("topic"+ topic + "\t" + prob);
			}

			pw.println();
			Arrays.fill(topicCounts, 0);
		}
		pw.close();
	}
	
	//print topic-word matrix, for further IR use
	public void printPhi(File f, double threshold) throws IOException{
		PrintWriter pw = new PrintWriter(new FileWriter(f));
		FeatureCounter[] wordCountsPerTopic = new FeatureCounter[numTopics];
		for (int ti = 0; ti < numTopics; ti++) {
			wordCountsPerTopic[ti] = new FeatureCounter(alphabet);
		}

		for (int fi = 0; fi < numTypes; fi++) {
			for (IntIntCursor keyVal : typeTopicCounts[fi]) {
				int topic = keyVal.key;
				int topicCount = keyVal.value;
				wordCountsPerTopic[topic].increment(fi, topicCount);
			}
		}
		
		for(int ti = 0; ti < numTopics; ti++){
			pw.println("Topic\t" + ti);
			FeatureCounter counter = wordCountsPerTopic[ti];
			FeatureVector fv = counter.toFeatureVector();
			for(int pos = 0; pos < fv.numLocations(); pos++){
				int fi = fv.indexAtLocation(pos);
				String word = (String) alphabet.lookupObject(fi);
				int count = (int) fv.valueAtLocation(pos);
				double prob;
				prob = (double) (count+beta)/(tokensPerTopic[ti] + betaSum);
				pw.println(word + "\t" + prob);
			}
			pw.println();
		}
		pw.close();
	}

	public void printDocumentTopics (ArrayList<Topication> dataset, File f) throws IOException {
		printDocumentTopics (dataset, new PrintWriter (new FileWriter (f) ) );
	}

	public void printDocumentTopics (ArrayList<Topication> dataset, PrintWriter pw) {
		printDocumentTopics (dataset, pw, 0.0, -1);
	}

	/**
	 *  @param pw          A print writer
	 *  @param threshold   Only print topics with proportion greater than this number
	 *  @param max         Print no more than this many topics
	 */
	public void printDocumentTopics (ArrayList<Topication> dataset, PrintWriter pw, double threshold, int max)	{
		pw.print ("#doc source topic proportion ...\n");
		int docLen;
		int[] topicCounts = new int[ numTopics ];

		IDSorter[] sortedTopics = new IDSorter[ numTopics ];
		for (int topic = 0; topic < numTopics; topic++) {
			// Initialize the sorters with dummy values
			sortedTopics[topic] = new IDSorter(topic, topic);
		}

		if (max < 0 || max > numTopics) {
			max = numTopics;
		}

		for (int di = 0; di < dataset.size(); di++) {
			LabelSequence topicSequence = dataset.get(di).topicSequence;
			int[] currentDocTopics = topicSequence.getFeatures();

			pw.print (di); pw.print (' ');

			if (dataset.get(di).instance.getSource() != null) {
				pw.print (dataset.get(di).instance.getSource());
			}
			else {
				pw.print ("null-source");
			}

			pw.print (' ');
			docLen = currentDocTopics.length;

			// Count up the tokens
			int realDocLen = 0;
			for (int token=0; token < docLen; token++) {
				if(currentDocTopics[token] != -1) {
					topicCounts[ currentDocTopics[token] ]++;
					realDocLen ++;
				}
			}
			assert(realDocLen == docLen);
            alphaSum=0.0;
			for(int topic=0; topic < numTopics; topic++){
				alphaSum+=alpha[topic];
			}
			
			// And normalize and smooth by Dirichlet prior alpha
			for (int topic = 0; topic < numTopics; topic++) {
				sortedTopics[topic].set(topic, (double) (topicCounts[topic]+alpha[topic]) / (docLen + alphaSum));
			}
     
			Arrays.sort(sortedTopics);

			for (int i = 0; i < max; i++) {
				if (sortedTopics[i].getWeight() < threshold) { break; }

				pw.print (sortedTopics[i].getID() + " " +
						  sortedTopics[i].getWeight() + " ");
			}
			pw.print (" \n");

			Arrays.fill(topicCounts, 0);
		}
        pw.close();
	}
	
	public void printState (ArrayList<Topication> dataset, File f) throws IOException {
		PrintStream out =
			new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(f))));
		printState(dataset, out);
		out.close();
	}

	public void printState (ArrayList<Topication> dataset, PrintStream out) {

		out.println ("#doc source pos typeindex type topic");

		for (int di = 0; di < dataset.size(); di++) {
			FeatureSequence tokenSequence =	(FeatureSequence) dataset.get(di).instance.getData();
			LabelSequence topicSequence =	dataset.get(di).topicSequence;

			String source = "NA";
			if (dataset.get(di).instance.getSource() != null) {
				source = dataset.get(di).instance.getSource().toString();
			}

			for (int pi = 0; pi < topicSequence.getLength(); pi++) {
				int type = tokenSequence.getIndexAtPosition(pi);
				int topic = topicSequence.getIndexAtPosition(pi);
				out.print(di); out.print(' ');
				out.print(source); out.print(' ');
				out.print(pi); out.print(' ');
				out.print(type); out.print(' ');
				out.print(alphabet.lookupObject(type)); out.print(' ');
				out.print(topic); out.println();
			}
		}
	}

}
