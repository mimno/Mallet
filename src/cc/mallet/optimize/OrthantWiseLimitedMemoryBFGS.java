package cc.mallet.optimize;

import java.util.LinkedList;
import java.util.logging.Logger;

import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletLogger;

/**
 * Implementation of orthant-wise limited memory quasi Newton method for
 * optimizing convex L1-regularized objectives. See:
 * "Scalable training of l1-regularized log-linear models" by Galen Andrew and
 * Jianfeng Gao in ICML 2007 for details. This code is an adaptation of the
 * freely-available C++ code on Galen's webpage.
 * 
 * @author Kedar Bellare
 */
public class OrthantWiseLimitedMemoryBFGS implements Optimizer {
	private static Logger logger = MalletLogger
			.getLogger(OrthantWiseLimitedMemoryBFGS.class.getName());

	boolean converged = false;
	Optimizable.ByGradientValue optimizable;
	// name of optimizable for value output
	String optName;
	final int maxIterations = 1000;
	final double tolerance = .0001;
	final double gradientTolerance = .001;
	final double eps = 1.0e-5;
	double l1Weight;

	// The number of corrections used in BFGS update
	// ideally 3 <= m <= 7. Larger m means more cpu time, memory.
	final int m = 4;

	// State of optimizer search
	// oldValue = value before line search, value = value after line search
	double oldValue, value, yDotY;
	// grad = gradient
	double[] grad, oldGrad, direction, steepestDescentDirection, parameters,
			oldParameters;
	// s = list of m previous "difference in parameters" values
	// y = list of m previous "difference in grad" values
	LinkedList<double[]> s, y;
	// rho = intermediate calculation
	LinkedList<Double> rhos;
	double[] alphas;
	int iterations;

	public OrthantWiseLimitedMemoryBFGS(Optimizable.ByGradientValue function) {
		this(function, 0.0);
	}

	public OrthantWiseLimitedMemoryBFGS(Optimizable.ByGradientValue function,
			double l1wt) {
		this.optimizable = function;
		this.l1Weight = l1wt;
		String parts[] = optimizable.getClass().getName().split("\\.");
		this.optName = parts[parts.length - 1];

		// initialize optimizer state
		iterations = 0;
		s = new LinkedList<double[]>();
		y = new LinkedList<double[]>();
		rhos = new LinkedList<Double>();
		alphas = new double[m];
		MatrixOps.setAll(alphas, 0.0);
		yDotY = 0;

		int numParameters = optimizable.getNumParameters();

		// get initial parameters
		parameters = new double[numParameters];
		optimizable.getParameters(parameters);

		// get initial value
		value = evalL1();

		// get initial gradient
		grad = new double[numParameters];
		evalGradient();

		// initialize direction
		direction = new double[numParameters];
		steepestDescentDirection = new double[numParameters];

		// initialize backups
		oldParameters = new double[numParameters];
		oldGrad = new double[numParameters];
	}

	public Optimizable getOptimizable() {
		return optimizable;
	}

	public boolean isConverged() {
		return converged;
	}

	public int getIteration() {
		return iterations;
	}

	public boolean optimize() {
		return optimize(Integer.MAX_VALUE);
	}

	public boolean optimize(int numIterations) {
		logger.fine("Entering OWL-BFGS.optimize(). L1 weight=" + l1Weight
				+ " Initial Value=" + value);

		for (int iter = 0; iter < numIterations; iter++) {
			// create descent direction
			makeSteepestDescDir();

			// adjust for curvature
			mapDirByInverseHessian(yDotY);

			// fix direction signs
			fixDirSigns();

			// backup parameters and gradient; then perform line-search
			storeSrcInDest(parameters, oldParameters);
			storeSrcInDest(grad, oldGrad);
			backTrackingLineSearch();

			// update gradient after line search
			evalGradient();

			// check for termination conditions
			if (checkValueTerminationCondition()) {
				logger.info("Exiting OWL-BFGS on termination #1:");
				logger.info("value difference below tolerance (oldValue: "
						+ oldValue + " newValue: " + value);
				converged = true;
				return true;
			}

			if (checkGradientTerminationCondition()) {
				logger.info("Exiting OWL-BFGS on termination #2:");
				logger.info("gradient=" + MatrixOps.twoNorm(grad) + " < "
						+ gradientTolerance);
				converged = true;
				return true;
			}

			// update hessian approximation
			yDotY = shift();

			iterations++;
			if (iterations > maxIterations) {
				logger.info("Too many iterations in OWL-BFGS. "
						+ "Continuing with current parameters.");
				converged = true;
				return true;
			}
		}

		return false;
	}

