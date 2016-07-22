package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import topicmod_projects_ldawn.WordnetFile.WordNetFile;
import topicmod_projects_ldawn.WordnetFile.WordNetFile.Synset;
import topicmod_projects_ldawn.WordnetFile.WordNetFile.Synset.Word;


/**
 * Converts a set of user-selected constraints into Protocol Buffer form.
 * This is an adaptation of Yuening's Python code that does the same thing.
 * Following the style of Brianna's original code of OntologyWriter.java.
 * 
 * @author Yuening Hu
 */

public class OntologyWriter {
	private Map<Integer, Set<Integer>> parents; 
	
	private int numFiles;
	private String filename;
		
	private int root;
	private Map<Integer, Map<String, Integer>> vocab;
	private boolean propagateCounts;
	
	private int maxLeaves;
	private Map<Integer, Synset.Builder> leafSynsets;
	private Map<Integer, Synset.Builder> internalSynsets;
	private WordNetFile.Builder leafWn;
	private WordNetFile.Builder internalWn;
	private boolean finalized;
	
	static class WordTuple {
		public int id;
		public int language;
		public String word;
		public double count;
	}
	
	static class VocabEntry	{
		public int index;
		public int language;
		public int flag;	
	}
	
	static class Constraint {
		public ArrayList<int[]> cl;
		public ArrayList<int[]> ml;
	}
	
	static class Node {
		public int index;
		public boolean rootChild;
		public String linkType;
		public ArrayList<Integer> children;
		public int[] words;
	}
	
	final static int ENGLISH_ID = 0;
	
	private OntologyWriter(String filename, boolean propagateCounts) {
		this.filename = filename;
		this.propagateCounts = propagateCounts;
		
		vocab = new TreeMap<Integer, Map<String, Integer>>();
		parents = new TreeMap<Integer, Set<Integer>>();
		
		root = -1;
		
		maxLeaves = 10000;
		leafSynsets = new TreeMap<Integer, Synset.Builder>();
		internalSynsets = new TreeMap<Integer, Synset.Builder>();
		leafWn = WordNetFile.newBuilder();
		leafWn.setRoot(-1);
		internalWn = WordNetFile.newBuilder();
		internalWn.setRoot(-1);
		finalized = false;		
	}	
	
	private void addParent(int childId, int parentId) {
		if (!parents.containsKey(childId)) {
			parents.put(childId, new TreeSet<Integer>());
		}
		parents.get(childId).add(parentId);
	}
	
	private List<Integer> getParents(int id) {
		List<Integer> parentList = new ArrayList<Integer>();
		if (!parents.containsKey(id) || parents.get(id).size() == 0) {
			if (this.root < 0)
				this.root = id;
			return new ArrayList<Integer>();
		} else {
			parentList.addAll(parents.get(id));
			for (int parentId : parents.get(id)) {
				parentList.addAll(getParents(parentId));
			}
		}
		return parentList;
	}
	
	private int getTermId(int language, String term) {
		if (!vocab.containsKey(language)) {
			vocab.put(language, new TreeMap<String, Integer>());
		}
		if (!vocab.get(language).containsKey(term)) {
			int length = vocab.get(language).size();
			vocab.get(language).put(term, length);
		}
		return vocab.get(language).get(term);
	}
	  
	private void findRoot(Map<Integer, Synset.Builder> synsets) {
		for (int synsetId : synsets.keySet()) {
			if (synsetId % 1000 == 0) {
				System.out.println("Finalizing " + synsetId);
			}
			for (int parentId : getParents(synsetId)) {
				if (propagateCounts) {
					double hypCount = this.internalSynsets.get(parentId).getHyponymCount();
					double rawCount = synsets.get(synsetId).getRawCount();
					this.internalSynsets.get(parentId).setHyponymCount(hypCount + rawCount);
				}
			}
		}
	}
	
