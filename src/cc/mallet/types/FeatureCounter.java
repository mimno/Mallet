package cc.mallet.types;

import com.carrotsearch.hppc.IntIntHashMap;

/** Efficient, compact, incremental counting of features in an alphabet. */
public class FeatureCounter 
{
	Alphabet alphabet;
	IntIntHashMap featureCounts;
	
	public FeatureCounter (Alphabet alphabet) {
		this.alphabet = alphabet;
		featureCounts = new IntIntHashMap();
	}
	
	public int increment (Object entry) {
		return featureCounts.putOrAdd(alphabet.lookupIndex(entry), 1, 1);
	}

	public int increment (Object entry, int incr) {
		return featureCounts.putOrAdd(alphabet.lookupIndex(entry), incr, incr);
	}

	public int increment (int featureIndex) {
		if (featureIndex < 0 || featureIndex > alphabet.size())
			throw new IllegalArgumentException ("featureIndex "+featureIndex+" out of range");
		return featureCounts.putOrAdd(featureIndex, 1, 1);
	}

	public int increment (int featureIndex, int incr) {
		if (featureIndex < 0 || featureIndex > alphabet.size())
			throw new IllegalArgumentException ("featureIndex "+featureIndex+" out of range");
		return featureCounts.putOrAdd(featureIndex, incr, incr);
	}
	
	
	public int get (int featureIndex) {
		if (featureIndex < 0 || featureIndex > alphabet.size())
			throw new IllegalArgumentException ("featureIndex "+featureIndex+" out of range");
		return featureCounts.get (featureIndex);
	}

	/** Unlike increment(Object), this method does not add the entry to the Alphabet if it is not there already. */
	public int get (Object entry) {
		int fi = alphabet.lookupIndex(entry, false);
		if (fi == -1)
			return 0;
		else
			return featureCounts.get (fi);
	}
	
	public int put (int featureIndex, int value) {
		if (featureIndex < 0 || featureIndex > alphabet.size())
			throw new IllegalArgumentException ("featureIndex "+featureIndex+" out of range");
		return featureCounts.put (featureIndex, value);
	}
	
	public int put (Object entry, int value) {
		return featureCounts.put (alphabet.lookupIndex(entry), value);
	}

	
	public FeatureVector toFeatureVector () {
		int[] indices = featureCounts.keys().toArray();
		double[] values = new double[indices.length];
		for (int i = 0; i < indices.length; i++)
			values[i] = featureCounts.get(indices[i]);
		return new FeatureVector (alphabet, indices, values);
	}
	
	public RankedFeatureVector toRankedFeatureVector () {
		int[] indices = featureCounts.keys().toArray();
		double[] values = new double[indices.length];
		for (int i = 0; i < indices.length; i++)
			values[i] = featureCounts.get(indices[i]);
		return new RankedFeatureVector (alphabet, indices, values);
	}
	
}
