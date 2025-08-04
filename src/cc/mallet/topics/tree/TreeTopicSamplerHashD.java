package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntObjectHashMap;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.GZIPOutputStream;

import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * This class defines the tree topic sampler, which loads the instances,  
 * reports the topics, and leaves the sampler method as an abstract method, 
 * which might be various for different methods.
 * 
 * @author Yuening Hu
 */

public abstract class TreeTopicSamplerHashD extends TreeTopicSampler implements TreeTopicSamplerInterface{
	
	/**
	 * This class defines the format of a document.
	 */
	public class DocData {
		TIntArrayList tokens;
		TIntArrayList topics;
		TIntArrayList paths;
		// sort
		TIntIntHashMap topicCounts;
		String docName;
		
		public DocData (String name, TIntArrayList tokens, TIntArrayList topics,
				TIntArrayList paths, TIntIntHashMap topicCounts) {
			this.docName = name;
			this.tokens = tokens;
			this.topics = topics;
			this.paths = paths;
			this.topicCounts = topicCounts;
		}
		
		public String toString() {
			String result = "***************\n";
			result += docName + "\n";
			
			result += "tokens:   ";
			for (int jj = 0; jj < tokens.size(); jj++) {
				int index = tokens.get(jj);
				String word = vocab.get(index);
				result += word + " " + index + ", ";
			}
			
			result += "\ntopics:   ";
			result += topics.toString();
			
			result += "\npaths:    ";
			result += paths.toString();
			
			result += "\ntopicCounts:   ";

			for(TIntIntIterator it = this.topicCounts.iterator(); it.hasNext(); ) {
				it.advance();
				result += "Topic " + it.key() + ": " + it.value() + ", ";
			}
			result += "\n*****************\n";
			return result;
		}
	}
	
	public class WordProb implements Comparable {
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
	
	ArrayList<DocData> data;
	TreeTopicModel topics;
	
	public TreeTopicSamplerHashD (int numberOfTopics, double alphaSum, int seed) {
		super(numberOfTopics, alphaSum, seed);
		this.data = new ArrayList<DocData> ();
		
		// notice: this.topics is not initialized in this abstract class,
		// in each sub class, the topics variable is initialized differently.
	}
		
	/**
	 * This function adds instances given the training data in mallet input data format.
	 * For each token in a document, sample a topic and then sample a path based on prior. 
	 */
	public void addInstances(InstanceList[] training) {
		boolean debug = false;
		int count = 0;
		for(int ll = 0; ll < training.length; ll++) {
			int totalcount = 0;
			for (Instance instance : training[ll]) {
				count++;
				FeatureSequence original_tokens = (FeatureSequence) instance.getData();
				String name = instance.getName().toString();
				//String name = "null-source";
				//if (instance.getSource() != null) {
				//	name = instance.getSource().toString();
				//}
	
				// *** remained problem: keep topicCounts sorted
				TIntArrayList tokens = new TIntArrayList(original_tokens.getLength());
				TIntIntHashMap topicCounts = new TIntIntHashMap ();			
				TIntArrayList topics = new TIntArrayList(original_tokens.getLength());
				TIntArrayList paths = new TIntArrayList(original_tokens.getLength());
	
				for (int jj = 0; jj < original_tokens.getLength(); jj++) {
					String word = (String) original_tokens.getObjectAtPosition(jj);
					int token = this.vocab.indexOf(word);
					int removed = this.removedWordsNew.indexOf(word);
					int removednew = this.removedWordsNew.indexOf(word);
					if(token != -1 && removed == -1 && removednew == -1) {
						int topic = random.nextInt(numTopics);
						if(debug) { topic = count % numTopics; }
						tokens.add(token);
						topics.add(topic);
						topicCounts.adjustOrPutValue(topic, 1, 1);
						// sample a path for this topic
						int path_index = this.topics.initialize(token, topic);
						paths.add(path_index);
					}
				}
				DocData doc = new DocData(name, tokens, topics, paths, topicCounts);
				this.data.add(doc);
				
				totalcount += tokens.size();
				//if (totalcount > 200000) {
				//	System.out.println("total number of tokens: " + totalcount + " docs: " + count);
				//	break;
				//}
			}
			System.out.println("total number of tokens: " + totalcount);
			//System.out.println(doc);
		}
	}
	
