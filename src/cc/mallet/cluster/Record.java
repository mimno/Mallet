package cc.mallet.cluster;

import gnu.trove.TIntObjectHashMap;

import java.io.Serializable;

import cc.mallet.types.Alphabet;
import cc.mallet.types.AugmentableFeatureVector;
import cc.mallet.types.FeatureVector;

public class Record implements Serializable {

	private static final long serialVersionUID = 1L;

	Alphabet fieldAlph;

	Alphabet valueAlph;

	TIntObjectHashMap<FeatureVector> field2values;

	public Record (Alphabet fieldAlph, Alphabet valueAlph) {
		this.fieldAlph = fieldAlph;
		this.valueAlph = valueAlph;
		field2values = new TIntObjectHashMap<FeatureVector>();
	}

	public Record (Alphabet fieldAlph, Alphabet valueAlph, String[][] vals) {
		this(fieldAlph, valueAlph);
		for (int i = 0; i < vals.length; i++) {
			AugmentableFeatureVector afv = new AugmentableFeatureVector(valueAlph, false);
			for (int j = 1; j < vals[i].length; j++)
				afv.add(valueAlph.lookupIndex(vals[i][j]), 1.0);
			field2values.put(fieldAlph.lookupIndex(vals[i][0]), afv.toFeatureVector());
		}
	}
	
	public FeatureVector values (String field) {
		return values(fieldAlph.lookupIndex(field));
	}

	public FeatureVector values (int field) {
		return (FeatureVector) field2values.get(field);
	}
	
	public int value (String field) {
		return value(fieldAlph.lookupIndex(field));
	}
	
	public int value (int field) {
		FeatureVector fv = values(field);
		return (fv == null) ? -1 : fv.indexAtLocation(0);
	}
	
	public int[] fields () { return field2values.keys(); }
	
	public Alphabet fieldAlphabet () { return this.fieldAlph; }
	
	public Alphabet valueAlphabet () { return this.valueAlph; }

	public String toString () { return toString(true); }
	
	public String toString (boolean oneLine) {
		StringBuffer b = new StringBuffer();
		int[] keys = field2values.keys();
		for (int i = 0; i < keys.length; i++) {
			b.append(fieldAlph.lookupObject(keys[i]) + "=");
			FeatureVector v = (FeatureVector) field2values.get(keys[i]);
			for (int j = 0; j < v.numLocations(); j++)
				b.append(valueAlph.lookupObject(v.indexAtLocation(j)) + ",");
			if (!oneLine) b.append("\n");
		}
		return b.toString();
	}

	public static void main (String[] args) {
		Record r =
				new Record(new Alphabet(), new Alphabet(),
										new String[][] { { "field1", "f1v1", "f1v2" },
																		{ "field2", "f2v1" },
																		{ "field3", "f3v1", "f3v2", "f3v3" } });
		System.out.println(r);
	}
}
