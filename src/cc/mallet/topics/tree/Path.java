package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;

/**
 * This class defines a path. 
 * A path is a list of nodes, and the last node emits a word.
 * 
 * @author Yuening Hu
 */

public class Path {
	
	TIntArrayList nodes;
	//TIntArrayList children;
	int finalWord;
	
	public Path () {
		this.nodes = new TIntArrayList();
		this.finalWord = -1;
	}
	
	/**
	 * Add nodes to this path.
	 */
	public void addNodes (TIntArrayList innodes) {
		for (int ii = 0; ii < innodes.size(); ii++) {
			int node_index = innodes.get(ii);
			this.nodes.add(node_index);
		}
	}
	
	/**
	 * Add the final word of this path.
	 */
	public void addFinalWord(int word) {
		this.finalWord = word;
	}
	
	/**
	 * return the node list.
	 */
	public TIntArrayList getNodes() {
		return this.nodes;
	}
	
	/**
	 * return the final word.
	 */
	public int getFinalWord() {
		return this.finalWord;
	}
}
