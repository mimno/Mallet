package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;

import java.util.Random;

/**
 * This class extends the tree topic model fast class
 * Only add one more function, it computes the smoothing for each word
 * only based on the prior (treat the real count as zero), so it 
 * serves as the upper bound of smoothing.
 * 
 * @author Yuening Hu
 */

public class TreeTopicModelFastEst extends TreeTopicModelFast {
	public TreeTopicModelFastEst(int numTopics, Random random) {
		super(numTopics, random);
		this.smoothingEst = new TIntDoubleHashMap();
	}
	
	/**
	 * This function computes the upper bound of smoothing bucket. 
	 */
	public void computeSmoothingEst(double[] alpha) {
		for(int ww : this.wordPaths.getKey1Set()) {
			this.smoothingEst.put(ww, 0.0);
			for(int tt = 0; tt < this.numTopics; tt++) {
				for(int pp : this.wordPaths.get(ww).keys()) {
					TIntArrayList path_nodes = this.wordPaths.get(ww, pp);
					double prob = 1.0;
					for(int nn = 0; nn < path_nodes.size() - 1; nn++) {
						int parent = path_nodes.get(nn);
						int child = path_nodes.get(nn+1);
						prob *= this.beta.get(parent, child) / this.betaSum.get(parent);
					}
					prob *= alpha[tt];
					this.smoothingEst.adjustValue(ww, prob);
				}
			}
		}
	}
}
