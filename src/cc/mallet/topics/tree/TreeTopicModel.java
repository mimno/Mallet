package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeMap;

import cc.mallet.types.Dirichlet;

/**
 * This class defines the tree topic model. 
 * It implements most of the functions and leave four abstract methods,
 * which might be various for different models.
 * 
 * @author Yuening Hu
 */

public abstract class TreeTopicModel implements Serializable {
	
	int numTopics;
	Random random;
	int maxDepth;
	int root;
	HIntIntObjectHashMap<TIntArrayList> wordPaths;
	TIntArrayList pathToWord;
	TIntArrayList pathToWordPath;
	TIntObjectHashMap<TIntArrayList> nodeToPath;

	TIntDoubleHashMap betaSum;
	HIntIntDoubleHashMap beta; // 2 levels hash map
	TIntDoubleHashMap priorSum;
	HIntIntDoubleHashMap priorPath;
	
	TIntObjectHashMap<HIntIntIntHashMap> nonZeroPaths;
	TIntObjectHashMap<ArrayList<int[]>> nonZeroPathsBubbleSorted;
	TIntObjectHashMap<TopicTreeWalk> traversals;
	
	HIntIntDoubleHashMap normalizer;
	TIntDoubleHashMap rootNormalizer;
	TIntDoubleHashMap smoothingEst;
	
	/*********************************************/
	double smoothingNocons;  // for unconstrained words
	double topicbetaNocons;  // for unconstrained words
	double betaNocons; // for unconstrained words
	/*********************************************/
	
	public TreeTopicModel(int numTopics, Random random) {
		this.numTopics = numTopics;
		this.random = random;
		
		this.betaSum = new TIntDoubleHashMap ();
		this.beta = new HIntIntDoubleHashMap ();
		this.priorSum = new TIntDoubleHashMap ();
		this.priorPath = new HIntIntDoubleHashMap ();
		
		this.wordPaths = new HIntIntObjectHashMap<TIntArrayList> ();
		this.pathToWord = new TIntArrayList ();
		this.pathToWordPath = new TIntArrayList();
		this.nodeToPath = new TIntObjectHashMap<TIntArrayList> ();
		
		this.nonZeroPaths = new TIntObjectHashMap<HIntIntIntHashMap> ();
		this.nonZeroPathsBubbleSorted = new TIntObjectHashMap<ArrayList<int[]>> ();
		this.traversals = new TIntObjectHashMap<TopicTreeWalk> ();
		
		this.smoothingNocons = 0.0;
		this.topicbetaNocons = 0.0;
	}
	