	/**
	 * Resume instance states from the saved states file. 
	 */
	public void resumeStates(InstanceList[] training, String statesFile) throws IOException{
		FileInputStream statesfstream = new FileInputStream(statesFile);
		DataInputStream statesdstream = new DataInputStream(statesfstream);
		BufferedReader states = new BufferedReader(new InputStreamReader(statesdstream));
		
		// reading topics, paths
		for(int ll = 0; ll < training.length; ll++) {
			for (Instance instance : training[ll]) {
				FeatureSequence original_tokens = (FeatureSequence) instance.getData();
				String name = instance.getName().toString();
	
				// *** remained problem: keep topicCounts sorted
				TIntArrayList tokens = new TIntArrayList(original_tokens.getLength());
				TIntIntHashMap topicCounts = new TIntIntHashMap ();			
				TIntArrayList topics = new TIntArrayList(original_tokens.getLength());
				TIntArrayList paths = new TIntArrayList(original_tokens.getLength());
				
				//
				String statesLine = states.readLine();
				myAssert(statesLine != null, "statesFile doesn't match with the training data");
				statesLine = statesLine.trim();
				String[] str = statesLine.split("\t");
	
				int count = -1;
				for (int jj = 0; jj < original_tokens.getLength(); jj++) {
					String word = (String) original_tokens.getObjectAtPosition(jj);
					int token = this.vocab.indexOf(word);
					int removed = this.removedWords.indexOf(word);
					int removednew = this.removedWordsNew.indexOf(word);
					if(token != -1 && removed == -1) {
						count++;
						if (removednew == -1) {
							String[] tp = str[count].split(":");
							myAssert(tp.length == 2, "statesFile problem!");
							int topic = Integer.parseInt(tp[0]);
							//int path = Integer.parseInt(tp[1]);
							
							int wordpath = Integer.parseInt(tp[1]);
							int path = -1;
							int backoffpath = -1;
							// find the path for this wordpath
							TIntObjectHashMap<TIntArrayList> allpaths = this.topics.wordPaths.get(token);
							for(int pp : allpaths.keys()) {
								if(backoffpath == -1 && this.topics.pathToWordPath.get(pp) == 0){
									backoffpath = pp;
								}							
								if(this.topics.pathToWordPath.get(pp) == wordpath){
									path = pp;
									break;
								}
							}
							
							if(path == -1) {
								// this path must be in a correlation, it will be cleared later
								path = backoffpath;				
								myAssert(path != -1, "path problem");
								
								//String tmp = "";
								//tmp += "file " + name + "\n";
								//tmp += "word " + word + "\n";
								//tmp += "token " + token + "\n";
								//tmp += "index " + count + "\n";
								//tmp += "topic " + topic + "\n";
								//tmp += "wordpath " + wordpath + "\n";
								//tmp += "allpaths";
								//for(int pp : allpaths.keys()) {
								//	tmp += " " + pp;
								//}
								//System.out.println(tmp);
							}
							
							tokens.add(token);
							topics.add(topic);
							paths.add(path);
							topicCounts.adjustOrPutValue(topic, 1, 1);
							this.topics.changeCountOnly(topic, token, path, 1);
						}
					}
				}
				if(count != -1) {
					count++;
					myAssert(str.length == count, "resume problem!");
				}
				
				DocData doc = new DocData(name, tokens, topics, paths, topicCounts);
				this.data.add(doc);
			}
		}
		states.close();
	}
	
