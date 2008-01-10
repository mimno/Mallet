package cc.mallet.fst;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import cc.mallet.fst.Transducer.State;
import cc.mallet.fst.Transducer.TransitionIterator;
import cc.mallet.types.DenseVector;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.Sequence;
import cc.mallet.types.SequencePair;
import cc.mallet.util.MalletLogger;



//******************************************************************************
//CPAL - NEW "BEAM" Version of Forward Backward
//******************************************************************************


public class SumLatticeBeam implements SumLattice  // CPAL - like Lattice but using max-product to get the viterbiPath
{


	// CPAL - these worked well for nettalk
	//private int beamWidth = 10;
	//private double KLeps = .005;
	boolean UseForwardBackwardBeam = false;
	protected static int beamWidth = 3;
	private double KLeps = 0;
	private double Rmin = 0.1;
	private double nstatesExpl[];
	private int curIter = 0;
	int tctIter = 0;    // The number of times we have been called this iteration
	private double curAvgNstatesExpl;





	public int getBeamWidth ()
	{
		return beamWidth;
	}

	public void setBeamWidth (int beamWidth)
	{
		this.beamWidth = beamWidth;
	}

	public int getTctIter(){
		return this.tctIter;
	}

	public void setCurIter (int curIter)
	{
		this.curIter = curIter;
		this.tctIter = 0;
	}

	public void incIter ()
	{
		this.tctIter++;
	}

	public void setKLeps (double KLeps)
	{
		this.KLeps = KLeps;
	}

	public void setRmin (double Rmin) {
		this.Rmin = Rmin;
	}

	public double[] getNstatesExpl()
	{
		return nstatesExpl;
	}

	public boolean getUseForwardBackwardBeam(){
		return this.UseForwardBackwardBeam;
	}

	public void setUseForwardBackwardBeam (boolean state) {
		this.UseForwardBackwardBeam = state;
	}






	private static Logger logger = MalletLogger.getLogger(SumLatticeBeam.class.getName());

	// "ip" == "input position", "op" == "output position", "i" == "state index"
	Transducer t;
	double weight;
	Sequence input, output;
	LatticeNode[][] nodes;			 // indexed by ip,i
	int latticeLength;
	int curBeamWidth;               // CPAL - can be adapted if maximizer is confused

	// xxx Now that we are incrementing here directly, there isn't
	// necessarily a need to save all these arrays...
	// log(probability) of being in state "i" at input position "ip"
	double[][] gammas;					 // indexed by ip,i
	double[][][] xis;            // indexed by ip,i,j; saved only if saveXis is true;

	LabelVector labelings[];			 // indexed by op, created only if "outputAlphabet" is non-null in constructor

	private LatticeNode getLatticeNode (int ip, int stateIndex)
	{
		if (nodes[ip][stateIndex] == null)
			nodes[ip][stateIndex] = new LatticeNode (ip, t.getState (stateIndex));
		return nodes[ip][stateIndex];
	}

	// You may pass null for output, meaning that the lattice
	// is not constrained to match the output
	public SumLatticeBeam (Transducer t, Sequence input, Sequence output, Transducer.Incrementor incrementor)
	{
		this (t, input, output, incrementor, false, null);
	}

	// You may pass null for output, meaning that the lattice
	// is not constrained to match the output
	public SumLatticeBeam (Transducer t, Sequence input, Sequence output, Transducer.Incrementor incrementor, boolean saveXis)
	{
		this (t, input, output, incrementor, saveXis, null);
	}

	// If outputAlphabet is non-null, this will create a LabelVector
	// for each position in the output sequence indicating the
	// probability distribution over possible outputs at that time
	// index
	public SumLatticeBeam (Transducer t, Sequence input, Sequence output, Transducer.Incrementor incrementor, boolean saveXis, LabelAlphabet outputAlphabet)
	{
		this.t = t;
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
			//System.out.println ("Forward pass initialWeight = "+initialWeight);
			if (initialWeight < Transducer.IMPOSSIBLE_WEIGHT) {
				getLatticeNode(0, i).alpha = initialWeight;
				//System.out.println ("nodes[0][i].alpha="+nodes[0][i].alpha);
				atLeastOneInitialState = true;
			}
		}
		if (atLeastOneInitialState == false)
			logger.warning ("There are no starting states!");


