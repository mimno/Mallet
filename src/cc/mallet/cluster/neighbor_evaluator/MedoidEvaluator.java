package cc.mallet.cluster.neighbor_evaluator;


import weka.core.Instances;
import cc.mallet.classify.Classifier;
import cc.mallet.cluster.Clustering;
import cc.mallet.cluster.util.PairwiseMatrix;
import cc.mallet.types.MatrixOps;

/**
 * Uses a {@link Classifier} over pairs of {@link Instances} to score
 * {@link Neighbor}. Currently only supports {@link
 * AgglomerativeNeighbor}s.
 *
 * @author "Michael Wick" <mwick@cs.umass.edu>
 * @version 1.0
 * @since 1.0
 * @see ClassifyingNeighborEvaluator
 */
public class MedoidEvaluator extends ClassifyingNeighborEvaluator {

	private static final long serialVersionUID = 1L;

	/**
     * If single link is true, then the score of clusters A and B is the score of the link between the two medoids.
     */
    boolean singleLink=false;

	/**
	 * How to combine a set of pairwise scores (e.g. mean, max, ...)... [currently not supported in this class]
	 */
	CombiningStrategy combiningStrategy;

	/**
	 * If true, score all edges involved in a merge. If false, only
	 * score the edges that croess the boundaries of the clusters being
	 * merged.
	 */
	boolean mergeFirst=true;

	/**
	 * Cache for calls to getScore. In some experiments, reduced running
	 * time by nearly half.
	 */
	PairwiseMatrix scoreCache;
	
	/**
	 *
	 * @param classifier Classifier to assign scores to {@link
	 * Neighbor}s for which a pair of Instances has been merged.
	 * @param scoringLabel The predicted label that corresponds to a
	 * positive example (e.g. "YES").
	 * @param combiningStrategy How to combine the pairwise scores
	 * (e.g. max, mean, ...).
	 * @param mergeFirst If true, score all edges involved in a
	 * merge. If false, only score the edges that cross the boundaries
	 * of the clusters being merged.
	 * @return
	 */
    public MedoidEvaluator(Classifier classifier, String scoringLabel)
    {
	super(classifier,scoringLabel);
	System.out.println("Using Medoid Evaluator");
    }
    public MedoidEvaluator(Classifier classifier, String scoringLabel,boolean singleLink,boolean mergeFirst)
    {
	super(classifier,scoringLabel);
	this.singleLink=singleLink;
	this.mergeFirst=mergeFirst;
	System.out.println("Using Medoid Evaluator. Single link="+singleLink+".");
    }

    /*
    public MedoidEvaluator (Classifier classifier,
			      String scoringLabel,
			      CombiningStrategy combiningStrategy,
			      boolean mergeFirst) {
		super(classifier, scoringLabel);
		this.combiningStrategy = combiningStrategy;
		this.mergeFirst = mergeFirst;
	System.out.println("Using Centroid Evaluator (2)");

	}
    */

	public double[] evaluate (Neighbor[] neighbors) {
		double[] scores = new double[neighbors.length];
		for (int i = 0; i < neighbors.length; i++)
			scores[i] = evaluate(neighbors[i]);
		return scores;
	}
	

    
    public double evaluate(Neighbor neighbor)
    {
	int result[] = new int[2];
	if (!(neighbor instanceof AgglomerativeNeighbor))
	    throw new IllegalArgumentException("Expect AgglomerativeNeighbor not " + neighbor.getClass().getName());
	int[][] oldIndices = ((AgglomerativeNeighbor)neighbor).getOldClusters();
	int[] mergedIndices=((AgglomerativeNeighbor)neighbor).getNewCluster();

	Clustering original = neighbor.getOriginal();

	result[0]=getCentroid(oldIndices[0],original);
	result[1]=getCentroid(oldIndices[1],original);
	if(singleLink) //scores a cluster based on link between medoid of each cluster
	    {
		AgglomerativeNeighbor pwn = new AgglomerativeNeighbor(original,original,oldIndices[0][result[0]],oldIndices[1][result[1]]);
		double score = getScore(pwn);
		return score;
	    }

	//
	//Returns average weighted average where weights are proportional to similarity to medoid
	double[] medsA=getMedWeights(result[0],oldIndices[0],original);
	double[] medsB=getMedWeights(result[1],oldIndices[1],original);

	double numerator=0;
	double denominator=0;
	for(int i=0;i<oldIndices[0].length;i++)
	    {
		//
		//cross-boundary
		for(int j=0;j<oldIndices[1].length;j++)
		    {

			AgglomerativeNeighbor pwn = new AgglomerativeNeighbor(original,original,oldIndices[0][i],oldIndices[1][j]);
			double interScore=getScore(pwn);
			numerator+=interScore*medsA[i]*medsB[j];
			denominator+=medsA[i]*medsB[j];
		    }
		//
		//intra-cluster1
		if(mergeFirst)
		    {
			for(int j=i+1;j<oldIndices[0].length;j++)
			    {
				AgglomerativeNeighbor pwn = new AgglomerativeNeighbor(original,original,oldIndices[0][i],oldIndices[0][j]);
				double interScore=getScore(pwn);
				numerator+=interScore*medsA[i]*medsA[j];
				denominator+=medsA[i]*medsA[j];
			    }
		    }
	    }
	//
	//intra-cluster2
	if(mergeFirst)
	    {
		for(int i=0;i<oldIndices[1].length;i++)
		    {
			for(int j=i+1;j<oldIndices[1].length;j++)
			    {
				AgglomerativeNeighbor pwn = new AgglomerativeNeighbor(original,original,oldIndices[1][i],oldIndices[1][j]);
				double interScore=getScore(pwn);
				numerator+=interScore*medsB[i]*medsB[j];
				denominator+=medsB[i]*medsB[j];
			    }
		    }
	    }

	return numerator/denominator;

    }
    private double[] getMedWeights(int medIdx,int[] indices,Clustering original)
    {
	double result[] = new double[indices.length];
	for(int i=0;i<result.length;i++)
	    {
		if(medIdx==i)
		    result[i]=1;
		else
		    {
			AgglomerativeNeighbor an = new AgglomerativeNeighbor(original,original,indices[medIdx],indices[i]);
			result[i] = getScore(an);
		    }
	    }
	return result;	
    }


