package cc.mallet.grmm.types;

/**
 * A factor that supports taking derivatives with respect to its continuous variables.
 *   For example, a Gaussian factor can support derivatives with respect to its mean and precision.
 * $Id: ParameterizedFactor.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public interface ParameterizedFactor extends Factor {

  /**
   * Computes the expected derivative of the log factor value.  That is,
   *  <pre>sum_{y} q(y) dlog f(y) / d theta<pre>,
   * where y are the outcomes of the discrete varables in the factor,
   *  f(y) is the factor value, and theta is the vector of continuous variables
   *  in the factor.  q is a user-specified distribution to take the expectation
   *  with respect to.
   *  <p>
   *  The factor q specifies with variables to sum over.  The summation will be over
   *  all the variables in <tt>q.varSet()</tt>, and the rest of the variables will be used
   *
   *  <p>
   * @param q Distribution to take with respect to (need not be normalized).
   *    <tt>q.varSet()</tt> should be all of the variables of this factor, except for one continuous variable
   *
   * @param param Parameter to take gradient with respect to.
   * @return The expected gradient
   */
  public double sumGradLog (Factor q, Variable param, Assignment assn);

}