	/**
	 * Evaluate value. Make it a minimization problem.
	 */
	private double evalL1() {
		double val = -optimizable.getValue();
		double sumAbsWt = 0;
		if (l1Weight > 0) {
			for (double param : parameters) {
				if (Double.isInfinite(param))
					continue;
				sumAbsWt += Math.abs(param) * l1Weight;
			}
		}
		logger.info("getValue() (" + optName + ".getValue() = " + val
				+ " + |w|=" + sumAbsWt + ") = " + (val + sumAbsWt));

		return val + sumAbsWt;
	}

	/**
	 * Evaluate gradient, make it a descent direction.
	 */
	private void evalGradient() {
		optimizable.getValueGradient(grad);
		adjustGradForInfiniteParams(grad);
		MatrixOps.timesEquals(grad, -1.0);
	}

	/**
	 * Creates steepest ascent direction from gradient and L1-regularization.
	 */
	private void makeSteepestDescDir() {
		if (l1Weight == 0) {
			for (int i = 0; i < grad.length; i++) {
				direction[i] = -grad[i];
			}
		} else {
			for (int i = 0; i < grad.length; i++) {
				if (parameters[i] < 0) {
					direction[i] = -grad[i] + l1Weight;
				} else if (parameters[i] > 0) {
					direction[i] = -grad[i] - l1Weight;
				} else {
					if (grad[i] < -l1Weight) {
						direction[i] = -grad[i] - l1Weight;
					} else if (grad[i] > l1Weight) {
						direction[i] = -grad[i] + l1Weight;
					} else {
						direction[i] = 0;
					}
				}
			}
		}

		storeSrcInDest(direction, steepestDescentDirection);
	}

	private void adjustGradForInfiniteParams(double d[]) {
		for (int i = 0; i < parameters.length; i++) {
			if (Double.isInfinite(parameters[i]))
				d[i] = 0;
		}
	}

	/**
	 * Adjusts direction based on approximate hessian inverse.
	 * 
	 * @param yDotY
	 *            y^T * y in BFGS calculation.
	 */
	private void mapDirByInverseHessian(double yDotY) {
		if (s.size() == 0)
			return;

		int count = s.size();
		for (int i = count - 1; i >= 0; i--) {
			alphas[i] = -MatrixOps.dotProduct(s.get(i), direction)
					/ rhos.get(i);
			MatrixOps.plusEquals(direction, y.get(i), alphas[i]);
		}

		double scalar = rhos.get(count - 1) / yDotY;
		logger.fine("Direction multiplier = " + scalar);
		MatrixOps.timesEquals(direction, scalar);

		for (int i = 0; i < count; i++) {
			double beta = MatrixOps.dotProduct(y.get(i), direction)
					/ rhos.get(i);
			MatrixOps.plusEquals(direction, s.get(i), -alphas[i] - beta);
		}
	}

	private void fixDirSigns() {
		if (l1Weight > 0) {
			for (int i = 0; i < direction.length; i++) {
				if (direction[i] * steepestDescentDirection[i] <= 0) {
					direction[i] = 0;
				}
			}
		}
	}