	/**
	 * This function clears the topic and path assignments for some words:
	 * (1) term option: only clears the topic and path for constraint words;
	 * (2) doc option: clears the topic and path for documents which contain 
	 *     at least one of the constraint words.
	 */
	public void clearTopicAssignments(String option, String consFile, String keepFile) {
		if (consFile != null) {
			this.loadConstraints(consFile);
		}
		if (this.cons == null || this.cons.size() <= 0) {
			return;
		}
		
		if (keepFile != null) {
			this.loadKeepList(keepFile);
		} else {
			this.topickeep = new HashMap<Integer, TIntHashSet>();
		}
		
		for(int dd = 0; dd < this.data.size(); dd++) {
			DocData doc = this.data.get(dd);
			
			for(int ii = 0; ii < doc.tokens.size(); ii++) {
				int word = doc.tokens.get(ii);
				int topic = doc.topics.get(ii);
				int path = doc.paths.get(ii);

				boolean keepTopicFlag = false;
				if(this.topickeep.containsKey(word)) {
					TIntHashSet keeptopics = this.topickeep.get(word);
					if(keeptopics.contains(topic)) {
						keepTopicFlag = true;
					}
				}
				
				if (option.equals("term")) {
					if(this.cons.contains(word) && (!keepTopicFlag)) {
						// change the count for count and node_count in TopicTreeWalk
						this.topics.changeCountOnly(topic, word, path, -1);
						doc.topics.set(ii, -1);
						doc.paths.set(ii, -1);
						//myAssert(doc.topicCounts.get(topic) >= 1, "clear topic assignments problem");
						doc.topicCounts.adjustValue(topic, -1);
					}
				} else { // option.equals("doc")
					if(!keepTopicFlag) {
						this.topics.changeCountOnly(topic, word, path, -1);
						doc.topics.set(ii, -1);
						doc.paths.set(ii, -1);
						doc.topicCounts.adjustValue(topic, -1);
					}
				}
			}
		}
			
//		for(int dd = 0; dd < this.data.size(); dd++) {
//			DocData doc = this.data.get(dd);			
//			Boolean flag = false;
//			for(int ii = 0; ii < doc.tokens.size(); ii++) {
//				int word = doc.tokens.get(ii);
//				int topic = doc.topics.get(ii);
//
//				boolean keepTopicFlag = false;
//				if(this.topickeep.containsKey(word)) {
//					TIntHashSet keeptopics = this.topickeep.get(word);
//					if(keeptopics.contains(topic)) {
//						keepTopicFlag = true;
//					}
//				}
//				
//				if(this.cons.contains(word) && (!keepTopicFlag)) {
//					if (option.equals("term")) {
//						int path = doc.paths.get(ii);
//						// change the count for count and node_count in TopicTreeWalk
//						this.topics.changeCountOnly(topic, word, path, -1);
//						doc.topics.set(ii, -1);
//						doc.paths.set(ii, -1);
//						myAssert(doc.topicCounts.get(topic) >= 1, "clear topic assignments problem");
//						doc.topicCounts.adjustValue(topic, -1);
//					} else if (option.equals("doc")) {
//						flag = true;
//						break;
//					}
//				}
//			}
//			if (flag) {
//				for(int ii = 0; ii < doc.tokens.size(); ii++) {
//					int word = doc.tokens.get(ii);
//					int topic = doc.topics.get(ii);
//					int path = doc.paths.get(ii);
//					this.topics.changeCountOnly(topic, word, path, -1);
//					doc.topics.set(ii, -1);
//					doc.paths.set(ii, -1);
//				}
//				doc.topicCounts.clear();
//			}	
//		}
		
	}
	
	/**
	 * This function defines how to change a topic during the sampling process.
	 * It handles the case where both new_topic and old_topic are "-1" (empty topic).
	 */
	public void changeTopic(int doc, int index, int word, int new_topic, int new_path) {
		DocData current_doc = this.data.get(doc);
		int old_topic = current_doc.topics.get(index);
		int old_path = current_doc.paths.get(index);		
		
		if (old_topic != -1) {
			myAssert((new_topic == -1 && new_path == -1), "old_topic != -1 but new_topic != -1");
			this.topics.changeCount(old_topic, word, old_path, -1);
			//myAssert(current_doc.topicCounts.get(old_topic) > 0, "Something wrong in changTopic");
			current_doc.topicCounts.adjustValue(old_topic, -1);
			current_doc.topics.set(index, -1);
			current_doc.paths.set(index, -1);
		}
		
		if (new_topic != -1) {
			myAssert((old_topic == -1 && old_path == -1), "new_topic != -1 but old_topic != -1");
			this.topics.changeCount(new_topic, word, new_path, 1);
			current_doc.topicCounts.adjustOrPutValue(new_topic, 1, 1);
			current_doc.topics.set(index, new_topic);
			current_doc.paths.set(index, new_path);
		}
	}
		
