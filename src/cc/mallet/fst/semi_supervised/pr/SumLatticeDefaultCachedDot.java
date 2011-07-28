package cc.mallet.fst.semi_supervised.pr;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import cc.mallet.fst.CRF;
import cc.mallet.fst.SumLattice;
import cc.mallet.fst.SumLatticeFactory;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.Transducer.State;
import cc.mallet.fst.Transducer.TransitionIterator;
import cc.mallet.types.DenseVector;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;

/** Default, full dynamic programming implementation of the Forward-Backward "Sum-(Product)-Lattice" algorithm */
public class SumLatticeDefaultCachedDot implements SumLattice
{
	private static Logger logger = MalletLogger.getLogger(SumLatticeDefaultCachedDot.class.getName());
	//{logger.setLevel(Level.FINE);}
	
	// Static variables acting as default values for the correspondingly-named instance variables.
	// Can be overridden sort of like named parameters, like this:
	// SumLattice lattice = new SumLatticeDefault(transducer, input) {{ saveXis=true; }} 
	protected static boolean saveXis = false;

	// "ip" == "input position", "op" == "output position", "i" == "state index"
	Transducer t;
	double totalWeight;
	Sequence input, output;
	LatticeNode[][] nodes;			 // indexed by ip,i
	int latticeLength;
	double[][] gammas;					 // indexed by ip,i
	double[][][] xis;            // indexed by ip,i,j; saved only if saveXis is true;

	LabelVector labelings[];			 // indexed by op, created only if "outputAlphabet" is non-null in constructor

	
	// Ensure that instances cannot easily be created by a zero arg constructor.
	protected SumLatticeDefaultCachedDot() {	}

	protected LatticeNode getLatticeNode (int ip, int stateIndex)
	{
		if (nodes[ip][stateIndex] == null)
			nodes[ip][stateIndex] = new LatticeNode (ip, t.getState (stateIndex));
		return nodes[ip][stateIndex];
	}

	public SumLatticeDefaultCachedDot (Transducer trans, Sequence input, Sequence output, 
	    double[][][] cachedDots, Transducer.Incrementor incrementor, boolean saveXis, LabelAlphabet outputAlphabet)
	{
		assert (output == null || input.size() == output.size());
		if (false && logger.isLoggable (Level.FINE)) {
			logger.fine ("Starting Lattice");
			logger.fine ("Input: ");
			for (int ip = 0; ip < input.size(); ip++)
				logger.fine (" " + input.get(ip));
			logger.fine ("\nOutput: ");
			if (output == null)
				logger.fine ("null");
			else
				for (int op = 0; op < output.size(); op++)
					logger.fine (" " + output.get(op));
			logger.fine ("\n");
		}

		// Initialize some structures
		this.t = trans;
		this.input = input;
		this.output = output;
		// xxx Not very efficient when the lattice is actually sparse,
		// especially when the number of states is large and the
		// sequence is long.
		latticeLength = input.size()+1;
		int numStates = t.numStates();
		nodes = new LatticeNode[latticeLength][numStates];
		// xxx Yipes, this could get big; something sparse might be better?
		gammas = new double[latticeLength][numStates];
		if (saveXis) xis = new double[latticeLength][numStates][numStates];

		double outputCounts[][] = null;
		if (outputAlphabet != null)
			outputCounts = new double[latticeLength][outputAlphabet.size()];

		for (int i = 0; i < numStates; i++) {
			for (int ip = 0; ip < latticeLength; ip++)
				gammas[ip][i] = Transducer.IMPOSSIBLE_WEIGHT;
			if (saveXis)
				for (int j = 0; j < numStates; j++)
					for (int ip = 0; ip < latticeLength; ip++)
						xis[ip][i][j] = Transducer.IMPOSSIBLE_WEIGHT;
		}

		// Forward pass
		logger.fine ("Starting Foward pass");
		boolean atLeastOneInitialState = false;
		for (int i = 0; i < numStates; i++) {
			double initialWeight = t.getState(i).getInitialWeight();
			//System.out.println ("Forward pass initialCost = "+initialCost);
			if (initialWeight > Transducer.IMPOSSIBLE_WEIGHT) {
				getLatticeNode(0, i).alpha = initialWeight;
				//System.out.println ("nodes[0][i].alpha="+nodes[0][i].alpha);
				atLeastOneInitialState = true;
			}
		}
		if (atLeastOneInitialState == false)
			logger.warning ("There are no starting states!");

		for (int ip = 0; ip < latticeLength-1; ip++)
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
					// xxx if we end up doing this a lot,
					// we could save a list of the non-null ones
					continue;
				State s = t.getState(i);
        CachedDotTransitionIterator iter = 
          new CachedDotTransitionIterator((CRF.State)s,input,ip,
              null,cachedDots[ip][i]);
				if (logger.isLoggable (Level.FINE))
					logger.fine (" Starting Foward transition iteration from state "
							+ s.getName() + " on input " + input.get(ip).toString()
							+ " and output "
							+ (output==null ? "(null)" : output.get(ip).toString()));
				while (iter.hasNext()) {
					State destination = iter.nextState();
					if (logger.isLoggable (Level.FINE))
						logger.fine ("Forward Lattice[inputPos="+ip+"][source="+s.getName()+"][dest="+destination.getName()+"]");
					LatticeNode destinationNode = getLatticeNode (ip+1, destination.getIndex());
					destinationNode.output = iter.getOutput();
					double transitionWeight = iter.getWeight();
					if (logger.isLoggable (Level.FINE))
						logger.fine ("BEFORE update: destinationNode.alpha="+destinationNode.alpha);
					destinationNode.alpha = Transducer.sumLogProb (destinationNode.alpha,	nodes[ip][i].alpha + transitionWeight);
					if (logger.isLoggable (Level.FINE))
						logger.fine ("transitionWeight="+transitionWeight+" nodes["+ip+"]["+i+"].alpha="+nodes[ip][i].alpha
								+" destinationNode.alpha="+destinationNode.alpha);
					//System.out.println ("destinationNode.alpha <- "+destinationNode.alpha);
				}
			}
		
