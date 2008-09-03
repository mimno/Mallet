/*
 * Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
 * This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 * http://www.cs.umass.edu/~mccallum/mallet This software is provided under the
 * terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE'
 * included with this distribution.
 */

/**
 * Clusters a set of point via k-Means. The instances that are clustered are
 * expected to be of the type FeatureVector.
 * 
 * EMPTY_SINGLE and other changes implemented March 2005 Heuristic cluster
 * selection implemented May 2005
 * 
 * @author Jerod Weinman <A
 *         HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
 * @author Mike Winter <a href =
 *         "mailto:mike.winter@gmail.com">mike.winter@gmail.com</a>
 * 
 */

package cc.mallet.cluster;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Metric;
import cc.mallet.types.SparseVector;
import cc.mallet.util.VectorStats;

/**
 * KMeans Clusterer
 * 
 * Clusters the points into k clusters by minimizing the total intra-cluster
 * variance. It uses a given {@link Metric} to find the distance between
 * {@link Instance}s, which should have {@link SparseVector}s in the data
 * field.
 * 
 */
public class KMeans extends Clusterer {

  private static final long serialVersionUID = 1L;

  // Stop after movement of means is less than this
  static double MEANS_TOLERANCE = 1e-2;

  // Maximum number of iterations
  static int MAX_ITER = 100;

  // Minimum fraction of points that move
  static double POINTS_TOLERANCE = .005;

  /**
   * Treat an empty cluster as an error condition.
   */
  public static final int EMPTY_ERROR = 0;
  /**
   * Drop an empty cluster
   */
  public static final int EMPTY_DROP = 1;
  /**
   * Place the single instance furthest from the previous cluster mean
   */
  public static final int EMPTY_SINGLE = 2;

  Random randinator;
  Metric metric;
  int numClusters;
  int emptyAction;
  ArrayList<SparseVector> clusterMeans;

  private static Logger logger = Logger
      .getLogger("edu.umass.cs.mallet.base.cluster.KMeans");

  /**
   * Construct a KMeans object
   * 
   * @param instancePipe Pipe for the instances being clustered
   * @param numClusters Number of clusters to use
   * @param metric Metric object to measure instance distances
   * @param emptyAction Specify what should happen when an empty cluster occurs
   */
  public KMeans(Pipe instancePipe, int numClusters, Metric metric,
      int emptyAction) {

    super(instancePipe);

    this.emptyAction = emptyAction;
    this.metric = metric;
    this.numClusters = numClusters;

    this.clusterMeans = new ArrayList<SparseVector>(numClusters);
    this.randinator = new Random();

  }

  /**
   * Construct a KMeans object
   * 
   * @param instancePipe Pipe for the instances being clustered
   * @param numClusters Number of clusters to use
   * @param metric Metric object to measure instance distances <p/> If an empty
   *        cluster occurs, it is considered an error.
   */
  public KMeans(Pipe instancePipe, int numClusters, Metric metric) {
    this(instancePipe, numClusters, metric, EMPTY_ERROR);
  }