	/**
	 * The function computes the document likelihood.
	 */
	public double docLHood() {
		int docNum = this.data.size();
		
		double val = 0.0;
		val += Dirichlet.logGamma(this.alphaSum) * docNum;
		double tmp = 0.0;
		for (int tt = 0; tt < this.numTopics; tt++) {
			tmp += Dirichlet.logGamma(this.alpha[tt]);
		}
		val -= tmp * docNum;
		for (int dd = 0; dd < docNum; dd++) {
			DocData doc = this.data.get(dd);
			for (int tt = 0; tt < this.numTopics; tt++) {
				val += Dirichlet.logGamma(this.alpha[tt] + doc.topicCounts.get(tt));
			}
			val -= Dirichlet.logGamma(this.alphaSum + doc.topics.size());
		}
		return val;
	}
	
	/**
	 * Print the topic proportion for all documents.
	 */
	public void printDocumentTopics (File file) throws IOException {
		PrintStream out = new PrintStream (file);
		out.print ("#doc source topic proportion ...\n");

        IDSorter[] sortedTopics = new IDSorter[ this.numTopics ];
		for (int topic = 0; topic < this.numTopics; topic++) {
            // Initialize the sorters with dummy values
			sortedTopics[topic] = new IDSorter(topic, topic);
        }
		
		for (int dd = 0; dd < this.data.size(); dd++) {
			DocData doc = this.data.get(dd);
			
			// compute topic proportion in one document
			double sum = 0.0;
			double[] prob = new double[this.numTopics];
			for (int topic=0; topic < this.numTopics; topic++) {
				if (doc.topicCounts.containsKey(topic)) {
					prob[topic] = this.alpha[topic] + doc.topicCounts.get(topic);
				} else {
					prob[topic] = this.alpha[topic];
				}
				sum += prob[topic];
			}

			// normalize and sort
			for (int topic=0; topic < this.numTopics; topic++) {
	            prob[topic] /= sum;
	            sortedTopics[topic].set(topic, prob[topic]);
			}
			Arrays.sort(sortedTopics);
			
			// print one document
			out.print (dd); out.print (" ");

            if (doc.docName != null || !doc.docName.equals(" ")) {
				out.print (doc.docName);
            } else {
                out.print ("null-source");
            }
            out.print (" ");
			for (int i = 0; i < numTopics; i++) {
                out.print (sortedTopics[i].getID() + " " +
						   sortedTopics[i].getWeight() + " ");
            }
            out.print (" \n");
		}
		out.close();
	}
	
	//////////////////////////////////////////////////////
	
	/**
	 * This function loads vocab, loads tree, and initialize parameters.
	 */
	public void initialize(String treeFiles, String hyperFile, String vocabFile, String removedwordsFile) {
		this.loadVocab(vocabFile);
		if (removedwordsFile != null) {
			this.loadRemovedWords(removedwordsFile + ".all", this.removedWords);
			this.loadRemovedWords(removedwordsFile + ".new", this.removedWordsNew);
		}
		this.topics.initializeParams(treeFiles, hyperFile, this.vocab);
	}
	
