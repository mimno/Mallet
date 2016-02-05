package cc.mallet.pipe;

import cc.mallet.types.*;
import java.io.Serializable;

public class ValueString2FeatureVector extends Pipe implements Serializable {
	
	public ValueString2FeatureVector (String[] fieldNames) {
		Alphabet alphabet = new Alphabet();

		for (String s: fieldNames) { alphabet.lookupIndex(s); }

		this.dataAlphabet = alphabet;
	}

	public ValueString2FeatureVector () {}
	
	public Instance pipe(Instance carrier) {

		String data = (String) carrier.getData();
		
		String[] fields = data.trim().split("\\s+");
		double[] values = new double[fields.length];
		
		for (int i=0; i<fields.length; i++) {
			values[i] = Double.parseDouble(fields[i]);
		}

		if (this.dataAlphabet == null) {
			Alphabet alphabet = new Alphabet();
			for (int i=0; i < fields.length; i++) {
				alphabet.lookupIndex("V" + i);
			}
			this.dataAlphabet = alphabet;
		}

		carrier.setData(new FeatureVector(this.dataAlphabet, values));

		return carrier;
	}
	
}