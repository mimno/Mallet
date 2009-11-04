package cc.mallet.topics;

import cc.mallet.types.*;
import cc.mallet.util.*;

import java.util.Arrays;
import java.io.*;

public class TopicInferencer implements Serializable {

	protected int numTopics; 

    // These values are used to encode type/topic counts as
    //  count/topic pairs in a single int.
    protected int topicMask;
    protected int topicBits;
	
    protected int numTypes;
	
    protected double[] alpha;
    protected double beta;
    protected double betaSum;
	
    protected int[][] typeTopicCounts;
    protected int[] tokensPerTopic;
	
	Alphabet alphabet;
	
	protected Randoms random = null;
	
	double smoothingOnlyMass = 0.0;
	double[] cachedCoefficients;
	
	public TopicInferencer (int[][] typeTopicCounts, int[] tokensPerTopic, Alphabet alphabet,
							double[] alpha, double beta, double betaSum) {

		this.tokensPerTopic = tokensPerTopic;
		this.typeTopicCounts = typeTopicCounts;

		this.alphabet = alphabet;

		numTopics = tokensPerTopic.length;
		numTypes = typeTopicCounts.length;
		
		if (Integer.bitCount(numTopics) == 1) {
            // exact power of 2
            topicMask = numTopics - 1;
            topicBits = Integer.bitCount(topicMask);
        }
        else {
            // otherwise add an extra bit
            topicMask = Integer.highestOneBit(numTopics) * 2 - 1;
            topicBits = Integer.bitCount(topicMask);
        }

		this.alpha = alpha;
		this.beta = beta;
		this.betaSum = betaSum;

		cachedCoefficients = new double[numTopics];
		
		for (int topic=0; topic < numTopics; topic++) {
			smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
			cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum);
		}

