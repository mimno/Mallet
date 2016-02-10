package cc.mallet.cluster;

import java.io.Serializable;


public class Clusterings implements Serializable {

	private static final long serialVersionUID = 1L;

	Clustering[] clusterings;
	
	public Clusterings (Clustering[] clusterings) {
		this.clusterings = clusterings;
	}
	
	public Clustering get (int i) { return clusterings[i]; }
		
	public void set (int i, Clustering clustering) { this.clusterings[i] = clustering; }
	
	public int size () { return clusterings.length; }
}
