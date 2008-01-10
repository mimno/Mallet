/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** A clustering of a set of points (instances).
    @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
*/

package cc.mallet.cluster;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

import cc.mallet.types.InstanceList;


public class Clustering implements Serializable {
	
	int numLabels;
	int labels[];
	InstanceList instances;
	
	/** Clustering constructor.
	 *
	 * @param instances Instances that are clustered
	 * @param numLabels Number of clusters
	 * @param labels Assignment of instances to clusters; many-to-one with 
	 *               range [0,numLabels).     
	 */
	public Clustering (InstanceList instances, int numLabels, int[] labels) {
		if (instances.size() != labels.length)
	    throw new IllegalArgumentException("Instance list length does not match cluster labeling");
		
		if (numLabels < 1)
	    throw new IllegalArgumentException("Number of labels must be strictly positive.");
		
		for (int i = 0 ; i < labels.length ; i++)
	    if (labels[i] < 0 || labels[i] >= numLabels)
				throw new IllegalArgumentException("Label mapping must have range [0,numLabels).");
		
		this.instances = instances;
		this.numLabels = numLabels;
		this.labels = labels;	
	}

	// GETTERS
	
	public InstanceList getInstances () { return this.instances; }

	/** Return an list of instances with a particular label. */
	public InstanceList getCluster(int label) {		
		InstanceList cluster = new InstanceList(instances.getPipe());		
		for (int n=0 ; n<instances.size() ; n++) 
	    if (labels[n] == label)
				cluster.add(instances.get(n));			
		return cluster;
	}

	/** Returns an array of instance lists corresponding to clusters. */
	public InstanceList[] getClusters() {		
		InstanceList[] clusters = new InstanceList[numLabels];		
		for (int c= 0 ; c<numLabels ; c++)
	    clusters[c] = getCluster(c);		
		return clusters;		
	}

	/** Get the cluster label for a particular instance. */
	public int getLabel(int index) { return labels[index]; }

	public int[] getLabels() { return labels; }
	
	public int getNumClusters() { return numLabels; }

	public int getNumInstances() { return instances.size(); }

	public int size (int label) {
		int size = 0;
		for (int i = 0; i < labels.length; i++)
			if (labels[i] == label)
				size++;
		return size;
	}
	
	public int[] getIndicesWithLabel (int label) {		
		int[] indices = new int[size(label)];
		int count = 0;
		for (int i = 0; i < labels.length; i++)
			if (labels[i] == label)
				indices[count++] = i;		
		return indices;
	}
	
	public boolean equals (Object o) {
		Clustering c = (Clustering) o;
		return Arrays.equals(c.getLabels(), labels);
	}
	
	public String toString () {
		String ret = "";
		for (int i = 0; i < labels.length; i++)
			ret += labels[i] + " ";
		return ret;
	}

	public Clustering shallowCopy () {
		int[] newLabels = new int[labels.length];
		System.arraycopy(labels, 0, newLabels, 0, labels.length);
		Clustering c = new Clustering(instances, numLabels, newLabels);
		return c;
	}
	
	// SETTERS
	
	/** Set the cluster label for a particular instance. */
	public void setLabel(int index, int label) { labels[index] = label; }
	
	/** Set the number of clusters */
	public void setNumLabels(int n) { numLabels = n; }

	 // SERIALIZATION

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

 
  private void writeObject (ObjectOutputStream out) throws IOException {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject ();
    int version = in.readInt ();
  }	
}
