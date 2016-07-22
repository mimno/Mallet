package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * nonZeroPathsBubbleSorted: Arraylist<int[]> sorted
 *                           sorted[0] = (topic << TOPIC_BITS) + path
 *                           sorted[1] = (masked_path) + real_count
 *                           
 * @author Yuening Hu
 */

public class TreeTopicModelFastSortW extends TreeTopicModelFast {
	
	static int TOPIC_BITS = 16;
	
	public TreeTopicModelFastSortW(int numTopics, Random random) {
		super(numTopics, random);
	}
	
	/**
	 * After adding instances, update the parameters.
	 */
	public void updateParams() {
		
		for(int ww : this.nonZeroPaths.keys()) {
			if (!this.nonZeroPathsBubbleSorted.containsKey(ww)) {
				ArrayList<int[]> sorted = new ArrayList<int[]> ();
				this.nonZeroPathsBubbleSorted.put(ww, sorted);
			}
		}
		for(int tt = 0; tt < this.numTopics; tt++) {
			for(int pp = 0; pp < this.getPathNum(); pp++) {
				this.updatePathMaskedCount(pp, tt);				
			}
			this.computeNormalizer(tt);
		}
		
//		for(int ww : this.nonZeroPaths.keys()) {
//			System.out.println("Word " + ww);
//			ArrayList<int[]> sorted = this.nonZeroPathsBubbleSorted.get(ww);
//			for(int ii = 0; ii < sorted.size(); ii++) {
//				int[] tmp = sorted.get(ii);
//				System.out.println(tmp[0] + " " + tmp[1] + " " + tmp[2] + " " + tmp[3]);
//			}
//		}
	}
	
	protected void updatePathMaskedCount(int path, int topic) {
		TopicTreeWalk tw = this.traversals.get(topic);
		int ww = this.getWordFromPath(path);
		TIntArrayList path_nodes = this.wordPaths.get(ww, path);
		int leaf_node = path_nodes.get(path_nodes.size() - 1);
		int original_count = tw.getNodeCount(leaf_node);
		
		int shift_count = this.INTBITS;
		int count = this.maxDepth - 1;
		int val = 0;
		boolean flag = false;
		
		// note root is not included here
		// if count of a node in the path is larger than 0, denote as "1"
		// else use "0"
		for(int nn = 1; nn < path_nodes.size(); nn++) {
			int node = path_nodes.get(nn);
			shift_count--;
			count--;
			if (tw.getNodeCount(node) > 0) {
				flag = true;
				val += 1 << shift_count;
			}
		}
		
		// if a path is shorter than tree depth, fill in "1"
		// should we fit in "0" ???
		while (flag && count > 0) {
			shift_count--;
			val += 1 << shift_count;
			count--;
		}
		
		val += original_count;
		this.addOrUpdateValue(topic, path, ww, val, false);

	}
	
	private void addOrUpdateValueold(int topic, int path, int word, int newvalue, boolean flag) {
		ArrayList<int[]> sorted = this.nonZeroPathsBubbleSorted.get(word);
		int key = (topic << TOPIC_BITS) + path;
		//remove the old value
		int oldindex = sorted.size();
		int oldvalue = 0;
		for(int ii = 0; ii < sorted.size(); ii++) {
			int[] tmp = sorted.get(ii);
			if(tmp[0] == key) {
				oldvalue = tmp[1];
				sorted.remove(ii);
				break;
			}
		}
		if(oldindex > sorted.size()) {
			oldindex--;
		}
		
		// flag is true, increase value, else just update value
		int value = 0;
		if(flag) {
			value = oldvalue + newvalue;
		} else {
			value = newvalue;
		}
		
		//add the new value
		if (value > 0) {
			int index;
			if (value > oldvalue) {
				index = 0;
				for(int ii = oldindex - 1; ii >= 0; ii--) {
					//System.out.println(ii + " " + oldindex + " " + sorted.size());
					int[] tmp = sorted.get(ii);
					if(value <= tmp[1]) {
						index = ii;
						break;
					}
				}
			} else {
				index = sorted.size();
				for(int ii = oldindex; ii < sorted.size(); ii++) {
					int[] tmp = sorted.get(ii);
					if(value >= tmp[1]) {
						index = ii;
						break;
					}
				}
			}

			int[] newpair = {key, value};
			sorted.add(index, newpair);
		}
	}
	
