package cc.mallet.pipe;

import cc.mallet.types.*;
import cc.mallet.pipe.*;

import java.io.Serializable;
import java.util.Arrays;

public class TargetStringToFeatures extends Pipe implements Serializable {
	public TargetStringToFeatures () {
		super(null, new Alphabet());
	}

	public Instance pipe(Instance carrier) {
		if (! (carrier.getTarget() instanceof String)) {
			throw new IllegalArgumentException("Target must be of type String");
		}

		String featuresLine = (String) carrier.getTarget();

		String[] features = featuresLine.split(",?\\s+");

		double[] values = new double[ features.length ];
		Arrays.fill(values, 1.0);

		for (int i=0; i<features.length; i++) {

			// Support the syntax "FEATURE=0.000342 OTHER_FEATURE=-2.32423"                                       \
                                                                                                                       
			if (features[i].indexOf("=") != -1) {
				String[] keyValuePair = features[i].split("=");
				features[i] = keyValuePair[0];
				values[i] = Double.parseDouble(keyValuePair[1]);
			}

			// ensure that the feature has a spot in the alphabet                                                 \
                                                                                                                       
			getTargetAlphabet().lookupIndex(features[i], true);
		}

		FeatureVector target = new FeatureVector(getTargetAlphabet(), features, values);

		carrier.setTarget(target);

		return carrier;
	}

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

}
