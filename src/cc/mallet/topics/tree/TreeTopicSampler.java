package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntHashSet;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import cc.mallet.topics.TopicInferencer;
import cc.mallet.topics.tree.TreeTopicSamplerSortD.DocData;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;


/**
 * This class defines the tree topic sampler.
 * Defines the basic functions for input, output, resume.
 * Also defines the abstract functions for child class.
 * 
 * @author Yuening Hu
 */

public abstract class TreeTopicSampler {
	
	int numTopics;
	int numIterations;
	int startIter;
	Randoms random;
	double[] alpha;
	double alphaSum;
	TDoubleArrayList lhood;
	TDoubleArrayList iterTime;
	ArrayList<String> vocab;
	ArrayList<String> removedWords;
	ArrayList<String> removedWordsNew;
	TIntHashSet cons;
	HashMap<Integer, TIntHashSet> topickeep;
	
	public TreeTopicSampler (int numberOfTopics, double alphaSum, int seed) {
		this.numTopics = numberOfTopics;
		this.random = new Randoms(seed);

		this.alphaSum = alphaSum;
		this.alpha = new double[numTopics];
		Arrays.fill(alpha, alphaSum / numTopics);
		
		this.vocab = new ArrayList<String> ();
		this.removedWords = new ArrayList<String> ();
		this.removedWordsNew = new ArrayList<String> ();
		this.cons = new TIntHashSet();
		this.topickeep = new HashMap<Integer, TIntHashSet>();
		
		this.lhood = new TDoubleArrayList();
		this.iterTime = new TDoubleArrayList();
		this.startIter = 0;
	}
	
	/////////////////////////////////////////////////////////////
	
	public void setNumIterations(int iters) {
		this.numIterations = iters;
	}
	
	/**
	 * Resumes from the saved files.
	 */
	public void resume(InstanceList[] training, String resumeDir) {
		try {
			String statesFile = resumeDir + ".states";
			resumeStates(training, statesFile);
			
			String lhoodFile = resumeDir + ".lhood";
			resumeLHood(lhoodFile);
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}
	
	//////////////////////////////////////////////////////////
	
	public int getNumIterations() {
		return this.numIterations;
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
		
//		if (this.startIter > this.numIterations) {
//			System.out.println("Have already sampled " + this.numIterations + " iterations!");
//			System.exit(0);
//		}
//		System.out.println("Start sampling for iteration " + this.startIter);
		
		brLHood.close();
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
		
	/**
	 * This function reports the detected topics, the documents topics,
	 * and saves states file and lhood file.
	 */
	public void report(String outputDir, int topWords) throws IOException {

		String topicKeysFile = outputDir + ".topics";
		this.printTopWords(new File(topicKeysFile), topWords);
		
		String docTopicsFile = outputDir + ".docs";
		this.printDocumentTopics(new File(docTopicsFile));
		
		String stateFile = outputDir + ".states";
		this.printState (new File(stateFile));
		
		String statsFile = outputDir + ".lhood";
		this.printStats (new File(statsFile));
		
		String topicWordsFile = outputDir + ".topic-words";
		this.printTopicWords(new File(topicWordsFile));
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
					System.out.println("Vocab file error at line: " + strLine);
				}
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No vocab file Found!");
		}

	}
	
	/**
	 * Load StopWords
	 */
	public void loadRemovedWords(String removedwordFile, ArrayList<String> removed) {
				
		try {
			
			FileInputStream infstream = new FileInputStream(removedwordFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				removed.add(strLine);
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No stop word file Found!");
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
			System.out.println("No constraint file Found!");
		}

	}
	
	/**
	 * For words on this list, topic assignments will not be cleared.
	 */
	public void loadKeepList(String keepFile) {
		try {
			FileInputStream infstream = new FileInputStream(keepFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				String[] words = strLine.split(" ");
				int word = this.vocab.indexOf(words[0]);
				int topic = Integer.parseInt(words[1]);
				if (!this.topickeep.containsKey(word)) {
					this.topickeep.put(word, new TIntHashSet());
				}
				TIntHashSet tmp = this.topickeep.get(word);
				tmp.add(topic);
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No keep file Found!");
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
	
	abstract public String displayTopWords (int numWords);
	abstract public void printState (File file) throws IOException;
	abstract public void printTopicWords (File file) throws IOException;
	abstract public void sampleDoc(int doc);
	abstract public double docLHood();
	abstract public void printDocumentTopics (File file) throws IOException;
	abstract public void resumeStates(InstanceList[] training, String statesFile) throws IOException;
	abstract public TreeTopicInferencer getInferencer();
	abstract public TreeMarginalProbEstimator getProbEstimator();
}