	private double dirDeriv() {
		if (l1Weight == 0) {
			return MatrixOps.dotProduct(direction, grad);
		} else {
			double val = 0.0;
			for (int i = 0; i < direction.length; i++) {
				if (direction[i] != 0) {
					if (parameters[i] < 0) {
						val += direction[i] * (grad[i] - l1Weight);
					} else if (parameters[i] > 0) {
						val += direction[i] * (grad[i] + l1Weight);
					} else if (direction[i] < 0) {
						val += direction[i] * (grad[i] - l1Weight);
					} else if (direction[i] > 0) {
						val += direction[i] * (grad[i] + l1Weight);
					}
				}
			}

			return val;
		}
	}

	private double shift() {
		double[] nextS = null, nextY = null;

		int listSize = s.size();
		if (listSize < m) {
			nextS = new double[parameters.length];
			nextY = new double[parameters.length];
		} else {
			nextS = s.removeFirst();
			nextY = y.removeFirst();
			rhos.removeFirst();
		}

		double rho = 0.0;
		double yDotY = 0.0;
		for (int i = 0; i < parameters.length; i++) {
			if (Double.isInfinite(parameters[i])
					&& Double.isInfinite(oldParameters[i])
					&& parameters[i] * oldParameters[i] > 0)
				nextS[i] = 0;
			else
				nextS[i] = parameters[i] - oldParameters[i];

			if (Double.isInfinite(grad[i]) && Double.isInfinite(oldGrad[i])
					&& grad[i] * oldGrad[i] > 0)
				nextY[i] = 0;
			else
				nextY[i] = grad[i] - oldGrad[i];

			rho += nextS[i] * nextY[i];
			yDotY += nextY[i] * nextY[i];
		}

		logger.fine("rho=" + rho);
		if (rho < 0) {
			throw new InvalidOptimizableException("rho = " + rho + " < 0: "
					+ "Invalid hessian inverse. "
					+ "Gradient change should be opposite of parameter change.");
		}

		s.addLast(nextS);
		y.addLast(nextY);
		rhos.addLast(rho);

		// update old params and grad
		storeSrcInDest(parameters, oldParameters);
		storeSrcInDest(grad, oldGrad);

		return yDotY;
	}

	private void storeSrcInDest(double src[], double dest[]) {
		System.arraycopy(src, 0, dest, 0, src.length);
	}

	// backtrack line search
	private void backTrackingLineSearch() {
		double origDirDeriv = dirDeriv();
		if (origDirDeriv >= 0) {
			throw new InvalidOptimizableException(
					"L-BFGS chose a non-ascent direction: check your gradient!");
		}

		double alpha = 1.0;
		double backoff = 0.5;
		if (iterations == 0) {
			double normDir = Math.sqrt(MatrixOps.dotProduct(direction,
					direction));
			alpha = 1.0 / normDir;
			backoff = 0.1;
		}

		final double c1 = 1e-4;
		// store old value
		oldValue = value;

		logger.fine("*** Starting line search iter=" + iterations);
		logger.fine("iter[" + iterations + "] Value at start of line search = "
				+ value);

		while (true) {
			// update parameters and gradient
			getNextPoint(alpha);

			// find new value
			value = evalL1();

			logger.fine("iter[" + iterations + "] Using alpha = " + alpha
					+ " new value = " + value + " |grad|="
					+ MatrixOps.twoNorm(grad) + " |x|="
					+ MatrixOps.twoNorm(parameters));

			if (value <= oldValue + c1 * origDirDeriv * alpha)
				break;

			alpha *= backoff;
		}
	}

	private void getNextPoint(double alpha) {
		for (int i = 0; i < parameters.length; i++) {
			parameters[i] = oldParameters[i] + direction[i] * alpha;
			if (l1Weight > 0) {
				// do not allow to cross orthant boundaries if using
				// L1-regularization
				if (oldParameters[i] * parameters[i] < 0) {
					parameters[i] = 0.0;
				}
			}
		}

		optimizable.setParameters(parameters);
	}

	// termination conditions
	private boolean checkValueTerminationCondition() {
		return (2.0 * Math.abs(value - oldValue) <= tolerance
				* (Math.abs(value) + Math.abs(oldValue) + eps));
	}

	private boolean checkGradientTerminationCondition() {
		return MatrixOps.twoNorm(grad) < gradientTolerance;
	}
}
