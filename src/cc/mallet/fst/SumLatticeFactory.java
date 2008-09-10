package cc.mallet.fst;

import java.io.Serializable;

import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;

/**
 * Provides factory methods to create inference engine for training a transducer.
 */
public abstract class SumLatticeFactory implements Serializable {

	public SumLattice newSumLattice (Transducer trans, Sequence input)
	{
		return newSumLattice (trans, input, null, (Transducer.Incrementor)null, false, null);
	}

	public SumLattice newSumLattice (Transducer trans, Sequence input, Transducer.Incrementor incrementor)
	{
		return newSumLattice (trans, input, null, incrementor, false, null);
	}

	public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output)
	{
		return newSumLattice (trans, input, output, (Transducer.Incrementor)null, false, null);
	}

	public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, Transducer.Incrementor incrementor)
	{
		return newSumLattice (trans, input, output, incrementor, false, null);
	}

	public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, Transducer.Incrementor incrementor, LabelAlphabet outputAlphabet)
	{
		return newSumLattice (trans, input, output, incrementor, false, outputAlphabet);
	}

	public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, Transducer.Incrementor incrementor, boolean saveXis)
	{
		return newSumLattice (trans, input, output, incrementor, saveXis, null);
	}

	/** 
	 * Returns a SumLattice object to run forward-backward.
	 * 
	 * @param trans Transducer model
	 * @param input Input sequence
	 * @param output If output is null then the forward-backward is not constrained to match the output
	 * @param incrementor If null then do not update the weights
	 * @param saveXis If true then save the transition weights as well
	 * @param outputAlphabet If outputAlphabet is non-null, this will create a LabelVector for each 
   *         position in the output sequence indicating the probability distribution 
   *         over possible outputs at that time index.
	 */
	public abstract SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, 
			Transducer.Incrementor incrementor, boolean saveXis, LabelAlphabet outputAlphabet);
	
}
