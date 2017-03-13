package cc.mallet.topics;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.*;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;

import com.carrotsearch.hppc.ObjectDoubleHashMap;
import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.cursors.IntIntCursor;

public class HierarchicalLDA implements Serializable {

    InstanceList instances;
    InstanceList testing;

    NCRPNode rootNode, node;

    int numLevels;
    int numDocuments;
    int numTypes;

    double alpha; // smoothing on topic distributions
    double gamma; // "imaginary" customers at the next, as yet unused table
    double eta;   // smoothing on word distributions
    double etaSum;

    int[][] levels; // indexed < doc, token >
    NCRPNode[] documentLeaves; // currently selected path (ie leaf node) through the NCRP tree

	int totalNodes = 0;

	String stateFile = "hlda.state";

    Randoms random;

	boolean showProgress = true;
	
	int displayTopicsInterval = 50;
	int numWordsToDisplay = 10;

    public HierarchicalLDA () {
		alpha = 10.0;
		gamma = 1.0;
		eta = 0.1;
    }

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	public void setGamma(double gamma) {
		this.gamma = gamma;
	}

	public void setEta(double eta) {
		this.eta = eta;
	}

	public void setStateFile(String stateFile) {
		this.stateFile = stateFile;
	}

	public void setTopicDisplay(int interval, int words) {
		displayTopicsInterval = interval;
		numWordsToDisplay = words;
	}

	/**  
	 *  This parameter determines whether the sampler outputs 
	 *   shows progress by outputting a character after every iteration.
	 */
	public void setProgressDisplay(boolean showProgress) {
		this.showProgress = showProgress;
	}

    public void initialize(InstanceList instances, InstanceList testing,
						   int numLevels, Randoms random) {
		this.instances = instances;
		this.testing = testing;
		this.numLevels = numLevels;
		this.random = random;

		if (! (instances.get(0).getData() instanceof FeatureSequence)) {
			throw new IllegalArgumentException("Input must be a FeatureSequence, using the --feature-sequence option when impoting data, for example");
		}

		numDocuments = instances.size();
		numTypes = instances.getDataAlphabet().size();
	
		etaSum = eta * numTypes;

		// Initialize a single path

		NCRPNode[] path = new NCRPNode[numLevels];

		rootNode = new NCRPNode(numTypes);

		levels = new int[numDocuments][];
		documentLeaves = new NCRPNode[numDocuments];

		// Initialize and fill the topic pointer arrays for 
		//  every document. Set everything to the single path that 
		//  we added earlier.
		for (int doc=0; doc < numDocuments; doc++) {
            FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();
            int seqLen = fs.getLength();

			path[0] = rootNode;
			rootNode.customers++;
			for (int level = 1; level < numLevels; level++) {
				path[level] = path[level-1].select();
				path[level].customers++;
			}
			node = path[numLevels - 1];
	    
			levels[doc] = new int[seqLen];
			documentLeaves[doc] = node;

			for (int token=0; token < seqLen; token++) {
				int type = fs.getIndexAtPosition(token);
				levels[doc][token] = random.nextInt(numLevels);
				node = path[ levels[doc][token] ];
				node.totalTokens++;
				node.typeCounts[type]++;
			}
		}
	}

	public void estimate(int numIterations) {
		for (int iteration = 1; iteration <= numIterations; iteration++) {
			for (int doc=0; doc < numDocuments; doc++) {
				samplePath(doc, iteration);
			}
			for (int doc=0; doc < numDocuments; doc++) {
				sampleTopics(doc);
			}
			
			if (showProgress) {
				System.out.print(".");
				if (iteration % 50 == 0) {
					System.out.println(" " + iteration);
				}
			}

			if (iteration % displayTopicsInterval == 0) {
				printNodes();
			}
		}
    }