		// CPAL - a sorted list for our beam experiments
		NBestSlist[] slists = new NBestSlist[latticeLength];
		// CPAL - used for stats
		nstatesExpl = new double[latticeLength];
		// CPAL - used to adapt beam if optimizer is getting confused
		// tctIter++;
		if(curIter == 0) {
			curBeamWidth = numStates;
		} else if(tctIter > 1 && curIter != 0) {
			//curBeamWidth = Math.min((int)Math.round(curAvgNstatesExpl*2),numStates);
			//System.out.println ("Doubling Minimum Beam Size to: "+curBeamWidth);
			curBeamWidth = beamWidth;
		} else {
			curBeamWidth = beamWidth;
		}

		// ************************************************************
		for (int ip = 0; ip < latticeLength-1; ip++) {

			// CPAL - add this to construct the beam
			// ***************************************************

			// CPAL - sets up the sorted list
			slists[ip] = new NBestSlist(numStates);
			// CPAL - set the
			slists[ip].setKLMinE(curBeamWidth);
			slists[ip].setKLeps(KLeps);
			slists[ip].setRmin(Rmin);

			for(int i = 0 ; i< numStates ; i++){
				if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
					continue;
				//State s = t.getState(i);
				// CPAL - give the NB viterbi node the (Weight, position)
				NBForBackNode cnode = new NBForBackNode(nodes[ip][i].alpha, i);
				slists[ip].push(cnode);

			}

			// CPAL - unlike std. n-best beam we now filter the list based
			// on a KL divergence like measure
			// ***************************************************
			// use method which computes the cumulative log sum and
			// finds the point at which the sum is within KLeps
			int KLMaxPos=1;
			int RminPos=1;


			if(KLeps > 0) {
				KLMaxPos = slists[ip].getKLpos();
				nstatesExpl[ip]=(double)KLMaxPos;
			} else if(KLeps == 0) {

				if(Rmin > 0) {
					RminPos = slists[ip].getTHRpos();
				} else {
					slists[ip].setRmin(-Rmin);
					RminPos = slists[ip].getTHRposSTRAWMAN();
				}
				nstatesExpl[ip]=(double)RminPos;

			} else {
				// Trick, negative values for KLeps mean use the max of KL an Rmin
				slists[ip].setKLeps(-KLeps);
				KLMaxPos = slists[ip].getKLpos();

				//RminPos = slists[ip].getTHRpos();

				if(Rmin > 0) {
					RminPos = slists[ip].getTHRpos();
				} else {
					slists[ip].setRmin(-Rmin);
					RminPos = slists[ip].getTHRposSTRAWMAN();
				}

				if(KLMaxPos > RminPos) {
					nstatesExpl[ip]=(double)KLMaxPos;
				} else {
					nstatesExpl[ip]=(double)RminPos;
				}
			}
			//System.out.println(nstatesExpl[ip] + " ");

			// CPAL - contemplating setting values to something else
			int tmppos;
			for (int i = (int) nstatesExpl[ip]+1; i < slists[ip].size(); i++) {
				tmppos = slists[ip].getPosByIndex(i);
				nodes[ip][tmppos].alpha = Transducer.IMPOSSIBLE_WEIGHT;
				nodes[ip][tmppos] = null;   // Null is faster and seems to work the same
			}
			// - done contemplation

			//for (int i = 0; i < numStates; i++) {
			for(int jj=0 ; jj< nstatesExpl[ip]; jj++) {

				int i = slists[ip].getPosByIndex(jj);

				// CPAL - dont need this anymore
				// should be taken care of in the lists
				//if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
				// xxx if we end up doing this a lot,
				// we could save a list of the non-null ones
				//	continue;


				State s = t.getState(i);

				TransitionIterator iter = s.transitionIterator (input, ip, output, ip);
				if (logger.isLoggable (Level.FINE))
					logger.fine (" Starting Foward transition iteration from state "
							+ s.getName() + " on input " + input.get(ip).toString()
							+ " and output "
							+ (output==null ? "(null)" : output.get(ip).toString()));
				while (iter.hasNext()) {
					State destination = iter.nextState();
					if (logger.isLoggable (Level.FINE))
						logger.fine ("Forward Lattice[inputPos="+ip
								+"][source="+s.getName()
								+"][dest="+destination.getName()+"]");
					LatticeNode destinationNode = getLatticeNode (ip+1, destination.getIndex());
					destinationNode.output = iter.getOutput();
					double transitionWeight = iter.getWeight();
					if (logger.isLoggable (Level.FINE))
						logger.fine ("transitionWeight="+transitionWeight
								+" nodes["+ip+"]["+i+"].alpha="+nodes[ip][i].alpha
								+" destinationNode.alpha="+destinationNode.alpha);
					destinationNode.alpha = Transducer.sumLogProb (destinationNode.alpha,
							nodes[ip][i].alpha + transitionWeight);
					//System.out.println ("destinationNode.alpha <- "+destinationNode.alpha);
				}
			}
		}

