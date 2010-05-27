package cc.mallet.fst.semi_supervised;

import java.util.HashMap;
import java.util.logging.Logger;

import cc.mallet.fst.CRF;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

/**
 * Trains a CRF using Generalized Expectation constraints that
 * considers a single label of a linear chain CRF.
 * 
 * See:
 * "Generalized Expectation Criteria for Semi-Supervised Learning of Conditional Random Fields"
 * Gideon Mann and Andrew McCallum
 * ACL 2008
 *
 * @author Gregory Druck
 */
public class CRFTrainerByGE extends TransducerTrainer {

	private static Logger logger = MalletLogger.getLogger(CRFTrainerByGE.class.getName());
	
	private static final int DEFAULT_NUM_RESETS = 1;
	private static final int DEFAULT_GPV = 10;
	
	private boolean converged;
	private int iteration;
	private int numThreads;
	private double gaussianPriorVariance;
	private HashMap<Integer,GECriterion> constraints;
	private CRF crf;
	private StateLabelMap stateLabelMap;
	
	public CRFTrainerByGE(CRF crf, HashMap<Integer,GECriterion> constraints) {
		this(crf,constraints,1);
	}
	
	public CRFTrainerByGE(CRF crf, HashMap<Integer,GECriterion> constraints, int numThreads) {
		this.converged = false;
		this.iteration = 0;
		this.constraints = constraints;
		this.crf = crf;
		this.numThreads = numThreads;
		this.gaussianPriorVariance = DEFAULT_GPV;
		// default one to one state label map
		// other maps can be set with setStateLabelMap
		this.stateLabelMap = new StateLabelMap(crf.getOutputAlphabet(),true);
	}
	
	@Override
	public int getIteration() {
		return iteration;
	}

	@Override
	public Transducer getTransducer() {
		return crf;
	}

	@Override
	public boolean isFinishedTraining() {
		return converged;
	}
	
	public void setGaussianPriorVariance(double gpv) {
		this.gaussianPriorVariance = gpv;
	}

	// map between states in CRF FST and labels
	public void setStateLabelMap(StateLabelMap map) {
		this.stateLabelMap = map;
	}
	
	@Override
	public boolean train(InstanceList unlabeledSet, int numIterations) {
    
    assert(constraints.size() > 0);
    if (constraints.size() == 0) {
    	throw new RuntimeException("No constraints specified!");
    }

    // TODO implement initialization
    //initMaxEnt(crf);
    
    // Check what type of constraints we have.
    // XXX Could instead implement separate trainers...
    boolean kl = false;
    boolean l2 = false;
    for (GECriterion constraint : constraints.values()) {
    	if (constraint instanceof GEL2Criterion) {
    		l2 = true;
    	}
    	else if (constraint instanceof GEKLCriterion) {
    		kl = true;
    	}
    	else {
    		throw new RuntimeException("Only KL and L2 constraints are supported " +
    	    "by this trainer. Constraint type is " + constraint.getClass());
    	}
    }
    if (kl && l2) {
  		throw new RuntimeException("Currently constraints must be either all KL " + 
  				"or all L2.");
    }
    
    GECriteria criteria; 
    if (kl) {
    	System.err.println("kl");
    	criteria = new GEKLCriteria(crf.numStates(), stateLabelMap, constraints);
    }
    else {
    	System.err.println("l2");
    	criteria = new GEL2Criteria(crf.numStates(), stateLabelMap, constraints);
    }
    

    
    CRFOptimizableByGECriteria ge = 
    	new CRFOptimizableByGECriteria(criteria, crf, unlabeledSet, numThreads);
    ge.setGaussianPriorVariance(gaussianPriorVariance);
		
		LimitedMemoryBFGS bfgs = new LimitedMemoryBFGS(ge);
		
		converged = false;
		logger.info ("CRF about to train with "+numIterations+" iterations");
		// sometimes resetting the optimizer helps to find
		// a better parameter setting
		for (int reset = 0; reset < DEFAULT_NUM_RESETS + 1; reset++) {
			for (int i = 0; i < numIterations; i++) {
				try {
					converged = bfgs.optimize (1);
					iteration++;
					logger.info ("CRF finished one iteration of maximizer, i="+i);
					runEvaluators();
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
					logger.info ("Catching exception; saying converged.");
					converged = true;
				} catch (Exception e) {
					e.printStackTrace();
					logger.info("Catching exception; saying converged.");
					converged = true;
				}
				if (converged) {
					logger.info ("CRF training has converged, i="+i);
					break;
				}
			}
			bfgs.reset();
		}
		
		ge.shutdown();
		
		return converged;
	}
}