    public void samplePath(int doc, int iteration) {
		NCRPNode[] path = new NCRPNode[numLevels];
		NCRPNode node;
		int level, token, type, topicCount;
		double weight;

		node = documentLeaves[doc];
		for (level = numLevels - 1; level >= 0; level--) {
			path[level] = node;
			node = node.parent;
		}

		documentLeaves[doc].dropPath();

		ObjectDoubleHashMap<NCRPNode> nodeWeights = 
			new ObjectDoubleHashMap<NCRPNode>();
	
		// Calculate p(c_m | c_{-m})
		calculateNCRP(nodeWeights, rootNode, 0.0);

		// Add weights for p(w_m | c, w_{-m}, z)
	
		// The path may have no further customers and therefore
		//  be unavailable, but it should still exist since we haven't
		//  reset documentLeaves[doc] yet...
	
		IntIntHashMap[] typeCounts = new IntIntHashMap[numLevels];

		int[] docLevels;

		for (level = 0; level < numLevels; level++) {
			typeCounts[level] = new IntIntHashMap();
		}

		docLevels = levels[doc];
		FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();
	    
		// Save the counts of every word at each level, and remove
		//  counts from the current path

		for (token = 0; token < docLevels.length; token++) {
			level = docLevels[token];
			type = fs.getIndexAtPosition(token);
	    
			if (! typeCounts[level].containsKey(type)) {
				typeCounts[level].put(type, 1);
			}
			else {
				typeCounts[level].addTo(type, 1);
			}

			path[level].typeCounts[type]--;
			assert(path[level].typeCounts[type] >= 0);
	    
			path[level].totalTokens--;	    
			assert(path[level].totalTokens >= 0);
		}

		// Calculate the weight for a new path at a given level.
		double[] newTopicWeights = new double[numLevels];
		for (level = 1; level < numLevels; level++) {  // Skip the root...
			int totalTokens = 0;

			for (IntIntCursor keyVal : typeCounts[level]) {
				for (int i=0; i< keyVal.value; i++) {
					newTopicWeights[level] += 
						Math.log((eta + i) / (etaSum + totalTokens));
					totalTokens++;
				}
			}

			//if (iteration > 1) { System.out.println(newTopicWeights[level]); }
		}
	
		calculateWordLikelihood(nodeWeights, rootNode, 0.0, typeCounts, newTopicWeights, 0, iteration);

		Object[] objectArray = nodeWeights.keys().toArray();
		NCRPNode[] nodes = Arrays.copyOf(objectArray, objectArray.length, NCRPNode[].class);
		double[] weights = new double[nodes.length];
		double sum = 0.0;
		double max = Double.NEGATIVE_INFINITY;

		// To avoid underflow, we're using log weights and normalizing the node weights so that 
		//  the largest weight is always 1.
		for (int i=0; i<nodes.length; i++) {
			if (nodeWeights.get(nodes[i]) > max) {
				max = nodeWeights.get(nodes[i]);
			}
		}

		for (int i=0; i<nodes.length; i++) {
			weights[i] = Math.exp(nodeWeights.get(nodes[i]) - max);

			/*
			  if (iteration > 1) {
			  if (nodes[i] == documentLeaves[doc]) {
			  System.out.print("* ");
			  }
			  System.out.println(((NCRPNode) nodes[i]).level + "\t" + weights[i] + 
			  "\t" + nodeWeights.get(nodes[i]));
			  }
			*/

			sum += weights[i];
		}

		//if (iteration > 1) {System.out.println();}

		node = nodes[ random.nextDiscrete(weights, sum) ];

		// If we have picked an internal node, we need to 
		//  add a new path.
		if (! node.isLeaf()) {
			node = node.getNewLeaf();
		}
	
		node.addPath();
		documentLeaves[doc] = node;

		for (level = numLevels - 1; level >= 0; level--) {

			for (IntIntCursor keyVal: typeCounts[level]) {
				node.typeCounts[keyVal.key] += keyVal.value;
				node.totalTokens += keyVal.value;
			}

			node = node.parent;
		}
    }

    public void calculateNCRP(ObjectDoubleHashMap<NCRPNode> nodeWeights, 
							  NCRPNode node, double weight) {
		for (NCRPNode child: node.children) {
			calculateNCRP(nodeWeights, child,
						  weight + Math.log((double) child.customers / (node.customers + gamma)));
		}

		nodeWeights.put(node, weight + Math.log(gamma / (node.customers + gamma)));
    }