		if (logger.isLoggable (Level.FINE)) {
			logger.fine("Forward Lattice:");
			for (int ip = 0; ip < latticeLength; ip++) {
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < numStates; i++) 
					sb.append (" "+(nodes[ip][i] == null ? "<null>" : nodes[ip][i].alpha));
				logger.fine(sb.toString());
			}
		}

		
		// Calculate total weight of Lattice.  This is the normalizer
		totalWeight = Transducer.IMPOSSIBLE_WEIGHT;
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
				//System.out.println ("Ending beta,  state["+i+"] = "+t.getState(i).getFinalWeight());
				totalWeight = Transducer.sumLogProb (totalWeight,	(nodes[latticeLength-1][i].alpha + t.getState(i).getFinalWeight()));
			}
		logger.fine ("totalWeight="+totalWeight);
		// totalWeight is now an "unnormalized weight" of the entire Lattice

		// If the sequence has -infinite weight, just return.
		// Usefully this avoids calling any incrementX methods.
		// It also relies on the fact that the gammas[][] and .alpha (but not .beta) values
		// are already initialized to values that reflect -infinite weight
		// TODO Is it important to fill in the betas before we return?
		if (totalWeight == Transducer.IMPOSSIBLE_WEIGHT)
			return;

		// Backward pass
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				State s = t.getState(i);
				nodes[latticeLength-1][i].beta = s.getFinalWeight();
				gammas[latticeLength-1][i] = nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - totalWeight;
				if (incrementor != null) {
					double p = Math.exp(gammas[latticeLength-1][i]);
					// gsc: reducing from 1e-10 to 1e-6
					// gsc: removing the isNaN check, range check will catch the NaN error as well
          // assert (p >= 0.0 && p <= 1.0+1e-10 && !Double.isNaN(p)) : "p="+p+" gamma="+gammas[latticeLength-1][i];
          assert (p >= 0.0 && p <= 1.0+1e-6) : "p="+p+", gamma="+gammas[latticeLength-1][i];
					incrementor.incrementFinalState (s, p);
				}
			}

		for (int ip = latticeLength-2; ip >= 0; ip--) {
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
					// Note that skipping here based on alpha means that beta values won't
					// be correct, but since alpha is infinite anyway, it shouldn't matter.
					continue;
				State s = t.getState(i);
        CachedDotTransitionIterator iter = 
          new CachedDotTransitionIterator((CRF.State)s,input,ip,
              null,cachedDots[ip][i]);
				while (iter.hasNext()) {
					State destination = iter.nextState();
					if (logger.isLoggable (Level.FINE))
						logger.fine ("Backward Lattice[inputPos="+ip+"][source="+s.getName()+"][dest="+destination.getName()+"]");
					int j = destination.getIndex();
					LatticeNode destinationNode = nodes[ip+1][j];
					if (destinationNode != null) {
						double transitionWeight = iter.getWeight();
						assert (!Double.isNaN(transitionWeight));
						double oldBeta = nodes[ip][i].beta;
						assert (!Double.isNaN(nodes[ip][i].beta));
						nodes[ip][i].beta = Transducer.sumLogProb (nodes[ip][i].beta,	destinationNode.beta + transitionWeight);
						assert (!Double.isNaN(nodes[ip][i].beta))
						: "dest.beta="+destinationNode.beta+" trans="+transitionWeight+" sum="+(destinationNode.beta+transitionWeight)	+ " oldBeta="+oldBeta;
						double xi = nodes[ip][i].alpha + transitionWeight + nodes[ip+1][j].beta - totalWeight;
						if (saveXis) xis[ip][i][j] = xi;
						assert (!Double.isNaN(nodes[ip][i].alpha));
						assert (!Double.isNaN(transitionWeight));
						assert (!Double.isNaN(nodes[ip+1][j].beta));
						assert (!Double.isNaN(totalWeight));
						if (incrementor != null || outputAlphabet != null) {
							double p = Math.exp(xi);
							// gsc: reducing from 1e-10 to 1e-6
              // gsc: removing the isNaN check, range check will catch the NaN error as well
							// assert (p >= 0.0 && p <= 1.0+1e-10 && !Double.isNaN(p)) : "xis["+ip+"]["+i+"]["+j+"]="+xi;
              assert (p >= 0.0 && p <= 1.0+1e-6) : "p="+p+", xis["+ip+"]["+i+"]["+j+"]="+xi;
							if (incrementor != null)
								incrementor.incrementTransition(iter, p);
							if (outputAlphabet != null) {
								int outputIndex = outputAlphabet.lookupIndex (iter.getOutput(), false);
								assert (outputIndex >= 0);
								// xxx This assumes that "ip" == "op"!
								outputCounts[ip][outputIndex] += p;
								//System.out.println ("CRF Lattice outputCounts["+ip+"]["+outputIndex+"]+="+p);
							}
						}
					}
				}
				gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta - totalWeight;
			}
		}
		if (incrementor != null)
			for (int i = 0; i < numStates; i++) {
				double p = Math.exp(gammas[0][i]);
        // gsc: reducing from 1e-10 to 1e-6
        // gsc: removing the isNaN check, range check will catch the NaN error as well
				// assert (p >= 0.0 && p <= 1.0+1e-10 && !Double.isNaN(p)) : "p="+p;
        assert (p >= 0.0 && p <= 1.0+1e-6) : "p="+p;
				incrementor.incrementInitialState(t.getState(i), p);
			}
		if (outputAlphabet != null) {
			labelings = new LabelVector[latticeLength];
			for (int ip = latticeLength-2; ip >= 0; ip--) {
				assert (Math.abs(1.0-MatrixOps.sum (outputCounts[ip])) < 0.000001);;
				labelings[ip] = new LabelVector (outputAlphabet, outputCounts[ip]);
			}
		}
		
		if (logger.isLoggable (Level.FINE)) {
			logger.fine("Lattice:");
			for (int ip = 0; ip < latticeLength; ip++) {
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < numStates; i++) 
					sb.append (" "+gammas[ip][i]);
				logger.fine(sb.toString());
			}
		}
	}



	public double[][][] getXis(){
		return xis;
	}

	public double[][] getGammas(){
		return gammas;
	}

	public double getTotalWeight () {
		assert (!Double.isNaN(totalWeight));
		return totalWeight; }

	public double getGammaWeight(int inputPosition, State s) {
		return gammas[inputPosition][s.getIndex()]; }

	public double getGammaWeight(int inputPosition, int stateIndex) {
		return gammas[inputPosition][stateIndex]; }

	public double getGammaProbability (int inputPosition, State s) {
		return Math.exp (gammas[inputPosition][s.getIndex()]); }

	public double getGammaProbability (int inputPosition, int stateIndex) {
		return Math.exp (gammas[inputPosition][stateIndex]); }

	public double getXiProbability (int ip, State s1, State s2) {
		if (xis == null)
			throw new IllegalStateException ("xis were not saved.");
		int i = s1.getIndex ();
		int j = s2.getIndex ();
		return Math.exp (xis[ip][i][j]);
	}

	public double getXiWeight(int ip, State s1, State s2)
	{
		if (xis == null)
			throw new IllegalStateException ("xis were not saved.");

		int i = s1.getIndex ();
		int j = s2.getIndex ();
		return xis[ip][i][j];
	}

	public int length () { return latticeLength; }

	public Sequence getInput() { 
	  return input;
	}
	
	public double getAlpha (int ip, State s) {
		LatticeNode node = getLatticeNode (ip, s.getIndex ());
		return node.alpha;
	}

	public double getBeta (int ip, State s) {
		LatticeNode node = getLatticeNode (ip, s.getIndex ());
		return node.beta;
	}

	public LabelVector getLabelingAtPosition (int outputPosition)	{
		if (labelings != null)
			return labelings[outputPosition];
		return null;
	}

	public Transducer getTransducer ()
	{
		return t;
	}


	// A container for some information about a particular input position and state
	protected class LatticeNode
	{
		int inputPosition;
		// outputPosition not really needed until we deal with asymmetric epsilon.
		State state;
		Object output;
		double alpha = Transducer.IMPOSSIBLE_WEIGHT;
		double beta = Transducer.IMPOSSIBLE_WEIGHT;
		LatticeNode (int inputPosition, State state)	{
			this.inputPosition = inputPosition;
			this.state = state;
			assert (this.alpha == Transducer.IMPOSSIBLE_WEIGHT);	// xxx Remove this check
		}
	}
}

