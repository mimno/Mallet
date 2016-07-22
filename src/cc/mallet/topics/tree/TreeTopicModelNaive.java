package cc.mallet.topics.tree;

import java.util.ArrayList;
import java.util.Random;

import cc.mallet.types.Dirichlet;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

/**
 * This class extends the tree topic model
 * It implemented the four abstract methods in a naive way: given a word, 
 * (1) compute the probability for each topic every time directly
 * 
 * @author Yuening Hu
 */

public class TreeTopicModelNaive extends TreeTopicModel{
	
	public TreeTopicModelNaive(int numTopics, Random random) {
		super(numTopics, random);
	}
	
	/**
	 * Just calls changeCountOnly(), nothing else.
	 */
	public void changeCount(int topic, int word, int path, int delta) {
//		TIntArrayList path_nodes = this.wordPaths.get(word, path_index);
//		TopicTreeWalk tw = this.traversals.get(topic);
//		tw.changeCount(path_nodes, delta);
		this.changeCountOnly(topic, word, path, delta);
	}
	
	/**
	 * Given a word and the topic counts in the current document,
	 * this function computes the probability per path per topic directly
	 * according to the sampleing equation.
	 */
	public double computeTopicTerm(double[] alpha, TIntIntHashMap local_topic_counts, int word, ArrayList<double[]> dict) {
		double norm = 0.0;
		int[] paths = this.getWordPathIndexSet(word);
		for(int tt = 0; tt < this.numTopics; tt++) {
			double topic_alpha = alpha[tt];
			int topic_count = local_topic_counts.get(tt);
			for (int pp = 0; pp < paths.length; pp++) {
				int path_index = paths[pp];
				double val = this.computeTopicPathProb(tt, word, path_index);
				val *= (topic_alpha + topic_count);
				double[] tmp = {tt, path_index, val};
				dict.add(tmp);
				norm += val;
			}
		}
		return norm;
	}
	
	/**
	 * No parameter needs to be updated.
	 */
	public void updateParams() {
	}
	
	/**
	 * Not actually used.
	 */
	public double getNormalizer(int topic, int path) {
		return 0;
	}
}