		random = new Randoms();
	}

	public void setRandomSeed(int seed) {
		random = new Randoms(seed);
	}

	/** 
	 *  Use Gibbs sampling to infer a topic distribution.
	 *  Topics are initialized to the (or a) most probable topic
	 *   for each token. Using zero iterations returns exactly this
	 *   initial topic distribution.<p/>
	 *  This code does not adjust type-topic counts: P(w|t) is clamped.
	 */
	public double[] getSampledDistribution(Instance instance, int numIterations,
										   int thinning, int burnIn) {

		FeatureSequence tokens = (FeatureSequence) instance.getData();
		int docLength = tokens.size();
		int[] topics = new int[docLength];

		int[] localTopicCounts = new int[numTopics];
        int[] localTopicIndex = new int[numTopics];
		
		int type;
		int[] currentTypeTopicCounts;

		// Initialize all positions to the most common topic
		//  for that type.

		for (int position = 0; position < docLength; position++) {
			type = tokens.getIndexAtPosition(position);

			// Ignore out of vocabulary terms
			if (type < numTypes && typeTopicCounts[type].length != 0) { 

				currentTypeTopicCounts = typeTopicCounts[type];

				// This value should be a topic such that
				//  no other topic has more tokens of this type 
				//  assigned to it. If for some reason there were 
				//  no tokens of this type in the training data, it
				//  will default to topic 0, which is no worse than 
				//  random initialization.
				topics[position] = 
					currentTypeTopicCounts[0] & topicMask;

				localTopicCounts[topics[position]]++;
			}
		}

		// Build an array that densely lists the topics that                                                          
        //  have non-zero counts.                                                                                     
        int denseIndex = 0;
        for (int topic = 0; topic < numTopics; topic++) {
            if (localTopicCounts[topic] != 0) {
                localTopicIndex[denseIndex] = topic;
                denseIndex++;
            }
        }

        // Record the total number of non-zero topics                                                                 
        int nonZeroTopics = denseIndex;

        //      Initialize the topic count/beta sampling bucket                                                       
        double topicBetaMass = 0.0;

        // Initialize cached coefficients and the topic/beta                                                          
        //  normalizing constant.                                                                                     

        for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
            int topic = localTopicIndex[denseIndex];
            int n = localTopicCounts[topic];

            //  initialize the normalization constant for the (B * n_{t|d}) term                                      
            topicBetaMass += beta * n / (tokensPerTopic[topic] + betaSum);

            //  update the coefficients for the non-zero topics                                                       
            cachedCoefficients[topic] = (alpha[topic] + n) / (tokensPerTopic[topic] + betaSum);
        }

        double topicTermMass = 0.0;
		double[] topicTermScores = new double[numTopics];
        int[] topicTermIndices;
        int[] topicTermValues;
        int i;
        double score;

		int oldTopic, newTopic;

		double[] result = new double[numTopics];
		double sum = 0.0;

		for (int iteration = 1; iteration <= numIterations; iteration++) {
			
			//  Iterate over the positions (words) in the document                                                        
			for (int position = 0; position < docLength; position++) {
				type = tokens.getIndexAtPosition(position);
				
				// ignore out-of-vocabulary terms
				if (type >= numTypes || typeTopicCounts[type].length == 0) { continue; }

				oldTopic = topics[position];
				currentTypeTopicCounts = typeTopicCounts[type];

				// Prepare to sample by adjusting existing counts.
				// Note that we do not need to change the smoothing-only
				//  mass since the denominator is clamped.

				topicBetaMass -= beta * localTopicCounts[oldTopic] /
					(tokensPerTopic[oldTopic] + betaSum);
				
				// Decrement the local doc/topic counts                                                                   
				
				localTopicCounts[oldTopic]--;
				//assert(localTopicCounts[oldTopic] >= 0);

				// Maintain the dense index, if we are deleting                                                           
				//  the old topic                                                                                         
				if (localTopicCounts[oldTopic] == 0) {

					// First get to the dense location associated with                                                    
					//  the old topic.                                                                                    

					denseIndex = 0;

					// We know it's in there somewhere, so we don't                                                       
					//  need bounds checking.                                                                             
					while (localTopicIndex[denseIndex] != oldTopic) {
						denseIndex++;
					}

					// shift all remaining dense indices to the left.                                                     
					while (denseIndex < nonZeroTopics) {
						if (denseIndex < localTopicIndex.length - 1) {
                        localTopicIndex[denseIndex] =
                            localTopicIndex[denseIndex + 1];
						}
						denseIndex++;
					}

					nonZeroTopics --;
				} // finished maintaining local topic index

				topicBetaMass += beta * localTopicCounts[oldTopic] /
					(tokensPerTopic[oldTopic] + betaSum);
				
				// Reset the cached coefficient for this topic                                                            
				cachedCoefficients[oldTopic] =
					(alpha[oldTopic] + localTopicCounts[oldTopic]) /
					(tokensPerTopic[oldTopic] + betaSum);
				if (cachedCoefficients[oldTopic] <= 0) {
					System.out.println("zero or less coefficient: " + oldTopic + " = (" + alpha[oldTopic] + " + " + localTopicCounts[oldTopic] + ") / ( " + tokensPerTopic[oldTopic] + " + " + betaSum + " );");
				}
				

				int index = 0;
				int currentTopic, currentValue;

				boolean alreadyDecremented = false;

				topicTermMass = 0.0;

				while (index < currentTypeTopicCounts.length &&
					   currentTypeTopicCounts[index] > 0) {
					currentTopic = currentTypeTopicCounts[index] & topicMask;
					currentValue = currentTypeTopicCounts[index] >> topicBits;
					
                    score =
                        cachedCoefficients[currentTopic] * currentValue;
                    topicTermMass += score;
                    topicTermScores[index] = score;
					
                    index++;
				}

				double sample = random.nextUniform() * (smoothingOnlyMass + topicBetaMass + topicTermMass);
				double origSample = sample;

				//  Make sure it actually gets set                                                                        
				newTopic = -1;

				if (sample < topicTermMass) {
					//topicTermCount++;                                                                                   

					i = -1;
					while (sample > 0) {
						i++;
						sample -= topicTermScores[i];
					}

					newTopic = currentTypeTopicCounts[i] & topicMask;
				}
				else {
					sample -= topicTermMass;

					if (sample < topicBetaMass) {
						//betaTopicCount++;                                                                               

						sample /= beta;

						for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
							int topic = localTopicIndex[denseIndex];

							sample -= localTopicCounts[topic] /
								(tokensPerTopic[topic] + betaSum);
							
							if (sample <= 0.0) {
								newTopic = topic;
								break;
							}
						}
						
					}
					else {
						sample -= topicBetaMass;

						sample /= beta;

						newTopic = 0;
						sample -= alpha[newTopic] /
							(tokensPerTopic[newTopic] + betaSum);
						
						while (sample > 0.0) {
							newTopic++;

							if (newTopic >= numTopics) {
								index = 0;
								
								while (index < currentTypeTopicCounts.length &&
									   currentTypeTopicCounts[index] > 0) {
									currentTopic = currentTypeTopicCounts[index] & topicMask;
									currentValue = currentTypeTopicCounts[index] >> topicBits;
									
									System.out.println(currentTopic + "\t" + currentValue + "\t" + topicTermScores[index] +
													   "\t" + cachedCoefficients[currentTopic]);
									index++;
								}
							}

							sample -= alpha[newTopic] /
								(tokensPerTopic[newTopic] + betaSum);
						}
						
					}
					
				}

				topics[position] = newTopic;

				topicBetaMass -= beta * localTopicCounts[newTopic] /
					(tokensPerTopic[newTopic] + betaSum);

				localTopicCounts[newTopic]++;

				// If this is a new topic for this document,                                                              
				//  add the topic to the dense index.                                                                     
				if (localTopicCounts[newTopic] == 1) {

					// First find the point where we                                                                      
					//  should insert the new topic by going to                                                           
					//  the end (which is the only reason we're keeping                                                   
					//  track of the number of non-zero                                                                   
					//  topics) and working backwards                                                                     

					denseIndex = nonZeroTopics;

					while (denseIndex > 0 &&
						   localTopicIndex[denseIndex - 1] > newTopic) {
						
						localTopicIndex[denseIndex] =
							localTopicIndex[denseIndex - 1];
						denseIndex--;
					}

					localTopicIndex[denseIndex] = newTopic;
					nonZeroTopics++;
				}

				//  update the coefficients for the non-zero topics                                                       
				cachedCoefficients[newTopic] =
					(alpha[newTopic] + localTopicCounts[newTopic]) /
					(tokensPerTopic[newTopic] + betaSum);
				
				topicBetaMass += beta * localTopicCounts[newTopic] /
					(tokensPerTopic[newTopic] + betaSum);

			}

			if (iteration > burnIn &&
				(iteration - burnIn) % thinning == 0) {

				// Save a sample
				for (int topic=0; topic < numTopics; topic++) {
					result[topic] += alpha[topic] + localTopicCounts[topic];
					sum += alpha[topic] + localTopicCounts[topic];
				}
			}
		}

        //  Clean up our mess: reset the coefficients to values with only
        //  smoothing. The next doc will update its own non-zero topics...
        for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
            int topic = localTopicIndex[denseIndex];

            cachedCoefficients[topic] =
                alpha[topic] / (tokensPerTopic[topic] + betaSum);
        }

		if (sum == 0.0) {
			// Save at least one sample
			for (int topic=0; topic < numTopics; topic++) {
				result[topic] = alpha[topic] + localTopicCounts[topic];
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
	 *  @param thinning      The number of iterations between saved samples
	 *  @param burnIn        The number of iterations before the first saved sample
	 *  @param threshold     The minimum proportion of a given topic that will be written
	 *  @param max           The total number of topics to report per document]
	 */
	public void writeInferredDistributions(InstanceList instances, 
										   File distributionsFile,
										   int numIterations, int thinning, int burnIn,
										   double threshold, int max) throws IOException {

		PrintWriter out = new PrintWriter(distributionsFile);
		
		out.print ("#doc source topic proportion ...\n");

        IDSorter[] sortedTopics = new IDSorter[ numTopics ];
		for (int topic = 0; topic < numTopics; topic++) {
            // Initialize the sorters with dummy values
			sortedTopics[topic] = new IDSorter(topic, topic);
        }

		if (max < 0 || max > numTopics) {
            max = numTopics;
		}

		int doc = 0;

		for (Instance instance: instances) {
			
			double[] topicDistribution =
				getSampledDistribution(instance, numIterations,
									   thinning, burnIn);
			out.print (doc); out.print (' ');

			// Print the Source field of the instance
            if (instance.getSource() != null) {
				out.print (instance.getSource());
            }
            else {
                out.print ("null-source");
            }
            out.print (' ');

			for (int topic = 0; topic < numTopics; topic++) {
                sortedTopics[topic].set(topic, topicDistribution[topic]);
            }
			Arrays.sort(sortedTopics);

			for (int i = 0; i < max; i++) {
                if (sortedTopics[i].getWeight() < threshold) { break; }

                out.print (sortedTopics[i].getID() + " " +
						   sortedTopics[i].getWeight() + " ");
            }
            out.print (" \n");
			doc++;
		}

		out.close();
	}

	// Serialization

    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    private static final int NULL_INTEGER = -1;

    private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);

        out.writeObject(alphabet);

		out.writeInt(numTopics);

		out.writeInt(topicMask);
        out.writeInt(topicBits);

        out.writeInt(numTypes);

		out.writeObject(alpha);
		out.writeDouble(beta);
        out.writeDouble(betaSum);

		out.writeObject(typeTopicCounts);
        out.writeObject(tokensPerTopic);

		out.writeObject(random);

		out.writeDouble(smoothingOnlyMass);
		out.writeObject(cachedCoefficients);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {

        int version = in.readInt ();

        alphabet = (Alphabet) in.readObject();

        numTopics = in.readInt();

        topicMask = in.readInt();
        topicBits = in.readInt();

        numTypes = in.readInt();

        alpha = (double[]) in.readObject();
        beta = in.readDouble();
        betaSum = in.readDouble();

        typeTopicCounts = (int[][]) in.readObject();
        tokensPerTopic = (int[]) in.readObject();

		random = (Randoms) in.readObject();

		smoothingOnlyMass = in.readDouble();
		cachedCoefficients = (double[]) in.readObject();
	}

	public static TopicInferencer read (File f) throws Exception {

        TopicInferencer inferencer = null;

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
        inferencer = (TopicInferencer) ois.readObject();
		ois.close();

        return inferencer;
    }
		
}