    //
    //a bettter strategy would use caching to incrimentally determine the centroid
    private int getCentroid(int[] indices,Clustering original)
    {
	if(indices.length<2)
	    return 0;
	    //return indices[0];

	double centDist=Double.NEGATIVE_INFINITY;
	int centIdx=-1;
	double[] scores = new double[indices.length];
	for(int i=0;i<indices.length;i++)
	    {
		double acc=0;
		for(int k=0;k<indices.length;k++)
		    {
			if(i==k)break;
			AgglomerativeNeighbor pwn = new AgglomerativeNeighbor(original,original,indices[i],indices[k]);
			double score=getScore(pwn);
			acc+=score;
			//scores[i] = getScore(pwn);
		    }
		acc/=(indices.length-1);
		scores[i]=acc;
	    }
	for(int i=0;i<scores.length;i++)
	    {
		if(scores[i]>centDist)
		    {
			centDist=scores[i];
			centIdx=i;
			//centIdx=indices[i];
		    }
	    }
	return centIdx;
    }
    
    /*
	public double evaluate (Neighbor neighbor) {
 		if (!(neighbor instanceof AgglomerativeNeighbor))
 			throw new IllegalArgumentException("Expect AgglomerativeNeighbor not " + neighbor.getClass().getName());

		Clustering original = neighbor.getOriginal();
		int[] mergedIndices = ((AgglomerativeNeighbor)neighbor).getNewCluster();
		ArrayList scores = new ArrayList();
 		for (int i = 0; i < mergedIndices.length; i++) {
			for (int j = i + 1; j < mergedIndices.length; j++) {
				if ((original.getLabel(mergedIndices[i]) != original.getLabel(mergedIndices[j])) || mergeFirst) {
					AgglomerativeNeighbor pwneighbor =
						new AgglomerativeNeighbor(original,	original,
																			mergedIndices[i], mergedIndices[j]);
					scores.add(new Double(getScore(pwneighbor)));
				}
			}
		}

		if (scores.size() < 1)
			throw new IllegalStateException("No pairs of Instances were scored.");
		
 		double[] vals = new double[scores.size()];
		for (int i = 0; i < vals.length; i++)
			vals[i] = ((Double)scores.get(i)).doubleValue();
 		return combiningStrategy.combine(vals);
	}
    */

	public void reset () {
		scoreCache = null;
	}
	
	public String toString () {
		return "class=" + this.getClass().getName() +
			" classifier=" + classifier.getClass().getName();
	}

	private double getScore (AgglomerativeNeighbor pwneighbor) {
		if (scoreCache == null)
			scoreCache = new PairwiseMatrix(pwneighbor.getOriginal().getNumInstances());
		int[] indices = pwneighbor.getNewCluster();
		if (scoreCache.get(indices[0], indices[1]) == 0.0) {
			scoreCache.set(indices[0], indices[1],
								 classifier.classify(pwneighbor).getLabelVector().value(scoringLabel));
		}
		return scoreCache.get(indices[0], indices[1]);
	}

	/**
	 * Specifies how to combine a set of pairwise scores into a
	 * cluster-wise score.
	 *
	 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
	 * @version 1.0
	 * @since 1.0
	 */
	public static interface CombiningStrategy {
		public double combine (double[] scores);
	}

	public static class Average implements CombiningStrategy {
		public double combine (double[] scores) {
			return MatrixOps.mean(scores);
		}		
	}

	public static class Minimum implements CombiningStrategy {
		public double combine (double[] scores) {
			return MatrixOps.min(scores);
		}		
	}

	public static class Maximum implements CombiningStrategy {
		public double combine (double[] scores) {
			return MatrixOps.max(scores);
		}		
	}
}
