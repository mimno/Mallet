package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;

/**
 * This class defines a node, which might have children,
 * and a distribution scaled by the node prior over the children.
 * A node is a synset, which might have children nodes and words
 * at the same time.
 * 
 * @author Yuening Hu
 */

public class Node {
	int offset;
	double rawCount;
	double hypoCount;
	String hyperparamName;
	
	TIntArrayList words;
	TDoubleArrayList wordsCount;
	TIntArrayList childOffsets;
	
	int numChildren;
	int numPaths;
	int numWords;
	
	double transitionScalor;
	TDoubleArrayList transitionPrior;
	
	public Node() {
		this.words = new TIntArrayList ();
		this.wordsCount = new TDoubleArrayList ();
		this.childOffsets = new TIntArrayList ();
		this.transitionPrior = new TDoubleArrayList ();
		this.numChildren = 0;
		this.numWords = 0;
		this.numPaths = 0;
	}
	
	/**
	 * Initialize the prior distribution.
	 */
	public void initializePrior(int size) {
		for (int ii = 0; ii < size; ii++ ) {
			this.transitionPrior.add(0.0);
		}
	}
	
	/**
	 * Initialize the prior distribution.
	 */
	public void setOffset(int val) {
		this.offset = val;
	}
	
	/**
	 * set the raw count.
	 */
	public void setRawCount(double count) {
		this.rawCount = count;
	}
	
	/**
	 * set the hypo count.
	 */
	public void setHypoCount(double count) {
		this.hypoCount = count;
	}
	
	/**
	 * set the hyperparameter name of this node.
	 */
	public void setHyperparamName(String name) {
		this.hyperparamName = name;
	}
	
	/**
	 * set the prior scaler.
	 */
	public void setTransitionScalor(double val) {
		this.transitionScalor = val;
	}
	
	/**
	 * set the prior for the given child index.
	 */
	public void setPrior(int index, double value) {
		this.transitionPrior.set(index, value);
	}
	
	/**
	 * Add a child, which is defined by the offset.
	 */
	public void addChildrenOffset(int childOffset) {
		this.childOffsets.add(childOffset);
		this.numChildren += 1;
	}
	
	/**
	 * Add a word.
	 */
	public void addWord(int wordIndex, double wordCount) {
		this.words.add(wordIndex);
		this.wordsCount.add(wordCount);
		this.numWords += 1;
	}
	
	/**
	 * Increase the number of paths.
	 */
	public void addPaths(int inc) {
		this.numPaths += inc;
	}
	
	/**
	 * return the offset of current node.
	 */
	public int getOffset() {
		return this.offset;
	}
	
	/**
	 * return the number of children.
	 */
	public int getNumChildren() {
		return this.numChildren;
	}
	
	/**
	 * return the number of words.
	 */
	public int getNumWords() {
		return this.numWords;
	}
	
	/**
	 * return the child offset given the child index.
	 */
	public int getChild(int child_index) {
		return this.childOffsets.get(child_index);
	}
	
	/**
	 * return the word given the word index.
	 */
	public int getWord(int word_index) {
		return this.words.get(word_index);
	}
	
	/**
	 * return the word count given the word index.
	 */
	public double getWordCount(int word_index) {
		return this.wordsCount.get(word_index);
	}
	
	/**
	 * return the hypocount of the node.
	 */
	public double getHypoCount() {
		return this.hypoCount;
	}
	
	/**
	 * return the transition scalor.
	 */
	public double getTransitionScalor() {
		return this.transitionScalor;
	}
	
	/**
	 * return the scaled transition prior distribution.
	 */
	public TDoubleArrayList getTransitionPrior() {
		return this.transitionPrior;
	}
	
	/**
	 * normalize the prior to be a distribution and then scale it.
	 */
	public void normalizePrior() {
		double norm = 0;
		for (int ii = 0; ii < this.transitionPrior.size(); ii++) {
			norm += this.transitionPrior.get(ii);
		}
		for (int ii = 0; ii < this.transitionPrior.size(); ii++) {
			double tmp = this.transitionPrior.get(ii) / norm;
			tmp *= this.transitionScalor;
			this.transitionPrior.set(ii, tmp);
		}
	}
}
