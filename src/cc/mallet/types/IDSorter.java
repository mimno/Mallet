/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.types;

/**
 *   This class is contains a comparator for use in sorting 
 *   integers that have associated floating point values. One
 *   example would be sorting words by probability in a Naive Bayes
 *   model. Ties are broken by the ID.
 *   <code><pre>
 *     IDSorter[] sortedIDs = new IDSorter[n];
 *     for (int i=0; i&lt;n; i++) {
 *       sortedIDs[i] = new IDSorter(i, weights[i]);
 *     }
 *     Arrays.sort(sortedIDs);
 *     
 *     for (int i=0; i&lt;10; i++) {
 *   </pre></code>
 *
 *    @author David Mimno
 */

public class IDSorter implements Comparable<IDSorter> {
    int id; double p;
    public IDSorter (int id, double p) { this.id = id; this.p = p; }
    public IDSorter (int id, int p) { this.id = id; this.p = p; }

    public final int compareTo (IDSorter o2) {

		double otherP = o2.p;

		if (p > o2.p) {
			return -1;
		}
		else if (p < o2.p) {
			return 1;
		}

		// p == otherP, sort by ID
		
		int otherID = o2.id;
		
		if (id > otherID) { return -1; }
		else if (id < otherID) { return 1; }
				 
		return 0;
	}
	
	public int getID() {return id;}
	public double getWeight() {return p;}

	/** Reinitialize an IDSorter */
	public void set(int id, double p) { this.id = id; this.p = p; }
}
