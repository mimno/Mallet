package cc.mallet.topics.tree;

import java.io.Serializable;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;

/**
 * This class counts each node and each edge for a topic with tree structure. 
 * 
 * @author Yuening Hu
 */

public class TopicTreeWalk implements Serializable {
	
	// *** To be sorted
	HIntIntIntHashMap counts;
	TIntIntHashMap nodeCounts;
	
	public TopicTreeWalk() {
		this.counts = new HIntIntIntHashMap();
		this.nodeCounts = new TIntIntHashMap();
	}

	/**
	 * Given a path (a list of nodes), increase the nodes and edges counts by 
	 * the specified amount. When a node count is changed from zero or changed
	 * to zero, return this node. (When this happens, the non-zero path of this
	 * node might need to be changed, that's why we need this list.)
	 */
	public int[] changeCount(TIntArrayList path_nodes, int increment) {
		for (int nn = 0; nn < path_nodes.size()-1; nn++) {
			int parent = path_nodes.get(nn);
			int child = path_nodes.get(nn+1);
			this.counts.adjustOrPutValue(parent, child, increment, increment);
		}
		
		// keep the nodes whose counts is changed from zero or changed to zero
		TIntHashSet affected_nodes = new TIntHashSet();
		
		for (int nn = 0; nn < path_nodes.size(); nn++) {
			int node = path_nodes.get(nn);
			if (! this.nodeCounts.contains(node)) {
				this.nodeCounts.put(node, 0);
			}
			
			int old_count = this.nodeCounts.get(node);
			this.nodeCounts.adjustValue(node, increment);
			int new_count = this.nodeCounts.get(node);
			
			// keep the nodes whose counts is changed from zero or changed to zero
			if (nn != 0 && (old_count == 0 || new_count == 0)) {
				affected_nodes.add(node);
			}
		}
		
		if (affected_nodes.size() > 0) {
			return affected_nodes.toArray();
		} else {
			return null;
		}
	}
	
	/**
	 * Return an edge count.
	 */
	public int getCount(int key1, int key2) {
		if (this.counts.contains(key1, key2)) {
			return this.counts.get(key1, key2);
		}
		return 0;
	}
	
	/**
	 * Return a node count.
	 */
	public int getNodeCount(int key) {
		if (this.nodeCounts.contains(key)) {
			return this.nodeCounts.get(key);
		}
		return 0;
	}
	
}
