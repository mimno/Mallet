package cc.mallet.grmm.learning;

import cc.mallet.optimize.Optimizable;
import cc.mallet.types.InstanceList;

/**
 * $Id: ACRFTrainer.java,v 1.1 2007/10/22 21:37:43 mccallum Exp $
 */
public interface ACRFTrainer {

  boolean train (ACRF acrf, InstanceList training);

  boolean train (ACRF acrf, InstanceList training, int numIter);

  boolean train (ACRF acrf, InstanceList training, ACRFEvaluator eval, int numIter);

  boolean train (ACRF acrf,
                 InstanceList training,
                 InstanceList validation,
                 InstanceList testing,
                 int numIter);

  boolean train (ACRF acrf,
                 InstanceList training,
                 InstanceList validation,
                 InstanceList testing,
                 ACRFEvaluator eval,
                 int numIter);

  boolean train (ACRF acrf,
                 InstanceList training,
                 InstanceList validation,
                 InstanceList testing,
                 ACRFEvaluator eval,
                 int numIter,
                 Optimizable.ByGradientValue macrf);
}
