package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import cc.mallet.topics.tree.TreeTopicSamplerHashD.DocData;
import cc.mallet.util.Randoms;

/**
 * This class defines a fast tree topic sampler, which calls the fast tree topic model.
 * (1) It divides the sampling into three bins: smoothing, topic beta, topic term.
 *     as Yao and Mimno's paper, KDD, 2009.
 * (2) Each time the smoothing, topic beta, and topic term are recomputed.
 * It is faster, because,
 * (1) For topic term, only compute the one with non-zero paths (see TreeTopicModelFast).
 * (2) The normalizer is saved.
 * (3) Topic counts for each documents are ranked.
 * 
 * @author Yuening Hu
 */
public class TreeTopicSamplerFastSortD extends TreeTopicSamplerSortD {
	
	public TreeTopicSamplerFastSortD (int numberOfTopics, double alphaSum, int seed, boolean sort) {
		super(numberOfTopics, alphaSum, seed);
		this.topics = new TreeTopicModelFast(this.numTopics, this.random);
		
		if (sort) {
		    this.topics = new TreeTopicModelFastSortW(this.numTopics, this.random);
		} else {
			this.topics = new TreeTopicModelFast(this.numTopics, this.random);
		}
	}
	
	/**
	 * For each word in a document, firstly covers its topic and path, then sample a
	 * topic and path, and update.
	 */
	public void sampleDoc(int doc_id){
		DocData doc = this.data.get(doc_id);
		//System.out.println("doc " + doc_id);
		
		for(int ii = 0; ii < doc.tokens.size(); ii++) {	
			//int word = doc.tokens.getIndexAtPosition(ii);
			int word = doc.tokens.get(ii);
			
			this.changeTopic(doc_id, ii, word, -1, -1);
						
			double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, word);
			double topic_beta_mass = this.topics.computeTermTopicBetaSortD(doc.topicCounts, word);
			
			ArrayList<double[]> topic_term_score = new ArrayList<double[]> ();
			double topic_term_mass = this.topics.computeTopicTermSortD(this.alpha, doc.topicCounts, word, topic_term_score);
			
			double norm = smoothing_mass + topic_beta_mass + topic_term_mass;
			double sample = this.random.nextDouble();
			//double sample = 0.5;
			sample *= norm;

			int new_topic = -1;
			int new_path = -1;
			
			int[] paths = this.topics.getWordPathIndexSet(word);
			
			// sample the smoothing bin
			if (sample < smoothing_mass) {
				for (int tt = 0; tt < this.numTopics; tt++) {
					for (int pp : paths) {
						double val = alpha[tt] * this.topics.getPathPrior(word, pp);
						val /= this.topics.getNormalizer(tt, pp);
						sample -= val;
						if (sample <= 0.0) {
							new_topic = tt;
							new_path = pp;
							break;
						}
					}
					if (new_topic >= 0) {
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling smoothing!");
			} else {
				sample -= smoothing_mass;
			}
			
			// sample the topic beta bin
			if (new_topic < 0 && sample < topic_beta_mass) {
				
				for(int jj = 0; jj < doc.topicCounts.size(); jj++) {
					int[] current = doc.topicCounts.get(jj);
					int tt = current[0];
					int count = current[1];
					for(int pp : paths) {
						double val = count * this.topics.getPathPrior(word, pp);
						val /= this.topics.getNormalizer(tt, pp);
						sample -= val;
						if (sample <= 0.0) {
							new_topic = tt;
							new_path = pp;
							break;
						}
					}
					if (new_topic >= 0) {
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling topic beta!");
			} else {
				sample -= topic_beta_mass;
			}
			
			// sample the topic term bin
			if (new_topic < 0) {
				for(int jj = 0; jj < topic_term_score.size(); jj++) {
					double[] tmp = topic_term_score.get(jj);
					int tt = (int) tmp[0];
					int pp = (int) tmp[1];
					double val = tmp[2];
					sample -= val;
					if (sample <= 0.0) {
						new_topic = tt;
						new_path = pp;
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling topic term!");
			}
			
			this.changeTopic(doc_id, ii, word, new_topic, new_path);
		}
	}
	
}