    public void calculateWordLikelihood(ObjectDoubleHashMap<NCRPNode> nodeWeights,
										NCRPNode node, double weight, 
										IntIntHashMap[] typeCounts, double[] newTopicWeights,
										int level, int iteration) {
	
		// First calculate the likelihood of the words at this level, given
		//  this topic.
		double nodeWeight = 0.0;
		int totalTokens = 0;
	
		//if (iteration > 1) { System.out.println(level + " " + nodeWeight); }

		for (IntIntCursor keyVal: typeCounts[level]) {
			for (int i=0; i<keyVal.value; i++) {
				nodeWeight +=
					Math.log((eta + node.typeCounts[keyVal.key] + i) /
							 (etaSum + node.totalTokens + totalTokens));
				totalTokens++;

				/*
				  if (iteration > 1) {
				  System.out.println("(" +eta + " + " + node.typeCounts[type] + " + " + i + ") /" + 
				  "(" + etaSum + " + " + node.totalTokens + " + " + totalTokens + ")" + 
				  " : " + nodeWeight);
				  }
				*/

			}
		}

		//if (iteration > 1) { System.out.println(level + " " + nodeWeight); }

		// Propagate that weight to the child nodes

		for (NCRPNode child: node.children) {
            calculateWordLikelihood(nodeWeights, child, weight + nodeWeight,
									typeCounts, newTopicWeights, level + 1, iteration);
        }

		// Finally, if this is an internal node, add the weight of
		//  a new path

		level++;
		while (level < numLevels) {
			nodeWeight += newTopicWeights[level];
			level++;
		}

		nodeWeights.addTo(node, nodeWeight);

    }

    /** Propagate a topic weight to a node and all its children.
		weight is assumed to be a log.
	*/
    public void propagateTopicWeight(ObjectDoubleHashMap<NCRPNode> nodeWeights,
									 NCRPNode node, double weight) {
		if (! nodeWeights.containsKey(node)) {
			// calculating the NCRP prior proceeds from the
			//  root down (ie following child links),
			//  but adding the word-topic weights comes from
			//  the bottom up, following parent links and then 
			//  child links. It's possible that the leaf node may have
			//  been removed just prior to this round, so the current
			//  node may not have an NCRP weight. If so, it's not 
			//  going to be sampled anyway, so ditch it.
			return;
		}
	
		for (NCRPNode child: node.children) {
			propagateTopicWeight(nodeWeights, child, weight);
		}

		nodeWeights.addTo(node, weight);
    }

    public void sampleTopics(int doc) {
		FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();
		int seqLen = fs.getLength();
		int[] docLevels = levels[doc];
		NCRPNode[] path = new NCRPNode[numLevels];
		NCRPNode node;
		int[] levelCounts = new int[numLevels];
		int type, token, level;
		double sum;

		// Get the leaf
		node = documentLeaves[doc];
		for (level = numLevels - 1; level >= 0; level--) {
			path[level] = node;
			node = node.parent;
		}

		double[] levelWeights = new double[numLevels];

		// Initialize level counts
		for (token = 0; token < seqLen; token++) {
			levelCounts[ docLevels[token] ]++;
		}

		for (token = 0; token < seqLen; token++) {
			type = fs.getIndexAtPosition(token);
	    
			levelCounts[ docLevels[token] ]--;
			node = path[ docLevels[token] ];
			node.typeCounts[type]--;
			node.totalTokens--;
	    

			sum = 0.0;
			for (level=0; level < numLevels; level++) {
				levelWeights[level] = 
					(alpha + levelCounts[level]) * 
					(eta + path[level].typeCounts[type]) /
					(etaSum + path[level].totalTokens);
				sum += levelWeights[level];
			}
			level = random.nextDiscrete(levelWeights, sum);

			docLevels[token] = level;
			levelCounts[ docLevels[token] ]++;
			node = path[ level ];
			node.typeCounts[type]++;
			node.totalTokens++;
		}
    }

	/**
	 *  Writes the current sampling state to the file specified in <code>stateFile</code>.
	 */
	public void printState() throws IOException, FileNotFoundException {
		printState(new PrintWriter(new BufferedWriter(new FileWriter(stateFile))));
	}

