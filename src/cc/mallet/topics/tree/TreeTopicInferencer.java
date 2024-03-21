package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;


/**
 * An implementation of inferencer for tree-based topic model
 * Followed the example in "cc.mallet.topics.TopicInferencer"
 * 
 * @author Yuening Hu
 */

public class TreeTopicInferencer implements Serializable {
	
	int TOPIC_BITS = TreeTopicModelFastSortW.TOPIC_BITS;
	
	int numTopics;
	double[] alpha;
	ArrayList<String> vocab;
	HashSet<String> removed;
	TreeTopicModel topics;
	String modelType;
	
	Randoms random;
	boolean sorted;
	
	public TreeTopicInferencer(TreeTopicModel topics, ArrayList<String> vocab, HashSet<String> removed, double[] alpha) {
		this.numTopics = topics.numTopics;
		this.vocab = vocab;
		this.removed = removed;
		this.alpha = alpha;
		this.topics = topics;
		this.random = new Randoms();
		
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

	public String toString() {
		String tmp = "";
		tmp += "numTopics: " + numTopics + "\n";
		tmp += "sorted: " + this.sorted + "\n";
		tmp += "removed: " + this.removed.size() + "\n";
		tmp += "nonzero: " + this.topics.nonZeroPaths.size() + "\n";
		tmp += "nonzerobubblesorted: " + this.topics.nonZeroPathsBubbleSorted.size() + "\n";
		return tmp;
	}
	
	/** 
	 *  Use Gibbs sampling to infer a topic distribution.
	 *  Topics are initialized to the (or a) most probable topic
	 *  for each token.
	 */
	public double[] getSampledDistribution(Instance instance, int numIterations, int interval) {

		FeatureSequence alltokens = (FeatureSequence) instance.getData();
		ArrayList<Integer> tokens = new ArrayList<Integer> ();
		for (int position = 0; position < alltokens.size(); position++) {
			String word = (String) alltokens.getObjectAtPosition(position);
			if(this.vocab.indexOf(word) >= 0 && !this.removed.contains(word)) {
				int type = this.vocab.indexOf(word);
				tokens.add(type);
			}
		}
		
		int docLength = tokens.size();
		int[] localtopics = new int[docLength];
		int[] localpaths = new int[docLength];
		TIntIntHashMap localTopicCounts = new TIntIntHashMap();
		
		// Initialize all positions to the most common topic for that type.
		for (int position = 0; position < docLength; position++) {
			int type = tokens.get(position);
			
			int tt = -1;
			int pp = -1;
			
			if (this.sorted) {
				ArrayList<int[]> pairs = this.topics.nonZeroPathsBubbleSorted.get(type);
				int[] pair = pairs.get(0);
				int key = pair[0];
				tt = key >> TOPIC_BITS;
				pp = key - (tt << TOPIC_BITS);
			} else {
				HIntIntIntHashMap pairs1 = this.topics.nonZeroPaths.get(type);
				int maxcount  = 0;
				for(int topic : pairs1.getKey1Set()) {
					int[] paths = pairs1.get(topic).keys();
					for (int jj = 0; jj < paths.length; jj++) {
						int path = paths[jj];
						int count = pairs1.get(topic,  path);
						if (count > maxcount) {
							maxcount = count;
							tt = topic;
							pp = path;
						}
					}
				}
			}
			
			localtopics[position] = tt;
			localpaths[position] = pp;
			localTopicCounts.adjustOrPutValue(tt, 1, 1);
		}
		
//		String tmpout = "";
//		for(int tt : localTopicCounts.keys()) {
//			tmpout += tt + " " + localTopicCounts.get(tt) + "; ";
//		}
//		System.out.println(tmpout);
		
		double[] result = new double[numTopics];
		double sum = 0.0;
		
		for (int iteration = 1; iteration <= numIterations; iteration++) {                                                       
			for (int position = 0; position < docLength; position++) {
				int type = tokens.get(position);
				
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
				
//				if (iteration % interval == 0) {
//					// Save a sample
//					for (int topic=0; topic < numTopics; topic++) {
//						if (localTopicCounts.containsKey(topic)) {
//							result[topic] = alpha[topic] + localTopicCounts.get(topic);
//						} else {
//							result[topic] = alpha[topic];
//						}
//						sum += result[topic];
//					}
//				}
			}
		}
		

		// save at least once
		if (sum == 0.0) {
			for (int topic=0; topic < numTopics; topic++) {
				if (localTopicCounts.containsKey(topic)) {
					result[topic] = alpha[topic] + localTopicCounts.get(topic);
				} else {
					result[topic] = alpha[topic];
				}
				sum += result[topic];
			}
		}

		// Normalize
		for (int topic=0; topic < numTopics; topic++) {
            result[topic] /= sum;
		}
		
		return result;
	}
	
	/**
	 *  Infer topics for the provided instances and
	 *   write distributions to the provided file.
	 *
	 *  @param instances
	 *  @param distributionsFile
	 *  @param numIterations The total number of iterations of sampling per document
	 *  @param interval      The number of iterations between saved samples
	 */
	public void writeInferredDistributions(InstanceList instances, 
										   File distributionsFile,
										   int numIterations, int interval) throws IOException {
		
		if(this.modelType.indexOf("fast-est") < 0) {
			System.out.println("%%%%%%%%%%%%%%%%%%%");
			System.out.println("Your current tree-model-type");
			System.out.println("\t " + this.modelType); 
			System.out.println("is not supported by inferencer. ");
			System.out.println("Inferencer only supports the following tree-model-type: ");
			System.out.println("\t fast-est \n\t fast-est-sortW \n\t fast-est-sortD \n\t fast-est-sortD-sortW");
			System.out.println("%%%%%%%%%%%%%%%%%%%");
			return;
		}
		
		PrintWriter out = new PrintWriter(distributionsFile);
		
		out.print ("#doc source topic proportion ...\n");

        IDSorter[] sortedTopics = new IDSorter[ numTopics ];
		for (int topic = 0; topic < numTopics; topic++) {
            // Initialize the sorters with dummy values
			sortedTopics[topic] = new IDSorter(topic, topic);
        }

		int doc = 0;

		for (Instance instance: instances) {
			
			double[] topicDistribution =
				getSampledDistribution(instance, numIterations, interval);
			out.print (doc); out.print (' ');

			// Print the Source field of the instance
            if (instance.getSource() != null) {
				out.print (instance.getSource());
            } else {
                out.print ("null-source");
            }
            out.print (' ');

			for (int topic = 0; topic < numTopics; topic++) {
                sortedTopics[topic].set(topic, topicDistribution[topic]);
            }
			Arrays.sort(sortedTopics);
			
			for (int i = 0; i < numTopics; i++) {
                out.print (sortedTopics[i].getID() + " " +
						   sortedTopics[i].getWeight() + " ");
            }
            out.print (" \n");
			doc++;
		}
		out.close();
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
        this.vocab = (ArrayList<String>) in.readObject();
        this.removed = (HashSet<String>) in.readObject();
        this.topics = (TreeTopicModel) in.readObject();
	}

	public static TreeTopicInferencer read (File f) throws Exception {

		TreeTopicInferencer inferencer = null;

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
        inferencer = (TreeTopicInferencer) ois.readObject();
		ois.close();
        return inferencer;
    }
}
