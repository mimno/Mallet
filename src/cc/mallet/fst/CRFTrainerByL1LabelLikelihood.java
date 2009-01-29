package cc.mallet.fst;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import cc.mallet.optimize.Optimizer;
import cc.mallet.optimize.OrthantWiseLimitedMemoryBFGS;
import cc.mallet.types.InstanceList;

/**
 * CRF trainer that implements L1-regularization.
 * 
 * @author Kedar Bellare
 */
public class CRFTrainerByL1LabelLikelihood extends CRFTrainerByLabelLikelihood {
	static final double SPARSE_PRIOR = 0.0;

	double l1Weight = SPARSE_PRIOR;

	public CRFTrainerByL1LabelLikelihood(CRF crf) {
		this(crf, SPARSE_PRIOR);
	}

	/**
	 * Constructor for CRF trainer.
	 * 
	 * @param crf
	 *            CRF to train.
	 * @param l1Weight
	 *            Weight of L1 term in objective (l1Weight*|w|). Higher L1
	 *            weight means sparser solutions.
	 */
	public CRFTrainerByL1LabelLikelihood(CRF crf, double l1Weight) {
		super(crf);
		this.l1Weight = l1Weight;
	}

	public void setL1RegularizationWeight(double l1Weight) {
		this.l1Weight = l1Weight;
	}

	public Optimizer getOptimizer(InstanceList trainingSet) {
		getOptimizableCRF(trainingSet);
		if (opt == null || ocrf != opt.getOptimizable())
			opt = new OrthantWiseLimitedMemoryBFGS(ocrf, l1Weight);
		return opt;
	}

	// Serialization
	private static final long serialVersionUID = 1L;

	private static final int CURRENT_SERIAL_VERSION = 0;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeInt(CURRENT_SERIAL_VERSION);
		out.writeDouble(l1Weight);
	}

	private void readObject(ObjectInputStream in) throws IOException {
		in.readInt(); // version
		l1Weight = in.readDouble();
	}
}
