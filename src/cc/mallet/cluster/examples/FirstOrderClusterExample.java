package cc.mallet.cluster.examples;

import cc.mallet.classify.Classifier;
import cc.mallet.classify.MaxEntTrainer;
import cc.mallet.classify.Trial;
import cc.mallet.classify.evaluate.ConfusionMatrix;
import cc.mallet.cluster.Clusterer;
import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.GreedyAgglomerativeByDensity;
import cc.mallet.cluster.evaluate.AccuracyEvaluator;
import cc.mallet.cluster.evaluate.BCubedEvaluator;
import cc.mallet.cluster.evaluate.ClusteringEvaluator;
import cc.mallet.cluster.evaluate.ClusteringEvaluators;
import cc.mallet.cluster.evaluate.MUCEvaluator;
import cc.mallet.cluster.evaluate.PairF1Evaluator;
import cc.mallet.cluster.iterator.ClusterSampleIterator;
import cc.mallet.cluster.neighbor_evaluator.AgglomerativeNeighbor;
import cc.mallet.cluster.neighbor_evaluator.ClassifyingNeighborEvaluator;
import cc.mallet.cluster.util.ClusterUtils;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.InfoGain;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.PropertyList;
import cc.mallet.util.Randoms;

/**
 * Illustrates use of a supervised clustering method that uses
 * features over clusters. Synthetic data is created where Instances
 * belong in same cluster iff they each have a feature called
 * "feature0".
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 */
public class FirstOrderClusterExample {

	Randoms random;
	double noise;
	
	public FirstOrderClusterExample () {
		this.random = new Randoms(123456789);
		this.noise = 0.01;
	}
	
	public void run () {
		Alphabet alphabet = dictOfSize(20);
		
		// TRAIN
		Clustering training = sampleClustering(alphabet);		
		Pipe clusterPipe = new OverlappingFeaturePipe();
		System.err.println("Training with " + training);
		InstanceList trainList = new InstanceList(clusterPipe);
		trainList.addThruPipe(new ClusterSampleIterator(training, random, 0.5, 100));
		System.err.println("Created " + trainList.size() + " instances.");
		Classifier me = new MaxEntTrainer().train(trainList);
		ClassifyingNeighborEvaluator eval =
			new ClassifyingNeighborEvaluator(me, "YES");
																					 
		Trial trial = new Trial(me, trainList);
		System.err.println(new ConfusionMatrix(trial));
		InfoGain ig = new InfoGain(trainList);
		ig.print();

// 		Clusterer clusterer = new GreedyAgglomerative(training.getInstances().getPipe(),
// 																									eval, 0.5);
		Clusterer clusterer = new GreedyAgglomerativeByDensity(training.getInstances().getPipe(),
																													 eval, 0.5, false,
																													 new java.util.Random(1));

		// TEST
		Clustering testing = sampleClustering(alphabet);		
		InstanceList testList = testing.getInstances();
		Clustering predictedClusters = clusterer.cluster(testList);			

		// EVALUATE
		System.err.println("\n\nEvaluating System: " + clusterer);
		ClusteringEvaluators evaluators = new ClusteringEvaluators(new ClusteringEvaluator[]{
				new BCubedEvaluator(),
				new PairF1Evaluator(),
				new MUCEvaluator(),
				new AccuracyEvaluator()});

		System.err.println("truth:" + testing);
		System.err.println("pred: " + predictedClusters);				
		System.err.println(evaluators.evaluate(testing, predictedClusters)); 					
	}
	
	/**
	 * Sample a InstanceList and its true clustering.
	 * @param alph
	 * @return
	 */
	private Clustering sampleClustering (Alphabet alph) {
		InstanceList instances =
			new InstanceList(random,
											 alph,
											 new String[]{"foo", "bar"},
											 30).subList(0, 20);
		Clustering singletons = ClusterUtils.createSingletonClustering(instances);
		// Merge instances that both have feature0
		for (int i = 0; i < instances.size(); i++) {
			FeatureVector fvi = (FeatureVector)instances.get(i).getData();
			for (int j = i + 1; j < instances.size(); j++) {
				FeatureVector fvj = (FeatureVector)instances.get(j).getData();
				if (fvi.contains("feature0") && fvj.contains("feature0")) {
					singletons = ClusterUtils.mergeClusters(singletons,
																									singletons.getLabel(i),
																									singletons.getLabel(j));
				} else if (!(fvi.contains("feature0") || fvj.contains("feature0"))
									 && random.nextUniform() < noise) {
					// Random noise.
					singletons = ClusterUtils.mergeClusters(singletons,
																									singletons.getLabel(i),
																									singletons.getLabel(j));					
				}
			}
		}
		return singletons;
	}

 	private Alphabet dictOfSize (int size) {
		Alphabet ret = new Alphabet ();
		for (int i = 0; i < size; i++)
			ret.lookupIndex ("feature"+i);
 		return ret;
	}

	/**
	 * Computes a feature that indicates whether or not all members of a
	 * cluster have a feature named "feature0".
	 *
	 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
	 * @version 1.0
	 * @since 1.0
	 * @see Pipe
	 */
	private class OverlappingFeaturePipe extends Pipe {

		private static final long serialVersionUID = 1L;

		public OverlappingFeaturePipe () {
			super (new Alphabet(), new LabelAlphabet());			
		}
		
		public Instance pipe (Instance carrier) {
			boolean mergeFirst = false;
			
			AgglomerativeNeighbor neighbor = (AgglomerativeNeighbor)carrier.getData();
			Clustering original = neighbor.getOriginal();
			InstanceList list = original.getInstances();			
			int[] mergedIndices = neighbor.getNewCluster();
			boolean match = true;
			for (int i = 0; i < mergedIndices.length; i++) {
				for (int j = i + 1; j < mergedIndices.length; j++) {
					if ((original.getLabel(mergedIndices[i]) !=
							 original.getLabel(mergedIndices[j])) || mergeFirst) {
						FeatureVector fvi = (FeatureVector)list.get(mergedIndices[i]).getData();
						FeatureVector fvj = (FeatureVector)list.get(mergedIndices[j]).getData();
						if (!(fvi.contains("feature0") && fvj.contains("feature0"))) {
							match = false;
							break;							
						}
					}
				}
			}

			PropertyList pl = null;
			if (match) 
				pl = PropertyList.add("Match", 1.0, pl);
			else
				pl = PropertyList.add("NoMatch", 1.0, pl);
			
			FeatureVector fv = new FeatureVector ((Alphabet)getDataAlphabet(),
																						pl, true);
			carrier.setData(fv);

			boolean positive = true;
			for (int i = 0; i < mergedIndices.length; i++) {
				for (int j = i + 1; j < mergedIndices.length; j++) {
					if (original.getLabel(mergedIndices[i]) != original.getLabel(mergedIndices[j])) {
						positive = false;
						break;
					}
				}
			}
			LabelAlphabet ldict = (LabelAlphabet)getTargetAlphabet();
			String label = positive ? "YES" : "NO";			
			carrier.setTarget(ldict.lookupLabel(label));
			return carrier;
		}
	}

		
	public static void main (String[] args) {
		FirstOrderClusterExample ex = new FirstOrderClusterExample();
		ex.run();
	}
	
}
