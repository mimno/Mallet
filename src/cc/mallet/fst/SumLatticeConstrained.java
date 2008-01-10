package cc.mallet.fst;

import java.util.logging.Level;
import java.util.logging.Logger;

import cc.mallet.fst.SumLatticeDefault.LatticeNode;
import cc.mallet.fst.Transducer.State;
import cc.mallet.fst.Transducer.TransitionIterator;
import cc.mallet.types.DenseVector;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.Sequence;
import cc.mallet.util.MalletLogger;


public class SumLatticeConstrained extends SumLatticeDefault {
	
	private static Logger logger = MalletLogger.getLogger(SumLatticeConstrained.class.getName());

	public SumLatticeConstrained (Transducer t, Sequence input, Sequence output, Segment requiredSegment, Sequence constrainedSequence) {
		this (t, input, output, (Transducer.Incrementor)null, null, makeConstraints(t, input, output, requiredSegment, constrainedSequence));
	}
	private static int[] makeConstraints (Transducer t, Sequence inputSequence, Sequence outputSequence, Segment requiredSegment, Sequence constrainedSequence) {
		if (constrainedSequence.size () != inputSequence.size ())
			throw new IllegalArgumentException ("constrainedSequence.size [" + constrainedSequence.size () + "] != inputSequence.size [" + inputSequence.size () + "]");
		// constraints tells the lattice which states must emit which
		// observations.  positive values say all paths must pass through
		// this state index, negative values say all paths must _not_
		// pass through this state index.  0 means we don't
		// care. initialize to 0. include 1 extra node for start state.
		int [] constraints = new int [constrainedSequence.size() + 1];
		for (int c = 0; c < constraints.length; c++)
			constraints[c] = 0;
		for (int i=requiredSegment.getStart (); i <= requiredSegment.getEnd(); i++) {
			int si = t.stateIndexOfString ((String)constrainedSequence.get (i));
			if (si == -1)
				logger.warning ("Could not find state " + constrainedSequence.get (i) + ". Check that state labels match startTages and inTags, and that all labels are seen in training data.");
//			throw new IllegalArgumentException ("Could not find state " + constrainedSequence.get(i) + ". Check that state labels match startTags and InTags.");
			constraints[i+1] = si + 1;
		}
		// set additional negative constraint to ensure state after
		// segment is not a continue tag

		// xxx if segment length=1, this actually constrains the sequence
		// to B-tag (B-tag)', instead of the intended constraint of B-tag
		// (I-tag)'
		// the fix below is unsafe, but will have to do for now.
		// FIXED BELOW
		/*		String endTag = (String) constrainedSequence.get (requiredSegment.getEnd ());
				if (requiredSegment.getEnd()+2 < constraints.length) {
					if (requiredSegment.getStart() == requiredSegment.getEnd()) { // segment has length 1
						if (endTag.startsWith ("B-")) {
							endTag = "I" + endTag.substring (1, endTag.length());
						}
						else if (!(endTag.startsWith ("I-") || endTag.startsWith ("0")))
							throw new IllegalArgumentException ("Constrained Lattice requires that states are tagged in B-I-O format.");
					}
					int statei = stateIndexOfString (endTag);
					if (statei == -1) // no I- tag for this B- tag
						statei = stateIndexOfString ((String)constrainedSequence.get (requiredSegment.getStart ()));
					constraints[requiredSegment.getEnd() + 2] = - (statei + 1);
				}
		 */
		if (requiredSegment.getEnd() + 2 < constraints.length) { // if
			String endTag = requiredSegment.getInTag().toString();
			int statei = t.stateIndexOfString (endTag);
			if (statei == -1)
				throw new IllegalArgumentException ("Could not find state " + endTag + ". Check that state labels match startTags and InTags.");
			constraints[requiredSegment.getEnd() + 2] = - (statei + 1);
		}

		//		printStates ();
		logger.fine ("Segment:\n" + requiredSegment.sequenceToString () +
				"\nconstrainedSequence:\n" + constrainedSequence +
		"\nConstraints:\n");
		for (int i=0; i < constraints.length; i++) {
			logger.fine (constraints[i] + "\t");
		}
		logger.fine ("");
		return constraints;
	}

