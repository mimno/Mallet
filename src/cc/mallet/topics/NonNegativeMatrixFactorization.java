package cc.mallet.topics;

import java.util.Arrays;
import java.io.*;

import cc.mallet.types.*;
import cc.mallet.util.*;

public class NonNegativeMatrixFactorization {

	static CommandOption.String inputFile = new CommandOption.String(NonNegativeMatrixFactorization.class, "input", "FILENAME", true, null,
																	 "The filename from which to read the list of training instances.  Use - for stdin.  " +
																	 "The instances must be FeatureVectors, not FeatureSequences", null);
	
	static CommandOption.String outputWordsFile = new CommandOption.String(NonNegativeMatrixFactorization.class, "output-words", "FILENAME", true, "word-weights.txt",
																	  "The filename to write weights for each word.", null);
	
	static CommandOption.String outputDocsFile = new CommandOption.String(NonNegativeMatrixFactorization.class, "output-docs", "FILENAME", true, "doc-weights.txt",
																	  "The filename to write weights for each document.", null);
	
	static CommandOption.Integer numDimensions = new CommandOption.Integer(NonNegativeMatrixFactorization.class, "num-dimensions", "INTEGER", true, 50,
																	   "The number of dimensions to fit.", null);
	
	static CommandOption.Integer clusterSize = new CommandOption.Integer(NonNegativeMatrixFactorization.class, "init-cluster-size", "INTEGER", true, 0,
																	   "Select this number of random instances to initialize each dimension. 0 = off.", null);
	
	static CommandOption.Boolean useIDFOption = new CommandOption.Boolean(NonNegativeMatrixFactorization.class, "use-idf", "TRUE/FALSE", true, true,
																	   "Whether to use IDF weighting.", null);

	// It should be trivial to parallelize, I haven't done it yet.
	//static CommandOption.Integer numThreads = new CommandOption.Integer(NonNegativeMatrixFactorization.class, "num-threads", "INTEGER", true, 1,
	//																	"The number of threads for parallel training.", null);

	static CommandOption.Integer numIterationsOption = new CommandOption.Integer(NonNegativeMatrixFactorization.class, "num-iters", "INTEGER", true, 1000,
																		"The number of passes through the training data.", null);


	InstanceList instances;
	int numFactors;

	int numFeatures;
	int numInstances;
	
	int numIterations;
	boolean idfWeighting;

	double[] featureWeights = null;

	double[][] featureFactorWeights;
	double[][] instanceFactorWeights;

	double[] featureSums;
	double[] instanceSums;
	
	Randoms random;

	public NonNegativeMatrixFactorization(InstanceList instances,
                                          int numFactors, boolean idfWeighting) {
		this(instances, numFactors, idfWeighting, new Randoms());
	}

	public NonNegativeMatrixFactorization(InstanceList instances,
										  int numFactors, boolean idfWeighting,
										  Randoms random) {

		this.instances = instances;
		this.numFactors = numFactors;
		this.idfWeighting = idfWeighting;
		this.random = random;

		numFeatures = instances.getDataAlphabet().size();
		numInstances = instances.size();

		featureFactorWeights = new double[ numFeatures ][ numFactors ];
		instanceFactorWeights = new double[ numInstances ][ numFactors ];

		featureSums = new double[ numFactors ];
		instanceSums = new double[ numFactors ];

		if (idfWeighting) { calculateIDFWeights(); }
		
		// Initialize randomly to break symmetry, plus a selection of random documents to give a good initialization.
		
		for (int feature = 0; feature < numFeatures; feature++) {
			for (int factor = 0; factor < numFactors; factor++) {
				featureFactorWeights[feature][factor] = 0.001 * random.nextUniform() / numFeatures;
				featureSums[factor] += featureFactorWeights[feature][factor];
			}
		}
		
		for (int instance = 0; instance < numInstances; instance++) {
			for (int factor = 0; factor < numFactors; factor++) {
				instanceFactorWeights[instance][factor] = 1.0 / numFactors; //random.nextUniform() / numFactors;
				instanceSums[factor] += instanceFactorWeights[instance][factor];
			}
		}
	}
	
	public void calculateIDFWeights() {
		this.idfWeighting = true;
		
		System.out.println("Counting word features");
		FeatureCountTool counter = new FeatureCountTool(instances);
		counter.count();
		int[] instanceCounts = counter.getDocumentFrequencies();

		featureWeights = new double[ numFeatures ];

		for (int feature = 0; feature < numFeatures; feature++) {
			if (instanceCounts[feature] > 0) {
				featureWeights[feature] = Math.log((double) numInstances / instanceCounts[feature]);
			}
		}
	}
	
	public void initialize(int clusterSize) {
		for (int factor = 0; factor < numFactors; factor++) {
			for (int sample = 0; sample < clusterSize; sample++) {
				FeatureVector data = (FeatureVector) instances.get(random.nextInt(numInstances)).getData();
				for (int location = 0; location < data.numLocations(); location++) {
					int feature = data.indexAtLocation(location);
					double value = data.valueAtLocation(location);
					if (idfWeighting) {
						value *= featureWeights[feature];
					}
					featureFactorWeights[feature][factor] += value / clusterSize;
					featureSums[factor] += value / clusterSize;
				}
			}
		}
	}
	