	/**
	 * This function defines the sampling process, computes the likelihood and running time,
	 * and specifies when to save the states files.
	 */
	public void estimate(int numIterations, String outputFolder, int outputInterval, int topWords) {
		// update parameters
		this.topics.updateParams();
		
		if (this.startIter > this.numIterations) {
			System.out.println("Have already sampled " + this.numIterations + " iterations!");
			System.exit(0);
		}
		System.out.println("Start sampling for iteration " + this.startIter);
		
		for (int ii = this.startIter; ii <= numIterations; ii++) {
			//int[] tmpstats = {0, 0, 0, 0};
			//this.stats.add(tmpstats);
			long starttime = System.currentTimeMillis();
			//System.out.println("Iter " + ii);
			for (int dd = 0; dd < this.data.size(); dd++) {
				this.sampleDoc(dd);
				if (dd > 0 && dd % 10000 == 0) {
					System.out.println("Sampled " + dd + " documents.");
				}
			}
			
			double totaltime = (double)(System.currentTimeMillis() - starttime) / 1000;
			double lhood = 0;
			if ((ii > 0 && ii % outputInterval == 0) || ii == numIterations) {
				lhood = this.lhood();
			}
			this.lhood.add(lhood);
			this.iterTime.add(totaltime);
			
			if (ii % 10 == 0) {
				String tmp = "Iteration " + ii;
				tmp += " likelihood " + lhood;
				tmp += " totaltime " + totaltime;
				System.out.println(tmp);
			}
			
			if ((ii > 0 && ii % outputInterval == 0) || ii == numIterations) {
				try {
					this.report(outputFolder, topWords);
				} catch (IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}
	}
	
	//////////////////////////////////////////////////////
	
	/**
	 * This function returns the likelihood.
	 */
	public double lhood() {
		return this.docLHood() + this.topics.topicLHood();
	}
	
	/**
	 * By implementing the comparable interface, this function ranks the words
	 * in each topic, and returns the top words for each topic.
	 */
	public String displayTopWords (int numWords) {
		


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
				if(ii >= numWords) {
					break;
				}				
				int pp = wp[ii].wi;
				int ww = this.topics.getWordFromPath(pp);
				String word = this.vocab.get(ww);
				if (this.removedWords.indexOf(word) == -1 && this.removedWordsNew.indexOf(word) == -1) {
					tmp = wp[ii].p + "\t" + word + "\n";
					out.append(tmp);
				}
			}	
		}	
		return out.toString();
	}
	
	/**
	 * Prints the topic word distributions.
	 */
	public void printTopicWords (File file) throws IOException {
				
		PrintStream out = new PrintStream (file);
		int numPaths = this.topics.getPathNum();
		String tmp;
		for (int tt = 0; tt < this.numTopics; tt++){
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
				String word = this.vocab.get(ww);
				if (this.removedWords.indexOf(word) == -1 && this.removedWordsNew.indexOf(word) == -1) {
					tmp = tt + "\t" + word + "\t" + wp[ii].p;
					out.println(tmp);
				}
			}	
		}
		
		out.close();
	}	
	
	/**
	 * Prints the topic and path of each word for all documents.
	 */
	public void printState (File file) throws IOException {
		//PrintStream out =
		//	new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))));
		PrintStream out = new PrintStream(file);
		
		for (int dd = 0; dd < this.data.size(); dd++) {
			DocData doc = this.data.get(dd);
			String tmp = "";
			for (int ww = 0; ww < doc.topics.size(); ww++) {
				int topic = doc.topics.get(ww);
				int path = doc.paths.get(ww);
				int wordpath = this.topics.pathToWordPath.get(path);
				tmp += topic + ":" + wordpath + "\t";
			}
			out.println(tmp);
		}
		out.close();
	}
	
	public TreeTopicInferencer getInferencer() {
		//this.topics.updateParams();
		HashSet<String> removedall = new HashSet<String> ();
		removedall.addAll(this.removedWords);
		removedall.addAll(this.removedWordsNew);
		TreeTopicInferencer inferencer = new TreeTopicInferencer(topics, vocab, removedall, alpha);
		return inferencer;
	}
	
	 public TreeMarginalProbEstimator getProbEstimator() {
		HashSet<String> removedall = new HashSet<String> ();
		removedall.addAll(this.removedWords);
		removedall.addAll(this.removedWordsNew);
		TreeMarginalProbEstimator estimator = new TreeMarginalProbEstimator(topics, vocab, removedall, alpha);
		return estimator;
	}
}