	// culotta: constructor for constrained lattice
	/** Create a lattice that constrains its transitions such that the
	 * <position,label> pairs in "constraints" are adhered
	 * to. constraints is an array where each entry is the index of
	 * the required label at that position. An entry of 0 means there
	 * are no constraints on that <position, label>. Positive values
	 * mean the path must pass through that state. Negative values
	 * mean the path must _not_ pass through that state. NOTE -
	 * constraints.length must be equal to output.size() + 1. A
	 * lattice has one extra position for the initial
	 * state. Generally, this should be unconstrained, since it does
	 * not produce an observation.
	 */
	public SumLatticeConstrained (Transducer trans, Sequence input, Sequence output, Transducer.Incrementor incrementor, LabelAlphabet outputAlphabet, int [] constraints)
	{
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
		// xxx Move this to an ivar, so we can save it?  But for what?
		// Commenting this out, because it's a memory hog and not used right now.
		//  Uncomment and conditionalize under a flag if ever needed. -cas
		// double xis[][][] = new double[latticeLength][numStates][numStates];
		double outputCounts[][] = null;
		if (outputAlphabet != null)
			outputCounts = new double[latticeLength][outputAlphabet.size()];

		for (int i = 0; i < numStates; i++) {
			for (int ip = 0; ip < latticeLength; ip++)
				gammas[ip][i] = Transducer.IMPOSSIBLE_WEIGHT;
			/* Commenting out xis -cas
			for (int j = 0; j < numStates; j++)
				for (int ip = 0; ip < latticeLength; ip++)
					xis[ip][i][j] = IMPOSSIBLE_WEIGHT;
			 */
		}

		// Forward pass
		logger.fine ("Starting Constrained Foward pass");

		// ensure that at least one state has initial weight greater than -Infinity
		// so we can start from there
		boolean atLeastOneInitialState = false;
		for (int i = 0; i < numStates; i++) {
			double initialWeight = t.getState(i).getInitialWeight();
			//System.out.println ("Forward pass initialWeight = "+initialWeight);
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
				logger.fine ("ip=" + ip+", i=" + i);
				// check if this node is possible at this <position,
				// label>. if not, skip it.
				if (constraints[ip] > 0) { // must be in state indexed by constraints[ip] - 1
					if (constraints[ip]-1 != i) {
						logger.fine ("Current state does not match positive constraint. position="+ip+", constraint="+(constraints[ip]-1)+", currState="+i);
						continue;
					}
				}
				else if (constraints[ip] < 0) { // must _not_ be in state indexed by constraints[ip]
					if (constraints[ip]+1 == -i) {
						logger.fine ("Current state does not match negative constraint. position="+ip+", constraint="+(constraints[ip]+1)+", currState="+i);
						continue;
					}
				}
				if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT) {
					// xxx if we end up doing this a lot,
					// we could save a list of the non-null ones
					if (nodes[ip][i] == null) logger.fine ("nodes[ip][i] is NULL");
					else if (nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT) logger.fine ("nodes[ip][i].alpha is -Inf");
					logger.fine ("-INFINITE weight or NULL...skipping");
					continue;
				}
				State s = t.getState(i);

				TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
				if (logger.isLoggable (Level.FINE))
					logger.fine (" Starting Forward transition iteration from state "
							+ s.getName() + " on input " + input.get(ip).toString()
							+ " and output "
							+ (output==null ? "(null)" : output.get(ip).toString()));
				while (iter.hasNext()) {
					State destination = iter.nextState();
					boolean legalTransition = true;
					// check constraints to see if node at <ip,i> can transition to destination
					if (ip+1 < constraints.length && constraints[ip+1] > 0 && ((constraints[ip+1]-1) != destination.getIndex())) {
						logger.fine ("Destination state does not match positive constraint. Assigning -infinite weight. position="+(ip+1)+", constraint="+(constraints[ip+1]-1)+", source ="+i+", destination="+destination.getIndex());
						legalTransition = false;
					}
					else if (((ip+1) < constraints.length) && constraints[ip+1] < 0 && (-(constraints[ip+1]+1) == destination.getIndex())) {
						logger.fine ("Destination state does not match negative constraint. Assigning -infinite weight. position="+(ip+1)+", constraint="+(constraints[ip+1]+1)+", destination="+destination.getIndex());
						legalTransition = false;
					}

					if (logger.isLoggable (Level.FINE))
						logger.fine ("Forward Lattice[inputPos="+ip
								+"][source="+s.getName()
								+"][dest="+destination.getName()+"]");
					LatticeNode destinationNode = getLatticeNode (ip+1, destination.getIndex());
					destinationNode.output = iter.getOutput();
					double transitionWeight = iter.getWeight();
					if (legalTransition) {
						//if (logger.isLoggable (Level.FINE))
						logger.fine ("transitionWeight="+transitionWeight
								+" nodes["+ip+"]["+i+"].alpha="+nodes[ip][i].alpha
								+" destinationNode.alpha="+destinationNode.alpha);
						destinationNode.alpha = Transducer.sumLogProb (destinationNode.alpha,
								nodes[ip][i].alpha + transitionWeight);
						//System.out.println ("destinationNode.alpha <- "+destinationNode.alpha);
						logger.fine ("Set alpha of latticeNode at ip = "+ (ip+1) + " stateIndex = " + destination.getIndex() + ", destinationNode.alpha = " + destinationNode.alpha);
					}
					else {
						// this is an illegal transition according to our
						// constraints, so set its prob to 0 . NO, alpha's are
						// unnormalized weights...set to -Inf //
						// destinationNode.alpha = 0.0;
//						destinationNode.alpha = IMPOSSIBLE_WEIGHT;
						logger.fine ("Illegal transition from state " + i + " to state " + destination.getIndex() + ". Setting alpha to -Inf");
					}
				}
			}

