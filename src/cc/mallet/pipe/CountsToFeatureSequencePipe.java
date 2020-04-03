package cc.mallet.pipe;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.util.*;

import java.util.*;

public class CountsToFeatureSequencePipe extends Pipe {

	public Instance pipe (Instance instance) {
		if (! (instance.getData() instanceof String)) {
			throw new IllegalArgumentException("CountsToFeatureSequencePipe only accepts Strings");
		}

		String input = (String) instance.getData();
		String[] fields = input.split(" ");

		int locations = Integer.parseInt(fields[0]);
		int[] ids = new int[locations];
		int[] counts = new int[locations];

		int length = 0;
		for (int i = 0; i < locations; i++) {
			String[] pair = fields[i+1].split( ":" );
			ids[i] = Integer.parseInt(pair[0]);
			counts[i] = Integer.parseInt(pair[1]);
			length += counts[i];
		}

		int[] features = new int[length];

		// convert feature/count pairs to feature sequences
		int position = 0;
		for (int i = 0; i < locations; i++) {
			for (int j = 0; j < counts[i]; j++) {
				features[ position ] = ids[i];
				position++;
			}
		}
		
		// Pass through one more time to shuffle randomly
		Randoms random = new Randoms();
		for (position = 0; position < length; position++) {
			int offset = random.nextInt(length - position);
			int swappedValue = features[position + offset];
			features[position + offset] = features[position];
			features[position] = swappedValue;
		}

		instance.setData( new FeatureSequence(this.getDataAlphabet(), features));

		return instance;
	}
}
