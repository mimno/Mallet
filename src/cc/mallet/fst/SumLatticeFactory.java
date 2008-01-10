package cc.mallet.fst;

import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;

/**  */
public abstract class SumLatticeFactory {

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

	// You may pass null for output, meaning that the ForwardBackward
	// is not constrained to match the output
	public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, Transducer.Incrementor incrementor)
	{
		return newSumLattice (trans, input, output, incrementor, false, null);
	}

	public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, Transducer.Incrementor incrementor, LabelAlphabet outputAlphabet)
	{
		return newSumLattice (trans, input, output, incrementor, false, outputAlphabet);
	}

	// You may pass null for output, meaning that the ForwardBackward
	// is not constrained to match the output
	public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, Transducer.Incrementor incrementor, boolean saveXis)
	{
		return newSumLattice (trans, input, output, incrementor, saveXis, null);
	}

	// If outputAlphabet is non-null, this will create a LabelVector
	// for each position in the output sequence indicating the
	// probability distribution over possible outputs at that time
	// index
	public abstract SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, 
			Transducer.Incrementor incrementor, boolean saveXis, LabelAlphabet outputAlphabet);
	
}