  /**
   * Cluster instances
   * 
   * @param instances List of instances to cluster
   */
  @Override
  public Clustering cluster(InstanceList instances) {

    assert (instances.getPipe() == this.instancePipe);

    // Initialize clusterMeans
    initializeMeansSample(instances, this.metric);

    int clusterLabels[] = new int[instances.size()];
    ArrayList<InstanceList> instanceClusters = new ArrayList<InstanceList>(
        numClusters);
    int instClust;
    double instClustDist, instDist;
    double deltaMeans = Double.MAX_VALUE;
    double deltaPoints = (double) instances.size();
    int iterations = 0;
    SparseVector clusterMean;

    for (int c = 0; c < numClusters; c++) {
      instanceClusters.add(c, new InstanceList(instancePipe));
    }

    logger.info("Entering KMeans iteration");

    while (deltaMeans > MEANS_TOLERANCE && iterations < MAX_ITER
        && deltaPoints > instances.size() * POINTS_TOLERANCE) {

      iterations++;
      deltaPoints = 0;

      // For each instance, measure its distance to the current cluster
      // means, and subsequently assign it to the closest cluster
      // by adding it to an corresponding instance list
      // The mean of each cluster InstanceList is then updated.
      for (int n = 0; n < instances.size(); n++) {

        instClust = 0;
        instClustDist = Double.MAX_VALUE;

        for (int c = 0; c < numClusters; c++) {
          instDist = metric.distance(clusterMeans.get(c),
              (SparseVector) instances.get(n).getData());

          if (instDist < instClustDist) {
            instClust = c;
            instClustDist = instDist;
          }
        }
        // Add to closest cluster & label it such
        instanceClusters.get(instClust).add(instances.get(n));

        if (clusterLabels[n] != instClust) {
          clusterLabels[n] = instClust;
          deltaPoints++;
        }

      }

      deltaMeans = 0;

      for (int c = 0; c < numClusters; c++) {

        if (instanceClusters.get(c).size() > 0) {
          clusterMean = VectorStats.mean(instanceClusters.get(c));

          deltaMeans += metric.distance(clusterMeans.get(c), clusterMean);

          clusterMeans.set(c, clusterMean);

          instanceClusters.set(c, new InstanceList(instancePipe));

        } else {

          logger.info("Empty cluster found.");

          switch (emptyAction) {
            case EMPTY_ERROR:
              return null;
            case EMPTY_DROP:
              logger.fine("Removing cluster " + c);
              clusterMeans.remove(c);
              instanceClusters.remove(c);
              for (int n = 0; n < instances.size(); n++) {

                assert (clusterLabels[n] != c) : "Cluster size is "
                    + instanceClusters.get(c).size()
                    + "+ yet clusterLabels[n] is " + clusterLabels[n];

                if (clusterLabels[n] > c)
                  clusterLabels[n]--;
              }

              numClusters--;
              c--; // <-- note this trickiness. bad style? maybe.
              // it just means now that we've deleted the entry,
              // we have to repeat the index to get the next entry.
              break;

            case EMPTY_SINGLE:

              // Get the instance the furthest from any centroid
              // and make it a new centroid.

              double newCentroidDist = 0;
              int newCentroid = 0;
              InstanceList cacheList = null;

              for (int clusters = 0; clusters < clusterMeans.size(); clusters++) {
                SparseVector centroid = clusterMeans.get(clusters);
                InstanceList centInstances = instanceClusters.get(clusters);

                // Dont't create new empty clusters.

                if (centInstances.size() <= 1)
                  continue;
                for (int n = 0; n < centInstances.size(); n++) {
                  double currentDist = metric.distance(centroid,
                      (SparseVector) centInstances.get(n).getData());
                  if (currentDist > newCentroidDist) {
                    newCentroid = n;
                    newCentroidDist = currentDist;
                    cacheList = centInstances;

                  }
                }
              }
              if (cacheList == null) {
                logger.info("Can't find an instance to move.  Exiting.");
                // Can't find an instance to move.
                return null;
              } else clusterMeans.set(c, (SparseVector) cacheList.get(
                  newCentroid).getData());

            default:
              return null;
          }
        }

      }

      logger.fine("Iter " + iterations + " deltaMeans = " + deltaMeans);
    }

    if (deltaMeans <= MEANS_TOLERANCE)
      logger.info("KMeans converged with deltaMeans = " + deltaMeans);
    else if (iterations >= MAX_ITER)
      logger.info("Maximum number of iterations (" + MAX_ITER + ") reached.");
    else if (deltaPoints <= instances.size() * POINTS_TOLERANCE)
      logger.info("Minimum number of points (np*" + POINTS_TOLERANCE + "="
          + (int) (instances.size() * POINTS_TOLERANCE)
          + ") moved in last iteration. Saying converged.");

    return new Clustering(instances, numClusters, clusterLabels);

  }

  /**
   * Uses a MAX-MIN heuristic to seed the initial cluster means..
   * 
   * @param instList List of instances.
   * @param metric Distance metric.
   */

  private void initializeMeansSample(InstanceList instList, Metric metric) {

    // InstanceList has no remove() and null instances aren't
    // parsed out by most Pipes, so we have to pre-process
    // here and possibly leave some instances without
    // cluster assignments.

    ArrayList<Instance> instances = new ArrayList<Instance>(instList.size());
    for (int i = 0; i < instList.size(); i++) {
      Instance ins = instList.get(i);
      SparseVector sparse = (SparseVector) ins.getData();
      if (sparse.numLocations() == 0)
        continue;

      instances.add(ins);
    }

    // Add next center that has the MAX of the MIN of the distances from
    // each of the previous j-1 centers (idea from Andrew Moore tutorial,
    // not sure who came up with it originally)

    for (int i = 0; i < numClusters; i++) {
      double max = 0;
      int selected = 0;
      for (int k = 0; k < instances.size(); k++) {
        double min = Double.MAX_VALUE;
        Instance ins = instances.get(k);
        SparseVector inst = (SparseVector) ins.getData();
        for (int j = 0; j < clusterMeans.size(); j++) {
          SparseVector centerInst = clusterMeans.get(j);
          double dist = metric.distance(centerInst, inst);
          if (dist < min)
            min = dist;

        }
        if (min > max) {
          selected = k;
          max = min;
        }
      }

      Instance newCenter = instances.remove(selected);
      clusterMeans.add((SparseVector) newCenter.getData());
    }

  }

  /**
   * Return the ArrayList of cluster means after a run of the algorithm.
   * 
   * @return An ArrayList of Instances.
   */

  public ArrayList<SparseVector> getClusterMeans() {
    return this.clusterMeans;
  }
}
