package cc.mallet.fst;

import cc.mallet.fst.Transducer.State;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Sequence;

/**
 * Interface to perform forward-backward during training of a transducer.
 */
public interface SumLattice {
	public double[][][] getXis();
	public double[][] getGammas();
	public double getTotalWeight ();
	public double getGammaWeight (int inputPosition, State s);
	public double getGammaProbability (int inputPosition, State s);
	public double getXiProbability (int ip, State s1, State s2);
	public double getXiWeight (int ip, State s1, State s2);
	public int length ();
	public Sequence getInput();
	public double getAlpha (int ip, State s);
	public double getBeta (int ip, State s);
	public LabelVector getLabelingAtPosition (int outputPosition);
	public Transducer getTransducer ();
}
