package cc.mallet.extract;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class BIOTokenizationFilterWithTokenIndices extends
		BIOTokenizationFilter {

	protected Span createSpan(Tokenization input, int startTokenIdx,
			int endTokenIdx) {
		StringSpan span = (StringSpan) input
				.subspan(startTokenIdx, endTokenIdx);
		span.setProperty("StartTokenIdx", Integer.valueOf(startTokenIdx));
		span.setProperty("EndTokenIdx", Integer.valueOf(endTokenIdx-1));
		return span;
	}

	// Serialization garbage

	private static final long serialVersionUID = 1L;

	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		out.writeInt(CURRENT_SERIAL_VERSION);
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		in.defaultReadObject();
		in.readInt(); // read version
	}

}
