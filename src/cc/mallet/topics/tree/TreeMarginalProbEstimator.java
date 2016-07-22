package cc.mallet.topics.tree;

import gnu.trove.TIntIntHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;

import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;


/**
 * An implementation of left-to-right algorithm for tree-based topic model marginal probability estimators 
 * presented in Wallach et al., "Evaluation Methods for Topic Models", ICML (2009)
 * Followed the example in "cc.mallet.topics.MarginalProbEstimator" by David Mimno
 * 
 * @author Yuening Hu
 */

public class TreeMarginalProbEstimator implements Serializable {
	int TOPIC_BITS = TreeTopicModelFastSortW.TOPIC_BITS;
	
	int numTopics;
	double[] alpha;
	double alphasum;
	ArrayList<String> vocab;
	HashSet<String> removed;
	TreeTopicModel topics;
	String modelType;
	
	Randoms random;
	boolean sorted;
	
	public TreeMarginalProbEstimator(TreeTopicModel topics, ArrayList<String> vocab, HashSet<String> removed, double[] alpha) {
		this.numTopics = topics.numTopics;
		this.vocab = vocab;
		this.removed = removed;
		this.alpha = alpha;
		this.topics = topics;
		this.random = new Randoms();
		
		this.alphasum = 0.0;
		for(int tt = 0; tt < numTopics; tt++) {
			this.alphasum += this.alpha[tt];
		}
		
		if (this.topics.nonZeroPathsBubbleSorted.size() > 0) {
			this.sorted = true;
		} else if (this.topics.nonZeroPaths.size() > 0) {
			this.sorted = false;
		}
		//System.out.println(this.sorted);
	}
	
	public void setRandomSeed(int seed) {
		this.random = new Randoms(seed);
	}
	
	public void setModelType(String modeltype) {
		this.modelType = modeltype;
	}
	

	public double evaluateLeftToRight (InstanceList testing, int numParticles, boolean usingResampling,
			   PrintStream docProbabilityStream) {
		
		if(this.modelType.indexOf("fast-est") < 0) {
			System.out.println("%%%%%%%%%%%%%%%%%%%");
			System.out.println("Your current tree-model-type");
			System.out.println("\t " + this.modelType); 
			System.out.println("is not supported by inferencer. ");
			System.out.println("Inferencer only supports the following tree-model-type: ");
			System.out.println("\t fast-est \n\t fast-est-sortW \n\t fast-est-sortD \n\t fast-est-sortD-sortW");
			System.out.println("%%%%%%%%%%%%%%%%%%%");
			return -1;
		}		
		
		double logNumParticles = Math.log(numParticles);
		double totalLogLikelihood = 0;
		for (Instance instance : testing) {
			
			FeatureSequence tokenSequence = (FeatureSequence) instance.getData();
			
			// read in type index in vocab (different from the alphabet)
			// remove tokens not in vocab
			ArrayList<Integer> tokens = new ArrayList<Integer> ();
			for (int position = 0; position < tokenSequence.size(); position++) {
				String word = (String) tokenSequence.getObjectAtPosition(position);
				if(this.vocab.indexOf(word) >= 0 && !this.removed.contains(word)) {
					int type = this.vocab.indexOf(word);
					tokens.add(type);
				}
			}			
			
			double docLogLikelihood = 0;
			
			double[][] particleProbabilities = new double[ numParticles ][];
			for (int particle = 0; particle < numParticles; particle++) {
				particleProbabilities[particle] =
						leftToRight(tokens, usingResampling);
			}
			
			for (int position = 0; position < particleProbabilities[0].length; position++) {
				double sum = 0;
				for (int particle = 0; particle < numParticles; particle++) {
					sum += particleProbabilities[particle][position];
				}
			
				if (sum > 0.0) { 
					docLogLikelihood += Math.log(sum) - logNumParticles;
				}
			}
		
			if (docProbabilityStream != null) {
				docProbabilityStream.println(docLogLikelihood);
			}
			totalLogLikelihood += docLogLikelihood;
		}
		
		return totalLogLikelihood;
	}
	
