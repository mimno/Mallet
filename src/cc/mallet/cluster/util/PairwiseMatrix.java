package cc.mallet.cluster.util;

import java.io.Serializable;

/**
 * 2-D upper-triangular matrix. Used to store pairwise affinity
 * scores.
 *
 * @author "Aron Culotta" <culotta@degas.cs.umass.edu>
 * @version 1.0
 * @since 1.0
 */
public class PairwiseMatrix implements Serializable {
	
	private static final long serialVersionUID = 1L;

	double[][] vals;
	
	public PairwiseMatrix (int size) {
		vals = new double[size - 1][];
		for (int i = 0; i < size - 1; i++) {
			vals[i] = new double[size - i - 1];
			for (int j = 0; j < vals[i].length; j++)
				vals[i][j] = 0.0;
		}
	}

	public void set (int i, int j, double v) {
		int[] indices = sort(i, j);
		vals[indices[0]][indices[1] - indices[0] - 1] = v;
	}

	public double get (int i, int j) {
		int[] indices = sort(i, j);
		return vals[indices[0]][indices[1] - indices[0] - 1];
	}

	public int length (int i) {
		return vals[i].length;
	}
	
	private int[] sort (int i, int j) {
		int[] ret = new int[2];
		if (i < j) {
			ret[0] = i;
			ret[1] = j;
		} else {
			ret[0] = j;
			ret[1] = i;
		}
		return ret;
	}

	public String toString () {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < vals.length; i++) {
			for (int j = 0; j < vals[i].length; j++) {
				buf.append(vals[i][j] + "    ");
			}
			buf.append("\n");
		}
		return buf.toString();
	}
	
	public static void main (String[] args) {
		int size = Integer.parseInt(args[0]);
		PairwiseMatrix m = new PairwiseMatrix(size);
		for (int i = 0; i < size; i++) 
			for (int j = i + 1; j < size; j++) 
				m.set(i, j, Double.parseDouble(i + "" + j));

		System.err.println(m);
	}
}