    public static final String[] BARS = { " ", "\u2581", "\u2582", "\u2583", "\u2584", "\u2585", "\u2586", "\u2587", "\u2588" };

     public static String getBar(double x, double min, double max) {
             if (x > max) { x = max; }
             if (x < min) { x = min; }
             return BARS[ (int) Math.round(8.0 * (x - min) / (max - min)) ];
     }
     
     public static String getBars(double[] sequence, double min, double max) {

              StringBuilder out = new StringBuilder();
              for (double x :sequence) {
                      out.append(getBar(x, min, max));
              }
              return out.toString();
      }

     public static String getBars(double[] sequence) {
             double max = Double.NEGATIVE_INFINITY;
             double min = Double.POSITIVE_INFINITY;
             
             for (double x : sequence) {
                     if (x > max) { max = x; }
                     if (x < min) { min = x; }
             }

             return getBars(sequence, 0.0, max);
     }

	public double getDivergence() {
		double divergence = 0.0;
		for (int instance = 0; instance < numInstances; instance++) {
			FeatureVector data = (FeatureVector) instances.get(instance).getData();

			double[] currentInstanceFactorWeights =
				instanceFactorWeights[instance];
		
			for (int location = 0; location < data.numLocations(); location++) {
				int feature = data.indexAtLocation(location);
				double value = data.valueAtLocation(location);
				if (idfWeighting) {
					value *= featureWeights[feature];
				}
				
				double[] currentFeatureFactorWeights = 
					featureFactorWeights[feature];
				
				double innerProduct = 0.0;

				for (int factor = 0; factor < numFactors; factor++) {
					innerProduct += 
						currentInstanceFactorWeights[factor] *
						currentFeatureFactorWeights[factor];
				}
				if (innerProduct == 0.0) { continue; }
				
				divergence += value * Math.log(value / innerProduct) - value + innerProduct;
			}
		
		}
		return divergence;
	}

	public void updateWeights() {

		// First do the instance-factor updates
		
		for (int instance = 0; instance < numInstances; instance++) {
			FeatureVector data = (FeatureVector) instances.get(instance).getData();

			double[] currentInstanceFactorWeights =
				instanceFactorWeights[instance];

			// Gather the expected counts (W * H)

			double[] updateRatios = new double[ numFactors ];
			
			double valueSum = 0.0;

			for (int location = 0; location < data.numLocations(); location++) {
				int feature = data.indexAtLocation(location);
				double value = data.valueAtLocation(location);
				if (idfWeighting) {
					value *= featureWeights[feature];
				}
				valueSum += value;
				
				double[] currentFeatureFactorWeights = 
					featureFactorWeights[feature];
				
				double innerProduct = 0.0;

				for (int factor = 0; factor < numFactors; factor++) {
					innerProduct += 
						currentInstanceFactorWeights[factor] *
						currentFeatureFactorWeights[factor];
				}
				if (innerProduct == 0.0) { continue; }
				
				double ratio = value / innerProduct;

				for (int factor = 0; factor < numFactors; factor++) {
					updateRatios[factor] +=
						currentFeatureFactorWeights[factor] * 
						ratio;
				}
			}

			// Finally, scale by the inverse of the sum over features for 
			//  this factor and do the update.

			if (valueSum > 0.0) {
				for (int factor = 0; factor < numFactors; factor++) {
					currentInstanceFactorWeights[ factor ] *=
						updateRatios[factor] / 
						featureSums[factor];
					assert(! Double.isNaN(currentInstanceFactorWeights[ factor ]));
				}
			}
			else {
				for (int factor = 0; factor < numFactors; factor++) {
					currentInstanceFactorWeights[ factor ] = 0.0;
				}
			}
		}

		Arrays.fill(instanceSums, 0);
		
		for (int instance = 0; instance < numInstances; instance++) {
			for (int factor = 0; factor < numFactors; factor++) {
				instanceSums[factor] += instanceFactorWeights[instance][factor];
			}
		}

		// Now collect data for the updates for featureFactor weights

		
		// It's more convenient to loop over documents
		//  and then features than the other way around,
		//  so we need to save some the full matrix of
		//  update ratios.
		double[][] featureFactorUpdateRatios = new double[ numFeatures ][ numFactors ];

		for (int instance = 0; instance < numInstances; instance++) {
            FeatureVector data = (FeatureVector) instances.get(instance).getData();

            double[] currentInstanceFactorWeights =
                instanceFactorWeights[instance];

			for (int location = 0; location < data.numLocations(); location++) {
                int feature = data.indexAtLocation(location);
                double value = data.valueAtLocation(location);
				if (idfWeighting) {
					value *= featureWeights[feature];
				}
				
				if (value == 0.0) {
					continue;
				}

                double[] currentFeatureFactorWeights =
                    featureFactorWeights[feature];

                double innerProduct = 0.0;

                for (int factor = 0; factor < numFactors; factor++) {

					assert( currentInstanceFactorWeights[factor] >= 0.0 );
					assert( currentFeatureFactorWeights[factor] >= 0.0 );

                    innerProduct +=
                        currentInstanceFactorWeights[factor] *
                        currentFeatureFactorWeights[factor];
                }

                double ratio = value / innerProduct;
				
                for (int factor = 0; factor < numFactors; factor++) {
					assert( ! Double.isNaN( currentInstanceFactorWeights[factor] ) );
					assert( ! Double.isNaN(ratio) ) : value + " / " + innerProduct;
					

                    featureFactorUpdateRatios[feature][factor] +=
                        currentInstanceFactorWeights[factor] *
                        ratio;
                }
			}
		}
		
		// ... and do the updates

		for (int feature = 0; feature < numFeatures; feature++) {

			double[] currentFeatureFactorWeights =
				 featureFactorWeights[feature];

			for (int factor = 0; factor < numFactors; factor++) {
				
				assert( ! Double.isNaN(featureFactorUpdateRatios[feature][factor]) );
				assert( ! Double.isNaN(instanceSums[factor]) ) : instanceSums[factor];

				currentFeatureFactorWeights[factor] *= 
					featureFactorUpdateRatios[feature][factor] /
					instanceSums[factor];

				assert( ! Double.isNaN(currentFeatureFactorWeights[factor]));
			}
			
		}
				
		Arrays.fill(featureSums, 0);

		for (int feature = 0; feature < numFeatures; feature++) {
			for (int factor = 0; factor < numFactors; factor++) {
				featureSums[factor] += featureFactorWeights[feature][factor];
			}
		}
		//System.out.println(getBars(featureSums));

		
	}