	private void addOrUpdateValue(int topic, int path, int word, int newvalue, boolean flag) {
		ArrayList<int[]> sorted = this.nonZeroPathsBubbleSorted.get(word);
		int key = (topic << TOPIC_BITS) + path;
		//remove the old value
		int value = 0;
		for(int ii = 0; ii < sorted.size(); ii++) {
			int[] tmp = sorted.get(ii);
			if(tmp[0] == key) {
				value = tmp[1];
				sorted.remove(ii);
				break;
			}
		}
		
		// flag is true, increase value, else just update value
		if(flag) {
			value += newvalue;
		} else {
			value = newvalue;
		}
		
		//add the new value
		if (value > 0) {
			int index = sorted.size();
			for(int ii = 0; ii < sorted.size(); ii++) {
				int[] tmp = sorted.get(ii);
				if(value >= tmp[1]) {
					index = ii;
					break;
				}
			}
			int[] newpair = {key, value};
			sorted.add(index, newpair);
		}
	}
	
	public void changeCount(int topic, int word, int path_index, int delta) {
		TopicTreeWalk tw = this.traversals.get(topic);
		TIntArrayList path_nodes = this.wordPaths.get(word, path_index);
		
		// for affected paths, firstly remove the old values
		// do not consider the root
		for(int nn = 1; nn < path_nodes.size() - 1; nn++) {
			int node = path_nodes.get(nn);
			double tmp = this.betaSum.get(node) + tw.getNodeCount(node);
			tmp = 1 / tmp;
			TIntArrayList paths = this.nodeToPath.get(node);
			updateNormalizer(topic, paths, tmp);
		}
		
		// change the count for each edge per topic
		// return the node index whose count is changed from 0 or to 0
		int[] affected_nodes = tw.changeCount(path_nodes, delta);
		
		// change path count
		this.addOrUpdateValue(topic, path_index, word, delta, true);
		
		// if necessary, change the path mask of the affected nodes
		if (affected_nodes != null && affected_nodes.length > 0) {
			int[] affected_paths = this.findAffectedPaths(affected_nodes);
			for(int ii = 0; ii < affected_paths.length; ii++) {
				this.updatePathMaskedCount(affected_paths[ii], topic);
			}
		}
		
		// for affected paths, update the normalizer
		for(int nn = 1; nn < path_nodes.size() - 1; nn++) {
			int node = path_nodes.get(nn);
			double tmp = this.betaSum.get(node) + tw.getNodeCount(node);
			TIntArrayList paths = this.nodeToPath.get(node);
			updateNormalizer(topic, paths, tmp);
		}
		
		// update the root normalizer
		double val = this.betaSum.get(root) + tw.getNodeCount(root);
		this.rootNormalizer.put(topic, val);
	}
	
	/**
	 * This function computes the topic term bucket.
	 */
	public double computeTopicTerm(double[] alpha, TIntIntHashMap local_topic_counts, int word, ArrayList<double[]> dict) {
		double norm = 0.0;
		ArrayList<int[]> nonzeros = this.nonZeroPathsBubbleSorted.get(word);
		
		// Notice only the nonzero paths are considered
		for(int ii = 0; ii < nonzeros.size(); ii++) {
			int[] tmp = nonzeros.get(ii);
			int key = tmp[0];
			int tt = key >> TOPIC_BITS;
			int pp = key - (tt << TOPIC_BITS);

			double topic_alpha = alpha[tt];
			int topic_count = local_topic_counts.get(tt);

			double val = this.getObservation(tt, word, pp);
			val *= (topic_alpha + topic_count);
			val /= this.getNormalizer(tt, pp);

			//System.out.println(tt + " " + pp + " " + tmp[2] + " " + val);
			
			double[] result = {tt, pp, val};
			dict.add(result);
			
			norm += val;
		}
		
		return norm;
	}
	
	public double computeTopicTermSortD(double[] alpha, ArrayList<int[]> local_topic_counts, int word, ArrayList<double[]> dict) {
		double norm = 0.0;
		ArrayList<int[]> nonzeros = this.nonZeroPathsBubbleSorted.get(word);
		
		
		int[] tmpTopics = new int[this.numTopics];
		for(int jj = 0; jj < this.numTopics; jj++) {
			tmpTopics[jj] = 0;
		}
		for(int jj = 0; jj < local_topic_counts.size(); jj++) {
			int[] current = local_topic_counts.get(jj);
			int tt = current[0];
			tmpTopics[tt] = current[1];
		}
		
		// Notice only the nonzero paths are considered
		for(int ii = 0; ii < nonzeros.size(); ii++) {
			int[] tmp = nonzeros.get(ii);
			int key = tmp[0];
			int tt = key >> TOPIC_BITS;
			int pp = key - (tt << TOPIC_BITS);

			double topic_alpha = alpha[tt];
			int topic_count = tmpTopics[tt];

			double val = this.getObservation(tt, word, pp);
			val *= (topic_alpha + topic_count);
			val /= this.getNormalizer(tt, pp);

			//System.out.println(tt + " " + pp + " " + tmp[2] + " " + val);
			
			double[] result = {tt, pp, val};
			dict.add(result);
			
			norm += val;
		}
		return norm;
	}
}
