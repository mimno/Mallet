package cc.mallet.fst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;

import cc.mallet.optimize.Optimizable;

import cc.mallet.util.MalletLogger;


/**
 * An adaptor for optimizables based on batch values/gradients.
 * <p>
 * Computes values, gradients for each batch in multiple threads and combines
 * them in the end.
 *
 * @author Gaurav Chandalia
 * @see CRFOptimizableByBatchLabelLikelihood
 */
public class ThreadedOptimizable implements Optimizable.ByGradientValue {
	private static Logger logger = MalletLogger.getLogger(ThreadedOptimizable.class.getName());

	/** Data */
	protected InstanceList trainingSet;

	/** optimizable to be parallelized */
	protected Optimizable.ByCombiningBatchGradient optimizable;

	/** Value obtained from the optimizable for each batch */
	protected double[] batchCachedValue;
  /** Gradient obtained from the optimizable for each batch */
	protected List<double[]> batchCachedGradient;

	// determine when value/gradient become stale
	protected CacheStaleIndicator cacheIndicator;

	// tasks to be executed in individual threads, each task is instantiated only
	// once but executed in every iteration
	private transient Collection<Callable<Double>> valueTasks;
	private transient Collection<Callable<Boolean>> gradientTasks;

	// thread pool to compute value/gradient for one batch of data
	private transient ThreadPoolExecutor executor;

	// milliseconds
	public static final int SLEEP_TIME = 100;

	/**
	 * Initializes the optimizable and starts new threads.
	 *
	 * @param optimizable Optimizable to be parallelized
	 * @param numFactors Number of factors in model's parameters, used to
	 *        initialize the gradient
	 * @param cacheIndicator Determines when value/gradient become stale
	 */
	public ThreadedOptimizable(Optimizable.ByCombiningBatchGradient optimizable,
			InstanceList trainingSet, int numFactors,
			CacheStaleIndicator cacheIndicator) {
		// set up
		this.trainingSet = trainingSet;
		this.optimizable = optimizable;

		int numBatches = optimizable.getNumBatches();
		assert(numBatches > 0) : "Invalid number of batches: " + numBatches;
		batchCachedValue = new double[numBatches];
		batchCachedGradient = new ArrayList<double[]>(numBatches);
		for (int i = 0; i < numBatches; ++i) {
			batchCachedGradient.add(new double[numFactors]);
		}

		this.cacheIndicator = cacheIndicator;

		logger.info("Creating " + numBatches + " threads for updating gradient...");
		executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numBatches);
		this.createTasks();
	}

	public Optimizable.ByCombiningBatchGradient getOptimizable() {
		return optimizable;
	}

	/**
	 * Shuts down the executor used to start and run threads to compute values
	 * and gradients.
	 * <p>
	 * *Note*: For a clean exit of all the threads, it is recommended to call
	 * this method after training finishes.
	 */
	public void shutdown() {
    assert(executor.shutdownNow().size() == 0) : "All tasks didn't finish";
	}

	public double getValue () {
		if (cacheIndicator.isValueStale()) {
			// compute values again
			try {
				// run all threads and wait for them to finish
				List<Future<Double>> results = executor.invokeAll(valueTasks);

				// compute final log probability
				int batch = 0;
				for (Future<Double> f : results) {
					try {
						batchCachedValue[batch++] = f.get();
					} catch (ExecutionException ee) {
						ee.printStackTrace();
					}
				}
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
			double cachedValue = MatrixOps.sum(batchCachedValue);
			logger.info("getValue() (loglikelihood, optimizable by label likelihood) =" + cachedValue);
			return cachedValue;
		}
		return MatrixOps.sum(batchCachedValue);
	}

	/**
	 * Returns the gradient, re-computes if gradient is stale. <p>
	 *
	 * *Note*: Assumes that <tt>buffer</tt> is already initialized.
	 */
	public void getValueGradient (double[] buffer) {
		if (cacheIndicator.isGradientStale()) {
			// compute values again if required
			this.getValue();

			// compute gradients again
			try {
				// run all threads and wait for them to finish
				executor.invokeAll(gradientTasks);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
		optimizable.combineGradients(batchCachedGradient, buffer);
	}

	/**
	 * Creates tasks to be executed in parallel, each task looks at a batch of
	 * data.
	 */
	protected void createTasks() {
		int numBatches = optimizable.getNumBatches();
		valueTasks = new ArrayList<Callable<Double>>(numBatches);
		gradientTasks = new ArrayList<Callable<Boolean>>(numBatches);
		// number of instances per batch
		int numBatchInstances = trainingSet.size() / numBatches;
		// batch assignments
		int start = -1, end = -1;
		for (int i = 0; i < numBatches; ++i) {
			// get the indices of batch
			if (i == 0) {
				start = 0;
				end = start + numBatchInstances;
			} else if (i == numBatches-1) {
				start = end;
				end = trainingSet.size();
			} else {
				start = end;
				end = start + numBatchInstances;
			}
			valueTasks.add(new ValueHandler(i, new int[]{start, end}));
			gradientTasks.add(new GradientHandler(i, new int[]{start, end}));
		}
	}

	public int getNumParameters () { return optimizable.getNumParameters(); }

	public void getParameters (double[] buffer) {
		optimizable.getParameters(buffer);
	}

	public double getParameter (int index) {
		return optimizable.getParameter(index);
	}

	public void setParameters (double [] buff) {
		optimizable.setParameters(buff);
	}

	public void setParameter (int index, double value) {
		optimizable.setParameter(index, value);
	}

	/**
	 * Computes value in a separate thread for a batch of data.
	 */
	private class ValueHandler implements Callable<Double> {
		private int batchIndex;
		private int[] batchAssignments;

		public ValueHandler(int batchIndex, int[] batchAssignments) {
			this.batchIndex = batchIndex;
			this.batchAssignments = batchAssignments;
		}

		/**
		 * Returns the value for a batch.
		 */
		public Double call() {
			return optimizable.getBatchValue(batchIndex, batchAssignments);
		}
	}

	/**
	 * Computes gradient in a separate thread for a batch of data.
	 */
	private class GradientHandler implements Callable<Boolean> {
		private int batchIndex;
		private int[] batchAssignments;

		public GradientHandler(int batchIndex, int[] batchAssignments) {
			this.batchIndex = batchIndex;
			this.batchAssignments = batchAssignments;
		}

		/**
		 * Computes the gradient for a batch, always returns true.
		 */
		public Boolean call() {
			optimizable.getBatchValueGradient(batchCachedGradient.get(batchIndex),
					batchIndex, batchAssignments);
			return true;
		}
	}
}