	// Named this so it doesn't conflict with Object.finalize
	private void finalizeMe() throws Exception {
		findRoot(this.leafSynsets);
		for(int id : this.leafSynsets.keySet()) {
			this.leafWn.addSynsets(this.leafSynsets.get(id));
		}
		write(this.leafWn);
		
		findRoot(this.internalSynsets);
		if(this.root < 0) {
			System.out.println("No root has been found!");
			throw new Exception();
		}
		this.internalWn.setRoot(this.root);
		for(int id : this.internalSynsets.keySet()) {
			this.internalWn.addSynsets(this.internalSynsets.get(id));
		}
		write(this.internalWn);
	}
	
	private void write(WordNetFile.Builder wnFile) {
		try {
			String newFilename = filename + "." + numFiles;
			WordNetFile builtFile = wnFile.build();
			builtFile.writeTo(new FileOutputStream(newFilename));
			System.out.println("Serialized version written to: " + newFilename);
			this.numFiles ++;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void addSynset(int numericId, String senseKey, List<Integer> children,
			List<WordTuple> words) {		
		Synset.Builder synset = Synset.newBuilder();
		
		double rawCount = 0.0;
		synset.setOffset(numericId);
		synset.setKey(senseKey);
		
		if(senseKey.startsWith("ML_")) {
			synset.setHyperparameter("ML_");
		} else if(senseKey.startsWith("CL_")) {
			synset.setHyperparameter("CL_");
		} else if(senseKey.startsWith("NL_")) {
			synset.setHyperparameter("NL_");
		} else if(senseKey.startsWith("ROOT")) {
			synset.setHyperparameter("NL_");
		} else if(senseKey.startsWith("LEAF_")) {
			synset.setHyperparameter("NL_");
		} else {
			synset.setHyperparameter("DEFAULT_");
		}
		
		if(children != null) {
			for (int child : children){
				addParent(child, numericId);
				synset.addChildrenOffsets(child);
			}
		}
		
		if(words != null) {
			for (WordTuple tuple : words) {
				Word.Builder word = Word.newBuilder();
				word.setLangId(tuple.language);
				//word.setTermId(getTermId(tuple.language, tuple.word));
				word.setTermId(tuple.id);
				word.setTermStr(tuple.word);
				word.setCount(tuple.count);
				rawCount += tuple.count;
				synset.addWords(word);
				synset.setRawCount(rawCount);
			}
		}
		
		synset.setHyponymCount(rawCount + 0.0);
		
		if(children != null && children.size() > 0) {
			//this.internalWn.addSynsets(synset.clone());
			this.internalSynsets.put(numericId, synset);
		} else {
			//this.leafWn.addSynsets(synset.clone());
			this.leafSynsets.put(numericId, synset);
		}
	}

	private static ArrayList<String> getVocab(String filename) {
		ArrayList<String> vocab = new ArrayList<String>();
		int index = 0;
		try {
			List<String> lines = Utils.readAll(filename);
			for (String line : lines)
			{
				String[] words = line.trim().split("\t");
				if (words.length > 1) {
					vocab.add(words[1]);
				} else {
					System.out.println("Error! " + index);
				}
				index++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return vocab;
	}
	
	private static void readConstraints(String consfilename, ArrayList<String> vocab, 
			ArrayList<int[]> ml, ArrayList<int[]> cl) {
	    //List<Constraint> constraints = new ArrayList<Constraint>();
	    
		try {
			List<String> lines = Utils.readAll(consfilename);
			for (String line : lines) {
				String[] words = line.trim().split("\t");
				int[] indexWords = new int[words.length - 1];
				for(int ii = 1; ii < words.length; ii++) {
					int index = vocab.indexOf(words[ii]);
					if (index == -1) {
						System.out.println("Found words that not contained in the vocab: " + words[ii]);
						throw new Exception();
					}
					indexWords[ii-1] = index;
				}
				
				//for(int ii = 0; ii < indexWords.length; ii++) {
				//	System.out.print(indexWords[ii] + " ");
				//}

				if (words[0].equals("SPLIT_")) {
					cl.add(indexWords);
				} else if (words[0].equals("MERGE_")) {
					ml.add(indexWords);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private static void generateGraph(ArrayList<int[]> cons, int value, HIntIntIntHashMap graph) {
		for (int[] con : cons) {
			for (int w1 : con) {
				for (int w2 : con) {
					if ( w1 != w2) {
						graph.put(w1, w2, value);
						graph.put(w2, w1, value);
					}
				}
			}
		}
	}
	
	private static ArrayList<int[]> BFS(HIntIntIntHashMap graph, int[] consWords, int choice) {
		ArrayList<int[]> connected = new ArrayList<int[]> ();
		TIntIntHashMap visited = new TIntIntHashMap ();
		for (int word : consWords) {
			visited.put(word, 0);
		}
		
		for (int word : consWords) {
			if (visited.get(word) == 0) {
				Stack<Integer> queue = new Stack<Integer> ();
				queue.push(word);
				TIntHashSet component = new TIntHashSet ();
				while (queue.size() > 0) {
					int node = queue.pop();
					component.add(node);
					for(int neighbor : graph.get(node).keys()) {
						if (choice == -1) {
							if (graph.get(node, neighbor) > 0 && visited.get(neighbor) == 0) {
								visited.adjustValue(neighbor, 1);
								queue.push(neighbor);
							}
						} else {
							if (graph.get(node, neighbor) == choice && visited.get(neighbor) == 0) {
								visited.adjustValue(neighbor, 1);
								queue.push(neighbor);
							}
						}
					}
				}
				connected.add(component.toArray());
			}
		}
		
		return connected;
		
	}
	
	private static ArrayList<int[]> mergeML(ArrayList<int[]> ml) {
		HIntIntIntHashMap graph = new HIntIntIntHashMap ();
		generateGraph(ml, 1, graph);
		int[] consWords = graph.getKey1Set();
		
		ArrayList<int[]> ml_merged = BFS(graph, consWords, -1);
		return ml_merged;
	}
	
	private static void mergeCL(ArrayList<Constraint> cl_ml_merged,
			ArrayList<Constraint> ml_remained, HIntIntIntHashMap graph) throws Exception {
		int[] consWords = graph.getKey1Set();
		// get connected components
		ArrayList<int[]> connectedComp = BFS(graph, consWords, -1);
		
		// merge ml to cl
		for(int[] comp : connectedComp) {
			ArrayList<int[]> cl_tmp = BFS(graph, comp, 1);
			ArrayList<int[]> ml_tmp = BFS(graph, comp, 2);
			
			ArrayList<int[]> cl_new = new ArrayList<int[]> ();
			for(int[] cons : cl_tmp) {
				if (cons.length > 1) {
					cl_new.add(cons);
				}
			}
			
			ArrayList<int[]> ml_new = new ArrayList<int[]> ();
			for(int[] cons : ml_tmp) {
				if (cons.length > 1) {
					ml_new.add(cons);
				}
			}
			
			if(cl_new.size() > 0) {
				Constraint cons = new Constraint();
				cons.cl = cl_new;
				cons.ml = ml_new;
				cl_ml_merged.add(cons);
			} else {
				if (ml_new.size() != 1) {
					System.out.println("ml_new.size != 1 && cl_new.size == 0");
					throw new Exception();
				}
				Constraint cons = new Constraint();
				cons.cl = null;
				cons.ml = ml_new;
				ml_remained.add(cons);
			}
		}
	}
	
	private static HIntIntIntHashMap flipGraph(ArrayList<int[]> cl_merged, ArrayList<int[]> ml_merged, 
			HIntIntIntHashMap graph) {
		
		HIntIntIntHashMap flipped = new HIntIntIntHashMap ();
		TIntHashSet consWordsSet = getConsWords(cl_merged);
		TIntHashSet set2 = getConsWords(ml_merged);
		consWordsSet.addAll(set2.toArray());
		int[] consWords = consWordsSet.toArray();
		
		for(int word : consWords) {
			TIntHashSet cl_neighbor = new TIntHashSet ();
			for(int neighbor : graph.get(word).keys()) {
				if(graph.get(word, neighbor) == 1) {
					cl_neighbor.add(neighbor);
				}
			}
			consWordsSet.removeAll(cl_neighbor.toArray());
			for(int nonConnected : consWordsSet.toArray()) {
				flipped.put(word, nonConnected, 1);
			}
			for(int neighbor : graph.get(word).keys()) {
				flipped.put(word, neighbor, 0);
			}
			consWordsSet.addAll(cl_neighbor.toArray());
		}
		
		//printGraph(flipped, "flipped half");
		for(int[] ml : ml_merged) {
			for(int w1 : ml) {
				for(int w2 : flipped.get(w1).keys()) {
					if(flipped.get(w1, w2) > 0) {
						for(int w3 : ml) {
							if(w1 != w3 && flipped.get(w3, w2) == 0) {
								flipped.put(w1, w2, 0);
								flipped.put(w2, w1, 0);
							}
						}
					}
				}
				
				for(int w2 : ml) {
					if (w1 != w2) {
						flipped.put(w1, w2, 2);
						flipped.put(w2, w1, 2);
					}
				}
			}
		}
		return flipped;
	}
	
	private static TIntHashSet getUnion(TIntHashSet set1, TIntHashSet set2) {
		TIntHashSet union = new TIntHashSet ();
		union.addAll(set1.toArray());
		union.addAll(set2.toArray());
		return union;
	}
	
	private static TIntHashSet getDifference(TIntHashSet set1, TIntHashSet set2) {
		TIntHashSet diff = new TIntHashSet ();
		diff.addAll(set1.toArray());
		diff.removeAll(set2.toArray());
		return diff;
	}
	
	private static TIntHashSet getIntersection(TIntHashSet set1, TIntHashSet set2) {
		TIntHashSet inter = new TIntHashSet ();
		for(int ww : set1.toArray()) {
			if(set2.contains(ww)) {
				inter.add(ww);
			}
		}
		return inter;
	}
	
	private static void BronKerBosch_v2(TIntHashSet R, TIntHashSet P, TIntHashSet X, 
			HIntIntIntHashMap G, ArrayList<int[]> C) {
		if(P.size() == 0 && X.size() == 0) {
			if(R.size() > 0) {
				C.add(R.toArray());
			}
			return;
		}
		
		int d = 0;
		int pivot = -1;
		
		TIntHashSet unionPX = getUnion(P, X);
		for(int v : unionPX.toArray()) {
			TIntHashSet neighbors = new TIntHashSet ();
			for(int node : G.get(v).keys()) {
				if(G.get(v, node) > 0 && v != node) {
					neighbors.add(node);
				}
			}
			if(neighbors.size() > d) {
				d = neighbors.size();
				pivot = v;
			}
		}
		
		TIntHashSet neighbors = new TIntHashSet ();
		if(pivot != -1) {
			for(int node : G.get(pivot).keys()) {
				if (G.get(pivot, node) > 0 && pivot != node) {
					neighbors.add(node);
				}
			}
		}
		
		TIntHashSet diffPN = getDifference(P, neighbors);
		for(int v : diffPN.toArray()) {
			TIntHashSet newNeighbors = new TIntHashSet();
			for(int node : G.get(v).keys()) {
				if(G.get(v, node) > 0 && v != node) {
					newNeighbors.add(node);
				}
			}
			
			TIntHashSet unionRV = new TIntHashSet();
			unionRV.add(v);
			unionRV.addAll(R.toArray());
			BronKerBosch_v2(unionRV, getIntersection(P, newNeighbors), getIntersection(X, newNeighbors), G, C);
			
			P.remove(v);
			X.add(v);
		}
	}
	
	private static ArrayList<int[]> generateCliques(HIntIntIntHashMap graph) {
		
		TIntHashSet R = new TIntHashSet ();
		TIntHashSet P = new TIntHashSet ();
		TIntHashSet X = new TIntHashSet ();
		ArrayList<int[]> cliques = new ArrayList<int[]> ();
		P.addAll(graph.getKey1Set());
		
		BronKerBosch_v2(R, P, X, graph, cliques);
		
		return cliques;
	}
	
	private static int generateCLTree(ArrayList<Constraint> cl_merged, 
			HIntIntIntHashMap graph, TIntObjectHashMap<Node> subtree) {
		// the index of root is 0
		int index = 0;
		for(Constraint con : cl_merged) {
			ArrayList<int[]> cl = con.cl;
			ArrayList<int[]> ml = con.ml;
			HIntIntIntHashMap flipped = flipGraph(cl, ml, graph);
			//printGraph(flipped, "flipped graph");
			ArrayList<int[]> cliques = generateCliques(flipped);
			//printArrayList(cliques, "cliques found from flipped graph");
			
			Node cl_node = new Node();
			cl_node.index = ++index;
			cl_node.rootChild = true;
			cl_node.linkType = "CL_";
			cl_node.children = new ArrayList<Integer>();		
			for(int[] clique : cliques) {
				TIntHashSet clique_remained = new TIntHashSet(clique);
				//printHashSet(clique_remained, "clique_remained");
				ArrayList<int[]> ml_tmp = BFS(graph, clique, 2);
				ArrayList<int[]> ml_new = new ArrayList<int[]> ();
				for(int[] ml_con : ml_tmp) {
					if (ml_con.length > 1) {
						ml_new.add(ml_con);
						for(int word : ml_con) {
							clique_remained.remove(word);
						}
					}
				}
				//printHashSet(clique_remained, "clique_remained");
				
				Node node = new Node();
				node.index = ++index;
				node.rootChild = false;
				cl_node.children.add(node.index);
				if(ml_new.size() == 0) {
					node.linkType = "NL_";
					node.children = null;
					node.words = clique_remained.toArray();
				} else if(clique_remained.size() == 0 && ml_new.size() == 1) {
					node.linkType = "ML_";
					node.children = null;
					node.words = ml_new.get(0);
				} else {
					node.linkType = "NL_IN_";
					node.children = new ArrayList<Integer>();
					node.words = null;
					for(int[] ml_clique : ml_new) {
						Node child_node = new Node();
						child_node.index = ++index;
						node.rootChild = false;
						child_node.linkType = "ML_";
						child_node.children = null;
						child_node.words = ml_clique;
						node.children.add(index);
						subtree.put(child_node.index, child_node);
					}
					if(clique_remained.size() > 0) {
						Node child_node = new Node();
						child_node.index = ++index;
						node.rootChild = false;
						child_node.linkType = "NL_";
						child_node.children = null;
						child_node.words = clique_remained.toArray();
						node.children.add(index);
						subtree.put(child_node.index, child_node);						
					}
				}
				subtree.put(node.index, node);
			}
			subtree.put(cl_node.index, cl_node);
		}
		
		return index;
	}
	
	private static int generateMLTree(ArrayList<Constraint> ml_remained, 
			TIntObjectHashMap<Node> subtree, int index) {
		//printConstraintList(ml_remained, "remained");
		int ml_index = index;
		for(Constraint con : ml_remained) {
			for(int[] ml : con.ml) {
				Node node = new Node();
				node.index = ++ml_index;
				node.rootChild = true;
				node.linkType = "ML_";
				node.children = null;
				node.words = ml;
				subtree.put(node.index, node);
			}
		}
		return ml_index;
	}
	
	private static TIntHashSet getConsWords(ArrayList<int[]> cons) {
		TIntHashSet consWords = new TIntHashSet();
		for(int[] con : cons) {
			consWords.addAll(con);
		}
		return consWords;
	}
	
	private static TIntObjectHashMap<Node> mergeAllConstraints(ArrayList<int[]> ml, ArrayList<int[]> cl) {
		//printArrayList(ml, "read in ml");
		//printArrayList(cl, "read in cl");
		
		// merge ml constraints
		ArrayList<int[]> ml_merged = mergeML(ml);
		
		// generate graph
		HIntIntIntHashMap graph = new HIntIntIntHashMap ();
		generateGraph(cl, 1, graph);
		generateGraph(ml_merged, 2, graph);
		//printGraph(graph, "original graph");
		
		// merge cl: notice some ml can be merged into cl, the remained ml will be kept
		ArrayList<Constraint> cl_ml_merged = new ArrayList<Constraint> ();
		ArrayList<Constraint> ml_remained = new ArrayList<Constraint> ();
		try {
			mergeCL(cl_ml_merged, ml_remained, graph);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//printConstraintList(cl_ml_merged, "cl ml merged");
		//printConstraintList(ml_remained, "ml_remained");
		
		TIntObjectHashMap<Node> subtree = new TIntObjectHashMap<Node>();
		int index = generateCLTree(cl_ml_merged, graph, subtree);
		int new_index = generateMLTree(ml_remained, subtree, index);
		
		return subtree;
	}
	
	private static TIntObjectHashMap<Node> noMergeConstraints(ArrayList<int[]> ml, ArrayList<int[]> cl) {
		TIntObjectHashMap<Node> subtree = new TIntObjectHashMap<Node>();
		int index = 0;
		for(int[] cons : ml) {
			Node node = new Node();
			node.index = ++index;
			node.rootChild = true;
			node.linkType = "ML_";
			node.children = null;
			node.words = cons;
			subtree.put(node.index, node);
		}
		
		for(int[] cons : cl) {
			Node node = new Node();
			node.index = ++index;
			node.rootChild = true;
			node.linkType = "CL_";
			node.children = null;
			node.words = cons;
			subtree.put(node.index, node);
		}
		
		return subtree;
	}
	
	private static void printHashSet(TIntHashSet set, String title){
		String tmp = title + ": ";
		for(int word : set.toArray()) {
			tmp += word + " ";
		}
	}
	
	private static void printConstraintList(ArrayList<Constraint> constraints, String title) {
		System.out.println(title + ": ");
		for(Constraint cons : constraints) {
			String tmp = "";
			if(cons.ml != null) {
				tmp = "ml: ";
				for(int[] ml : cons.ml) {
					tmp += "( ";
					for(int ww : ml) {
						tmp += ww + " ";
					}
					tmp += ") ";
				}
			}
			if(cons.cl != null) {
				tmp += "cl: ";
				for(int[] cl : cons.cl) {
					tmp += "( ";
					for(int ww : cl) {
						tmp += ww + " ";
					}
					tmp += ") ";
				}
			}
			System.out.println(tmp);
		}
	}
	
	private static void printGraph(HIntIntIntHashMap graph, String title) {
		System.out.println(title + ": ");
		for(int w1 : graph.getKey1Set()) {
			String tmp = "";
			for(int w2 : graph.get(w1).keys()) {
				tmp += "( " + w1 + " " + w2 + " : " + graph.get(w1, w2) + " ) ";
			}
			System.out.println(tmp);
		}
	}
	
	private static void printArrayList(ArrayList<int[]> result, String title) {
		System.out.println(title + ": ");
	    for(int[] sent : result) {
	    	String line = "";
	    	for (int word : sent) {
	    		line += word + " ";
	    	}
	    	System.out.println(line);
	    }
	}
	
	private static void printSubTree(TIntObjectHashMap<Node> subtree) {
		for(int index : subtree.keys()) {
			Node node = subtree.get(index);
			String tmp  = "Node " + index + " : ";
			tmp += node.linkType + " ";
			if(node.children != null) {
				tmp += "chilren [";
				for(int child : node.children) {
					tmp += child + " ";
				}
				tmp += "]";
			}
			if (node.words != null) {
				tmp += " words [ "; 
				for(int word : node.words) {
					tmp += word + " ";
				}
				tmp += "]";
			}
			System.out.println(tmp);
		}
	}
	
	/**
	 * This is the top-level method that creates the ontology from a set of
	 * Constraint objects.
	 * @param vocabFilename the .voc file corresponding to the data set
	 * being used
	 * @throws Exception 
	 */
	public static void createOntology(String consFilename, String vocabFilename,
			String outputDir, boolean mergeConstraints) throws Exception {
		
		// load vocab
		int LANG_ID = 0;
	    ArrayList<String> vocab = getVocab(vocabFilename);
	    System.out.println("Load vocab size: " + vocab.size());
	    // load constraints and make sure all constraints words are contained in vocab
	    ArrayList<int[]> ml = new ArrayList<int[]> ();
	    ArrayList<int[]> cl = new ArrayList<int[]> ();
	    if(consFilename != null) {
	    	readConstraints(consFilename, vocab, ml, cl);
	    }
	    
	    // merge constraints
	    TIntObjectHashMap<Node> subtree;
	    if (mergeConstraints) {
	    	subtree = mergeAllConstraints(ml, cl);
	    } else {
	    	subtree = noMergeConstraints(ml, cl);
	    }
	    printSubTree(subtree);
	    
	    // get constraint count (If count == 0, it is unconstraint words)
	    int[] vocabFlag = new int[vocab.size()];
	    for(int ii = 0; ii < vocabFlag.length; ii++) {
	    	vocabFlag[ii] = 0;
	    }
	    for(int index : subtree.keys()) {
	    	Node node = subtree.get(index);
	    	if(node.words != null) {
	    		for(int wordIndex : node.words) {
	    			vocabFlag[wordIndex]++;
	    		}
	    	}
	    }	    
	    
	    /////////////////
	    
	    OntologyWriter writer = new OntologyWriter(outputDir, true);
	    List<Integer> rootChildren = new ArrayList<Integer>();
	    
	    int leafIndex = subtree.size();
	    for(int index : subtree.keys()) {
	    	Node node = subtree.get(index);
	    	List<Integer> nodeChildren = null;
	    	ArrayList<WordTuple> nodeWords = null; 
	    	if(node.rootChild) {
	    		rootChildren.add(node.index);
	    	}
	    	if(node.children != null && node.words != null) {
	    		System.out.println("A node has both children and words! Wrong!");
	    		throw new Exception();
	    	} else if(node.children != null) {
	    		nodeChildren = node.children;
	    	} else if(node.words != null) {
	    		if(node.words.length == 1) {
	    	    	nodeWords = new ArrayList<WordTuple> ();
	    			WordTuple wt = new WordTuple();
	    			wt.id = node.words[0];
	    			wt.language = LANG_ID;
	    			wt.word = vocab.get(wt.id);
	    			wt.count = 1.0 / vocabFlag[wt.id];
	    			nodeWords.add(wt);
	    		} else {
		    		nodeChildren = new ArrayList<Integer>();
		    		for(int wordIndex : node.words) {
		    			leafIndex++;
		    			nodeChildren.add(leafIndex);
		    	    	List<Integer> leafChildren = null;
		    	    	ArrayList<WordTuple> leafWords = new ArrayList<WordTuple> ();
		    			WordTuple wt = new WordTuple();
		    			wt.id = wordIndex;
		    			wt.language = LANG_ID;
		    			wt.word = vocab.get(wordIndex);
		    			wt.count = 1.0 / vocabFlag[wordIndex];
		    			leafWords.add(wt);
		    			String name = "LEAF_" + leafIndex + "_" + wt.word;
		    			writer.addSynset(leafIndex, name, leafChildren, leafWords);
		    		}
	    		}
	    	}
	    	
	    	if(node.words != null && node.words.length == 1) {
    			node.linkType = "LEAF_";
    			String name = node.linkType + node.index + "_" + vocab.get(node.words[0]);
	    		writer.addSynset(node.index, name, nodeChildren, nodeWords);
	    	} else {
	    		writer.addSynset(node.index, node.linkType + node.index, nodeChildren, nodeWords);
	    	}
	    	
	    }
	    
	    // Unused words
	    for(int wordIndex = 0; wordIndex < vocabFlag.length; wordIndex++) {
	    	if (vocabFlag[wordIndex] == 0) {
	    		rootChildren.add(++leafIndex);
    	    	List<Integer> leafChildren = null;
    	    	ArrayList<WordTuple> leafWords = new ArrayList<WordTuple> ();
    			WordTuple wt = new WordTuple();
    			wt.id = wordIndex;
    			wt.language = LANG_ID;
    			wt.word = vocab.get(wordIndex);
    			wt.count = 1.0;
    			leafWords.add(wt);
    			String name = "LEAF_" + leafIndex + "_" + wt.word;  			
	    		writer.addSynset(leafIndex, name, leafChildren, leafWords);
	    	}
	    }
	    
	    writer.addSynset(0, "ROOT", rootChildren, null);
	    writer.finalizeMe();
	}
	
	
	public static void main(String[] args) {
		String vocabFn = "input/toy/toy.voc";
		String consFile = "input/toy/toy.cons";
		String outputFn = "input/toy/toy_test.wn";		
		boolean mergeConstraints = true;
		try {
			createOntology(consFile, vocabFn, outputFn, mergeConstraints);
		} catch (Exception e){
			e.printStackTrace();
		}
	}
}
