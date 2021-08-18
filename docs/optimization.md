# Optimization

MALLET includes methods for numerically optimizing functions. The primary use for numerical optimization in machine learning is to find parameters that maximize a log-likelihood function given observed data. The code, however, is very general and can be used for arbitrary problems.

The `cc.mallet.optimize` package centers around two interfaces, `Optimizable` and `Optimizer`. `Optimizable` has several sub-interfaces that define different classes of methods for optimization. `Optimizable` classes are stateful; they must store the current values of all parameters. The most commonly used optimizable sub-interface is `Optimizable.ByGradientValue`. Classes that implement this interface must provide methods for calculating the value of a specific function at the current parameter settings and for calculating the gradient of the function, with respect to the current parameter values. Note that this interface does not require the Hessian matrix of second derivatives, so the interface is appropriate for numerical methods that approximate the Hessian.

The following is an example of a class implementing `Optimizable.ByGradientValue`. This class implements a very simple quadratic function in two variables.

    public class OptimizerExample implements Optimizable.ByGradientValue {

        // Optimizables encapsulate all state variables, 
        //  so a single Optimizer object can be used to optimize 
        //  several functions.

        double[] parameters;

        public OptimizerExample(double x, double y) {
            parameters = new double[2];
            parameters[0] = x;
            parameters[1] = y;
        }

        public double getValue() {

            double x = parameters[0];
            double y = parameters[1];

            return -3*x*x - 4*y*y + 2*x - 4*y + 18;

        }

        public void getValueGradient(double[] gradient) {

            gradient[0] = -6 * parameters[0] + 2;
            gradient[1] = -8 * parameters[1] - 4;

        }

        // The following get/set methods satisfy the Optimizable interface

        public int getNumParameters() { return 2; }
        public double getParameter(int i) { return parameters[i]; }
        public void getParameters(double[] buffer) {
            buffer[0] = parameters[0];
            buffer[1] = parameters[1];
        }

        public void setParameter(int i, double r) {
            parameters[i] = r;
        }
        public void setParameters(double[] newParameters) {
            parameters[0] = newParameters[0];
            parameters[1] = newParameters[1];
        }
    }

Now that we have a class representing an optimizable function, we can pass it to an optimizer that takes this sub-interface. The `ByGradientValue` sub-interface is compatible with the Limited Memory BFGS optimizer, a quasi-Newton method that does not require computation of a Hessian matrix.

In this example, we create an optimizable object, pass it to a new optimizer, and optimize the parameters.

    OptimizerExample optimizable = new OptimizerExample(0, 0);
    Optimizer optimizer = new LimitedMemoryBFGS(optimizable);

    boolean converged = false;

    try {
        converged = optimizer.optimize();
    } catch (IllegalArgumentException e) {
        // This exception may be thrown if L-BFGS
        //  cannot step in the current direction.
        // This condition does not necessarily mean that
        //  the optimizer has failed, but it doesn't want
        //  to claim to have succeeded...        
    }

    System.out.println(optimizable.getParameter(0) + ", " +
                       optimizable.getParameter(1));
                           
As expected, the result is close to 0.33 and -0.5.