	public void printFactorFeatures(int limit) {
		IDSorter[] sortedIDs = new IDSorter[numFeatures];

		StringBuilder output = new StringBuilder();

		for (int factor = 0; factor < numFactors; factor++) {

			for (int feature = 0; feature < numFeatures; feature++) {
				sortedIDs[feature] = new IDSorter(feature, featureFactorWeights[feature][factor]);
			}
			Arrays.sort(sortedIDs);

			output.append(factor + "\t");
			for (int i = 0; i < limit; i++) {
				output.append(instances.getDataAlphabet().lookupObject(sortedIDs[i].getID()) + " ");
			}
			output.append("\n");
		}

		System.out.println(output);
	}
	
	public void writeFeatureFactors(PrintWriter out) throws IOException {
		for (int feature = 0; feature < numFeatures; feature++) {

			double[] currentFeatureFactorWeights =
				 featureFactorWeights[feature];
			
			out.print(instances.getDataAlphabet().lookupObject(feature));

			for (int factor = 0; factor < numFactors; factor++) {
				out.format("\t%f", currentFeatureFactorWeights[factor]);
			}
			out.println();
		}
	}
	
	public void writeInstanceFactors(PrintWriter out) throws IOException {
		for (int instance = 0; instance < numInstances; instance++) {

			double[] currentInstanceFactorWeights =
				 instanceFactorWeights[instance];
			
			out.print(instances.get(instance).getName());

			for (int factor = 0; factor < numFactors; factor++) {
				out.format("\t%f", currentInstanceFactorWeights[factor]);
			}
			out.println();
		}
	}

	public static void main (String[] args) throws Exception {
		
		// Process the command-line options
		CommandOption.setSummary (NonNegativeMatrixFactorization.class,
								  "Train non-negative matrix factorization.");
		CommandOption.process (NonNegativeMatrixFactorization.class, args);

		InstanceList instances = InstanceList.load(new File(inputFile.value));
		
		NonNegativeMatrixFactorization nmf = 
			new NonNegativeMatrixFactorization(instances, numDimensions.value, useIDFOption.value);

		if (clusterSize.value > 0) {
			nmf.initialize(clusterSize.value);
		}

		System.out.println("Finding " + numDimensions.value + " factors.");
		System.out.println("Histograms show relative factor sizes, the number measures factorization error (smaller is better).");
		double previousDivergence = Double.POSITIVE_INFINITY;
		for (int iteration = 1; iteration <= numIterationsOption.value; iteration++) {
			nmf.updateWeights();
			
			if (iteration % 100 == 0) {
				nmf.printFactorFeatures(15);
			}
			if (iteration % 10 == 0) {
				double divergence = nmf.getDivergence();
				System.out.println(getBars(nmf.featureSums) + "\t" + getBars(nmf.instanceSums) + "\t" + divergence);
				if (divergence / previousDivergence > 0.9999) {
					break;
				}
				previousDivergence = divergence;
			}
		}		
		
		if (outputWordsFile.value != null) {
			System.out.println("Writing to " + outputWordsFile.value);

			PrintWriter out = new PrintWriter(new File(outputWordsFile.value));
			nmf.writeFeatureFactors(out);
			out.close();
		}
		
		if (outputDocsFile.value != null) {
			System.out.println("Writing to " + outputDocsFile.value);

			PrintWriter out = new PrintWriter(new File(outputDocsFile.value));
			nmf.writeInstanceFactors(out);
			out.close();			
		}
	}
}