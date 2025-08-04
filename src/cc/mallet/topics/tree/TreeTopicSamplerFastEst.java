package cc.mallet.topics.tree;

import java.util.ArrayList;

import cc.mallet.topics.tree.TreeTopicSamplerHashD.DocData;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;

/**
 * This class improves the fast sampler based on estimation of smoothing.
 * Most of the time, the smoothing is very small and not worth to recompute since
 * it will hardly be hit. So we use an upper bound for smoothing. 
 * Only if the smoothing bin is hit, the actual smoothing is computed and resampled.
 * 
 * @author Yuening Hu
 */

public class TreeTopicSamplerFastEst extends TreeTopicSamplerHashD{
	
	public TreeTopicSamplerFastEst (int numberOfTopics, double alphaSum, int seed, boolean sort) {
		super(numberOfTopics, alphaSum, seed);
		
		if (sort) {
		    this.topics = new TreeTopicModelFastEstSortW(this.numTopics, this.random);
		} else {
			this.topics = new TreeTopicModelFastEst(this.numTopics, this.random);
		}
	}
	
	/**
	 * Use an upper bound for smoothing. Only if the smoothing 
	 * bin is hit, the actual smoothing is computed and resampled.
	 */
	public void sampleDoc(int doc_id) {
		DocData doc = this.data.get(doc_id);
		//System.out.println("doc " + doc_id);
		//int[] tmpstats = this.stats.get(this.stats.size()-1);
		
		for(int ii = 0; ii < doc.tokens.size(); ii++) {	
			//int word = doc.tokens.getIndexAtPosition(ii);
			int word = doc.tokens.get(ii);
						
			this.changeTopic(doc_id, ii, word, -1, -1);
			
			//double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, word);
			double smoothing_mass_est = this.topics.smoothingEst.get(word);
			
			double topic_beta_mass = this.topics.computeTermTopicBeta(doc.topicCounts, word);	
			
			ArrayList<double[]> topic_term_score = new ArrayList<double[]>();
			double topic_term_mass = this.topics.computeTopicTerm(this.alpha, doc.topicCounts, word, topic_term_score);			
			
			double norm_est = smoothing_mass_est + topic_beta_mass + topic_term_mass;
			double sample = this.random.nextDouble();
			//double sample = 0.5;
			sample *= norm_est;

			int new_topic = -1;
			int new_path = -1;
			
			int[] paths = this.topics.getWordPathIndexSet(word);
			
			// sample the smoothing bin
			if (sample < smoothing_mass_est) {
				//tmpstats[0] += 1;
				double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, word);
				double norm =  smoothing_mass + topic_beta_mass + topic_term_mass;
				sample /= norm_est;
				sample *= norm;
				if (sample < smoothing_mass) {
					//tmpstats[1] += 1;
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
			} else {
				sample -= smoothing_mass_est;
			}

			// sample topic beta bin
			if (new_topic < 0 && sample < topic_beta_mass) {
				//tmpstats[2] += 1;
				for(int tt : doc.topicCounts.keys()) {
					for (int pp : paths) {
						double val = doc.topicCounts.get(tt) * this.topics.getPathPrior(word, pp);
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
			
			
			// sample topic term bin
			if (new_topic < 0) {
				//tmpstats[3] += 1;
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
	
	/**
	 * Before sampling start, compute smoothing upper bound for each word.
	 */
	public void estimate(int numIterations, String outputFolder, int outputInterval, int topWords) {
		if(this.topics instanceof TreeTopicModelFastEst) {
			TreeTopicModelFastEst tmp = (TreeTopicModelFastEst) this.topics;
			tmp.computeSmoothingEst(this.alpha);
		} else if (this.topics instanceof TreeTopicModelFastEstSortW) {
			TreeTopicModelFastEstSortW tmp = (TreeTopicModelFastEstSortW) this.topics;
			tmp.computeSmoothingEst(this.alpha);
		}
		
		super.estimate(numIterations, outputFolder, outputInterval, topWords);
	}
	
}