	/**
	 * Initialize the parameters, including:
	 * (1) loading the tree
	 * (2) initialize betaSum and beta
	 * (3) initialize priorSum, priorPath
	 * (4) initialize wordPaths, pathToWord, NodetoPath
	 * (5) initialize traversals
	 * (6) initialize nonZeroPaths
	 */
	protected void initializeParams(String treeFiles, String hyperFile, ArrayList<String> vocab) {
		
		PriorTree tree = new PriorTree();
		tree.initialize(treeFiles, hyperFile, vocab);
		
		// get tree depth
		this.maxDepth = tree.getMaxDepth();
		// get root index
		this.root = tree.getRoot();
		// get tree nodes
		TIntObjectHashMap<Node> nodes = tree.getNodes();
		// get tree paths
		TIntObjectHashMap<ArrayList<Path>> word_paths = tree.getWordPaths();
		
		// if one node contains multiple words, we need to change each word to a leaf node
		// (assigning a leaf index for each word).
		int leaf_index = nodes.size();
		HIntIntIntHashMap tmp_wordleaf = new HIntIntIntHashMap();
		
		// initialize betaSum and beta
		for (TIntObjectIterator<Node> it = nodes.iterator(); it.hasNext(); ) {
			it.advance();
			int index = it.key();
			Node node = it.value();
			TDoubleArrayList transition_prior = node.getTransitionPrior();
			
			// when node has children
			if (node.getNumChildren() > 0) {
				//assert node.getNumWords() == 0;
				this.betaSum.put(index, node.getTransitionScalor());
				for (int ii = 0; ii < node.getNumChildren(); ii++) {
					int child = node.getChild(ii);
					this.beta.put(index, child, transition_prior.get(ii));
				}
			}
			
			// when node contains multiple words.
			// we change a node containing multiple words to a node containing multiple
			// leaf node and each leaf node containing one word
			if (node.getNumWords() > 1) {
				//assert node.getNumChildren() == 0;
				this.betaSum.put(index, node.getTransitionScalor());
				for (int ii = 0; ii < node.getNumWords(); ii++) {
					int word = node.getWord(ii);
					leaf_index++;
					this.beta.put(index, leaf_index, transition_prior.get(ii));
					
					// one word might have multiple paths, 
					// so we keep the (word_index, word_parent)
					// as the index for this leaf index, which is needed later
					tmp_wordleaf.put(word, index, leaf_index);
				}
			}
		}
		
		/*********************************************/
		// find beta for unconstrained words
		Node rootnode = nodes.get(this.root);
		for (int ii = 0; ii < rootnode.getNumChildren(); ii++) {
			int child = rootnode.getChild(ii);
			Node childnode = nodes.get(child);
			double tmpbeta = this.beta.get(this.root, child);
			//System.out.println("beta for root to " + child + ": " + tmpbeta);
			if (childnode.getHypoCount() == 1.0) {
				this.betaNocons = this.beta.get(this.root, child);
				System.out.println("beta for unconstrained words from root to " + child + ": " + tmpbeta);
				break;
			}
		}
		/*********************************************/
		
		// initialize priorSum, priorPath
		// initialize wordPaths, pathToWord, NodetoPath
		int path_index = -1;
		TIntObjectHashMap<TIntHashSet> tmp_nodeToPath = new TIntObjectHashMap<TIntHashSet>();
		for (TIntObjectIterator<ArrayList<Path>> it = word_paths.iterator(); it.hasNext(); ) {
			it.advance();
			
			int word = it.key();
			ArrayList<Path> paths = it.value();
			this.priorSum.put(word, 0.0);
			
			int word_path_index = -1;
			for (int ii = 0; ii < paths.size(); ii++) {
				path_index++;
				word_path_index++;
				this.pathToWord.add(word);
				this.pathToWordPath.add(word_path_index);
				
				double prob = 1.0;
				Path p = paths.get(ii);
				TIntArrayList path_nodes = p.getNodes();
				
				// for a node that contains multiple words
				// if yes, retrieve the leaf index for each word
				// and that to nodes of path
				int parent = path_nodes.get(path_nodes.size()-1);
				if (tmp_wordleaf.contains(word, parent)) {
					leaf_index = tmp_wordleaf.get(word, parent);
					path_nodes.add(leaf_index);
				}
				
				for (int nn = 0; nn < path_nodes.size() - 1; nn++) {
					parent = path_nodes.get(nn);
					int child = path_nodes.get(nn+1);
					prob *= this.beta.get(parent, child);
				}
				
				for (int nn = 0; nn < path_nodes.size(); nn++) {
					int node = path_nodes.get(nn);
					if (! tmp_nodeToPath.contains(node)) {
						tmp_nodeToPath.put(node, new TIntHashSet());
					}
					tmp_nodeToPath.get(node).add(path_index);
					//tmp_nodeToPath.get(node).add(word_path_index);
				}
				
				this.priorPath.put(word, path_index, prob);
				this.priorSum.adjustValue(word, prob);
				this.wordPaths.put(word, path_index, path_nodes);
			}
		}
		
		// change tmp_nodeToPath to this.nodeToPath
		// this is because arraylist is much more efficient than hashset, when we
		// need to go over the whole set multiple times
		for(TIntObjectIterator it = tmp_nodeToPath.iterator(); it.hasNext(); ) {
			it.advance();
			int node = it.key();
			TIntHashSet paths = (TIntHashSet)it.value();
			TIntArrayList tmp = new TIntArrayList(paths.toArray());
			
//			System.out.println("Node" + node);
//			for(int ii = 0; ii < tmp.size(); ii++) {
//				System.out.print(tmp.get(ii) + " ");
//			}
//			System.out.println("");
			
			this.nodeToPath.put(node, tmp);
		}
		
		// initialize traversals
		for (int tt = 0; tt < this.numTopics; tt++) {
			TopicTreeWalk tw = new TopicTreeWalk();
			this.traversals.put(tt, tw);
		}
		
		// initialize nonZeroPaths
		int[] words = this.wordPaths.getKey1Set();
		for (int ww = 0; ww < words.length; ww++) {
			int word = words[ww]; 
			this.nonZeroPaths.put(word, new HIntIntIntHashMap());
		}
	}
	
	/**
	 * This function samples a path based on the prior
	 * and change the node and edge count for a topic.
	 */
	protected int initialize (int word, int topic) {
		double sample = this.random.nextDouble();
		int path_index = this.samplePathFromPrior(word, sample);
		this.changeCountOnly(topic, word, path_index, 1);
		return path_index;
	}
	