	protected double[] leftToRight (ArrayList<Integer> tokens, boolean usingResampling) {
		
		int docLength = tokens.size();
		double[] wordProbabilities = new double[docLength];
		
		int[] localtopics = new int[docLength];
		int[] localpaths = new int[docLength];
		TIntIntHashMap localTopicCounts = new TIntIntHashMap();
		
		int tokensSoFar = 0;
		int type;
		for (int limit = 0; limit < docLength; limit++) {
			if (usingResampling) {
				
				// Iterate up to the current limit
				for (int position = 0; position < limit; position++) {
					type = tokens.get(position);
					
					// change topic counts
					int old_topic = localtopics[position];
					localtopics[position] = -1;
					localpaths[position] = -1;
					localTopicCounts.adjustValue(old_topic, -1);
					
					double smoothing_mass_est = this.topics.smoothingEst.get(type);

					double topic_beta_mass = this.topics.computeTermTopicBeta(localTopicCounts, type);	
					
					ArrayList<double[]> topic_term_score = new ArrayList<double[]>();
					double topic_term_mass = this.topics.computeTopicTerm(this.alpha, localTopicCounts, type, topic_term_score);			
					
					double norm_est = smoothing_mass_est + topic_beta_mass + topic_term_mass;
					double sample = this.random.nextDouble();
					//double sample = 0.5;
					sample *= norm_est;

					int new_topic = -1;
					int new_path = -1;
					
					int[] paths = this.topics.getWordPathIndexSet(type);
					
					// sample the smoothing bin
					if (sample < smoothing_mass_est) {
						double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, type);
						double norm =  smoothing_mass + topic_beta_mass + topic_term_mass;
						sample /= norm_est;
						sample *= norm;
						if (sample < smoothing_mass) {		
							for (int tt = 0; tt < this.numTopics; tt++) {
								for (int pp : paths) {
									double val = alpha[tt] * this.topics.getPathPrior(type, pp);
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
						} else {
							sample -= smoothing_mass;
						}
					} else {
						sample -= smoothing_mass_est;
					}
					
					// sample topic beta bin
					if (new_topic < 0 && sample < topic_beta_mass) {
						for(int tt : localTopicCounts.keys()) {
							for (int pp : paths) {
								double val = localTopicCounts.get(tt) * this.topics.getPathPrior(type, pp);
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
					} else {
						sample -= topic_beta_mass;
					}
					
					// sample topic term bin
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
					}		
					
					// change topic counts
					localtopics[position] = new_topic;
					localpaths[position] = new_path;
					localTopicCounts.adjustOrPutValue(new_topic, 1, 1);
				}
			}
			
			// sample current token at the current limit
			type = tokens.get(limit);
			
			//double smoothing_mass_est = this.topics.smoothingEst.get(type);
			double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, type);
			
			double topic_beta_mass = this.topics.computeTermTopicBeta(localTopicCounts, type);	
			
			ArrayList<double[]> topic_term_score = new ArrayList<double[]>();
			double topic_term_mass = this.topics.computeTopicTerm(this.alpha, localTopicCounts, type, topic_term_score);			
			
			//double norm_est = smoothing_mass_est + topic_beta_mass + topic_term_mass;
			double norm = smoothing_mass + topic_beta_mass + topic_term_mass;
			double sample = this.random.nextDouble();
			sample *= norm;
			
			wordProbabilities[limit] += (smoothing_mass + topic_beta_mass + topic_term_mass) /
					(this.alphasum + tokensSoFar);
			
			tokensSoFar++;

			int new_topic = -1;
			int new_path = -1;
			int[] paths = this.topics.getWordPathIndexSet(type);
			
			// sample the smoothing bin
			if (sample < smoothing_mass) {		
				for (int tt = 0; tt < this.numTopics; tt++) {
					for (int pp : paths) {
						double val = alpha[tt] * this.topics.getPathPrior(type, pp);
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
			} else {
				sample -= smoothing_mass;
			}
			
			// sample topic beta bin
			if (new_topic < 0 && sample < topic_beta_mass) {
				for(int tt : localTopicCounts.keys()) {
					for (int pp : paths) {
						double val = localTopicCounts.get(tt) * this.topics.getPathPrior(type, pp);
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
			} else {
				sample -= topic_beta_mass;
			}
			
			// sample topic term bin
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
			}
			
			// change topic counts
			localtopics[limit] = new_topic;
			localpaths[limit] = new_path;
			localTopicCounts.adjustOrPutValue(new_topic, 1, 1);		
		}
		
		return wordProbabilities;
	}
	
	
	// for serialize
	private static final long serialVersionUID = 1L;
    private static final int CURRENT_SERIAL_VERSION = 0;
    private static final int NULL_INTEGER = -1;
	
    private void writeObject (ObjectOutputStream out) throws IOException {
    	out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeInt(this.numTopics);
		out.writeInt(this.TOPIC_BITS);
		out.writeBoolean(this.sorted);
		out.writeObject(this.modelType);
		out.writeObject(this.alpha);
		out.writeDouble(this.alphasum);
		out.writeObject(this.vocab);
		out.writeObject(this.removed);
		out.writeObject(this.topics);
	}
	
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        this.numTopics = in.readInt();
        this.TOPIC_BITS = in.readInt();
        this.sorted = in.readBoolean();
        this.modelType = (String) in.readObject();
        this.alpha = (double[]) in.readObject();
        this.alphasum = in.readDouble();
        this.vocab = (ArrayList<String>) in.readObject();
        this.removed = (HashSet<String>) in.readObject();
        this.topics = (TreeTopicModel) in.readObject();
	}

	public static TreeMarginalProbEstimator read (File f) throws Exception {

		TreeMarginalProbEstimator estimator = null;

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
		estimator = (TreeMarginalProbEstimator) ois.readObject();
		ois.close();
        return estimator;
    }
	
}