		// Calculate total weight of Lattice.  This is the normalizer
		totalWeight = Transducer.IMPOSSIBLE_WEIGHT;
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				// Note: actually we could sum at any ip index,
				// the choice of latticeLength-1 is arbitrary
				//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
				//System.out.println ("Ending beta,  state["+i+"] = "+getState(i).finalWeight);
				if (constraints[latticeLength-1] > 0 && i != constraints[latticeLength-1]-1)
					continue;
				if (constraints[latticeLength-1] < 0 && -i == constraints[latticeLength-1]+1)
					continue;
				logger.fine ("Summing final lattice weight. state="+i+", alpha="+nodes[latticeLength-1][i].alpha + ", final weight = "+t.getState(i).getFinalWeight());
				totalWeight = Transducer.sumLogProb (totalWeight,
						(nodes[latticeLength-1][i].alpha + t.getState(i).getFinalWeight()));
			}
		// Weight is now an "unnormalized weight" of the entire Lattice
		//assert (weight >= 0) : "weight = "+weight;

		// If the sequence has -infinite weight, just return.
		// Usefully this avoids calling any incrementX methods.
		// It also relies on the fact that the gammas[][] and .alpha and .beta values
		// are already initialized to values that reflect -infinite weight
		// xxx Although perhaps not all (alphas,betas) exactly correctly reflecting?
		if (totalWeight == Transducer.IMPOSSIBLE_WEIGHT)
			return;

		// Backward pass
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				State s = t.getState(i);
				nodes[latticeLength-1][i].beta = s.getFinalWeight();
				gammas[latticeLength-1][i] =
					nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - totalWeight;
				if (incrementor != null) {
					double p = Math.exp(gammas[latticeLength-1][i]);
					assert (p >= 0.0 && p <= 1.0 && !Double.isNaN(p))	: "p="+p+" gamma="+gammas[latticeLength-1][i];
					incrementor.incrementFinalState(s, p);
				}
			}
		for (int ip = latticeLength-2; ip >= 0; ip--) {
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
					// Note that skipping here based on alpha means that beta values won't
					// be correct, but since alpha is -infinite anyway, it shouldn't matter.
					continue;
				State s = t.getState(i);
				TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
				while (iter.hasNext()) {
					State destination = iter.nextState();
					if (logger.isLoggable (Level.FINE))
						logger.fine ("Backward Lattice[inputPos="+ip
								+"][source="+s.getName()
								+"][dest="+destination.getName()+"]");
					int j = destination.getIndex();
					LatticeNode destinationNode = nodes[ip+1][j];
					if (destinationNode != null) {
						double transitionWeight = iter.getWeight();
						assert (!Double.isNaN(transitionWeight));
						//							assert (transitionWeight >= 0);  Not necessarily
						double oldBeta = nodes[ip][i].beta;
						assert (!Double.isNaN(nodes[ip][i].beta));
						nodes[ip][i].beta = Transducer.sumLogProb (nodes[ip][i].beta,
								destinationNode.beta + transitionWeight);
						assert (!Double.isNaN(nodes[ip][i].beta))
						: "dest.beta="+destinationNode.beta+" trans="+transitionWeight+" sum="+(destinationNode.beta+transitionWeight)
						+ " oldBeta="+oldBeta;
						// xis[ip][i][j] = nodes[ip][i].alpha + transitionWeight + nodes[ip+1][j].beta - weight;
						assert (!Double.isNaN(nodes[ip][i].alpha));
						assert (!Double.isNaN(transitionWeight));
						assert (!Double.isNaN(nodes[ip+1][j].beta));
						assert (!Double.isNaN(totalWeight));
						if (incrementor != null || outputAlphabet != null) {
							double xi = nodes[ip][i].alpha + transitionWeight + nodes[ip+1][j].beta - totalWeight;
							double p = Math.exp(xi);
							assert (p > Transducer.IMPOSSIBLE_WEIGHT && !Double.isNaN(p)) : "xis["+ip+"]["+i+"]["+j+"]="+xi;
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
				assert (p > Transducer.IMPOSSIBLE_WEIGHT && !Double.isNaN(p));
				incrementor.incrementInitialState(t.getState(i), p);
			}
		if (outputAlphabet != null) {
			labelings = new LabelVector[latticeLength];
			for (int ip = latticeLength-2; ip >= 0; ip--) {
				assert (Math.abs(1.0-DenseVector.sum (outputCounts[ip])) < 0.000001);;
				labelings[ip] = new LabelVector (outputAlphabet, outputCounts[ip]);
			}
		}
	}
	
	// The following used to be in fst.Transducer.
	// Does it still apply?  Does it still need addressing?
	// -akm
	
	// culotta: interface for constrained lattice
	/**
		 Create constrained lattice such that all paths pass through the
		 the labeling of <code> requiredSegment </code> as indicated by
		 <code> constrainedSequence </code>
		 @param inputSequence input sequence
		 @param outputSequence output sequence
		 @param requiredSegment segment of sequence that must be labelled
		 @param constrainedSequence lattice must have labels of this
		 sequence from <code> requiredSegment.start </code> to <code>
		 requiredSegment.end </code> correctly
	 *//*
	public Lattice forwardBackward (Sequence inputSequence,
	                                Sequence outputSequence,
	                                Segment requiredSegment,
	                                Sequence constrainedSequence) {
		if (constrainedSequence.size () != inputSequence.size ())
			throw new IllegalArgumentException ("constrainedSequence.size [" + constrainedSequence.size () + "] != inputSequence.size [" + inputSequence.size () + "]"); 
		// constraints tells the lattice which states must emit which
		// observations.  positive values say all paths must pass through
		// this state index, negative values say all paths must _not_
		// pass through this state index.  0 means we don't
		// care. initialize to 0. include 1 extra node for start state.
		int [] constraints = new int [constrainedSequence.size() + 1];
		for (int c = 0; c < constraints.length; c++)
			constraints[c] = 0;
		for (int i=requiredSegment.getStart (); i <= requiredSegment.getEnd(); i++) {
			int si = stateIndexOfString ((String)constrainedSequence.get (i));
			if (si == -1)
				logger.warning ("Could not find state " + constrainedSequence.get (i) + ". Check that state labels match startTages and inTags, and that all labels are seen in training data.");
//			throw new IllegalArgumentException ("Could not find state " + constrainedSequence.get(i) + ". Check that state labels match startTags and InTags.");
			constraints[i+1] = si + 1;
		}
		// set additional negative constraint to ensure state after
		// segment is not a continue tag

		// xxx if segment length=1, this actually constrains the sequence
		// to B-tag (B-tag)', instead of the intended constraint of B-tag
		// (I-tag)'
		// the fix below is unsafe, but will have to do for now.
		// FIXED BELOW
		/*		String endTag = (String) constrainedSequence.get (requiredSegment.getEnd ());
		if (requiredSegment.getEnd()+2 < constraints.length) {
			if (requiredSegment.getStart() == requiredSegment.getEnd()) { // segment has length 1
				if (endTag.startsWith ("B-")) {
					endTag = "I" + endTag.substring (1, endTag.length());
				}
				else if (!(endTag.startsWith ("I-") || endTag.startsWith ("0")))
					throw new IllegalArgumentException ("Constrained Lattice requires that states are tagged in B-I-O format.");
			}
			int statei = stateIndexOfString (endTag);
			if (statei == -1) // no I- tag for this B- tag
				statei = stateIndexOfString ((String)constrainedSequence.get (requiredSegment.getStart ()));
			constraints[requiredSegment.getEnd() + 2] = - (statei + 1);
		}
		 *//*
		if (requiredSegment.getEnd() + 2 < constraints.length) { // if 
			String endTag = requiredSegment.getInTag().toString();
			int statei = stateIndexOfString (endTag);
			if (statei == -1)
				logger.fine ("Could not find state " + endTag + ". Check that state labels match startTags and InTags.");
			else
				constraints[requiredSegment.getEnd() + 2] = - (statei + 1);			
		}

		logger.fine ("Segment:\n" + requiredSegment.sequenceToString () +
				"\nconstrainedSequence:\n" + constrainedSequence +
		"\nConstraints:\n");
		for (int i=0; i < constraints.length; i++) {
			logger.fine (constraints[i] + "\t");
		}
		logger.fine ("");
		return forwardBackward (inputSequence, outputSequence, constraints);
	}		
*/

}
