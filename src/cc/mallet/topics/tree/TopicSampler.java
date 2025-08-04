package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import cc.mallet.topics.tree.TreeTopicSamplerHashD.DocData;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * Abstract class for TopicSampler.
 * Defines the basic functions for input, output, resume.
 * Also defines the abstract functions for child class.
 * 
 * @author Yuening Hu
 */

public abstract class TopicSampler{
		
	int numTopics;
	int numIterations;
	int startIter;
	Randoms random;
	double[] alpha;
	double alphaSum;
	TDoubleArrayList lhood;
	TDoubleArrayList iterTime;
	ArrayList<String> vocab;

	TreeTopicModel topics;
	TIntHashSet cons;
	
	public TopicSampler (int numberOfTopics, double alphaSum, int seed) {
		this.numTopics = numberOfTopics;
		this.random = new Randoms(seed);

		this.alphaSum = alphaSum;
		this.alpha = new double[numTopics];
		Arrays.fill(alpha, alphaSum / numTopics);

		this.vocab = new ArrayList<String> ();
		this.cons = new TIntHashSet();
		
		this.lhood = new TDoubleArrayList();
		this.iterTime = new TDoubleArrayList();
		this.startIter = 0;
		
		// notice: this.topics and this.data are not initialized in this abstract class,
		// in each sub class, the topics variable is initialized differently.
	}
	

	
	public void setNumIterations(int iters) {
		this.numIterations = iters;
	}
	
	public int getNumIterations() {
		return this.numIterations;
	}
	

		
	/**
	 * This function returns the likelihood.
	 */
	public double lhood() {
		return this.docLHood() + this.topics.topicLHood();
	}
	
	/**
	 * Resume lhood and iterTime from the saved lhood file. 
	 */
	public void resumeLHood(String lhoodFile) throws IOException{
		FileInputStream lhoodfstream = new FileInputStream(lhoodFile);
		DataInputStream lhooddstream = new DataInputStream(lhoodfstream);
		BufferedReader brLHood = new BufferedReader(new InputStreamReader(lhooddstream));
		// the first line is the title
		String strLine = brLHood.readLine();
		while ((strLine = brLHood.readLine()) != null) {
			strLine = strLine.trim();
			String[] str = strLine.split("\t");
			// iteration, likelihood, iter_time
			myAssert(str.length == 3, "lhood file problem!");
			this.lhood.add(Double.parseDouble(str[1]));
			this.iterTime.add(Double.parseDouble(str[2]));
		}
		this.startIter = this.lhood.size();
		if (this.startIter > this.numIterations) {
			System.out.println("Have already sampled " + this.numIterations + " iterations!");
			System.exit(0);
		}
		System.out.println("Start sampling for iteration " + this.startIter);
		brLHood.close();
	}
	
	/**
	 * Resumes from the saved files.
	 */
	public void resume(InstanceList training, String resumeDir) {
		try {
			String statesFile = resumeDir + ".states";
			resumeStates(training, statesFile);
			
			String lhoodFile = resumeDir + ".lhood";
			resumeLHood(lhoodFile);
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}
	
	/**
	 * This function prints the topic words of each topic.
	 */
	public void printTopWords(File file, int numWords) throws IOException {
		PrintStream out = new PrintStream (file);
		out.print(displayTopWords(numWords));
		out.close();
	}
	
	/**
	 * By implementing the comparable interface, this function ranks the words
	 * in each topic, and returns the top words for each topic.
	 */
	public String displayTopWords (int numWords) {
		
		class WordProb implements Comparable {
			int wi;
			double p;
			public WordProb (int wi, double p) { this.wi = wi; this.p = p; }
			public final int compareTo (Object o2) {
				if (p > ((WordProb)o2).p)
					return -1;
				else if (p == ((WordProb)o2).p)
					return 0;
				else return 1;
			}
		}

		StringBuilder out = new StringBuilder();
		int numPaths = this.topics.getPathNum();
		//System.out.println(numPaths);
		
		for (int tt = 0; tt < this.numTopics; tt++){
			String tmp = "\n--------------\nTopic " + tt + "\n------------------------\n";
			//System.out.print(tmp);
			out.append(tmp);
			WordProb[] wp = new WordProb[numPaths];
			for (int pp = 0; pp < numPaths; pp++){
				int ww = this.topics.getWordFromPath(pp);
				double val = this.topics.computeTopicPathProb(tt, ww, pp);
				wp[pp] = new WordProb(pp, val);
			}
			Arrays.sort(wp);
			for (int ii = 0; ii < wp.length; ii++){
				int pp = wp[ii].wi;
				int ww = this.topics.getWordFromPath(pp);
				//tmp = wp[ii].p + "\t" + this.vocab.lookupObject(ww) + "\n";
				tmp = wp[ii].p + "\t" + this.vocab.get(ww) + "\n";
				//System.out.print(tmp);
				out.append(tmp);
				if(ii > numWords) {
					break;
				}
			}	
		}	
		return out.toString();
	}
		
	/**
	 * Prints likelihood and iter time.
	 */
	public void printStats (File file) throws IOException {
		PrintStream out = new PrintStream (file);
		String tmp = "Iteration\t\tlikelihood\titer_time\n";
		out.print(tmp);
		for (int iter = 0; iter < this.lhood.size(); iter++) {
			tmp = iter + "\t" + this.lhood.get(iter) + "\t" + this.iterTime.get(iter);
			out.println(tmp);
		}
		out.close();
	}
	
	public void loadVocab(String vocabFile) {
		
		try {
			FileInputStream infstream = new FileInputStream(vocabFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				String[] str = strLine.split("\t");
				if (str.length > 1) {
					this.vocab.add(str[1]);
				} else {
					System.out.println("Error! " + strLine);
				}
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No vocab file Found!");
		}

	}
	
	/**
	 * Load constraints
	 */
	public void loadConstraints(String consFile) {
		try {
			FileInputStream infstream = new FileInputStream(consFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				String[] str = strLine.split("\t");
				if (str.length > 1) {
					// str[0] is either "MERGE_" or "SPLIT_", not a word
					for(int ii = 1; ii < str.length; ii++) {
						int word = this.vocab.indexOf(str[ii]);
						myAssert(word >= 0, "Constraint words not found in vocab: " + str[ii]);
						cons.add(word);
					}
					this.vocab.add(str[1]);
				} else {
					System.out.println("Error! " + strLine);
				}
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No vocab file Found!");
		}

	}
	
	/**
	 * For testing~~
	 */
	public static void myAssert(boolean flag, String info) {
		if(!flag) {
			System.out.println(info);
			System.exit(0);
		}
	}
	
	abstract void addInstances(InstanceList training);
	abstract void resumeStates(InstanceList training, String statesFile) throws IOException;
	abstract void clearTopicAssignments(String option, String consFile);
	abstract void changeTopic(int doc, int index, int word, int new_topic, int new_path);
	abstract double docLHood();
	abstract void printDocumentTopics (File file) throws IOException;
	abstract void sampleDoc(int doc);
}
