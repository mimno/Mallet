package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectDoubleHashMap;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import cc.mallet.types.Alphabet;
import cc.mallet.types.InstanceList;

import topicmod_projects_ldawn.WordnetFile.WordNetFile;
import topicmod_projects_ldawn.WordnetFile.WordNetFile.Synset;
import topicmod_projects_ldawn.WordnetFile.WordNetFile.Synset.Word;

/**
 * This class loads the prior tree structure from the proto buffer files of tree structure.
 * Main entrance: initialize()
 * 
 * @author Yuening Hu
 */

public class PriorTree {
	
	int root;
	int maxDepth;
	
	TObjectDoubleHashMap<String> hyperparams;
	TIntObjectHashMap<Node> nodes;
	TIntObjectHashMap<ArrayList<Path>> wordPaths;
	
	public PriorTree () {
		this.hyperparams = new TObjectDoubleHashMap<String> ();
		this.nodes = new TIntObjectHashMap<Node> ();
		this.wordPaths = new TIntObjectHashMap<ArrayList<Path>> ();
	}
	
	/**
	 * Get the input tree file lists from the given tree file names
	 */
	private ArrayList<String> getFileList(String tree_files) {
		
		int split_index = tree_files.lastIndexOf('/');
		String dirname = tree_files.substring(0, split_index);
		String fileprefix = tree_files.substring(split_index+1);
		fileprefix = fileprefix.replace("*", "");
		
		//System.out.println(dirname);
		//System.out.println(fileprefix);
		
		File dir = new File(dirname);
		String[] children = dir.list();
		ArrayList<String> filelist = new ArrayList<String>();

		for (int i = 0; i < children.length; i++) {
			if (children[i].startsWith(fileprefix)) {
				System.out.println("Found one: " + dirname + "/" + children[i]);
				String filename = dirname + "/" + children[i];
				filelist.add(filename);
			}
		}
		return filelist;
	}
	
	/**
	 * Load hyper parameters from the given file
	 */
	private void loadHyperparams(String hyperFile) {
		try {
			FileInputStream infstream = new FileInputStream(hyperFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				String[] str = strLine.split(" ");
				if (str.length != 2) {
					System.out.println("Hyperparameter file is not in the correct format!");
					System.exit(0);
				}
				double tmp = Double.parseDouble(str[1]);
				hyperparams.put(str[0], tmp);
			}
			in.close();			
			
//			Iterator<Map.Entry<String, Double>> it = hyperparams.entrySet().iterator();
//			while (it.hasNext()) {
//				Map.Entry<String, Double> entry = it.next();
//				System.out.println(entry.getKey());
//				System.out.println(entry.getValue());
//			}
			
		} catch (IOException e) {
			System.out.println("No hyperparameter file Found!");
		}
	}
	
	/**
	 * Load tree nodes one by one: load the children, words of each node
	 */
	private void loadTree(String tree_files, ArrayList<String> vocab) {
		
		ArrayList<String> filelist = getFileList(tree_files);
		
		for (int ii = 0; ii < filelist.size(); ii++) {
			String filename = filelist.get(ii);
			WordNetFile tree = null;
			try {
				tree = WordNetFile.parseFrom(new FileInputStream(filename));
			} catch (IOException e) {
				System.out.println("Cannot find tree file: " + filename);
			}
			
			int new_root = tree.getRoot();
			assert( (new_root == -1) || (this.root == -1) || (new_root == this.root));
			if (new_root >= 0) {
				this.root = new_root;
			}
			
			for (int jj = 0; jj < tree.getSynsetsCount(); jj++) {
				Synset synset = tree.getSynsets(jj);
				Node n = new Node();
				n.setOffset(synset.getOffset());
				n.setRawCount(synset.getRawCount());
				n.setHypoCount(synset.getHyponymCount());
				
				double transition = hyperparams.get(synset.getHyperparameter());
				n.setTransitionScalor(transition);
				for (int cc = 0; cc < synset.getChildrenOffsetsCount(); cc++) {
					n.addChildrenOffset(synset.getChildrenOffsets(cc));
				}
				
				for (int ww = 0; ww < synset.getWordsCount(); ww++) {
					Word word = synset.getWords(ww);
					int term_id = vocab.indexOf(word.getTermStr());
					//int term_id = vocab.lookupIndex(word.getTermStr());
					double word_count = word.getCount();
					n.addWord(term_id, word_count);
				}
				
				nodes.put(n.getOffset(), n);
			}
		}
		
		assert(this.root >= 0) : "Cannot find a root node in the tree file. Have you provided " +
		"all tree files instead of a single tree file? (e.g., use 'tree.wn' instead of 'tree.wn.0')";
		
	}
	
	/**
	 * Get all the paths in the tree, keep the (word, path) pairs
	 * Note the word in the pair is actually the word of the leaf node
	 */
	private int searchDepthFirst(int depth, 
								 int node_index, 
								 TIntArrayList traversed, 
								 TIntArrayList next_pointers) {
		int max_depth = depth;
		traversed.add(node_index);
		Node current_node = this.nodes.get(node_index);
		current_node.addPaths(1);
		
		// go over the words that current node emits
		for (int ii = 0; ii < current_node.getNumWords(); ii++) {
			int word = current_node.getWord(ii);
			Path p = new Path();
			p.addNodes(traversed);
			// p.addChildren(next_pointers);
			p.addFinalWord(word);
			if (! this.wordPaths.contains(word)) {
				this.wordPaths.put(word, new ArrayList<Path> ());
			}
			ArrayList<Path> tmp = this.wordPaths.get(word);
			tmp.add(p);
		}
		
		// go over the child nodes of the current node
		for (int ii = 0; ii < current_node.getNumChildren(); ii++) {
			int child = current_node.getChild(ii);
			next_pointers.add(child);
			int child_depth = this.searchDepthFirst(depth+1, child, traversed, next_pointers);
			next_pointers.remove(next_pointers.size()-1);
			max_depth = max_depth >= child_depth ? max_depth : child_depth;
		}
		
		traversed.remove(traversed.size()-1);
		return max_depth;
	}
	