	/**
	 *  Write a text file describing the current sampling state. 
	 */
    protected void printState(PrintWriter out) throws IOException {
		int doc = 0;

		Alphabet alphabet = instances.getDataAlphabet();

		for (Instance instance: instances) {
			FeatureSequence fs = (FeatureSequence) instance.getData();
			int seqLen = fs.getLength();
			int[] docLevels = levels[doc];
			NCRPNode node;
			int type, token, level;

			StringBuffer path = new StringBuffer();
			
			// Start with the leaf, and build a string describing the path for this doc
			node = documentLeaves[doc];
			for (level = numLevels - 1; level >= 0; level--) {
				path.append(node.nodeID + " ");
				node = node.parent;
			}

			for (token = 0; token < seqLen; token++) {
				type = fs.getIndexAtPosition(token);
				level = docLevels[token];
				
				// The "" just tells java we're not trying to add a string and an int
				out.println(path + "" + type + " " + alphabet.lookupObject(type) + " " + level + " ");
			}

			doc++;
		}
		out.close();
	}	    

    public void printNodes() {
		printNode(rootNode, 0, false);
    }
    
    public void printNodes(boolean withWeight) {
		printNode(rootNode, 0, withWeight);
    }

    public void printNode(NCRPNode node, int indent, boolean withWeight) {
		StringBuffer out = new StringBuffer();
		for (int i=0; i<indent; i++) {
			out.append("  ");
		}

		out.append(node.totalTokens + "/" + node.customers + " ");
		out.append(node.getTopWords(numWordsToDisplay, withWeight));
		System.out.println(out);
	
		for (NCRPNode child: node.children) {
			printNode(child, indent + 1, withWeight);
		}
    }

    /** For use with empirical likelihood evaluation: 
     *   sample a path through the tree, then sample a multinomial over
     *   topics in that path, then return a weighted sum of words.
     */
    public double empiricalLikelihood(int numSamples, InstanceList testing)  {
		NCRPNode[] path = new NCRPNode[numLevels];
		NCRPNode node;
		double weight;
		path[0] = rootNode;

		FeatureSequence fs;
		int sample, level, type, token, doc, seqLen;

		Dirichlet dirichlet = new Dirichlet(numLevels, alpha);
		double[] levelWeights;
		double[] multinomial = new double[numTypes];

		double[][] likelihoods = new double[ testing.size() ][ numSamples ];

		for (sample = 0; sample < numSamples; sample++) {
			Arrays.fill(multinomial, 0.0);

			for (level = 1; level < numLevels; level++) {
				path[level] = path[level-1].selectExisting();
			}
	    
			levelWeights = dirichlet.nextDistribution();
	    
			for (type = 0; type < numTypes; type++) {
				for (level = 0; level < numLevels; level++) {
					node = path[level];
					multinomial[type] +=
						levelWeights[level] * 
						(eta + node.typeCounts[type]) /
						(etaSum + node.totalTokens);
				}

			}

			for (type = 0; type < numTypes; type++) {
				multinomial[type] = Math.log(multinomial[type]);
			}

			for (doc=0; doc<testing.size(); doc++) {
                fs = (FeatureSequence) testing.get(doc).getData();
                seqLen = fs.getLength();
                
                for (token = 0; token < seqLen; token++) {
                    type = fs.getIndexAtPosition(token);
                    likelihoods[doc][sample] += multinomial[type];
                }
            }
		}
	
        double averageLogLikelihood = 0.0;
        double logNumSamples = Math.log(numSamples);
        for (doc=0; doc<testing.size(); doc++) {
            double max = Double.NEGATIVE_INFINITY;
            for (sample = 0; sample < numSamples; sample++) {
                if (likelihoods[doc][sample] > max) {
                    max = likelihoods[doc][sample];
                }
            }

            double sum = 0.0;
            for (sample = 0; sample < numSamples; sample++) {
                sum += Math.exp(likelihoods[doc][sample] - max);
            }

            averageLogLikelihood += Math.log(sum) + max - logNumSamples;
        }

		return averageLogLikelihood;
    }