		//System.out.println("Mean Nodes Explored: " + MatrixOps.mean(nstatesExpl));
		curAvgNstatesExpl = MatrixOps.mean(nstatesExpl);

		// Calculate total cost of Lattice.  This is the normalizer
		weight = Transducer.IMPOSSIBLE_WEIGHT;
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				// Note: actually we could sum at any ip index,
				// the choice of latticeLength-1 is arbitrary
				//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
				//System.out.println ("Ending beta,  state["+i+"] = "+t.getState(i).finalWeight);
				weight = Transducer.sumLogProb (weight,
						(nodes[latticeLength-1][i].alpha + t.getState(i).getFinalWeight()));
			}
		// Weight is now an "unnormalized weight" of the entire Lattice
		//assert (weight >= 0) : "weight = "+weight;

		// If the sequence has -infinite weight, just return.
		// Usefully this avoids calling any incrementX methods.
		// It also relies on the fact that the gammas[][] and .alpha and .beta values
		// are already initialized to values that reflect -infinite weight
		// xxx Although perhaps not all (alphas,betas) exactly correctly reflecting?
		if (weight == Transducer.IMPOSSIBLE_WEIGHT)
			return;

		// Backward pass
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				State s = t.getState(i);
				nodes[latticeLength-1][i].beta = s.getFinalWeight();
				gammas[latticeLength-1][i] =
					nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - weight;
				if (incrementor != null) {
					double p = Math.exp(gammas[latticeLength-1][i]);
					assert (p > Transducer.IMPOSSIBLE_WEIGHT && !Double.isNaN(p))
					: "p="+p+" gamma="+gammas[latticeLength-1][i];
					incrementor.incrementFinalState(s, p);
				}
			}

		for (int ip = latticeLength-2; ip >= 0; ip--) {
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
					// Note that skipping here based on alpha means that beta values won't
					// be correct, but since alpha is infinite anyway, it shouldn't matter.
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
						double xi = nodes[ip][i].alpha + transitionWeight + nodes[ip+1][j].beta - weight;
						if (saveXis) xis[ip][i][j] = xi;
						assert (!Double.isNaN(nodes[ip][i].alpha));
						assert (!Double.isNaN(transitionWeight));
						assert (!Double.isNaN(nodes[ip+1][j].beta));
						assert (!Double.isNaN(weight));
						if (incrementor != null || outputAlphabet != null) {
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
				gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta - weight;
			}

			if(true){
				// CPAL - check the normalization
				double checknorm = Transducer.IMPOSSIBLE_WEIGHT;
				for (int i = 0; i < numStates; i++)
					if (nodes[ip][i] != null) {
						// Note: actually we could sum at any ip index,
						// the choice of latticeLength-1 is arbitrary
						//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
						//System.out.println ("Ending beta,  state["+i+"] = "+t.getState(i).finalWeight);
						checknorm = Transducer.sumLogProb (checknorm, gammas[ip][i]);
					}
				// System.out.println ("Check Gamma, sum="+checknorm);
				// CPAL - done check of normalization

				// CPAL - normalize
				for (int i = 0; i < numStates; i++)
					if (nodes[ip][i] != null) {
						gammas[ip][i] = gammas[ip][i] - checknorm;
					}
				//System.out.println ("Check Gamma, sum="+checknorm);
				// CPAL - normalization
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

	// CPAL - a simple node holding a weight and position of the state
	private class NBForBackNode
	{
		double weight;
		int pos;
		NBForBackNode(double weight, int pos)
		{
			this.weight = weight;
			this.pos = pos;
		}
	}

	private class NBestSlist
	{
		ArrayList list = new ArrayList();
		int MaxElements;
		int KLMinElements;
		int KLMaxPos;
		double KLeps;
		double Rmin;

		NBestSlist(int MaxElements)
		{
			this.MaxElements = MaxElements;
		}

		boolean setKLMinE(int KLMinElements){
			this.KLMinElements = KLMinElements;
			return true;
		}

		int size()
		{
			return list.size();
		}

		boolean empty()
		{
			return list.isEmpty();
		}

		Object pop()
		{
			return list.remove(0);
		}

		int getPosByIndex(int ii){
			NBForBackNode tn = (NBForBackNode)list.get(ii);
			return tn.pos;
		}

		double getWeightByIndex(int ii){
			NBForBackNode tn = (NBForBackNode)list.get(ii);
			return tn.weight;
		}

		void setKLeps(double KLeps){
			this.KLeps = KLeps;
		}

		void setRmin(double Rmin){
			this.Rmin = Rmin;
		}

		int getTHRpos(){

			NBForBackNode tn;
			double lc1, lc2;


			tn = (NBForBackNode)list.get(0);
			lc1 = tn.weight;
			tn = (NBForBackNode)list.get(list.size()-1);
			lc2 = tn.weight;

			double minc = lc1 - lc2;
			double mincTHR = minc - minc*Rmin;

			for(int i=1;i<list.size();i++){
				tn = (NBForBackNode)list.get(i);
				lc1 = tn.weight - lc2;
				if(lc1 > mincTHR){
					return i+1;
				}

			}

			return list.size();

		}

		int getTHRposSTRAWMAN(){

			NBForBackNode tn;
			double lc1, lc2;


			tn = (NBForBackNode)list.get(0);
			lc1 = tn.weight;

			double mincTHR = -lc1*Rmin;

			//double minc = lc1 - lc2;
			//double mincTHR = minc - minc*Rmin;

			for(int i=1;i<list.size();i++){
				tn = (NBForBackNode)list.get(i);
				lc1 = -tn.weight;
				if(lc1 < mincTHR){
					return i+1;
				}

			}

			return list.size();

		}

		int getKLpos(){

			//double KLeps = 0.1;
			double CSNLP[];
			CSNLP = new double[MaxElements];
			double worstc;
			NBForBackNode tn;

			tn = (NBForBackNode)list.get(list.size()-1);
			worstc = tn.weight;

			for(int i=0;i<list.size();i++){
				tn = (NBForBackNode)list.get(i);
				// NOTE: sometimes we can have positive numbers !
				double lc = tn.weight;
				//double lc = tn.weight-worstc;

				//if(lc >0){
				//    int asdf=1;
				//}

				if (i==0) {
					CSNLP[i] = lc;
				} else {
					CSNLP[i] = Transducer.sumLogProb(CSNLP[i-1], lc);
				}
			}

			// normalize
			for(int i=0;i<list.size();i++){
				CSNLP[i]=CSNLP[i]-CSNLP[list.size()-1];
				if(CSNLP[i] < KLeps){
					KLMaxPos = i+1;
					if(KLMaxPos >= KLMinElements) {
						return KLMaxPos;
					} else if(list.size() >= KLMinElements){
						return KLMinElements;
					}
				}
			}

			KLMaxPos = list.size();
			return KLMaxPos;
		}

		ArrayList push(NBForBackNode vn)
		{
			double tc = vn.weight;
			boolean atEnd = true;

			for(int i=0;i<list.size();i++){
				NBForBackNode tn = (NBForBackNode)list.get(i);
				double lc = tn.weight;
				if(tc < lc){
					list.add(i,vn);
					atEnd = false;
					break;
				}
			}

			if(atEnd) {
				list.add(vn);
			}

			// CPAL - if the list is too big,
			// remove the first, largest weight element
			if(list.size()>MaxElements) {
				list.remove(MaxElements);
			}

			//double f = o.totalWeight[o.nextBestStateIndex];
			//boolean atEnd = true;
			//for(int i=0; i<list.size(); i++){
			//	ASearchNode_NBest tempNode = (ASearchNode_NBest)list.get(i);
			//	double f1 = tempNode.totalWeight[tempNode.nextBestStateIndex];
			//	if(f < f1) {
			//		list.add(i, o);
			//		atEnd = false;
			//		break;
			//	}
			//}

			//if(atEnd) list.add(o);

			return list;
		}
	} // CPAL - end NBestSlist

//	culotta: interface for constrained lattice
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
	 */
	SumLatticeBeam (Transducer t, Sequence inputSequence, Sequence outputSequence, Segment requiredSegment, Sequence constrainedSequence) 
	{
		this (t, inputSequence, outputSequence, (Transducer.Incrementor)null, null, 
				makeConstraints(t, inputSequence, outputSequence, requiredSegment, constrainedSequence));
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
	public SumLatticeBeam (Transducer t, Sequence input, Sequence output, Transducer.Incrementor incrementor, LabelAlphabet outputAlphabet, int [] constraints)
	{
		this.t = t;
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
					xis[ip][i][j] = Transducer.IMPOSSIBLE_WEIGHT;
			 */
		}

		// Forward pass
		logger.fine ("Starting Constrained Foward pass");

		// ensure that at least one state has initial weight less than Infinity
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
					else if (nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT) logger.fine ("nodes[ip][i].alpha is Inf");
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
						// unnormalized weights...set to Inf //
						// destinationNode.alpha = 0.0;
//						destinationNode.alpha = Transducer.IMPOSSIBLE_WEIGHT;
						logger.fine ("Illegal transition from state " + i + " to state " + destination.getIndex() + ". Setting alpha to Inf");
					}
				}
			}

		// Calculate total weight of Lattice.  This is the normalizer
		weight = Transducer.IMPOSSIBLE_WEIGHT;
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				// Note: actually we could sum at any ip index,
				// the choice of latticeLength-1 is arbitrary
				//System.out.println ("Ending alpha, state["+i+"] = "+nodes[latticeLength-1][i].alpha);
				//System.out.println ("Ending beta,  state["+i+"] = "+t.getState(i).finalWeight);
				if (constraints[latticeLength-1] > 0 && i != constraints[latticeLength-1]-1)
					continue;
				if (constraints[latticeLength-1] < 0 && -i == constraints[latticeLength-1]+1)
					continue;
				logger.fine ("Summing final lattice weight. state="+i+", alpha="+nodes[latticeLength-1][i].alpha + ", final weight = "+t.getState(i).getFinalWeight());
				weight = Transducer.sumLogProb (weight,
						(nodes[latticeLength-1][i].alpha + t.getState(i).getFinalWeight()));
			}
		// Weight is now an "unnormalized weight" of the entire Lattice
		//assert (weight >= 0) : "weight = "+weight;

		// If the sequence has -infinite weight, just return.
		// Usefully this avoids calling any incrementX methods.
		// It also relies on the fact that the gammas[][] and .alpha and .beta values
		// are already initialized to values that reflect -infinite weight
		// xxx Although perhaps not all (alphas,betas) exactly correctly reflecting?
		if (weight == Transducer.IMPOSSIBLE_WEIGHT)
			return;

		// Backward pass
		for (int i = 0; i < numStates; i++)
			if (nodes[latticeLength-1][i] != null) {
				State s = t.getState(i);
				nodes[latticeLength-1][i].beta = s.getFinalWeight();
				gammas[latticeLength-1][i] =
					nodes[latticeLength-1][i].alpha + nodes[latticeLength-1][i].beta - weight;
				if (incrementor != null) {
					double p = Math.exp(gammas[latticeLength-1][i]);
					assert (p >= 0 && p <= 1.0 && !Double.isNaN(p)) : "p="+p+" gamma="+gammas[latticeLength-1][i];
					incrementor.incrementFinalState(s, p);
				}
			}
		for (int ip = latticeLength-2; ip >= 0; ip--) {
			for (int i = 0; i < numStates; i++) {
				if (nodes[ip][i] == null || nodes[ip][i].alpha == Transducer.IMPOSSIBLE_WEIGHT)
					// Note that skipping here based on alpha means that beta values won't
					// be correct, but since alpha is infinite anyway, it shouldn't matter.
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
						assert (!Double.isNaN(weight));
						if (incrementor != null || outputAlphabet != null) {
							double xi = nodes[ip][i].alpha + transitionWeight + nodes[ip+1][j].beta - weight;
							double p = Math.exp(xi);
							assert (p >= 0 && p <= 1.0 && !Double.isNaN(p)) : "xis["+ip+"]["+i+"]["+j+"]="+-xi;
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
				gammas[ip][i] = nodes[ip][i].alpha + nodes[ip][i].beta - weight;
			}
		}
		if (incrementor != null)
			for (int i = 0; i < numStates; i++) {
				double p = Math.exp(gammas[0][i]);
				assert (p >= 0.0 && p <= 1.0 && !Double.isNaN(p));
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

	public double getTotalWeight () {
		assert (!Double.isNaN(weight));
		return weight; }

	// No, this.weight is an "unnormalized weight"
	//public double getProbability () { return Math.exp (weight); }

	public double getGammaWeight (int inputPosition, State s) {
		return gammas[inputPosition][s.getIndex()]; }

	public double getGammaProbability (int inputPosition, State s) {
		return Math.exp (gammas[inputPosition][s.getIndex()]); }
	
	public double[][][] getXis() {
		return xis;
	}

	public double[][] getGammas () {
		return gammas;
	}
	

	public double getXiProbability (int ip, State s1, State s2) {
		if (xis == null)
			throw new IllegalStateException ("xis were not saved.");

		int i = s1.getIndex ();
		int j = s2.getIndex ();
		return Math.exp (xis[ip][i][j]);
	}

	public double getXiWeight (int ip, State s1, State s2)
	{
		if (xis == null)
			throw new IllegalStateException ("xis were not saved.");

		int i = s1.getIndex ();
		int j = s2.getIndex ();
		return xis[ip][i][j];
	}

	public int length () { return latticeLength; }

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
	private class LatticeNode
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
	
	public static class Factory extends SumLatticeFactory
	{
		int bw;
		public Factory (int beamWidth) {
			bw = beamWidth;
		}
		public SumLattice newSumLattice (Transducer trans, Sequence input, Sequence output, 
				Transducer.Incrementor incrementor, boolean saveXis, LabelAlphabet outputAlphabet)
		{
			return new SumLatticeBeam (trans, input, output, incrementor, saveXis, outputAlphabet) {{ beamWidth = bw; }};
		}


	}

}	