	/**
	 * Set the scaled prior distribution of each node
	 * According to the hypoCount of the nodes' children, generate a Multinomial
	 * distribution, then scaled by transitionScalor
	 */
	private void setPrior() {
		for (TIntObjectIterator<Node> it = this.nodes.iterator(); it.hasNext(); ) {
			it.advance();
			Node n = it.value();
			int numChildren = n.getNumChildren();
			int numWords = n.getNumWords();
			
			// firstly set the hypoCount for each child
			if (numChildren > 0) {
				assert numWords == 0;
				n.initializePrior(numChildren);
				for (int ii = 0; ii < numChildren; ii++) {
					int child = n.getChild(ii);
					n.setPrior(ii, this.nodes.get(child).getHypoCount());
				}
			}
			
			// this step is for tree structures whose leaf nodes contain more than one words
			// if the leaf node contains multiple words, we will treat each word 
			// as a "leaf node" and set a multinomial over all the words
			// if the leaf node contains only one word, so this step will be jumped over.
			if (numWords > 1) {
				assert numChildren == 0;
				n.initializePrior(numWords);
				for (int ii = 0; ii < numWords; ii++) {
					n.setPrior(ii, n.getWordCount(ii));
				}				
			}
			
			// then normalize and scale
			n.normalizePrior();
		}
	}
	
	/**
	 * the entrance of this class
	 */
	public void initialize(String treeFiles, String hyperFile, ArrayList<String> vocab) {
		this.loadHyperparams(hyperFile);
		this.loadTree(treeFiles, vocab);
		
		TIntArrayList traversed = new TIntArrayList ();
		TIntArrayList next_pointers = new TIntArrayList ();
		//this.maxDepth = this.searchDepthFirst(0, 0, traversed, next_pointers);
        this.maxDepth = this.searchDepthFirst(0, this.root, traversed, next_pointers);
        this.setPrior();
        
        //System.out.println("**************************");
        // check the word paths
        System.out.println("Number of words: " + this.wordPaths.size());
        //System.out.println("Initialized paths");
        
		/*        
        for (TIntObjectIterator<ArrayList<Path>> it = this.wordPaths.iterator(); it.hasNext(); ) {
        	it.advance();
        	ArrayList<Path> paths = it.value();
        	System.out.print(it.key() + ", " + vocab.get(it.key()));
        	//System.out.print(it.key() + ", " + vocab.lookupObject(it.key()));
        	for (int ii = 0; ii < paths.size(); ii++) {
        		Path p = paths.get(ii);
        		System.out.print(", Path " + ii);
        		System.out.print(", Path nodes list: " + p.getNodes());
        		System.out.println(", Path final word: " + p.getFinalWord());
        	}
		}
		System.out.println("**************************");

		// check the prior
		System.out.println("Check the prior");
		for (TIntObjectIterator<Node> it = this.nodes.iterator(); it.hasNext(); ) {
			it.advance();
			if (it.value().getTransitionPrior().size() > 0) {
				System.out.print("Node " + it.key());
				System.out.println(", Transition prior " + it.value().getTransitionPrior());
			}
		}
		System.out.println("**************************");
		*/
		
	}
	
	public int getMaxDepth() {
		return this.maxDepth;
	}
	
	public int getRoot() {
		return this.root;
	}
	
	public TIntObjectHashMap<Node> getNodes() {
		return this.nodes;
	}
	
	public TIntObjectHashMap<ArrayList<Path>> getWordPaths() {
		return this.wordPaths;
	} 
	
	/**
	 * Load vocab
	 */
	public ArrayList<String> readVocab(String vocabFile) {
		
		ArrayList<String> vocab = new ArrayList<String> ();
		
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
					vocab.add(str[1]);
				} else {
					System.out.println("Error! " + strLine);
					return null;
				}
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No vocab file Found!");
		}
		
		return vocab;
	}
	
	/**
	 * test main
	 */
	public static void main(String[] args) throws Exception{
		
		//String treeFiles = "../toy/toy_set1.wn.*";
		//String hyperFile = "../toy/tree_hyperparams";
		//String inputFile = "../input/toy-topic-input.mallet";
		//String vocabFile = "../toy/toy.voc";
		
		//String treeFiles = "../synthetic/synthetic_set1.wn.*";
		//String hyperFile = "../synthetic/tree_hyperparams";
		//String inputFile = "../input/synthetic-topic-input.mallet";
		//String vocabFile = "../synthetic/synthetic.voc";
		
		String treeFiles = "input/denews.all.wn";
		String hyperFile = "input/denews.hyper";
		String inputFile = "input/denews-topic-input.mallet";
		String vocabFile = "input/denews.filter.voc";		
		
		PriorTree tree = new PriorTree();
		ArrayList<String> vocab = tree.readVocab(vocabFile);
		
		InstanceList ilist = InstanceList.load (new File(inputFile));
		tree.initialize(treeFiles, hyperFile, vocab);
	}

}