	public void write (File serializedModelFile) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(serializedModelFile));
			oos.writeObject(this);
			oos.close();
		} catch (IOException e) {
			System.err.println("Problem serializing HierarchicalLDA to file " +
					serializedModelFile + ": " + e);
		}
	}

	public static HierarchicalLDA read (File f) throws Exception {

		HierarchicalLDA topicModel;

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
		topicModel = (HierarchicalLDA) ois.readObject();
		ois.close();

		return topicModel;
	}

	/** 
	 *  This method is primarily for testing purposes. The {@link cc.mallet.topics.tui.HierarchicalLDATUI}
	 *   class has a more flexible interface for command-line use.
	 */
    public static void main (String[] args) {
		try {
			InstanceList instances = InstanceList.load(new File(args[0]));
			InstanceList testing = InstanceList.load(new File(args[1]));

			HierarchicalLDA sampler = new HierarchicalLDA();
			sampler.initialize(instances, testing, 5, new Randoms());
			sampler.estimate(250);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    class NCRPNode implements Serializable {
		int customers;
		ArrayList<NCRPNode> children;
		NCRPNode parent;
		int level;

		int totalTokens;
		int[] typeCounts;

		public int nodeID;

		public NCRPNode(NCRPNode parent, int dimensions, int level) {
			customers = 0;
			this.parent = parent;
			children = new ArrayList<NCRPNode>();
			this.level = level;

			//System.out.println("new node at level " + level);
	    
			totalTokens = 0;
			typeCounts = new int[dimensions];

			nodeID = totalNodes;
			totalNodes++;
		}

		public NCRPNode(int dimensions) {
			this(null, dimensions, 0);
		}

		public NCRPNode addChild() {
			NCRPNode node = new NCRPNode(this, typeCounts.length, level + 1);
			children.add(node);
			return node;
		}

		public boolean isLeaf() {
			return level == numLevels - 1;
		}

		public NCRPNode getNewLeaf() {
			NCRPNode node = this;
			for (int l=level; l<numLevels - 1; l++) {
				node = node.addChild();
			}
			return node;
		}

		public void dropPath() {
			NCRPNode node = this;
			node.customers--;
			if (node.customers == 0) {
				node.parent.remove(node);
			}
			for (int l = 1; l < numLevels; l++) {
				node = node.parent;
				node.customers--;
				if (node.customers == 0) {
					node.parent.remove(node);
				}
			}
		}

		public void remove(NCRPNode node) {
			children.remove(node);
		}

		public void addPath() {
			NCRPNode node = this;
			node.customers++;
			for (int l = 1; l < numLevels; l++) {
				node = node.parent;
				node.customers++;
			}
		}

		public NCRPNode selectExisting() {
			double[] weights = new double[children.size()];
	    
			int i = 0;
			for (NCRPNode child: children) {
				weights[i] = (double) child.customers / (gamma + customers);
				i++;
			}

			int choice = random.nextDiscrete(weights);
			return children.get(choice);
		}

		public NCRPNode select() {
			double[] weights = new double[children.size() + 1];
	    
			weights[0] = gamma / (gamma + customers);

			int i = 1;
			for (NCRPNode child: children) {
				weights[i] = (double) child.customers / (gamma + customers);
				i++;
			}

			int choice = random.nextDiscrete(weights);
			if (choice == 0) {
				return(addChild());
			}
			else {
				return children.get(choice - 1);
			}
		}
	
		public String getTopWords(int numWords, boolean withWeight) {
			IDSorter[] sortedTypes = new IDSorter[numTypes];
	    
			for (int type=0; type < numTypes; type++) {
				sortedTypes[type] = new IDSorter(type, typeCounts[type]);
			}
			Arrays.sort(sortedTypes);
	    
			Alphabet alphabet = instances.getDataAlphabet();
			StringBuffer out = new StringBuffer();
			for (int i = 0; i < numWords; i++) {
				if (withWeight){
					out.append(alphabet.lookupObject(sortedTypes[i].getID()) + ":" + sortedTypes[i].getWeight() + " ");
				}else
					out.append(alphabet.lookupObject(sortedTypes[i].getID()) + " ");
			}
			return out.toString();
		}

    }
}
