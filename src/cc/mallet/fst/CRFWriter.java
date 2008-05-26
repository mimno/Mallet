package cc.mallet.fst;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.logging.Logger;

import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

/*
 * Saves a trained model to specified filename. <p>
 * 
 * Can be used to save the model every few iterations, e.g. to save every 5 iterations: <p>
 * <code>
 * new CRFWriter(filePrefix) { public boolean precondition (TransducerTrainer tt) { return tt.getIteration() % 5 == 0; };
 * </code> <p>
 * 
 * The trained model is saved in the format: filenamePrefix.<iteration>.bin.
 * 
 * @author Gaurav Chandalia
 */
public class CRFWriter extends TransducerEvaluator {
	private static Logger logger = MalletLogger.getLogger(CRFWriter.class.getName());

	String filenamePrefix;

	public CRFWriter (String filenamePrefix) {
		super (new InstanceList[]{}, new String[]{});
		this.filenamePrefix = filenamePrefix;
	}

	protected void preamble (TransducerTrainer tt) {
		int iteration = tt.getIteration();
		String filename = filenamePrefix + "." + iteration + ".bin";
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
			oos.writeObject(tt.getTransducer());
			logger.info("Trained model saved: " + filename + ", iter: " + iteration);
		} catch (FileNotFoundException fnfe) {
			logger.warning("Could not save model: " + filename + ", iter: " + iteration);
			fnfe.printStackTrace();
		} catch (IOException ioe) {
			logger.warning("Could not save model: " + filename + ", iter: " + iteration);
			ioe.printStackTrace();
		}
	}

	@Override
	public void evaluateInstanceList(TransducerTrainer transducer, InstanceList instances, String description) { }
}