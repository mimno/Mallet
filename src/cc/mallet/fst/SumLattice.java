package cc.mallet.fst;

import cc.mallet.fst.Transducer.State;
import cc.mallet.types.LabelVector;

public interface SumLattice {
	public double[][][] getXis();
	public double[][] getGammas();
	public double getTotalWeight ();
	public double getGammaWeight (int inputPosition, State s);
	public double getGammaProbability (int inputPosition, State s);
	public double getXiProbability (int ip, State s1, State s2);
	public double getXiWeight (int ip, State s1, State s2);
	public int length ();
	public double getAlpha (int ip, State s);
	public double getBeta (int ip, State s);
	public LabelVector getLabelingAtPosition (int outputPosition);
	public Transducer getTransducer ();
}