	/**
	 * This function changes the node and edge count for a topic.
	 */
	protected void changeCountOnly(int topic, int word, int path, int delta) {
		TIntArrayList path_nodes = this.wordPaths.get(word, path);
		TopicTreeWalk tw = this.traversals.get(topic);
		tw.changeCount(path_nodes, delta);
	}
	
	/**
	 * This function samples a path from the prior.
	 */
	protected int samplePathFromPrior(int term, double sample) {
		int sampled_path = -1;
		sample *= this.priorSum.get(term);
		TIntDoubleHashMap paths = this.priorPath.get(term);
		for(TIntDoubleIterator it = paths.iterator(); it.hasNext(); ) {
			it.advance();
			sample -= it.value();
			if (sample <= 0.0) {
				sampled_path = it.key();
				break;
			}
		}

		return sampled_path;
	}
	
	/**
	 * This function computes a path probability in a topic.
	 */
	public double computeTopicPathProb(int topic, int word, int path_index) {
		TIntArrayList path_nodes = this.wordPaths.get(word, path_index);
		TopicTreeWalk tw = this.traversals.get(topic);
		double val = 1.0;
		for(int ii = 0; ii < path_nodes.size()-1; ii++) {
			int parent = path_nodes.get(ii);
			int child = path_nodes.get(ii+1);
			val *= this.beta.get(parent, child) + tw.getCount(parent, child);
			val /= this.betaSum.get(parent) + tw.getNodeCount(parent);
		}
		return val;
	}
	
	/**
	 * This function computes the topic likelihood (by node).
	 */
	public double topicLHood() {
		double val = 0.0;
		for (int tt = 0; tt < this.numTopics; tt++) {
			for (int nn : this.betaSum.keys()) {
				double beta_sum = this.betaSum.get(nn);
				//val += Dirichlet.logGamma(beta_sum) * this.beta.get(nn).size();
				val += Dirichlet.logGamma(beta_sum);
				
				double tmp = 0.0;
				for (int cc : this.beta.get(nn).keys()) {
					tmp += Dirichlet.logGamma(this.beta.get(nn, cc));
				}
				//val -= tmp * this.beta.get(nn).size();
				val -= tmp;
				
				for (int cc : this.beta.get(nn).keys()) {
					int count = this.traversals.get(tt).getCount(nn, cc);
					val += Dirichlet.logGamma(this.beta.get(nn, cc) + count);
				}
				
				int count = this.traversals.get(tt).getNodeCount(nn);
				val -= Dirichlet.logGamma(beta_sum + count);
			}
			//System.out.println("likelihood " + val);
		}
		return val;
	}
	
	public TIntObjectHashMap<TIntArrayList> getPaths(int word) {
		return this.wordPaths.get(word);
	}
	
	public int[] getWordPathIndexSet(int word) {
		return this.wordPaths.get(word).keys();
	}
	
	public int getPathNum() {
		return this.pathToWord.size();
	}
	
	public int getWordFromPath(int pp) {
		return this.pathToWord.get(pp);
	}
	
	public double getPathPrior(int word, int path) {
		return this.priorPath.get(word, path);
	}
	
	// for TreeTopicSamplerFast 
	public double computeTermSmoothing(double[] alpha, int word) {
		return 0;
	}
	
	public double computeTermTopicBeta(TIntIntHashMap topic_counts, int word) {
		return 0;
	}
	
	public double computeTopicTermTest(double[] alpha, TIntIntHashMap local_topic_counts, int word, ArrayList<double[]> dict){
		return 0;
	}
	
	public double computeTermTopicBetaSortD(ArrayList<int[]> topicCounts, int word) {
		return 0;
	}
	
	public double computeTopicTermSortD(double[] alpha, ArrayList<int[]> local_topic_counts, int word, ArrayList<double[]> dict){
		return 0;
	}
	
	/*********************************************/
	public void computeSmoothingNocons(double[] alpha) {}
	public void computeDocTopicBetaNocons(TIntIntHashMap topic_counts) {}
	public void updateStatisticsNocons(double alpha, int topic, int topicCount, int delta){}
	/*********************************************/
	
	// shared methods
	abstract double getNormalizer(int topic, int path);
	abstract void updateParams();
	abstract void changeCount(int topic, int word, int path_index, int delta);
	abstract double computeTopicTerm(double[] alpha, TIntIntHashMap local_topic_counts, int word, ArrayList<double[]> dict);

}
